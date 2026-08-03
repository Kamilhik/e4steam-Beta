package link.e4steam;

import com.mojang.blaze3d.platform.NativeImage;
import link.e4steam.steam.SteamRuntime;
import link.e4steam.steam.SteamSession;
import link.e4steam.steam.SteamSocialSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.FriendToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Native Minecraft 26.x renderer for the Steam Friends overlay. */
public final class SteamFriends26Screen extends Screen {
    private static final int CONTENT_WIDTH = 180;
    private static final int PANEL_WIDTH = 196;
    private static final int BORDER = 8;
    private static final int ROW_HEIGHT = 28;
    private static final int FRIEND_CONTROLS_HEIGHT = 48;
    private static final int REQUESTS_HEADER_HEIGHT = 24;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_MIN_HEIGHT = 32;
    private static final int REFRESH_INTERVAL_TICKS = 20;
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(
            "minecraft", "friends/background"
    );
    private static final Identifier SEPARATOR = Identifier.fromNamespaceAndPath(
            "minecraft", "friends/list_separator_top"
    );
    private static final Identifier EMPTY_ILLUSTRATION = Identifier.fromNamespaceAndPath(
            "minecraft", "friends/illustrations_00"
    );
    private static final Identifier SCROLLER = Identifier.fromNamespaceAndPath(
            "minecraft", "widget/scroller"
    );
    private static final Identifier SCROLLER_BACKGROUND = Identifier.fromNamespaceAndPath(
            "minecraft", "widget/scroller_background"
    );

    private final Screen parent;
    private static boolean showAllFriends;
    private static final Map<Long, Long> TOASTED_INVITATION_GENERATIONS = new HashMap<>();
    private static final Map<Long, Long> TOASTED_JOIN_REQUEST_GENERATIONS = new HashMap<>();
    private final Map<Long, AvatarTexture> avatars = new HashMap<>();
    private final Object activityLock = new Object();
    private final FriendsUiRequestGate requestGate = new FriendsUiRequestGate();
    private final List<Button> inviteRowActions = new ArrayList<>();
    private final List<Button> requestJoinRowActions = new ArrayList<>();
    private final List<Button> joinRowActions = new ArrayList<>();
    private final List<Button> rejectRowActions = new ArrayList<>();
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
    private int contentHeight;
    private int panelHeight;
    private int panelLeft;
    private int panelTop;
    private int contentLeft;
    private int contentTop;
    private int visibleRows;
    private Button friendsTab;
    private Button requestsTab;
    private Checkbox showAllCheckbox;
    private EditBox searchBox;
    private String searchText = "";
    private boolean draggingScrollbar;

    public SteamFriends26Screen(Screen parent) {
        super(Component.translatable("text.e4steam_minecraft.friends.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        open = true;
        generation = requestGate.open();
        int availableContentHeight = Math.max(FRIEND_CONTROLS_HEIGHT + ROW_HEIGHT, height - 80);
        int completeFriendRows = Math.max(1,
                (availableContentHeight - FRIEND_CONTROLS_HEIGHT) / ROW_HEIGHT);
        contentHeight = FRIEND_CONTROLS_HEIGHT + completeFriendRows * ROW_HEIGHT;
        panelHeight = contentHeight + BORDER * 2 + 1;
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = Math.max(8, (height - panelHeight) / 2);
        contentLeft = panelLeft + BORDER;
        contentTop = panelTop + BORDER;
        visibleRows = rowCapacity();

        friendsTab = addRenderableWidget(new FriendsUi26TabButton(
                contentLeft, contentTop - 27, 90,
                Component.translatable("text.e4steam_minecraft.friends.tab"),
                () -> select(Tab.FRIENDS), () -> selectedTab == Tab.FRIENDS
        ));
        requestsTab = addRenderableWidget(new FriendsUi26TabButton(
                contentLeft + 90, contentTop - 27, 90,
                requestsTitle(), () -> select(Tab.REQUESTS), () -> selectedTab == Tab.REQUESTS
        ));
        searchBox = addRenderableWidget(new EditBox(
                font, contentLeft + 4, contentTop + 3, CONTENT_WIDTH - 8, 20,
                Component.translatable("text.e4steam_minecraft.friends.search")
        ));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.translatable("text.e4steam_minecraft.friends.search"));
        searchBox.setValue(searchText);
        searchBox.setResponder(value -> {
            searchText = value;
            scrollIndex = 0;
            updateWidgets();
        });
        showAllCheckbox = addRenderableWidget(Checkbox.builder(
                        Component.translatable("text.e4steam_minecraft.friends.filter.all"), font)
                .pos(contentLeft + 4, contentTop + 27)
                .selected(showAllFriends)
                .maxWidth(CONTENT_WIDTH - 8)
                .tooltip(Tooltip.create(Component.translatable(
                        "text.e4steam_minecraft.friends.filter.all.help")))
                .onValueChange((checkbox, selected) -> {
                    showAllFriends = selected;
                    scrollIndex = 0;
                    updateWidgets();
                })
                .build());
        inviteRowActions.clear();
        requestJoinRowActions.clear();
        joinRowActions.clear();
        rejectRowActions.clear();
        int widgetRowCapacity = Math.max(1, contentHeight / ROW_HEIGHT);
        for (int row = 0; row < widgetRowCapacity; row++) {
            int selectedRow = row;
            int actionY = rowsTop() + row * ROW_HEIGHT + 4;
            inviteRowActions.add(addRenderableWidget(FriendsUi26Widgets.iconButton(
                    Component.translatable("text.e4steam_minecraft.friends.invite.help"),
                    input -> activateRow(selectedRow),
                    actionButtonLeft(), actionY, 20, 20, "minecraft:friends/send_request", 15, 15
            )));
            requestJoinRowActions.add(addRenderableWidget(FriendsUi26Widgets.iconButton(
                    Component.translatable("text.e4steam_minecraft.friends.join_request.help"),
                    input -> activateRow(selectedRow),
                    actionButtonLeft(), actionY, 20, 20, "minecraft:friends/send_request", 15, 15
            )));
            joinRowActions.add(addRenderableWidget(FriendsUi26Widgets.iconButton(
                    Component.translatable("text.e4steam_minecraft.friends.join.help"),
                    input -> activateRow(selectedRow),
                    actionButtonLeft(), actionY, 20, 20, "minecraft:friends/accept", 18, 18
            )));
            rejectRowActions.add(addRenderableWidget(FriendsUi26Widgets.iconButton(
                    Component.translatable("text.e4steam_minecraft.friends.request.reject.help"),
                    input -> rejectInvitationRow(selectedRow),
                    actionButtonLeft(), actionY, 20, 20, "minecraft:friends/reject", 18, 18
            )));
        }
        updateWidgets();
        refreshNow();
    }

    @Override
    public void tick() {
        if (open && !operationInProgress && ++refreshTicks >= REFRESH_INTERVAL_TICKS) {
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
        for (AvatarTexture texture : avatars.values()) {
            minecraft.getTextureManager().release(texture.location());
        }
        avatars.clear();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (inside(mouseX, mouseY, contentLeft, contentTop - 27, 90, 20)) {
            select(Tab.FRIENDS);
            return true;
        }
        if (inside(mouseX, mouseY, contentLeft + 90, contentTop - 27, 90, 20)) {
            select(Tab.REQUESTS);
            return true;
        }
        if (isOverScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingScrollbar) {
            updateScrollFromMouse(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (inside(mouseX, mouseY, contentLeft, contentTop, CONTENT_WIDTH, contentHeight)
                && verticalAmount != 0.0 && entries().size() > visibleRows) {
            int next = scrollIndex + (verticalAmount > 0.0 ? -1 : 1);
            int clamped = Math.max(0, Math.min(next, maxScrollIndex()));
            if (clamped != scrollIndex) {
                scrollIndex = clamped;
                updateWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        if (entries().size() <= visibleRows) return false;
        int trackX = contentLeft + CONTENT_WIDTH - SCROLLBAR_WIDTH;
        return inside(mouseX, mouseY, trackX, rowsTop(), SCROLLBAR_WIDTH, visibleRows * ROW_HEIGHT);
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

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (parent != null) {
            parent.extractBackground(graphics, mouseX, mouseY, partialTick);
            graphics.nextStratum();
            parent.extractRenderState(graphics, -1, -1, partialTick);
            graphics.nextStratum();
        } else {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
        }
        graphics.fill(0, 0, width, height, 0x77000000);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND,
                panelLeft, panelTop, PANEL_WIDTH, panelHeight);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (selectedTab == Tab.FRIENDS) {
            renderFriends(graphics);
        } else {
            renderRequests(graphics);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderFriends(GuiGraphicsExtractor graphics) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SEPARATOR,
                contentLeft, rowsTop() - 2, CONTENT_WIDTH, 2);
        List<SteamSocialSnapshot.Friend> friends = visibleFriends();
        int start = scrollIndex;
        if (friends.isEmpty()) {
            String emptyKey = emptyFriendsStatusKey();
            int listHeight = contentHeight - (rowsTop() - contentTop);
            if (emptyKey.endsWith(".empty") && listHeight >= 66) {
                int imageY = rowsTop() + Math.max(0, (listHeight - 66) / 2);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY_ILLUSTRATION,
                        contentLeft + (CONTENT_WIDTH - 128) / 2, imageY, 128, 48);
                graphics.centeredText(font, Component.translatable(emptyKey),
                        contentLeft + CONTENT_WIDTH / 2, imageY + 53, 0xffa0a0a0);
            } else {
                graphics.centeredText(font, Component.translatable(emptyKey),
                        contentLeft + CONTENT_WIDTH / 2, rowsTop() + listHeight / 2 - 4, 0xffa0a0a0);
            }
        }
        for (int row = 0; row < visibleRows && start + row < friends.size(); row++) {
            SteamSocialSnapshot.Friend friend = friends.get(start + row);
            int y = rowsTop() + row * ROW_HEIGHT;
            avatar(graphics, friend.steamId(), friend.avatar(), contentLeft + 4, y + 2, 24);
            graphics.text(font, Component.literal(shortName(friend.name(), 15)), contentLeft + 32, y + 3, 0xffffffff, false);
            graphics.text(font, friendStatus(friend), contentLeft + 32, y + 15, friendStatusColor(friend), false);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SEPARATOR,
                    contentLeft, y + ROW_HEIGHT - 2, CONTENT_WIDTH, 2);
        }
        renderScrollbar(graphics, friends.size());
    }

    private void renderRequests(GuiGraphicsExtractor graphics) {
        graphics.centeredText(font,
                Component.translatable("text.e4steam_minecraft.friends.requests.received.heading")
                        .withStyle(style -> style.withUnderlined(true)),
                contentLeft + CONTENT_WIDTH / 2, contentTop + 7, 0xffffffff);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SEPARATOR,
                contentLeft, rowsTop() - 2, CONTENT_WIDTH, 2);
        List<SteamSocialSnapshot.Invitation> invitations = visibleInvitations();
        int start = scrollIndex;
        if (invitations.isEmpty()) {
            graphics.centeredText(font, Component.translatable("text.e4steam_minecraft.friends.requests.empty"),
                    contentLeft + CONTENT_WIDTH / 2, rowsTop() + (contentHeight - REQUESTS_HEADER_HEIGHT) / 2 - 4,
                    0xffa0a0a0);
        }
        for (int row = 0; row < visibleRows && start + row < invitations.size(); row++) {
            SteamSocialSnapshot.Invitation invitation = invitations.get(start + row);
            int y = rowsTop() + row * ROW_HEIGHT;
            SteamSocialSnapshot.Friend friend = findFriend(invitation.steamId());
            avatar(graphics, invitation.steamId(),
                    friend == null ? SteamSocialSnapshot.Avatar.empty() : friend.avatar(),
                    contentLeft + 4, y + 2, 24);
            graphics.text(font, Component.literal(shortName(invitation.friendName(), 15)), contentLeft + 32, y + 3, 0xffffffff, false);
            boolean received = invitation.actionable(System.currentTimeMillis());
            graphics.text(font, invitationStatus(invitation),
                    contentLeft + 32, y + 15, received ? 0xff55ff55 : 0xffaaaaaa, false);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SEPARATOR,
                    contentLeft, y + ROW_HEIGHT - 2, CONTENT_WIDTH, 2);
        }
        renderScrollbar(graphics, invitations.size());
    }

    private void renderScrollbar(GuiGraphicsExtractor graphics, int entryCount) {
        if (entryCount <= visibleRows) return;
        int trackX = contentLeft + CONTENT_WIDTH - SCROLLBAR_WIDTH;
        int trackY = rowsTop();
        int trackHeight = visibleRows * ROW_HEIGHT;
        int thumbHeight = scrollbarThumbHeight(entryCount, trackHeight);
        int travel = trackHeight - thumbHeight;
        int thumbY = trackY + (maxScrollIndex() == 0 ? 0 : travel * scrollIndex / maxScrollIndex());
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_BACKGROUND,
                trackX, trackY, SCROLLBAR_WIDTH, trackHeight);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER,
                trackX, thumbY, SCROLLBAR_WIDTH, thumbHeight);
    }

    private static int scrollbarThumbHeight(int entryCount, int trackHeight) {
        int contentPixelHeight = entryCount * ROW_HEIGHT;
        int maximum = Math.max(8, trackHeight - 8);
        int minimum = Math.min(SCROLLBAR_MIN_HEIGHT, maximum);
        return Math.max(minimum,
                Math.min(maximum, trackHeight * trackHeight / contentPixelHeight));
    }

    private static void fallbackFace(GuiGraphicsExtractor graphics, int x, int y, int size, long seed) {
        int skin = (seed & 1L) == 0L ? 0xffb8794f : 0xff9d633f;
        int hair = (seed & 2L) == 0L ? 0xff4b2a1b : 0xff2f2018;
        graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xff111111);
        graphics.fill(x, y, x + size, y + size, skin);
        graphics.fill(x, y, x + size, y + Math.max(4, size / 4), hair);
        int eyeY = y + size / 2;
        int eye = Math.max(2, size / 8);
        graphics.fill(x + size / 4, eyeY, x + size / 4 + eye, eyeY + eye, 0xffeeeeee);
        graphics.fill(x + size * 3 / 4 - eye, eyeY, x + size * 3 / 4, eyeY + eye, 0xffeeeeee);
    }

    private void avatar(
            GuiGraphicsExtractor graphics,
            long steamId,
            SteamSocialSnapshot.Avatar avatar,
            int x,
            int y,
            int size
    ) {
        graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xff111111);
        AvatarTexture texture = texture(steamId, avatar);
        if (texture == null) {
            fallbackFace(graphics, x, y, size, steamId);
            return;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture.location(), x, y, 0.0f, 0.0f,
                size, size,
                texture.width(), texture.height(),
                texture.width(), texture.height());
    }

    private AvatarTexture texture(long steamId, SteamSocialSnapshot.Avatar avatar) {
        if (!avatar.available()) {
            return null;
        }
        AvatarTexture existing = avatars.get(steamId);
        if (existing != null && existing.width() == avatar.width() && existing.height() == avatar.height()) {
            return existing;
        }
        if (existing != null) {
            minecraft.getTextureManager().release(existing.location());
        }
        NativeImage image = new NativeImage(avatar.width(), avatar.height(), false);
        byte[] rgba = avatar.rgba();
        int offset = 0;
        for (int imageY = 0; imageY < avatar.height(); imageY++) {
            for (int imageX = 0; imageX < avatar.width(); imageX++) {
                int red = rgba[offset++] & 0xff;
                int green = rgba[offset++] & 0xff;
                int blue = rgba[offset++] & 0xff;
                int alpha = rgba[offset++] & 0xff;
                image.setPixelABGR(imageX, imageY, alpha << 24 | blue << 16 | green << 8 | red);
            }
        }
        Identifier location = Identifier.fromNamespaceAndPath(
                "e4steam_minecraft",
                "steam_avatar/" + Long.toUnsignedString(steamId)
        );
        DynamicTexture dynamic = new DynamicTexture(
                () -> "e4steam Steam avatar " + Long.toUnsignedString(steamId),
                image
        );
        minecraft.getTextureManager().register(location, dynamic);
        AvatarTexture created = new AvatarTexture(location, avatar.width(), avatar.height());
        avatars.put(steamId, created);
        return created;
    }

    private void select(Tab tab) {
        selectedTab = tab;
        scrollIndex = 0;
        updateWidgets();
    }

    private void refreshNow() {
        int requestedGeneration = requestGate.tryBegin();
        if (requestedGeneration < 0) return;
        boolean initialRefresh = !loadedOnce;
        if (initialRefresh) {
            operationInProgress = true;
            statusKey = "text.e4steam_minecraft.friends.loading";
        }
        refreshTicks = 0;
        try {
            ensureActivity();
            SteamRuntime.get().socialSnapshotAsync().whenComplete((loaded, failure) ->
                    Minecraft.getInstance().execute(() ->
                            finishRefresh(requestedGeneration, loaded, failure, initialRefresh))
            );
        } catch (Throwable failure) {
            Minecraft.getInstance().execute(() ->
                    finishRefresh(requestedGeneration, null, failure, initialRefresh));
        }
    }

    private void finishRefresh(
            int requestedGeneration,
            SteamSocialSnapshot loaded,
            Throwable failure,
            boolean initialRefresh
    ) {
        if (!requestGate.finish(requestedGeneration)) return;
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
            showNewInvitationToasts();
            statusKey = snapshot.friends().isEmpty()
                    ? "text.e4steam_minecraft.friends.empty"
                    : "text.e4steam_minecraft.friends.ready";
            scrollIndex = Math.min(scrollIndex, maxScrollIndex());
        }
        updateWidgets();
    }

    private void ensureActivity() {
        synchronized (activityLock) {
            if (activity == null) activity = SteamRuntime.get().acquireActivity();
        }
    }

    private void activateRow(int row) {
        int index = scrollIndex + row;
        if (selectedTab == Tab.REQUESTS) {
            List<SteamSocialSnapshot.Invitation> invitations = visibleInvitations();
            if (index < invitations.size()) {
                SteamSocialSnapshot.Invitation invitation = invitations.get(index);
                if (invitation.actionable(System.currentTimeMillis())) {
                    if (invitation.direction() == SteamSocialSnapshot.Direction.JOIN_REQUEST_RECEIVED) {
                        runOperation(
                                Operation.APPROVE_JOIN_REQUEST,
                                invitation.steamId(),
                                invitation.expiresAtMillis()
                        );
                    } else {
                        runOperation(Operation.JOIN_INVITATION, invitation.steamId(), invitation.lobbyId());
                    }
                }
            }
            return;
        }
        List<SteamSocialSnapshot.Friend> friends = visibleFriends();
        if (index >= friends.size()) return;
        SteamSocialSnapshot.Friend friend = friends.get(index);
        if (SteamRuntime.get().isPeerConnected(friend.steamId())) return;
        SteamSession session = E4steamClient.session;
        if (session != null && session.state == SteamSession.State.STARTED
                && friend.presence() != SteamSocialSnapshot.Presence.OFFLINE) {
            runOperation(Operation.INVITE, friend.steamId());
        } else if (friend.joinable() && friend.compatible()) {
            runOperation(Operation.JOIN, friend.steamId());
        } else if (friend.hosting() && friend.compatible()) {
            runOperation(Operation.REQUEST_JOIN, friend.steamId());
        }
    }

    private void rejectInvitationRow(int row) {
        if (selectedTab != Tab.REQUESTS) return;
        int index = scrollIndex + row;
        List<SteamSocialSnapshot.Invitation> invitations = visibleInvitations();
        if (index >= invitations.size()) return;
        SteamSocialSnapshot.Invitation invitation = invitations.get(index);
        if (invitation.actionable(System.currentTimeMillis())) {
            if (invitation.direction() == SteamSocialSnapshot.Direction.JOIN_REQUEST_RECEIVED) {
                runOperation(
                        Operation.DISMISS_JOIN_REQUEST,
                        invitation.steamId(),
                        invitation.expiresAtMillis()
                );
            } else {
                runOperation(Operation.DISMISS_INVITATION, invitation.steamId(), invitation.lobbyId());
            }
        }
    }

    private void runOperation(Operation operation, long steamId) {
        runOperation(operation, steamId, 0L);
    }

    private void runOperation(Operation operation, long steamId, long lobbyId) {
        int requestedGeneration = requestGate.tryBegin();
        if (requestedGeneration < 0) return;
        operationInProgress = true;
        statusKey = "text.e4steam_minecraft.friends.working";
        try {
            ensureActivity();
            SteamRuntime runtime = SteamRuntime.get();
            CompletableFuture<Boolean> task = switch (operation) {
                case JOIN -> runtime.joinFriendAsync(steamId);
                case JOIN_INVITATION -> runtime.joinInvitationAsync(lobbyId, steamId);
                case DISMISS_INVITATION -> runtime.dismissInvitationAsync(lobbyId);
                case REQUEST_JOIN -> runtime.requestToJoinAsync(steamId);
                case DISMISS_JOIN_REQUEST -> runtime.dismissJoinRequestAsync(steamId, lobbyId);
                case APPROVE_JOIN_REQUEST -> {
                    SteamSession session = E4steamClient.session;
                    yield session == null
                            ? CompletableFuture.completedFuture(false)
                            : runtime.approveJoinRequestAsync(session, steamId, lobbyId);
                }
                case INVITE -> {
                    SteamSession session = E4steamClient.session;
                    yield session == null
                            ? CompletableFuture.completedFuture(false)
                            : runtime.inviteFriendAsync(session, steamId);
                }
            };
            task.whenComplete((succeeded, failure) -> Minecraft.getInstance().execute(() ->
                    finishOperation(requestedGeneration, operation, Boolean.TRUE.equals(succeeded), failure)
            ));
        } catch (Throwable failure) {
            Minecraft.getInstance().execute(() ->
                    finishOperation(requestedGeneration, operation, false, failure)
            );
        }
    }

    private void finishOperation(int requestedGeneration, Operation operation, boolean succeeded, Throwable failure) {
        if (!requestGate.finish(requestedGeneration)) return;
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
        } else if (operation == Operation.DISMISS_INVITATION) {
            refreshNow();
            return;
        } else if (operation == Operation.DISMISS_JOIN_REQUEST
                || operation == Operation.APPROVE_JOIN_REQUEST) {
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
    }

    private void updateWidgets() {
        if (friendsTab == null) return;
        visibleRows = rowCapacity();
        friendsTab.active = !operationInProgress;
        requestsTab.active = !operationInProgress;
        requestsTab.setMessage(requestsTitle());
        searchBox.visible = selectedTab == Tab.FRIENDS;
        searchBox.active = !operationInProgress;
        showAllCheckbox.visible = selectedTab == Tab.FRIENDS;
        showAllCheckbox.active = !operationInProgress;
        scrollIndex = Math.min(scrollIndex, maxScrollIndex());
        List<?> entries = entries();
        int start = scrollIndex;
        List<SteamSocialSnapshot.Friend> friends = selectedTab == Tab.FRIENDS ? visibleFriends() : List.of();
        for (int row = 0; row < inviteRowActions.size(); row++) {
            int index = start + row;
            int actionY = rowsTop() + row * ROW_HEIGHT + 4;
            inviteRowActions.get(row).setY(actionY);
            requestJoinRowActions.get(row).setY(actionY);
            joinRowActions.get(row).setY(actionY);
            rejectRowActions.get(row).setY(actionY);
            inviteRowActions.get(row).visible = false;
            requestJoinRowActions.get(row).visible = false;
            joinRowActions.get(row).visible = false;
            rejectRowActions.get(row).visible = false;
            if (row >= visibleRows || index >= entries.size()) continue;
            Button action;
            if (selectedTab == Tab.REQUESTS) {
                SteamSocialSnapshot.Invitation invitation = visibleInvitations().get(index);
                boolean actionable = invitation.actionable(System.currentTimeMillis());
                if (!actionable) continue;
                action = joinRowActions.get(row);
                action.setX(acceptButtonLeft());
                Button reject = rejectRowActions.get(row);
                reject.visible = true;
                reject.active = !operationInProgress;
            } else {
                SteamSocialSnapshot.Friend friend = friends.get(index);
                if (SteamRuntime.get().isPeerConnected(friend.steamId())) continue;
                SteamSession session = E4steamClient.session;
                if (session != null && session.state == SteamSession.State.STARTED
                        && friend.presence() != SteamSocialSnapshot.Presence.OFFLINE) {
                    action = inviteRowActions.get(row);
                } else if (friend.joinable() && friend.compatible()) {
                    action = joinRowActions.get(row);
                } else if (friend.hosting() && friend.compatible()) {
                    action = requestJoinRowActions.get(row);
                } else {
                    continue;
                }
                action.setX(actionButtonLeft());
            }
            action.visible = true;
            action.active = !operationInProgress;
        }
    }

    private Component friendStatus(SteamSocialSnapshot.Friend friend) {
        if (SteamRuntime.get().isPeerConnected(friend.steamId())) {
            return Component.translatable("text.e4steam_minecraft.friends.status.connected");
        }
        if (friend.hosting()) {
            return Component.translatable(
                    "text.e4steam_minecraft.friends.status.minecraft.world.version",
                    friend.minecraftVersion()
            );
        }
        if (friend.playingMinecraft()) {
            return Component.translatable("text.e4steam_minecraft.friends.status.minecraft.online.version",
                    friend.minecraftVersion());
        }
        if (friend.playingSpacewar()) {
            return Component.translatable("text.e4steam_minecraft.friends.status.spacewar");
        }
        return Component.translatable(switch (friend.presence()) {
            case OFFLINE -> "text.e4steam_minecraft.friends.status.offline";
            case AWAY -> "text.e4steam_minecraft.friends.status.away";
            case BUSY -> "text.e4steam_minecraft.friends.status.busy";
            case ONLINE -> "text.e4steam_minecraft.friends.status.online";
        });
    }

    private SteamSocialSnapshot.Friend findFriend(long steamId) {
        for (SteamSocialSnapshot.Friend friend : snapshot.friends()) {
            if (friend.steamId() == steamId) {
                return friend;
            }
        }
        return null;
    }

    private static String shortName(String value, int maxCharacters) {
        if (value == null || value.length() <= maxCharacters) return value == null ? "" : value;
        return value.substring(0, Math.max(1, maxCharacters - 1)) + "…";
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

    private List<?> entries() {
        return selectedTab == Tab.FRIENDS ? visibleFriends() : visibleInvitations();
    }

    private List<SteamSocialSnapshot.Invitation> visibleInvitations() {
        long now = System.currentTimeMillis();
        return snapshot.invitations().stream()
                .filter(invitation -> invitation.direction() == SteamSocialSnapshot.Direction.RECEIVED
                        || invitation.direction() == SteamSocialSnapshot.Direction.JOIN_REQUEST_RECEIVED)
                .filter(invitation -> !invitation.canceled() && now < invitation.expiresAtMillis())
                .toList();
    }

    private List<SteamSocialSnapshot.Friend> visibleFriends() {
        List<SteamSocialSnapshot.Friend> base = showAllFriends
                ? snapshot.friends()
                : SteamSocialSnapshot.minecraftFriends(snapshot.friends());
        return SteamSocialSnapshot.filterByName(base, searchText);
    }

    private String emptyFriendsStatusKey() {
        if (!"text.e4steam_minecraft.friends.ready".equals(statusKey)
                && !"text.e4steam_minecraft.friends.empty".equals(statusKey)) {
            return statusKey;
        }
        if (!searchText.isBlank()) {
            return "text.e4steam_minecraft.friends.search.empty";
        }
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
        return Component.translatable("text.e4steam_minecraft.friends.requests.tab", received);
    }

    private void showNewInvitationToasts() {
        long now = System.currentTimeMillis();
        for (SteamSocialSnapshot.Invitation invitation : snapshot.invitations()) {
            if (!invitation.actionable(now)) continue;
            SteamSocialSnapshot.Friend friend = findFriend(invitation.steamId());
            String minecraftName = friend == null ? "" : friend.minecraftName();
            if (invitation.direction() == SteamSocialSnapshot.Direction.JOIN_REQUEST_RECEIVED) {
                showJoinRequestToast(
                        invitation.steamId(),
                        invitation.friendName(),
                        minecraftName,
                        invitation.expiresAtMillis()
                );
            } else {
                showInvitationToast(
                        invitation.lobbyId(),
                        invitation.steamId(),
                        invitation.friendName(),
                        minecraftName,
                        invitation.expiresAtMillis()
                );
            }
        }
    }

    public static void showJoinRequestToast(
            long friendSteamId,
            String friendName,
            String minecraftName,
            long requestGeneration
    ) {
        if (friendSteamId == 0 || requestGeneration <= 0) return;
        Long previousGeneration = TOASTED_JOIN_REQUEST_GENERATIONS.get(friendSteamId);
        if (previousGeneration != null && requestGeneration <= previousGeneration) return;
        TOASTED_JOIN_REQUEST_GENERATIONS.put(friendSteamId, requestGeneration);
        Minecraft minecraft = Minecraft.getInstance();
        FriendToast.add(
                minecraft.gui.toastManager(),
                minecraft.font,
                resolvableProfile(minecraftName),
                Component.translatable("text.e4steam_minecraft.friends.join_request.toast", friendName)
        );
    }

    /** Called on Minecraft's client thread by the version-neutral Steam event bridge. */
    public static void showInvitationToast(
            long lobbyId,
            long friendSteamId,
            String friendName,
            String minecraftName,
            long invitationGeneration
    ) {
        if (lobbyId == 0 || invitationGeneration <= 0) return;
        Long previousGeneration = TOASTED_INVITATION_GENERATIONS.get(lobbyId);
        if (previousGeneration != null && invitationGeneration <= previousGeneration) return;
        TOASTED_INVITATION_GENERATIONS.put(lobbyId, invitationGeneration);
        Minecraft minecraft = Minecraft.getInstance();
        FriendToast.add(
                minecraft.gui.toastManager(),
                minecraft.font,
                resolvableProfile(minecraftName),
                Component.translatable("text.e4steam_minecraft.friends.invite.toast", friendName)
        );
    }

    private static ResolvableProfile resolvableProfile(String minecraftName) {
        if (minecraftName == null || minecraftName.isBlank()) return null;
        try {
            return ResolvableProfile.createUnresolved(minecraftName);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Component invitationStatus(SteamSocialSnapshot.Invitation invitation) {
        if (invitation.canceled()) {
            return Component.translatable("text.e4steam_minecraft.friends.request.canceled");
        }
        if (System.currentTimeMillis() >= invitation.expiresAtMillis()) {
            return Component.translatable("text.e4steam_minecraft.friends.request.expired");
        }
        return Component.translatable(switch (invitation.direction()) {
            case RECEIVED -> "text.e4steam_minecraft.friends.request.received";
            case SENT -> "text.e4steam_minecraft.friends.request.sent";
            case JOIN_REQUEST_RECEIVED -> "text.e4steam_minecraft.friends.join_request.received";
            case JOIN_REQUEST_SENT -> "text.e4steam_minecraft.friends.join_request.sent";
        });
    }

    private int actionButtonLeft() {
        return contentLeft + CONTENT_WIDTH - SCROLLBAR_WIDTH - 24;
    }

    private int acceptButtonLeft() {
        return actionButtonLeft() - 22;
    }

    private int rowsTop() {
        return contentTop + (selectedTab == Tab.FRIENDS ? FRIEND_CONTROLS_HEIGHT : REQUESTS_HEADER_HEIGHT);
    }

    private int rowCapacity() {
        int reserved = selectedTab == Tab.FRIENDS ? FRIEND_CONTROLS_HEIGHT : REQUESTS_HEADER_HEIGHT;
        return Math.max(1, (contentHeight - reserved) / ROW_HEIGHT);
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private enum Tab { FRIENDS, REQUESTS }

    private enum Operation {
        INVITE,
        JOIN,
        JOIN_INVITATION,
        DISMISS_INVITATION,
        REQUEST_JOIN,
        APPROVE_JOIN_REQUEST,
        DISMISS_JOIN_REQUEST
    }

    private record AvatarTexture(Identifier location, int width, int height) {
    }
}
