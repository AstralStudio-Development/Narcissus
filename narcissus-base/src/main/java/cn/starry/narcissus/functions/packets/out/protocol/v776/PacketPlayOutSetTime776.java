package cn.starry.narcissus.functions.packets.out.protocol.v776;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import java.io.IOException;

/** 26.2+ world-clock map replaces the legacy daylight fields. */
public record PacketPlayOutSetTime776(long gameTime, long worldClockTime) implements PacketOut {
    @Override public void write(PacketSerializer out) throws IOException {
        out.writeLong(gameTime);
        out.writeVarInt(2);
        for (int clockId = 0; clockId < 2; clockId++) {
            out.writeVarInt(clockId); // overworld, the_end (registry ids)
            out.writeVarLong(worldClockTime);
            out.writeFloat(0.0F);
            out.writeFloat(0.0F); // zero rate freezes both clocks
        }
    }
}
