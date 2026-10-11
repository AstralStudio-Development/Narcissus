package cn.starry.narcissus.functions.packets.out.protocol.v47;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import cn.starry.narcissus.functions.world.Chunk;
import cn.starry.narcissus.functions.world.WorldLighting;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 1.8 Chunk Data (0x21). Body: Int chunkX, Int chunkZ, Bool ground-up,
 * UShort primary bitmask, VarInt-prefixed payload, then no tile entities.
 * The payload is grouped: every selected section contributes 8192 block
 * bytes first, then every selected section contributes 2048 block-light
 * nibbles and (when the dimension has sky light) 2048 sky-light nibbles,
 * and the 256-byte biome array terminates the payload. Sections are
 * selected when they hold blocks or carry block light (sky light alone does
 * not select a section).
 */
public final class PacketPlayOutChunkData47 implements PacketOut {
    private final int chunkX;
    private final int chunkZ;
    private final boolean groundUp;
    private final int primaryMask;
    private final byte[] payload;

    public PacketPlayOutChunkData47(Chunk chunk, WorldLighting lighting, boolean skylight) {
        this(chunk.getX(), chunk.getZ(), chunk, lighting, skylight);
    }

    /** Halo chunk: no block storage, only propagated light from the world solution. */
    public PacketPlayOutChunkData47(int chunkX, int chunkZ, WorldLighting lighting,
                                    boolean skylight) {
        this(chunkX, chunkZ, null, lighting, skylight);
    }

    private PacketPlayOutChunkData47(int chunkX, int chunkZ, Chunk chunk,
                                     WorldLighting lighting, boolean skylight) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.groundUp = true;

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        cn.starry.narcissus.functions.packets.PacketSerializer s =
                new cn.starry.narcissus.functions.packets.PacketSerializer(buffer);
        int mask = 0;
        try {
            for (int section = 0; section < 16; section++) {
                boolean hasBlocks = chunk != null && chunk.hasLegacyBlocksInSection(section);
                boolean hasLight = lighting != null && sectionHasLight(lighting, chunkX, chunkZ, section);
                if (!hasBlocks && !hasLight)
                    continue;
                mask |= 1 << section;
                if (chunk != null && hasBlocks)
                    s.writeRawBytes(chunk.getLegacyBlockSection(section));
                else
                    s.writeRawBytes(new byte[8192]);
            }
            for (int section = 0; section < 16; section++) {
                if ((mask & (1 << section)) == 0)
                    continue;
                s.writeRawBytes(sectionNibbles(lighting, chunkX, chunkZ, section, false));
            }
            if (skylight) {
                for (int section = 0; section < 16; section++) {
                    if ((mask & (1 << section)) == 0)
                        continue;
                    s.writeRawBytes(sectionNibbles(lighting, chunkX, chunkZ, section, true));
                }
            }
            if (mask != 0)
                s.writeRawBytes(chunk != null ? chunk.getLegacyBiomeData() : new byte[256]);
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode v47 chunk data", e);
        }
        this.primaryMask = mask;
        this.payload = buffer.toByteArray();
    }

    private static boolean sectionHasLight(WorldLighting lighting, int chunkX, int chunkZ, int section) {
        byte[] data = lighting.blockSection(chunkX, chunkZ, section);
        if (data == null)
            return false;
        for (byte datum : data)
            if (datum != 0)
                return true;
        return false;
    }

    private static byte[] sectionNibbles(WorldLighting lighting, int chunkX, int chunkZ,
                                         int section, boolean sky) {
        byte[] data = sky ? (lighting.hasSkyLight() ? lighting.skySection(chunkX, chunkZ, section) : null)
                : lighting.blockSection(chunkX, chunkZ, section);
        return data != null ? data : new byte[2048];
    }

    @Override
    public void write(PacketSerializer out) throws IOException {
        out.writeInt(this.chunkX);
        out.writeInt(this.chunkZ);
        out.writeBoolean(this.groundUp);
        out.writeShort((short) this.primaryMask);
        out.writeByteArray(this.payload);
    }
}
