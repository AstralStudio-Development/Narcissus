package cn.starry.narcissus.util;

import cn.starry.narcissus.functions.packets.PlayerListEntry;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MojangSkinProviderTest {
    @Test
    void parsesAshconSignedTexturesProperty() {
        String json = "{\"uuid\":\"0123456789abcdef0123456789abcdef\","
                + "\"textures\":{\"raw\":{\"value\":\"skin-value\","
                + "\"signature\":\"skin-signature\"}}}";

        Optional<PlayerListEntry.ProfileProperty> property = MojangSkinProvider.parseAshconTextures(json);

        assertTrue(property.isPresent());
        assertEquals("textures", property.get().getName());
        assertEquals("skin-value", property.get().getValue());
        assertEquals("skin-signature", property.get().getSignature());
    }

    @Test
    void parsesAshconUnsignedTexturesProperty() {
        String json = "{\"textures\":{\"raw\":{\"value\":\"skin-value\"}}}";

        Optional<PlayerListEntry.ProfileProperty> property = MojangSkinProvider.parseAshconTextures(json);

        assertTrue(property.isPresent());
        assertEquals("skin-value", property.get().getValue());
        assertEquals(null, property.get().getSignature());
    }

    @Test
    void convertsAshconSkinUrlToStandardTexturesProperty() {
        String json = "{\"uuid\":\"01234567-89ab-cdef-0123-456789abcdef\","
                + "\"username\":\"Alex\",\"slim\":true,"
                + "\"textures\":{\"skin\":{\"url\":\"http://textures.minecraft.net/texture/abc\"},"
                + "\"cape\":{\"url\":\"http://textures.minecraft.net/texture/cape\"}}}";

        Optional<PlayerListEntry.ProfileProperty> property = MojangSkinProvider.parseAshconTextures(json);

        assertTrue(property.isPresent());
        assertEquals("textures", property.get().getName());
        assertEquals(null, property.get().getSignature());
        String decoded = new String(Base64.getDecoder().decode(property.get().getValue()), StandardCharsets.UTF_8);
        assertTrue(decoded.contains("\"profileId\":\"0123456789abcdef0123456789abcdef\""));
        assertTrue(decoded.contains("\"profileName\":\"Alex\""));
        assertTrue(decoded.contains("\"model\":\"slim\""));
        assertTrue(decoded.contains("http://textures.minecraft.net/texture/abc"));
        assertTrue(decoded.contains("http://textures.minecraft.net/texture/cape"));
    }

    @Test
    void ignoresAshconResponseWithoutRawTextures() {
        assertEquals(Optional.empty(), MojangSkinProvider.parseAshconTextures(
                "{\"textures\":{\"raw\":null}}"));
    }

    @Test
    void parsesSignedTexturesProperty() {
        String json = "{\"id\":\"0123456789abcdef0123456789abcdef\","
                + "\"properties\":[{\"name\":\"textures\",\"value\":\"skin-value\","
                + "\"signature\":\"skin-signature\"}]}";

        Optional<PlayerListEntry.ProfileProperty> property = MojangSkinProvider.parseTextures(json);

        assertTrue(property.isPresent());
        assertEquals("textures", property.get().getName());
        assertEquals("skin-value", property.get().getValue());
        assertEquals("skin-signature", property.get().getSignature());
    }

    @Test
    void parsesUnsignedTexturesPropertyWithoutSignature() {
        String json = "{\"properties\":[{\"name\":\"textures\",\"value\":\"skin-value\"}]}";

        Optional<PlayerListEntry.ProfileProperty> property = MojangSkinProvider.parseTextures(json);

        assertTrue(property.isPresent());
        assertEquals("skin-value", property.get().getValue());
        assertEquals(null, property.get().getSignature());
    }

    @Test
    void parsesMojangProfileId() {
        Optional<String> id = MojangSkinProvider.parseProfileId(
                "{\"id\":\"0123456789abcdef0123456789abcdef\",\"name\":\"Alex\"}");

        assertEquals(Optional.of("0123456789abcdef0123456789abcdef"), id);
    }

    @Test
    void parsesProfileUuidFromTexturesValue() {
        String payload = "{\"profileId\":\"0123456789abcdef0123456789abcdef\","
                + "\"profileName\":\"Killer\",\"textures\":{}}";
        PlayerListEntry.ProfileProperty property = new PlayerListEntry.ProfileProperty(
                "textures",
                Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8)),
                "signature");

        assertEquals(Optional.of(UUID.fromString("01234567-89ab-cdef-0123-456789abcdef")),
                MojangSkinProvider.parseTexturesProfileUuid(property));
    }

    @Test
    void invalidTexturesProfileUuidReturnsEmpty() {
        PlayerListEntry.ProfileProperty property = new PlayerListEntry.ProfileProperty(
                "textures", "not-base64", null);

        assertEquals(Optional.empty(), MojangSkinProvider.parseTexturesProfileUuid(property));
    }
}
