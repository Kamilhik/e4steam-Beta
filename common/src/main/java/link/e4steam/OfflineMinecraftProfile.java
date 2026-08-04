package link.e4steam;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Minecraft-compatible identity for a local/offline profile name. */
public final class OfflineMinecraftProfile {
    private static final String UUID_PREFIX = "OfflinePlayer:";

    private OfflineMinecraftProfile() {
    }

    /**
     * Uses the same deterministic UUID algorithm as a vanilla offline-mode
     * server. The exact profile name is preserved because it is part of the
     * vanilla identity and must not be replaced with a Microsoft UUID.
     */
    public static UUID uuidForName(String profileName) {
        if (profileName == null || profileName.isBlank()) {
            throw new IllegalArgumentException("Offline Minecraft profile name is blank");
        }
        return UUID.nameUUIDFromBytes(
                (UUID_PREFIX + profileName).getBytes(StandardCharsets.UTF_8)
        );
    }
}
