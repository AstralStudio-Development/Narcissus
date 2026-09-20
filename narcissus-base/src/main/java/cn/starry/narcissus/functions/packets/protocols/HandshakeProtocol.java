package cn.starry.narcissus.functions.packets.protocols;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.AbstractProtocol;
import cn.starry.narcissus.functions.packets.Status;
import cn.starry.narcissus.functions.packets.in.PacketInHandshake;
import cn.starry.narcissus.functions.packets.out.PacketOutDisconnect;
import cn.starry.narcissus.functions.world.Chunk;
import cn.starry.narcissus.functions.world.SignTileEntity;

/**
 * Created by Rigner on 29/08/16 for project Limbo.
 * All rights reserved.
 */
public class HandshakeProtocol extends AbstractProtocol
{
    public HandshakeProtocol(NetworkManager networkManager)
    {
        super(networkManager);

        this.registerPacketIn(Status.HANDSHAKE, 0x00, PacketInHandshake.class);

        this.registerPacketOut(Status.HANDSHAKE, 0x00, PacketOutDisconnect.class);
    }

    @Override
    public void disconnect(PlayerManager playerManager, String message)
    {
        this.networkManager.sendPacket(playerManager, new PacketOutDisconnect(message));
    }

    @Override
    public void sendJoinGame(PlayerManager playerManager, int entityId, byte gameMode, int dimension, byte difficulty, byte maxPlayers, String levelType, boolean debugInfo)
    {
    }

    @Override
    public void sendPosition(PlayerManager playerManager, double x, double y, double z, float yaw, float pitch)
    {
    }

    @Override
    public void sendKeepAlive(PlayerManager playerManager, int id)
    {
    }

    @Override
    public void sendChunk(PlayerManager playerManager, Chunk chunk)
    {
    }

    @Override
    public void sendSign(PlayerManager playerManager, SignTileEntity signTileEntity)
    {
    }

    @Override
    public int[] getVersions()
    {
        return new int[]{-1};
    }
}
