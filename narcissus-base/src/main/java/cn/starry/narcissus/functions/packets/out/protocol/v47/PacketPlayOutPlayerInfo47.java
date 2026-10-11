package cn.starry.narcissus.functions.packets.out.protocol.v47;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import cn.starry.narcissus.functions.packets.PlayerListEntry;

import java.io.IOException;
import java.util.List;

/**
 * 1.8 Player List Item (0x38). Only the add-player (action 0) and
 * remove-player (action 4) actions are used. Add: name, properties,
 * gamemode, ping and a JSON display-name component. Remove: name array.
 */
public final class PacketPlayOutPlayerInfo47 implements PacketOut {
    private final int action;
    private final List<PlayerListEntry> entries;

    public PacketPlayOutPlayerInfo47(List<PlayerListEntry> entries) {
        this.action = 0; // add player
        this.entries = entries;
    }

    public static PacketPlayOutPlayerInfo47 remove(List<java.util.UUID> uuids) {
        return new PacketPlayOutPlayerInfo47(4, toNameEntries(uuids));
    }

    private static List<PlayerListEntry> toNameEntries(List<java.util.UUID> uuids) {
        java.util.List<PlayerListEntry> result = new java.util.ArrayList<>(uuids.size());
        for (java.util.UUID uuid : uuids)
            result.add(new PlayerListEntry(uuid, "", 0));
        return result;
    }

    private PacketPlayOutPlayerInfo47(int action, List<PlayerListEntry> entries) {
        this.action = action;
        this.entries = entries;
    }

    @Override
    public void write(PacketSerializer out) throws IOException {
        out.writeVarInt(this.action);
        out.writeVarInt(this.entries.size());
        for (PlayerListEntry entry : this.entries) {
            out.writeUUID(entry.getUuid());
            if (this.action == 4)
                continue; // remove player: only the UUID is written
            out.writeString(entry.getUsername());
            out.writeVarInt(entry.getProperties().size());
            for (PlayerListEntry.ProfileProperty property : entry.getProperties()) {
                out.writeString(property.getName());
                out.writeString(property.getValue());
                out.writeBoolean(property.getSignature() != null);
                if (property.getSignature() != null)
                    out.writeString(property.getSignature());
            }
            out.writeVarInt(entry.getGameMode());
            out.writeVarInt(0); // ping
            out.writeBoolean(entry.isListed());
            out.writeString("{\"text\":\"§7" + escapeJson(entry.getUsername()) + "\"}");
        }
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
