package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import java.io.IOException;

public record PacketPlayOutGameEvent774(int event, float value) implements PacketOut {
    public static final int START_WAITING_FOR_CHUNKS = 13;
    @Override public void write(PacketSerializer out) throws IOException {
        out.writeByte((byte) event);
        out.writeFloat(value);
    }
}
