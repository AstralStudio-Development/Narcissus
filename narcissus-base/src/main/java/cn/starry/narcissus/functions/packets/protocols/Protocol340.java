package cn.starry.narcissus.functions.packets.protocols;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.AbstractProtocol;
import cn.starry.narcissus.functions.packets.Status;
import cn.starry.narcissus.functions.packets.in.PacketInIgnored;
import cn.starry.narcissus.functions.packets.in.PacketLoginInStart;
import cn.starry.narcissus.functions.packets.in.PacketStatusInPing;
import cn.starry.narcissus.functions.packets.in.PacketStatusInRequest;
import cn.starry.narcissus.functions.packets.in.protocol.v340.PacketPlayInKeepAlive340;
import cn.starry.narcissus.functions.packets.in.protocol.v47.PacketPlayInChat47;
import cn.starry.narcissus.functions.packets.out.*;
import cn.starry.narcissus.functions.packets.out.protocol.v107.PacketPlayOutPlayerPositionAndLook107;
import cn.starry.narcissus.functions.packets.out.protocol.v109.PacketPlayOutJoinGame109;
import cn.starry.narcissus.functions.packets.out.protocol.v110.PacketPlayOutChunkData110;
import cn.starry.narcissus.functions.packets.out.protocol.v110.PacketPlayOutUpdateBlockEntity110;
import cn.starry.narcissus.functions.packets.out.protocol.v340.PacketPlayOutKeepAlive340;
import cn.starry.narcissus.functions.packets.out.protocol.v47.PacketPlayOutChat47;
import cn.starry.narcissus.functions.world.Chunk;
import cn.starry.narcissus.functions.world.SignTileEntity;

/**
 * Created by Rigner on 31/08/16 for project Limbo.
 * All rights reserved.
 */
public class Protocol340 extends AbstractProtocol
{
    public Protocol340(NetworkManager networkManager)
    {
        super(networkManager);

        this.registerPacketIn(Status.STATUS, 0x00, PacketStatusInRequest.class);
        this.registerPacketIn(Status.STATUS, 0x01, PacketStatusInPing.class);
        this.registerPacketOut(Status.STATUS, 0x00, PacketStatusOutResponse.class);
        this.registerPacketOut(Status.STATUS, 0x01, PacketStatusOutPong.class);

        this.registerPacketIn(Status.LOGIN, 0x00, PacketLoginInStart.class);
        this.registerPacketOut(Status.LOGIN, 0x00, PacketOutDisconnect.class);
        this.registerPacketOut(Status.LOGIN, 0x02, PacketLoginOutSuccess.class);

        this.registerPacketOut(Status.PLAY, 0x1A, PacketOutDisconnect.class);
        this.registerPacketOut(Status.PLAY, 0x1F, PacketPlayOutKeepAlive340.class);
        this.registerPacketOut(Status.PLAY, 0x2F, PacketPlayOutPlayerPositionAndLook107.class);
        this.registerPacketOut(Status.PLAY, 0x46, PacketPlayOutUpdateSign.class);

        //Protocol109
        this.registerPacketOut(Status.PLAY, 0x23, PacketPlayOutJoinGame109.class);

        //Protocol110
        this.registerPacketOut(Status.PLAY, 0x09, PacketPlayOutUpdateBlockEntity110.class);
        this.registerPacketOut(Status.PLAY, 0x20, PacketPlayOutChunkData110.class);
        this.registerPacketOut(Status.PLAY, 0x46, null);

        this.registerPacketIn(Status.PLAY, 0x01, PacketPlayInChat47.class);//Chat In
        this.registerPacketOut(Status.PLAY, 0x02, PacketPlayOutChat47.class);//Chat Out (Msg)

        this.registerPacketIn(Status.PLAY, 0x00, PacketInIgnored.class);//Teleport confirm
        this.registerPacketIn(Status.PLAY, 0x01, PacketInIgnored.class);//Tab complete
        this.registerPacketIn(Status.PLAY, 0x03, PacketInIgnored.class);//Client status
        this.registerPacketIn(Status.PLAY, 0x04, PacketInIgnored.class);//Client Settings
        this.registerPacketIn(Status.PLAY, 0x05, PacketInIgnored.class);//Confirm transaction
        this.registerPacketIn(Status.PLAY, 0x06, PacketInIgnored.class);//Enchant item
        this.registerPacketIn(Status.PLAY, 0x07, PacketInIgnored.class);//Click window
        this.registerPacketIn(Status.PLAY, 0x08, PacketInIgnored.class);//Close window
        this.registerPacketIn(Status.PLAY, 0x09, PacketInIgnored.class);//Plugin message
        this.registerPacketIn(Status.PLAY, 0x0A, PacketInIgnored.class);//Use entity
        this.registerPacketIn(Status.PLAY, 0x0B, PacketPlayInKeepAlive340.class);
        this.registerPacketIn(Status.PLAY, 0x0C, PacketInIgnored.class);//Player position
        this.registerPacketIn(Status.PLAY, 0x0E, PacketInIgnored.class);//Player position and look
        this.registerPacketIn(Status.PLAY, 0x0D, PacketInIgnored.class);//Player look
        this.registerPacketIn(Status.PLAY, 0x0F, PacketInIgnored.class);//Player (ground)
        this.registerPacketIn(Status.PLAY, 0x10, PacketInIgnored.class);//Vehicle move
        this.registerPacketIn(Status.PLAY, 0x11, PacketInIgnored.class);//Steer boat
        this.registerPacketIn(Status.PLAY, 0x12, PacketInIgnored.class);//Player abilities
        this.registerPacketIn(Status.PLAY, 0x13, PacketInIgnored.class);//Player digging
        this.registerPacketIn(Status.PLAY, 0x14, PacketInIgnored.class);//Entity action
        this.registerPacketIn(Status.PLAY, 0x15, PacketInIgnored.class);//Steer vehicle
        this.registerPacketIn(Status.PLAY, 0x16, PacketInIgnored.class);//Resource pack status
        this.registerPacketIn(Status.PLAY, 0x17, PacketInIgnored.class);//Held item change
        this.registerPacketIn(Status.PLAY, 0x18, PacketInIgnored.class);//Creative inventory action
        this.registerPacketIn(Status.PLAY, 0x19, PacketInIgnored.class);//Update sign
        this.registerPacketIn(Status.PLAY, 0x1A, PacketInIgnored.class);//Animation
        this.registerPacketIn(Status.PLAY, 0x1B, PacketInIgnored.class);//Spectate
        this.registerPacketIn(Status.PLAY, 0x1C, PacketInIgnored.class);//Block placement
        this.registerPacketIn(Status.PLAY, 0x1D, PacketInIgnored.class);//Use item

    }

    @Override
    public int[] getVersions()
    {
        return new int[]{340};
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

    @Override
    public void disconnect(PlayerManager playerManager, String message)
    {
        this.networkManager.sendPacket(playerManager, new PacketOutDisconnect(message));
    }

    @Override
    public void sendJoinGame(PlayerManager playerManager, int entityId, byte gameMode, int dimension, byte difficulty, byte maxPlayers, String levelType, boolean debugInfo)
    {
        this.networkManager.sendPacket(playerManager, new PacketPlayOutJoinGame109(entityId, gameMode, (byte)dimension, difficulty, maxPlayers, levelType, debugInfo));
    }

    @Override
    public void sendPosition(PlayerManager playerManager, double x, double y, double z, float yaw, float pitch)
    {
        this.networkManager.sendPacket(playerManager, new PacketPlayOutPlayerPositionAndLook107(x, y, z, yaw, pitch, (byte)0, 0));
    }

    @Override
    public void sendKeepAlive(PlayerManager playerManager, int id)
    {
        this.networkManager.sendPacket(playerManager, new PacketPlayOutKeepAlive340(id));
    }
}
