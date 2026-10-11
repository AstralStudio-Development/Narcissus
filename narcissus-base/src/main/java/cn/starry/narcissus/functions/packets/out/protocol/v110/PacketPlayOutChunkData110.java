package cn.starry.narcissus.functions.packets.out.protocol.v110;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import cn.starry.narcissus.functions.world.Chunk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** 1.9.3/1.10+ adds the block-entity list after the paletted chunk payload. */
public final class PacketPlayOutChunkData110 extends cn.starry.narcissus.functions.packets.out.protocol.v107.PacketPlayOutChunkData107 {
    public PacketPlayOutChunkData110(Chunk chunk) { super(chunk); }
    public PacketPlayOutChunkData110(Chunk chunk, boolean hasSkyLight) { super(chunk, hasSkyLight); }
    @Override public void write(PacketSerializer out) throws IOException {
        super.write(out);
        out.writeVarInt(0); // sign NBT is sent by separate Update Block Entity packets
    }
}
