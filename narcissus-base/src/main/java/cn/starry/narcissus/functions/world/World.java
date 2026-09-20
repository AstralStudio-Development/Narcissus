package cn.starry.narcissus.functions.world;

import cn.starry.narcissus.functions.modern.LegacyBlockMap;
import cn.starry.narcissus.functions.modern.ModernBlockStateMap;
import cn.starry.narcissus.functions.world.lighting.BlockLightingProperties;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Rigner on 30/08/16 for project Limbo.
 * All rights reserved.
 */
public class World
{
    private final Chunk[][] chunks;
    /** Absolute chunk coordinate represented by array index zero. */
    private final int chunkOriginX;
    private final int chunkOriginZ;
    /** Shared schematic-local palette for a modern .schem world, or null for legacy worlds. */
    private final ModernBlockPalette modernBlockPalette;
    /** Protocol-specific light solutions; template copies share immutable initial arrays. */
    private final Map<LightingProfileKey, WorldLighting> lightingCache = new HashMap<>();
    private WorldLighting lighting;
    private List<WorldLighting.ChunkLightChange> pendingLightChanges = Collections.emptyList();

    World(int width, int length)
    {
        this(width, length, 0, 0, null);
    }

    /**
     * Creates a world whose first array cell represents {@code chunkOriginX/Z}.
     * This keeps schematic coordinates absolute while allowing a placement that
     * crosses zero or starts at a non-zero chunk coordinate.
     */
    World(int width, int length, int chunkOriginX, int chunkOriginZ)
    {
        this(width, length, chunkOriginX, chunkOriginZ, null);
    }

    World(int width, int length, int chunkOriginX, int chunkOriginZ,
          ModernBlockPalette modernBlockPalette)
    {
        if (width < 1 || length < 1)
            throw new IllegalArgumentException("World dimensions must be positive");
        this.chunks = new Chunk[width][length];
        this.chunkOriginX = chunkOriginX;
        this.chunkOriginZ = chunkOriginZ;
        this.modernBlockPalette = modernBlockPalette;
    }

    void setBlock(int x, int y, int z, int id, byte data)
    {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        int arrayX = chunkX - this.chunkOriginX;
        int arrayZ = chunkZ - this.chunkOriginZ;
        if (!isChunkInBounds(arrayX, arrayZ))
            throw new IllegalArgumentException("Block outside world bounds: " + x + "," + y + "," + z);

        Chunk chunk = this.chunks[arrayX][arrayZ];
        if (chunk == null)
        {
            chunk = new Chunk(chunkX, chunkZ);
            this.chunks[arrayX][arrayZ] = chunk;
        }
        chunk.setBlock(x & 0xF, y, z & 0xF, id, data);
    }

    void setModernBlockState(int x, int y, int z, int stateKey)
    {
        if (this.modernBlockPalette == null)
            throw new IllegalStateException("World has no modern block-state palette");
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        int arrayX = chunkX - this.chunkOriginX;
        int arrayZ = chunkZ - this.chunkOriginZ;
        if (y < 0 || y >= 256 || !isChunkInBounds(arrayX, arrayZ))
            throw new IllegalArgumentException("Modern block outside world bounds: " + x + "," + y + "," + z);

        Chunk chunk = this.chunks[arrayX][arrayZ];
        if (chunk == null)
        {
            chunk = new Chunk(chunkX, chunkZ);
            this.chunks[arrayX][arrayZ] = chunk;
        }
        chunk.setModernStateKey(x & 0xF, y, z & 0xF, stateKey);
    }

    void addModernBlockEntity(ModernBlockEntity blockEntity)
    {
        if (blockEntity == null)
            return;
        BlockPosition position = blockEntity.getPosition();
        if (position.getY() < 0 || position.getY() >= 256)
            throw new IllegalArgumentException("Modern BlockEntity outside world height: " + position);
        Chunk chunk = getChunkAtWorldPos(position.getX(), position.getZ());
        if (chunk != null)
            chunk.addModernBlockEntity(blockEntity);
    }

    public boolean hasModernBlockStates()
    {
        return this.modernBlockPalette != null;
    }

    public ModernBlockPalette getModernBlockPalette()
    {
        return this.modernBlockPalette;
    }

    public String getModernState(int stateKey)
    {
        return this.modernBlockPalette == null
                ? "minecraft:air"
                : this.modernBlockPalette.stateAt(stateKey);
    }

    /** Computes and installs the complete v47 light solution once before chunk serialization. */
    public synchronized void prepareLegacyLighting(BlockLightingProperties properties,
                                                   boolean hasSkyLight)
    {
        LightingProfileKey key = new LightingProfileKey(properties, 0, 16, hasSkyLight, false);
        WorldLighting cached = this.lightingCache.get(key);
        if (cached == null)
        {
            cached = WorldLighting.legacy(this, properties, hasSkyLight);
            cached.recompute();
            this.lightingCache.put(key, cached);
        }
        this.lighting = cached;
        this.pendingLightChanges = Collections.emptyList();
    }

    /** Computes and installs the complete protocol-specific modern light solution. */
    public synchronized void prepareModernLighting(BlockLightingProperties properties,
                                                   LegacyBlockMap legacyBlockMap,
                                                   ModernBlockStateMap modernBlockStateMap,
                                                   int minY, int sectionCount,
                                                   boolean hasSkyLight)
    {
        LightingProfileKey key = new LightingProfileKey(properties, minY, sectionCount,
                hasSkyLight, true);
        WorldLighting cached = this.lightingCache.get(key);
        if (cached == null)
        {
            cached = WorldLighting.modern(this, properties, legacyBlockMap,
                    modernBlockStateMap, minY, sectionCount, hasSkyLight);
            cached.recompute();
            this.lightingCache.put(key, cached);
        }
        this.lighting = cached;
        this.pendingLightChanges = Collections.emptyList();
    }

    public synchronized WorldLighting getLighting()
    {
        return this.lighting;
    }

    /** Returns and clears the sections changed by the latest private-world mutation. */
    public synchronized List<WorldLighting.ChunkLightChange> consumeLightChanges()
    {
        List<WorldLighting.ChunkLightChange> result = this.pendingLightChanges;
        this.pendingLightChanges = Collections.emptyList();
        return result;
    }

    /** Returns a legacy block id at absolute coordinates, or air when absent. */
    public int getLegacyId(int x, int y, int z)
    {
        if (y < 0 || y > 255)
            return 0;
        Chunk chunk = getChunkAtWorldPos(x, z);
        return chunk == null ? 0 : chunk.getLegacyId(x & 0xF, y, z & 0xF);
    }

    /** Returns a legacy metadata nibble at absolute coordinates, or zero. */
    public int getLegacyMeta(int x, int y, int z)
    {
        if (y < 0 || y > 255)
            return 0;
        Chunk chunk = getChunkAtWorldPos(x, z);
        return chunk == null ? 0 : chunk.getLegacyMeta(x & 0xF, y, z & 0xF);
    }

    private boolean isChunkInBounds(int arrayX, int arrayZ)
    {
        return arrayX >= 0 && arrayZ >= 0
                && arrayX < this.chunks.length
                && arrayZ < this.chunks[arrayX].length;
    }

    /**
     * Sets the block at world coordinates to air. Returns false when the
     * position lies outside the loaded schematic (or targets an empty chunk),
     * so callers can skip echoing an update for out-of-world digs.
     */
    public boolean setBlockAir(int x, int y, int z)
    {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        int arrayX = chunkX - this.chunkOriginX;
        int arrayZ = chunkZ - this.chunkOriginZ;
        if (y < 0 || y > 255 || !isChunkInBounds(arrayX, arrayZ))
        {
            return false;
        }
        Chunk chunk = this.chunks[arrayX][arrayZ];
        if (chunk == null)
        {
            return false;
        }
        chunk.setBlock(x & 0xF, y, z & 0xF, 0, (byte) 0);
        if (this.modernBlockPalette != null)
        {
            chunk.setModernStateKey(x & 0xF, y, z & 0xF, 0);
            chunk.removeModernBlockEntitiesAt(x, y, z);
        }
        if (this.lighting != null)
        {
            // Other protocol snapshots describe the old block state. Keep only
            // the active player's profile and replace its arrays atomically.
            this.lightingCache.clear();
            this.pendingLightChanges = this.lighting.recompute();
            this.lightingCache.put(profileKey(this.lighting), this.lighting);
        }
        return true;
    }

    /**
     * Creates a private mutable world copy for one connection. The schematic
     * template and every other player's copy remain untouched when this copy is
     * edited.
     */
    public World copy()
    {
        int length = this.chunks.length == 0 ? 0 : this.chunks[0].length;
        World copy = new World(this.chunks.length, length, this.chunkOriginX, this.chunkOriginZ,
                this.modernBlockPalette);
        for (int x = 0; x < this.chunks.length; x++)
        {
            for (int z = 0; z < this.chunks[x].length; z++)
            {
                Chunk chunk = this.chunks[x][z];
                if (chunk != null)
                    copy.chunks[x][z] = chunk.copy();
            }
        }
        for (Map.Entry<LightingProfileKey, WorldLighting> entry : this.lightingCache.entrySet())
        {
            WorldLighting copiedLighting = entry.getValue().copyFor(copy);
            copy.lightingCache.put(entry.getKey(), copiedLighting);
            if (entry.getValue() == this.lighting)
                copy.lighting = copiedLighting;
        }
        return copy;
    }

    /** Returns the highest non-air block at an absolute X/Z column, or -1. */
    public int getHighestNonAirY(int x, int z)
    {
        Chunk chunk = getChunkAtWorldPos(x, z);
        if (chunk == null)
            return -1;
        for (int y = 255; y >= 0; y--)
        {
            if (this.modernBlockPalette != null
                    ? chunk.getModernStateKey(x & 0xF, y, z & 0xF) != 0
                    : chunk.getLegacyId(x & 0xF, y, z & 0xF) != 0)
                return y;
        }
        return -1;
    }

    /** Returns the highest non-air block in a square around an absolute X/Z. */
    public int getHighestNonAirYInRadius(int centerX, int centerZ, int radius)
    {
        int highest = -1;
        for (int x = centerX - radius; x <= centerX + radius; x++)
        {
            for (int z = centerZ - radius; z <= centerZ + radius; z++)
                highest = Math.max(highest, getHighestNonAirY(x, z));
        }
        return highest;
    }

    /**
     * Returns a spawn Y that leaves two blocks of air above the loaded structure.
     * Spectators can intentionally spawn inside blocks, but all other game modes
     * must not be placed inside the schematic's solid area.
     */
    public double getSafeSpawnY(double spawnX, double spawnY, double spawnZ, int gameMode)
    {
        if (gameMode == 3)
            return spawnY;

        int highestNearby = getHighestNonAirYInRadius(
                (int) Math.floor(spawnX),
                (int) Math.floor(spawnZ),
                1);
        if (highestNearby >= (int) Math.floor(spawnY))
            return highestNearby + 2.0D;
        return spawnY;
    }

    public Chunk[][] getChunks()
    {
        return this.chunks;
    }

    Chunk getChunkAtWorldPos(int x, int z)
    {
        return getChunk(x >> 4, z >> 4);
    }

    /** Returns the stored schematic chunk at absolute chunk coordinates, or null. */
    public Chunk getChunk(int chunkX, int chunkZ)
    {
        int arrayX = chunkX - this.chunkOriginX;
        int arrayZ = chunkZ - this.chunkOriginZ;
        return isChunkInBounds(arrayX, arrayZ) ? this.chunks[arrayX][arrayZ] : null;
    }

    int getChunkOriginX()
    {
        return this.chunkOriginX;
    }

    int getChunkOriginZ()
    {
        return this.chunkOriginZ;
    }

    private static LightingProfileKey profileKey(WorldLighting lighting)
    {
        return new LightingProfileKey(lighting.properties(), lighting.minY(),
                lighting.sectionCount(), lighting.hasSkyLight(), lighting.isModern());
    }

    private record LightingProfileKey(BlockLightingProperties properties, int minY,
                                      int sectionCount, boolean hasSkyLight, boolean modern)
    {
    }
}

