package cn.starry.narcissus.functions.packets.out.protocol.v340;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;

import java.io.IOException;

/** 1.12.2 Keep Alive carries a signed 64-bit id. */
public final class PacketPlayOutKeepAlive340 implements PacketOut {
    private final long id;
    public PacketPlayOutKeepAlive340(long id) { this.id = id; }
    @Override public void write(PacketSerializer out) throws IOException { out.writeLong(id); }
}
