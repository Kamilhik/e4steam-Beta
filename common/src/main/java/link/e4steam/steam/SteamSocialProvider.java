package link.e4steam.steam;

import com.codedisaster.steamworks.SteamFriends;
import com.codedisaster.steamworks.SteamFriendsCallback;
import com.codedisaster.steamworks.SteamID;
import com.codedisaster.steamworks.SteamNativeHandle;
import com.codedisaster.steamworks.SteamUtils;
import link.e4steam.E4steamClient;
import link.e4steam.MinecraftVersion;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Owns Steam's friends API, rich presence, avatars, and invitation history.
 * Every method is called on the single Steam worker thread.
 */
final class SteamSocialProvider implements AutoCloseable {
    static final String LOBBY_CONNECT_PREFIX = "e4steam-lobby:";
    static final long INVITATION_TTL_MILLIS = 5 * 60_000L;
    private static final long INVITATION_POLL_INTERVAL_MILLIS = 500L;
    private static final int MAX_INVITATIONS = 16;
    private static final int MAX_OUTGOING_INVITE_SIGNALS = SteamLobbyManager.VANILLA_MAX_GUESTS;
    private static final long PRESENCE_CACHE_MILLIS = 45_000L;
    private static final String PRESENCE_MARKER = "e4steam";
    private static final String PRESENCE_PROTOCOL = "e4steam_protocol";
    private static final String PRESENCE_MINECRAFT = "e4steam_minecraft";
    private static final String PRESENCE_PLAYER = "e4steam_player";
    private static final String PRESENCE_HOSTING = "e4steam_hosting";
    private static final String PRESENCE_INVITE_PREFIX = "e4steam_invite_";
    private static final String PRESENCE_JOIN_REQUEST_PREFIX = "e4steam_join_request_";
    private static final String PROTOCOL_VERSION = "2";

    private final String minecraftVersion = MinecraftVersion.current();
    private final String minecraftPlayerName = Minecraft.getInstance().getUser().getName();
    private final SteamUtils utils;
    private final SteamFriends friends;
    private final ArrayDeque<SteamSocialSnapshot.Invitation> invitationHistory = new ArrayDeque<>();
    private final Map<Long, CachedPresence> presenceCache = new HashMap<>();
    private final Map<Long, OutgoingInviteSignal> outgoingInviteSignals = new LinkedHashMap<>();
    private final Map<Long, OutgoingJoinRequestSignal> outgoingJoinRequestSignals = new LinkedHashMap<>();
    private Listener listener;
    private long nextInvitationPollMillis;

    SteamSocialProvider(SteamUtils utils) {
        this.utils = utils;
        friends = new SteamFriends(new SteamFriendsCallback() {
            @Override
            public void onGameLobbyJoinRequested(SteamID lobby, SteamID friend) {
                long lobbyId = nativeHandle(lobby);
                long friendId = nativeHandle(friend);
                rememberInvitation(friendId, personaName(friend), SteamSocialSnapshot.Direction.RECEIVED, lobbyId);
                Listener current = listener;
                if (current != null) {
                    current.onLobbyJoinRequested(lobbyId, friendId);
                }
            }

            @Override
            public void onGameRichPresenceJoinRequested(SteamID friend, String connect) {
                OptionalLong parsed = parseLobbyConnect(connect);
                if (parsed.isEmpty()) {
                    return;
                }
                long friendId = nativeHandle(friend);
                rememberInvitation(
                        friendId,
                        personaName(friend),
                        SteamSocialSnapshot.Direction.RECEIVED,
                        parsed.getAsLong()
                );
                Listener current = listener;
                if (current != null) {
                    current.onLobbyJoinRequested(parsed.getAsLong(), friendId);
                }
            }
        });
        publishPlayingPresenceQuietly();
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    SteamSocialSnapshot snapshot(long localSteamId, long nowMillis) {
        cleanupInvitations(nowMillis);
        int count = Math.max(0, friends.getFriendCount(SteamFriends.FriendFlags.Immediate));
        ArrayList<SteamSocialSnapshot.Friend> result = new ArrayList<>(count);
        Set<Long> currentFriendIds = new HashSet<>(count);
        for (int index = 0; index < count; index++) {
            SteamID id = friends.getFriendByIndex(index, SteamFriends.FriendFlags.Immediate);
            long steamId = nativeHandle(id);
            if (id == null || steamId == 0) {
                continue;
            }
            currentFriendIds.add(steamId);
            friends.requestFriendRichPresence(id);
            SteamFriends.FriendGameInfo game = new SteamFriends.FriendGameInfo();
            boolean playingSpacewar = friends.getFriendGamePlayed(id, game) && game.getGameID() == 480L;
            String marker = safeRichPresence(id, PRESENCE_MARKER);
            String protocol = safeRichPresence(id, PRESENCE_PROTOCOL);
            String friendMinecraft = safeRichPresence(id, PRESENCE_MINECRAFT);
            String friendMinecraftName = safeRichPresence(id, PRESENCE_PLAYER);
            boolean hosting = "1".equals(safeRichPresence(id, PRESENCE_HOSTING));
            scanInvitationSignals(id, steamId, localSteamId, friendMinecraftName, nowMillis);
            scanJoinRequestSignals(id, steamId, localSteamId, friendMinecraftName, nowMillis);
            if ("1".equals(marker) && !friendMinecraft.isBlank()) {
                presenceCache.put(steamId, new CachedPresence(
                        protocol, friendMinecraftName, friendMinecraft, nowMillis
                ));
            } else {
                CachedPresence cached = presenceCache.get(steamId);
                if (playingSpacewar && cached != null
                        && nowMillis - cached.capturedAtMillis() <= PRESENCE_CACHE_MILLIS) {
                    marker = "1";
                    protocol = cached.protocol();
                    friendMinecraftName = cached.minecraftName();
                    friendMinecraft = cached.minecraftVersion();
                } else if (!playingSpacewar) {
                    presenceCache.remove(steamId);
                }
            }
            OptionalLong lobbyId = parseLobbyConnect(safeRichPresence(id, "connect"));
            boolean e4steam = "1".equals(marker);
            boolean compatible = e4steam
                    && PROTOCOL_VERSION.equals(protocol)
                    && minecraftVersion.equals(friendMinecraft);
            int avatarHandle = friends.getMediumFriendAvatar(id);
            if (avatarHandle < 0) {
                friends.requestUserInformation(id, false);
            }
            result.add(new SteamSocialSnapshot.Friend(
                    steamId,
                    personaName(id),
                    mapPresence(friends.getFriendPersonaState(id)),
                    e4steam,
                    playingSpacewar,
                    e4steam && hosting,
                    e4steam && lobbyId.isPresent(),
                    compatible,
                    friendMinecraftName,
                    friendMinecraft,
                    lobbyId.orElse(0L),
                    readAvatar(avatarHandle)
            ));
        }
        presenceCache.keySet().retainAll(currentFriendIds);
        SteamID local = SteamID.createFromNativeHandle(localSteamId);
        return new SteamSocialSnapshot(
                friends.getPersonaName(),
                readAvatar(friends.getMediumFriendAvatar(local)),
                SteamSocialSnapshot.sortFriends(result),
                List.copyOf(invitationHistory),
                nowMillis
        );
    }

    void publishHosting(long lobbyId, boolean advertiseJoin) throws IOException {
        friends.clearRichPresence();
        outgoingInviteSignals.clear();
        outgoingJoinRequestSignals.clear();
        publishMinecraftIdentity();
        requirePresence(PRESENCE_HOSTING, "1");
        requirePresence("status", "Hosting a Minecraft LAN world");
        if (advertiseJoin) {
            requirePresence("connect", connectString(lobbyId));
        }
    }

    void clearHostingPresence() {
        friends.clearRichPresence();
        outgoingInviteSignals.clear();
        outgoingJoinRequestSignals.clear();
        publishPlayingPresenceQuietly();
    }

    boolean inviteToLobby(long remoteSteamId, long lobbyId) throws IOException {
        SteamID remote = steamId(remoteSteamId);
        if (friends.getFriendRelationship(remote) != SteamFriends.FriendRelationship.Friend) {
            throw new IOException("The selected Steam user is not a direct friend");
        }
        if (friends.getFriendPersonaState(remote) == SteamFriends.PersonaState.Offline) {
            return false;
        }
        if (!friends.inviteUserToGame(remote, connectString(lobbyId))) {
            return false;
        }
        publishInviteSignal(remoteSteamId, lobbyId, System.currentTimeMillis());
        rememberInvitation(
                remoteSteamId,
                personaName(remote),
                SteamSocialSnapshot.Direction.SENT,
                lobbyId
        );
        return true;
    }

    boolean requestToJoin(long remoteSteamId) throws IOException {
        SteamID remote = steamId(remoteSteamId);
        if (friends.getFriendRelationship(remote) != SteamFriends.FriendRelationship.Friend) {
            throw new IOException("The selected Steam user is not a direct friend");
        }
        if (friends.getFriendPersonaState(remote) == SteamFriends.PersonaState.Offline
                || !"1".equals(safeRichPresence(remote, PRESENCE_MARKER))
                || !PROTOCOL_VERSION.equals(safeRichPresence(remote, PRESENCE_PROTOCOL))
                || !minecraftVersion.equals(safeRichPresence(remote, PRESENCE_MINECRAFT))
                || !"1".equals(safeRichPresence(remote, PRESENCE_HOSTING))) {
            return false;
        }
        long nowMillis = System.currentTimeMillis();
        long expiresAtMillis = publishJoinRequestSignal(remoteSteamId, nowMillis);
        rememberSocialRequest(
                remoteSteamId,
                personaName(remote),
                SteamSocialSnapshot.Direction.JOIN_REQUEST_SENT,
                0L,
                nowMillis,
                expiresAtMillis
        );
        return true;
    }

    OptionalLong friendLobby(long remoteSteamId) {
        SteamID remote = steamId(remoteSteamId);
        OptionalLong result = parseLobbyConnect(safeRichPresence(remote, "connect"));
        if (result.isEmpty()) {
            friends.requestFriendRichPresence(remote);
        }
        return result;
    }

    boolean isDirectFriend(long remoteSteamId) {
        return friends.getFriendRelationship(steamId(remoteSteamId)) == SteamFriends.FriendRelationship.Friend;
    }

    String friendName(long remoteSteamId) {
        return personaName(steamId(remoteSteamId));
    }

    void openFriendsOverlay() throws IOException {
        requireOverlay();
        friends.activateGameOverlay(SteamFriends.OverlayDialog.Friends);
    }

    void openInviteOverlay(long lobbyId) throws IOException {
        requireOverlay();
        friends.activateGameOverlayInviteDialog(steamId(lobbyId));
    }

    void openFriendProfile(long remoteSteamId) throws IOException {
        requireOverlay();
        friends.activateGameOverlayToUser(SteamFriends.OverlayToUserDialog.SteamID, steamId(remoteSteamId));
    }

    void markInvitationUnavailable(long lobbyId) {
        if (lobbyId == 0) {
            return;
        }
        ArrayDeque<SteamSocialSnapshot.Invitation> updated = new ArrayDeque<>();
        for (SteamSocialSnapshot.Invitation invitation : invitationHistory) {
            updated.addLast(invitation.lobbyId() == lobbyId ? invitation.cancel() : invitation);
        }
        invitationHistory.clear();
        invitationHistory.addAll(updated);
    }

    void markJoinRequestUnavailable(long remoteSteamId, long generation) {
        ArrayDeque<SteamSocialSnapshot.Invitation> updated = new ArrayDeque<>();
        for (SteamSocialSnapshot.Invitation request : invitationHistory) {
            boolean matches = request.steamId() == remoteSteamId
                    && request.direction() == SteamSocialSnapshot.Direction.JOIN_REQUEST_RECEIVED
                    && (generation <= 0 || request.expiresAtMillis() == generation);
            updated.addLast(matches ? request.cancel() : request);
        }
        invitationHistory.clear();
        invitationHistory.addAll(updated);
    }

    void cleanupInvitations(long nowMillis) {
        pruneOutgoingInviteSignals(nowMillis);
        pruneOutgoingJoinRequestSignals(nowMillis);
        Iterator<SteamSocialSnapshot.Invitation> iterator = invitationHistory.descendingIterator();
        while (iterator.hasNext()) {
            SteamSocialSnapshot.Invitation invitation = iterator.next();
            if (invitation.expiresAtMillis() + INVITATION_TTL_MILLIS <= nowMillis) {
                iterator.remove();
            }
        }
    }

    void pollIncomingInvitations(long localSteamId, long nowMillis) {
        if (localSteamId == 0 || nowMillis < nextInvitationPollMillis) return;
        nextInvitationPollMillis = nowMillis + INVITATION_POLL_INTERVAL_MILLIS;
        int count = Math.max(0, friends.getFriendCount(SteamFriends.FriendFlags.Immediate));
        for (int index = 0; index < count; index++) {
            SteamID id = friends.getFriendByIndex(index, SteamFriends.FriendFlags.Immediate);
            long steamId = nativeHandle(id);
            if (id == null || steamId == 0) continue;
            SteamFriends.FriendGameInfo game = new SteamFriends.FriendGameInfo();
            if (!friends.getFriendGamePlayed(id, game) || game.getGameID() != 480L) continue;
            friends.requestFriendRichPresence(id);
            scanInvitationSignals(
                    id,
                    steamId,
                    localSteamId,
                    safeRichPresence(id, PRESENCE_PLAYER),
                    nowMillis
            );
            scanJoinRequestSignals(
                    id,
                    steamId,
                    localSteamId,
                    safeRichPresence(id, PRESENCE_PLAYER),
                    nowMillis
            );
        }
    }

    private void scanInvitationSignals(
            SteamID id,
            long steamId,
            long localSteamId,
            String friendMinecraftName,
            long nowMillis
    ) {
        for (int slot = 0; slot < MAX_OUTGOING_INVITE_SIGNALS; slot++) {
            InviteSignal signal = parseInviteSignal(safeRichPresence(id, PRESENCE_INVITE_PREFIX + slot));
            if (signal == null || signal.targetSteamId() != localSteamId
                    || nowMillis >= signal.expiresAtMillis()) {
                continue;
            }
            if (rememberIncomingSignal(
                    steamId,
                    personaName(id),
                    signal.lobbyId(),
                    nowMillis,
                    signal.expiresAtMillis()
            )) {
                Listener current = listener;
                if (current != null) {
                    current.onGuiInvitationReceived(
                            signal.lobbyId(),
                            steamId,
                            personaName(id),
                            friendMinecraftName,
                            signal.expiresAtMillis()
                    );
                }
            }
        }
    }

    private void scanJoinRequestSignals(
            SteamID id,
            long steamId,
            long localSteamId,
            String friendMinecraftName,
            long nowMillis
    ) {
        for (int slot = 0; slot < MAX_OUTGOING_INVITE_SIGNALS; slot++) {
            JoinRequestSignal signal = parseJoinRequestSignal(
                    safeRichPresence(id, PRESENCE_JOIN_REQUEST_PREFIX + slot)
            );
            if (signal == null || signal.targetSteamId() != localSteamId
                    || nowMillis >= signal.expiresAtMillis()) {
                continue;
            }
            if (rememberIncomingJoinRequest(
                    steamId,
                    personaName(id),
                    nowMillis,
                    signal.expiresAtMillis()
            )) {
                Listener current = listener;
                if (current != null) {
                    current.onGuiJoinRequestReceived(
                            steamId,
                            personaName(id),
                            friendMinecraftName,
                            signal.expiresAtMillis()
                    );
                }
            }
        }
    }

    private void rememberInvitation(
            long steamId,
            String name,
            SteamSocialSnapshot.Direction direction,
            long lobbyId
    ) {
        long now = System.currentTimeMillis();
        rememberSocialRequest(steamId, name, direction, lobbyId, now, now + INVITATION_TTL_MILLIS);
    }

    private void rememberSocialRequest(
            long steamId,
            String name,
            SteamSocialSnapshot.Direction direction,
            long lobbyId,
            long nowMillis,
            long expiresAtMillis
    ) {
        invitationHistory.removeIf(entry -> entry.steamId() == steamId && entry.direction() == direction);
        invitationHistory.addFirst(new SteamSocialSnapshot.Invitation(
                steamId,
                name,
                direction,
                lobbyId,
                nowMillis,
                expiresAtMillis,
                false
        ));
        while (invitationHistory.size() > MAX_INVITATIONS) {
            invitationHistory.removeLast();
        }
    }

    private boolean rememberIncomingSignal(
            long steamId,
            String name,
            long lobbyId,
            long nowMillis,
            long expiresAtMillis
    ) {
        Iterator<SteamSocialSnapshot.Invitation> iterator = invitationHistory.iterator();
        while (iterator.hasNext()) {
            SteamSocialSnapshot.Invitation invitation = iterator.next();
            if (invitation.steamId() == steamId
                    && invitation.direction() == SteamSocialSnapshot.Direction.RECEIVED
                    && invitation.lobbyId() == lobbyId) {
                // The same rich-presence value is polled repeatedly. A canceled entry must
                // stay hidden until the host explicitly republishes this lobby with a newer
                // expiry, which represents a fresh invitation after the guest disconnected.
                if (!invitation.canceled() || expiresAtMillis <= invitation.expiresAtMillis()) {
                    return false;
                }
                iterator.remove();
                break;
            }
        }
        invitationHistory.addFirst(new SteamSocialSnapshot.Invitation(
                steamId,
                name,
                SteamSocialSnapshot.Direction.RECEIVED,
                lobbyId,
                nowMillis,
                Math.min(expiresAtMillis, nowMillis + INVITATION_TTL_MILLIS),
                false
        ));
        while (invitationHistory.size() > MAX_INVITATIONS) {
            invitationHistory.removeLast();
        }
        return true;
    }

    private boolean rememberIncomingJoinRequest(
            long steamId,
            String name,
            long nowMillis,
            long expiresAtMillis
    ) {
        Iterator<SteamSocialSnapshot.Invitation> iterator = invitationHistory.iterator();
        while (iterator.hasNext()) {
            SteamSocialSnapshot.Invitation request = iterator.next();
            if (request.steamId() == steamId
                    && request.direction() == SteamSocialSnapshot.Direction.JOIN_REQUEST_RECEIVED) {
                if (!request.canceled() || expiresAtMillis <= request.expiresAtMillis()) return false;
                iterator.remove();
                break;
            }
        }
        rememberSocialRequest(
                steamId,
                name,
                SteamSocialSnapshot.Direction.JOIN_REQUEST_RECEIVED,
                0L,
                nowMillis,
                Math.min(expiresAtMillis, nowMillis + INVITATION_TTL_MILLIS)
        );
        return true;
    }

    private void publishInviteSignal(long targetSteamId, long lobbyId, long nowMillis) {
        pruneOutgoingInviteSignals(nowMillis);
        OutgoingInviteSignal existing = outgoingInviteSignals.get(targetSteamId);
        int slot = existing == null ? firstFreeInviteSlot() : existing.slot();
        if (slot < 0) {
            Iterator<Map.Entry<Long, OutgoingInviteSignal>> iterator = outgoingInviteSignals.entrySet().iterator();
            if (!iterator.hasNext()) return;
            Map.Entry<Long, OutgoingInviteSignal> oldest = iterator.next();
            slot = oldest.getValue().slot();
            iterator.remove();
        }
        long expiresAtMillis = nowMillis + INVITATION_TTL_MILLIS;
        OutgoingInviteSignal signal = new OutgoingInviteSignal(slot, lobbyId, expiresAtMillis);
        outgoingInviteSignals.put(targetSteamId, signal);
        try {
            requirePresence(PRESENCE_INVITE_PREFIX + slot,
                    encodeInviteSignal(targetSteamId, lobbyId, expiresAtMillis));
        } catch (IOException failure) {
            outgoingInviteSignals.remove(targetSteamId);
            E4steamClient.LOGGER.debug("Steam rejected e4steam GUI invitation presence", failure);
        }
    }

    private long publishJoinRequestSignal(long targetSteamId, long nowMillis) throws IOException {
        pruneOutgoingJoinRequestSignals(nowMillis);
        OutgoingJoinRequestSignal existing = outgoingJoinRequestSignals.get(targetSteamId);
        int slot = existing == null ? firstFreeJoinRequestSlot() : existing.slot();
        if (slot < 0) {
            Iterator<Map.Entry<Long, OutgoingJoinRequestSignal>> iterator =
                    outgoingJoinRequestSignals.entrySet().iterator();
            if (!iterator.hasNext()) throw new IOException("Too many pending join requests");
            Map.Entry<Long, OutgoingJoinRequestSignal> oldest = iterator.next();
            slot = oldest.getValue().slot();
            iterator.remove();
        }
        long expiresAtMillis = nowMillis + INVITATION_TTL_MILLIS;
        requirePresence(
                PRESENCE_JOIN_REQUEST_PREFIX + slot,
                encodeJoinRequestSignal(targetSteamId, expiresAtMillis)
        );
        outgoingJoinRequestSignals.put(
                targetSteamId,
                new OutgoingJoinRequestSignal(slot, expiresAtMillis)
        );
        return expiresAtMillis;
    }

    private void pruneOutgoingInviteSignals(long nowMillis) {
        Iterator<Map.Entry<Long, OutgoingInviteSignal>> iterator = outgoingInviteSignals.entrySet().iterator();
        while (iterator.hasNext()) {
            OutgoingInviteSignal signal = iterator.next().getValue();
            if (nowMillis >= signal.expiresAtMillis()) {
                friends.setRichPresence(PRESENCE_INVITE_PREFIX + signal.slot(), "");
                iterator.remove();
            }
        }
    }

    private void pruneOutgoingJoinRequestSignals(long nowMillis) {
        Iterator<Map.Entry<Long, OutgoingJoinRequestSignal>> iterator =
                outgoingJoinRequestSignals.entrySet().iterator();
        while (iterator.hasNext()) {
            OutgoingJoinRequestSignal signal = iterator.next().getValue();
            if (nowMillis >= signal.expiresAtMillis()) {
                friends.setRichPresence(PRESENCE_JOIN_REQUEST_PREFIX + signal.slot(), "");
                iterator.remove();
            }
        }
    }

    private int firstFreeInviteSlot() {
        for (int slot = 0; slot < MAX_OUTGOING_INVITE_SIGNALS; slot++) {
            int candidate = slot;
            if (outgoingInviteSignals.values().stream().noneMatch(signal -> signal.slot() == candidate)) {
                return slot;
            }
        }
        return -1;
    }

    private int firstFreeJoinRequestSlot() {
        for (int slot = 0; slot < MAX_OUTGOING_INVITE_SIGNALS; slot++) {
            int candidate = slot;
            if (outgoingJoinRequestSignals.values().stream()
                    .noneMatch(signal -> signal.slot() == candidate)) {
                return slot;
            }
        }
        return -1;
    }

    static String encodeInviteSignal(long targetSteamId, long lobbyId, long expiresAtMillis) {
        return Long.toUnsignedString(targetSteamId)
                + ":" + Long.toUnsignedString(lobbyId)
                + ":" + expiresAtMillis;
    }

    static InviteSignal parseInviteSignal(String value) {
        if (value == null || value.isBlank() || value.length() > 96) return null;
        String[] parts = value.split(":", -1);
        if (parts.length != 3) return null;
        try {
            long target = Long.parseUnsignedLong(parts[0]);
            long lobby = Long.parseUnsignedLong(parts[1]);
            long expires = Long.parseLong(parts[2]);
            return target == 0 || lobby == 0 || expires <= 0
                    ? null
                    : new InviteSignal(target, lobby, expires);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static String encodeJoinRequestSignal(long targetSteamId, long expiresAtMillis) {
        return Long.toUnsignedString(targetSteamId) + ":" + expiresAtMillis;
    }

    static JoinRequestSignal parseJoinRequestSignal(String value) {
        if (value == null || value.isBlank() || value.length() > 64) return null;
        String[] parts = value.split(":", -1);
        if (parts.length != 2) return null;
        try {
            long target = Long.parseUnsignedLong(parts[0]);
            long expires = Long.parseLong(parts[1]);
            return target == 0 || expires <= 0 ? null : new JoinRequestSignal(target, expires);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private SteamSocialSnapshot.Avatar readAvatar(int handle) {
        if (handle <= 0) {
            return SteamSocialSnapshot.Avatar.empty();
        }
        int[] size = new int[2];
        if (!utils.getImageSize(handle, size)) {
            return SteamSocialSnapshot.Avatar.empty();
        }
        int width = size[0];
        int height = size[1];
        if (width < 1 || height < 1 || width > 256 || height > 256) {
            return SteamSocialSnapshot.Avatar.empty();
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4);
        try {
            if (!utils.getImageRGBA(handle, buffer)) {
                return SteamSocialSnapshot.Avatar.empty();
            }
        } catch (Exception ignored) {
            return SteamSocialSnapshot.Avatar.empty();
        }
        buffer.rewind();
        byte[] rgba = new byte[buffer.remaining()];
        buffer.get(rgba);
        return new SteamSocialSnapshot.Avatar(width, height, rgba);
    }

    private void requirePresence(String key, String value) throws IOException {
        if (!friends.setRichPresence(key, value)) {
            throw new IOException("Steam rejected rich presence key " + key);
        }
    }

    private void publishMinecraftIdentity() throws IOException {
        requirePresence(PRESENCE_MARKER, "1");
        requirePresence(PRESENCE_PROTOCOL, PROTOCOL_VERSION);
        requirePresence(PRESENCE_MINECRAFT, minecraftVersion);
        requirePresence(PRESENCE_PLAYER, minecraftPlayerName);
    }

    private void publishPlayingPresenceQuietly() {
        try {
            publishMinecraftIdentity();
            requirePresence("status", "Playing Minecraft " + minecraftVersion);
        } catch (IOException failure) {
            E4steamClient.LOGGER.debug("Steam rejected e4steam playing presence", failure);
        }
    }

    private void requireOverlay() throws IOException {
        if (!utils.isOverlayEnabled()) {
            throw new IOException("Steam Overlay is unavailable");
        }
    }

    private String safeRichPresence(SteamID id, String key) {
        String value = friends.getFriendRichPresence(id, key);
        return value == null ? "" : value;
    }

    private String personaName(SteamID id) {
        String name = id == null ? null : friends.getFriendPersonaName(id);
        return name == null ? "" : name;
    }

    static OptionalLong parseLobbyConnect(String connect) {
        if (connect == null || !connect.startsWith(LOBBY_CONNECT_PREFIX)) {
            return OptionalLong.empty();
        }
        try {
            long lobbyId = Long.parseUnsignedLong(connect.substring(LOBBY_CONNECT_PREFIX.length()));
            return lobbyId == 0 ? OptionalLong.empty() : OptionalLong.of(lobbyId);
        } catch (NumberFormatException ignored) {
            return OptionalLong.empty();
        }
    }

    static SteamSocialSnapshot.Presence mapPresence(SteamFriends.PersonaState state) {
        if (state == null || state == SteamFriends.PersonaState.Offline
                || state == SteamFriends.PersonaState.Invisible) {
            return SteamSocialSnapshot.Presence.OFFLINE;
        }
        if (state == SteamFriends.PersonaState.Busy) {
            return SteamSocialSnapshot.Presence.BUSY;
        }
        if (state == SteamFriends.PersonaState.Away || state == SteamFriends.PersonaState.Snooze) {
            return SteamSocialSnapshot.Presence.AWAY;
        }
        return SteamSocialSnapshot.Presence.ONLINE;
    }

    private static String connectString(long lobbyId) {
        return LOBBY_CONNECT_PREFIX + Long.toUnsignedString(lobbyId);
    }

    private static SteamID steamId(long value) {
        return SteamID.createFromNativeHandle(value);
    }

    private static long nativeHandle(SteamID id) {
        return id == null ? 0L : SteamNativeHandle.getNativeHandle(id);
    }

    @Override
    public void close() {
        listener = null;
        friends.clearRichPresence();
        friends.dispose();
        invitationHistory.clear();
        presenceCache.clear();
        outgoingInviteSignals.clear();
        outgoingJoinRequestSignals.clear();
    }

    interface Listener {
        void onLobbyJoinRequested(long lobbyId, long friendSteamId);

        void onGuiInvitationReceived(
                long lobbyId,
                long friendSteamId,
                String friendName,
                String minecraftName,
                long invitationGeneration
        );

        void onGuiJoinRequestReceived(
                long friendSteamId,
                String friendName,
                String minecraftName,
                long requestGeneration
        );
    }

    private record CachedPresence(
            String protocol,
            String minecraftName,
            String minecraftVersion,
            long capturedAtMillis
    ) {
    }

    record InviteSignal(long targetSteamId, long lobbyId, long expiresAtMillis) {
    }

    record JoinRequestSignal(long targetSteamId, long expiresAtMillis) {
    }

    private record OutgoingInviteSignal(int slot, long lobbyId, long expiresAtMillis) {
    }

    private record OutgoingJoinRequestSignal(int slot, long expiresAtMillis) {
    }
}
