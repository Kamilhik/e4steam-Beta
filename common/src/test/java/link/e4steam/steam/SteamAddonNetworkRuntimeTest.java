package link.e4steam.steam;

import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class SteamAddonNetworkRuntimeTest {
    @Test void requiredPreJoinNeverUsesOptionalCompatibilityGrace() {
        for (long seconds : new long[]{0, 1, 2, 4, 5, 60}) {
            assertFalse(SteamAddonNetworkRuntime.allowOptionalHelloFallback(true, true, false,
                    TimeUnit.SECONDS.toNanos(seconds)));
        }
    }
    @Test void optionalOnlyPeersRetainBoundedCompatibility() {
        assertFalse(SteamAddonNetworkRuntime.allowOptionalHelloFallback(false, true, false, 999_999_999L));
        assertTrue(SteamAddonNetworkRuntime.allowOptionalHelloFallback(false, true, false, 1_000_000_000L));
        assertFalse(SteamAddonNetworkRuntime.allowOptionalHelloFallback(false, true, true, 2_000_000_000L));
        assertFalse(SteamAddonNetworkRuntime.allowOptionalHelloFallback(false, false, false, 2_000_000_000L));
    }
}
