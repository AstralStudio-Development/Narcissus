package cn.starry.narcissus.functions.packets.in;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.managers.PlayerManager;

import java.io.IOException;

/**
 * Created by Rigner on 29/08/16 for project Limbo.
 * All rights reserved.
 */
public interface PacketIn
{
    void readPacket(PacketSerializer packetSerializer) throws IOException;

    void handlePacket(NetworkManager networkManager, PlayerManager playerManager);
}
