package cn.starry.narcissus.functions.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldCoordinateOriginTest {

    @Test
    void absoluteCoordinatesSurviveNonZeroChunkOriginAndCopy() {
        World template = new World(2, 2, -1, -1);
        template.setBlock(-1, 10, -1, 2, (byte) 0);

        Chunk chunk = template.getChunkAtWorldPos(-1, -1);
        assertNotNull(chunk);
        assertEquals(-1, chunk.getX());
        assertEquals(-1, chunk.getZ());
        assertEquals(2, legacyId(template, -1, 10, -1));

        World copy = template.copy();
        assertEquals(2, legacyId(copy, -1, 10, -1));
        assertTrue(copy.setBlockAir(-1, 10, -1));
        assertEquals(0, legacyId(copy, -1, 10, -1));
        assertEquals(2, legacyId(template, -1, 10, -1));
    }

    @Test
    void highestNonAirYWorksWithAbsoluteCoordinates() {
        World world = new World(2, 2, -1, -1);
        world.setBlock(-1, 71, -1, 2, (byte) 0);
        world.setBlock(0, 109, 0, 2, (byte) 0);

        assertEquals(71, world.getHighestNonAirY(-1, -1));
        assertEquals(109, world.getHighestNonAirYInRadius(0, 0, 1));
    }

    @Test
    void airUpdatesRejectCoordinatesOutsideTranslatedWorld() {
        World world = new World(1, 1, 2, 3);
        assertFalse(world.setBlockAir(31, 10, 47));
    }

    @Test
    void safeSpawnMovesNonSpectatorsAboveSolidSchematic() {
        World world = new World(1, 1, 0, 0);
        world.setBlock(0, 90, 0, 1, (byte) 0);
        world.setBlock(1, 109, 1, 1, (byte) 0);

        assertEquals(111.0D, world.getSafeSpawnY(0.5D, 90.0D, 0.5D, 2));
        assertEquals(90.0D, world.getSafeSpawnY(0.5D, 90.0D, 0.5D, 3));
    }

    private static int legacyId(World world, int x, int y, int z) {
        Chunk chunk = world.getChunkAtWorldPos(x, z);
        assertNotNull(chunk);
        int index = (x & 0xF) + y * 256 + (z & 0xF) * 16;
        index *= 2;
        int low = chunk.getMap()[index] & 0xFF;
        int high = chunk.getMap()[index + 1] & 0xFF;
        return ((high & 0xF) << 4) | (low >> 4);
    }
}
