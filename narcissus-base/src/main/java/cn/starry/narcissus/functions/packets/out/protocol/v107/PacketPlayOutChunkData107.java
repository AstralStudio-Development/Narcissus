package cn.starry.narcissus.functions.packets.out.protocol.v107;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import cn.starry.narcissus.functions.world.Chunk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** 1.9 chunks use spanning 13-bit global paletted containers, not 1.8 raw shorts. */
public class PacketPlayOutChunkData107 implements PacketOut {
    private final int chunkX, chunkZ, primaryMask;
    private final byte[] payload;

    /** The default Limbo dimension is the End, without sky-light arrays. */
    public PacketPlayOutChunkData107(Chunk chunk) { this(chunk, false); }
    public PacketPlayOutChunkData107(Chunk chunk, boolean hasSkyLight) {
        chunkX = chunk.getX();
        chunkZ = chunk.getZ();
        int mask = 0;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            PacketSerializer out = new PacketSerializer(bytes);
            byte[] storage = chunk.getMap();
            for (int section = 0; section < 16; section++) {
                boolean nonEmpty = chunk.hasLegacyBlocksInSection(section);
                int blockLightStart = 131072 + section * 2048;
                for (int i = 0; !nonEmpty && i < 2048; i++) nonEmpty = storage[blockLightStart + i] != 0;
                if (!nonEmpty) continue;
                mask |= 1 << section;
                long[] packed = new long[832];
                for (int index = 0; index < 4096; index++) {
                    int x = index & 15, z = (index >>> 4) & 15, y = section * 16 + (index >>> 8);
                    int state = (chunk.getLegacyId(x, y, z) << 4) | chunk.getLegacyMeta(x, y, z);
                    if (state < 0 || state > 8191) throw new IllegalArgumentException("Block state exceeds the 1.9 global palette");
                    int offset = index * 13, word = offset >>> 6, shift = offset & 63;
                    packed[word] |= (long) state << shift;
                    if (shift > 51) packed[word + 1] |= (long) state >>> (64 - shift);
                }
                out.writeByte((byte) 13);
                out.writeVarInt(0); // no local palette in the global mode
                out.writeLongArray(packed);
                out.writeRawBytes(java.util.Arrays.copyOfRange(storage, blockLightStart, blockLightStart + 2048));
                if (hasSkyLight) {
                    int skyStart = 163840 + section * 2048;
                    out.writeRawBytes(java.util.Arrays.copyOfRange(storage, skyStart, skyStart + 2048));
                }
            }
            out.writeRawBytes(chunk.getLegacyBiomeData());
            payload = bytes.toByteArray();
        } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
        primaryMask = mask;
    }
    @Override public void write(PacketSerializer out) throws IOException {
        out.writeInt(chunkX);
        out.writeInt(chunkZ);
        out.writeBoolean(true);
        out.writeVarInt(primaryMask);
        out.writeByteArray(payload);
        // 1.9/1.9.2 has no trailing block-entity list; 1.9.3+ adds it.
    }
}
