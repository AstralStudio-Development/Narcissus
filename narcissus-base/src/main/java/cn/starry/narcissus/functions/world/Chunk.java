package cn.starry.narcissus.functions.world;

import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.PacketSerializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by Rigner on 30/08/16,Update by Stalyer for project Limbo.
 * All rights reserved.
 */
public class Chunk
{
    private static final int CHUNK_SIZE = 196864;
    private static final int LEGACY_BLOCK_LIGHT_OFFSET = 16 * 16 * 256 * 2;
    private static final int LEGACY_SKY_LIGHT_OFFSET = LEGACY_BLOCK_LIGHT_OFFSET + 16 * 2048;
    private final int x;
    private final int z;
    private final byte[] map;
    private final long[][] mapWithPalette;
    /** Schematic-local modern state keys; allocated only for modern schematics. */
    private int[] modernStateKeys;
    private List<TileEntity> tileEntities;
    private List<ModernBlockEntity> modernBlockEntities;

    Chunk(int x, int z)
    {
        this.x = x;
        this.z = z;
        this.map = new byte[Chunk.CHUNK_SIZE];
        this.mapWithPalette = new long[16][832];
        this.modernStateKeys = null;
        this.tileEntities = new ArrayList<>();
        this.modernBlockEntities = new ArrayList<>();
        Arrays.fill(this.map, (byte)0);
        for (long[] tab : this.mapWithPalette)
            Arrays.fill(tab, (long)0);
    }

    void setBlock(int x, int y, int z, int id, int meta)
    {
        int index = x + y * 16 * 16 + z * 16;
        index *= 2;
        this.map[index] = ((byte) ((byte) (id << 4) | meta));
        this.map[(index + 1)] = ((byte) (id >> 4));
        if (this.modernStateKeys != null && y >= 0 && y < 256)
            this.modernStateKeys[stateIndex(x, y, z)] = 0;

        int section = y / 16;
        index = x + z * 16 + y % 16 * 16 * 16;
        index *= 13;
        int l = index / 64;
        index = index % 64;
        for (int i = 0; i < 13; ++i)
        {
            if (index == 64)
            {
                index = 0;
                l++;
            }
            long value;
            if (i < 4)
                value = (meta & (1L << i)) != 0 ? 1 : 0;
            else
                value = (id & (1L << (i - 4))) != 0 ? 1 : 0;
            this.mapWithPalette[section][l] = this.mapWithPalette[section][l] & ~(1L << index);
            this.mapWithPalette[section][l] = this.mapWithPalette[section][l] | (value << index);

            index++;
        }
    }

    public int getX()
    {
        return this.x;
    }

    public int getZ()
    {
        return this.z;
    }

    /** Returns the legacy block id at local x/y/z. */
    public int getLegacyId(int x, int y, int z)
    {
        int index = (x + y * 16 * 16 + z * 16) * 2;
        int low = this.map[index] & 0xFF;
        int high = this.map[index + 1] & 0xFF;
        return ((high & 0xF) << 4) | (low >> 4);
    }

    /** Returns the legacy metadata nibble at local x/y/z. */
    public int getLegacyMeta(int x, int y, int z)
    {
        int index = (x + y * 16 * 16 + z * 16) * 2;
        return this.map[index] & 0xF;
    }

    public boolean hasModernBlockStates()
    {
        return this.modernStateKeys != null;
    }

    public int getModernStateKey(int x, int y, int z)
    {
        if (x < 0 || x >= 16 || y < 0 || y >= 256 || z < 0 || z >= 16)
            return 0;
        return this.modernStateKeys == null ? 0 : this.modernStateKeys[stateIndex(x, y, z)];
    }

    void setModernStateKey(int x, int y, int z, int stateKey)
    {
        if (x < 0 || x >= 16 || y < 0 || y >= 256 || z < 0 || z >= 16)
            throw new IllegalArgumentException("Modern block outside chunk bounds: " + x + "," + y + "," + z);
        if (this.modernStateKeys == null)
            this.modernStateKeys = new int[16 * 256 * 16];
        this.modernStateKeys[stateIndex(x, y, z)] = Math.max(0, stateKey);
    }

    private static int stateIndex(int x, int y, int z)
    {
        return x + y * 256 + z * 16;
    }

    public boolean hasLegacyBlocksInSection(int section)
    {
        if (section < 0 || section >= 16)
            return false;
        int start = section * 8192;
        int end = start + 8192;
        for (int index = start; index < end; index += 2)
        {
            if (this.map[index] != 0 || this.map[index + 1] != 0)
                return true;
        }
        return false;
    }

    public byte[] getLegacyBlockSection(int section)
    {
        if (section < 0 || section >= 16)
            throw new IllegalArgumentException("Invalid legacy block section " + section);
        return Arrays.copyOfRange(this.map, section * 8192, (section + 1) * 8192);
    }

    public byte[] getLegacyBiomeData()
    {
        return Arrays.copyOfRange(this.map, this.map.length - 256, this.map.length);
    }

    void setLegacyLightSection(int section, byte[] blockLight, byte[] skyLight)
    {
        if (section < 0 || section >= 16)
            throw new IllegalArgumentException("Invalid legacy light section " + section);
        if (blockLight == null || blockLight.length != 2048
                || skyLight == null || skyLight.length != 2048)
            throw new IllegalArgumentException("Legacy light sections must contain 2048 nibble bytes");
        System.arraycopy(blockLight, 0, this.map,
                LEGACY_BLOCK_LIGHT_OFFSET + section * 2048, 2048);
        System.arraycopy(skyLight, 0, this.map,
                LEGACY_SKY_LIGHT_OFFSET + section * 2048, 2048);
    }

    public byte[] getMap()
    {
        return this.map;
    }

    /**
     * Creates an independent mutable copy for one player's private world.
     * All block storage arrays are copied so block changes cannot leak between
     * players or back into the schematic template.
     */
    Chunk copy()
    {
        Chunk copy = new Chunk(this.x, this.z);
        System.arraycopy(this.map, 0, copy.map, 0, this.map.length);
        for (int section = 0; section < this.mapWithPalette.length; section++)
            System.arraycopy(this.mapWithPalette[section], 0, copy.mapWithPalette[section], 0,
                    this.mapWithPalette[section].length);
        if (this.modernStateKeys != null)
            copy.modernStateKeys = this.modernStateKeys.clone();
        copy.tileEntities = new ArrayList<>(this.tileEntities);
        copy.modernBlockEntities = new ArrayList<>(this.modernBlockEntities);
        return copy;
    }

    public byte[] getMapWithPalette() throws IOException
    {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PacketSerializer packetSerializer = new PacketSerializer(byteArrayOutputStream);
        for (int i = 0; i < 16; ++i)
        {
            packetSerializer.writeByte((byte) 13);
            packetSerializer.writeVarInt(0);
            packetSerializer.writeVarInt(this.mapWithPalette[i].length);
            for (long value : this.mapWithPalette[i])
                packetSerializer.writeLong(value);
            for (int j = 0; j < 2048; ++j)
                packetSerializer.writeByte((byte)0);
        }
        return byteArrayOutputStream.toByteArray();
    }

    public void sendTileEntities(PlayerManager player)
    {
        this.tileEntities.stream().filter(tileEntity -> (tileEntity instanceof SignTileEntity)).forEach(tileEntity -> player.getProtocol().sendSign(player, (SignTileEntity) tileEntity));
    }

    void addTileEntity(TileEntity tileEntity)
    {
        this.tileEntities.add(tileEntity);
    }

    void addModernBlockEntity(ModernBlockEntity blockEntity)
    {
        if (blockEntity != null)
            this.modernBlockEntities.add(blockEntity);
    }

    public List<ModernBlockEntity> getModernBlockEntities()
    {
        return Collections.unmodifiableList(this.modernBlockEntities);
    }

    void removeModernBlockEntitiesAt(int x, int y, int z)
    {
        this.modernBlockEntities.removeIf(entity -> {
            BlockPosition position = entity.getPosition();
            return position.getX() == x && position.getY() == y && position.getZ() == z;
        });
    }
}
