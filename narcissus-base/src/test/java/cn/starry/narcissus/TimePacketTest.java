package cn.starry.narcissus;

import cn.starry.narcissus.functions.config.Configuration;
import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.packets.AbstractProtocol;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.Status;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import cn.starry.narcissus.functions.packets.out.protocol.v47.PacketPlayOutTimeUpdate47;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutSetTime774;
import cn.starry.narcissus.functions.packets.out.protocol.v776.PacketPlayOutSetTime776;
import cn.starry.narcissus.functions.packets.protocols.ModernProtocol;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimePacketTest {

    @Test
    void timePacketIdsMatchAllThreeSupportedProtocols() {
        NetworkManager manager = manager();
        AbstractProtocol v47 = manager.getProtocolByVersionId(47);
        AbstractProtocol v774 = manager.getProtocolByVersionId(774);
        AbstractProtocol v776 = manager.getProtocolByVersionId(776);

        assertEquals(0x03, v47.getPacketOutByClass(PacketPlayOutTimeUpdate47.class, Status.PLAY));
        assertEquals(0x6f, v774.getPacketOutByClass(PacketPlayOutSetTime774.class, Status.PLAY));
        assertEquals(0x71, v776.getPacketOutByClass(PacketPlayOutSetTime776.class, Status.PLAY));
    }

    @Test
    void legacyTimeUsesNegativeDayTimeToFreezeTheClientClock() throws IOException {
        AbstractProtocol protocol = manager().getProtocolByVersionId(47);

        PacketSerializer midnight = input(encode(
                new PacketPlayOutTimeUpdate47(123456L, 18000L), protocol));
        assertEquals(0x03, midnight.readVarInt());
        assertEquals(123456L, midnight.readLong());
        assertEquals(-18000L, midnight.readLong());
        assertEquals(0, midnight.available());

        PacketSerializer sunrise = input(encode(
                new PacketPlayOutTimeUpdate47(123456L, 0L), protocol));
        assertEquals(0x03, sunrise.readVarInt());
        sunrise.readLong();
        assertEquals(-24000L, sunrise.readLong(), "day-time zero still needs a negative frozen value");
        assertEquals(0, sunrise.available());
    }

    @Test
    void protocol774WritesTickDayTimeFalse() throws IOException {
        AbstractProtocol protocol = manager().getProtocolByVersionId(774);
        PacketOut packet = ((ModernProtocol) protocol).createSetTime(123456L, 999999L, 2000L);

        PacketSerializer in = input(encode(packet, protocol));
        assertEquals(0x6f, in.readVarInt());
        assertEquals(123456L, in.readLong());
        assertEquals(2000L, in.readLong());
        assertFalse(in.readBoolean());
        assertEquals(0, in.available());
    }

    @Test
    void protocol776WritesBothBuiltInClocksAtZeroRate() throws IOException {
        AbstractProtocol protocol = manager().getProtocolByVersionId(776);
        PacketOut packet = ((ModernProtocol) protocol).createSetTime(123456L, 987654L, 2000L);

        PacketSerializer in = input(encode(packet, protocol));
        assertEquals(0x71, in.readVarInt());
        assertEquals(123456L, in.readLong());
        assertEquals(2, in.readVarInt());

        assertFrozenClock(in, 0, 987654L); // minecraft:overworld
        assertFrozenClock(in, 1, 987654L); // minecraft:the_end
        assertEquals(0, in.available());
    }

    private static void assertFrozenClock(PacketSerializer in, int expectedClockId,
                                          long expectedTicks) throws IOException {
        assertEquals(expectedClockId, in.readVarInt());
        assertEquals(expectedTicks, in.readVarLong());
        assertEquals(Float.floatToIntBits(0.0F), in.readInt());
        assertEquals(Float.floatToIntBits(0.0F), in.readInt());
    }

    private static NetworkManager manager() {
        return new NetworkManager(null, Configuration.builder().build());
    }

    private static PacketSerializer input(byte[] bytes) {
        return new PacketSerializer(new ByteArrayInputStream(bytes));
    }

    private static byte[] encode(PacketOut packet, AbstractProtocol protocol) throws IOException {
        int id = protocol.getPacketOutByClass(packet.getClass(), Status.PLAY);
        assertTrue(id >= 0, "packet not registered: " + packet.getClass());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PacketSerializer serializer = new PacketSerializer(output);
        serializer.writeVarInt(id);
        packet.write(serializer);
        return output.toByteArray();
    }
}
