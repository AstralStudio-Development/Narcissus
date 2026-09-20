package cn.starry.narcissus.functions.modern;

import cn.starry.narcissus.Narcissus;
import cn.starry.narcissus.functions.world.Chunk;
import cn.starry.narcissus.functions.world.World;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;

/**
 * Maps legacy MCEdit {@code id:meta} block references to the flattened global
 * block-state id of a target protocol version. Backed by a pre-serialized
 * 256*16 big-endian int fixture ({@code legacy_blockmap.bin}) generated offline
 * from the authoritative blocks.json report; index = legacyId*16 + meta.
 *
 * Unmapped combinations resolve to air (global id 0). Only the block
 * combinations present in the bundled schematic are populated.
 */
public final class LegacyBlockMap {

    /** Global block-state id of air; also the value for unmapped entries. */
    public static final int AIR = 0;

    private static final int TABLE_SIZE = 256 * 16;
    private static final int PANE_VARIANTS = 17; // glass pane + 16 stained colors
    private static final int PANE_MASKS = 16;
    private static final int PANE_TABLE_SIZE = PANE_VARIANTS * PANE_MASKS;

    private final int[] table;
    private final int[] paneStates;

    private LegacyBlockMap(int[] table, int[] paneStates) {
        this.table = table;
        this.paneStates = paneStates;
    }

    /** Global block-state id for a legacy {@code id:meta}, or air if unmapped. */
    public int toGlobalState(int legacyId, int meta) {
        if (legacyId < 0 || legacyId > 255) {
            return AIR;
        }
        return this.table[legacyId * 16 + (meta & 0xF)];
    }

    /** Resolves one block with neighbor-aware states for connected glass panes. */
    public int toGlobalState(World world, Chunk chunk, int localX, int y, int localZ) {
        int legacyId = chunk.getLegacyId(localX, y, localZ);
        int meta = chunk.getLegacyMeta(localX, y, localZ);
        int variant = paneVariant(legacyId, meta);
        if (variant >= 0) {
            int state = this.paneStates[variant * PANE_MASKS
                    + paneConnectionMask(world, chunk, localX, y, localZ)];
            if (state != AIR) {
                return state;
            }
        }
        return toGlobalState(legacyId, meta);
    }

    /** Returns east=1, north=2, south=4, west=8 connection bits for a pane. */
    public int paneConnectionMask(World world, Chunk chunk, int localX, int y, int localZ) {
        int worldX = (chunk.getX() << 4) | (localX & 0xF);
        int worldZ = (chunk.getZ() << 4) | (localZ & 0xF);
        int mask = 0;
        if (connectsToPane(world, worldX + 1, y, worldZ)) mask |= 1;
        if (connectsToPane(world, worldX, y, worldZ - 1)) mask |= 2;
        if (connectsToPane(world, worldX, y, worldZ + 1)) mask |= 4;
        if (connectsToPane(world, worldX - 1, y, worldZ)) mask |= 8;
        return mask;
    }

    public boolean isPaneLegacy(int legacyId) {
        return legacyId == 102 || legacyId == 160;
    }

    private int paneVariant(int legacyId, int meta) {
        if (legacyId == 102 && meta == 0) {
            return 0;
        }
        if (legacyId == 160 && meta >= 0 && meta < 16) {
            return meta + 1;
        }
        return -1;
    }

    private boolean connectsToPane(World world, int x, int y, int z) {
        int neighborId = world.getLegacyId(x, y, z);
        if (isPaneLegacy(neighborId)) {
            return true;
        }
        // These are the structural/opaque legacy blocks present in the bundled
        // schematic. Leaves, torches, carpets and air do not form pane joins.
        return switch (neighborId) {
            case 1, 2, 3, 5, 17, 53, 85, 118, 126, 134 -> true;
            default -> false;
        };
    }

    /**
     * Loads the map for a version directory (e.g. "v774"). Returns an all-air
     * map when the fixture is missing, so a version without one degrades to an
     * empty world rather than failing during startup.
     */
    public static LegacyBlockMap load(String versionDir) {
        int[] table = new int[TABLE_SIZE];
        int[] paneStates = loadPaneStates(versionDir);
        String path = "/registry/" + versionDir + "/legacy_blockmap.bin";
        try (InputStream in = LegacyBlockMap.class.getResourceAsStream(path)) {
            if (in == null) {
                Narcissus.LOGGER.warning("Legacy block map not found: " + path + " (modern chunks will be empty)");
                return new LegacyBlockMap(table, paneStates);
            }
            byte[] buf = new byte[TABLE_SIZE * 4];
            int off = 0;
            int n;
            while (off < buf.length && (n = in.read(buf, off, buf.length - off)) != -1) {
                off += n;
            }
            for (int i = 0; i < TABLE_SIZE; i++) {
                int b = i * 4;
                table[i] = ((buf[b] & 0xFF) << 24) | ((buf[b + 1] & 0xFF) << 16)
                        | ((buf[b + 2] & 0xFF) << 8) | (buf[b + 3] & 0xFF);
            }
        } catch (IOException ex) {
            Narcissus.LOGGER.log(Level.SEVERE, "Failed to load legacy block map " + path, ex);
        }
        return new LegacyBlockMap(table, paneStates);
    }

    private static int[] loadPaneStates(String versionDir) {
        int[] states = new int[PANE_TABLE_SIZE];
        String path = "/registry/" + versionDir + "/pane_states.bin";
        try (InputStream in = LegacyBlockMap.class.getResourceAsStream(path)) {
            if (in == null) {
                Narcissus.LOGGER.warning("Connected pane state map not found: " + path);
                return states;
            }
            byte[] buf = new byte[PANE_TABLE_SIZE * 4];
            int off = 0;
            int n;
            while (off < buf.length && (n = in.read(buf, off, buf.length - off)) != -1) {
                off += n;
            }
            if (off != buf.length) {
                Narcissus.LOGGER.warning("Connected pane state map is truncated: " + path);
                return new int[PANE_TABLE_SIZE];
            }
            for (int i = 0; i < PANE_TABLE_SIZE; i++) {
                int b = i * 4;
                states[i] = ((buf[b] & 0xFF) << 24) | ((buf[b + 1] & 0xFF) << 16)
                        | ((buf[b + 2] & 0xFF) << 8) | (buf[b + 3] & 0xFF);
            }
        } catch (IOException ex) {
            Narcissus.LOGGER.log(Level.WARNING, "Failed to load connected pane state map " + path, ex);
        }
        return states;
    }
}
