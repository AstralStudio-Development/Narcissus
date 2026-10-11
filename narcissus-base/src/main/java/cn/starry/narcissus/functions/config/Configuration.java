package cn.starry.narcissus.functions.config;

import cn.starry.narcissus.Narcissus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.InvalidPropertiesFormatException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;

/**
 * Created by Rigner on 30/08/16, Update by Stalyer for project Limbo.
 * All rights reserved.
 *
 * Reworked into an immutable value object that the plugin layer builds explicitly
 * (via {@link Builder}). Ports are ints validated to 1..65535 with a default of
 * {@link #DEFAULT_PORT}. A {@link #load(Path)} helper preserves the legacy
 * server.properties path for standalone use, but the running server no longer
 * depends on the process working directory.
 */
public final class Configuration
{
    public static final int DEFAULT_PORT = 65530;
    public static final List<Integer> DEFAULT_ALLOWED_VERSIONS = Collections.unmodifiableList(
            Arrays.asList(47, 774, 775, 776, 777));

    private final int maxSlots;
    private final int port;
    private final String ip;
    private final String schematicFile;
    private final byte dimension;
    private final byte gameMode;
    private final double spawnX;
    private final double spawnY;
    private final double spawnZ;
    private final float spawnYaw;
    private final float spawnPitch;
    private final boolean reducedDebugInfo;
    private final String motd;
    private final List<Integer> allowedVersions;

    private Configuration(Builder builder)
    {
        this.maxSlots = builder.maxSlots;
        this.port = builder.port;
        this.ip = builder.ip;
        this.schematicFile = builder.schematicFile;
        this.dimension = builder.dimension;
        this.gameMode = builder.gameMode;
        this.spawnX = builder.spawnX;
        this.spawnY = builder.spawnY;
        this.spawnZ = builder.spawnZ;
        this.spawnYaw = builder.spawnYaw;
        this.spawnPitch = builder.spawnPitch;
        this.reducedDebugInfo = builder.reducedDebugInfo;
        this.motd = builder.motd;
        this.allowedVersions = Collections.unmodifiableList(new ArrayList<>(builder.allowedVersions));
    }

    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Legacy loader kept for standalone execution. Reads {@code server.properties}
     * from the given directory (creating it from the bundled resource if missing).
     * Returns {@code null} on failure, matching the historical behaviour.
     */
    public static Configuration load(Path directory)
    {
        try
        {
            Path file = directory.resolve("server.properties");
            if (!Files.exists(file))
            {
                try (InputStream bundled = Narcissus.class.getResourceAsStream("/server.properties"))
                {
                    if (bundled != null)
                    {
                        Files.createDirectories(directory);
                        Files.copy(bundled, file);
                    }
                }
            }
            Properties properties = new Properties();
            try (InputStream in = Files.newInputStream(file))
            {
                properties.load(in);
            }

            Builder builder = builder();
            builder.maxSlots(getInt(properties, "maxSlots", -1));
            builder.port(getInt(properties, "server-port", DEFAULT_PORT));
            builder.ip(getString(properties, "server-ip", "127.0.0.1"));
            builder.schematicFile(getString(properties, "schematic-file", "world.schematic"));
            builder.dimension((byte) getInt(properties, "dimension", 1));
            builder.gameMode((byte) getInt(properties, "gamemode", 2));
            builder.motd(getString(properties, "motd", "Limbo"));
            builder.allowedVersions(getVersionList(properties, "allow-version", DEFAULT_ALLOWED_VERSIONS));

            String[] split = getString(properties, "spawn", "0.5;64;0.5;0;0").split(";");
            try
            {
                builder.spawn(
                        Double.parseDouble(split[0]),
                        Double.parseDouble(split[1]),
                        Double.parseDouble(split[2]),
                        Float.parseFloat(split[3]),
                        Float.parseFloat(split[4]));
            }
            catch (NumberFormatException | IndexOutOfBoundsException ex)
            {
                throw new InvalidPropertiesFormatException("Invalid coordinate format");
            }
            return builder.build();
        }
        catch (Exception ex)
        {
            Narcissus.LOGGER.log(Level.SEVERE, ex.getMessage(), ex);
        }
        return null;
    }

    private static int getInt(Properties properties, String key, int def) throws InvalidPropertiesFormatException
    {
        String result = properties.getProperty(key);
        if (result == null)
            return def;
        try
        {
            return Integer.parseInt(result.trim());
        }
        catch (NumberFormatException ex)
        {
            throw new InvalidPropertiesFormatException(key + " is not a number property");
        }
    }

    private static List<Integer> getVersionList(Properties properties, String key, List<Integer> def)
            throws InvalidPropertiesFormatException
    {
        String result = properties.getProperty(key);
        if (result == null)
            return def;

        List<Integer> versions = new ArrayList<>();
        for (String token : result.split(","))
        {
            String value = token.trim();
            if (value.isEmpty())
                throw new InvalidPropertiesFormatException(key + " contains an empty version");
            try
            {
                versions.add(Integer.parseInt(value));
            }
            catch (NumberFormatException ex)
            {
                throw new InvalidPropertiesFormatException(key + " contains a non-number version: " + value);
            }
        }
        return versions;
    }

    private static String getString(Properties properties, String key, String def)
    {
        String result = properties.getProperty(key);
        return result == null ? def : result.trim();
    }

    public int getMaxSlots()
    {
        return this.maxSlots;
    }

    public int getPort()
    {
        return this.port;
    }

    /** Returns a Builder pre-populated with this configuration's values. */
    public Builder toBuilder()
    {
        return builder()
                .maxSlots(this.maxSlots)
                .port(this.port)
                .ip(this.ip)
                .schematicFile(this.schematicFile)
                .dimension(this.dimension)
                .gameMode(this.gameMode)
                .spawn(this.spawnX, this.spawnY, this.spawnZ, this.spawnYaw, this.spawnPitch)
                .reducedDebugInfo(this.reducedDebugInfo)
                .motd(this.motd)
                .allowedVersions(this.allowedVersions);
    }

    public String getIp()
    {
        return this.ip;
    }

    public String getSchematicFile()
    {
        return this.schematicFile;
    }

    public byte getDimension()
    {
        return this.dimension;
    }

    public byte getGameMode()
    {
        return this.gameMode;
    }

    public double getSpawnX()
    {
        return this.spawnX;
    }

    public double getSpawnY()
    {
        return this.spawnY;
    }

    public double getSpawnZ()
    {
        return this.spawnZ;
    }

    public float getSpawnYaw()
    {
        return this.spawnYaw;
    }

    public float getSpawnPitch()
    {
        return this.spawnPitch;
    }

    public boolean isReducedDebugInfo()
    {
        return this.reducedDebugInfo;
    }

    public String getMotd()
    {
        return this.motd;
    }

    public List<Integer> getAllowedVersions()
    {
        return this.allowedVersions;
    }

    public boolean isVersionAllowed(int version)
    {
        return this.allowedVersions.contains(version);
    }

    public static final class Builder
    {
        private int maxSlots = -1;
        private int port = DEFAULT_PORT;
        private String ip = "127.0.0.1";
        private String schematicFile = "world.schematic";
        private byte dimension = 1;
        private byte gameMode = 2;
        private double spawnX = 0.5D;
        private double spawnY = 64.0D;
        private double spawnZ = 0.5D;
        private float spawnYaw = 0.0F;
        private float spawnPitch = 0.0F;
        private boolean reducedDebugInfo = false;
        private String motd = "Limbo";
        private List<Integer> allowedVersions = new ArrayList<>(DEFAULT_ALLOWED_VERSIONS);

        public Builder maxSlots(int maxSlots)
        {
            this.maxSlots = maxSlots;
            return this;
        }

        public Builder port(int port)
        {
            this.port = port;
            return this;
        }

        public Builder ip(String ip)
        {
            if (ip != null && !ip.isEmpty())
                this.ip = ip;
            return this;
        }

        public Builder schematicFile(String schematicFile)
        {
            if (schematicFile != null && !schematicFile.isEmpty())
                this.schematicFile = schematicFile;
            return this;
        }

        public Builder dimension(byte dimension)
        {
            this.dimension = dimension;
            return this;
        }

        public Builder gameMode(byte gameMode)
        {
            this.gameMode = gameMode;
            return this;
        }

        public Builder spawn(double x, double y, double z, float yaw, float pitch)
        {
            this.spawnX = x;
            this.spawnY = y;
            this.spawnZ = z;
            this.spawnYaw = yaw;
            this.spawnPitch = pitch;
            return this;
        }

        public Builder reducedDebugInfo(boolean reducedDebugInfo)
        {
            this.reducedDebugInfo = reducedDebugInfo;
            return this;
        }

        public Builder motd(String motd)
        {
            if (motd != null)
                this.motd = motd;
            return this;
        }

        public Builder allowedVersions(List<Integer> allowedVersions)
        {
            if (allowedVersions == null)
            {
                this.allowedVersions = new ArrayList<>();
                return this;
            }

            Set<Integer> unique = new LinkedHashSet<>();
            for (Integer version : allowedVersions)
            {
                if (version == null)
                    throw new IllegalArgumentException("allowedVersions must not contain null");
                unique.add(version);
            }
            this.allowedVersions = new ArrayList<>(unique);
            return this;
        }

        public Configuration build()
        {
            if (this.port < 1 || this.port > 65535)
                throw new IllegalArgumentException("port must be between 1 and 65535, got " + this.port);
            if (this.ip == null || this.ip.isEmpty())
                throw new IllegalArgumentException("ip must not be empty");
            if (this.schematicFile == null || this.schematicFile.isEmpty())
                throw new IllegalArgumentException("schematicFile must not be empty");
            return new Configuration(this);
        }
    }
}
