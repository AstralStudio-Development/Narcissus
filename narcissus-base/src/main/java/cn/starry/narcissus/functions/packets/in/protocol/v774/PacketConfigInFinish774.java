package cn.starry.narcissus.functions.packets.in.protocol.v774;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.Status;
import cn.starry.narcissus.functions.packets.in.PacketIn;
import cn.starry.narcissus.functions.packets.protocols.ModernConfigurationDriver;

import java.io.IOException;

/**
 * Finish Configuration acknowledgement (finish_configuration, CONFIGURATION 0x03)
 * for protocol 774. The client sends this after we send our Finish Configuration;
 * the connection then enters PLAY and we send the join sequence.
 */
public class PacketConfigInFinish774 implements PacketIn {

    @Override
    public void readPacket(PacketSerializer s) throws IOException {
        // no fields
    }

    @Override
    public void handlePacket(NetworkManager networkManager, PlayerManager playerManager) {
        playerManager.setConfigurationFinished(true);
        playerManager.setStatus(Status.PLAY);
        ModernConfigurationDriver.enterPlay(networkManager, playerManager);
    }
}
