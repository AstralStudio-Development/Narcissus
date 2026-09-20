package cn.starry.narcissus.functions.packets.protocols;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModernConfigurationDriverTest
{
    @Test
    void joinViewUsesOneCompleteThirtyByThirtyBatch()
    {
        assertEquals(30, ModernConfigurationDriver.BOOTSTRAP_CHUNK_DIAMETER);
        assertEquals(30, ModernConfigurationDriver.JOIN_CHUNK_DIAMETER);
        assertEquals(900, ModernConfigurationDriver.JOIN_CHUNK_DIAMETER
                * ModernConfigurationDriver.JOIN_CHUNK_DIAMETER);
        assertEquals(-15, ModernConfigurationDriver.chunkViewStart(0,
                ModernConfigurationDriver.JOIN_CHUNK_DIAMETER));
        assertEquals(1, ModernConfigurationDriver.chunkViewStart(16,
                ModernConfigurationDriver.JOIN_CHUNK_DIAMETER));
    }
}
