package cn.starry.narcissus.functions.packets.protocols;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.Status;
import cn.starry.narcissus.functions.packets.out.protocol.v109.PacketPlayOutJoinGame109;

/**
 * Created by Rigner on 31/08/16 for project Limbo.
 * All rights reserved.
 */
public class Protocol109 extends Protocol107
{
    public Protocol109(NetworkManager networkManager)
    {
        super(networkManager);

        this.registerPacketOut(Status.PLAY, 0x23, PacketPlayOutJoinGame109.class);
    }

    @Override
    public void sendJoinGame(PlayerManager playerManager, int entityId, byte gameMode, int dimension, byte difficulty, byte maxPlayers, String levelType, boolean debugInfo)
    {
        this.networkManager.sendPacket(playerManager, new PacketPlayOutJoinGame109(entityId, gameMode, dimension, difficulty, maxPlayers, levelType, debugInfo));
    }

    @Override
    public int[] getVersions()
    {
        return new int[]{108, 109};
    }
}
