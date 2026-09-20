package cn.starry.narcissus.functions.packets.in.protocol.v774;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.in.PacketIn;
import cn.starry.narcissus.functions.packets.protocols.ModernConfigurationDriver;
import cn.starry.narcissus.functions.packets.protocols.ModernProtocol;

import java.io.IOException;

/**
 * Client response to Select Known Packs (CONFIGURATION 0x07). Registry data is
 * deliberately sent only after this response, matching the vanilla sequence.
 */
public class PacketConfigInSelectKnownPacks774 implements PacketIn {

    private static final int MAX_PACKS = 64;

    private String acceptedCoreVersion;

    @Override
    public void readPacket(PacketSerializer s) throws IOException {
        int count = s.readVarInt();
        if (count < 0 || count > MAX_PACKS) {
            throw new IOException("Invalid known-pack count: " + count);
        }
        for (int i = 0; i < count; i++) {
            String namespace = s.readString();
            String id = s.readString();
            String version = s.readString();
            if ("minecraft".equals(namespace) && "core".equals(id)) {
                this.acceptedCoreVersion = version;
            }
        }
    }

    @Override
    public void handlePacket(NetworkManager networkManager, PlayerManager playerManager) {
        ModernProtocol protocol = (ModernProtocol) playerManager.getProtocol();
        boolean knowsCore = protocol.getCorePackVersion().equals(this.acceptedCoreVersion);
        ModernConfigurationDriver.sendRegistryData(networkManager, playerManager, knowsCore);
    }
}
