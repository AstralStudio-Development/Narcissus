package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.world.WorldLighting;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/** Complete light masks/arrays shared by Level Chunk With Light and Light Update. */
final class LightData774 {
    private final BitSet sky = new BitSet(), block = new BitSet();
    private final BitSet emptySky = new BitSet(), emptyBlock = new BitSet();
    private final List<byte[]> skyArrays = new ArrayList<>(), blockArrays = new ArrayList<>();
    private final boolean byteArrayMasks;

    LightData774(int chunkX, int chunkZ, int sectionCount, boolean hasSkyLight,
            WorldLighting lighting, BitSet selectedSky, BitSet selectedBlock) {
        this(chunkX, chunkZ, sectionCount, hasSkyLight, lighting, selectedSky, selectedBlock, false);
    }
    LightData774(int chunkX, int chunkZ, int sectionCount, boolean hasSkyLight,
            WorldLighting lighting, BitSet selectedSky, BitSet selectedBlock, boolean byteArrayMasks) {
        this.byteArrayMasks = byteArrayMasks;
        int lightSections = sectionCount + 2; // includes a boundary section below and above
        if (hasSkyLight) collect(chunkX, chunkZ, lightSections, true, lighting, selectedSky,
                sky, emptySky, skyArrays);
        collect(chunkX, chunkZ, lightSections, false, lighting, selectedBlock,
                block, emptyBlock, blockArrays);
    }
    private static void collect(int chunkX, int chunkZ, int count, boolean skyLayer,
            WorldLighting lighting, BitSet selected, BitSet dataMask, BitSet emptyMask,
            List<byte[]> arrays) {
        BitSet wanted = selected == null ? new BitSet(count) : (BitSet) selected.clone();
        if (selected == null) wanted.set(0, count);
        if (wanted.length() > count) throw new IllegalArgumentException("Light mask exceeds dimension sections");
        for (int bit = wanted.nextSetBit(0); bit >= 0; bit = wanted.nextSetBit(bit + 1)) {
            byte[] values;
            if (lighting != null) {
                values = skyLayer ? lighting.skySection(chunkX, chunkZ, bit)
                        : lighting.blockSection(chunkX, chunkZ, bit);
            } else {
                values = new byte[2048];
                if (skyLayer) Arrays.fill(values, (byte) 0xff); // unobstructed empty sky
            }
            if (values == null) {
                values = new byte[2048]; // sparse missing sections are all-zero
                if (skyLayer && !lighting.containsChunk(chunkX, chunkZ)) Arrays.fill(values, (byte) 0xff);
            }
            boolean nonZero = false;
            for (byte value : values) if (value != 0) { nonZero = true; break; }
            if (nonZero) {
                dataMask.set(bit);
                arrays.add(values.clone()); // the queued packet must not change after a world edit
            } else {
                emptyMask.set(bit);
            }
        }
    }
    void write(PacketSerializer out) throws IOException {
        writeMask(out, sky);
        writeMask(out, block);
        writeMask(out, emptySky);
        writeMask(out, emptyBlock);
        writeArrays(out, skyArrays);
        writeArrays(out, blockArrays);
    }
    private void writeMask(PacketSerializer out, BitSet mask) throws IOException {
        if (byteArrayMasks) out.writeByteArray(mask.toByteArray());
        else out.writeBitSet(mask);
    }
    private static void writeArrays(PacketSerializer out, List<byte[]> arrays) throws IOException {
        out.writeVarInt(arrays.size());
        for (byte[] array : arrays) out.writeByteArray(array);
    }
}
