package cn.starry.narcissus.functions.packets.in.protocol.v47;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.in.PacketIn;

import java.io.IOException;

/**
 * Created by Stalyer
 * All rights reserved.
 */
public class PacketPlayInChat47 implements PacketIn
{
    private String message;

    @Override
    public void readPacket(PacketSerializer packetSerializer) throws IOException
    {
        this.message = packetSerializer.readString();
    }

    @Override
    public void handlePacket(NetworkManager networkManager, PlayerManager playerManager)
    {
        networkManager.getCommandManager().handleChat(networkManager, playerManager, this.message);
    }
}
