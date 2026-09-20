package cn.starry.narcissus;

import cn.starry.narcissus.functions.config.Configuration;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Configuration builder API.
 */
class ConfigurationTest {

    @Test
    void builder_createsConfigWithCorrectPort() {
        Configuration cfg = Configuration.builder()
                .ip("127.0.0.1")
                .port(65530)
                .build();
        assertNotNull(cfg);
        assertEquals(65530, cfg.getPort());
        assertEquals("127.0.0.1", cfg.getIp());
    }

    @Test
    void builder_invalidPortThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                Configuration.builder().port(0).build());
        assertThrows(IllegalArgumentException.class, () ->
                Configuration.builder().port(99999).build());
    }

    @Test
    void builder_defaultPortIs65530() {
        Configuration cfg = Configuration.builder().build();
        assertEquals(Configuration.DEFAULT_PORT, cfg.getPort());
        assertEquals(Arrays.asList(47, 774, 776), cfg.getAllowedVersions());
    }

    @Test
    void allowVersionListIsDeduplicatedAndCopied() {
        Configuration cfg = Configuration.builder()
                .allowedVersions(Arrays.asList(47, 774, 47))
                .build();

        assertEquals(Arrays.asList(47, 774), cfg.getAllowedVersions());
        assertTrue(cfg.isVersionAllowed(47));
        assertFalse(cfg.isVersionAllowed(776));
        assertEquals(cfg.getAllowedVersions(), cfg.toBuilder().build().getAllowedVersions());
    }
}
