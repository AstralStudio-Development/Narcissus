package cn.starry.narcissus.functions.packets.in.protocol.v774;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.Status;
import cn.starry.narcissus.functions.packets.in.PacketIn;
import cn.starry.narcissus.functions.packets.protocols.ModernConfigurationDriver;

import java.io.IOException;

/**
 * Login Acknowledged (login_acknowledged, LOGIN 0x03) for protocol 774.
 * Transitions the connection into CONFIGURATION and kicks off the registry /
 * finish-configuration exchange.
 */
public class PacketLoginInAcknowledged774 implements PacketIn {

    @Override
    public void readPacket(PacketSerializer s) throws IOException {
        // no fields
    }

    @Override
    public void handlePacket(NetworkManager networkManager, PlayerManager playerManager) {
        playerManager.setLoginAcknowledged(true);
        playerManager.setStatus(Status.CONFIGURATION);
        ModernConfigurationDriver.beginConfiguration(networkManager, playerManager);
    }
}
