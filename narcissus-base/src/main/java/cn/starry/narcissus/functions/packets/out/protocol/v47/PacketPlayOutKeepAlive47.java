package cn.starry.narcissus.functions.packets.out.protocol.v47;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;

import java.io.IOException;

/** 1.8 Keep Alive (0x00): VarInt id. */
public final class PacketPlayOutKeepAlive47 implements PacketOut {
    private final int id;

    public PacketPlayOutKeepAlive47(int id) {
        this.id = id;
    }

    @Override
    public void write(PacketSerializer out) throws IOException {
        out.writeVarInt(this.id);
    }
}
