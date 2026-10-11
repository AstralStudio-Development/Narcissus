package cn.starry.narcissus;

import cn.starry.narcissus.functions.packets.out.protocol.v774.ModernPacketEncoding;
import cn.starry.narcissus.functions.packets.protocols.ModernConfigurationDriver;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ModernPositionEncodingTest {
    @Test void packedModernPositionPlacesYInTheLowTwelveBits() {
        assertEquals(0x1040L, ModernPacketEncoding.position(0, 64, 1));
        assertEquals(0x48000022014L, ModernPacketEncoding.position(18, 20, 34));
        assertEquals(-1L, ModernPacketEncoding.position(-1, -1, -1));
    }
    @Test void authoritativeBlockSnapshotUsesTheModernWireLayout() throws Exception {
        var method = ModernConfigurationDriver.class.getDeclaredMethod("packBlockPosition", int.class, int.class, int.class);
        method.setAccessible(true);
        assertEquals(0x1040L, method.invoke(null, 0, 64, 1));
    }
}
