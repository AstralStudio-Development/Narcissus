package cn.starry.narcissus.functions.world;

import cn.starry.narcissus.functions.modern.ModernBlockStateMap;
import cn.starry.narcissus.functions.world.lighting.BlockLightingProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockLightingPropertiesTest
{
    @Test
    void loadsCompleteLegacyOpticalTable()
    {
        BlockLightingProperties properties = BlockLightingProperties.load("v47", "1.8.9", 4096);
        assertEquals(4096, properties.stateCount());
        assertEquals(0, properties.emission(0)); // air
        assertEquals(15, properties.emission(10 << 4)); // lava
        assertEquals(14, properties.emission(50 << 4)); // torch
        assertEquals(13, properties.emission(62 << 4)); // lit furnace
        assertEquals(7, properties.emission(76 << 4)); // lit redstone torch
        assertEquals(15, properties.emission(89 << 4)); // glowstone
        assertEquals(15, properties.emission(124 << 4)); // lit redstone lamp
        assertEquals(15, properties.dampening(1 << 4)); // stone
        assertEquals(3, properties.dampening(8 << 4)); // water
        assertEquals(1, properties.dampening(18 << 4)); // leaves
        assertEquals(0, properties.dampening(20 << 4)); // glass
        assertEquals("b58b2ceb36e01bcd8dbf49c8fb66c55a9f0676cd", properties.sourceHash());
    }

    @Test
    void legacyComplementarySlabFacesOccludeOnlyAsAUnion()
    {
        BlockLightingProperties properties = BlockLightingProperties.load("v47", "1.8.9", 4096);
        int bottom = (44 << 4);
        int top = (44 << 4) | 8;
        assertTrue(properties.usesShapeForLightOcclusion(bottom));
        assertFalse(properties.occludes(bottom, bottom, BlockLightingProperties.EAST));
        assertTrue(properties.occludes(bottom, top, BlockLightingProperties.EAST));
    }

    @Test
    void loadsOfficialModernPropertiesForAllProtocols()
    {
        assertModern("v774", "1.21.11");
        assertModern("v775", "26.1");
        assertModern("v776", "26.2");
        assertModern("v777", "26.3");
    }

    private static void assertModern(String versionDir, String sourceVersion)
    {
        ModernBlockStateMap states = ModernBlockStateMap.load(versionDir);
        BlockLightingProperties properties = BlockLightingProperties.load(
                versionDir, sourceVersion, states.size());
        assertEquals(sourceVersion, properties.sourceVersion());
        assertEquals(0, properties.emission(states.resolve("minecraft:air")));
        assertEquals(14, properties.emission(states.resolve("minecraft:torch")));
        assertEquals(10, properties.emission(states.resolve("minecraft:soul_torch")));
        assertEquals(15, properties.emission(states.resolve("minecraft:glowstone")));
        assertEquals(15, properties.emission(states.resolve("minecraft:lava[level=0]")));
        assertEquals(15, properties.emission(states.resolve("minecraft:redstone_lamp[lit=true]")));
        assertEquals(0, properties.emission(states.resolve("minecraft:redstone_lamp[lit=false]")));
        assertEquals(12, properties.emission(states.resolve(
                "minecraft:candle[candles=4,lit=true,waterlogged=false]")));
        assertEquals(0, properties.emission(states.resolve(
                "minecraft:candle[candles=4,lit=false,waterlogged=false]")));
        assertEquals(15, properties.dampening(states.resolve("minecraft:stone")));
        assertEquals(0, properties.dampening(states.resolve("minecraft:glass")));
        assertTrue(properties.canOcclude(states.resolve("minecraft:stone")));
        assertFalse(properties.canOcclude(states.resolve("minecraft:air")));

        int bottom = states.resolve("minecraft:stone_slab[type=bottom,waterlogged=false]");
        int top = states.resolve("minecraft:stone_slab[type=top,waterlogged=false]");
        assertTrue(properties.usesShapeForLightOcclusion(bottom));
        assertFalse(properties.occludes(bottom, bottom, BlockLightingProperties.EAST));
        assertTrue(properties.occludes(bottom, top, BlockLightingProperties.EAST));
    }
}
