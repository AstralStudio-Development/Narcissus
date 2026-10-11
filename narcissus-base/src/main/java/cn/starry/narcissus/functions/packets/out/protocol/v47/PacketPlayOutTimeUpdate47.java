package cn.starry.narcissus.functions.packets.out.protocol.v47;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;

import java.io.IOException;

/**
 * 1.8 Time Update (0x03). The limbo sends a negative day time so the vanilla
 * client clock never advances; day time zero maps to the frozen sunset value
 * -24000.
 */
public final class PacketPlayOutTimeUpdate47 implements PacketOut {
    private final long worldTime;
    private final long dayTime;

    public PacketPlayOutTimeUpdate47(long worldTime, long dayTime) {
        this.worldTime = worldTime;
        this.dayTime = dayTime;
    }

    @Override
    public void write(PacketSerializer out) throws IOException {
        out.writeLong(this.worldTime);
        out.writeLong(this.dayTime == 0 ? -24000L : -this.dayTime);
    }
}
