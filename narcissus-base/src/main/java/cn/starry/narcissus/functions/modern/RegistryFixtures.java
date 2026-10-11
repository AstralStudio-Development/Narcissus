package cn.starry.narcissus.functions.modern;

import cn.starry.narcissus.Narcissus;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Loads pre-serialized {@code registry_data} packet bodies bundled as resources
 * under {@code /registry/<version>/}. Each {@code .bin} file is the exact packet
 * body after the packet id, generated offline from the authoritative
 * minecraft-data codec for that protocol version. This avoids implementing a
 * runtime NBT encoder for the large vanilla registry set while remaining
 * byte-exact.
 *
 * The {@code manifest.txt} lists, in send order, lines of:
 * {@code <registryId>\t<file>\t<entryCount>\t<byteLen>}.
 */
public final class RegistryFixtures {

    private final List<byte[]> registryPackets;
    private final byte[] updateTagsBody;

    private RegistryFixtures(List<byte[]> registryPackets, byte[] updateTagsBody) {
        this.registryPackets = registryPackets;
        this.updateTagsBody = updateTagsBody;
    }

    /** Ordered registry_data bodies (each is one registry). */
    public List<byte[]> getRegistryBodies() {
        return this.registryPackets;
    }

    /** Non-null pre-serialized {@code update_tags} body, validated when fixtures load. */
    public byte[] getUpdateTagsBody() {
        return this.updateTagsBody;
    }

    /**
     * Loads a complete fixture set for a version directory (e.g. "v774").
     * Missing mandatory resources fail during protocol initialization, before
     * a client can enter configuration with partial registries or null tags.
     */
    public static RegistryFixtures load(String versionDir) {
        List<byte[]> bodies = new ArrayList<>();
        String base = "/registry/" + versionDir + "/";
        String manifestPath = base + "manifest.txt";

        byte[] updateTags = readRequiredResource(base + "update_tags.bin");

        try (InputStream manifestStream = RegistryFixtures.class.getResourceAsStream(manifestPath)) {
            if (manifestStream == null) {
                throw new IllegalStateException("Registry manifest not found: " + manifestPath);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(manifestStream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\t");
                if (parts.length < 2) continue;
                String file = parts[1];
                bodies.add(readRequiredResource(base + file));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load registry fixtures for " + versionDir, ex);
        }

        return new RegistryFixtures(bodies, updateTags);
    }

    private static byte[] readRequiredResource(String path) {
        byte[] data = readResource(path);
        if (data == null || data.length == 0) {
            throw new IllegalStateException("Missing or empty required registry fixture: " + path);
        }
        return data;
    }

    private static byte[] readResource(String path) {
        try (InputStream in = RegistryFixtures.class.getResourceAsStream(path)) {
            if (in == null) {
                Narcissus.LOGGER.warning("Registry fixture not found: " + path);
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (IOException ex) {
            Narcissus.LOGGER.log(Level.SEVERE, "Failed to read registry fixture " + path, ex);
            return null;
        }
    }
}
