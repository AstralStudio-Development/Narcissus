package cn.starry.narcissus.functions.packets.protocols;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.AbstractProtocol;
import cn.starry.narcissus.functions.packets.Status;
import cn.starry.narcissus.functions.packets.in.PacketInIgnored;
import cn.starry.narcissus.functions.packets.in.PacketInPluginMessage;
import cn.starry.narcissus.functions.packets.in.PacketLoginInStart;
import cn.starry.narcissus.functions.packets.in.PacketStatusInPing;
import cn.starry.narcissus.functions.packets.in.PacketStatusInRequest;
import cn.starry.narcissus.functions.packets.out.*;
import cn.starry.narcissus.functions.packets.out.protocol.v47.*;
import cn.starry.narcissus.functions.packets.in.protocol.v47.PacketPlayInChat47;
import cn.starry.narcissus.functions.packets.in.protocol.v47.PacketPlayInClientSettings47;
import cn.starry.narcissus.functions.packets.in.protocol.v47.PacketPlayInKeepAlive47;
import cn.starry.narcissus.functions.packets.in.protocol.v47.PacketPlayInTabComplete47;
import cn.starry.narcissus.functions.world.Chunk;
import cn.starry.narcissus.functions.world.SignTileEntity;
import cn.starry.narcissus.functions.world.World;
import cn.starry.narcissus.functions.world.WorldLighting;
import cn.starry.narcissus.functions.world.lighting.BlockLightingProperties;
import cn.starry.narcissus.util.ProtocolVersionNames;

/**
 * Created by Rigner on 29/08/16 for project Limbo.
 * All rights reserved.
 */
public class Protocol47 extends AbstractProtocol
{
    private static final BlockLightingProperties LIGHTING =
            BlockLightingProperties.load("v47", "1.8.9", 4096);
    public Protocol47(NetworkManager networkManager)
    {
        super(networkManager);

        this.registerPacketIn(Status.STATUS, 0x00, PacketStatusInRequest.class);
        this.registerPacketIn(Status.STATUS, 0x01, PacketStatusInPing.class);
        this.registerPacketOut(Status.STATUS, 0x00, PacketStatusOutResponse.class);
        this.registerPacketOut(Status.STATUS, 0x01, PacketStatusOutPong.class);

        this.registerPacketIn(Status.LOGIN, 0x00, PacketLoginInStart.class);
        this.registerPacketOut(Status.LOGIN, 0x00, PacketOutDisconnect.class);
        this.registerPacketOut(Status.LOGIN, 0x02, PacketLoginOutSuccess.class);

        this.registerPacketOut(Status.PLAY, 0x00, PacketPlayOutKeepAlive47.class);
        this.registerPacketOut(Status.PLAY, 0x01, PacketPlayOutJoinGame47.class);
        this.registerPacketOut(Status.PLAY, 0x03, PacketPlayOutTimeUpdate47.class);
        this.registerPacketOut(Status.PLAY, 0x08, PacketPlayOutPlayerPositionAndLook47.class);
        this.registerPacketOut(Status.PLAY, 0x0A, PacketPlayOutUseBed47.class);
        this.registerPacketOut(Status.PLAY, 0x0C, PacketPlayOutSpawnPlayer47.class);
        this.registerPacketOut(Status.PLAY, 0x0F, PacketPlayOutSpawnMob47.class);
        this.registerPacketOut(Status.PLAY, 0x13, PacketPlayOutDestroyEntities47.class);
        this.registerPacketOut(Status.PLAY, 0x1B, PacketPlayOutAttachEntity47.class);
        this.registerPacketOut(Status.PLAY, 0x1C, PacketPlayOutEntityMetadata47.class);
        this.registerPacketOut(Status.PLAY, 0x21, PacketPlayOutChunkData47.class); //Create World
        this.registerPacketOut(Status.PLAY, 0x23, PacketPlayOutBlockChange47.class); //Block Change
        this.registerPacketOut(Status.PLAY, 0x33, PacketPlayOutUpdateSign.class);
        this.registerPacketOut(Status.PLAY, 0x40, PacketOutDisconnect.class);
        this.registerPacketOut(Status.PLAY, 0x45, PacketPlayOutTitle47.class); //Title
        this.registerPacketOut(Status.PLAY, 0x38, PacketPlayOutPlayerInfo47.class); //Player list item
        this.registerPacketOut(Status.PLAY, 0x3E, PacketPlayOutTeams47.class); //Teams
        this.registerPacketOut(Status.PLAY, 0x47, PacketPlayOutPlayerListHeaderFooter47.class); //Tab header/footer
        this.registerPacketOut(Status.PLAY, 0x3A, PacketPlayOutTabComplete47.class); //Tab complete
        this.registerPacketIn(Status.PLAY, 0x04, PacketInIgnored.class);


        this.registerPacketIn(Status.PLAY, 0x00, PacketPlayInKeepAlive47.class);

        this.registerPacketIn(Status.PLAY, 0x01, PacketPlayInChat47.class);//Chat In
        this.registerPacketOut(Status.PLAY, 0x02, PacketPlayOutChat47.class);//Chat Out (Msg)

        this.registerPacketIn(Status.PLAY, 0x02, PacketInIgnored.class);//Use entity
        this.registerPacketIn(Status.PLAY, 0x03, PacketInIgnored.class);//Player (ground)
        this.registerPacketIn(Status.PLAY, 0x05, PacketInIgnored.class);//Player look
        this.registerPacketIn(Status.PLAY, 0x06, PacketInIgnored.class);//Player position and look
        this.registerPacketIn(Status.PLAY, 0x07, PacketInIgnored.class);//Player digging
        this.registerPacketIn(Status.PLAY, 0x08, PacketInIgnored.class);//Block placement
        this.registerPacketIn(Status.PLAY, 0x09, PacketInIgnored.class);//Held item change
        this.registerPacketIn(Status.PLAY, 0x0A, PacketInIgnored.class);//Animation
        this.registerPacketIn(Status.PLAY, 0x0B, PacketInIgnored.class);//Entity action
        this.registerPacketIn(Status.PLAY, 0x0C, PacketInIgnored.class);//Steer vehicle
        this.registerPacketIn(Status.PLAY, 0x0D, PacketInIgnored.class);//Close window
        this.registerPacketIn(Status.PLAY, 0x0E, PacketInIgnored.class);//Click window
        this.registerPacketIn(Status.PLAY, 0x0F, PacketInIgnored.class);//Confirm transaction
        this.registerPacketIn(Status.PLAY, 0x10, PacketInIgnored.class);//Creative inventory action
        this.registerPacketIn(Status.PLAY, 0x11, PacketInIgnored.class);//Enchant item
        this.registerPacketIn(Status.PLAY, 0x12, PacketInIgnored.class);//Update sign
        this.registerPacketIn(Status.PLAY, 0x13, PacketInIgnored.class);//Player abilities
        this.registerPacketIn(Status.PLAY, 0x14, PacketPlayInTabComplete47.class);//Tab complete
        this.registerPacketIn(Status.PLAY, 0x15, PacketPlayInClientSettings47.class);//Client Settings
        this.registerPacketIn(Status.PLAY, 0x16, PacketInIgnored.class);//Client status
        this.registerPacketIn(Status.PLAY, 0x17, PacketInPluginMessage.class);//Plugin message
        this.registerPacketIn(Status.PLAY, 0x18, PacketInIgnored.class);//Spectate
        this.registerPacketIn(Status.PLAY, 0x19, PacketInIgnored.class);//Resource pack status
    }

    @Override
    public int[] getVersions()
    {
        return new int[]{47};
    }

    @Override
    public void disconnect(PlayerManager playerManager, String message)
    {
        this.networkManager.sendPacket(playerManager, new PacketOutDisconnect(message));
    }

    @Override
    public void sendJoinGame(PlayerManager playerManager, int entityId, byte gameMode, int dimension, byte difficulty, byte maxPlayers, String levelType, boolean debugInfo)
    {
        this.networkManager.sendPacket(playerManager, new PacketPlayOutJoinGame47(entityId, gameMode, (byte)dimension, difficulty, maxPlayers, levelType, debugInfo));
    }

    @Override
    public void sendPosition(PlayerManager playerManager, double x, double y, double z, float yaw, float pitch)
    {
        this.networkManager.sendPacket(playerManager, new PacketPlayOutPlayerPositionAndLook47(x, y, z, yaw, pitch, (byte)0));
    }

    @Override
    public void sendKeepAlive(PlayerManager playerManager, int id)
    {
        this.networkManager.sendPacket(playerManager, new PacketPlayOutKeepAlive47(id));
    }

    @Override
    public void sendChunk(PlayerManager playerManager, Chunk chunk)
    {
        World world = playerManager.getWorld();
        boolean hasSkyLight = hasSkyLight();
        if (world != null)
            prepareLighting(world);
        this.networkManager.sendPacket(playerManager, new PacketPlayOutChunkData47(
                chunk, world == null ? null : world.getLighting(), hasSkyLight));
    }

    /** Sends only empty halo chunks that contain propagated block light. */
    public void sendLightHaloChunks(PlayerManager playerManager)
    {
        World world = playerManager.getWorld();
        if (world == null)
            return;
        boolean hasSkyLight = hasSkyLight();
        world.prepareLegacyLighting(LIGHTING, hasSkyLight);
        WorldLighting lighting = world.getLighting();
        for (int chunkX = lighting.minChunkX(); chunkX <= lighting.maxChunkX(); chunkX++)
        {
            for (int chunkZ = lighting.minChunkZ(); chunkZ <= lighting.maxChunkZ(); chunkZ++)
            {
                if (world.getChunk(chunkX, chunkZ) == null && lighting.hasBlockLight(chunkX, chunkZ))
                {
                    this.networkManager.sendPacket(playerManager, new PacketPlayOutChunkData47(
                            chunkX, chunkZ, lighting, hasSkyLight));
                }
            }
        }
    }

    /** Prewarms the immutable template snapshot used by subsequent player copies. */
    public void prepareLighting(World world)
    {
        if (world != null)
            world.prepareLegacyLighting(LIGHTING, hasSkyLight());
    }

    private boolean hasSkyLight()
    {
        return this.networkManager == null
                || this.networkManager.getLimboConfiguration() == null
                || this.networkManager.getLimboConfiguration().getDimension() == 0;
    }

    @Override
    public void sendSign(PlayerManager playerManager, SignTileEntity signTileEntity)
    {
        this.networkManager.sendPacket(playerManager,
                new PacketPlayOutUpdateSign(signTileEntity,
                        ProtocolVersionNames.forProtocol(playerManager.getProtocolId())));
    }

}
