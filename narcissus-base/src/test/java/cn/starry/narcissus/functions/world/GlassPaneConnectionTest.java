package cn.starry.narcissus.functions.world;

import cn.starry.narcissus.functions.modern.LegacyBlockMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class GlassPaneConnectionTest {

    @Test
    void panesConnectAcrossChunkBoundary() {
        World world = new World(2, 1, 0, 0);
        world.setBlock(15, 10, 0, 160, (byte) 7);
        world.setBlock(16, 10, 0, 160, (byte) 7);

        LegacyBlockMap map = LegacyBlockMap.load("v774");
        Chunk left = world.getChunkAtWorldPos(15, 0);
        Chunk right = world.getChunkAtWorldPos(16, 0);

        assertEquals(1, map.paneConnectionMask(world, left, 15, 10, 0));
        assertEquals(8, map.paneConnectionMask(world, right, 0, 10, 0));
        assertNotEquals(
                map.toGlobalState(160, 7),
                map.toGlobalState(world, left, 15, 10, 0));
        assertNotEquals(
                map.toGlobalState(160, 7),
                map.toGlobalState(world, right, 0, 10, 0));
    }

    @Test
    void isolatedPaneKeepsAllConnectionsFalse() {
        World world = new World(1, 1);
        world.setBlock(1, 10, 1, 160, (byte) 7);

        LegacyBlockMap map = LegacyBlockMap.load("v774");
        Chunk chunk = world.getChunkAtWorldPos(1, 1);

        assertEquals(0, map.paneConnectionMask(world, chunk, 1, 10, 1));
        assertEquals(
                map.toGlobalState(160, 7),
                map.toGlobalState(world, chunk, 1, 10, 1));
    }

    @Test
    void paneConnectsToSchematicSolidBlock() {
        World world = new World(1, 1);
        world.setBlock(1, 10, 1, 160, (byte) 7);
        world.setBlock(2, 10, 1, 1, (byte) 0);

        LegacyBlockMap map = LegacyBlockMap.load("v774");
        Chunk chunk = world.getChunkAtWorldPos(1, 1);

        assertEquals(1, map.paneConnectionMask(world, chunk, 1, 10, 1));
    }
}
