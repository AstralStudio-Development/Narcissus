package cn.starry.narcissus.functions.modern;

import cn.starry.narcissus.functions.world.Chunk;
import cn.starry.narcissus.functions.world.World;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the {@code chunkData} byte blob for a modern (1.21.x) all-air chunk.
 *
 * The blob is a sequence of chunk sections. Each section is:
 * <pre>
 *   short blockCount
 *   paletted-container blockStates (single-value palette)
 *   paletted-container biomes      (single-value palette)
 * </pre>
 * A single-value palette is: {@code byte bitsPerEntry=0; varint paletteValue; varint dataLen=0}.
 * For an all-air section blockCount=0 and the palette value is the air block-state id (0).
 * The biome single-value palette uses biome registry id 0 (first biome in the fixture).
 *
 * Section count is derived from the dimension height: {@code height / 16}.
 */
public final class ModernChunk {

    private ModernChunk() {}

    /** Air block-state id in the flattened global palette is 0. */
    private static final int AIR_BLOCK_STATE = 0;

    /** Bits per heightmap entry and number of (x,z) columns. */
    private static final int HEIGHTMAP_BITS = 9;
    private static final int HEIGHTMAP_COLS = 256;

    /**
     * Computes the MOTION_BLOCKING heightmap for a schematic chunk: for each of
     * the 256 (x,z) columns, the value is the highest non-air block's distance
     * above the dimension minY plus one, or 0 when the column is entirely air.
     * Values use the same non-spanning
     * PackedIntegerArray layout as the vanilla heightmap: seven 9-bit entries
     * per long, with unused high bits, for ceil(256/7) = 37 longs.
     *
     * A zeroed heightmap tells the client every column's top block is at y=0,
     * which makes the light engine treat the whole structure as occluded and
     * renders large areas black/invisible. Computing real heights fixes that.
     */
    public static long[] buildMotionBlockingHeightmap(Chunk chunk, int sectionCount) {
        return buildMotionBlockingHeightmap(null, chunk, sectionCount);
    }

    public static long[] buildMotionBlockingHeightmap(World world, Chunk chunk, int sectionCount) {
        return buildMotionBlockingHeightmap(world, chunk, sectionCount, null);
    }

    public static long[] buildMotionBlockingHeightmap(World world, Chunk chunk, int sectionCount,
                                                       ModernBlockStateMap stateMap) {
        return buildMotionBlockingHeightmap(world, chunk, sectionCount, 0, stateMap);
    }

    public static long[] buildMotionBlockingHeightmap(World world, Chunk chunk, int sectionCount,
                                                       int minY, ModernBlockStateMap stateMap) {
        byte[] legacy = chunk.getMap();
        int sourceHeight = legacy.length / (16 * 16 * 2);
        int dimensionMaxY = minY + sectionCount * 16 - 1;
        int minSourceY = Math.max(0, minY);
        int maxSourceY = Math.min(sourceHeight - 1, dimensionMaxY);
        int[] heights = new int[HEIGHTMAP_COLS]; // index = z*16 + x
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int top = 0;
                for (int y = maxSourceY; y >= minSourceY; y--) {
                    int li = (x + y * 256 + z * 16) * 2;
                    boolean nonAir;
                    if (world != null && world.hasModernBlockStates()) {
                        int stateKey = chunk.getModernStateKey(x, y, z);
                        nonAir = stateMap == null
                                ? stateKey != AIR_BLOCK_STATE
                                : stateMap.resolve(world.getModernState(stateKey)) != AIR_BLOCK_STATE;
                    } else {
                        int b0 = legacy[li] & 0xFF;
                        int b1 = legacy[li + 1] & 0xFF;
                        int legacyId = ((b1 & 0xF) << 4) | (b0 >> 4);
                        nonAir = legacyId != 0;
                    }
                    if (nonAir) { // Heightmaps store distance above the dimension's minY.
                        top = y - minY + 1;
                        break;
                    }
                }
                heights[z * 16 + x] = top;
            }
        }
        // Vanilla SimpleBitStorage uses floor(64 / bits) entries per long and
        // never lets an entry cross a long boundary. For 9 bits that is 7
        // entries per long and 37 longs for 256 columns.
        int entriesPerLong = 64 / HEIGHTMAP_BITS; // 7
        int longCount = (HEIGHTMAP_COLS + entriesPerLong - 1) / entriesPerLong; // 37
        long[] out = new long[longCount];
        long mask = (1L << HEIGHTMAP_BITS) - 1;
        for (int i = 0; i < HEIGHTMAP_COLS; i++) {
            int longIdx = i / entriesPerLong;
            int bitOffset = (i % entriesPerLong) * HEIGHTMAP_BITS;
            out[longIdx] |= ((long) heights[i] & mask) << bitOffset;
        }
        return out;
    }

    /**
     * @param sectionCount number of 16-block-tall sections (e.g. 16 for the_end height 256)
     * @param biomeId      single biome registry id to fill (0 = first biome)
     */
    public static byte[] emptyChunkData(int sectionCount, int biomeId) {
        return emptyChunkData(sectionCount, biomeId, false);
    }

    public static byte[] emptyChunkData(int sectionCount, int biomeId, boolean sectionBlockCountMarker) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            for (int i = 0; i < sectionCount; i++) {
                out.writeShort(0); // blockCount = 0 (all air)
                if (sectionBlockCountMarker) {
                    out.writeShort(0); // 26.2 section marker
                }
                writeSingleValuePalette(out, AIR_BLOCK_STATE); // block states
                writeSingleValuePalette(out, biomeId);          // biomes
            }
            return baos.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Writes a single-valued paletted container. In protocol 774 (1.21.5+) the
     * data-array length prefix is OMITTED: the client computes the packed long
     * count from bitsPerEntry. Layout: bitsPerEntry=0; varint value. No data
     * array (and no length) follows.
     */
    private static void writeSingleValuePalette(DataOutputStream out, int value) throws IOException {
        out.writeByte(0);        // bits per entry = 0 -> single value palette
        writeVarInt(out, value); // the single palette value
        // 1.21.5+: no data-array-length varint, no data array.
    }

    /**
     * Returns a BitSet (length sectionCount+2, the wire format pads one section
     * above and below the world) marking which sections contain at least one
     * non-air block. Section index in the BitSet is offset by +1 because the
     * light masks include a virtual section below the dimension minY at bit 0.
     */
    public static java.util.BitSet nonEmptySections(Chunk chunk, int sectionCount) {
        return nonEmptySections(null, chunk, sectionCount);
    }

    public static java.util.BitSet nonEmptySections(World world, Chunk chunk, int sectionCount) {
        return nonEmptySections(world, chunk, sectionCount, 0);
    }

    public static java.util.BitSet nonEmptySections(World world, Chunk chunk, int sectionCount,
                                                     int minY) {
        java.util.BitSet set = new java.util.BitSet(sectionCount + 2);
        byte[] legacy = chunk.getMap();
        int minSection = Math.floorDiv(minY, 16);
        int sourceSectionCount = legacy.length / (16 * 16 * 16 * 2);
        for (int packetSection = 0; packetSection < sectionCount; packetSection++) {
            int sourceSection = minSection + packetSection;
            if (sourceSection < 0 || sourceSection >= sourceSectionCount) {
                continue;
            }
            int baseY = sourceSection * 16;
            boolean any = false;
            for (int y = 0; y < 16 && !any; y++) {
                for (int z = 0; z < 16 && !any; z++) {
                    for (int x = 0; x < 16; x++) {
                        boolean nonAir;
                        if (world != null && world.hasModernBlockStates()) {
                            nonAir = chunk.getModernStateKey(x, baseY + y, z) != AIR_BLOCK_STATE;
                        } else {
                            int li = (x + (baseY + y) * 256 + z * 16) * 2;
                            int b0 = legacy[li] & 0xFF;
                            int b1 = legacy[li + 1] & 0xFF;
                            int legacyId = ((b1 & 0xF) << 4) | (b0 >> 4);
                            nonAir = legacyId != 0;
                        }
                        if (nonAir) {
                            any = true;
                            break;
                        }
                    }
                }
            }
            if (any) {
                set.set(packetSection + 1); // +1: bit 0 is the below-world section
            }
        }
        return set;
    }

    /**
     * Builds {@code chunkData} for a schematic {@link Chunk}, converting each
     * legacy {@code id:meta} to the global block-state id via {@code map}.
     * Sections without any non-air block are emitted as single-value (air)
     * palettes; populated sections use a multi-value palette.
     *
     * The legacy {@code Chunk} stores 16x(16*16*16) blocks across absolute
     * world Y=0..255. Packet sections are dimension-relative, so minY=-64
     * inserts four air sections before source section zero; all other
     * out-of-range sections are emitted as air.
     */
    public static byte[] buildChunkData(Chunk chunk, int sectionCount, int biomeId, LegacyBlockMap map) {
        return buildChunkData(null, chunk, sectionCount, biomeId, map, null, false);
    }

    public static byte[] buildChunkData(Chunk chunk, int sectionCount, int biomeId,
                                        LegacyBlockMap map, boolean sectionBlockCountMarker) {
        return buildChunkData(null, chunk, sectionCount, biomeId, map, null, sectionBlockCountMarker);
    }

    public static byte[] buildChunkData(World world, Chunk chunk, int sectionCount, int biomeId,
                                        LegacyBlockMap map, boolean sectionBlockCountMarker) {
        return buildChunkData(world, chunk, sectionCount, biomeId, map, null, sectionBlockCountMarker);
    }

    public static byte[] buildChunkData(World world, Chunk chunk, int sectionCount, int biomeId,
                                        LegacyBlockMap map, ModernBlockStateMap stateMap,
                                        boolean sectionBlockCountMarker) {
        return buildChunkData(world, chunk, sectionCount, 0, biomeId, map, stateMap,
                sectionBlockCountMarker);
    }

    public static byte[] buildChunkData(World world, Chunk chunk, int sectionCount, int minY,
                                        int biomeId, LegacyBlockMap map, ModernBlockStateMap stateMap,
                                        boolean sectionBlockCountMarker) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            byte[] legacy = chunk.getMap();
            int minSection = Math.floorDiv(minY, 16);
            int sourceSectionCount = legacy.length / (16 * 16 * 16 * 2);
            for (int packetSection = 0; packetSection < sectionCount; packetSection++) {
                // Decode the source section represented by this dimension-relative packet section.
                int[] states = new int[4096];
                int nonAir = 0;
                int sourceSection = minSection + packetSection;
                if (sourceSection >= 0 && sourceSection < sourceSectionCount) {
                    int baseY = sourceSection * 16;
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                int li = (x + (baseY + y) * 256 + z * 16) * 2;
                                int b0 = legacy[li] & 0xFF;
                                int b1 = legacy[li + 1] & 0xFF;
                                int legacyId = ((b1 & 0xF) << 4) | (b0 >> 4);
                                int meta = b0 & 0xF;
                                int gid;
                                if (world != null && world.hasModernBlockStates()) {
                                    if (stateMap == null)
                                        throw new IllegalArgumentException("Modern block-state map is required for a modern schematic");
                                    gid = stateMap.resolve(world.getModernState(chunk.getModernStateKey(x, baseY + y, z)));
                                } else {
                                    gid = world == null
                                            ? map.toGlobalState(legacyId, meta)
                                            : map.toGlobalState(world, chunk, x, baseY + y, z);
                                }
                                int idx = (y << 8) | (z << 4) | x;
                                states[idx] = gid;
                                if (gid != LegacyBlockMap.AIR) {
                                    nonAir++;
                                }
                            }
                        }
                    }
                }
                out.writeShort(nonAir);
                if (sectionBlockCountMarker) {
                    out.writeShort(0); // 26.2 section marker
                }
                if (nonAir == 0) {
                    writeSingleValuePalette(out, AIR_BLOCK_STATE);
                } else {
                    writeMultiValuePalette(out, states);
                }
                writeSingleValuePalette(out, biomeId);
            }
            return baos.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Writes a paletted container with a per-section palette built from the
     * given 4096 block-state ids (index = y<<8 | z<<4 | x).
     */
    private static void writeMultiValuePalette(DataOutputStream out, int[] states) throws IOException {
        // Build palette in first-seen order.
        List<Integer> palette = new ArrayList<>();
        int[] paletteIndex = new int[4096];
        // Map global id -> palette slot via a small linear scan (palette is tiny).
        for (int i = 0; i < 4096; i++) {
            int gid = states[i];
            int slot = palette.indexOf(gid);
            if (slot < 0) {
                palette.add(gid);
                slot = palette.size() - 1;
            }
            paletteIndex[i] = slot;
        }
        int paletteSize = palette.size();
        int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(Math.max(1, paletteSize - 1)));

        if (bits <= 8) {
            out.writeByte(bits);
            writeVarInt(out, paletteSize);
            for (int gid : palette) {
                writeVarInt(out, gid);
            }

            int entriesPerLong = 64 / bits;
            int longCount = (4096 + entriesPerLong - 1) / entriesPerLong;
            // 1.21.5+: the data-array length prefix is OMITTED; the client derives
            // the long count from bitsPerEntry. Write only the packed longs.
            long mask = (1L << bits) - 1;
            for (int l = 0; l < longCount; l++) {
                long value = 0L;
                int base = l * entriesPerLong;
                for (int e = 0; e < entriesPerLong; e++) {
                    int idx = base + e;
                    if (idx >= 4096) {
                        break;
                    }
                    // The network section order is decoded by the client with
                    // the first entry at the low end of each packed long.
                    value |= ((long) (paletteIndex[idx] & mask)) << (e * bits);
                }
                out.writeLong(value);
            }
        } else {
            // Match the 26.2 reference implementation's global palette mode:
            // 16 bits per entry, four global state IDs per long, no local palette.
            out.writeByte(16);
            for (int base = 0; base < 4096; base += 4) {
                long value = 0L;
                for (int entry = 0; entry < 4; entry++) {
                    value = (value << 16) | (states[base + entry] & 0xFFFFL);
                }
                out.writeLong(value);
            }
        }
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while (true) {
            int part = value & 0x7F;
            value >>>= 7;
            if (value != 0) {
                out.writeByte(part | 0x80);
            } else {
                out.writeByte(part);
                break;
            }
        }
    }
}
