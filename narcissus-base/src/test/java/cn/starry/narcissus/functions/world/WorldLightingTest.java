package cn.starry.narcissus.functions.world;

import cn.starry.narcissus.functions.modern.LegacyBlockMap;
import cn.starry.narcissus.functions.modern.ModernBlockStateMap;
import cn.starry.narcissus.functions.world.lighting.BlockLightingProperties;
import cn.starry.narcissus.functions.world.nbt.CompoundTag;
import cn.starry.narcissus.functions.world.nbt.NBTTag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Duration;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldLightingTest
{
    private static final BlockLightingProperties V47 =
            BlockLightingProperties.load("v47", "1.8.9", 4096);

    @Test
    void blockLightPropagatesAcrossChunkBoundaries()
    {
        World world = new World(2, 1);
        world.setBlock(15, 10, 0, 89, (byte) 0); // glowstone
        world.prepareLegacyLighting(V47, false);

        assertEquals(15, world.getLighting().blockLightAt(15, 10, 0));
        assertEquals(14, world.getLighting().blockLightAt(16, 10, 0));
        assertEquals(13, world.getLighting().blockLightAt(17, 10, 0));
    }

    @Test
    void targetDampeningAndOpaqueBlocksFollowVanillaAttenuation()
    {
        World water = new World(1, 1);
        water.setBlock(1, 10, 1, 89, (byte) 0);
        water.setBlock(2, 10, 1, 8, (byte) 0);
        water.prepareLegacyLighting(V47, false);
        assertEquals(12, water.getLighting().blockLightAt(2, 10, 1));

        World stone = new World(1, 1);
        stone.setBlock(1, 10, 1, 89, (byte) 0);
        stone.setBlock(2, 10, 1, 1, (byte) 0);
        stone.prepareLegacyLighting(V47, false);
        assertEquals(0, stone.getLighting().blockLightAt(2, 10, 1));
    }

    @Test
    void spatialSkyLightKeepsDirectColumnsAtFifteenAndSpreadsAroundRoofs()
    {
        World world = new World(1, 1);
        world.setBlock(8, 20, 8, 1, (byte) 0);
        world.prepareLegacyLighting(V47, true);

        assertEquals(15, world.getLighting().skyLightAt(8, 21, 8));
        assertEquals(0, world.getLighting().skyLightAt(8, 20, 8));
        assertEquals(14, world.getLighting().skyLightAt(8, 19, 8));

        World glass = new World(1, 1);
        glass.setBlock(8, 20, 8, 20, (byte) 0);
        glass.prepareLegacyLighting(V47, true);
        assertEquals(15, glass.getLighting().skyLightAt(8, 19, 8));

        World cave = new World(3, 3);
        for (int x = 0; x < 48; x++)
        {
            for (int z = 0; z < 48; z++)
                cave.setBlock(x, 20, z, 1, (byte) 0);
        }
        cave.prepareLegacyLighting(V47, true);
        assertEquals(15, cave.getLighting().skyLightAt(24, 21, 24));
        assertEquals(0, cave.getLighting().skyLightAt(24, 19, 24));

        World noSky = new World(1, 1);
        noSky.prepareLegacyLighting(V47, false);
        assertEquals(0, noSky.getLighting().skyLightAt(8, 20, 8));
    }

    @Test
    void propagationDomainIncludesAFullChunkHalo()
    {
        World world = new World(1, 1);
        world.setBlock(15, 10, 0, 89, (byte) 0);
        world.prepareLegacyLighting(V47, false);
        WorldLighting lighting = world.getLighting();

        assertEquals(-1, lighting.minChunkX());
        assertEquals(1, lighting.maxChunkX());
        assertTrue(lighting.containsChunk(1, 0));
        assertEquals(14, lighting.blockLightAt(16, 10, 0));
        assertEquals(1, lighting.blockLightAt(29, 10, 0));
        assertEquals(0, lighting.blockLightAt(30, 10, 0));
        assertTrue(lighting.hasBlockLight(1, 0));
    }

    @Test
    void negativeOriginHaloUsesFloorChunkCoordinates()
    {
        World world = new World(1, 1, -1, -1);
        world.setBlock(-16, 10, -16, 89, (byte) 0);
        world.prepareLegacyLighting(V47, false);

        assertEquals(-2, world.getLighting().minChunkX());
        assertEquals(-2, world.getLighting().minChunkZ());
        assertEquals(14, world.getLighting().blockLightAt(-17, 10, -16));
        assertEquals(14, world.getLighting().blockLightAt(-16, 10, -17));
    }

    @Test
    void multipleEmittersTakeTheMaximumPropagatedLevel()
    {
        World world = new World(1, 1);
        world.setBlock(1, 10, 1, 50, (byte) 0); // torch 14
        world.setBlock(9, 10, 1, 89, (byte) 0); // glowstone 15
        world.prepareLegacyLighting(V47, false);

        assertEquals(11, world.getLighting().blockLightAt(5, 10, 1));
        assertEquals(14, world.getLighting().blockLightAt(8, 10, 1));
    }

    @Test
    void removingRoofRecomputesSkyWithoutResidualShadow()
    {
        World world = new World(1, 1);
        world.setBlock(8, 20, 8, 1, (byte) 0);
        world.prepareLegacyLighting(V47, true);
        assertEquals(14, world.getLighting().skyLightAt(8, 19, 8));

        assertTrue(world.setBlockAir(8, 20, 8));
        assertEquals(15, world.getLighting().skyLightAt(8, 19, 8));
        assertFalse(world.consumeLightChanges().isEmpty());
    }

    @Test
    void modernProfilesUseTheirExactGlobalStateIds()
    {
        assertModernCrossChunk("v774", "1.21.11");
        assertModernCrossChunk("v776", "26.2");
    }

    @Test
    void representativeMultiChunkRelightStaysWithinBudget()
    {
        World world = new World(8, 8);
        for (int chunkX = 0; chunkX < 8; chunkX++)
        {
            for (int chunkZ = 0; chunkZ < 8; chunkZ++)
                world.setBlock((chunkX << 4) + 8, 64, (chunkZ << 4) + 8, 89, (byte) 0);
        }

        assertTimeout(Duration.ofSeconds(10), () -> world.prepareLegacyLighting(V47, false));
        assertEquals(-1, world.getLighting().minChunkX());
        assertEquals(8, world.getLighting().maxChunkX());
    }

    @Test
    void bundledSchematicNowContainsRealTorchLighting() throws Exception
    {
        World world;
        try (InputStream raw = Schematic.class.getResourceAsStream("/world.schematic"))
        {
            assertNotNull(raw);
            CompoundTag nbt = NBTTag.readTag(new GZIPInputStream(raw)).toCompoundTag();
            world = Schematic.toWorld(nbt);
        }
        world.prepareLegacyLighting(V47, true);

        boolean foundTorch = false;
        boolean foundPropagation = false;
        for (Chunk[] column : world.getChunks())
        {
            for (Chunk chunk : column)
            {
                if (chunk == null)
                    continue;
                for (int y = 0; y < 256; y++)
                {
                    for (int z = 0; z < 16; z++)
                    {
                        for (int x = 0; x < 16; x++)
                        {
                            if (chunk.getLegacyId(x, y, z) != 50)
                                continue;
                            foundTorch = true;
                            int worldX = (chunk.getX() << 4) | x;
                            int worldZ = (chunk.getZ() << 4) | z;
                            assertEquals(14, world.getLighting().blockLightAt(worldX, y, worldZ));
                            for (int[] offset : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0},
                                    {0, -1, 0}, {0, 0, 1}, {0, 0, -1}})
                            {
                                if (world.getLighting().blockLightAt(worldX + offset[0],
                                        y + offset[1], worldZ + offset[2]) > 0)
                                    foundPropagation = true;
                            }
                        }
                    }
                }
            }
        }
        assertTrue(foundTorch, "bundled schematic must retain its torch blocks");
        assertTrue(foundPropagation, "torch light must visibly propagate into the schematic");
    }

    @Test
    void templateCachesAllProtocolProfilesForPlayerCopies()
    {
        World template = new World(1, 1);
        template.setBlock(1, 10, 1, 89, (byte) 0);
        template.prepareLegacyLighting(V47, false);
        byte[] legacySection = template.getLighting().blockSection(0, 0, 0);

        ModernBlockStateMap states774 = ModernBlockStateMap.load("v774");
        template.prepareModernLighting(BlockLightingProperties.load("v774", "1.21.11", states774.size()),
                LegacyBlockMap.load("v774"), states774, 0, 16, false);
        byte[] section774 = template.getLighting().blockSection(0, 0, 1);

        ModernBlockStateMap states776 = ModernBlockStateMap.load("v776");
        template.prepareModernLighting(BlockLightingProperties.load("v776", "26.2", states776.size()),
                LegacyBlockMap.load("v776"), states776, 0, 16, false);
        byte[] section776 = template.getLighting().blockSection(0, 0, 1);

        World copy = template.copy();
        copy.prepareLegacyLighting(V47, false);
        assertSame(legacySection, copy.getLighting().blockSection(0, 0, 0));
        copy.prepareModernLighting(BlockLightingProperties.load("v774", "1.21.11", states774.size()),
                LegacyBlockMap.load("v774"), states774, 0, 16, false);
        assertSame(section774, copy.getLighting().blockSection(0, 0, 1));
        copy.prepareModernLighting(BlockLightingProperties.load("v776", "26.2", states776.size()),
                LegacyBlockMap.load("v776"), states776, 0, 16, false);
        assertSame(section776, copy.getLighting().blockSection(0, 0, 1));
    }

    @Test
    void privateWorldRelightingDoesNotLeakToOtherPlayers()
    {
        World template = new World(1, 1);
        template.setBlock(1, 10, 1, 89, (byte) 0);
        template.prepareLegacyLighting(V47, false);
        World playerA = template.copy();
        World playerB = template.copy();

        byte[] templateSection = template.getLighting().blockSection(0, 0, 0);
        assertSame(templateSection, playerA.getLighting().blockSection(0, 0, 0));
        assertSame(templateSection, playerB.getLighting().blockSection(0, 0, 0));
        assertEquals(14, playerA.getLighting().blockLightAt(2, 10, 1));
        assertTrue(playerA.setBlockAir(1, 10, 1));
        assertFalse(playerA.consumeLightChanges().isEmpty());
        assertNotSame(templateSection, playerA.getLighting().blockSection(0, 0, 0));
        assertEquals(0, playerA.getLighting().blockLightAt(2, 10, 1));
        assertEquals(14, playerB.getLighting().blockLightAt(2, 10, 1));
        assertEquals(14, template.getLighting().blockLightAt(2, 10, 1));
    }

    private static void assertModernCrossChunk(String versionDir, String sourceVersion)
    {
        ModernBlockPalette palette = new ModernBlockPalette();
        World world = new World(2, 1, 0, 0, palette);
        world.setModernBlockState(15, 10, 0, palette.indexOf("minecraft:glowstone"));
        ModernBlockStateMap stateMap = ModernBlockStateMap.load(versionDir);
        world.prepareModernLighting(BlockLightingProperties.load(versionDir, sourceVersion,
                        stateMap.size()), LegacyBlockMap.load(versionDir), stateMap,
                0, 16, false);
        assertEquals(15, world.getLighting().blockLightAt(15, 10, 0));
        assertEquals(14, world.getLighting().blockLightAt(16, 10, 0));
    }
}
