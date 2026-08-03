package link.e4steam;

import link.e4steam.steam.SteamRuntime;
import link.e4steam.steam.SteamSession;
import link.e4steam.steam.SteamSocialSnapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Version-neutral state and layout for the snapshot-style Steam friends overlay. */
public abstract class SteamFriendsScreenBase extends Screen {
    protected static final int CONTENT_WIDTH = 180;
    protected static final int PANEL_WIDTH = 196;
    private static final int BORDER = 8;
    private static final int MAX_ROWS = 6;
    private static final int ROW_HEIGHT = 32;
    private static final int REFRESH_INTERVAL_TICKS = 20 * 10;
    private static final ResourceLocation BACKGROUND = new ResourceLocation(
            "e4steam_minecraft",
            "textures/gui/sprites/friends/background.png"
    );
    private static final ResourceLocation LIST_SEPARATOR = new ResourceLocation(
            "e4steam_minecraft",
            "textures/gui/sprites/friends/list_separator_top.png"
    );

    protected final Screen parent;
    protected int panelLeft;
    protected int panelTop;
    protected int panelHeight;
    protected int contentLeft;
    protected int contentTop;
    protected int contentHeight;

    private final Object activityLock = new Object();
    private final FriendsUiRequestGate requestGate = new FriendsUiRequestGate();
    private final Button[] rowActions = new Button[MAX_ROWS];
    private volatile SteamRuntime.Activity activity;
    private volatile boolean open;
    private volatile boolean operationInProgress;
    private SteamSocialSnapshot snapshot = SteamSocialSnapshot.empty();
    private Tab selectedTab = Tab.FRIENDS;
    private String statusKey = "text.e4steam_minecraft.friends.loading";
    private boolean loadedOnce;
    private int page;
    private int refreshTicks;
    private int generation;
    private int visibleRows;
    private Button friendsTab;
    private Button requestsTab;
    private Button steamButton;
    private Button refreshButton;
    private Button previousButton;
    private Button nextButton;
    private Button closeButton;

    protected SteamFriendsScreenBase(Screen parent) {
        super(Mirror.translatable("text.e4steam_minecraft.friends.title"));
        this.parent = parent;
    }

    @Override
    protected final void init() {
        open = true;
        generation = requestGate.open();
        contentHeight = Math.max(118, Math.min(220, height - 80));
        panelHeight = contentHeight + BORDER * 2 + 1;
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = Math.max(8, (height - panelHeight) / 2);
        contentLeft = panelLeft + BORDER;
        contentTop = panelTop + BORDER;
        visibleRows = Math.max(1, Math.min(MAX_ROWS, (contentHeight - 72) / ROW_HEIGHT));

        int tabWidth = CONTENT_WIDTH / 2;
        friendsTab = addRenderableWidget(MinecraftUiCompat.button(
                Mirror.translatable("text.e4steam_minecraft.friends.tab"),
                ignored -> select(Tab.FRIENDS),
                contentLeft,
                contentTop - 27,
                tabWidth,
                20
        ));
        requestsTab = addRenderableWidget(MinecraftUiCompat.button(
                requestsTitle(),
                ignored -> select(Tab.REQUESTS),
                contentLeft + tabWidth,
                contentTop - 27,
                CONTENT_WIDTH - tabWidth,
                20
        ));

        steamButton = addRenderableWidget(MinecraftUiCompat.button(
                FriendsUiIcons.invite(),
                ignored -> runOperation(Operation.OVERLAY, 0),
                actionButtonLeft(),
                contentTop + 8,
                20,
                20
        ));
        MinecraftUiCompat.tooltip(
                steamButton,
                Mirror.translatable("text.e4steam_minecraft.friends.steam.help")
        );
        refreshButton = addRenderableWidget(MinecraftUiCompat.button(
                FriendsUiIcons.refresh(),
                ignored -> refreshNow(),
                actionButtonLeft() - 23,
                contentTop + 8,
                20,
                20
        ));
        MinecraftUiCompat.tooltip(
                refreshButton,
                Mirror.translatable("text.e4steam_minecraft.friends.refresh.help")
        );

        for (int row = 0; row < MAX_ROWS; row++) {
            final int selectedRow = row;
            rowActions[row] = addRenderableWidget(MinecraftUiCompat.button(
                    FriendsUiIcons.profile(),
                    ignored -> activateRow(selectedRow),
                    actionButtonLeft(),
                    rowsTop() + row * ROW_HEIGHT + 4,
                    20,
                    20
            ));
        }

        previousButton = addRenderableWidget(MinecraftUiCompat.button(
                Mirror.literal("<"),
                ignored -> changePage(-1),
                contentLeft + 8,
                contentTop + contentHeight - 25,
                20,
                20
        ));
        nextButton = addRenderableWidget(MinecraftUiCompat.button(
                Mirror.literal(">"),
                ignored -> changePage(1),
                contentLeft + CONTENT_WIDTH - 28,
                contentTop + contentHeight - 25,
                20,
                20
        ));
        closeButton = addRenderableWidget(MinecraftUiCompat.button(
                Mirror.translatable("text.e4steam_minecraft.friends.close"),
                ignored -> onClose(),
                contentLeft + (CONTENT_WIDTH - 80) / 2,
                contentTop + contentHeight - 25,
                80,
                20
        ));

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
        if (inside(mouseX, mouseY, contentLeft, contentTop - 27, CONTENT_WIDTH / 2, 20)) {
            select(Tab.FRIENDS);
            return true;
        }
        if (inside(mouseX, mouseY, contentLeft + CONTENT_WIDTH / 2, contentTop - 27, CONTENT_WIDTH / 2, 20)) {
            select(Tab.REQUESTS);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    protected final void renderPanel(Painter painter, int mouseX, int mouseY) {
        painter.fill(0, 0, width, height, 0x77000000);
        drawPanelBackground(painter);
        int underlineX = selectedTab == Tab.FRIENDS ? contentLeft + 4 : contentLeft + CONTENT_WIDTH / 2 + 4;
        painter.fill(underlineX, contentTop - 9, underlineX + CONTENT_WIDTH / 2 - 8, contentTop - 7, 0xffffffff);

        if (selectedTab == Tab.FRIENDS) {
            renderFriends(painter);
        } else {
            renderRequests(painter);
        }
    }

    protected final void renderButtonAvatars(Painter painter) {
        // Button icons are rendered by the version-specific widget implementation.
    }

    protected void releaseRenderResources() {
    }

    private void renderFriends(Painter painter) {
        painter.avatar(0L, snapshot.localAvatar(), contentLeft + 8, contentTop + 6, 24);
        String localName = snapshot.localPersonaName().isBlank() ? "..." : snapshot.localPersonaName();
        painter.text(Mirror.literal(shortName(localName, 11)), contentLeft + 40, contentTop + 7, 0xffffffff);
        painter.text(Mirror.translatable("text.e4steam_minecraft.friends.status.steam"),
                contentLeft + 40, contentTop + 19, 0xffa0a0a0);
        drawSeparator(painter, contentTop + 38);

        List<SteamSocialSnapshot.Friend> friends = snapshot.friends();
        int start = page * visibleRows;
        if (friends.isEmpty()) {
            painter.centered(Mirror.translatable(statusKey), contentLeft + CONTENT_WIDTH / 2, rowsTop() + 18, 0xffa0a0a0);
        }
        for (int row = 0; row < visibleRows && start + row < friends.size(); row++) {
            SteamSocialSnapshot.Friend friend = friends.get(start + row);
            int y = rowsTop() + row * ROW_HEIGHT;
            painter.avatar(friend.steamId(), friend.avatar(), contentLeft + 8, y + 2, 24);
            painter.text(Mirror.literal(shortName(friend.name(), 16)), contentLeft + 40, y + 3, 0xffffffff);
            painter.text(friendStatus(friend), contentLeft + 40, y + 15, 0xffa0a0a0);
        }
    }

    private void renderRequests(Painter painter) {
        painter.centered(
                Mirror.translatable("text.e4steam_minecraft.friends.requests.heading"),
                contentLeft + CONTENT_WIDTH / 2,
                contentTop + 14,
                0xffffffff
        );
        drawSeparator(painter, contentTop + 38);
        List<SteamSocialSnapshot.Invitation> invitations = snapshot.invitations();
        int start = page * visibleRows;
        if (invitations.isEmpty()) {
            painter.centered(
                    Mirror.translatable("text.e4steam_minecraft.friends.requests.empty"),
                    contentLeft + CONTENT_WIDTH / 2,
                    rowsTop() + 18,
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
                    contentLeft + 8,
                    y + 2,
                    24
            );
            painter.text(Mirror.literal(invitation.friendName()), contentLeft + 40, y + 3, 0xffffffff);
            painter.text(
                    invitationStatus(invitation),
                    contentLeft + 40,
                    y + 15,
                    invitation.actionable(System.currentTimeMillis())
                            ? 0xff55ff55
                            : 0xffaaaaaa
            );
        }
    }

    private int actionButtonLeft() {
        return contentLeft + CONTENT_WIDTH - 28;
    }

    private int rowsTop() {
        return contentTop + 44;
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

    private void select(Tab tab) {
        selectedTab = tab;
        page = 0;
        updateWidgets();
    }

    private void changePage(int amount) {
        page = Math.max(0, Math.min(page + amount, pageCount() - 1));
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
                page = Math.min(page, pageCount() - 1);
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
        int index = page * visibleRows + row;
        if (selectedTab == Tab.REQUESTS) {
            if (index < snapshot.invitations().size()) {
                SteamSocialSnapshot.Invitation invitation = snapshot.invitations().get(index);
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
        List<SteamSocialSnapshot.Friend> friends = snapshot.friends();
        if (index >= friends.size()) {
            return;
        }
        SteamSocialSnapshot.Friend friend = friends.get(index);
        if (SteamRuntime.get().isPeerConnected(friend.steamId())) return;
        SteamSession session = E4steamClient.session;
        if (session != null && session.state == SteamSession.State.STARTED) {
            runOperation(Operation.INVITE, friend.steamId());
        } else if (friend.joinable() && friend.compatible()) {
            runOperation(Operation.JOIN, friend.steamId());
        } else if (friend.hosting() && friend.compatible()) {
            runOperation(Operation.REQUEST_JOIN, friend.steamId());
        } else {
            runOperation(Operation.PROFILE, friend.steamId());
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
                case REQUEST_JOIN -> runtime.requestToJoinAsync(steamId);
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
            } else if (operation == Operation.APPROVE_JOIN_REQUEST) {
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
        if (friendsTab == null) {
            return;
        }
        friendsTab.active = !operationInProgress;
        requestsTab.active = !operationInProgress;
        requestsTab.setMessage(requestsTitle());
        steamButton.visible = selectedTab == Tab.FRIENDS;
        steamButton.active = !operationInProgress;
        refreshButton.active = !operationInProgress;
        List<?> entries = entries();
        int start = page * visibleRows;
        for (int row = 0; row < MAX_ROWS; row++) {
            int index = start + row;
            Button action = rowActions[row];
            action.visible = row < visibleRows && index < entries.size();
            if (!action.visible) {
                continue;
            }
            action.active = !operationInProgress;
            if (selectedTab == Tab.REQUESTS) {
                SteamSocialSnapshot.Invitation invitation = snapshot.invitations().get(index);
                boolean actionable = invitation.actionable(System.currentTimeMillis());
                action.setMessage(actionable ? FriendsUiIcons.join() : FriendsUiIcons.profile());
                MinecraftUiCompat.tooltip(action, Mirror.translatable(actionable
                        ? "text.e4steam_minecraft.friends.join.help"
                        : "text.e4steam_minecraft.friends.profile.help"));
                continue;
            }
            SteamSocialSnapshot.Friend friend = snapshot.friends().get(index);
            SteamSession session = E4steamClient.session;
            if (session != null && session.state == SteamSession.State.STARTED) {
                action.setMessage(FriendsUiIcons.invite());
                MinecraftUiCompat.tooltip(action, Mirror.translatable("text.e4steam_minecraft.friends.invite.help"));
            } else if (friend.joinable() && friend.compatible()) {
                action.setMessage(FriendsUiIcons.join());
                MinecraftUiCompat.tooltip(action, Mirror.translatable("text.e4steam_minecraft.friends.join.help"));
            } else {
                action.setMessage(FriendsUiIcons.profile());
                MinecraftUiCompat.tooltip(action, Mirror.translatable("text.e4steam_minecraft.friends.profile.help"));
            }
        }
        int pages = pageCount();
        previousButton.visible = pages > 1;
        nextButton.visible = pages > 1;
        previousButton.active = page > 0;
        nextButton.active = page + 1 < pages;
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
        if (value == null || value.length() <= maxCharacters) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(1, maxCharacters - 1)) + "…";
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

    private List<?> entries() {
        return selectedTab == Tab.FRIENDS ? snapshot.friends() : snapshot.invitations();
    }

    private int pageCount() {
        return Math.max(1, (entries().size() + visibleRows - 1) / visibleRows);
    }

    private Component requestsTitle() {
        return Mirror.translatable("text.e4steam_minecraft.friends.requests.tab", snapshot.invitations().size());
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

    private enum Operation {
        OVERLAY,
        PROFILE,
        INVITE,
        JOIN,
        JOIN_INVITATION,
        REQUEST_JOIN,
        APPROVE_JOIN_REQUEST
    }
}
