package cn.starry.narcissus.functions.world;

import cn.starry.narcissus.functions.modern.LegacyBlockMap;
import cn.starry.narcissus.functions.modern.ModernBlockStateMap;
import cn.starry.narcissus.functions.modern.ModernChunk;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class ModernChunkStorageBoundaryTest {
    @Test void legacyLightStorageDoesNotBecomeBlocksAboveY255() {
        var world = new World(1, 1);
        world.setBlock(0, 0, 0, 0, (byte) 0); // allocate an otherwise empty source chunk
        var chunk = world.getChunk(0, 0);
        // Block-light storage follows the 131072-byte legacy block-state region.
        Arrays.fill(chunk.getMap(), 131072, 163840, (byte) 1);
        assertTrue(ModernChunk.nonEmptySections(world, chunk, 24, 0).isEmpty());
        long[] heights = ModernChunk.buildMotionBlockingHeightmap(world, chunk, 24, 0,
                ModernBlockStateMap.load("v774"));
        assertTrue(Arrays.stream(heights).allMatch(value -> value == 0));
        assertArrayEquals(ModernChunk.emptyChunkData(24, 40, true),
                ModernChunk.buildChunkData(world, chunk, 24, 0, 40,
                        LegacyBlockMap.load("v774"), ModernBlockStateMap.load("v774"), true));
    }
}
