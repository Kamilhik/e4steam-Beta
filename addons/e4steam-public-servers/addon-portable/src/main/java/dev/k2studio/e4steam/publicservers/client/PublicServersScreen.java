package dev.k2studio.e4steam.publicservers.client;

import dev.k2studio.e4steam.publicservers.common.PublicJoinCoordinator;
import dev.k2studio.e4steam.publicservers.common.PublicServersSnapshot;
import dev.k2studio.e4steam.publicservers.contract.ListingMetadata;
import dev.k2studio.e4steam.publicservers.contract.PublicServerEntry;
import dev.k2studio.e4steam.publicservers.contract.PublicServerType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Catalog with native keyboard controls and a portable card renderer. */
public final class PublicServersScreen extends Screen {
    private int refreshTicks;
    private static final int MAX_ROWS = 12;
    private final Screen parent;
    private final List<Button> rowButtons = new ArrayList<>();
    private PublicServersSnapshot snapshot = PublicServersClientBridge.snapshot();
    private AutoCloseable listener = () -> { };
    private boolean open;
    private boolean compatibleOnly;
    private boolean freeSlotsOnly;
    private PublicServerType typeFilter;
    private String searchText = "";
    private int scrollIndex;
    private int visibleRows;
    private EditBox searchBox;
    private Button allButton;
    private Button worldsButton;
    private Button dedicatedButton;
    private Button compatibleButton;
    private Button slotsButton;
    private Button previousButton;
    private Button nextButton;
    private Button connectButton;
    private Component statusText = PortableComponents.literal("");
    private int contentLeft, contentWidth, listTop, rowHeight, footerTop;

    public PublicServersScreen(Screen parent) {
        super(PortableComponents.translatable("e4steam_public_servers.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        open = true;
        rowButtons.clear();
        closeListener();
        PublicServersClientBridge.setCreatorName(minecraft.getUser().getName());

        contentWidth = Math.min(620, Math.max(180, width - 24));
        int left = (width - contentWidth) / 2;
        contentLeft = left;
        addButton(PortableMinecraftUi.button(PortableComponents.translatable("e4steam_public_servers.name.open"),
                ignored -> minecraft.setScreen(new ServerNameScreen(this)), left + contentWidth - 120, 8, 120, 20));
        int smallGap = 4;
        int refreshWidth = Math.min(88, Math.max(68, contentWidth / 5));
        int searchWidth = contentWidth - refreshWidth - smallGap;

        searchBox = addRenderableWidget(new EditBox(font, left, 40, searchWidth, 20,
                PortableComponents.translatable("e4steam_public_servers.search")));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchText);
        searchBox.setSuggestion(PortableComponents.translatable(
                "e4steam_public_servers.search").getString());
        searchBox.setResponder(value -> {
            searchText = value;
            scrollIndex = 0;
            PublicServersClientBridge.search(value);
        });

        Button refresh = addButton(PortableMinecraftUi.button(
                PortableComponents.translatable("e4steam_public_servers.refresh"),
                ignored -> PublicServersClientBridge.refresh(),
                left + searchWidth + smallGap, 40, refreshWidth, 20));
        PortableMinecraftUi.tooltip(refresh, PortableComponents.translatable(
                "e4steam_public_servers.refresh.tooltip"));

        int tabWidth = contentWidth / 3;
        allButton = addButton(PortableMinecraftUi.button(PortableComponents.translatable(
                        "e4steam_public_servers.filter.all"), ignored -> selectType(null),
                left, 64, tabWidth, 20));
        worldsButton = addButton(PortableMinecraftUi.button(PortableComponents.translatable(
                        "e4steam_public_servers.filter.worlds"),
                ignored -> selectType(PublicServerType.INTEGRATED_WORLD),
                left + tabWidth, 64, tabWidth, 20));
        dedicatedButton = addButton(PortableMinecraftUi.button(PortableComponents.translatable(
                        "e4steam_public_servers.filter.dedicated"),
                ignored -> selectType(PublicServerType.DEDICATED_SERVER),
                left + tabWidth * 2, 64, contentWidth - tabWidth * 2, 20));

        int filterWidth = (contentWidth - smallGap) / 2;
        compatibleButton = addButton(PortableMinecraftUi.button(PortableComponents.literal(""),
                ignored -> {
                    compatibleOnly = !compatibleOnly;
                    scrollIndex = 0;
                    PublicServersClientBridge.compatibleOnly(compatibleOnly);
                    syncSnapshot();
                }, left, 88, filterWidth, 20));
        slotsButton = addButton(PortableMinecraftUi.button(PortableComponents.literal(""),
                ignored -> {
                    freeSlotsOnly = !freeSlotsOnly;
                    scrollIndex = 0;
                    PublicServersClientBridge.hasSlotsOnly(freeSlotsOnly);
                    syncSnapshot();
                }, left + filterWidth + smallGap, 88,
                contentWidth - filterWidth - smallGap, 20));

        footerTop = Math.max(164, height - 54);
        listTop = 114;
        rowHeight = height < 280 ? 32 : 40;
        visibleRows = Math.max(1, Math.min(MAX_ROWS, (footerTop - listTop - 18) / (rowHeight + 4)));
        for (int row = 0; row < visibleRows; row++) {
            final int rowOffset = row;
            Button button = addButton(PortableMinecraftUi.button(PortableComponents.literal(""),
                    ignored -> selectVisibleRow(rowOffset), left, listTop + row * (rowHeight + 4),
                    contentWidth, rowHeight));
            rowButtons.add(button);
        }

        int navWidth = 24;
        int connectWidth = Math.min(180, contentWidth - 126);
        int backWidth = 60;
        previousButton = addButton(PortableMinecraftUi.button(PortableComponents.literal("<"),
                ignored -> scroll(-visibleRows), left, footerTop, navWidth, 20));
        nextButton = addButton(PortableMinecraftUi.button(PortableComponents.literal(">"),
                ignored -> scroll(visibleRows), left + navWidth + smallGap, footerTop,
                navWidth, 20));
        connectButton = addButton(PortableMinecraftUi.button(PortableComponents.translatable(
                        "e4steam_public_servers.connect"), ignored -> joinSelected(),
                left + 60, footerTop, connectWidth, 20));
        addButton(PortableMinecraftUi.button(PortableComponents.translatable(
                        "e4steam_public_servers.back"), ignored -> onClose(),
                left + contentWidth - backWidth, footerTop, backWidth, 20));
        listener = PublicServersClientBridge.listen(() -> {
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> {
                if (open) syncSnapshot();
            });
        });
        PublicServersClientBridge.search(searchText);
        PublicServersClientBridge.type(typeFilter);
        PublicServersClientBridge.compatibleOnly(compatibleOnly);
        PublicServersClientBridge.hasSlotsOnly(freeSlotsOnly);
        syncSnapshot();
        PublicServersClientBridge.refresh();
    }

    @Override
    public void tick() {
        syncSnapshot();
        if (++refreshTicks >= 200) {
            refreshTicks = 0;
            if (!PublicServersClientBridge.joinSnapshot().map(PublicServersScreen::pending).orElse(false) && snapshot.state() != PublicServersSnapshot.State.LOADING) {
                PublicServersClientBridge.refresh();
            }
        }
    }

    @Override
    public void removed() {
        open = false;
        closeListener();
        super.removed();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private Button addButton(Button button) {
        return addRenderableWidget(button);
    }

    private void selectType(PublicServerType type) {
        if (typeFilter == type) return;
        typeFilter = type;
        scrollIndex = 0;
        PublicServersClientBridge.type(type);
        syncSnapshot();
    }

    private void selectVisibleRow(int row) {
        int index = scrollIndex + row;
        if (index < 0 || index >= snapshot.items().size()) return;
        PublicServerEntry entry = snapshot.items().get(index).entry();
        PublicServersClientBridge.select(entry.listingId());
        syncSnapshot();
    }

    private void scroll(int amount) {
        scrollIndex = Math.max(0, Math.min(maxScrollIndex(), scrollIndex + amount));
        syncSnapshot();
    }

    private void joinSelected() {
        Optional<PublicJoinCoordinator.Snapshot> operation =
                PublicServersClientBridge.joinSnapshot();
        if (operation.map(PublicServersScreen::pending).orElse(false)) {
            PublicServersClientBridge.cancelJoin();
            return;
        }
        PublicServersSnapshot.Item selected = snapshot.selected();
        if (selected != null && selected.compatibility().joinable()) {
            PublicServersClientBridge.join(selected.entry().listingId());
        }
    }

    private void syncSnapshot() {
        snapshot = PublicServersClientBridge.snapshot();
        scrollIndex = Math.min(scrollIndex, maxScrollIndex());
        allButton.active = typeFilter != null;
        worldsButton.active = typeFilter != PublicServerType.INTEGRATED_WORLD;
        dedicatedButton.active = typeFilter != PublicServerType.DEDICATED_SERVER;
        compatibleButton.setMessage(toggleMessage(compatibleOnly,
                "e4steam_public_servers.filter.compatible"));
        slotsButton.setMessage(toggleMessage(freeSlotsOnly,
                "e4steam_public_servers.filter.free_slots"));

        for (int row = 0; row < rowButtons.size(); row++) {
            Button button = rowButtons.get(row);
            int index = scrollIndex + row;
            if (index >= snapshot.items().size()) {
                button.visible = false;
                continue;
            }
            PublicServersSnapshot.Item item = snapshot.items().get(index);
            button.visible = true;
            button.active = true;
            button.setMessage(rowMessage(item));
            PortableMinecraftUi.tooltip(button, rowTooltip(item));
        }

        previousButton.active = scrollIndex > 0;
        nextButton.active = scrollIndex < maxScrollIndex();
        Optional<PublicJoinCoordinator.Snapshot> operation =
                PublicServersClientBridge.joinSnapshot();
        boolean joining = operation.map(PublicServersScreen::pending).orElse(false);
        PublicServersSnapshot.Item selected = snapshot.selected();
        connectButton.active = joining
                || selected != null && selected.compatibility().joinable();
        connectButton.setMessage(PortableComponents.translatable(joining
                ? "e4steam_public_servers.cancel" : "e4steam_public_servers.connect"));
        statusText = statusMessage(operation);
    }

    private Component rowMessage(PublicServersSnapshot.Item item) {
        return PortableComponents.literal(item.entry().metadata().name());
    }

    public void renderCatalog(Object context, int mouseX, int mouseY) {
        PortableGraphics g = new PortableGraphics(context);
        int left = contentLeft;
        g.drawString(font, font.plainSubstrByWidth(title.getString(), contentWidth - 128), left, 10, 0xFFFFFF, false);
        g.drawCenteredString(font, PortableComponents.translatable("e4steam_public_servers.browser.subtitle"), width / 2, 24, 0xA5B4C8);
        for (int row = 0; row < rowButtons.size(); row++) {
            int index = scrollIndex + row;
            if (index >= snapshot.items().size()) break;
            PublicServersSnapshot.Item item = snapshot.items().get(index);
            ListingMetadata m = item.entry().metadata();
            int y = listTop + row * (rowHeight + 4);
            boolean selected = item.entry().listingId().equals(snapshot.selectedListingId());
            boolean hovered = mouseX >= left && mouseX < left + contentWidth && mouseY >= y && mouseY < y + rowHeight;
            g.fill(left + 1, y + 1, left + contentWidth - 1, y + rowHeight - 1, selected ? 0xFF203F4B : hovered ? 0xFF273445 : 0xFF182331);
            g.fill(left + 1, y + 1, left + 4, y + rowHeight - 1, selected ? 0xFF57D5B1 : item.compatibility().joinable() ? 0xFF4E7185 : 0xFF9A665D);
            String players = m.players() + "/" + m.capacity();
            int nameWidth = Math.max(20, contentWidth - font.width(players) - 34);
            g.drawString(font, font.plainSubstrByWidth(m.name(), nameWidth), left + 10, y + 5, 0xFFFFFF, false);
            g.drawString(font, players, left + contentWidth - font.width(players) - 10, y + 5, 0xAFD8CD, false);
            String details = m.minecraftVersion() + " · " + m.loader() + " · " + PortableComponents.translatable(item.entry().type() == PublicServerType.INTEGRATED_WORLD ? "e4steam_public_servers.type.integrated" : "e4steam_public_servers.type.dedicated").getString();
            g.drawString(font, font.plainSubstrByWidth(details, contentWidth - 20), left + 10, y + rowHeight - 12, 0xA5B4C8, false);
        }
        if (snapshot.items().isEmpty()) {
            g.drawCenteredString(font, statusText, width / 2, listTop + 10, 0xCED8E5);
        }
        g.drawString(font, font.plainSubstrByWidth(statusText.getString(), contentWidth), left, footerTop - 12, 0xAFD8CD, false);
        g.fill(left + 60, footerTop + 19, left + 60 + connectButton.getWidth(), footerTop + 21, 0xFF57D5B1);
        g.drawString(font, font.plainSubstrByWidth(PortableComponents.translatable("e4steam_public_servers.browser.publish_help").getString(), contentWidth), left, footerTop + 28, 0xA5B4C8, false);
    }

    private Component rowTooltip(PublicServersSnapshot.Item item) {
        ListingMetadata metadata = item.entry().metadata();
        String compatibility = item.compatibility().compatible()
                ? PortableComponents.translatable("e4steam_public_servers.compatible").getString()
                : PortableComponents.translatable("e4steam_public_servers.incompatible").getString();
        return PortableComponents.literal(metadata.description() + "\n" + compatibility
                + " · e4steam " + metadata.e4steamVersion());
    }

    private Component statusMessage(Optional<PublicJoinCoordinator.Snapshot> operation) {
        if (operation.isPresent()) {
            String key = joinStateKey(operation.get().state());
            if (!key.isEmpty()) return PortableComponents.translatable(key);
        }
        if (snapshot.items().isEmpty()) {
            String key = snapshot.messageKey().isEmpty()
                    ? stateKey(snapshot.state()) : snapshot.messageKey();
            return PortableComponents.translatable(key);
        }
        PublicServersSnapshot.Item selected = snapshot.selected();
        if (selected != null) {
            return selected.compatibility().compatible()
                    ? PortableComponents.translatable("e4steam_public_servers.compatible")
                    : PortableComponents.translatable("e4steam_public_servers.incompatible");
        }
        return PortableComponents.translatable("e4steam_public_servers.selection.none");
    }

    private static Component toggleMessage(boolean selected, String key) {
        return PortableComponents.literal((selected ? "[x] " : "[ ] ")
                + PortableComponents.translatable(key).getString());
    }

    private int maxScrollIndex() {
        return Math.max(0, snapshot.items().size() - visibleRows);
    }

    private void closeListener() {
        try {
            listener.close();
        } catch (Exception ignored) {
            // Addon registrations are idempotent.
        }
        listener = () -> { };
    }

    private static boolean pending(PublicJoinCoordinator.Snapshot operation) {
        switch (operation.state()) {
            case RESOLVING:
            case JOINING_STEAM_TARGET:
            case AUTHENTICATING:
            case CONNECTING_MINECRAFT:
                return true;
            default:
                return false;
        }
    }

    private static String joinStateKey(PublicJoinCoordinator.State state) {
        switch (state) {
            case RESOLVING: return "e4steam_public_servers.state.resolving";
            case JOINING_STEAM_TARGET: return "e4steam_public_servers.state.joining";
            case AUTHENTICATING: return "e4steam_public_servers.state.authenticating";
            case CONNECTING_MINECRAFT: return "e4steam_public_servers.state.connecting";
            case CANCELLED: return "e4steam_public_servers.state.cancelled";
            case STALE: return "e4steam_public_servers.state.stale";
            case FULL: return "e4steam_public_servers.full";
            case INCOMPATIBLE: return "e4steam_public_servers.incompatible";
            case DENIED: return "e4steam_public_servers.state.denied";
            case TARGET_LOST: return "e4steam_public_servers.state.target_lost";
            case REGISTRY_UNAVAILABLE: return "e4steam_public_servers.state.registry_unavailable";
            case FAILED: return "e4steam_public_servers.state.failed";
            default: return "";
        }
    }

    private static String stateKey(PublicServersSnapshot.State state) {
        switch (state) {
            case NOT_CONFIGURED: return "e4steam_public_servers.state.not_configured";
            case LOADING: return "e4steam_public_servers.state.loading";
            case CACHED: return "e4steam_public_servers.state.cached";
            case RATE_LIMITED: return "e4steam_public_servers.state.rate_limited";
            case UNAVAILABLE:
            case CLOSED:
                return "e4steam_public_servers.state.registry_unavailable";
            default:
                return "e4steam_public_servers.state.empty";
        }
    }
}
