package cn.starry.narcissus.functions.modern;

import cn.starry.narcissus.Narcissus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/** Resolves modern block-entity resource locations to protocol registry ids. */
public final class BlockEntityTypeMap
{
    private final Map<String, Integer> types;
    private final boolean bedEntityRemoved;

    private BlockEntityTypeMap(Map<String, Integer> types, String versionDir)
    {
        this.types = Collections.unmodifiableMap(types);
        this.bedEntityRemoved = "v776".equals(versionDir) || "v777".equals(versionDir);
    }

    public static BlockEntityTypeMap load(String versionDir)
    {
        Map<String, Integer> types = new HashMap<>();
        String path = "/registry/" + versionDir + "/block_entity_types.txt";
        try (InputStream input = BlockEntityTypeMap.class.getResourceAsStream(path))
        {
            if (input == null)
            {
                Narcissus.LOGGER.warning("Block-entity type map not found: " + path);
                return new BlockEntityTypeMap(types, versionDir);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#"))
                        continue;
                    int separator = line.lastIndexOf('\t');
                    if (separator <= 0 || separator == line.length() - 1)
                        continue;
                    try
                    {
                        types.put(normalize(line.substring(0, separator)),
                                Integer.parseInt(line.substring(separator + 1)));
                    }
                    catch (NumberFormatException ex)
                    {
                        Narcissus.LOGGER.warning("Ignoring malformed block-entity type line in " + path + ": " + line);
                    }
                }
            }
        }
        catch (IOException ex)
        {
            Narcissus.LOGGER.log(Level.WARNING, "Failed to load block-entity type map " + path, ex);
        }
        return new BlockEntityTypeMap(types, versionDir);
    }

    public int resolve(String id)
    {
        String normalized = normalize(id);
        Integer exact = this.types.get(normalized);
        if (exact != null)
            return exact;
        if (normalized.endsWith("_head"))
        {
            Integer skull = this.types.get("minecraft:skull");
            if (skull != null)
                return skull;
        }
        return -1;
    }

    /**
     * Legacy schematic NBT that is intentionally omitted for this protocol.
     * Official 26.2+ BedBlock no longer implements EntityBlock: the colored bed
     * block state is sufficient, and old type 25 now denotes a conduit.
     * Keep resolve() at -1 rather than assigning an invalid wire type.
     */
    public boolean isKnownRemoved(String id)
    {
        return this.bedEntityRemoved && "minecraft:bed".equals(normalize(id));
    }

    public int size()
    {
        return this.types.size();
    }

    private static String normalize(String id)
    {
        if (id == null || id.trim().isEmpty())
            return "minecraft:unknown";
        String normalized = id.trim();
        return normalized.indexOf(':') < 0 ? "minecraft:" + normalized : normalized;
    }
}
