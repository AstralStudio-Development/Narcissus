package cn.starry.narcissus;

import cn.starry.narcissus.functions.auth.AuthenticationProvider;
import cn.starry.narcissus.functions.auth.AuthenticationResult;
import cn.starry.narcissus.functions.commands.CommandManager;
import cn.starry.narcissus.functions.config.Configuration;
import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.world.WorldTimeService;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeCommandTest {

    @Test
    void nonStaffCannotChangePersonalWorldTime() {
        RecordingNetworkManager network = new RecordingNetworkManager(false);

        new CommandManager().handleChat(network, null, "/time 08:00");

        assertNull(network.sentTime);
        assertEquals(List.of("&c只有已认证的 Staff 账号可以使用 /time。"), network.messages);
    }

    @Test
    void staffReceivesOnlyTheRequestedFrozenTime() {
        RecordingNetworkManager network = new RecordingNetworkManager(true);

        new CommandManager().handleChat(network, null, "/time 08:00");

        assertEquals(2000L, network.sentTime.dayTime());
        assertEquals(2000L, Math.floorMod(
                network.sentTime.worldClockTime(), WorldTimeService.TICKS_PER_DAY));
        assertEquals(List.of(
                "&a已将你的世界时间设置为 &e08:00 &7(2000 ticks)&a。",
                "&7下一个 UTC+8 整点将恢复全局同步。"), network.messages);
    }

    @Test
    void syncRestoresCurrentUtc8Time() {
        RecordingNetworkManager network = new RecordingNetworkManager(true);

        new CommandManager().handleChat(network, null, "/time sync");

        assertEquals(2000L, network.sentTime.dayTime());
        assertEquals(List.of(
                "&a已恢复当前 UTC+8 世界时间：&e08:00 &7(2000 ticks)&a。",
                "&7下一个 UTC+8 整点将恢复全局同步。"), network.messages);
    }

    @Test
    void similarlyNamedCommandsAreNotIntercepted() {
        RecordingNetworkManager network = new RecordingNetworkManager(true);

        new CommandManager().handleChat(network, null, "/timezone 08:00");

        assertNull(network.sentTime);
        assertEquals(List.of("&fUnknown command."), network.messages);
    }

    @Test
    void staffGetsUsageForInvalidTime() {
        RecordingNetworkManager network = new RecordingNetworkManager(true);

        new CommandManager().handleChat(network, null, "/time 24:00");

        assertNull(network.sentTime);
        assertEquals(List.of("&e用法：/time <HH:mm|0-23999|sync>"), network.messages);
    }

    @Test
    void networkRequiresBothAuthenticationAndStaffMembership() throws Exception {
        NetworkManager network = new NetworkManager(null, Configuration.builder().build(),
                new StaffAuthenticationProvider(), null);
        try (ServerSocketChannel listener = ServerSocketChannel.open()) {
            listener.bind(new InetSocketAddress("127.0.0.1", 0));
            try (SocketChannel client = SocketChannel.open(listener.getLocalAddress());
                 SocketChannel accepted = listener.accept()) {
                PlayerManager player = new PlayerManager(network, accepted);
                player.setUserName("Admin");

                assertFalse(network.isStaffAccount(player));
                player.setAuthenticated(true);
                assertTrue(network.isStaffAccount(player));
                player.setUserName("Player");
                assertFalse(network.isStaffAccount(player));
            }
        }
    }

    private static final class StaffAuthenticationProvider implements AuthenticationProvider {
        @Override
        public AuthenticationResult register(String username, String credential) {
            return null;
        }

        @Override
        public AuthenticationResult login(String username, String credential) {
            return null;
        }

        @Override
        public AuthenticationResult beginTwoFactorSetup(String username) {
            return null;
        }

        @Override
        public AuthenticationResult confirmTwoFactor(String username, String code) {
            return null;
        }

        @Override
        public boolean isRegistered(String username) {
            return true;
        }

        @Override
        public boolean isStaffAccount(String username) {
            return "Admin".equals(username);
        }

        @Override
        public boolean isTwoFactorConfigured(String username) {
            return true;
        }
    }

    private static final class RecordingNetworkManager extends NetworkManager {
        private final boolean staff;
        private final WorldTimeService timeService = new WorldTimeService(
                Clock.fixed(Instant.parse("2026-08-06T00:00:00Z"), ZoneOffset.UTC));
        private final List<String> messages = new ArrayList<>();
        private WorldTimeService.Snapshot sentTime;

        private RecordingNetworkManager(boolean staff) {
            super(null, Configuration.builder().build());
            this.staff = staff;
        }

        @Override
        public boolean handleAuthenticationCommand(PlayerManager playerManager, String input) {
            return false;
        }

        @Override
        public boolean isStaffAccount(PlayerManager playerManager) {
            return this.staff;
        }

        @Override
        public WorldTimeService getWorldTimeService() {
            return this.timeService;
        }

        @Override
        public void sendWorldTime(PlayerManager playerManager, WorldTimeService.Snapshot snapshot) {
            this.sentTime = snapshot;
        }

        @Override
        public void sendMessage(PlayerManager playerManager, String message) {
            this.messages.add(message);
        }
    }
}
