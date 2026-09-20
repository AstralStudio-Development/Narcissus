package cn.starry.narcissus.functions.packets.in;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.out.PacketStatusOutPong;

import java.io.IOException;

/**
 * Created by Rigner on 30/08/16 for project Limbo.
 * All rights reserved.
 */
public class PacketStatusInPing implements PacketIn
{
    private long id;

    @Override
    public void readPacket(PacketSerializer packetSerializer) throws IOException
    {
        this.id = packetSerializer.readLong();
    }

    @Override
    public void handlePacket(NetworkManager networkManager, PlayerManager playerManager)
    {
        networkManager.sendPacket(playerManager, new PacketStatusOutPong(this.id));
    }
}
