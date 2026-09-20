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
import java.util.TreeMap;
import java.util.logging.Level;

/** Resolves canonical Sponge block-state strings to a protocol global state id. */
public final class ModernBlockStateMap
{
    public static final int AIR = 0;

    private final Map<String, Integer> states;
    private final Map<String, Map<String, String>> defaults;
    private final String versionDir;

    private ModernBlockStateMap(String versionDir, Map<String, Integer> states,
                                Map<String, Map<String, String>> defaults)
    {
        this.versionDir = versionDir;
        this.states = Collections.unmodifiableMap(states);
        this.defaults = Collections.unmodifiableMap(defaults);
    }

    public static ModernBlockStateMap load(String versionDir)
    {
        Map<String, Integer> states = new HashMap<>();
        Map<String, Map<String, String>> defaults = new HashMap<>();
        String path = "/registry/" + versionDir + "/blockstates.txt";
        loadStates(path, states);
        loadDefaults("/registry/" + versionDir + "/blockstate_defaults.txt", defaults);
        return new ModernBlockStateMap(versionDir, states, defaults);
    }

    private static void loadStates(String path, Map<String, Integer> states)
    {
        try (InputStream input = ModernBlockStateMap.class.getResourceAsStream(path))
        {
            if (input == null)
            {
                Narcissus.LOGGER.warning("Modern block-state map not found: " + path);
                return;
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
                    {
                        Narcissus.LOGGER.warning("Ignoring malformed block-state map line in " + path);
                        continue;
                    }
                    String state = canonicalize(line.substring(0, separator));
                    try
                    {
                        states.put(state, Integer.parseInt(line.substring(separator + 1)));
                    }
                    catch (NumberFormatException ex)
                    {
                        Narcissus.LOGGER.warning("Ignoring malformed block-state id in " + path + ": " + line);
                    }
                }
            }
        }
        catch (IOException ex)
        {
            Narcissus.LOGGER.log(Level.WARNING, "Failed to load modern block-state map " + path, ex);
        }
    }

    private static void loadDefaults(String path, Map<String, Map<String, String>> defaults)
    {
        try (InputStream input = ModernBlockStateMap.class.getResourceAsStream(path))
        {
            if (input == null)
            {
                Narcissus.LOGGER.warning("Modern block-state defaults not found: " + path);
                return;
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
                    int separator = line.indexOf('\t');
                    String name = separator < 0 ? line : line.substring(0, separator);
                    String body = separator < 0 ? "" : line.substring(separator + 1);
                    Map<String, String> properties = new TreeMap<>();
                    if (!body.isEmpty())
                    {
                        for (String part : body.split(","))
                        {
                            int equals = part.indexOf('=');
                            if (equals > 0 && equals < part.length() - 1)
                                properties.put(part.substring(0, equals), part.substring(equals + 1));
                        }
                    }
                    defaults.put(normalizeName(name), properties);
                }
            }
        }
        catch (IOException ex)
        {
            Narcissus.LOGGER.log(Level.WARNING, "Failed to load modern block-state defaults " + path, ex);
        }
    }

    public int resolve(String state)
    {
        String canonical = canonicalize(state);
        Integer id = this.states.get(canonical);
        if (id != null)
            return id;

        StateParts parts = split(canonical);
        Map<String, String> defaultProperties = this.defaults.get(parts.name);
        if (defaultProperties != null && !defaultProperties.isEmpty())
        {
            Map<String, String> merged = new TreeMap<>(defaultProperties);
            merged.putAll(parts.properties);
            id = this.states.get(format(parts.name, merged));
            if (id != null)
                return id;
        }
        return AIR;
    }

    public boolean contains(String state)
    {
        return this.resolve(state) != AIR || "minecraft:air".equals(canonicalize(state));
    }

    public int size()
    {
        return this.states.size();
    }

    public String getVersionDir()
    {
        return this.versionDir;
    }

    /** Canonicalizes a Sponge palette state without depending on property order. */
    public static String canonicalize(String state)
    {
        if (state == null)
            return "minecraft:air";
        state = state.trim();
        if (state.isEmpty())
            return "minecraft:air";

        int bracket = state.indexOf('[');
        if (bracket < 0)
            return normalizeName(state);
        int close = state.lastIndexOf(']');
        if (close < bracket || close != state.length() - 1)
            return normalizeName(state);

        String name = normalizeName(state.substring(0, bracket));
        String body = state.substring(bracket + 1, close).trim();
        if (body.isEmpty())
            return name;

        Map<String, String> properties = new TreeMap<>();
        for (String part : body.split(","))
        {
            int equals = part.indexOf('=');
            if (equals <= 0 || equals == part.length() - 1)
                return state;
            String key = part.substring(0, equals).trim();
            String value = part.substring(equals + 1).trim();
            properties.put(key, value);
        }
        return format(name, properties);
    }

    private static String format(String name, Map<String, String> properties)
    {
        if (properties.isEmpty())
            return name;
        StringBuilder canonical = new StringBuilder(name).append('[');
        boolean first = true;
        for (Map.Entry<String, String> entry : properties.entrySet())
        {
            if (!first)
                canonical.append(',');
            first = false;
            canonical.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return canonical.append(']').toString();
    }

    private static StateParts split(String canonical)
    {
        int bracket = canonical.indexOf('[');
        if (bracket < 0)
            return new StateParts(canonical, new TreeMap<>());
        String name = canonical.substring(0, bracket);
        String body = canonical.substring(bracket + 1, canonical.length() - 1);
        Map<String, String> properties = new TreeMap<>();
        if (!body.isEmpty())
        {
            for (String part : body.split(","))
            {
                int equals = part.indexOf('=');
                if (equals > 0 && equals < part.length() - 1)
                    properties.put(part.substring(0, equals), part.substring(equals + 1));
            }
        }
        return new StateParts(name, properties);
    }

    private static String normalizeName(String name)
    {
        name = name.trim();
        return name.indexOf(':') < 0 ? "minecraft:" + name : name;
    }

    private static final class StateParts
    {
        private final String name;
        private final Map<String, String> properties;

        private StateParts(String name, Map<String, String> properties)
        {
            this.name = name;
            this.properties = properties;
        }
    }
}
