package cn.starry.narcissus.functions.packets.protocols;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.out.protocol.v110.PacketPlayOutChunkData110;
import cn.starry.narcissus.functions.packets.out.protocol.v110.PacketPlayOutUpdateBlockEntity110;
import cn.starry.narcissus.functions.world.Chunk;
import cn.starry.narcissus.functions.world.SignTileEntity;

/**
 * Created by Rigner on 31/08/16 for project Limbo.
 * All rights reserved.
 */
public class Protocol335 extends Protocol316
{
    public Protocol335(NetworkManager networkManager)
    {
        super(networkManager);

    }

    @Override
    public int[] getVersions()
    {
        return new int[]{335};
    }

    @Override
    public void sendSign(PlayerManager playerManager, SignTileEntity signTileEntity)
    {
        this.networkManager.sendPacket(playerManager, new PacketPlayOutUpdateBlockEntity110(signTileEntity, (byte)9));
    }

    @Override
    public void sendChunk(PlayerManager playerManager, Chunk chunk)
    {
        this.networkManager.sendPacket(playerManager, new PacketPlayOutChunkData110(chunk));
    }
}
