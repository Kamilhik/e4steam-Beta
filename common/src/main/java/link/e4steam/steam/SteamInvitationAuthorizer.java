package link.e4steam.steam;

import java.security.MessageDigest;

/** Pure invitation/token decision logic shared by the host packet path. */
final class SteamInvitationAuthorizer {
    enum Decision {
        ALLOWED,
        SESSION_CLOSED,
        INVALID_OR_EXPIRED_TOKEN,
        PEER_NOT_READY,
        PEER_NOT_ALLOWED
    }

    private SteamInvitationAuthorizer() {
    }

    static Decision authorize(
            byte[] activeToken,
            byte[] presentedToken,
            boolean peerAllowed,
            boolean peerMayBecomeAllowed
    ) {
        if (activeToken == null) {
            return Decision.SESSION_CLOSED;
        }
        if (presentedToken == null || !MessageDigest.isEqual(activeToken, presentedToken)) {
            return Decision.INVALID_OR_EXPIRED_TOKEN;
        }
        if (peerAllowed) {
            return Decision.ALLOWED;
        }
        return peerMayBecomeAllowed ? Decision.PEER_NOT_READY : Decision.PEER_NOT_ALLOWED;
    }
}
