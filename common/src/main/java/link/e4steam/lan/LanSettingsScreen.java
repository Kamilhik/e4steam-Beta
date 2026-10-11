package link.e4steam.lan;

import link.e4steam.Config;
import link.e4steam.E4steamClient;
import link.e4steam.MinecraftUiCompat;
import link.e4steam.Mirror;
import link.e4steam.internal.api.CoreAccessModeCatalog;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

/** One settings form for both vanilla LAN entry screens and an already published world. */
public final class LanSettingsScreen extends Screen {
    private final Screen parent;
    private LanSettings.Snapshot draft;
    private CoreAccessModeCatalog.Selection access;
    private EditBox players;
    private EditBox port;
    private Button done;
    private boolean saving;
    private boolean published;
    private boolean playersValid = true;
    private boolean portValid = true;
    private int panelLeft, panelTop, panelWidth, panelHeight;
    private int gameTop, networkTop, sectionHeight, columnWidth;
    private int doneX, doneY, doneWidth;

    public LanSettingsScreen(Screen parent) {
        super(Mirror.translatable("text.e4steam_minecraft.lan.settings"));
        this.parent = parent;
        this.draft = LanSettings.current();
        this.access = CoreAccessModeCatalog.normalize(E4steamClient.selectedAccessMode,
                E4steamClient.selectedCustomAccessMode);
    }

    @Override protected void init() {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) { onClose(); return; }
        published = LanServerSettingsApplier.published(server);
        LanSettings.Snapshot active = LanSettings.active(server);
        if (active != null && players == null) draft = active;
        String playersText = players == null ? Integer.toString(draft.maxPlayers()) : players.getValue();
        String portText = port == null ? Integer.toString(published
                ? LanServerSettingsApplier.port(server) : draft.port()) : port.getValue();
        panelWidth = Math.min(360, width - 16);
        panelHeight = Math.min(264, height - 12);
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        boolean compact = panelHeight < 252;
        sectionHeight = compact ? 72 : 80;
        gameTop = panelTop + (compact ? 40 : 52);
        networkTop = gameTop + sectionHeight + 8;
        int left = panelLeft + 22;
        int contentWidth = panelWidth - 44;
        columnWidth = (contentWidth - 8) / 2;
        int right = left + columnWidth + 8;
        int gameRow = gameTop + 22;
        Button mode = addRenderableWidget(MinecraftUiCompat.button(gameModeLabel(), button -> {
            String[] modes = {"survival", "creative", "adventure", "spectator"};
            int index = java.util.Arrays.asList(modes).indexOf(draft.gameMode());
            draft = draft.withGameMode(modes[(index + 1) % modes.length]);
            button.setMessage(gameModeLabel());
            MinecraftUiCompat.tooltip(button, Mirror.translatable("text.e4steam_minecraft.lan.gameMode", gameModeLabel()));
        }, left, gameRow, columnWidth, 20));
        MinecraftUiCompat.tooltip(mode, Mirror.translatable("text.e4steam_minecraft.lan.gameMode", gameModeLabel()));
        Button cheats = addRenderableWidget(MinecraftUiCompat.button(state("cheats", draft.allowCheats()), button -> {
            draft = draft.withAllowCheats(!draft.allowCheats());
            button.setMessage(state("cheats", draft.allowCheats()));
        }, right, gameRow, columnWidth, 20));
        MinecraftUiCompat.tooltip(cheats, Mirror.translatable("text.e4steam_minecraft.lan.cheatsHelp"));
        addRenderableWidget(MinecraftUiCompat.button(state("pvp", draft.pvp()), button -> {
            draft = draft.withPvp(!draft.pvp());
            button.setMessage(state("pvp", draft.pvp()));
        }, left, gameRow + 24, columnWidth, 20));
        players = numberBox(right + columnWidth - 44, gameRow + 24, 44, draft.maxPlayers(), "players");
        players.setMaxLength(2);
        players.setValue(playersText);
        MinecraftUiCompat.tooltip(players, Mirror.translatable("text.e4steam_minecraft.lan.playersHelp"));
        int networkRow = networkTop + 22;
        Button accessButton = addRenderableWidget(MinecraftUiCompat.button(accessLabel(), button -> {
            access = CoreAccessModeCatalog.next(access.mode(), access.customModeId());
            button.setMessage(accessLabel());
            MinecraftUiCompat.tooltip(button, accessLabel());
        }, left, networkRow, contentWidth, 20));
        accessButton.active = Config.INSTANCE.hostEnabled.value();
        MinecraftUiCompat.tooltip(accessButton, accessLabel());
        Button online = addRenderableWidget(MinecraftUiCompat.button(state("onlineMode", draft.onlineMode()), button -> {
            draft = draft.withOnlineMode(!draft.onlineMode());
            button.setMessage(state("onlineMode", draft.onlineMode()));
        }, left, networkRow + 24, columnWidth, 20));
        MinecraftUiCompat.tooltip(online, Mirror.translatable("text.e4steam_minecraft.lan.onlineHelp"));
        port = numberBox(right + columnWidth - 64, networkRow + 24, 64, draft.port(), "port");
        port.setValue(portText);
        port.setEditable(!published);
        MinecraftUiCompat.tooltip(port, Mirror.translatable(published
                ? "text.e4steam_minecraft.lan.portLocked" : "text.e4steam_minecraft.lan.portHelp"));
        int footer = networkTop + sectionHeight + (compact ? 8 : 12);
        int footerWidth = (panelWidth - 32) / 2;
        addRenderableWidget(MinecraftUiCompat.button(Mirror.translatable("gui.cancel"), button -> onClose(),
                panelLeft + 12, footer, footerWidth, 20));
        doneX=panelLeft + panelWidth - 12 - footerWidth; doneY=footer; doneWidth=footerWidth;
        done = addRenderableWidget(MinecraftUiCompat.button(Mirror.translatable(published
                ? "text.e4steam_minecraft.lan.apply" : "lanServer.start"), button -> save(),
                panelLeft + panelWidth - 12 - footerWidth, footer, footerWidth, 20));
        players.setResponder(value -> validate());
        port.setResponder(value -> validate());
        validate();
    }

    public void renderLanPanel(Object context) {
        LanScreenGraphics graphics = new LanScreenGraphics(context);
        graphics.fill(0, 0, width, height, 0xB80A1019);
        graphics.fill(panelLeft + 3, panelTop + 4, panelLeft + panelWidth + 3,
                panelTop + panelHeight + 4, 0x50000000);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xF216202C);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 2, 0xFF54CFBC);
        graphics.drawCenteredString(font, title, width / 2, panelTop + 12, 0xF1F6FB);
        graphics.drawCenteredString(font, Mirror.translatable(published
                ? "text.e4steam_minecraft.lan.live" : "text.e4steam_minecraft.lan.ready"),
                width / 2, panelTop + 27, published ? 0x7DE1C7 : 0x98AABD);
        section(graphics, gameTop, "rules");
        section(graphics, networkTop, "connection");
        int right = panelLeft + 22 + columnWidth + 8;
        graphics.drawString(font, Mirror.translatable("text.e4steam_minecraft.lan.players"),
                right, gameTop + 52, playersValid ? 0xB7C6D5 : 0xFF8E8E, false);
        graphics.drawString(font, Mirror.translatable("text.e4steam_minecraft.lan.port"),
                right, networkTop + 52, !portValid ? 0xFF8E8E : published ? 0x738497 : 0xB7C6D5, false);
        if (!playersValid) outline(graphics, players, 44);
        if (!portValid) outline(graphics, port, 64);
    }

    public void renderLanIndicators(Object context) {
        LanScreenGraphics graphics = new LanScreenGraphics(context);
        int right = panelLeft + 22 + columnWidth + 8;
        // Retain native Minecraft button focus and hover behavior.
        indicator(graphics, right + 3, gameTop + 25, draft.allowCheats());
        indicator(graphics, panelLeft + 25, gameTop + 49, draft.pvp());
        indicator(graphics, panelLeft + 25, networkTop + 49, draft.onlineMode());
        graphics.fill(doneX, doneY + 19, doneX + doneWidth, doneY + 20,
                done.active ? 0xFF54CFBC : 0xFF43505E);
    }

    private void section(LanScreenGraphics graphics, int top, String key) {
        int left = panelLeft + 12;
        graphics.fill(left, top, panelLeft + panelWidth - 12, top + sectionHeight, 0xFF202D3B);
        graphics.fill(left, top, left + 2, top + sectionHeight, 0xFF334A5E);
        graphics.drawString(font, Mirror.translatable("text.e4steam_minecraft.lan." + key),
                left + 10, top + 7, 0x92D9CE, false);
    }

    private void indicator(LanScreenGraphics graphics, int x, int y, boolean enabled) {
        graphics.fill(x, y, x + 2, y + 14, enabled ? 0xFF66D5AF : 0xFF758496);
    }

    private void outline(LanScreenGraphics graphics, EditBox input, int size) {
        int x = panelLeft + 22 + columnWidth + 8 + columnWidth - size;
        int y = (input == players ? gameTop : networkTop) + 46;
        graphics.fill(x - 2, y - 2, x + size + 2, y - 1, 0xFFFF8E8E);
        graphics.fill(x - 2, y + 21, x + size + 2, y + 22, 0xFFFF8E8E);
    }

    private EditBox numberBox(int x, int y, int size, int value, String key) {
        EditBox input = new EditBox(font, x, y, size, 20,
                Mirror.translatable("text.e4steam_minecraft.lan." + key));
        input.setMaxLength(5);
        input.setFilter(valueText -> valueText.matches("[0-9]*"));
        input.setValue(Integer.toString(value));
        return addRenderableWidget(input);
    }

    private void validate() {
        if (done == null || players == null || port == null) return;
        Integer count = number(players.getValue(), LanSettings.MIN_PLAYERS, LanSettings.MAX_PLAYERS);
        Integer selectedPort = port.getValue().trim().isEmpty() ? Integer.valueOf(LanSettings.DEFAULT_PORT)
                : number(port.getValue(), 1, 65535);
        playersValid = count != null;
        portValid = selectedPort != null;
        if (playersValid && portValid) draft = draft.withMaxPlayers(count).withPort(selectedPort);
        done.active = playersValid && portValid && !saving;
    }

    private Integer number(String text, int min, int max) {
        try {
            int value = Integer.parseInt(text);
            return value >= min && value <= max ? value : null;
        } catch (NumberFormatException invalid) { return null; }
    }

    private void save() {
        validate();
        if (!done.active) return;
        if (port.getValue().trim().isEmpty()) port.setValue(Integer.toString(draft.port()));
        if (access.requiresConfirmation() && !E4steamClient.customAccessConfirmed(access.customModeId())) {
            MinecraftUiCompat.setScreen(minecraft, new ConfirmScreen(confirmed -> {
                if (confirmed) E4steamClient.confirmCustomAccess(access.customModeId());
                MinecraftUiCompat.setScreen(minecraft, this);
                if (confirmed) save();
            }, Mirror.translatable(access.confirmationTitleKey()), Mirror.translatable(access.confirmationMessageKey())));
            return;
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) { onClose(); return; }
        saving = true;
        done.active = false;
        LanSettings.Snapshot selected = draft;
        E4steamClient.selectedAccessMode = access.mode();
        E4steamClient.selectedCustomAccessMode = access.customModeId();
        server.execute(() -> {
            try {
                if (minecraft.getSingleplayerServer() != server)
                    throw new IllegalStateException("The integrated world changed");
                LanServerSettingsApplier.apply(server, selected);
                if (!published && !LanServerSettingsApplier.publish(server, selected))
                    throw new IllegalStateException("Minecraft could not open the selected LAN port");
                // Vanilla publication may reset game mode/commands; apply the same snapshot again.
                LanServerSettingsApplier.apply(server, selected);
                minecraft.execute(() -> {
                    if (minecraft.getSingleplayerServer() != server) { saving = false; return; }
                    ModernLanSettings.save(selected);
                    if (published) ModernLanSettings.refreshSteamSession(server);
                    MinecraftUiCompat.setScreen(minecraft, null);
                });
            } catch (RuntimeException failure) {
                E4steamClient.LOGGER.warn("Could not apply e4steam LAN settings", failure);
                minecraft.execute(() -> {
                    saving = false;
                    done.setMessage(Mirror.translatable("text.e4steam_minecraft.lan.failed"));
                    validate();
                });
            }
        });
    }

    private Component gameModeLabel() {
        return Mirror.translatable("gameMode." + draft.gameMode());
    }

    private Component accessLabel() {
        return Mirror.append(Mirror.append(Mirror.translatable("text.e4steam_minecraft.accessMode"),
                Mirror.literal(": ")), Mirror.translatable(access.translationKey()));
    }

    private Component state(String key, boolean enabled) {
        return Mirror.translatable("text.e4steam_minecraft.lan." + key,
                Mirror.translatable(enabled ? "options.on" : "options.off"));
    }

    @Override public void onClose() {
        if (!saving) MinecraftUiCompat.setScreen(minecraft, parent);
    }
}
