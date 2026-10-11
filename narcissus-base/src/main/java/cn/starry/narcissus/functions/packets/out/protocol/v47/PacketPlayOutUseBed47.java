package cn.starry.narcissus.functions.packets.out.protocol.v47;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;

import java.io.IOException;

/**
 * 1.8 Use Bed (0x0A): VarInt entity id, then the 1.8 packed block position
 * {@code X<<38 | Y<<26 | Z} (modern order swaps Y and Z).
 */
public final class PacketPlayOutUseBed47 implements PacketOut {
    private final int entityId;
    private final int x;
    private final int y;
    private final int z;

    public PacketPlayOutUseBed47(int entityId, int x, int y, int z) {
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void write(PacketSerializer out) throws IOException {
        out.writeVarInt(this.entityId);
        long position = ((long) (this.x & 0x3FFFFFF) << 38)
                | ((long) (this.y & 0xFFF) << 26)
                | (this.z & 0x3FFFFFFL);
        out.writeLong(position);
    }
}
