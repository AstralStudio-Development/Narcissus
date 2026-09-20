package cn.starry.narcissus.functions.packets.in;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.config.Configuration;
import cn.starry.narcissus.functions.packets.out.PacketStatusOutResponse;

import java.io.IOException;

/**
 * Created by Rigner on 30/08/16 for project Limbo.
 * All rights reserved.
 *
 * Echoes the client's own handshake protocol number so every version's server
 * list shows the entry as joinable. Because Narcissus now natively speaks the
 * supported protocols (no proxy translation), reflecting the requested protocol
 * avoids a spurious "outdated client/server" prompt on the multiplayer list.
 */
public class PacketStatusInRequest implements PacketIn
{
    @Override
    public void readPacket(PacketSerializer packetSerializer) throws IOException
    {
    }

    @Override
    public void handlePacket(NetworkManager networkManager, PlayerManager playerManager)
    {
        Configuration configuration = networkManager.getLimboConfiguration();
        int max = configuration.getMaxSlots() >= 0 ? configuration.getMaxSlots() : 1000;
        long online = networkManager.getConnectedPlayers();
        String motd = jsonEscape(configuration.getMotd());
        int protocol = playerManager.getProtocolId();

        String response = "{"
                + "\"version\":{\"name\":\"Narcissus\",\"protocol\":" + protocol + "},"
                + "\"players\":{\"max\":" + max + ",\"online\":" + online + ",\"sample\":[]},"
                + "\"description\":{\"text\":\"" + motd + "\"}"
                + "}";
        networkManager.sendPacket(playerManager, new PacketStatusOutResponse(response));
    }

    private static String jsonEscape(String value)
    {
        if (value == null)
            return "Limbo";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
