package link.e4steam.steam;

import com.codedisaster.steamworks.SteamAPICall;
import com.codedisaster.steamworks.SteamID;
import com.codedisaster.steamworks.SteamMatchmaking;
import com.codedisaster.steamworks.SteamMatchmakingCallback;
import com.codedisaster.steamworks.SteamNativeHandle;
import com.codedisaster.steamworks.SteamResult;
import link.e4steam.E4steamClient;
import link.e4steam.MinecraftVersion;
import link.e4steam.Mirror;

import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongConsumer;

/** Steam lobby and invitation-join state. Called only by the Steam worker. */
final class SteamLobbyManager implements AutoCloseable {
    static final int VANILLA_LOBBY_CAPACITY = 8;
    static final int VANILLA_MAX_GUESTS = VANILLA_LOBBY_CAPACITY - 1;
    private static final String KEY_PROTOCOL = "e4steam_protocol";
    private static final String KEY_MINECRAFT = "e4steam_minecraft";
    private static final String KEY_ENDPOINT = "e4steam_endpoint";
    private static final String PROTOCOL_VERSION = Byte.toString(SteamProtocol.VERSION);
    private static final long GUEST_JOIN_TIMEOUT_MILLIS = 30_000;

    private final SteamRuntime runtime;
    private final String minecraftVersion = MinecraftVersion.current();
    private final SteamSocialProvider social;
    private final SteamMatchmaking matchmaking;

    private PendingHost pendingHost;
    private PendingHost queuedHost;
    private HostLobby hostLobby;
    private GuestLobby guestLobby;
    private long requestedLobbyId;
    private long requestedFriendId;
    private long requestedJoinDeadlineMillis;
    private final Set<Long> canceledJoinLobbyIds = new HashSet<>();

    SteamLobbyManager(SteamRuntime runtime, SteamSocialProvider social) {
        this.runtime = runtime;
        this.social = social;
        social.setListener(new SteamSocialProvider.Listener() {
            @Override
            public void onLobbyJoinRequested(long lobbyId, long friendSteamId) {
                requestJoin(lobbyId, friendSteamId);
            }

            @Override
            public void onGuiInvitationReceived(
                    long lobbyId,
                    long friendSteamId,
                    String friendName,
                    String minecraftName,
                    long invitationGeneration
            ) {
                E4steamClient.showSteamInvitationToast(
                        lobbyId, friendSteamId, friendName, minecraftName, invitationGeneration
                );
            }

            @Override
            public void onGuiJoinRequestReceived(
                    long friendSteamId,
                    String friendName,
                    String minecraftName,
                    long requestGeneration
            ) {
                E4steamClient.showSteamJoinRequestToast(
                        friendSteamId, friendName, minecraftName, requestGeneration
                );
            }
        });
        matchmaking = new SteamMatchmaking(new SteamMatchmakingCallback() {
            @Override
            public void onLobbyCreated(SteamResult result, SteamID lobby) {
                handleLobbyCreated(result, lobby);
            }

            @Override
            public void onLobbyEnter(
                    SteamID lobby,
                    int chatPermissions,
                    boolean blocked,
                    SteamMatchmaking.ChatRoomEnterResponse response
            ) {
                handleLobbyEnter(lobby, response);
            }

            @Override
            public void onLobbyDataUpdate(SteamID lobby, SteamID member, boolean success) {
                long lobbyId = SteamNativeHandle.getNativeHandle(lobby);
                if (success && guestLobby != null && guestLobby.lobbyId == lobbyId && guestLobby.endpoint == null) {
                    resolveGuestEndpoint();
                }
            }

            @Override
            public void onLobbyChatUpdate(
                    SteamID lobby,
                    SteamID changedUser,
                    SteamID makingChange,
                    SteamMatchmaking.ChatMemberStateChange stateChange
            ) {
                if (stateChange == SteamMatchmaking.ChatMemberStateChange.Entered) {
                    return;
                }
                long lobbyId = SteamNativeHandle.getNativeHandle(lobby);
                long userId = SteamNativeHandle.getNativeHandle(changedUser);
                if (hostLobby != null && hostLobby.lobbyId == lobbyId) {
                    if (userId == runtime.steamIdValue()) {
                        loseHostLobby("Steam removed the host from its lobby");
                        return;
                    }
                    runtime.closeRemoteBridges(userId);
                }
                if (guestLobby != null
                        && guestLobby.lobbyId == lobbyId
                        && (guestLobby.hostSteamId == userId || userId == runtime.steamIdValue())) {
                    loseGuestLobby();
                }
            }

            @Override
            public void onLobbyKicked(SteamID lobby, SteamID admin, boolean disconnected) {
                long lobbyId = SteamNativeHandle.getNativeHandle(lobby);
                if (hostLobby != null && hostLobby.lobbyId == lobbyId) {
                    loseHostLobby("Steam closed the host lobby");
                } else if (guestLobby != null && guestLobby.lobbyId == lobbyId) {
                    loseGuestLobby();
                }
            }
        });
    }

    CompletableFuture<Long> createHostLobby(
            SteamSession owner,
            SteamAccessMode accessMode,
            SteamAddress address
    ) {
        CompletableFuture<Long> result = new CompletableFuture<>();
        if (accessMode == SteamAccessMode.LOCAL_ONLY) {
            result.completeExceptionally(new IOException("Local-only mode does not create a Steam lobby"));
            return result;
        }
        if (hostLobby != null || queuedHost != null) {
            result.completeExceptionally(new IOException("A Steam lobby is already active"));
            return result;
        }

        PendingHost requested = new PendingHost(owner, accessMode, address, result);
        if (pendingHost != null) {
            if (!pendingHost.canceled) {
                result.completeExceptionally(new IOException("A Steam lobby is already being created"));
            } else {
                queuedHost = requested;
            }
            return result;
        }

        issueHostCreate(requested);
        return result;
    }

    void stopHosting(SteamSession owner) {
        PendingHost pending = pendingHost;
        if (pending != null && pending.owner == owner) {
            // Steam's lobby-created callback does not identify its API call.
            // Keep this canceled request as a tombstone so a late callback
            // cannot be mistaken for a newer hosting session.
            pending.canceled = true;
            pending.result.completeExceptionally(new IOException("Steam hosting was stopped"));
        }
        PendingHost queued = queuedHost;
        if (queued != null && queued.owner == owner) {
            queuedHost = null;
            queued.result.completeExceptionally(new IOException("Steam hosting was stopped"));
        }
        HostLobby current = hostLobby;
        if (current != null && current.owner == owner) {
            SteamID lobby = SteamID.createFromNativeHandle(current.lobbyId);
            matchmaking.setLobbyJoinable(lobby, false);
            matchmaking.leaveLobby(lobby);
            hostLobby = null;
            social.clearHostingPresence();
            social.markInvitationUnavailable(current.lobbyId);
        }
    }

    void openHostInviteOverlay(SteamSession owner) throws IOException {
        HostLobby current = hostLobby;
        if (current == null || current.owner != owner) {
            throw new IOException("Steam lobby is not ready");
        }
        social.openInviteOverlay(current.lobbyId);
    }

    boolean inviteFriend(SteamSession owner, long remoteSteamId) throws IOException {
        HostLobby current = hostLobby;
        if (current == null || current.owner != owner) {
            throw new IOException("Steam lobby is not ready");
        }
        if (runtime.isPeerConnected(remoteSteamId)) {
            return false;
        }
        return social.inviteToLobby(remoteSteamId, current.lobbyId);
    }

    boolean requestToJoin(long remoteSteamId) throws IOException {
        return social.requestToJoin(remoteSteamId);
    }

    boolean approveJoinRequest(
            SteamSession owner,
            long remoteSteamId,
            long requestGeneration
    ) throws IOException {
        boolean invited = inviteFriend(owner, remoteSteamId);
        if (invited) social.markJoinRequestUnavailable(remoteSteamId, requestGeneration);
        return invited;
    }

    boolean joinFriend(long remoteSteamId) {
        OptionalLong lobbyId = social.friendLobby(remoteSteamId);
        if (lobbyId.isEmpty()) {
            return false;
        }
        requestJoin(lobbyId.getAsLong(), remoteSteamId);
        return true;
    }

    boolean joinInvitation(long lobbyId, long remoteSteamId) {
        if (lobbyId == 0 || !social.isDirectFriend(remoteSteamId)) {
            return false;
        }
        requestJoin(lobbyId, remoteSteamId);
        return true;
    }

    boolean allows(SteamSession owner, long remoteSteamId) {
        HostLobby current = hostLobby;
        if (current == null || current.owner != owner) {
            return false;
        }
        return isAllowedHostPeer(current, remoteSteamId);
    }

    boolean mayAcceptPeer(long remoteSteamId) {
        GuestLobby currentGuest = guestLobby;
        if (currentGuest != null && currentGuest.hostSteamId == remoteSteamId) {
            return true;
        }
        HostLobby currentHost = hostLobby;
        return currentHost != null
                && (isAllowedHostPeer(currentHost, remoteSteamId)
                || mayAwaitHostPeer(currentHost, remoteSteamId));
    }

    /**
     * A private-lobby member update can reach the guest before it reaches the
     * host. Keep the authenticated Steam session alive briefly, but do not
     * authorize Minecraft traffic until Steam lists the friend as a member.
     */
    boolean mayAwaitHostPeer(SteamSession owner, long remoteSteamId) {
        HostLobby current = hostLobby;
        return current != null
                && current.owner == owner
                && mayAwaitHostPeer(current, remoteSteamId);
    }

    void forEachKnownSessionPeer(LongConsumer consumer) {
        GuestLobby currentGuest = guestLobby;
        if (currentGuest != null && currentGuest.hostSteamId != 0) {
            consumer.accept(currentGuest.hostSteamId);
        }

        HostLobby currentHost = hostLobby;
        if (currentHost == null) {
            return;
        }
        SteamID lobbyId = SteamID.createFromNativeHandle(currentHost.lobbyId);
        int memberCount = matchmaking.getNumLobbyMembers(lobbyId);
        for (int index = 0; index < memberCount; index++) {
            SteamID member = matchmaking.getLobbyMemberByIndex(lobbyId, index);
            if (member == null) {
                continue;
            }
            long remoteSteamId = SteamNativeHandle.getNativeHandle(member);
            if (remoteSteamId != 0
                    && remoteSteamId != runtime.steamIdValue()
                    && social.isDirectFriend(remoteSteamId)) {
                consumer.accept(remoteSteamId);
            }
        }
    }

    boolean keepsRuntimeAlive() {
        return (pendingHost != null && !pendingHost.canceled)
                || queuedHost != null
                || hostLobby != null
                || guestLobby != null
                || requestedLobbyId != 0;
    }

    void clientBridgeOpened(long remoteSteamId) {
        GuestLobby current = guestLobby;
        if (current != null && current.hostSteamId == remoteSteamId) {
            current.joinState.connected();
        }
    }

    void clientBridgeClosed(long remoteSteamId, boolean anotherBridgeExists) {
        GuestLobby current = guestLobby;
        if (current != null && current.hostSteamId == remoteSteamId && !anotherBridgeExists) {
            leaveGuestLobby();
        }
    }

    void cancelGuestJoin() {
        leaveGuestLobby();
    }

    boolean claimGuestInvite(String endpoint) {
        GuestLobby current = guestLobby;
        if (current == null
                || endpoint == null
                || !endpoint.equals(current.endpoint)
                || !current.joinState.claim()) {
            return false;
        }
        return true;
    }

    boolean beginGuestConnect(String endpoint) {
        GuestLobby current = guestLobby;
        if (current != null
                && endpoint != null
                && endpoint.equals(current.endpoint)) {
            return current.joinState.beginConnect(
                    System.currentTimeMillis() + GUEST_JOIN_TIMEOUT_MILLIS
            );
        }
        return false;
    }

    void cleanup(long now) {
        if (requestedLobbyId != 0 && requestedJoinDeadlineMillis <= now) {
            leaveGuestLobby();
            E4steamClient.showSteamJoinFailure(Mirror.translatable("text.e4steam_minecraft.joinLobbyTimeout"));
            return;
        }
        GuestLobby current = guestLobby;
        if (current != null && current.joinState.expired(now)) {
            if (runtime.hasClientBridgeForRemote(current.hostSteamId)) {
                current.joinState.connected();
                return;
            }
            leaveGuestLobby();
            E4steamClient.showSteamJoinFailure(Mirror.translatable("text.e4steam_minecraft.joinConnectTimeout"));
        }
    }

    private void handleLobbyCreated(SteamResult result, SteamID lobby) {
        PendingHost pending = pendingHost;
        pendingHost = null;
        long lobbyId = SteamNativeHandle.getNativeHandle(lobby);
        if (pending == null) {
            if (lobbyId != 0) {
                matchmaking.leaveLobby(lobby);
            }
            return;
        }
        if (pending.canceled) {
            if (lobbyId != 0) {
                matchmaking.leaveLobby(lobby);
            }
            issueQueuedHostCreate();
            return;
        }
        if (result != SteamResult.OK || lobbyId == 0) {
            pending.result.completeExceptionally(new IOException("Steam lobby creation failed: " + result));
            issueQueuedHostCreate();
            return;
        }

        boolean metadataReady = matchmaking.setLobbyJoinable(lobby, false)
                && matchmaking.setLobbyData(lobby, KEY_PROTOCOL, PROTOCOL_VERSION)
                && matchmaking.setLobbyData(lobby, KEY_MINECRAFT, minecraftVersion)
                && matchmaking.setLobbyData(lobby, KEY_ENDPOINT, pending.address.inviteString())
                && matchmaking.setLobbyJoinable(lobby, true);
        if (!metadataReady) {
            matchmaking.leaveLobby(lobby);
            pending.result.completeExceptionally(new IOException("Steam rejected e4steam lobby metadata"));
            issueQueuedHostCreate();
            return;
        }

        hostLobby = new HostLobby(pending.owner, pending.accessMode, lobbyId);
        try {
            social.publishHosting(lobbyId, pending.accessMode == SteamAccessMode.FRIENDS_ONLY);
        } catch (IOException exception) {
            matchmaking.setLobbyJoinable(lobby, false);
            matchmaking.leaveLobby(lobby);
            hostLobby = null;
            pending.result.completeExceptionally(exception);
            issueQueuedHostCreate();
            return;
        }
        pending.result.complete(lobbyId);
    }

    private void issueHostCreate(PendingHost requested) {
        pendingHost = requested;
        SteamMatchmaking.LobbyType type = requested.accessMode == SteamAccessMode.FRIENDS_ONLY
                ? SteamMatchmaking.LobbyType.FriendsOnly
                : SteamMatchmaking.LobbyType.Private;
        SteamAPICall call = matchmaking.createLobby(type, VANILLA_LOBBY_CAPACITY);
        if (call == null || !call.isValid()) {
            pendingHost = null;
            requested.result.completeExceptionally(new IOException("Steam rejected the lobby creation request"));
            issueQueuedHostCreate();
        }
    }

    private void issueQueuedHostCreate() {
        if (pendingHost != null || hostLobby != null || queuedHost == null) {
            return;
        }
        PendingHost queued = queuedHost;
        queuedHost = null;
        issueHostCreate(queued);
    }

    private void requestJoin(long lobbyId, long friendSteamId) {
        if (lobbyId == 0 || (hostLobby != null && hostLobby.lobbyId == lobbyId)) {
            return;
        }
        if (canceledJoinLobbyIds.contains(lobbyId)) {
            E4steamClient.showSteamJoinFailure(Mirror.translatable("text.e4steam_minecraft.joinCancelPending"));
            return;
        }
        if (requestedLobbyId == lobbyId || (guestLobby != null && guestLobby.lobbyId == lobbyId)) {
            return;
        }
        if (guestLobby != null
                && (guestLobby.endpoint != null
                || guestLobby.joinState.isConnected()
                || runtime.hasClientBridgeForRemote(guestLobby.hostSteamId))) {
            E4steamClient.showSteamJoinFailure(Mirror.translatable("text.e4steam_minecraft.joinCurrentSession"));
            return;
        }
        if (guestLobby != null || requestedLobbyId != 0) {
            leaveGuestLobby();
        }
        requestedLobbyId = lobbyId;
        requestedFriendId = friendSteamId;
        requestedJoinDeadlineMillis = System.currentTimeMillis() + GUEST_JOIN_TIMEOUT_MILLIS;
        SteamID lobby = SteamID.createFromNativeHandle(lobbyId);
        SteamAPICall call = matchmaking.joinLobby(lobby);
        if (call == null || !call.isValid()) {
            requestedLobbyId = 0;
            requestedFriendId = 0;
            requestedJoinDeadlineMillis = 0;
            E4steamClient.showSteamJoinFailure(Mirror.translatable("text.e4steam_minecraft.joinRejected"));
        }
    }

    private void handleLobbyEnter(SteamID lobby, SteamMatchmaking.ChatRoomEnterResponse response) {
        long lobbyId = SteamNativeHandle.getNativeHandle(lobby);
        if (canceledJoinLobbyIds.remove(lobbyId)) {
            matchmaking.leaveLobby(lobby);
            return;
        }
        if (hostLobby != null && hostLobby.lobbyId == lobbyId) {
            return;
        }
        if (requestedLobbyId != lobbyId) {
            matchmaking.leaveLobby(lobby);
            return;
        }
        requestedLobbyId = 0;
        requestedJoinDeadlineMillis = 0;
        if (response != SteamMatchmaking.ChatRoomEnterResponse.Success) {
            requestedFriendId = 0;
            E4steamClient.showSteamJoinFailure(Mirror.translatable(
                    "text.e4steam_minecraft.joinLobbyEnterFailed",
                    response
            ));
            return;
        }

        SteamID owner = matchmaking.getLobbyOwner(lobby);
        long ownerId = owner == null ? 0 : SteamNativeHandle.getNativeHandle(owner);
        if (ownerId == 0
                || ownerId == runtime.steamIdValue()
                || !social.isDirectFriend(ownerId)) {
            matchmaking.leaveLobby(lobby);
            requestedFriendId = 0;
            E4steamClient.showSteamJoinFailure(Mirror.translatable("text.e4steam_minecraft.joinOwnerNotFriend"));
            return;
        }
        guestLobby = new GuestLobby(
                lobbyId,
                ownerId,
                requestedFriendId,
                System.currentTimeMillis() + GUEST_JOIN_TIMEOUT_MILLIS
        );
        requestedFriendId = 0;
        resolveGuestEndpoint();
    }

    private void resolveGuestEndpoint() {
        GuestLobby current = guestLobby;
        if (current == null || current.endpoint != null) {
            return;
        }
        SteamID lobby = SteamID.createFromNativeHandle(current.lobbyId);
        String protocol = matchmaking.getLobbyData(lobby, KEY_PROTOCOL);
        String minecraft = matchmaking.getLobbyData(lobby, KEY_MINECRAFT);
        String endpoint = matchmaking.getLobbyData(lobby, KEY_ENDPOINT);
        if (protocol == null || protocol.isBlank() || endpoint == null || endpoint.isBlank()) {
            matchmaking.requestLobbyData(lobby);
            return;
        }
        if (!PROTOCOL_VERSION.equals(protocol) || !minecraftVersion.equals(minecraft)) {
            leaveGuestLobby();
            E4steamClient.showSteamJoinFailure(Mirror.translatable("text.e4steam_minecraft.joinIncompatible"));
            return;
        }
        Optional<SteamAddress> parsed = SteamAddress.tryParse(endpoint);
        if (parsed.isEmpty() || parsed.get().steamId() != current.hostSteamId) {
            leaveGuestLobby();
            E4steamClient.showSteamJoinFailure(Mirror.translatable("text.e4steam_minecraft.joinInvalidAddress"));
            return;
        }

        current.endpoint = endpoint;
        // The in-world confirmation has no countdown. Keep this lobby alive
        // until the user chooses Join, then beginGuestConnect() starts the
        // bounded Minecraft connection window.
        current.joinState.waitForConfirmation();
        String hostName = social.friendName(current.hostSteamId);
        E4steamClient.acceptSteamInvite(endpoint, hostName);
    }

    private boolean isAllowedHostPeer(HostLobby lobby, long remoteSteamId) {
        if (!social.isDirectFriend(remoteSteamId)) {
            return false;
        }

        // Friends-only deliberately permits the copied address as a fallback.
        // A private lobby is stricter: membership proves that Steam admitted
        // this friend through an invitation for the current hosting session.
        if (lobby.accessMode == SteamAccessMode.FRIENDS_ONLY) {
            return true;
        }

        SteamID lobbyId = SteamID.createFromNativeHandle(lobby.lobbyId);
        int memberCount = matchmaking.getNumLobbyMembers(lobbyId);
        for (int index = 0; index < memberCount; index++) {
            SteamID member = matchmaking.getLobbyMemberByIndex(lobbyId, index);
            if (member != null && SteamNativeHandle.getNativeHandle(member) == remoteSteamId) {
                return true;
            }
        }
        return false;
    }

    private boolean mayAwaitHostPeer(HostLobby lobby, long remoteSteamId) {
        return lobby.accessMode == SteamAccessMode.INVITE_ONLY
                && social.isDirectFriend(remoteSteamId);
    }

    private void loseHostLobby(String detail) {
        HostLobby lost = hostLobby;
        if (lost == null) {
            return;
        }
        hostLobby = null;
        social.clearHostingPresence();
        social.markInvitationUnavailable(lost.lobbyId);
        lost.owner.runtimeFailed(new IOException(detail));
    }

    private void loseGuestLobby() {
        GuestLobby lost = guestLobby;
        if (lost == null) {
            return;
        }
        lost.joinState.loseLobby();
        runtime.closeRemoteBridges(lost.hostSteamId);
        leaveGuestLobby();
        E4steamClient.showSteamJoinFailure(Mirror.translatable("text.e4steam_minecraft.joinLobbyClosed"));
    }

    private void leaveGuestLobby() {
        GuestLobby current = guestLobby;
        long requested = requestedLobbyId;
        guestLobby = null;
        requestedLobbyId = 0;
        requestedFriendId = 0;
        requestedJoinDeadlineMillis = 0;
        if (current != null) {
            current.joinState.cancel();
            matchmaking.leaveLobby(SteamID.createFromNativeHandle(current.lobbyId));
            social.markInvitationUnavailable(current.lobbyId);
        } else if (requested != 0) {
            // LobbyEnter does not identify the JoinLobby API call. Preserve a
            // tombstone so a late callback cannot be accepted as a later
            // retry for the same lobby in this Steam runtime generation.
            canceledJoinLobbyIds.add(requested);
            matchmaking.leaveLobby(SteamID.createFromNativeHandle(requested));
            social.markInvitationUnavailable(requested);
        }
    }

    @Override
    public void close() {
        PendingHost pending = pendingHost;
        pendingHost = null;
        if (pending != null) {
            pending.result.completeExceptionally(new IOException("Steam runtime stopped before creating the lobby"));
        }
        PendingHost queued = queuedHost;
        queuedHost = null;
        if (queued != null) {
            queued.result.completeExceptionally(new IOException("Steam runtime stopped before creating the lobby"));
        }
        if (hostLobby != null) {
            SteamID lobby = SteamID.createFromNativeHandle(hostLobby.lobbyId);
            matchmaking.setLobbyJoinable(lobby, false);
            matchmaking.leaveLobby(lobby);
            hostLobby = null;
        }
        leaveGuestLobby();
        canceledJoinLobbyIds.clear();
        social.clearHostingPresence();
        matchmaking.dispose();
    }

    private static final class PendingHost {
        private final SteamSession owner;
        private final SteamAccessMode accessMode;
        private final SteamAddress address;
        private final CompletableFuture<Long> result;
        private boolean canceled;

        private PendingHost(
                SteamSession owner,
                SteamAccessMode accessMode,
                SteamAddress address,
                CompletableFuture<Long> result
        ) {
            this.owner = owner;
            this.accessMode = accessMode;
            this.address = address;
            this.result = result;
        }
    }

    private record HostLobby(SteamSession owner, SteamAccessMode accessMode, long lobbyId) {
    }

    private static final class GuestLobby {
        private final long lobbyId;
        private final long hostSteamId;
        @SuppressWarnings("unused")
        private final long inviterSteamId;
        private final SteamGuestJoinState joinState;
        private String endpoint;

        private GuestLobby(long lobbyId, long hostSteamId, long inviterSteamId, long deadlineMillis) {
            this.lobbyId = lobbyId;
            this.hostSteamId = hostSteamId;
            this.inviterSteamId = inviterSteamId;
            this.joinState = new SteamGuestJoinState(deadlineMillis);
        }
    }
}
