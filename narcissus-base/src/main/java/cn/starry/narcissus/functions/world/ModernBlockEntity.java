package cn.starry.narcissus.functions.world;

import cn.starry.narcissus.functions.world.nbt.CompoundTag;

/** A modern schematic block entity after placement into absolute world coordinates. */
public final class ModernBlockEntity
{
    private final String id;
    private final BlockPosition position;
    private final CompoundTag data;

    public ModernBlockEntity(String id, BlockPosition position, CompoundTag data)
    {
        this.id = id;
        this.position = position;
        this.data = data;
    }

    public String getId()
    {
        return this.id;
    }

    public BlockPosition getPosition()
    {
        return this.position;
    }

    /** NBT compound already normalized for the modern chunk packet. */
    public CompoundTag getData()
    {
        return this.data;
    }
}
