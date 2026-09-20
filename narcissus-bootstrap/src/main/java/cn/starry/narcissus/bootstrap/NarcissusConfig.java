package cn.starry.narcissus.bootstrap;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Reads narcissus.yml from the plugin data folder.
 * Falls back to safe defaults for every field so onEnable never fails on a missing key.
 */
public class NarcissusConfig {

    private static final List<Integer> DEFAULT_ALLOWED_VERSIONS =
            Arrays.asList(47, 774, 776);

    private final int port;
    private final String schematicFile;
    private final byte dimension;
    private final byte gameMode;
    private final int maxSlots;
    private final double spawnX, spawnY, spawnZ;
    private final float spawnYaw, spawnPitch;
    private final List<Integer> allowedVersions;
    private final String loginTargetServer;
    private final String accountsFile;
    private final String staffAccountFile;

    private NarcissusConfig(int port, String schematicFile, byte dimension, byte gameMode,
                             int maxSlots, double spawnX, double spawnY, double spawnZ,
                             float spawnYaw, float spawnPitch, List<Integer> allowedVersions,
                             String loginTargetServer, String accountsFile, String staffAccountFile) {
        this.port = port;
        this.schematicFile = schematicFile;
        this.dimension = dimension;
        this.gameMode = gameMode;
        this.maxSlots = maxSlots;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.spawnZ = spawnZ;
        this.spawnYaw = spawnYaw;
        this.spawnPitch = spawnPitch;
        this.allowedVersions = List.copyOf(allowedVersions);
        this.loginTargetServer = loginTargetServer;
        this.accountsFile = accountsFile;
        this.staffAccountFile = staffAccountFile;
    }

    public static NarcissusConfig load(Path yamlFile) throws Exception {
        Yaml yaml = new Yaml();
        try (InputStream in = new FileInputStream(yamlFile.toFile())) {
            Map<?, ?> root = yaml.load(in);
            Map<?, ?> settings = root != null && root.get("settings") instanceof Map<?,?> s ? s : Map.of();
            int port = getInt(settings, "port", 65530);
            String schematicFile = getString(settings, "schematic_file", "world.schematic");
            byte dimension = (byte) getInt(settings, "dimension", 1);
            byte gameMode = (byte) getInt(settings, "gamemode", 2);
            int maxSlots = getInt(settings, "max_slots", -1);
            List<Integer> allowedVersions = getVersionList(settings, "allow-version", DEFAULT_ALLOWED_VERSIONS);
            Map<?, ?> spawn = settings.get("spawn") instanceof Map<?,?> sp ? sp : Map.of();
            double spawnX = getDouble(spawn, "x", 0.5);
            double spawnY = getDouble(spawn, "y", 64.0);
            double spawnZ = getDouble(spawn, "z", 0.5);
            float spawnYaw = (float) getDouble(spawn, "yaw", 0.0);
            float spawnPitch = (float) getDouble(spawn, "pitch", 0.0);
            String loginTargetServer = getString(settings, "login_target_server", "Login");
            String accountsFile = getString(settings, "accounts_file", "accounts.yml");
            String staffAccountFile = getString(settings, "staff_account_file", "staffAccount.yml");
            return new NarcissusConfig(port, schematicFile, dimension, gameMode, maxSlots,
                    spawnX, spawnY, spawnZ, spawnYaw, spawnPitch, allowedVersions,
                    loginTargetServer, accountsFile, staffAccountFile);
        }
    }

    private static List<Integer> getVersionList(Map<?, ?> map, String key, List<Integer> def) {
        Object value = map.get(key);
        if (value == null)
            return new ArrayList<>(def);

        List<Integer> versions = new ArrayList<>();
        if (value instanceof Iterable<?> iterable)
        {
            for (Object item : iterable)
                versions.add(parseVersion(item, key));
        }
        else if (value instanceof Number || value instanceof String)
        {
            String text = String.valueOf(value);
            for (String token : text.split(","))
                versions.add(parseVersion(token.trim(), key));
        }
        else
        {
            throw new IllegalArgumentException(key + " must be a list of protocol numbers");
        }
        return versions;
    }

    private static int parseVersion(Object value, String key) {
        if (value instanceof Number number)
            return number.intValue();
        if (value instanceof String text)
        {
            try
            {
                return Integer.parseInt(text.trim());
            }
            catch (NumberFormatException ex)
            {
                throw new IllegalArgumentException(key + " contains invalid protocol number: " + text, ex);
            }
        }
        throw new IllegalArgumentException(key + " contains a non-number value: " + value);
    }

    private static int getInt(Map<?,?> map, String key, int def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        return def;
    }

    private static String getString(Map<?,?> map, String key, String def) {
        Object v = map.get(key);
        if (v instanceof String s) return s;
        return def;
    }

    private static double getDouble(Map<?,?> map, String key, double def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.doubleValue();
        return def;
    }

    public int getPort() { return port; }
    public String getSchematicFile() { return schematicFile; }
    public byte getDimension() { return dimension; }
    public byte getGameMode() { return gameMode; }
    public int getMaxSlots() { return maxSlots; }
    public double getSpawnX() { return spawnX; }
    public double getSpawnY() { return spawnY; }
    public double getSpawnZ() { return spawnZ; }
    public float getSpawnYaw() { return spawnYaw; }
    public float getSpawnPitch() { return spawnPitch; }
    public List<Integer> getAllowedVersions() { return allowedVersions; }
    public String getLoginTargetServer() { return loginTargetServer; }
    public String getAccountsFile() { return accountsFile; }
    public String getStaffAccountFile() { return staffAccountFile; }
}
