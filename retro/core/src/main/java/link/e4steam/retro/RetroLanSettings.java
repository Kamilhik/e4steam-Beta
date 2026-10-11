package link.e4steam.retro;

import link.e4steam.lan.LanSettings;
import link.e4steam.lan.LanSettingsStore;
import link.e4steam.lan.LanServerSettingsApplier;
import link.e4steam.lan.LanServerTasks;
import link.e4steam.E4steamClient;
import java.util.concurrent.CompletableFuture;

/** Shared editor state; only widgets and mapped server callbacks vary by Minecraft branch. */
public final class RetroLanSettings {
    public LanSettings.Snapshot draft = LanSettings.current();
    public volatile boolean saving;
    public volatile String error = "";
    public final Object server;
    public final boolean published;

    public RetroLanSettings(Object server) {
        this.server = server;
        published = LanServerSettingsApplier.published(server);
        LanSettings.Snapshot active = LanSettings.active(server);
        if (active != null) draft = active;
        if (published) draft = draft.withPort(LanServerSettingsApplier.port(server));
    }

    public void cycleMode() {
        String[] modes = RetroVersion.minecraft().startsWith("1.7.")
                ? new String[]{"survival", "creative", "adventure"}
                : new String[]{"survival", "creative", "adventure", "spectator"};
        int index = java.util.Arrays.asList(modes).indexOf(draft.gameMode());
        draft = draft.withGameMode(modes[(index + 1) % modes.length]);
    }

    public boolean validate(String players, String port) {
        try {
            draft = draft.withMaxPlayers(Integer.parseInt(players)).withPort((port.trim().isEmpty() ? LanSettings.DEFAULT_PORT : Integer.parseInt(port)));
            return !saving;
        } catch (IllegalArgumentException invalid) { return false; }
    }

    public CompletableFuture<Boolean> save() {
        saving = true;
        return LanServerTasks.apply(server, draft).handle((success, failure) -> {
            saving = false;
            if (failure != null || !Boolean.TRUE.equals(success)) {
                error = "Could not apply LAN settings";
                E4steamClient.LOGGER.warn(error, failure);
                return false;
            }
            LanSettingsStore.update(draft);
            return true;
        });
    }
}
