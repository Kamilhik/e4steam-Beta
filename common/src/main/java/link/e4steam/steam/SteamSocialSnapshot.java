package link.e4steam.steam;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;

/** Immutable Steam social state safe to pass from the Steam worker to Minecraft's render thread. */
public record SteamSocialSnapshot(
        String localPersonaName,
        Avatar localAvatar,
        List<Friend> friends,
        List<Invitation> invitations,
        long capturedAtMillis
) {
    public SteamSocialSnapshot {
        localPersonaName = localPersonaName == null ? "" : localPersonaName;
        localAvatar = localAvatar == null ? Avatar.empty() : localAvatar;
        friends = friends == null ? List.of() : List.copyOf(friends);
        invitations = invitations == null ? List.of() : List.copyOf(invitations);
    }

    public static SteamSocialSnapshot empty() {
        return new SteamSocialSnapshot("", Avatar.empty(), List.of(), List.of(), 0L);
    }

    /** Native Friends-list ordering: joinable worlds, online friends, then offline friends. */
    public static List<Friend> sortFriends(List<Friend> source) {
        ArrayList<Friend> sorted = new ArrayList<>(source == null ? List.of() : source);
        sorted.sort(Comparator
                .comparingInt(SteamSocialSnapshot::sortRank)
                .thenComparing(Friend::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparingLong(Friend::steamId));
        return List.copyOf(sorted);
    }

    /** Friends whose e4steam rich presence proves that Minecraft is currently active. */
    public static List<Friend> minecraftFriends(List<Friend> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream().filter(Friend::playingMinecraft).toList();
    }

    /** Case-insensitive Steam persona-name filtering for the friends screen. */
    public static List<Friend> filterByName(List<Friend> source, String query) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        String normalized = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return List.copyOf(source);
        }
        return source.stream()
                .filter(friend -> friend.name().toLowerCase(Locale.ROOT).contains(normalized)
                        || friend.minecraftName().toLowerCase(Locale.ROOT).contains(normalized))
                .toList();
    }

    private static int sortRank(Friend friend) {
        if (friend.joinable()) {
            return 0;
        }
        return friend.presence() == Presence.OFFLINE ? 2 : 1;
    }

    public record Friend(
            long steamId,
            String name,
            Presence presence,
            boolean e4steamActive,
            boolean playingSpacewar,
            boolean hosting,
            boolean joinable,
            boolean compatible,
            String minecraftName,
            String minecraftVersion,
            long lobbyId,
            Avatar avatar
    ) {
        public Friend {
            name = name == null || name.isBlank() ? Long.toUnsignedString(steamId) : name;
            minecraftName = minecraftName == null ? "" : minecraftName;
            minecraftVersion = minecraftVersion == null ? "" : minecraftVersion;
            avatar = avatar == null ? Avatar.empty() : avatar;
        }

        public Friend(
                long steamId,
                String name,
                Presence presence,
                boolean e4steamActive,
                boolean playingSpacewar,
                boolean joinable,
                boolean compatible,
                String minecraftName,
                String minecraftVersion,
                long lobbyId,
                Avatar avatar
        ) {
            this(steamId, name, presence, e4steamActive, playingSpacewar, joinable, joinable, compatible,
                    minecraftName, minecraftVersion, lobbyId, avatar);
        }

        public Friend(
                long steamId,
                String name,
                Presence presence,
                boolean e4steamActive,
                boolean playingSpacewar,
                boolean joinable,
                boolean compatible,
                String minecraftVersion,
                long lobbyId,
                Avatar avatar
        ) {
            this(steamId, name, presence, e4steamActive, playingSpacewar, joinable, joinable, compatible,
                    "", minecraftVersion, lobbyId, avatar);
        }

        public boolean playingMinecraft() {
            return e4steamActive && !minecraftVersion.isBlank();
        }
    }

    public record Avatar(int width, int height, byte[] rgba) {
        public Avatar {
            rgba = rgba == null ? new byte[0] : rgba.clone();
            if (width < 1 || height < 1 || rgba.length != width * height * 4) {
                width = 0;
                height = 0;
                rgba = new byte[0];
            }
        }

        @Override
        public byte[] rgba() {
            return rgba.clone();
        }

        public boolean available() {
            return width > 0 && height > 0;
        }

        public static Avatar empty() {
            return new Avatar(0, 0, new byte[0]);
        }
    }

    public record Invitation(
            long steamId,
            String friendName,
            Direction direction,
            long lobbyId,
            long createdAtMillis,
            long expiresAtMillis,
            boolean canceled
    ) {
        public Invitation {
            friendName = friendName == null || friendName.isBlank()
                    ? Long.toUnsignedString(steamId)
                    : friendName;
        }

        public boolean actionable(long nowMillis) {
            return (direction == Direction.RECEIVED && lobbyId != 0
                    || direction == Direction.JOIN_REQUEST_RECEIVED)
                    && !canceled
                    && nowMillis < expiresAtMillis;
        }

        public Invitation cancel() {
            return canceled ? this : new Invitation(
                    steamId,
                    friendName,
                    direction,
                    lobbyId,
                    createdAtMillis,
                    expiresAtMillis,
                    true
            );
        }
    }

    public enum Presence {
        OFFLINE,
        ONLINE,
        BUSY,
        AWAY
    }

    public enum Direction {
        RECEIVED,
        SENT,
        JOIN_REQUEST_RECEIVED,
        JOIN_REQUEST_SENT
    }
}
