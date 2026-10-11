package cn.starry.narcissus.functions.packets.out.protocol.v47;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;

import java.io.IOException;

/**
 * 1.8 Block Change (0x23): the 1.8 packed position {@code X<<38 | Y<<26 | Z}
 * then VarInt block state {@code (id << 4) | meta}.
 */
public final class PacketPlayOutBlockChange47 implements PacketOut {
    private final int x;
    private final int y;
    private final int z;
    private final int blockId;
    private final int blockMeta;

    public PacketPlayOutBlockChange47(int x, int y, int z, int blockId, int blockMeta) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockId = blockId;
        this.blockMeta = blockMeta;
    }

    @Override
    public void write(PacketSerializer out) throws IOException {
        long position = ((long) (this.x & 0x3FFFFFF) << 38)
                | ((long) (this.y & 0xFFF) << 26)
                | (this.z & 0x3FFFFFFL);
        out.writeLong(position);
        out.writeVarInt((this.blockId << 4) | (this.blockMeta & 0xF));
    }
}
