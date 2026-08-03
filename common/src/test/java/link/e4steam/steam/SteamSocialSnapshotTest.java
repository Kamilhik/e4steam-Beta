package link.e4steam.steam;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SteamSocialSnapshotTest {
    @Test
    void copiesListsBeforeCrossingToMinecraftThread() {
        ArrayList<SteamSocialSnapshot.Friend> friends = new ArrayList<>();
        friends.add(new SteamSocialSnapshot.Friend(
                42L,
                "Alex",
                SteamSocialSnapshot.Presence.ONLINE,
                true,
                true,
                true,
                true,
                "1.20.2",
                123L,
                SteamSocialSnapshot.Avatar.empty()
        ));

        SteamSocialSnapshot snapshot = new SteamSocialSnapshot(
                "Kamilchik",
                SteamSocialSnapshot.Avatar.empty(),
                friends,
                List.of(),
                123L
        );
        friends.clear();

        assertEquals(1, snapshot.friends().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.friends().clear());
    }

    @Test
    void normalizesMissingDisplayData() {
        SteamSocialSnapshot.Friend friend = new SteamSocialSnapshot.Friend(
                42L,
                "",
                SteamSocialSnapshot.Presence.OFFLINE,
                false,
                false,
                false,
                false,
                null,
                0L,
                null
        );

        assertEquals("42", friend.name());
        assertEquals("", friend.minecraftVersion());
    }

    @Test
    void sortsWorldOnlineAndOfflineFriendsInThatOrder() {
        SteamSocialSnapshot.Friend offline = friend(3, "Offline", SteamSocialSnapshot.Presence.OFFLINE, false);
        SteamSocialSnapshot.Friend online = friend(2, "Online", SteamSocialSnapshot.Presence.ONLINE, false);
        SteamSocialSnapshot.Friend world = friend(1, "World", SteamSocialSnapshot.Presence.ONLINE, true);

        assertEquals(
                List.of(world, online, offline),
                SteamSocialSnapshot.sortFriends(List.of(offline, online, world))
        );
    }

    @Test
    void filtersToFriendsWhoseMinecraftPresenceIsVerifiedByE4steam() {
        SteamSocialSnapshot.Friend minecraft = new SteamSocialSnapshot.Friend(
                1L, "Minecraft", SteamSocialSnapshot.Presence.ONLINE,
                true, true, false, true, "26.2", 0L, SteamSocialSnapshot.Avatar.empty()
        );
        SteamSocialSnapshot.Friend genericSpacewar = new SteamSocialSnapshot.Friend(
                2L, "Spacewar", SteamSocialSnapshot.Presence.ONLINE,
                false, true, false, false, "", 0L, SteamSocialSnapshot.Avatar.empty()
        );
        SteamSocialSnapshot.Friend online = new SteamSocialSnapshot.Friend(
                3L, "Online", SteamSocialSnapshot.Presence.ONLINE,
                false, false, false, false, "", 0L, SteamSocialSnapshot.Avatar.empty()
        );

        assertEquals(List.of(minecraft), SteamSocialSnapshot.minecraftFriends(
                List.of(minecraft, genericSpacewar, online)
        ));
        assertTrue(minecraft.playingMinecraft());
        assertFalse(genericSpacewar.playingMinecraft());
    }

    @Test
    void searchesSteamFriendsByPersonaNameIgnoringCase() {
        SteamSocialSnapshot.Friend apple = friend(
                1L, "AppleG", SteamSocialSnapshot.Presence.ONLINE, false
        );
        SteamSocialSnapshot.Friend worker = new SteamSocialSnapshot.Friend(
                2L, "Rabotka", SteamSocialSnapshot.Presence.ONLINE,
                true, true, false, true, "MinecraftNick", "26.2", 0L,
                SteamSocialSnapshot.Avatar.empty()
        );

        assertEquals(List.of(apple), SteamSocialSnapshot.filterByName(List.of(apple, worker), "pPlE"));
        assertEquals(List.of(worker), SteamSocialSnapshot.filterByName(List.of(apple, worker), "craftnick"));
        assertEquals(List.of(apple, worker), SteamSocialSnapshot.filterByName(List.of(apple, worker), "  "));
    }

    @Test
    void canceledAndExpiredInvitationsCannotBeAccepted() {
        SteamSocialSnapshot.Invitation current = new SteamSocialSnapshot.Invitation(
                42L, "Alex", SteamSocialSnapshot.Direction.RECEIVED, 99L, 100L, 200L, false
        );
        assertTrue(current.actionable(199L));
        assertFalse(current.actionable(200L));
        assertFalse(current.cancel().actionable(150L));
    }

    private static SteamSocialSnapshot.Friend friend(
            long id,
            String name,
            SteamSocialSnapshot.Presence presence,
            boolean joinable
    ) {
        return new SteamSocialSnapshot.Friend(
                id,
                name,
                presence,
                joinable,
                joinable,
                joinable,
                false,
                "1.20.2",
                joinable ? 99L : 0L,
                SteamSocialSnapshot.Avatar.empty()
        );
    }
}
