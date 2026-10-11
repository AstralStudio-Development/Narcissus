package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import java.io.IOException;
import java.util.List;

public record PacketPlayOutCommandSuggestions774(int transactionId, int start, int length,
        List<String> suggestions) implements PacketOut {
    public PacketPlayOutCommandSuggestions774 { suggestions = List.copyOf(suggestions); }
    @Override public void write(PacketSerializer out) throws IOException {
        out.writeVarInt(transactionId);
        out.writeVarInt(start);
        out.writeVarInt(length);
        out.writeVarInt(suggestions.size());
        for (String suggestion : suggestions) {
            out.writeString(suggestion);
            out.writeBoolean(false); // absent tooltip
        }
    }
}
