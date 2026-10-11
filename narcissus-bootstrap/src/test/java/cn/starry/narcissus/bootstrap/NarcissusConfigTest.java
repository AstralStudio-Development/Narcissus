package cn.starry.narcissus.bootstrap;

import cn.starry.narcissus.functions.config.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NarcissusConfigTest {
    @Test
    void missingAllowListUsesAllSupportedReleaseProtocols(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("narcissus.yml");
        Files.writeString(file, "settings: {}\n");
        assertEquals(List.of(47, 774, 775, 776, 777), NarcissusConfig.load(file).getAllowedVersions());
        assertEquals(Configuration.DEFAULT_ALLOWED_VERSIONS, NarcissusConfig.load(file).getAllowedVersions());
    }

    @Test
    void bundledAllowListMatchesCoreDefaults(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("narcissus.yml");
        try (var resource = Configuration.class.getResourceAsStream("/narcissus.yml")) {
            assertNotNull(resource);
            Files.copy(resource, file);
        }
        assertEquals(Configuration.DEFAULT_ALLOWED_VERSIONS, NarcissusConfig.load(file).getAllowedVersions());
    }

    @Test
    void existingExplicitAllowListRemainsRestricted(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("narcissus.yml");
        Files.writeString(file, "settings:\n  allow-version: [47, 774]\n");
        assertEquals(List.of(47, 774), NarcissusConfig.load(file).getAllowedVersions());
    }

    @Test
    void explicitlyEmptyAllowListDoesNotEnableNewVersions(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("narcissus.yml");
        Files.writeString(file, "settings:\n  allow-version: []\n");
        assertTrue(NarcissusConfig.load(file).getAllowedVersions().isEmpty());
    }
}
