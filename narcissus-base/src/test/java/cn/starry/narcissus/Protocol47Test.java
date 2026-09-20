package cn.starry.narcissus;

import cn.starry.narcissus.functions.config.Configuration;
import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.packets.AbstractProtocol;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.Status;
import cn.starry.narcissus.functions.packets.in.PacketInHandshake;
import cn.starry.narcissus.functions.packets.in.PacketInPluginMessage;
import cn.starry.narcissus.functions.packets.in.PacketLoginInStart;
import cn.starry.narcissus.functions.packets.in.PacketStatusInPing;
import cn.starry.narcissus.functions.packets.in.PacketStatusInRequest;
import cn.starry.narcissus.functions.packets.in.protocol.v47.PacketPlayInChat47;
import cn.starry.narcissus.functions.packets.in.protocol.v47.PacketPlayInClientSettings47;
import cn.starry.narcissus.functions.packets.in.protocol.v47.PacketPlayInKeepAlive47;
import cn.starry.narcissus.functions.packets.in.protocol.v47.PacketPlayInTabComplete47;
import cn.starry.narcissus.functions.packets.out.PacketLoginOutSuccess;
import cn.starry.narcissus.functions.packets.out.PacketOutDisconnect;
import cn.starry.narcissus.functions.packets.out.protocol.v47.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the v47 backend protocol packet id registrations and a few golden
 * clientbound encodings. Only Handshake + Protocol47 are registered on the
 * backend; every client version is translated to v47 by ViaVersion on the proxy.
 */
class Protocol47Test {

    private static AbstractProtocol backend() {
        Configuration cfg = Configuration.builder().build();
        NetworkManager manager = new NetworkManager(null, cfg);
        AbstractProtocol backend = manager.getBackendProtocol();
        assertNotNull(backend, "v47 backend protocol must be registered");
        return backend;
    }

    @Test
    void backendReportsVersion47() {
        assertArrayEquals(new int[]{47}, backend().getVersions());
    }

    @Test
    void statusPacketIdsRegistered() {
        AbstractProtocol p = backend();
        assertEquals(PacketStatusInRequest.class, p.getPacketInById(0x00, Status.STATUS));
        assertEquals(PacketStatusInPing.class, p.getPacketInById(0x01, Status.STATUS));
    }

    @Test
    void loginPacketIdsRegistered() {
        AbstractProtocol p = backend();
        assertEquals(PacketLoginInStart.class, p.getPacketInById(0x00, Status.LOGIN));
        assertEquals(0x00, p.getPacketOutByClass(PacketOutDisconnect.class, Status.LOGIN));
        assertEquals(0x02, p.getPacketOutByClass(PacketLoginOutSuccess.class, Status.LOGIN));
    }

    @Test
    void handshakePacketIdRegistered() {
        // Handshake protocol maps 0x00 in the HANDSHAKE state
        Configuration cfg = Configuration.builder().build();
        NetworkManager manager = new NetworkManager(null, cfg);
        AbstractProtocol handshake = manager.getProtocolByVersionId(-1);
        assertNotNull(handshake);
        assertEquals(PacketInHandshake.class, handshake.getPacketInById(0x00, Status.HANDSHAKE));
    }

    @Test
    void playClientboundIdsRegistered() {
        AbstractProtocol p = backend();
        assertEquals(0x00, p.getPacketOutByClass(PacketPlayOutKeepAlive47.class, Status.PLAY));
        assertEquals(0x01, p.getPacketOutByClass(PacketPlayOutJoinGame47.class, Status.PLAY));
        assertEquals(0x08, p.getPacketOutByClass(PacketPlayOutPlayerPositionAndLook47.class, Status.PLAY));
        assertEquals(0x0A, p.getPacketOutByClass(PacketPlayOutUseBed47.class, Status.PLAY));
        assertEquals(0x0C, p.getPacketOutByClass(PacketPlayOutSpawnPlayer47.class, Status.PLAY));
        assertEquals(0x0F, p.getPacketOutByClass(PacketPlayOutSpawnMob47.class, Status.PLAY));
        assertEquals(0x13, p.getPacketOutByClass(PacketPlayOutDestroyEntities47.class, Status.PLAY));
        assertEquals(0x1B, p.getPacketOutByClass(PacketPlayOutAttachEntity47.class, Status.PLAY));
        assertEquals(0x21, p.getPacketOutByClass(PacketPlayOutChunkData47.class, Status.PLAY));
        assertEquals(0x23, p.getPacketOutByClass(PacketPlayOutBlockChange47.class, Status.PLAY));
        assertEquals(0x3E, p.getPacketOutByClass(PacketPlayOutTeams47.class, Status.PLAY));
        assertEquals(0x45, p.getPacketOutByClass(PacketPlayOutTitle47.class, Status.PLAY));
    }

    @Test
    void playServerboundKeepAliveRegistered() {
        AbstractProtocol p = backend();
        assertEquals(PacketPlayInKeepAlive47.class, p.getPacketInById(0x00, Status.PLAY));
    }

    @Test
    void legacySkinLayerPacketsAreRegistered() {
        AbstractProtocol p = backend();
        assertEquals(PacketPlayInClientSettings47.class, p.getPacketInById(0x15, Status.PLAY));
        assertEquals(0x1C, p.getPacketOutByClass(PacketPlayOutEntityMetadata47.class, Status.PLAY));
    }

    @Test
    void legacyPluginMessageIsParsedForForwardedSkinProperties() {
        AbstractProtocol p = backend();
        assertEquals(PacketInPluginMessage.class, p.getPacketInById(0x17, Status.PLAY));
    }

    @Test
    void playServerboundChatStillUsesLegacyPacket() {
        AbstractProtocol p = backend();
        assertEquals(PacketPlayInChat47.class, p.getPacketInById(0x01, Status.PLAY));
    }

    @Test
    void legacyTabCompletePacketsAreRegistered() {
        AbstractProtocol p = backend();
        assertEquals(PacketPlayInTabComplete47.class, p.getPacketInById(0x14, Status.PLAY));
        assertEquals(0x3A, p.getPacketOutByClass(PacketPlayOutTabComplete47.class, Status.PLAY));
    }

    @Test
    void legacyPlayerListPacketsAreRegistered() {
        AbstractProtocol p = backend();
        assertEquals(0x38, p.getPacketOutByClass(PacketPlayOutPlayerInfo47.class, Status.PLAY));
        assertEquals(0x47, p.getPacketOutByClass(PacketPlayOutPlayerListHeaderFooter47.class, Status.PLAY));
    }

    @Test
    void legacyPlayerListAddEncoding() throws IOException {
        java.util.UUID uuid = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");
        java.util.List<cn.starry.narcissus.functions.packets.PlayerListEntry> entries =
                java.util.Collections.singletonList(new cn.starry.narcissus.functions.packets.PlayerListEntry(uuid, "Steve", 1));
        byte[] body = encodeBody(new PacketPlayOutPlayerInfo47(entries));
        PacketSerializer in = new PacketSerializer(new ByteArrayInputStream(body));
        assertEquals(0x38, in.readVarInt());
        assertEquals(0, in.readVarInt());
        assertEquals(1, in.readVarInt());
        assertEquals(uuid, in.readUUID());
        assertEquals("Steve", in.readString());
        assertEquals(0, in.readVarInt());
        assertEquals(1, in.readVarInt());
        assertEquals(0, in.readVarInt());
        assertTrue(in.readBoolean());
        assertEquals("{\"text\":\"§7Steve\"}", in.readString());
    }

    @Test
    void legacyPlayerListEncodesSignedSkinProperty() throws IOException {
        java.util.UUID uuid = java.util.UUID.fromString("00000000-0000-0000-0000-000000000002");
        java.util.List<cn.starry.narcissus.functions.packets.PlayerListEntry.ProfileProperty> properties =
                java.util.Collections.singletonList(new cn.starry.narcissus.functions.packets.PlayerListEntry.ProfileProperty(
                        "textures", "encoded-value", "encoded-signature"));
        java.util.List<cn.starry.narcissus.functions.packets.PlayerListEntry> entries =
                java.util.Collections.singletonList(new cn.starry.narcissus.functions.packets.PlayerListEntry(
                        uuid, "Alex", 1, properties));

        byte[] body = encodeBody(new PacketPlayOutPlayerInfo47(entries));
        PacketSerializer in = new PacketSerializer(new ByteArrayInputStream(body));
        assertEquals(0x38, in.readVarInt());
        assertEquals(0, in.readVarInt());
        assertEquals(1, in.readVarInt());
        assertEquals(uuid, in.readUUID());
        assertEquals("Alex", in.readString());
        assertEquals(1, in.readVarInt());
        assertEquals("textures", in.readString());
        assertEquals("encoded-value", in.readString());
        assertTrue(in.readBoolean());
        assertEquals("encoded-signature", in.readString());
        assertEquals(1, in.readVarInt());
        assertEquals(0, in.readVarInt());
        assertTrue(in.readBoolean());
        assertEquals("{\"text\":\"§7Alex\"}", in.readString());
    }

    @Test
    void legacyHiddenNpcTeamDisablesNametag() throws IOException {
        byte[] body = encodeBody(new PacketPlayOutTeams47("nar_npc_hidden", "NPC"));
        PacketSerializer in = new PacketSerializer(new ByteArrayInputStream(body));
        assertEquals(0x3E, in.readVarInt());
        assertEquals("nar_npc_hidden", in.readString());
        assertEquals(0, in.readUnsignedByte());
        assertEquals("NPC", in.readString());
        assertEquals("", in.readString());
        assertEquals("", in.readString());
        assertEquals(0, in.readUnsignedByte());
        assertEquals("never", in.readString());
        assertEquals(0xFF, in.readUnsignedByte());
        assertEquals(1, in.readVarInt());
        assertEquals("NPC", in.readString());
        assertEquals(0, in.available());
    }

    @Test
    void legacyTabHeaderEncoding() throws IOException {
        byte[] body = encodeBody(new PacketPlayOutPlayerListHeaderFooter47("§b地狱边境\n§7Sleep...", "§bYUME GAMES"));
        PacketSerializer in = new PacketSerializer(new ByteArrayInputStream(body));
        assertEquals(0x47, in.readVarInt());
        assertEquals("{\"text\":\"§b地狱边境\\n§7Sleep...\"}", in.readString());
        assertEquals("{\"text\":\"§bYUME GAMES\"}", in.readString());
    }

    @Test
    void legacyEntityMetadataUsesCombinedIndexAndLegacyTerminator() throws IOException {
        byte[] body = encodeBody(new PacketPlayOutEntityMetadata47(1, 0x35));
        PacketSerializer in = new PacketSerializer(new ByteArrayInputStream(body));
        assertEquals(0x1C, in.readVarInt());
        assertEquals(1, in.readVarInt());
        assertEquals(13, in.readUnsignedByte());
        assertEquals(0x35, in.readUnsignedByte());
        assertEquals(0x7F, in.readUnsignedByte());
    }

    @Test
    void legacySpawnPlayerEncodingUsesFixedPointCoordinates() throws IOException {
        java.util.UUID uuid = java.util.UUID.fromString("00000000-0000-0000-0000-000000000002");
        byte[] body = encodeBody(new PacketPlayOutSpawnPlayer47(
                2, uuid, 0.5D, 88.0D, 5.5D, 180.0F, 0.0F));
        PacketSerializer in = new PacketSerializer(new ByteArrayInputStream(body));
        assertEquals(0x0C, in.readVarInt());
        assertEquals(2, in.readVarInt());
        assertEquals(uuid, in.readUUID());
        assertEquals(16, in.readInt());
        assertEquals(2816, in.readInt());
        assertEquals(176, in.readInt());
        assertEquals(128, in.readUnsignedByte()); // NORTH yaw
        assertEquals(0, in.readUnsignedByte()); // pitch
        assertEquals(0, in.readShort()); // current item
        assertEquals(0x7F, in.readUnsignedByte()); // metadata terminator
    }

    @Test
    void legacyNpcPosePacketsEncodeExactFieldStreams() throws IOException {
        PacketSerializer bed = new PacketSerializer(new ByteArrayInputStream(encodeBody(
                new PacketPlayOutUseBed47(2, -6, 88, 10))));
        assertEquals(0x0A, bed.readVarInt());
        assertEquals(2, bed.readVarInt());
        long bedPosition = bed.readLong();
        assertEquals(-6, (int) (bedPosition >> 38));
        assertEquals(88, (int) ((bedPosition >> 26) & 0xFFF));
        assertEquals(10, (int) (bedPosition << 38 >> 38));
        assertEquals(0, bed.available());

        PacketSerializer seat = new PacketSerializer(new ByteArrayInputStream(encodeBody(
                new PacketPlayOutSpawnMob47(3, 6.5D, 88.0D, 12.5D, 180.0F, 0.0F))));
        assertEquals(0x0F, seat.readVarInt());
        assertEquals(3, seat.readVarInt());
        assertEquals(30, seat.readUnsignedByte());
        assertEquals(208, seat.readInt());
        assertEquals(2816, seat.readInt());
        assertEquals(400, seat.readInt());
        assertEquals(128, seat.readUnsignedByte());
        assertEquals(0, seat.readUnsignedByte());
        assertEquals(128, seat.readUnsignedByte());
        assertEquals(0, seat.readShort());
        assertEquals(0, seat.readShort());
        assertEquals(0, seat.readShort());
        assertEquals(0, seat.readUnsignedByte());
        assertEquals(0x20, seat.readUnsignedByte());
        assertEquals(10, seat.readUnsignedByte());
        assertEquals(0x19, seat.readUnsignedByte());
        assertEquals(0x7F, seat.readUnsignedByte());
        assertEquals(0, seat.available());

        PacketSerializer attach = new PacketSerializer(new ByteArrayInputStream(encodeBody(
                new PacketPlayOutAttachEntity47(2, 3))));
        assertEquals(0x1B, attach.readVarInt());
        assertEquals(2, attach.readInt());
        assertEquals(3, attach.readInt());
        assertFalse(attach.readBoolean());
        assertEquals(0, attach.available());

        PacketSerializer destroy = new PacketSerializer(new ByteArrayInputStream(encodeBody(
                new PacketPlayOutDestroyEntities47(2, 3))));
        assertEquals(0x13, destroy.readVarInt());
        assertEquals(2, destroy.readVarInt());
        assertEquals(2, destroy.readVarInt());
        assertEquals(3, destroy.readVarInt());
        assertEquals(0, destroy.available());
    }

    @Test
    void golden_keepAliveEncoding() throws IOException {
        byte[] body = encodeBody(new PacketPlayOutKeepAlive47(300));
        // packetId 0x00 then VarInt 300 (0xAC 0x02)
        assertArrayEquals(new byte[]{0x00, (byte) 0xAC, 0x02}, body);
    }

    @Test
    void golden_blockChangeEncoding() throws IOException {
        // stone (id 1, data 0) at (0,0,0): position long = 0, state VarInt = (1<<4)=16
        byte[] body = encodeBody(new PacketPlayOutBlockChange47(0, 0, 0, 1, 0));
        byte[] expected = new byte[]{
                0x23,                                   // packet id (with the length header stripped)
                0, 0, 0, 0, 0, 0, 0, 0,                 // position long = 0
                0x10                                    // block state VarInt = 16
        };
        assertArrayEquals(expected, body);
    }

    @Test
    void golden_loginSuccessEncoding() throws IOException {
        java.util.UUID uuid = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");
        byte[] body = encodeBody(new PacketLoginOutSuccess(uuid, "Steve"), Status.LOGIN, backend());
        // 0x02 packet id, then string uuid, then string name
        PacketSerializer in = new PacketSerializer(new ByteArrayInputStream(body));
        assertEquals(0x02, in.readVarInt());
        assertEquals(uuid.toString(), in.readString());
        assertEquals("Steve", in.readString());
    }

    /** Encodes packet id + body exactly as NetworkManager would (without the outer length prefix). */
    private static byte[] encodeBody(cn.starry.narcissus.functions.packets.out.PacketOut packet) throws IOException {
        return encodeBody(packet, Status.PLAY, backend());
    }

    private static byte[] encodeBody(cn.starry.narcissus.functions.packets.out.PacketOut packet,
                                     Status status, AbstractProtocol protocol) throws IOException {
        int id = protocol.getPacketOutByClass(packet.getClass(), status);
        assertTrue(id != -1, "packet not registered: " + packet.getClass());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PacketSerializer s = new PacketSerializer(out);
        s.writeVarInt(id);
        packet.write(s);
        return out.toByteArray();
    }
}
