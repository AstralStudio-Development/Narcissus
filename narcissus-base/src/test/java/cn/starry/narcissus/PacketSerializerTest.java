package cn.starry.narcissus;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the hardened {@link PacketSerializer}: VarInt/VarLong round trips,
 * unsigned reads, bounded strings, EOF handling and the incremental frame helper.
 */
class PacketSerializerTest {

    private static byte[] write(Writer writer) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(new PacketSerializer(out));
        return out.toByteArray();
    }

    private interface Writer {
        void write(PacketSerializer serializer) throws IOException;
    }

    @Test
    void varInt_roundTrip() throws IOException {
        int[] samples = {0, 1, 2, 127, 128, 255, 25565, -1, Integer.MAX_VALUE, Integer.MIN_VALUE};
        for (int value : samples) {
            byte[] encoded = write(s -> s.writeVarInt(value));
            PacketSerializer in = new PacketSerializer(new ByteArrayInputStream(encoded));
            assertEquals(value, in.readVarInt(), "VarInt round trip failed for " + value);
        }
    }

    @Test
    void varInt_knownEncoding() throws IOException {
        // 300 encodes as 0xAC 0x02 in the Minecraft VarInt format
        byte[] encoded = write(s -> s.writeVarInt(300));
        assertArrayEquals(new byte[]{(byte) 0xAC, 0x02}, encoded);
    }

    @Test
    void varLong_roundTrip() throws IOException {
        long[] samples = {0L, 1L, 127L, 128L, 2147483647L, -1L, Long.MAX_VALUE, Long.MIN_VALUE};
        for (long value : samples) {
            byte[] encoded = write(s -> s.writeVarLong(value));
            PacketSerializer in = new PacketSerializer(new ByteArrayInputStream(encoded));
            assertEquals(value, in.readVarLong(), "VarLong round trip failed for " + value);
        }
    }

    @Test
    void varInt_tooBigThrows() {
        byte[] fiveContinuationBytes = {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80};
        PacketSerializer in = new PacketSerializer(new ByteArrayInputStream(fiveContinuationBytes));
        assertThrows(IOException.class, in::readVarInt);
    }

    @Test
    void unsignedByte_and_short() throws IOException {
        byte[] data = {(byte) 0xFF, (byte) 0x80, 0x01};
        PacketSerializer in = new PacketSerializer(new ByteArrayInputStream(data));
        assertEquals(255, in.readUnsignedByte());
        assertEquals((short) 0x8001, in.readShort());
    }

    @Test
    void string_roundTripUtf8() throws IOException {
        String text = "Narcissus 你好 §c";
        byte[] encoded = write(s -> s.writeString(text));
        PacketSerializer in = new PacketSerializer(new ByteArrayInputStream(encoded));
        assertEquals(text, in.readString());
    }

    @Test
    void readBytes_eofThrows() {
        byte[] data = {0x01, 0x02};
        PacketSerializer in = new PacketSerializer(new ByteArrayInputStream(data));
        assertThrows(EOFException.class, () -> in.readBytes(4));
    }

    @Test
    void tryReadVarInt_partialReturnsNull() throws IOException {
        // Only a continuation byte present: value not complete yet
        byte[] partial = {(byte) 0x80};
        assertNull(PacketSerializer.tryReadVarInt(partial, 0, partial.length));
    }

    @Test
    void tryReadVarInt_completeReturnsValueAndLength() throws IOException {
        byte[] complete = {(byte) 0xAC, 0x02, 0x7F};
        int[] result = PacketSerializer.tryReadVarInt(complete, 0, complete.length);
        assertNotNull(result);
        assertEquals(300, result[0]);
        assertEquals(2, result[1]);
    }

    @Test
    void tryReadVarInt_respectsLimit() throws IOException {
        byte[] data = {(byte) 0xAC, 0x02};
        // limit cuts off before the second byte -> incomplete
        assertNull(PacketSerializer.tryReadVarInt(data, 0, 1));
    }
}
