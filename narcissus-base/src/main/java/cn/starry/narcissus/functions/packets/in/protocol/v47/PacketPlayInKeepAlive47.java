package cn.starry.narcissus.functions.packets.in.protocol.v47;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.in.PacketIn;

import java.io.IOException;

/**
 * Created by Rigner on 30/08/16 for project Limbo.
 * All rights reserved.
 */
public class PacketPlayInKeepAlive47 implements PacketIn
{
    private int id;

    @Override
    public void readPacket(PacketSerializer packetSerializer) throws IOException
    {
        this.id = packetSerializer.readVarInt();
    }

    @Override
    public void handlePacket(NetworkManager networkManager, PlayerManager playerManager)
    {
        if (this.id == networkManager.getCurrentKeepAliveId())
            playerManager.keepAlive();
    }
}
