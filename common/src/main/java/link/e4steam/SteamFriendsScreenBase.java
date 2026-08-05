package link.e4steam;

import link.e4steam.steam.SteamRuntime;
import link.e4steam.steam.SteamSession;
import link.e4steam.steam.SteamSocialSnapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Version-neutral state and layout for the snapshot-style Steam friends overlay. */
public abstract class SteamFriendsScreenBase extends Screen {
    protected static final int CONTENT_WIDTH = 220;
    protected static final int PANEL_WIDTH = 236;
    private static final int TAB_WIDTH = 110;
    private static final int TAB_HEIGHT = 20;
    private static final int TAB_GAP = 7;
    private static final int BORDER = 8;
    private static final int LIST_MARGIN = 8;
    private static final int ENTRY_WIDTH = CONTENT_WIDTH - LIST_MARGIN * 2;
    private static final int MAX_ROWS = 32;
    private static final int ROW_HEIGHT = 28;
    private static final int FRIEND_CONTROLS_HEIGHT = 50;
    private static final int REQUESTS_HEADER_HEIGHT = 24;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_MIN_HEIGHT = 24;
    private static final int REFRESH_INTERVAL_TICKS = 20;
    private static final ResourceLocation BACKGROUND = MinecraftUiCompat.resourceLocation(
            "e4steam_minecraft",
            "textures/gui/sprites/friends/background.png"
    );
    private static final ResourceLocation LIST_SEPARATOR = MinecraftUiCompat.resourceLocation(
            "e4steam_minecraft",
            "textures/gui/sprites/friends/list_separator_top.png"
    );
    private static final ResourceLocation BUTTON = friendsTexture("button.png");
    private static final ResourceLocation BUTTON_DISABLED = friendsTexture("button_disabled.png");
    private static final ResourceLocation BUTTON_HIGHLIGHTED = friendsTexture("button_highlighted.png");
    private static final ResourceLocation ACCEPT = friendsTexture("accept.png");
    private static final ResourceLocation ACCEPT_HIGHLIGHTED = friendsTexture("accept_highlighted.png");
    private static final ResourceLocation REJECT = friendsTexture("reject.png");
    private static final ResourceLocation REJECT_HIGHLIGHTED = friendsTexture("reject_highlighted.png");
    private static final ResourceLocation SEND_REQUEST = friendsTexture("send_request.png");
    private static final ResourceLocation EMPTY_ILLUSTRATION = friendsTexture("illustrations_00.png");
    private static final ResourceLocation CHECKBOX = widgetTexture("checkbox.png");
    private static final ResourceLocation CHECKBOX_HIGHLIGHTED = widgetTexture("checkbox_highlighted.png");
    private static final ResourceLocation CHECKBOX_SELECTED = widgetTexture("checkbox_selected.png");
    private static final ResourceLocation CHECKBOX_SELECTED_HIGHLIGHTED =
            widgetTexture("checkbox_selected_highlighted.png");
    private static final ResourceLocation SCROLLER = widgetTexture("scroller.png");
    private static final ResourceLocation SCROLLER_BACKGROUND = widgetTexture("scroller_background.png");

    protected final Screen parent;
    protected int panelLeft;
    protected int panelTop;
    protected int panelHeight;
    protected int contentLeft;
    protected int contentTop;
    protected int contentHeight;

    private final Object activityLock = new Object();
    private final FriendsUiRequestGate requestGate = new FriendsUiRequestGate();
    private static boolean showAllFriends;
    private volatile SteamRuntime.Activity activity;
    private volatile boolean open;
    private volatile boolean operationInProgress;
    private SteamSocialSnapshot snapshot = SteamSocialSnapshot.empty();
    private Tab selectedTab = Tab.FRIENDS;
    private String statusKey = "text.e4steam_minecraft.friends.loading";
    private boolean loadedOnce;
    private int scrollIndex;
    private int refreshTicks;
    private int generation;
    private int visibleRows;
    private EditBox searchBox;
    private String searchText = "";
    private boolean draggingScrollbar;

    protected SteamFriendsScreenBase(Screen parent) {
        super(Mirror.translatable("text.e4steam_minecraft.friends.title"));
        this.parent = parent;
    }

    @Override
    protected final void init() {
        open = true;
        generation = requestGate.open();
        contentHeight = Math.max(FRIEND_CONTROLS_HEIGHT + ROW_HEIGHT, height - 80);
        panelHeight = contentHeight + BORDER * 2 + 1;
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - panelHeight) / 2;
        contentLeft = panelLeft + BORDER;
        contentTop = panelTop + BORDER;
        visibleRows = rowCapacity();

        searchBox = addRenderableWidget(new EditBox(
                font, contentLeft + LIST_MARGIN, contentTop + 3,
                ENTRY_WIDTH, 20,
                Mirror.translatable("text.e4steam_minecraft.friends.search")
        ));
        searchBox.setMaxLength(64);
        MinecraftUiCompat.editBoxHint(searchBox,
                Mirror.translatable("text.e4steam_minecraft.friends.search"));
        searchBox.setValue(searchText);
        searchBox.setResponder(value -> {
            searchText = value;
            scrollIndex = 0;
            updateWidgets();
        });

        updateWidgets();
        refreshNow();
    }

    @Override
    public final void tick() {
        if (!open || operationInProgress) {
            return;
        }
        if (++refreshTicks >= REFRESH_INTERVAL_TICKS) {
            refreshNow();
        }
    }

    @Override
    public void removed() {
        open = false;
        generation = requestGate.close();
        SteamRuntime.Activity closing;
        synchronized (activityLock) {
            closing = activity;
            activity = null;
        }
        if (closing != null) {
            closing.close();
        }
        releaseRenderResources();
    }

    @Override
    public final void onClose() {
        if (minecraft != null) {
            MinecraftUiCompat.setScreen(minecraft, parent);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inside(mouseX, mouseY, contentLeft,
                contentTop - TAB_HEIGHT - TAB_GAP, TAB_WIDTH, TAB_HEIGHT)) {
            select(Tab.FRIENDS);
            return true;
        }
        if (inside(mouseX, mouseY, contentLeft + TAB_WIDTH,
                contentTop - TAB_HEIGHT - TAB_GAP, TAB_WIDTH, TAB_HEIGHT)) {
            select(Tab.REQUESTS);
            return true;
        }
        if (selectedTab == Tab.FRIENDS
                && inside(mouseX, mouseY, contentLeft + LIST_MARGIN, contentTop + 27,
                ENTRY_WIDTH, 20)) {
            showAllFriends = !showAllFriends;
            scrollIndex = 0;
            updateWidgets();
            return true;
        }
        if (isOverScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return true;
        }
        if (!operationInProgress && clickRowAction(mouseX, mouseY)) {
            return true;
        }
        int tabsTop = contentTop - TAB_HEIGHT - TAB_GAP;
        if (!inside(mouseX, mouseY, panelLeft, tabsTop,
                PANEL_WIDTH, panelHeight + TAB_HEIGHT + TAB_GAP)) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickRowAction(double mouseX, double mouseY) {
        if (mouseY < rowsTop() || mouseY >= rowsTop() + visibleRows * ROW_HEIGHT) return false;
        int row = (int) (mouseY - rowsTop()) / ROW_HEIGHT;
        int index = scrollIndex + row;
        if (index >= entries().size()) return false;
        int buttonY = rowsTop() + row * ROW_HEIGHT + 4;
        if (selectedTab == Tab.REQUESTS) {
            SteamSocialSnapshot.Invitation invitation = visibleInvitations().get(index);
            if (!invitation.actionable(System.currentTimeMillis())) return false;
            if (inside(mouseX, mouseY, acceptButtonLeft(), buttonY, 20, 20)) {
                activateRow(row);
                return true;
            }
            if (inside(mouseX, mouseY, actionButtonLeft(), buttonY, 20, 20)) {
                rejectInvitationRow(row);
                return true;
            }
            return false;
        }
        SteamSocialSnapshot.Friend friend = visibleFriends().get(index);
        if (friendAction(friend) != FriendAction.NONE
                && inside(mouseX, mouseY, actionButtonLeft(), buttonY, 20, 20)) {
            activateRow(row);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return handleMouseScrolled(mouseX, mouseY, amount);
    }

    /** Minecraft 1.20.5+ adds a horizontal scroll argument. */
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return handleMouseScrolled(mouseX, mouseY, verticalAmount);
    }

    private boolean handleMouseScrolled(double mouseX, double mouseY, double amount) {
        if (inside(mouseX, mouseY, contentLeft, rowsTop(), CONTENT_WIDTH, listHeight())
                && amount != 0.0 && entries().size() > visibleRows) {
            int next = scrollIndex + (amount > 0.0 ? -1 : 1);
            int clamped = Math.max(0, Math.min(next, maxScrollIndex()));
            if (clamped != scrollIndex) {
                scrollIndex = clamped;
                updateWidgets();
            }
            return true;
        }
        return false;
    }

    protected final void renderPanel(Painter painter, int mouseX, int mouseY) {
        painter.fill(0, 0, width, height, 0x66000000);
        drawPanelBackground(painter);
        int tabY = contentTop - TAB_HEIGHT - TAB_GAP;
        drawTab(painter, contentLeft, tabY,
                Mirror.translatable("text.e4steam_minecraft.friends.tab"),
                selectedTab == Tab.FRIENDS, mouseX, mouseY);
        drawTab(painter, contentLeft + TAB_WIDTH, tabY, requestsTitle(),
                selectedTab == Tab.REQUESTS, mouseX, mouseY);

        if (selectedTab == Tab.FRIENDS) {
            renderFriends(painter, mouseX, mouseY);
        } else {
            renderRequests(painter, mouseX, mouseY);
        }
    }

    protected final void renderButtonAvatars(Painter painter) {
        // Button icons are rendered by the version-specific widget implementation.
    }

    protected void releaseRenderResources() {
    }

    private void renderFriends(Painter painter, int mouseX, int mouseY) {
        int boxLeft = contentLeft + LIST_MARGIN;
        int boxTop = contentTop + 27;
        int boxSize = 20;
        boolean checkboxHovered = inside(mouseX, mouseY, boxLeft, boxTop, ENTRY_WIDTH, boxSize);
        ResourceLocation checkbox = showAllFriends
                ? (checkboxHovered ? CHECKBOX_SELECTED_HIGHLIGHTED : CHECKBOX_SELECTED)
                : (checkboxHovered ? CHECKBOX_HIGHLIGHTED : CHECKBOX);
        painter.texture(checkbox, boxLeft, boxTop, 0.0f, 0.0f, boxSize, boxSize, boxSize, boxSize);
        painter.text(Mirror.translatable("text.e4steam_minecraft.friends.filter.all"),
                boxLeft + boxSize + 7, boxTop + 6, 0xffffffff);
        drawSeparator(painter, rowsTop() - 2);

        List<SteamSocialSnapshot.Friend> friends = visibleFriends();
        int start = scrollIndex;
        if (friends.isEmpty()) {
            String emptyKey = emptyFriendsStatusKey();
            if ("text.e4steam_minecraft.friends.loading".equals(emptyKey)) {
                String dots = ".".repeat((int) (System.currentTimeMillis() / 350L % 4L));
                painter.centered(Mirror.literal(
                                Mirror.translatable(emptyKey).getString() + dots),
                        contentLeft + CONTENT_WIDTH / 2,
                        rowsTop() + Math.max(0, listHeight() / 2 - 4), 0xffffffff);
            } else if (emptyKey.endsWith(".empty") && listHeight() >= 66) {
                int imageX = contentLeft + (CONTENT_WIDTH - 128) / 2;
                int imageY = rowsTop() + Math.max(0, (listHeight() - 66) / 2);
                painter.texture(EMPTY_ILLUSTRATION, imageX, imageY,
                        0.0f, 0.0f, 128, 48, 128, 48);
                painter.centered(Mirror.translatable(emptyKey),
                        contentLeft + CONTENT_WIDTH / 2, imageY + 53, 0xffa0a0a0);
            } else {
                painter.centered(Mirror.translatable(emptyKey),
                        contentLeft + CONTENT_WIDTH / 2,
                        rowsTop() + Math.max(0, listHeight() / 2 - 4), 0xffa0a0a0);
            }
        }
        for (int row = 0; row < visibleRows && start + row < friends.size(); row++) {
            SteamSocialSnapshot.Friend friend = friends.get(start + row);
            int y = rowsTop() + row * ROW_HEIGHT;
            painter.avatar(friend.steamId(), friend.avatar(), entryLeft(), y + 2, 24);
            int textLeft = entryLeft() + 28;
            int textWidth = actionButtonLeft() - textLeft - 2;
            painter.text(Mirror.literal(ellipsize(friend.name(), textWidth)), textLeft, y + 3, 0xffffffff);
            painter.text(ellipsize(friendStatus(friend), textWidth), textLeft, y + 15, friendStatusColor(friend));
            FriendAction action = friendAction(friend);
            if (action != FriendAction.NONE) {
                ResourceLocation icon = action == FriendAction.JOIN ? ACCEPT : SEND_REQUEST;
                ResourceLocation highlightedIcon = action == FriendAction.JOIN
                        ? ACCEPT_HIGHLIGHTED : SEND_REQUEST;
                int iconSize = action == FriendAction.JOIN ? 18 : 15;
                drawIconButton(painter, actionButtonLeft(), y + 4, icon, highlightedIcon,
                        iconSize, mouseX, mouseY, !operationInProgress);
            }
        }
        renderScrollbar(painter, friends.size());
    }

    private void renderRequests(Painter painter, int mouseX, int mouseY) {
        painter.centered(
                Mirror.withStyle(
                        Mirror.translatable("text.e4steam_minecraft.friends.requests.received.heading"),
                        style -> style.withBold(true).withUnderlined(true)
                ),
                contentLeft + CONTENT_WIDTH / 2,
                contentTop + 7,
                0xffffffff
        );
        drawSeparator(painter, rowsTop() - 2);
        List<SteamSocialSnapshot.Invitation> invitations = visibleInvitations();
        int start = scrollIndex;
        if (invitations.isEmpty()) {
            painter.centered(
                    Mirror.translatable("text.e4steam_minecraft.friends.requests.empty"),
                    contentLeft + CONTENT_WIDTH / 2,
                    rowsTop() + Math.max(0, listHeight() / 2 - 4),
                    0xffa0a0a0
            );
        }
        for (int row = 0; row < visibleRows && start + row < invitations.size(); row++) {
            SteamSocialSnapshot.Invitation invitation = invitations.get(start + row);
            int y = rowsTop() + row * ROW_HEIGHT;
            SteamSocialSnapshot.Friend friend = findFriend(invitation.steamId());
            painter.avatar(
                    invitation.steamId(),
                    friend == null ? SteamSocialSnapshot.Avatar.empty() : friend.avatar(),
                    entryLeft(),
                    y + 2,
                    24
            );
            int textLeft = entryLeft() + 28;
            int textWidth = acceptButtonLeft() - textLeft - 2;
            painter.text(Mirror.literal(ellipsize(invitation.friendName(), textWidth)), textLeft, y + 3, 0xffffffff);
            painter.text(
                    ellipsize(invitationStatus(invitation), textWidth),
                    textLeft,
                    y + 15,
                    invitation.actionable(System.currentTimeMillis())
                            ? 0xff55ff55
                            : 0xffaaaaaa
            );
            if (invitation.actionable(System.currentTimeMillis())) {
                drawIconButton(painter, acceptButtonLeft(), y + 4,
                        ACCEPT, ACCEPT_HIGHLIGHTED, 18,
                        mouseX, mouseY, !operationInProgress);
                drawIconButton(painter, actionButtonLeft(), y + 4,
                        REJECT, REJECT_HIGHLIGHTED, 18,
                        mouseX, mouseY, !operationInProgress);
            }
        }
        renderScrollbar(painter, invitations.size());
    }

    private int actionButtonLeft() {
        return entryLeft() + ENTRY_WIDTH - 20;
    }

    private int rowsTop() {
        return contentTop + (selectedTab == Tab.FRIENDS
                ? FRIEND_CONTROLS_HEIGHT : REQUESTS_HEADER_HEIGHT);
    }

    private void drawPanelBackground(Painter painter) {
        painter.texture(BACKGROUND, panelLeft, panelTop, 0.0f, 0.0f, PANEL_WIDTH, BORDER, 236, 34);
        int remaining = panelHeight - BORDER * 2;
        int y = panelTop + BORDER;
        while (remaining > 0) {
            int slice = Math.min(18, remaining);
            painter.texture(BACKGROUND, panelLeft, y, 0.0f, 8.0f, PANEL_WIDTH, slice, 236, 34);
            y += slice;
            remaining -= slice;
        }
        painter.texture(BACKGROUND, panelLeft, panelTop + panelHeight - BORDER,
                0.0f, 26.0f, PANEL_WIDTH, BORDER, 236, 34);
    }

    private void drawSeparator(Painter painter, int y) {
        int x = contentLeft;
        int remaining = CONTENT_WIDTH;
        while (remaining > 0) {
            int slice = Math.min(32, remaining);
            painter.texture(LIST_SEPARATOR, x, y, 0.0f, 0.0f, slice, 2, 32, 2);
            x += slice;
            remaining -= slice;
        }
    }

    private void drawTab(
            Painter painter,
            int x,
            int y,
            Component title,
            boolean selected,
            int mouseX,
            int mouseY
    ) {
        boolean highlighted = selected || inside(mouseX, mouseY, x, y, TAB_WIDTH, TAB_HEIGHT);
        ResourceLocation texture = operationInProgress
                ? BUTTON_DISABLED
                : highlighted ? BUTTON_HIGHLIGHTED : BUTTON;
        drawHorizontalNineSlice(painter, texture, x, y, TAB_WIDTH, TAB_HEIGHT, 3, 200, 20);
        painter.centered(title, x + TAB_WIDTH / 2, y + (selected ? 6 : 7),
                operationInProgress ? 0xffa0a0a0 : 0xffffffff);
        if (selected) {
            int underlineWidth = Math.min(font.width(title), TAB_WIDTH - 4);
            int underlineX = x + (TAB_WIDTH - underlineWidth) / 2;
            painter.fill(underlineX, y + TAB_HEIGHT - 2,
                    underlineX + underlineWidth, y + TAB_HEIGHT - 1, 0xffffffff);
        }
    }

    private void drawIconButton(
            Painter painter,
            int x,
            int y,
            ResourceLocation icon,
            ResourceLocation highlightedIcon,
            int iconSize,
            int mouseX,
            int mouseY,
            boolean active
    ) {
        boolean hovered = active && inside(mouseX, mouseY, x, y, 20, 20);
        ResourceLocation background = !active
                ? BUTTON_DISABLED : hovered ? BUTTON_HIGHLIGHTED : BUTTON;
        drawHorizontalNineSlice(painter, background, x, y, 20, 20, 3, 200, 20);
        int iconOffset = (20 - iconSize) / 2;
        painter.texture(hovered ? highlightedIcon : icon,
                x + iconOffset, y + iconOffset,
                0.0f, 0.0f, iconSize, iconSize, iconSize, iconSize);
    }

    private static void drawHorizontalNineSlice(
            Painter painter,
            ResourceLocation texture,
            int x,
            int y,
            int width,
            int height,
            int border,
            int textureWidth,
            int textureHeight
    ) {
        int middle = Math.max(0, width - border * 2);
        painter.texture(texture, x, y, 0.0f, 0.0f,
                border, height, textureWidth, textureHeight);
        int drawn = 0;
        int sourceMiddle = textureWidth - border * 2;
        while (drawn < middle) {
            int slice = Math.min(sourceMiddle, middle - drawn);
            painter.texture(texture, x + border + drawn, y, border, 0.0f,
                    slice, height, textureWidth, textureHeight);
            drawn += slice;
        }
        painter.texture(texture, x + width - border, y, textureWidth - border, 0.0f,
                border, height, textureWidth, textureHeight);
    }

    private static ResourceLocation friendsTexture(String fileName) {
        return MinecraftUiCompat.resourceLocation(
                "e4steam_minecraft", "textures/gui/sprites/friends/" + fileName
        );
    }

    private static ResourceLocation widgetTexture(String fileName) {
        return MinecraftUiCompat.resourceLocation(
                "e4steam_minecraft", "textures/gui/sprites/widget/" + fileName
        );
    }

    private void select(Tab tab) {
        selectedTab = tab;
        scrollIndex = 0;
        updateWidgets();
    }

    private void refreshNow() {
        int requestedGeneration = requestGate.tryBegin();
        if (requestedGeneration < 0) {
            return;
        }
        boolean initialRefresh = !loadedOnce;
        if (initialRefresh) {
            operationInProgress = true;
            statusKey = "text.e4steam_minecraft.friends.loading";
        }
        refreshTicks = 0;
        try {
            ensureActivity();
            SteamRuntime.get().socialSnapshotAsync().whenComplete(
                    (loaded, failure) -> finishRefresh(requestedGeneration, loaded, failure, initialRefresh)
            );
        } catch (Throwable failure) {
            finishRefresh(requestedGeneration, null, failure, initialRefresh);
        }
    }

    private void finishRefresh(
            int requestedGeneration,
            SteamSocialSnapshot loaded,
            Throwable failure,
            boolean initialRefresh
    ) {
        Minecraft.getInstance().execute(() -> {
            if (!requestGate.finish(requestedGeneration)) {
                return;
            }
            if (initialRefresh) operationInProgress = false;
            if (failure != null) {
                if (initialRefresh) {
                    E4steamClient.LOGGER.warn("Could not load the e4steam friends list", failure);
                    statusKey = "text.e4steam_minecraft.friends.unavailable";
                } else {
                    E4steamClient.LOGGER.debug("Could not refresh the e4steam friends list", failure);
                }
            } else {
                snapshot = loaded == null ? SteamSocialSnapshot.empty() : loaded;
                loadedOnce = true;
                statusKey = snapshot.friends().isEmpty()
                        ? "text.e4steam_minecraft.friends.empty"
                        : "text.e4steam_minecraft.friends.ready";
                scrollIndex = Math.min(scrollIndex, maxScrollIndex());
            }
            updateWidgets();
        });
    }

    private void ensureActivity() {
        synchronized (activityLock) {
            if (activity == null) {
                activity = SteamRuntime.get().acquireActivity();
            }
        }
    }

    private void activateRow(int row) {
        int index = scrollIndex + row;
        if (selectedTab == Tab.REQUESTS) {
            List<SteamSocialSnapshot.Invitation> invitations = visibleInvitations();
            if (index < invitations.size()) {
                SteamSocialSnapshot.Invitation invitation = invitations.get(index);
                if (invitation.actionable(System.currentTimeMillis())) {
                    runOperation(
                            invitation.direction() == SteamSocialSnapshot.Direction.JOIN_REQUEST_RECEIVED
                                    ? Operation.APPROVE_JOIN_REQUEST
                                    : Operation.JOIN_INVITATION,
                            invitation.steamId(),
                            invitation.direction() == SteamSocialSnapshot.Direction.JOIN_REQUEST_RECEIVED
                                    ? invitation.expiresAtMillis()
                                    : invitation.lobbyId()
                    );
                } else {
                    runOperation(Operation.PROFILE, invitation.steamId());
                }
            }
            return;
        }
        List<SteamSocialSnapshot.Friend> friends = visibleFriends();
        if (index >= friends.size()) {
            return;
        }
        SteamSocialSnapshot.Friend friend = friends.get(index);
        switch (friendAction(friend)) {
            case INVITE -> runOperation(Operation.INVITE, friend.steamId());
            case JOIN -> runOperation(Operation.JOIN, friend.steamId());
            case REQUEST_JOIN -> runOperation(Operation.REQUEST_JOIN, friend.steamId());
            case NONE -> { }
        }
    }

    private void rejectInvitationRow(int row) {
        if (selectedTab != Tab.REQUESTS) return;
        int index = scrollIndex + row;
        List<SteamSocialSnapshot.Invitation> invitations = visibleInvitations();
        if (index >= invitations.size()) return;
        SteamSocialSnapshot.Invitation invitation = invitations.get(index);
        if (!invitation.actionable(System.currentTimeMillis())) return;
        if (invitation.direction() == SteamSocialSnapshot.Direction.JOIN_REQUEST_RECEIVED) {
            runOperation(Operation.DISMISS_JOIN_REQUEST, invitation.steamId(), invitation.expiresAtMillis());
        } else {
            runOperation(Operation.DISMISS_INVITATION, invitation.steamId(), invitation.lobbyId());
        }
    }

    private void runOperation(Operation operation, long steamId) {
        runOperation(operation, steamId, 0L);
    }

    private void runOperation(Operation operation, long steamId, long lobbyId) {
        int requestedGeneration = requestGate.tryBegin();
        if (requestedGeneration < 0) {
            return;
        }
        operationInProgress = true;
        statusKey = "text.e4steam_minecraft.friends.working";
        try {
            ensureActivity();
            SteamRuntime runtime = SteamRuntime.get();
            CompletableFuture<Boolean> task = switch (operation) {
                case OVERLAY -> runtime.openFriendsOverlayAsync().thenApply(ignored -> true);
                case PROFILE -> runtime.openFriendProfileAsync(steamId).thenApply(ignored -> true);
                case JOIN -> runtime.joinFriendAsync(steamId);
                case JOIN_INVITATION -> runtime.joinInvitationAsync(lobbyId, steamId);
                case DISMISS_INVITATION -> runtime.dismissInvitationAsync(lobbyId);
                case REQUEST_JOIN -> runtime.requestToJoinAsync(steamId);
                case DISMISS_JOIN_REQUEST -> runtime.dismissJoinRequestAsync(steamId, lobbyId);
                case APPROVE_JOIN_REQUEST -> {
                    SteamSession current = E4steamClient.session;
                    yield current == null
                            ? CompletableFuture.completedFuture(false)
                            : runtime.approveJoinRequestAsync(current, steamId, lobbyId);
                }
                case INVITE -> {
                    SteamSession current = E4steamClient.session;
                    yield current == null
                            ? CompletableFuture.completedFuture(false)
                            : runtime.inviteFriendAsync(current, steamId);
                }
            };
            task.whenComplete((succeeded, failure) -> finishOperation(
                    requestedGeneration,
                    operation,
                    Boolean.TRUE.equals(succeeded),
                    failure
            ));
        } catch (Throwable failure) {
            finishOperation(requestedGeneration, operation, false, failure);
        }
    }

    private void finishOperation(int requestedGeneration, Operation operation, boolean succeeded, Throwable failure) {
        Minecraft.getInstance().execute(() -> {
            if (!requestGate.finish(requestedGeneration)) {
                return;
            }
            operationInProgress = false;
            if (failure != null) {
                E4steamClient.LOGGER.warn("Steam friends action failed", failure);
                statusKey = "text.e4steam_minecraft.friends.action.failed";
            } else if (!succeeded) {
                statusKey = "text.e4steam_minecraft.friends.action.unavailable";
            } else if (operation == Operation.INVITE) {
                statusKey = "text.e4steam_minecraft.friends.invite.sent";
                refreshNow();
                return;
            } else if (operation == Operation.APPROVE_JOIN_REQUEST
                    || operation == Operation.DISMISS_INVITATION
                    || operation == Operation.DISMISS_JOIN_REQUEST) {
                refreshNow();
                return;
            } else if (operation == Operation.REQUEST_JOIN) {
                statusKey = "text.e4steam_minecraft.friends.join_request.sent";
            } else if (operation == Operation.JOIN || operation == Operation.JOIN_INVITATION) {
                statusKey = "text.e4steam_minecraft.friends.join.started";
            } else {
                statusKey = "text.e4steam_minecraft.friends.ready";
            }
            updateWidgets();
        });
    }

    private void updateWidgets() {
        if (searchBox == null) {
            return;
        }
        searchBox.visible = selectedTab == Tab.FRIENDS;
        searchBox.active = !operationInProgress;
        visibleRows = rowCapacity();
        scrollIndex = Math.min(scrollIndex, maxScrollIndex());
    }

    private SteamSocialSnapshot.Friend findFriend(long steamId) {
        for (SteamSocialSnapshot.Friend friend : snapshot.friends()) {
            if (friend.steamId() == steamId) {
                return friend;
            }
        }
        return null;
    }

    private String ellipsize(String value, int maxWidth) {
        if (value == null || value.isEmpty()) return "";
        if (font.width(value) <= maxWidth) return value;
        return font.plainSubstrByWidth(value, Math.max(1, maxWidth - font.width("…"))) + "…";
    }

    private Component ellipsize(Component value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        return Mirror.literal(ellipsize(value.getString(), maxWidth));
    }

    private Component friendStatus(SteamSocialSnapshot.Friend friend) {
        if (SteamRuntime.get().isPeerConnected(friend.steamId())) {
            return colored(Mirror.translatable(
                    "text.e4steam_minecraft.friends.status.connected"), ChatFormatting.GREEN);
        }
        if (friend.hosting()) {
            return colored(Mirror.translatable(
                    "text.e4steam_minecraft.friends.status.minecraft.world.version",
                    friend.minecraftVersion()), ChatFormatting.GREEN);
        }
        if (friend.playingMinecraft()) {
            return colored(Mirror.translatable(
                    "text.e4steam_minecraft.friends.status.minecraft.online.version",
                    friend.minecraftVersion()), ChatFormatting.AQUA);
        }
        if (friend.joinable()) {
            return colored(
                    friend.compatible()
                            ? Mirror.translatable("text.e4steam_minecraft.friends.status.joinable")
                            : Mirror.translatable(
                                    "text.e4steam_minecraft.friends.status.incompatible",
                                    friend.minecraftVersion().isBlank() ? "?" : friend.minecraftVersion()
                            ),
                    friend.compatible() ? ChatFormatting.GREEN : ChatFormatting.YELLOW
            );
        }
        if (friend.e4steamActive()) {
            return colored(Mirror.translatable("text.e4steam_minecraft.friends.status.e4steam"), ChatFormatting.AQUA);
        }
        if (friend.playingSpacewar()) {
            return colored(Mirror.translatable("text.e4steam_minecraft.friends.status.spacewar"), ChatFormatting.DARK_AQUA);
        }
        return colored(Mirror.translatable(switch (friend.presence()) {
            case OFFLINE -> "text.e4steam_minecraft.friends.status.offline";
            case AWAY -> "text.e4steam_minecraft.friends.status.away";
            case BUSY -> "text.e4steam_minecraft.friends.status.busy";
            case ONLINE -> "text.e4steam_minecraft.friends.status.online";
        }), switch (friend.presence()) {
            case OFFLINE -> ChatFormatting.GRAY;
            case AWAY, BUSY -> ChatFormatting.YELLOW;
            case ONLINE -> ChatFormatting.WHITE;
        });
    }

    private FriendAction friendAction(SteamSocialSnapshot.Friend friend) {
        if (SteamRuntime.get().isPeerConnected(friend.steamId())) return FriendAction.NONE;
        SteamSession session = E4steamClient.session;
        if (session != null && session.state == SteamSession.State.STARTED
                && friend.presence() != SteamSocialSnapshot.Presence.OFFLINE) {
            return FriendAction.INVITE;
        }
        if (friend.joinable() && friend.compatible()) return FriendAction.JOIN;
        if (friend.hosting() && friend.compatible()) return FriendAction.REQUEST_JOIN;
        return FriendAction.NONE;
    }

    private List<?> entries() {
        return selectedTab == Tab.FRIENDS ? visibleFriends() : visibleInvitations();
    }

    private List<SteamSocialSnapshot.Friend> visibleFriends() {
        List<SteamSocialSnapshot.Friend> base = showAllFriends
                ? snapshot.friends()
                : SteamSocialSnapshot.minecraftFriends(snapshot.friends());
        return SteamSocialSnapshot.filterByName(base, searchText);
    }

    private List<SteamSocialSnapshot.Invitation> visibleInvitations() {
        long now = System.currentTimeMillis();
        return snapshot.invitations().stream()
                .filter(invitation -> invitation.direction() == SteamSocialSnapshot.Direction.RECEIVED
                        || invitation.direction() == SteamSocialSnapshot.Direction.JOIN_REQUEST_RECEIVED)
                .filter(invitation -> !invitation.canceled() && now < invitation.expiresAtMillis())
                .toList();
    }

    private String emptyFriendsStatusKey() {
        if (!"text.e4steam_minecraft.friends.ready".equals(statusKey)
                && !"text.e4steam_minecraft.friends.empty".equals(statusKey)) {
            return statusKey;
        }
        if (!searchText.isBlank()) return "text.e4steam_minecraft.friends.search.empty";
        return showAllFriends
                ? "text.e4steam_minecraft.friends.empty"
                : "text.e4steam_minecraft.friends.minecraft.empty";
    }

    private int maxScrollIndex() {
        return Math.max(0, entries().size() - visibleRows);
    }

    private Component requestsTitle() {
        long now = System.currentTimeMillis();
        long received = snapshot.invitations().stream()
                .filter(invitation -> invitation.actionable(now))
                .count();
        return Mirror.translatable("text.e4steam_minecraft.friends.requests.tab", received);
    }

    private Component invitationStatus(SteamSocialSnapshot.Invitation invitation) {
        if (invitation.canceled()) {
            return Mirror.translatable("text.e4steam_minecraft.friends.request.canceled");
        }
        if (System.currentTimeMillis() >= invitation.expiresAtMillis()) {
            return Mirror.translatable("text.e4steam_minecraft.friends.request.expired");
        }
        return Mirror.translatable(switch (invitation.direction()) {
            case RECEIVED -> "text.e4steam_minecraft.friends.request.received";
            case SENT -> "text.e4steam_minecraft.friends.request.sent";
            case JOIN_REQUEST_RECEIVED -> "text.e4steam_minecraft.friends.join_request.received";
            case JOIN_REQUEST_SENT -> "text.e4steam_minecraft.friends.join_request.sent";
        });
    }

    private static Component colored(Component component, ChatFormatting color) {
        return Mirror.withStyle(component, style -> style.withColor(color));
    }

    private int friendStatusColor(SteamSocialSnapshot.Friend friend) {
        if (friend.joinable()) return friend.compatible() ? 0xff55ff55 : 0xffffff55;
        if (friend.e4steamActive()) return 0xff55ffff;
        if (friend.playingSpacewar()) return 0xff00aaaa;
        return switch (friend.presence()) {
            case OFFLINE -> 0xffaaaaaa;
            case AWAY, BUSY -> 0xffffff55;
            case ONLINE -> 0xffffffff;
        };
    }

    private int entryLeft() {
        return contentLeft + LIST_MARGIN;
    }

    private int acceptButtonLeft() {
        return actionButtonLeft() - 24;
    }

    private int listHeight() {
        return Math.max(ROW_HEIGHT, contentHeight - (rowsTop() - contentTop));
    }

    private int rowCapacity() {
        int reserved = selectedTab == Tab.FRIENDS
                ? FRIEND_CONTROLS_HEIGHT : REQUESTS_HEADER_HEIGHT;
        return Math.max(1, Math.min(MAX_ROWS, (contentHeight - reserved) / ROW_HEIGHT));
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        if (entries().size() <= visibleRows) return false;
        int trackX = contentLeft + CONTENT_WIDTH - SCROLLBAR_WIDTH;
        return inside(mouseX, mouseY, trackX, rowsTop(),
                SCROLLBAR_WIDTH, visibleRows * ROW_HEIGHT);
    }

    private void updateScrollFromMouse(double mouseY) {
        int entryCount = entries().size();
        if (entryCount <= visibleRows) return;
        int trackHeight = visibleRows * ROW_HEIGHT;
        int thumbHeight = scrollbarThumbHeight(entryCount, trackHeight);
        int travel = trackHeight - thumbHeight;
        if (travel <= 0) return;
        double relative = mouseY - rowsTop() - thumbHeight / 2.0;
        double fraction = Math.max(0.0, Math.min(1.0, relative / travel));
        scrollIndex = (int) Math.round(fraction * maxScrollIndex());
        updateWidgets();
    }

    private void renderScrollbar(Painter painter, int entryCount) {
        if (entryCount <= visibleRows) return;
        int trackX = contentLeft + CONTENT_WIDTH - SCROLLBAR_WIDTH;
        int trackY = rowsTop();
        int trackHeight = visibleRows * ROW_HEIGHT;
        int thumbHeight = scrollbarThumbHeight(entryCount, trackHeight);
        int travel = trackHeight - thumbHeight;
        int thumbY = trackY + (maxScrollIndex() == 0
                ? 0 : travel * scrollIndex / maxScrollIndex());
        drawVerticalNineSlice(painter, SCROLLER_BACKGROUND,
                trackX, trackY, SCROLLBAR_WIDTH, trackHeight);
        drawVerticalNineSlice(painter, SCROLLER,
                trackX, thumbY, SCROLLBAR_WIDTH, thumbHeight);
    }

    private static void drawVerticalNineSlice(
            Painter painter,
            ResourceLocation texture,
            int x,
            int y,
            int width,
            int height
    ) {
        painter.texture(texture, x, y, 0.0f, 0.0f, width, 1, 6, 32);
        int middle = Math.max(0, height - 2);
        int drawn = 0;
        while (drawn < middle) {
            int slice = Math.min(30, middle - drawn);
            painter.texture(texture, x, y + 1 + drawn, 0.0f, 1.0f,
                    width, slice, 6, 32);
            drawn += slice;
        }
        if (height > 1) {
            painter.texture(texture, x, y + height - 1, 0.0f, 31.0f,
                    width, 1, 6, 32);
        }
    }

    private static int scrollbarThumbHeight(int entryCount, int trackHeight) {
        int contentPixelHeight = entryCount * ROW_HEIGHT;
        int maximum = Math.max(8, trackHeight - 8);
        int minimum = Math.min(SCROLLBAR_MIN_HEIGHT, maximum);
        return Math.max(minimum, Math.min(maximum,
                trackHeight * trackHeight / contentPixelHeight));
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    protected interface Painter {
        void fill(int left, int top, int right, int bottom, int color);

        void text(Component text, int x, int y, int color);

        void centered(Component text, int centerX, int y, int color);

        void avatar(long steamId, SteamSocialSnapshot.Avatar avatar, int x, int y, int size);

        void texture(ResourceLocation texture, int x, int y, float u, float v, int width, int height,
                     int textureWidth, int textureHeight);
    }

    private enum Tab {
        FRIENDS,
        REQUESTS
    }

    private enum FriendAction {
        NONE,
        INVITE,
        JOIN,
        REQUEST_JOIN
    }

    private enum Operation {
        OVERLAY,
        PROFILE,
        INVITE,
        JOIN,
        JOIN_INVITATION,
        DISMISS_INVITATION,
        REQUEST_JOIN,
        DISMISS_JOIN_REQUEST,
        APPROVE_JOIN_REQUEST
    }
}
