package cn.starry.narcissus.util;

import cn.starry.narcissus.functions.packets.PlayerListEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinPropertyPayloadTest {
    @Test
    void roundTripsSignedTexturesProperty() throws Exception {
        PlayerListEntry.ProfileProperty source = new PlayerListEntry.ProfileProperty(
                "textures", "encoded-value", "encoded-signature");

        PlayerListEntry.ProfileProperty decoded = SkinPropertyPayload.decode(
                SkinPropertyPayload.encode(source));

        assertEquals("textures", decoded.getName());
        assertEquals("encoded-value", decoded.getValue());
        assertEquals("encoded-signature", decoded.getSignature());
    }

    @Test
    void roundTripsUnsignedTexturesProperty() throws Exception {
        PlayerListEntry.ProfileProperty source = new PlayerListEntry.ProfileProperty(
                "textures", "encoded-value", null);

        PlayerListEntry.ProfileProperty decoded = SkinPropertyPayload.decode(
                SkinPropertyPayload.encode(source));

        assertEquals("encoded-value", decoded.getValue());
        assertNull(decoded.getSignature());
    }

    @Test
    void recognizesBothProxyChannelNames() {
        assertTrue(SkinPropertyPayload.isSkinChannel("narcissus:skin"));
        assertTrue(SkinPropertyPayload.isSkinChannel("NarcissusSkin"));
    }
}
