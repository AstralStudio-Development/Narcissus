package cn.starry.narcissus.functions.packets.in.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;

import java.io.IOException;

/** Move Player Position (move_player_pos) for protocol 774/776. */
public class PacketPlayInPosition774 extends PacketPlayInMovement774
{
    @Override
    public void readPacket(PacketSerializer serializer) throws IOException
    {
        this.x = readDouble(serializer);
        readDouble(serializer); // y
        this.z = readDouble(serializer);
        readMovementFlags(serializer);
    }
}
