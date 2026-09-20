package cn.starry.narcissus.functions.world;

import cn.starry.narcissus.functions.modern.ModernBlockStateMap;
import cn.starry.narcissus.functions.world.nbt.ByteArrayTag;
import cn.starry.narcissus.functions.world.nbt.CompoundTag;
import cn.starry.narcissus.functions.world.nbt.IntArrayTag;
import cn.starry.narcissus.functions.world.nbt.IntTag;
import cn.starry.narcissus.functions.world.nbt.ListTag;
import cn.starry.narcissus.functions.world.nbt.NamedTag;
import cn.starry.narcissus.functions.world.nbt.NBTTag;
import cn.starry.narcissus.functions.world.nbt.ShortTag;
import cn.starry.narcissus.functions.world.nbt.StringTag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loads legacy MCEdit schematics and Sponge schematic v2/v3 files. */
public final class Schematic
{
    private Schematic()
    {
    }

    /** Loads the schematic at its historical origin (0, 0, 0). */
    public static World toWorld(CompoundTag nbt)
    {
        return toWorld(nbt, 0, 0, 0);
    }

    /** Loads the schematic with a horizontal offset and the original Y level. */
    public static World toWorld(CompoundTag nbt, int offsetX, int offsetZ)
    {
        return toWorld(nbt, offsetX, 0, offsetZ);
    }

    /** Loads the schematic with an absolute X/Y/Z block offset. */
    public static World toWorld(CompoundTag nbt, int offsetX, int offsetY, int offsetZ)
    {
        return parse(nbt).toWorld(offsetX, offsetY, offsetZ);
    }

    /** Parses once, centers non-air bounds on the supplied fixed anchor, and loads the world. */
    public static Placement centered(CompoundTag nbt, double targetX, double targetY, double targetZ)
    {
        ParsedSchematic schematic = parse(nbt);
        int[] bounds = schematic.bounds;
        int offsetX = (int) Math.round(targetX - (bounds[0] + bounds[1]) / 2.0D);
        int offsetY = (int) Math.round(targetY - (bounds[2] + bounds[3]) / 2.0D);
        int offsetZ = (int) Math.round(targetZ - (bounds[4] + bounds[5]) / 2.0D);
        return new Placement(schematic.toWorld(offsetX, offsetY, offsetZ), offsetX, offsetY, offsetZ,
                schematic.modern, schematic.version, schematic.width, schematic.height, schematic.length);
    }

    /** Returns the X offset that puts the non-air schematic bounds over target X. */
    public static int centerOffsetX(CompoundTag nbt, double targetX)
    {
        int[] bounds = parse(nbt).bounds;
        return (int) Math.round(targetX - (bounds[0] + bounds[1]) / 2.0D);
    }

    /** Returns the Y offset that puts the non-air schematic bounds over target Y. */
    public static int centerOffsetY(CompoundTag nbt, double targetY)
    {
        int[] bounds = parse(nbt).bounds;
        return (int) Math.round(targetY - (bounds[2] + bounds[3]) / 2.0D);
    }

    /** Returns the Z offset that puts the non-air schematic bounds over target Z. */
    public static int centerOffsetZ(CompoundTag nbt, double targetZ)
    {
        int[] bounds = parse(nbt).bounds;
        return (int) Math.round(targetZ - (bounds[4] + bounds[5]) / 2.0D);
    }

    private static ParsedSchematic parse(CompoundTag nbt)
    {
        if (nbt == null)
            throw new IllegalArgumentException("Schematic NBT cannot be null");

        // Sponge v3 stores the actual schematic in an outer root compound under
        // the name "Schematic". Older WorldEdit/Sponge files may use that
        // compound itself as the root, so accept both layouts.
        if (nbt.get("Width") == null && nbt.get("Schematic") instanceof CompoundTag)
            nbt = (CompoundTag) nbt.get("Schematic");

        int width = requiredInt(nbt, "Width");
        int length = requiredInt(nbt, "Length");
        int height = requiredInt(nbt, "Height");
        validateDimensions(width, height, length);

        NBTTag blockTag = nbt.get("Blocks");
        if (blockTag instanceof ByteArrayTag)
            return parseLegacy(nbt, width, height, length, (ByteArrayTag) blockTag);
        if (blockTag instanceof CompoundTag)
            return parseSpongeV3(nbt, width, height, length, (CompoundTag) blockTag);
        if (nbt.get("Palette") instanceof CompoundTag && nbt.get("BlockData") instanceof ByteArrayTag)
            return parseSpongeV2(nbt, width, height, length);

        throw new IllegalArgumentException("Unsupported schematic layout: expected legacy Blocks byte array or Sponge Palette/BlockData");
    }

    private static ParsedSchematic parseLegacy(CompoundTag nbt, int width, int height, int length,
                                                ByteArrayTag blocksTag)
    {
        NBTTag dataTag = nbt.get("Data");
        if (!(dataTag instanceof ByteArrayTag))
            throw new IllegalArgumentException("Legacy schematic is missing byte-array Data");
        byte[] blocks = blocksTag.getBytes();
        byte[] data = ((ByteArrayTag) dataTag).getBytes();
        int volume = volume(width, height, length);
        if (blocks.length != volume || data.length != volume)
            throw new IllegalArgumentException("Legacy schematic data length mismatch: expected " + volume
                    + ", blocks=" + blocks.length + ", data=" + data.length);

        int[] bounds = emptyBounds(width, height, length);
        for (int index = 0; index < volume; index++)
        {
            if ((blocks[index] & 0xFF) == 0)
                continue;
            int[] position = positionForIndex(width, length, index);
            include(bounds, position[0], position[1], position[2]);
        }
        List<CompoundTag> legacyEntities = compoundList(nbt.get("TileEntities"));
        return ParsedSchematic.legacy(width, height, length, blocks, data, bounds, legacyEntities,
                schematicVersion(nbt, 1));
    }

    private static ParsedSchematic parseSpongeV2(CompoundTag nbt, int width, int height, int length)
    {
        CompoundTag palette = (CompoundTag) nbt.get("Palette");
        ByteArrayTag data = (ByteArrayTag) nbt.get("BlockData");
        return parseModern(width, height, length, palette, data,
                compoundList(nbt.get("BlockEntities")), schematicVersion(nbt, 2));
    }

    private static ParsedSchematic parseSpongeV3(CompoundTag nbt, int width, int height, int length,
                                                  CompoundTag blocks)
    {
        int version = schematicVersion(nbt, 3);
        if (version != 3)
            throw new IllegalArgumentException("Nested Blocks schematic must use Sponge version 3, got " + version);
        NBTTag paletteTag = blocks.get("Palette");
        NBTTag dataTag = blocks.get("Data");
        if (!(paletteTag instanceof CompoundTag) || !(dataTag instanceof ByteArrayTag))
            throw new IllegalArgumentException("Sponge v3 Blocks must contain Palette compound and Data byte array");
        return parseModern(width, height, length, (CompoundTag) paletteTag, (ByteArrayTag) dataTag,
                compoundList(blocks.get("BlockEntities")), version);
    }

    private static ParsedSchematic parseModern(int width, int height, int length, CompoundTag palette,
                                               ByteArrayTag dataTag, List<CompoundTag> entityTags, int version)
    {
        ModernBlockPalette modernPalette = new ModernBlockPalette();
        Map<Integer, Integer> localToStateKey = new HashMap<>();
        for (NamedTag entry : palette.getTags())
        {
            if (!(entry instanceof IntTag))
                throw new IllegalArgumentException("Sponge palette entry is not an int: " + entry.getName());
            int localIndex = ((IntTag) entry).getValue();
            if (localIndex < 0)
                throw new IllegalArgumentException("Negative Sponge palette index: " + localIndex);
            localToStateKey.put(localIndex, modernPalette.indexOf(entry.getName()));
        }
        if (localToStateKey.isEmpty())
            throw new IllegalArgumentException("Sponge schematic palette is empty");

        byte[] encoded = dataTag.getBytes();
        int volume = volume(width, height, length);
        int[] stateKeys = new int[volume];
        int[] bounds = emptyBounds(width, height, length);
        Cursor cursor = new Cursor(encoded);
        for (int index = 0; index < volume; index++)
        {
            int localIndex = cursor.readVarInt();
            Integer stateKey = localToStateKey.get(localIndex);
            if (stateKey == null)
                throw new IllegalArgumentException("Sponge block data references missing palette index " + localIndex);
            stateKeys[index] = stateKey;
            if (stateKey != 0)
            {
                int[] position = positionForIndex(width, length, index);
                include(bounds, position[0], position[1], position[2]);
            }
        }
        if (!cursor.isAtEnd())
            throw new IllegalArgumentException("Sponge block data contains trailing bytes after " + volume + " entries");

        List<ModernBlockEntitySpec> entities = new ArrayList<>();
        for (CompoundTag entity : entityTags)
            entities.add(parseModernBlockEntity(entity, version >= 3, width, height, length));
        return ParsedSchematic.modern(width, height, length, modernPalette, stateKeys, bounds, entities, version);
    }

    private static ModernBlockEntitySpec parseModernBlockEntity(CompoundTag entity, boolean nestedData,
                                                                  int width, int height, int length)
    {
        NBTTag idTag = entity.get("Id");
        NBTTag posTag = entity.get("Pos");
        if (!(idTag instanceof StringTag) || !(posTag instanceof IntArrayTag))
            throw new IllegalArgumentException("Modern BlockEntity requires string Id and int-array Pos");
        int[] position = ((IntArrayTag) posTag).getValues();
        if (position.length != 3)
            throw new IllegalArgumentException("Modern BlockEntity Pos must contain exactly three integers");
        if (position[0] < 0 || position[0] >= width
                || position[1] < 0 || position[1] >= height
                || position[2] < 0 || position[2] >= length)
            throw new IllegalArgumentException("Modern BlockEntity Pos lies outside schematic bounds: "
                    + position[0] + "," + position[1] + "," + position[2]);

        String id = ((StringTag) idTag).getValue();
        CompoundTag payload = new CompoundTag();
        if (nestedData)
        {
            NBTTag dataTag = entity.get("Data");
            if (dataTag != null && !(dataTag instanceof CompoundTag))
                throw new IllegalArgumentException("Modern v3 BlockEntity Data must be a compound");
            if (dataTag instanceof CompoundTag)
            {
                for (NamedTag child : ((CompoundTag) dataTag).getTags())
                    payload.put(child.getName(), child);
            }
        }
        else
        {
            for (NamedTag child : entity.getTags())
            {
                if (!"Id".equals(child.getName()) && !"Pos".equals(child.getName()))
                    payload.put(child.getName(), child);
            }
        }
        return new ModernBlockEntitySpec(id, position[0], position[1], position[2], payload);
    }

    private static List<CompoundTag> compoundList(NBTTag tag)
    {
        List<CompoundTag> result = new ArrayList<>();
        if (tag == null)
            return result;
        if (!(tag instanceof ListTag))
            throw new IllegalArgumentException("Schematic block-entity list has wrong NBT type");
        for (NamedTag child : ((ListTag) tag).getTags())
        {
            if (!(child instanceof CompoundTag))
                throw new IllegalArgumentException("Schematic block-entity list contains a non-compound tag");
            result.add((CompoundTag) child);
        }
        return result;
    }

    private static int schematicVersion(CompoundTag nbt, int fallback)
    {
        NBTTag version = nbt.get("Version");
        return version == null ? fallback : numericValue(version, "Version");
    }

    private static int requiredInt(CompoundTag nbt, String name)
    {
        NBTTag tag = nbt.get(name);
        if (tag == null)
            throw new IllegalArgumentException("Schematic is missing " + name);
        return numericValue(tag, name);
    }

    private static int numericValue(NBTTag tag, String name)
    {
        if (tag instanceof IntTag)
            return ((IntTag) tag).getValue();
        if (tag instanceof ShortTag)
            return ((ShortTag) tag).getValue() & 0xFFFF;
        throw new IllegalArgumentException("Schematic " + name + " must be an int or short");
    }

    private static void validateDimensions(int width, int height, int length)
    {
        if (width < 1 || height < 1 || length < 1)
            throw new IllegalArgumentException("Schematic dimensions must be positive");
        volume(width, height, length);
    }

    private static int volume(int width, int height, int length)
    {
        long volume = (long) width * height * length;
        if (volume > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Schematic volume is too large: " + volume);
        return (int) volume;
    }

    private static int[] positionForIndex(int width, int length, int index)
    {
        int y = index / (width * length);
        int remainder = index % (width * length);
        int z = remainder / width;
        int x = remainder % width;
        return new int[]{x, y, z};
    }

    private static int[] emptyBounds(int width, int height, int length)
    {
        return new int[]{width, -1, height, -1, length, -1};
    }

    private static void include(int[] bounds, int x, int y, int z)
    {
        bounds[0] = Math.min(bounds[0], x);
        bounds[1] = Math.max(bounds[1], x);
        bounds[2] = Math.min(bounds[2], y);
        bounds[3] = Math.max(bounds[3], y);
        bounds[4] = Math.min(bounds[4], z);
        bounds[5] = Math.max(bounds[5], z);
    }

    public static final class Placement
    {
        private final World world;
        private final int offsetX;
        private final int offsetY;
        private final int offsetZ;
        private final boolean modern;
        private final int version;
        private final int width;
        private final int height;
        private final int length;

        private Placement(World world, int offsetX, int offsetY, int offsetZ, boolean modern, int version,
                          int width, int height, int length)
        {
            this.world = world;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.modern = modern;
            this.version = version;
            this.width = width;
            this.height = height;
            this.length = length;
        }

        public World getWorld() { return this.world; }
        public int getOffsetX() { return this.offsetX; }
        public int getOffsetY() { return this.offsetY; }
        public int getOffsetZ() { return this.offsetZ; }
        public boolean isModern() { return this.modern; }
        public int getVersion() { return this.version; }
        public int getWidth() { return this.width; }
        public int getHeight() { return this.height; }
        public int getLength() { return this.length; }
    }

    private static final class ParsedSchematic
    {
        private final int width;
        private final int height;
        private final int length;
        private final boolean modern;
        private final int version;
        private final int[] bounds;
        private final byte[] legacyBlocks;
        private final byte[] legacyData;
        private final ModernBlockPalette modernPalette;
        private final int[] modernStateKeys;
        private final List<CompoundTag> legacyEntities;
        private final List<ModernBlockEntitySpec> modernEntities;

        private ParsedSchematic(int width, int height, int length, boolean modern, int version, int[] bounds,
                                byte[] legacyBlocks, byte[] legacyData, ModernBlockPalette modernPalette,
                                int[] modernStateKeys, List<CompoundTag> legacyEntities,
                                List<ModernBlockEntitySpec> modernEntities)
        {
            this.width = width;
            this.height = height;
            this.length = length;
            this.modern = modern;
            this.version = version;
            this.bounds = bounds[1] < 0 ? new int[]{0, 0, 0, 0, 0, 0} : bounds;
            this.legacyBlocks = legacyBlocks;
            this.legacyData = legacyData;
            this.modernPalette = modernPalette;
            this.modernStateKeys = modernStateKeys;
            this.legacyEntities = legacyEntities;
            this.modernEntities = modernEntities;
        }

        private static ParsedSchematic legacy(int width, int height, int length, byte[] blocks, byte[] data,
                                              int[] bounds, List<CompoundTag> entities, int version)
        {
            return new ParsedSchematic(width, height, length, false, version, bounds, blocks, data,
                    null, null, entities, new ArrayList<>());
        }

        private static ParsedSchematic modern(int width, int height, int length, ModernBlockPalette palette,
                                              int[] stateKeys, int[] bounds, List<ModernBlockEntitySpec> entities,
                                              int version)
        {
            return new ParsedSchematic(width, height, length, true, version, bounds, null, null,
                    palette, stateKeys, new ArrayList<>(), entities);
        }

        private World toWorld(int offsetX, int offsetY, int offsetZ)
        {
            int minChunkX = offsetX >> 4;
            int minChunkZ = offsetZ >> 4;
            int maxChunkX = (offsetX + this.width - 1) >> 4;
            int maxChunkZ = (offsetZ + this.length - 1) >> 4;
            World world = new World(maxChunkX - minChunkX + 1, maxChunkZ - minChunkZ + 1,
                    minChunkX, minChunkZ, this.modernPalette);

            int volume = volume(this.width, this.height, this.length);
            for (int index = 0; index < volume; index++)
            {
                int[] position = positionForIndex(this.width, this.length, index);
                int x = position[0] + offsetX;
                int y = position[1] + offsetY;
                int z = position[2] + offsetZ;
                if (this.modern)
                {
                    world.setModernBlockState(x, y, z, this.modernStateKeys[index]);
                }
                else
                {
                    world.setBlock(x, y, z, this.legacyBlocks[index] & 0xFF,
                            (byte) (this.legacyData[index] & 0xF));
                }
            }

            if (this.modern)
            {
                for (ModernBlockEntitySpec entity : this.modernEntities)
                {
                    int x = entity.x + offsetX;
                    int y = entity.y + offsetY;
                    int z = entity.z + offsetZ;
                    world.addModernBlockEntity(new ModernBlockEntity(entity.id,
                            new BlockPosition(x, y, z), entity.toNetworkData(x, y, z)));
                }
            }
            else
            {
                for (CompoundTag tag : this.legacyEntities)
                {
                    if (tag.get("Text1") == null || tag.get("Text2") == null
                            || tag.get("Text3") == null || tag.get("Text4") == null)
                        continue;
                    SignTileEntity sign = new SignTileEntity(tag);
                    sign.translate(offsetX, offsetY, offsetZ);
                    BlockPosition position = sign.getBlockPosition();
                    Chunk chunk = world.getChunkAtWorldPos(position.getX(), position.getZ());
                    if (chunk != null)
                        chunk.addTileEntity(sign);
                }
            }
            return world;
        }
    }

    private static final class ModernBlockEntitySpec
    {
        private final String id;
        private final int x;
        private final int y;
        private final int z;
        private final CompoundTag payload;

        private ModernBlockEntitySpec(String id, int x, int y, int z, CompoundTag payload)
        {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.payload = payload;
        }

        private CompoundTag toNetworkData(int x, int y, int z)
        {
            CompoundTag result = new CompoundTag();
            for (NamedTag child : this.payload.getTags())
                result.put(child.getName(), child);
            result.put("id", new StringTag(this.id));
            result.put("x", new IntTag(x));
            result.put("y", new IntTag(y));
            result.put("z", new IntTag(z));
            return result;
        }
    }

    private static final class Cursor
    {
        private final byte[] data;
        private int index;

        private Cursor(byte[] data)
        {
            this.data = data;
        }

        private int readVarInt()
        {
            int value = 0;
            int shift = 0;
            while (true)
            {
                if (this.index >= this.data.length)
                    throw new IllegalArgumentException("Truncated Sponge block-data VarInt at byte " + this.index);
                int part = this.data[this.index++] & 0xFF;
                value |= (part & 0x7F) << shift;
                if ((part & 0x80) == 0)
                    return value;
                shift += 7;
                if (shift >= 35)
                    throw new IllegalArgumentException("Sponge block-data VarInt exceeds five bytes");
            }
        }

        private boolean isAtEnd()
        {
            return this.index == this.data.length;
        }
    }
}
