package cn.starry.narcissus;

import cn.starry.narcissus.functions.config.Configuration;
import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.Status;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import cn.starry.narcissus.functions.packets.out.protocol.v47.PacketPlayOutDestroyEntities47;
import cn.starry.narcissus.functions.packets.out.protocol.v47.PacketPlayOutUseBed47;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutAddEntity774;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutEntityMetadata774;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutRemoveEntities774;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutSetPassengers774;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutTeleportEntity774;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutPlayerInfoUpdate774;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutPlayerInfoRemove774;
import cn.starry.narcissus.functions.world.NpcSchedule;
import cn.starry.narcissus.functions.world.WorldTimeService;
import org.junit.jupiter.api.Test;

import java.nio.channels.SocketChannel;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcScheduleNetworkTest
{
    private static final WorldTimeService TIME_SERVICE = new WorldTimeService(
            Clock.fixed(Instant.parse("2026-08-06T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void modernScheduleTransitionsSendOnlyNpcStateDifferences() throws Exception
    {
        RecordingNetworkManager network = new RecordingNetworkManager();
        try (SocketChannel socket = SocketChannel.open())
        {
            PlayerManager player = createPlayPlayer(network, socket, 774);
            player.setNpcState(NpcSchedule.State.STANDING);

            network.sendWorldTime(player, snapshotAt(LocalTime.of(14, 0)));
            assertEquals(NpcSchedule.State.SITTING, player.getNpcState());
            assertTrue(network.packets.stream().anyMatch(PacketPlayOutTeleportEntity774.class::isInstance));
            assertEquals(2, network.packets.stream()
                    .filter(PacketPlayOutEntityMetadata774.class::isInstance).count());
            assertTrue(network.packets.stream().anyMatch(PacketPlayOutAddEntity774.class::isInstance));
            assertTrue(network.packets.stream().anyMatch(PacketPlayOutSetPassengers774.class::isInstance));

            network.packets.clear();
            network.sendWorldTime(player, snapshotAt(LocalTime.of(15, 0)));
            assertEquals(1, network.packets.size(),
                    "same scheduled state should emit only the world-time packet");

            network.packets.clear();
            network.sendWorldTime(player, snapshotAt(LocalTime.of(8, 0)));
            assertEquals(NpcSchedule.State.SLEEPING, player.getNpcState());
            assertTrue(network.packets.stream().anyMatch(PacketPlayOutRemoveEntities774.class::isInstance));
            assertTrue(network.packets.stream().anyMatch(PacketPlayOutTeleportEntity774.class::isInstance));
            assertTrue(network.packets.stream().anyMatch(PacketPlayOutEntityMetadata774.class::isInstance));
        }
    }

    @Test
    void legacyScheduleTransitionRespawnsNpcAndUsesBedPacket() throws Exception
    {
        RecordingNetworkManager network = new RecordingNetworkManager();
        try (SocketChannel socket = SocketChannel.open())
        {
            PlayerManager player = createPlayPlayer(network, socket, 47);
            player.setNpcState(NpcSchedule.State.STANDING);

            network.sendWorldTime(player, snapshotAt(LocalTime.of(8, 0)));

            assertEquals(NpcSchedule.State.SLEEPING, player.getNpcState());
            assertTrue(network.packets.stream().anyMatch(PacketPlayOutDestroyEntities47.class::isInstance));
            assertInstanceOf(PacketPlayOutUseBed47.class, network.packets.stream()
                    .filter(PacketPlayOutUseBed47.class::isInstance)
                    .findFirst().orElseThrow());
        }
    }

    @Test
    void modernNpc2AppearsOnlyWhileNpc1Sleeps() throws Exception
    {
        RecordingNetworkManager network = new RecordingNetworkManager();
        try (SocketChannel socket = SocketChannel.open())
        {
            PlayerManager player = createPlayPlayer(network, socket, 774);
            player.setNpcState(NpcSchedule.State.STANDING);
            player.setNpc2Visible(false);

            // 站立 -> 睡眠：NPC2 出现（玩家 spawn + 座椅 spawn + PlayerInfo add）
            network.sendWorldTime(player, snapshotAt(LocalTime.of(8, 0)));
            assertEquals(NpcSchedule.State.SLEEPING, player.getNpcState());
            assertTrue(player.isNpc2Visible());
            assertEquals(2, network.packets.stream()
                    .filter(PacketPlayOutAddEntity774.class::isInstance).count(),
                    "NPC2 player spawn and seat spawn must both use add_entity");
            assertTrue(network.packets.stream().anyMatch(
                    PacketPlayOutPlayerInfoUpdate774.class::isInstance));
            assertTrue(network.packets.stream().anyMatch(
                    PacketPlayOutSetPassengers774.class::isInstance));

            // 睡眠状态保持不变：不重复生成 NPC2
            network.packets.clear();
            network.sendWorldTime(player, snapshotAt(LocalTime.of(7, 0)));
            assertTrue(player.isNpc2Visible());
            assertEquals(1, network.packets.size(),
                    "unchanged state should emit only the world-time packet");

            // 睡眠 -> 站立：NPC2 与其座椅移除，profile 一并清理
            network.packets.clear();
            network.sendWorldTime(player, snapshotAt(LocalTime.of(9, 0)));
            assertEquals(NpcSchedule.State.STANDING, player.getNpcState());
            assertFalse2(player.isNpc2Visible());
            assertTrue(network.packets.stream().anyMatch(
                    PacketPlayOutRemoveEntities774.class::isInstance));
            assertTrue(network.packets.stream().anyMatch(
                    PacketPlayOutPlayerInfoRemove774.class::isInstance));
        }
    }

    @Test
    void legacyNpc2IsRespawnedAndDestroyedWithNpc1SleepState() throws Exception
    {
        RecordingNetworkManager network = new RecordingNetworkManager();
        try (SocketChannel socket = SocketChannel.open())
        {
            PlayerManager player = createPlayPlayer(network, socket, 47);
            player.setNpcState(NpcSchedule.State.STANDING);
            player.setNpc2Visible(false);

            network.sendWorldTime(player, snapshotAt(LocalTime.of(8, 0)));
            assertTrue(player.isNpc2Visible());

            network.packets.clear();
            network.sendWorldTime(player, snapshotAt(LocalTime.of(9, 0)));
            assertFalse2(player.isNpc2Visible());
            // 1.8: destroy NPC1 rebuild + NPC2 player/seat destruction
            long destroys = network.packets.stream()
                    .filter(PacketPlayOutDestroyEntities47.class::isInstance).count();
            assertEquals(2, destroys);
        }
    }

    private static void assertFalse2(boolean value)
    {
        org.junit.jupiter.api.Assertions.assertFalse(value);
    }

    private static PlayerManager createPlayPlayer(RecordingNetworkManager network,
                                                   SocketChannel socket, int protocolVersion)
            throws Exception
    {
        PlayerManager player = new PlayerManager(network, socket);
        player.setProtocol(network.getProtocolByVersionId(protocolVersion));
        player.setStatus(Status.PLAY);
        return player;
    }

    private static WorldTimeService.Snapshot snapshotAt(LocalTime time)
    {
        return TIME_SERVICE.atDayTime(WorldTimeService.dayTimeFor(time));
    }

    private static final class RecordingNetworkManager extends NetworkManager
    {
        private final List<PacketOut> packets = new ArrayList<>();

        private RecordingNetworkManager()
        {
            super(null, Configuration.builder().build());
        }

        @Override
        public void sendPacket(PlayerManager playerManager, PacketOut packetOut)
        {
            this.packets.add(packetOut);
        }
    }
}
