package cn.starry.narcissus.functions.packets.in;

import cn.starry.narcissus.Narcissus;
import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.AbstractProtocol;
import cn.starry.narcissus.functions.packets.Status;
import cn.starry.narcissus.util.UUIDConverter;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Created by Rigner on 29/08/16 for project Limbo.
 * All rights reserved.
 *
 * Selects the native backend protocol implementation by the client's handshake
 * protocol number (no ViaVersion translation). Narcissus natively supports a
 * discrete set of protocols (v47, v774-v777); any other LOGIN attempt is
 * rejected with a clear message rather than pretending to support it.
 *
 * For STATUS pings from unsupported versions we still answer using a generic
 * status-capable protocol so the server list shows a description, and the ping
 * response echoes the client's own protocol number.
 *
 * BungeeCord legacy IP forwarding is only trusted when the genuine transport
 * address is loopback (the co-located proxy).
 */
public class PacketInHandshake implements PacketIn
{
    private int version;
    private String serverAddress;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private int serverPort;
    private int nextState;

    @Override
    public void readPacket(PacketSerializer packetSerializer) throws IOException
    {
        this.version = packetSerializer.readVarInt();
        this.serverAddress = packetSerializer.readString();
        this.serverPort = packetSerializer.readShort() & 0xFFFF;
        this.nextState = packetSerializer.readVarInt();
    }

    @Override
    public void handlePacket(NetworkManager networkManager, PlayerManager playerManager)
    {
        Status status = this.nextState == 1 ? Status.STATUS : this.nextState == 2 ? Status.LOGIN : null;
        if (status == null)
        {
            networkManager.disconnect(playerManager, "State " + this.nextState + " incorrect");
            return;
        }

        playerManager.setProtocolId(this.version);

        AbstractProtocol protocol = networkManager.getProtocolByVersionId(this.version);

        if (status == Status.STATUS)
        {
            // Answer any client's ping. Use the native protocol if we have one,
            // otherwise fall back to a status-capable protocol.
            playerManager.setProtocol(protocol != null ? protocol : networkManager.getStatusProtocol());
            playerManager.setStatus(Status.STATUS);
            this.applyForwarding(playerManager, false);
            return;
        }

        // LOGIN: apply the configured allow-version list before checking the
        // native registry, so direct connections cannot bypass Bungee's filter.
        if (!networkManager.getLimboConfiguration().isVersionAllowed(this.version))
        {
            networkManager.disconnect(playerManager,
                    "§c此服务器不允许你的客户端版本（协议 " + this.version
                            + "）。允许的协议版本："
                            + networkManager.getLimboConfiguration().getAllowedVersions());
            return;
        }

        // LOGIN: require a natively supported protocol.
        if (protocol == null)
        {
            networkManager.disconnect(playerManager,
                    "§c你的版本暂不被此服务器支持（协议 " + this.version
                            + "）。当前原生支持: 1.8.x / 1.21.11 / 26.1 / 26.1.1 / 26.1.2 / 26.2 / 26.3。");
            return;
        }

        if (networkManager.getLimboConfiguration().getMaxSlots() >= 0
                && networkManager.getConnectedPlayers() >= networkManager.getLimboConfiguration().getMaxSlots())
        {
            networkManager.disconnect(playerManager, "Server is full");
            return;
        }

        playerManager.setProtocol(protocol);
        playerManager.setStatus(Status.LOGIN);
        this.applyForwarding(playerManager, true);
    }

    private void applyForwarding(PlayerManager playerManager, boolean login)
    {
        String[] data = this.serverAddress.split("\00");
        InetSocketAddress bungeeAddress = null;
        if ((data.length == 3 || data.length == 4) && playerManager.isLoopbackTransport())
        {
            bungeeAddress = playerManager.getTransportAddress();
            try
            {
                playerManager.setInetAddress(new InetSocketAddress(data[1], playerManager.getInetAddress().getPort()));
                playerManager.setForwardedUuid(UUIDConverter.fromString(data[2]));
            }
            catch (Exception ex)
            {
                Narcissus.LOGGER.warning("Malformed BungeeCord forwarding data from " + playerManager.getTransportAddress());
            }
        }

        if (login)
            Narcissus.LOGGER.info("New connection from " + playerManager.getInetAddress().getHostString()
                    + (bungeeAddress == null ? "" : " (via BungeeCord " + bungeeAddress.getHostString() + ")")
                    + " [client protocol " + this.version + "]");
    }
}
