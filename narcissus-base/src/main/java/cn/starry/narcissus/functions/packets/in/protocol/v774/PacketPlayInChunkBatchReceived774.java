package cn.starry.narcissus.functions.packets.in.protocol.v774;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.in.PacketIn;
import cn.starry.narcissus.functions.packets.protocols.ModernConfigurationDriver;

import java.io.IOException;

/**
 * Chunk batch received (serverbound PLAY 0x0a in protocol 774).
 * The payload is the client's f32 chunks-per-tick recommendation. Receipt is
 * the first reliable signal that the client has completed the chunk batch.
 */
public final class PacketPlayInChunkBatchReceived774 implements PacketIn {

    @SuppressWarnings("unused")
    private float chunksPerTick;

    @Override
    public void readPacket(PacketSerializer s) throws IOException {
        this.chunksPerTick = Float.intBitsToFloat(s.readInt());
    }

    @Override
    public void handlePacket(NetworkManager networkManager, PlayerManager playerManager) {
        ModernConfigurationDriver.onChunkBatchReceived(networkManager, playerManager);
    }
}
