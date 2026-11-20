package cn.starry.narcissus;

import cn.starry.narcissus.functions.commands.CommandManager;
import cn.starry.narcissus.functions.config.Configuration;
import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.world.WorldTimeService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TimeCommandTest {

    @Test
    void playerReceivesOnlyTheRequestedFrozenTime() {
        RecordingNetworkManager network = new RecordingNetworkManager();

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
        RecordingNetworkManager network = new RecordingNetworkManager();

        new CommandManager().handleChat(network, null, "/time sync");

        assertEquals(2000L, network.sentTime.dayTime());
        assertEquals(List.of(
                "&a已恢复当前 UTC+8 世界时间：&e08:00 &7(2000 ticks)&a。",
                "&7下一个 UTC+8 整点将恢复全局同步。"), network.messages);
    }

    @Test
    void similarlyNamedCommandsAreNotIntercepted() {
        RecordingNetworkManager network = new RecordingNetworkManager();

        new CommandManager().handleChat(network, null, "/timezone 08:00");

        assertNull(network.sentTime);
        assertEquals(List.of("&fUnknown command."), network.messages);
    }

    @Test
    void usageForInvalidTime() {
        RecordingNetworkManager network = new RecordingNetworkManager();

        new CommandManager().handleChat(network, null, "/time 24:00");

        assertNull(network.sentTime);
        assertEquals(List.of("&e用法：/time <HH:mm|0-23999|sync>"), network.messages);
    }

    private static final class RecordingNetworkManager extends NetworkManager {
        private final WorldTimeService timeService = new WorldTimeService(
                Clock.fixed(Instant.parse("2026-08-06T00:00:00Z"), ZoneOffset.UTC));
        private final List<String> messages = new ArrayList<>();
        private WorldTimeService.Snapshot sentTime;

        private RecordingNetworkManager() {
            super(null, Configuration.builder().build());
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
