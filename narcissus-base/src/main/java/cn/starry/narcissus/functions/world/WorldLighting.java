package cn.starry.narcissus.functions.world;

import cn.starry.narcissus.functions.modern.LegacyBlockMap;
import cn.starry.narcissus.functions.modern.ModernBlockStateMap;
import cn.starry.narcissus.functions.world.lighting.BlockLightingProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

/**
 * Complete block/sky-light solution for one private world and one protocol profile.
 * The propagation domain includes one chunk-aligned halo around the schematic, so
 * all light with a maximum range of 15 can cross the stored-world boundary.
 */
public final class WorldLighting
{
    private static final int[] DX = {0, 0, 0, 0, -1, 1};
    private static final int[] DY = {-1, 1, 0, 0, 0, 0};
    private static final int[] DZ = {0, 0, -1, 1, 0, 0};

    private final World world;
    private final BlockLightingProperties properties;
    private final LegacyBlockMap legacyBlockMap;
    private final ModernBlockStateMap modernBlockStateMap;
    private final int minY;
    private final int sectionCount;
    private final boolean hasSkyLight;
    private final boolean modern;
    private final int lightSectionCount;
    private final int lightMinY;
    private final int domainOriginChunkX;
    private final int domainOriginChunkZ;
    private final int worldWidthChunks;
    private final int worldLengthChunks;
    private final int widthChunks;
    private final int lengthChunks;
    private final int widthBlocks;
    private final int lengthBlocks;
    private final int planeSize;
    private final int lightHeight;

    private byte[][] blockSections;
    private byte[][] skySections;

    private WorldLighting(World world, BlockLightingProperties properties,
                          LegacyBlockMap legacyBlockMap, ModernBlockStateMap modernBlockStateMap,
                          int minY, int sectionCount, boolean hasSkyLight, boolean modern)
    {
        this.world = world;
        this.properties = properties;
        this.legacyBlockMap = legacyBlockMap;
        this.modernBlockStateMap = modernBlockStateMap;
        this.minY = minY;
        this.sectionCount = sectionCount;
        this.hasSkyLight = hasSkyLight;
        this.modern = modern;
        this.lightSectionCount = modern ? sectionCount + 2 : sectionCount;
        this.lightMinY = modern ? minY - 16 : minY;
        this.worldWidthChunks = world.getChunks().length;
        this.worldLengthChunks = this.worldWidthChunks == 0 ? 0 : world.getChunks()[0].length;
        // A full chunk on every side is the chunk-aligned form of the required
        // 15-block schematic halo. Cells outside the stored world resolve as air.
        this.domainOriginChunkX = world.getChunkOriginX() - 1;
        this.domainOriginChunkZ = world.getChunkOriginZ() - 1;
        this.widthChunks = this.worldWidthChunks + 2;
        this.lengthChunks = this.worldLengthChunks + 2;
        this.widthBlocks = this.widthChunks * 16;
        this.lengthBlocks = this.lengthChunks * 16;
        this.planeSize = this.widthBlocks * this.lengthBlocks;
        this.lightHeight = this.lightSectionCount * 16;
    }

    public static WorldLighting legacy(World world, BlockLightingProperties properties,
                                       boolean hasSkyLight)
    {
        return new WorldLighting(world, properties, null, null, 0, 16, hasSkyLight, false);
    }

    public static WorldLighting modern(World world, BlockLightingProperties properties,
                                       LegacyBlockMap legacyBlockMap,
                                       ModernBlockStateMap modernBlockStateMap,
                                       int minY, int sectionCount, boolean hasSkyLight)
    {
        return new WorldLighting(world, properties, legacyBlockMap, modernBlockStateMap,
                minY, sectionCount, hasSkyLight, true);
    }

    /** Recomputes the complete solution and returns every changed modern light section. */
    public List<ChunkLightChange> recompute()
    {
        if (this.widthBlocks == 0 || this.lengthBlocks == 0 || this.lightHeight == 0)
        {
            this.blockSections = new byte[0][];
            this.skySections = new byte[0][];
            return Collections.emptyList();
        }

        int volume = Math.multiplyExact(this.planeSize, this.lightHeight);
        int[] states = resolveStates(volume);
        byte[] blockLight = new byte[volume];
        byte[] skyLight = new byte[volume];
        propagateBlockLight(states, blockLight);
        if (this.hasSkyLight)
            propagateSkyLight(states, skyLight);

        byte[][] newBlockSections = encodeSections(blockLight);
        byte[][] newSkySections = encodeSections(skyLight);
        List<ChunkLightChange> changes = diff(this.blockSections, this.skySections,
                newBlockSections, newSkySections);
        this.blockSections = newBlockSections;
        this.skySections = newSkySections;
        return changes;
    }

    public byte[] blockSection(int chunkX, int chunkZ, int lightSectionIndex)
    {
        return section(this.blockSections, chunkX, chunkZ, lightSectionIndex);
    }

    public byte[] skySection(int chunkX, int chunkZ, int lightSectionIndex)
    {
        return section(this.skySections, chunkX, chunkZ, lightSectionIndex);
    }

    public int blockLightAt(int x, int y, int z)
    {
        return lightAt(this.blockSections, x, y, z, 0);
    }

    public int skyLightAt(int x, int y, int z)
    {
        return lightAt(this.skySections, x, y, z, this.hasSkyLight ? 15 : 0);
    }

    public int minChunkX()
    {
        return this.domainOriginChunkX;
    }

    public int minChunkZ()
    {
        return this.domainOriginChunkZ;
    }

    public int maxChunkX()
    {
        return this.domainOriginChunkX + this.widthChunks - 1;
    }

    public int maxChunkZ()
    {
        return this.domainOriginChunkZ + this.lengthChunks - 1;
    }

    /** Whether this coordinate is represented by the authoritative halo snapshot. */
    public boolean containsChunk(int chunkX, int chunkZ)
    {
        return chunkX >= minChunkX() && chunkX <= maxChunkX()
                && chunkZ >= minChunkZ() && chunkZ <= maxChunkZ();
    }

    /** Whether an empty legacy halo chunk needs sending to carry block light. */
    public boolean hasBlockLight(int chunkX, int chunkZ)
    {
        if (!containsChunk(chunkX, chunkZ))
            return false;
        for (int section = 0; section < this.lightSectionCount; section++)
        {
            byte[] data = blockSection(chunkX, chunkZ, section);
            if (!isAll(data, (byte) 0))
                return true;
        }
        return false;
    }

    public int lightSectionCount()
    {
        return this.lightSectionCount;
    }

    public int minY()
    {
        return this.minY;
    }

    public int sectionCount()
    {
        return this.sectionCount;
    }

    public boolean hasSkyLight()
    {
        return this.hasSkyLight;
    }

    public BlockLightingProperties properties()
    {
        return this.properties;
    }

    public boolean isModern()
    {
        return this.modern;
    }

    WorldLighting copyFor(World copy)
    {
        WorldLighting result = new WorldLighting(copy, this.properties, this.legacyBlockMap,
                this.modernBlockStateMap, this.minY, this.sectionCount, this.hasSkyLight, this.modern);
        // Snapshots are replaced rather than mutated during recompute, so player
        // copies can share the template's immutable initial nibble arrays.
        result.blockSections = this.blockSections;
        result.skySections = this.skySections;
        return result;
    }

    private int[] resolveStates(int volume)
    {
        int[] states = new int[volume];
        int maxStoredY = 255;
        for (int localY = 0; localY < this.lightHeight; localY++)
        {
            int worldY = this.lightMinY + localY;
            if (worldY < 0 || worldY > maxStoredY)
                continue;
            int layer = localY * this.planeSize;
            for (int chunkLocalX = 0; chunkLocalX < this.worldWidthChunks; chunkLocalX++)
            {
                for (int chunkLocalZ = 0; chunkLocalZ < this.worldLengthChunks; chunkLocalZ++)
                {
                    Chunk chunk = this.world.getChunks()[chunkLocalX][chunkLocalZ];
                    if (chunk == null)
                        continue;
                    int baseX = (this.world.getChunkOriginX() + chunkLocalX
                            - this.domainOriginChunkX) * 16;
                    int baseZ = (this.world.getChunkOriginZ() + chunkLocalZ
                            - this.domainOriginChunkZ) * 16;
                    for (int z = 0; z < 16; z++)
                    {
                        int row = layer + (baseZ + z) * this.widthBlocks + baseX;
                        for (int x = 0; x < 16; x++)
                        {
                            int state;
                            if (!this.modern)
                            {
                                state = (chunk.getLegacyId(x, worldY, z) << 4)
                                        | (chunk.getLegacyMeta(x, worldY, z) & 0xF);
                            }
                            else if (this.world.hasModernBlockStates())
                            {
                                int stateKey = chunk.getModernStateKey(x, worldY, z);
                                state = stateKey == 0 ? 0
                                        : this.modernBlockStateMap.resolve(this.world.getModernState(stateKey));
                            }
                            else
                            {
                                state = this.legacyBlockMap.toGlobalState(this.world, chunk, x, worldY, z);
                            }
                            states[row + x] = state;
                        }
                    }
                }
            }
        }
        return states;
    }

    private void propagateBlockLight(int[] states, byte[] light)
    {
        IntQueue[] buckets = queues();
        for (int index = 0; index < states.length; index++)
        {
            int emission = this.properties.emission(states[index]);
            if (emission > 0)
            {
                light[index] = (byte) emission;
                buckets[emission].add(index);
            }
        }
        propagate(states, light, buckets);
    }

    private void propagateSkyLight(int[] states, byte[] light)
    {
        IntQueue[] buckets = queues();
        for (int z = 0; z < this.lengthBlocks; z++)
        {
            for (int x = 0; x < this.widthBlocks; x++)
            {
                int sourceState = 0;
                for (int localY = this.lightHeight - 1; localY >= 0; localY--)
                {
                    int index = localY * this.planeSize + z * this.widthBlocks + x;
                    int targetState = states[index];
                    if (this.properties.dampening(targetState) != 0
                            || this.properties.occludes(sourceState, targetState,
                            BlockLightingProperties.DOWN))
                        break;
                    light[index] = 15;
                    buckets[15].add(index);
                    sourceState = targetState;
                }
            }
        }
        // Direct-sky cells were seeded at 15. All propagation away from those direct columns,
        // including around an obstruction, follows the normal at-least-one attenuation rule.
        propagate(states, light, buckets);
    }

    private void propagate(int[] states, byte[] light, IntQueue[] buckets)
    {
        for (int level = 15; level > 0; level--)
        {
            IntQueue queue = buckets[level];
            while (queue.hasNext())
            {
                int index = queue.next();
                if ((light[index] & 0xFF) != level)
                    continue;
                int localY = index / this.planeSize;
                int inPlane = index - localY * this.planeSize;
                int z = inPlane / this.widthBlocks;
                int x = inPlane - z * this.widthBlocks;
                int sourceState = states[index];
                for (int direction = 0; direction < 6; direction++)
                {
                    int targetX = x + DX[direction];
                    int targetY = localY + DY[direction];
                    int targetZ = z + DZ[direction];
                    if (targetX < 0 || targetX >= this.widthBlocks
                            || targetY < 0 || targetY >= this.lightHeight
                            || targetZ < 0 || targetZ >= this.lengthBlocks)
                        continue;
                    int targetIndex = targetY * this.planeSize
                            + targetZ * this.widthBlocks + targetX;
                    int targetState = states[targetIndex];
                    if (this.properties.occludes(sourceState, targetState, direction))
                        continue;
                    int candidate = level - Math.max(1, this.properties.dampening(targetState));
                    if (candidate <= 0 || candidate <= (light[targetIndex] & 0xFF))
                        continue;
                    light[targetIndex] = (byte) candidate;
                    buckets[candidate].add(targetIndex);
                }
            }
        }
    }

    private byte[][] encodeSections(byte[] light)
    {
        byte[][] result = new byte[this.widthChunks * this.lengthChunks * this.lightSectionCount][];
        for (int chunkLocalX = 0; chunkLocalX < this.widthChunks; chunkLocalX++)
        {
            for (int chunkLocalZ = 0; chunkLocalZ < this.lengthChunks; chunkLocalZ++)
            {
                for (int section = 0; section < this.lightSectionCount; section++)
                {
                    byte[] nibbles = new byte[2048];
                    int sectionY = section * 16;
                    for (int y = 0; y < 16; y++)
                    {
                        int layer = (sectionY + y) * this.planeSize;
                        for (int z = 0; z < 16; z++)
                        {
                            int source = layer + (chunkLocalZ * 16 + z) * this.widthBlocks
                                    + chunkLocalX * 16;
                            int target = y * 256 + z * 16;
                            for (int x = 0; x < 16; x += 2)
                            {
                                int low = light[source + x] & 0xF;
                                int high = light[source + x + 1] & 0xF;
                                nibbles[(target + x) >> 1] = (byte) (low | (high << 4));
                            }
                        }
                    }
                    result[sectionArrayIndex(chunkLocalX, chunkLocalZ, section)] = nibbles;
                }
            }
        }
        return result;
    }

    private List<ChunkLightChange> diff(byte[][] oldBlock, byte[][] oldSky,
                                        byte[][] newBlock, byte[][] newSky)
    {
        if (oldBlock == null || oldSky == null)
            return Collections.emptyList();
        List<ChunkLightChange> changes = new ArrayList<>();
        for (int chunkLocalX = 0; chunkLocalX < this.widthChunks; chunkLocalX++)
        {
            for (int chunkLocalZ = 0; chunkLocalZ < this.lengthChunks; chunkLocalZ++)
            {
                BitSet block = new BitSet(this.lightSectionCount);
                BitSet sky = new BitSet(this.lightSectionCount);
                for (int section = 0; section < this.lightSectionCount; section++)
                {
                    int index = sectionArrayIndex(chunkLocalX, chunkLocalZ, section);
                    if (!Arrays.equals(oldBlock[index], newBlock[index]))
                        block.set(section);
                    if (!Arrays.equals(oldSky[index], newSky[index]))
                        sky.set(section);
                }
                if (!block.isEmpty() || !sky.isEmpty())
                    changes.add(new ChunkLightChange(this.domainOriginChunkX + chunkLocalX,
                            this.domainOriginChunkZ + chunkLocalZ, sky, block));
            }
        }
        return changes;
    }

    private byte[] section(byte[][] sections, int chunkX, int chunkZ, int section)
    {
        if (sections == null || section < 0 || section >= this.lightSectionCount)
            return null;
        int localX = chunkX - this.domainOriginChunkX;
        int localZ = chunkZ - this.domainOriginChunkZ;
        if (localX < 0 || localX >= this.widthChunks || localZ < 0 || localZ >= this.lengthChunks)
            return null;
        return sections[sectionArrayIndex(localX, localZ, section)];
    }

    private int lightAt(byte[][] sections, int x, int y, int z, int outsideDefault)
    {
        int section = Math.floorDiv(y - this.lightMinY, 16);
        if (section < 0 || section >= this.lightSectionCount)
            return 0;
        byte[] data = section(sections, x >> 4, z >> 4, section);
        if (data == null)
            return outsideDefault;
        int localY = Math.floorMod(y - this.lightMinY, 16);
        int nibbleIndex = (x & 15) + (z & 15) * 16 + localY * 256;
        int packed = data[nibbleIndex >> 1] & 0xFF;
        return (nibbleIndex & 1) == 0 ? packed & 0xF : packed >>> 4;
    }

    private int sectionArrayIndex(int chunkLocalX, int chunkLocalZ, int section)
    {
        return ((chunkLocalX * this.lengthChunks) + chunkLocalZ) * this.lightSectionCount + section;
    }

    private static IntQueue[] queues()
    {
        IntQueue[] queues = new IntQueue[16];
        for (int i = 0; i < queues.length; i++)
            queues[i] = new IntQueue();
        return queues;
    }

    private static boolean isAll(byte[] data, byte value)
    {
        if (data == null)
            return false;
        for (byte datum : data)
        {
            if (datum != value)
                return false;
        }
        return true;
    }

    public record ChunkLightChange(int chunkX, int chunkZ, BitSet skySections, BitSet blockSections)
    {
        public ChunkLightChange
        {
            skySections = (BitSet) skySections.clone();
            blockSections = (BitSet) blockSections.clone();
        }
    }

    private static final class IntQueue
    {
        private int[] values = new int[16];
        private int size;
        private int cursor;

        private void add(int value)
        {
            if (this.size == this.values.length)
                this.values = Arrays.copyOf(this.values, this.values.length << 1);
            this.values[this.size++] = value;
        }

        private boolean hasNext()
        {
            return this.cursor < this.size;
        }

        private int next()
        {
            return this.values[this.cursor++];
        }
    }
}
