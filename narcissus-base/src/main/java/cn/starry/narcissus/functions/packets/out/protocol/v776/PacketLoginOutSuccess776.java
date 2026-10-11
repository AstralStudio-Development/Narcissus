package cn.starry.narcissus.functions.packets.out.protocol.v776;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import java.io.IOException;
import java.util.UUID;

public record PacketLoginOutSuccess776(UUID uuid, String username, UUID sessionId) implements PacketOut {
    @Override public void write(PacketSerializer out) throws IOException {
        out.writeUUID(uuid);
        out.writeString(username);
        out.writeVarInt(0); // offline profile properties
        out.writeUUID(sessionId);
    }
}
