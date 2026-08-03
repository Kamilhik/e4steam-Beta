package link.e4steam.steam;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SteamSocialProviderTest {
    @Test
    void roundTripsTargetedGuiInvitationPresence() {
        String encoded = SteamSocialProvider.encodeInviteSignal(
                76561198000000001L,
                -1234567890123456789L,
                1_900_000_000_000L
        );

        assertEquals(
                new SteamSocialProvider.InviteSignal(
                        76561198000000001L,
                        -1234567890123456789L,
                        1_900_000_000_000L
                ),
                SteamSocialProvider.parseInviteSignal(encoded)
        );
    }

    @Test
    void rejectsMalformedGuiInvitationPresence() {
        assertNull(SteamSocialProvider.parseInviteSignal(""));
        assertNull(SteamSocialProvider.parseInviteSignal("1:2"));
        assertNull(SteamSocialProvider.parseInviteSignal("0:2:3"));
        assertNull(SteamSocialProvider.parseInviteSignal("friend:lobby:later"));
    }

    @Test
    void roundTripsTargetedJoinRequestPresence() {
        String encoded = SteamSocialProvider.encodeJoinRequestSignal(
                76561198000000001L,
                1_900_000_000_000L
        );

        assertEquals(
                new SteamSocialProvider.JoinRequestSignal(
                        76561198000000001L,
                        1_900_000_000_000L
                ),
                SteamSocialProvider.parseJoinRequestSignal(encoded)
        );
    }

    @Test
    void rejectsMalformedJoinRequestPresence() {
        assertNull(SteamSocialProvider.parseJoinRequestSignal(""));
        assertNull(SteamSocialProvider.parseJoinRequestSignal("1"));
        assertNull(SteamSocialProvider.parseJoinRequestSignal("0:3"));
        assertNull(SteamSocialProvider.parseJoinRequestSignal("friend:later"));
    }
}
