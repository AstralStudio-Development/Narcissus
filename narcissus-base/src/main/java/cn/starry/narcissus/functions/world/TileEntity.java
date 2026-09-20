package cn.starry.narcissus.functions.world;

import cn.starry.narcissus.functions.world.nbt.CompoundTag;

/**
 * Created by Rigner on 30/08/16 for project Limbo.
 * All rights reserved.
 */
public abstract class TileEntity
{
    private BlockPosition blockPosition;
    private CompoundTag data;

    TileEntity(CompoundTag data)
    {
        this.data = data;
        this.blockPosition = new BlockPosition(data.get("x").toIntTag().getValue(), data.get("y").toIntTag().getValue(), data.get("z").toIntTag().getValue());
    }

    public BlockPosition getBlockPosition()
    {
        return this.blockPosition;
    }

    /** Moves this tile entity together with a translated schematic. */
    void translate(int offsetX, int offsetY, int offsetZ)
    {
        this.blockPosition = new BlockPosition(
                this.blockPosition.getX() + offsetX,
                this.blockPosition.getY() + offsetY,
                this.blockPosition.getZ() + offsetZ);
    }

    public abstract String getId();

    public CompoundTag getData()
    {
        return this.data;
    }
}
