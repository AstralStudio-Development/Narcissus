package cn.starry.narcissus.functions.packets.protocols;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.Status;
import cn.starry.narcissus.functions.packets.out.protocol.v110.PacketPlayOutChunkData110;
import cn.starry.narcissus.functions.packets.out.protocol.v110.PacketPlayOutUpdateBlockEntity110;
import cn.starry.narcissus.functions.world.Chunk;
import cn.starry.narcissus.functions.world.SignTileEntity;

/**
 * Created by Rigner on 31/08/16 for project Limbo.
 * All rights reserved.
 */
public class Protocol110 extends Protocol109
{
    public Protocol110(NetworkManager networkManager)
    {
        super(networkManager);

        this.registerPacketOut(Status.PLAY, 0x09, PacketPlayOutUpdateBlockEntity110.class);
        this.registerPacketOut(Status.PLAY, 0x20, PacketPlayOutChunkData110.class);
        this.registerPacketOut(Status.PLAY, 0x46, null);
    }

    @Override
    public int[] getVersions()
    {
        return new int[]{110};
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
