package cn.starry.narcissus.functions.packets.in.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModernMovementPacketTest
{
    @Test
    void readsPositionAndMovementFlags() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PacketSerializer output = new PacketSerializer(bytes);
        output.writeLong(Double.doubleToLongBits(32.5D));
        output.writeLong(Double.doubleToLongBits(90.0D));
        output.writeLong(Double.doubleToLongBits(-17.25D));
        output.writeByte((byte) 0x03);

        PacketPlayInPosition774 packet = new PacketPlayInPosition774();
        PacketSerializer input = new PacketSerializer(new ByteArrayInputStream(bytes.toByteArray()));
        packet.readPacket(input);

        assertEquals(32.5D, packet.getX());
        assertEquals(-17.25D, packet.getZ());
    }

    @Test
    void readsPositionRotationAndMovementFlags() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PacketSerializer output = new PacketSerializer(bytes);
        output.writeLong(Double.doubleToLongBits(-16.0D));
        output.writeLong(Double.doubleToLongBits(90.0D));
        output.writeLong(Double.doubleToLongBits(48.75D));
        output.writeInt(Float.floatToIntBits(90.0F));
        output.writeInt(Float.floatToIntBits(12.5F));
        output.writeByte((byte) 0x01);

        PacketPlayInPositionAndLook774 packet = new PacketPlayInPositionAndLook774();
        PacketSerializer input = new PacketSerializer(new ByteArrayInputStream(bytes.toByteArray()));
        packet.readPacket(input);

        assertEquals(-16.0D, packet.getX());
        assertEquals(48.75D, packet.getZ());
    }
}
