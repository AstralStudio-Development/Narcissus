package cn.starry.narcissus.functions.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldIsolationTest {

    @Test
    void copiedWorldsDoNotShareBlockStorage() {
        World template = new World(1, 1);
        template.setBlock(2, 10, 3, 2, (byte) 0);

        World playerA = template.copy();
        World playerB = template.copy();

        assertNotSame(template, playerA);
        assertNotSame(playerA, playerB);
        assertNotSame(template.getChunks()[0][0], playerA.getChunks()[0][0]);
        assertNotSame(playerA.getChunks()[0][0], playerB.getChunks()[0][0]);
        assertNotSame(template.getChunks()[0][0].getMap(), playerA.getChunks()[0][0].getMap());
        assertNotSame(playerA.getChunks()[0][0].getMap(), playerB.getChunks()[0][0].getMap());

        assertEquals(2, legacyId(playerA, 2, 10, 3));
        assertEquals(2, legacyId(playerB, 2, 10, 3));
        assertEquals(2, legacyId(template, 2, 10, 3));

        assertTrue(playerA.setBlockAir(2, 10, 3));

        assertEquals(0, legacyId(playerA, 2, 10, 3));
        assertEquals(2, legacyId(playerB, 2, 10, 3));
        assertEquals(2, legacyId(template, 2, 10, 3));
    }

    private static int legacyId(World world, int x, int y, int z) {
        Chunk chunk = world.getChunks()[x >> 4][z >> 4];
        int index = (x & 0xF) + y * 256 + (z & 0xF) * 16;
        index *= 2;
        int low = chunk.getMap()[index] & 0xFF;
        int high = chunk.getMap()[index + 1] & 0xFF;
        return ((high & 0xF) << 4) | (low >> 4);
    }
}
