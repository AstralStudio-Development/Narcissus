package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import java.io.IOException;
import java.util.UUID;

public record PacketLoginOutSuccess774(UUID uuid, String username) implements PacketOut {
    @Override public void write(PacketSerializer out) throws IOException {
        out.writeUUID(uuid);
        out.writeString(username);
        out.writeVarInt(0); // offline profile properties
    }
}
