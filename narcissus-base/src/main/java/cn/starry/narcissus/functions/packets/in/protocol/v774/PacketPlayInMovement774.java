package cn.starry.narcissus.functions.packets.in.protocol.v774;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.in.PacketIn;
import java.io.IOException;

/** Shared movement handling for modern 774/776 position packets. */
abstract class PacketPlayInMovement774 implements PacketIn
{
    protected double x;
    protected double z;

    @Override
    public void handlePacket(NetworkManager networkManager, PlayerManager playerManager)
    {
        // Movement is intentionally ignored. Modern chunks are preloaded once
        // during join; there is no real-time chunk streaming in this mode.
    }

    public double getX()
    {
        return this.x;
    }

    public double getZ()
    {
        return this.z;
    }

    protected static double readDouble(PacketSerializer serializer) throws IOException
    {
        return Double.longBitsToDouble(serializer.readLong());
    }

    protected static float readFloat(PacketSerializer serializer) throws IOException
    {
        return Float.intBitsToFloat(serializer.readInt());
    }

    protected static void readMovementFlags(PacketSerializer serializer) throws IOException
    {
        // MovementFlags is an unsigned byte containing onGround and
        // hasHorizontalCollision bits. Both bits must be consumed together.
        serializer.readUnsignedByte();
    }
}
