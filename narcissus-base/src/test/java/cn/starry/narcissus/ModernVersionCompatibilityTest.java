package cn.starry.narcissus;

import cn.starry.narcissus.functions.config.Configuration;
import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.AbstractProtocol;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.Status;
import cn.starry.narcissus.functions.packets.in.PacketInHandshake;
import cn.starry.narcissus.functions.packets.in.protocol.v774.PacketInKeepAlive774;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketOutKeepAlive774;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketConfigOutSelectKnownPacks774;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketConfigOutUpdateEnabledFeatures774;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketConfigOutUpdateTags774;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutChunkWithLight774;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutGameEvent774;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutSystemChat774;
import cn.starry.narcissus.functions.packets.protocols.ModernProtocol;
import cn.starry.narcissus.util.ProtocolVersionNames;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ModernVersionCompatibilityTest {
    @Test
    void defaultsAllowEveryReleaseFrom12111Through263() {
        assertEquals(List.of(47, 774, 775, 776, 777),
                Configuration.builder().build().getAllowedVersions());
    }

    @Test
    void everyModernReleaseHasItsOwnNativeProtocolAndRegistries() {
        NetworkManager manager = new NetworkManager(null, Configuration.builder().build());
        for (int version : new int[]{774, 775, 776, 777}) {
            AbstractProtocol protocol = manager.getProtocolByVersionId(version);
            assertNotNull(protocol, "missing native protocol " + version);
            assertArrayEquals(new int[]{version}, protocol.getVersions());
            ModernProtocol modern = assertInstanceOf(ModernProtocol.class, protocol);
            assertFalse(modern.getRegistryBodies(false).isEmpty(), "missing registries " + version);
            assertTrue(modern.getModernBlockStateMap().size() > 0);
        }
        assertNull(manager.getProtocolByVersionId(778), "future protocols must not be aliased");
        assertNull(manager.getProtocolByVersionId(769), "1.21.4 must not be aliased to 774");
    }

    @Test
    void loginHandshakeSelectsEveryModernNativeProtocol() throws Exception {
        NetworkManager manager = new NetworkManager(null, Configuration.builder().build());
        try (ServerSocketChannel listener = ServerSocketChannel.open()) {
            listener.bind(new InetSocketAddress("127.0.0.1", 0));
            for (int version : new int[]{774, 775, 776, 777}) {
                try (SocketChannel client = SocketChannel.open(listener.getLocalAddress());
                     SocketChannel accepted = listener.accept()) {
                    PlayerManager player = new PlayerManager(manager, accepted);
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    PacketSerializer out = new PacketSerializer(bytes);
                    out.writeVarInt(version);
                    out.writeString("localhost");
                    out.writeShort((short) 25565);
                    out.writeVarInt(2);
                    PacketInHandshake handshake = new PacketInHandshake();
                    handshake.readPacket(new PacketSerializer(new ByteArrayInputStream(bytes.toByteArray())));
                    handshake.handlePacket(manager, player);
                    assertEquals(Status.LOGIN, player.getStatus());
                    assertEquals(version, player.getProtocolId());
                    assertSame(manager.getProtocolByVersionId(version), player.getProtocol());
                    assertTrue(accepted.isOpen());
                }
            }
        }
    }

    @Test
    void reusedSerializersAreRegisteredAtTheNewReleaseIds() {
        NetworkManager manager = new NetworkManager(null, Configuration.builder().build());
        for (int version : new int[]{775, 776, 777}) {
            AbstractProtocol protocol = manager.getProtocolByVersionId(version);
            assertNotNull(protocol);
            ModernProtocol modern = (ModernProtocol) protocol;
            boolean is263 = version == 777;
            assertEquals(is263 ? 0x0d : 0x0c, protocol.getPacketOutByClass(PacketConfigOutUpdateEnabledFeatures774.class, Status.CONFIGURATION));
            assertEquals(is263 ? 0x0e : 0x0d, protocol.getPacketOutByClass(PacketConfigOutUpdateTags774.class, Status.CONFIGURATION));
            assertEquals(is263 ? 0x0f : 0x0e, protocol.getPacketOutByClass(PacketConfigOutSelectKnownPacks774.class, Status.CONFIGURATION));
            assertEquals(is263 ? 0x27 : 0x26, protocol.getPacketOutByClass(PacketPlayOutGameEvent774.class, Status.PLAY));
            assertEquals(is263 ? 0x2d : 0x2c, protocol.getPacketOutByClass(PacketOutKeepAlive774.class, Status.PLAY));
            assertEquals(is263 ? 0x2e : 0x2d, protocol.getPacketOutByClass(PacketPlayOutChunkWithLight774.class, Status.PLAY));
            assertEquals(is263 ? 0x7c : 0x79, protocol.getPacketOutByClass(PacketPlayOutSystemChat774.class, Status.PLAY));
            assertEquals(PacketInKeepAlive774.class, protocol.getPacketInById(0x1c, Status.PLAY));
            assertPacketId(protocol, 0x02, Status.LOGIN, modern.createLoginSuccess(new UUID(0, 1), "Steve"));
            assertPacketId(protocol, is263 ? 0x32 : 0x31, Status.PLAY,
                    modern.createJoinGame(1, new String[]{modern.getDimensionName()}, 20, 2, 2,
                            false, modern.getDimensionTypeId(), modern.getDimensionName(), 2, modern.getSeaLevel()));
            assertPacketId(protocol, is263 ? 0x65 : 0x63, Status.PLAY, modern.createEntityMetadata(1, 0x7f));
            assertPacketId(protocol, is263 ? 0x4e : 0x4d, Status.PLAY, modern.createRemoveEntities(2));
            assertPacketId(protocol, is263 ? 0x6d : 0x6b, Status.PLAY, modern.createSetPassengers(3, 2));
            assertPacketId(protocol, is263 ? 0x46 : 0x45, Status.PLAY,
                    modern.createPlayerInfoRemove(List.of(new UUID(0, 1))));
            assertPacketId(protocol, is263 ? 0x47 : 0x46, Status.PLAY, modern.createPlayerInfoUpdate(List.of()));
            assertPacketId(protocol, is263 ? 0x6f : 0x6d, Status.PLAY, modern.createHiddenNameTagTeam("npc", "Steve"));
            assertPacketId(protocol, is263 ? 0x73 : 0x71, Status.PLAY, modern.createSetTime(1, 2, 3));
            assertPacketId(protocol, is263 ? 0x7d : 0x7a, Status.PLAY, modern.createTabList("header", "footer"));
            assertPacketId(protocol, is263 ? 0x80 : 0x7d, Status.PLAY,
                    modern.createTeleportEntity(2, 0.5, 64, 0.5, 0, 0));
        }
    }

    @Test
    void playerNpcEntityTypesFollowTheOfficialReleaseRegistries() throws Exception {
        NetworkManager manager = new NetworkManager(null, Configuration.builder().build());
        int[] versions = {774, 775, 776, 777};
        int[] playerTypeIds = {155, 155, 156, 159};
        for (int i = 0; i < versions.length; i++) {
            ModernProtocol modern = (ModernProtocol) manager.getProtocolByVersionId(versions[i]);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            modern.createPlayerNpc(2, new UUID(0, 1), 0, 64, 0, 0, 0).write(new PacketSerializer(bytes));
            PacketSerializer in = new PacketSerializer(new ByteArrayInputStream(bytes.toByteArray()));
            assertEquals(2, in.readVarInt());
            assertEquals(new UUID(0, 1), in.readUUID());
            assertEquals(playerTypeIds[i], in.readVarInt(), "wrong player entity type for " + versions[i]);
        }
    }
    private static void assertPacketId(AbstractProtocol protocol, int expected, Status status, PacketOut packet) {
        assertEquals(expected, protocol.getPacketOutByClass(packet.getClass(), status),
                "serializer registered at a stale id: " + packet.getClass());
    }
    @Test
    void versionLabelsMatchReleaseProtocols() {
        assertEquals("1.21.11", ProtocolVersionNames.forProtocol(774));
        assertEquals("26.1 / 26.1.1 / 26.1.2", ProtocolVersionNames.forProtocol(775));
        assertEquals("26.2", ProtocolVersionNames.forProtocol(776));
        assertEquals("26.3", ProtocolVersionNames.forProtocol(777));
    }
}
