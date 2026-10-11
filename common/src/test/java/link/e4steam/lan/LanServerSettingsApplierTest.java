package link.e4steam.lan;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LanServerSettingsApplierTest {
    @AfterEach void reset() { LanSettings.closed(); }
    enum Mode { SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR }
    static class Players {
        private final int maxPlayers = Integer.parseInt("8");
        boolean cheats;
        final java.util.List<Client> clients = new java.util.ArrayList<>();
        public java.util.List<Client> getPlayers() { return clients; }
        public void sendPlayerPermissionLevel(Client client) {
            client.commandsAvailable = cheats || client.explicitOperator;
            client.syncs++;
        }
        public void setAllowCheatsForAllPlayers(boolean enabled) { cheats = enabled; }
    }
    static class Client {
        boolean commandsAvailable;
        boolean explicitOperator;
        int syncs;
    }

    @Test void togglingCheatsUpdatesAlreadyConnectedHostAndGuestWithoutRejoining() {
        Integrated server = new Integrated();
        Client host = new Client();
        Client guest = new Client();
        Client operator = new Client();
        operator.explicitOperator = true;
        server.players.clients.addAll(java.util.Arrays.asList(host, guest, operator));
        LanServerSettingsApplier.apply(server, LanSettings.defaults().withAllowCheats(false));
        assertFalse(host.commandsAvailable);
        assertFalse(guest.commandsAvailable);
        LanServerSettingsApplier.apply(server, LanSettings.defaults().withAllowCheats(true));
        assertTrue(host.commandsAvailable);
        assertTrue(guest.commandsAvailable);
        LanServerSettingsApplier.apply(server, LanSettings.defaults().withAllowCheats(false));
        assertFalse(host.commandsAvailable);
        assertFalse(guest.commandsAvailable);
        assertTrue(operator.commandsAvailable);
        assertEquals(3, host.syncs);
        assertEquals(3, guest.syncs);
    }

    static class ServerBase {
        final Players players = new Players();
        boolean online = true, pvp = true;
        Mode mode = Mode.SURVIVAL;
        public Players getPlayerList() { return players; }
        public void setUsesAuthentication(boolean value) { online = value; }
        public void setPvpAllowed(boolean value) { pvp = value; }
        public void setDefaultGameType(Mode value) { mode = value; }
    }
    static class Integrated extends ServerBase {
        int port;
        public boolean publishServer(Mode mode, boolean cheats, int port) {
            this.port = port; return true;
        }
    }

    @Test void appliesInheritedServerHooksAndTheActualPlayerListLimit() {
        Integrated server = new Integrated();
        LanSettings.Snapshot settings = LanSettings.defaults().withMaxPlayers(20).withPort(30000)
                .withOnlineMode(false).withPvp(false).withGameMode("creative").withAllowCheats(true);
        LanServerSettingsApplier.apply(server, settings);
        assertFalse(server.online);
        assertFalse(server.pvp);
        assertTrue(server.players.cheats);
        assertEquals(Mode.CREATIVE, server.mode);
        assertEquals(20, server.players.maxPlayers);
        assertTrue(LanServerSettingsApplier.publish(server, settings));
        assertEquals(30000, server.port);
    }

    @Test void aMissingRequiredHookFailsBeforeChangingAuthentication() {
        class Unsupported {
            boolean online = true;
            public Players getPlayerList() { return new Players(); }
            public void setUsesAuthentication(boolean value) { online = value; }
        }
        Unsupported server = new Unsupported();
        assertThrows(IllegalStateException.class, () -> LanServerSettingsApplier.apply(
                server, LanSettings.defaults().withOnlineMode(false)));
        assertTrue(server.online);
        assertNull(LanSettings.active(server));
    }

    enum MultiplayerScope { OFF, LAN }
    static class Rules {
        public static final Object PVP = new Object();
        boolean pvp = true;
        public void set(Object rule, Object value, Modern263 server) {
            assertSame(PVP, rule);
            pvp = (Boolean) value;
        }
    }
    static class Modern263 {
        final Rules rules = new Rules();
        boolean online = true, cheats;
        int port;
        Mode mode;
        MultiplayerScope scope;
        public Object getPlayerList() { return new Object(); }
        public void setUsesAuthentication(boolean value) { online = value; }
        public Rules getGameRules() { return rules; }
        public void setGuestCommandAccess(boolean value) { cheats = value; }
        public void setDefaultGameType(Mode value) { mode = value; }
        public boolean publishServer(MultiplayerScope scope, boolean cheats, int port) {
            this.scope = scope; this.cheats = cheats; this.port = port;
            return true;
        }
    }

    @Test void minecraft263UsesLanScopeGuestPermissionsAndGameRulePvp() {
        Modern263 server = new Modern263();
        LanSettings.Snapshot settings = LanSettings.defaults().withGameMode("creative")
                .withAllowCheats(true).withPvp(false).withOnlineMode(false)
                .withMaxPlayers(24).withPort(30001);
        LanServerSettingsApplier.apply(server, settings);
        assertTrue(LanServerSettingsApplier.publish(server, settings));
        assertSame(MultiplayerScope.LAN, server.scope);
        assertSame(Mode.CREATIVE, server.mode);
        assertTrue(server.cheats);
        assertFalse(server.rules.pvp);
        assertFalse(server.online);
        assertEquals(30001, server.port);
        assertEquals(24, LanSettings.active(server).maxPlayers());
    }
}
