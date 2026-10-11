package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import java.io.IOException;

public record PacketPlayOutSetTime774(long gameTime, long dayTime) implements PacketOut {
    @Override public void write(PacketSerializer out) throws IOException {
        out.writeLong(gameTime);
        out.writeLong(dayTime);
        out.writeBoolean(false); // freeze daylight
    }
}
