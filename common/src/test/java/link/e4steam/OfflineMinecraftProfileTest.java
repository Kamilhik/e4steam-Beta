package link.e4steam;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OfflineMinecraftProfileTest {
    @Test
    void usesVanillaOfflineUuid() {
        assertEquals(
                UUID.fromString("b50ad385-829d-3141-a216-7e7d7539ba7f"),
                OfflineMinecraftProfile.uuidForName("Notch")
        );
    }

    @Test
    void preservesCaseAndRejectsMissingNames() {
        assertNotEquals(
                OfflineMinecraftProfile.uuidForName("Player"),
                OfflineMinecraftProfile.uuidForName("player")
        );
        assertThrows(IllegalArgumentException.class, () -> OfflineMinecraftProfile.uuidForName(""));
    }

    @Test
    void recognizesOfflineOwnerWhenLauncherReplacesLocalUuid() {
        UUID vanillaId = OfflineMinecraftProfile.uuidForName("OfflineHost");
        UUID launcherId = UUID.fromString("11111111-2222-3333-8444-555555555555");

        assertEquals(true, OfflineMinecraftProfile.sameLocalOwner(
                vanillaId, "OfflineHost", launcherId, "offlinehost"
        ));
        assertEquals(false, OfflineMinecraftProfile.sameLocalOwner(
                vanillaId, "OfflineHost", launcherId, "OtherPlayer"
        ));
        assertEquals(false, OfflineMinecraftProfile.sameLocalOwner(
                null, "", null, ""
        ));
    }
}
