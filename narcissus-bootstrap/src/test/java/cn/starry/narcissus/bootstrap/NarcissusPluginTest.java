package cn.starry.narcissus.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke-tests for bootstrap-side logic that does not require a live BungeeCord proxy.
 */
class NarcissusPluginTest {

    @Test
    void serverNameConstantIsCorrect() {
        assertEquals("narcissus-limbo", NarcissusPlugin.SERVER_NAME);
    }

    @Test
    void defaultsLobbyServer(@TempDir Path tempDir) throws Exception {
        Path config = tempDir.resolve("narcissus.yml");
        Files.writeString(config, "settings: {}\n");

        NarcissusConfig loaded = NarcissusConfig.load(config);

        assertEquals("lobby", loaded.getLobbyServer());
    }

    @Test
    void legacyLoginTargetServerStillResolvesTheLobby(@TempDir Path tempDir) throws Exception {
        Path config = tempDir.resolve("narcissus.yml");
        Files.writeString(config, "settings:\n  login_target_server: Login\n");

        NarcissusConfig loaded = NarcissusConfig.load(config);

        assertEquals("Login", loaded.getLobbyServer());
    }

    @Test
    void loadsAllowVersionListFromYaml(@TempDir Path tempDir) throws Exception {
        Path config = tempDir.resolve("narcissus.yml");
        Files.writeString(config, "settings:\n  allow-version:\n    - 47\n    - 774\n  lobby_server: survival\n");

        NarcissusConfig loaded = NarcissusConfig.load(config);

        assertEquals(Arrays.asList(47, 774), loaded.getAllowedVersions());
        assertEquals("survival", loaded.getLobbyServer());
    }
}
