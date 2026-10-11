package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.modern.Nbt;
import cn.starry.narcissus.functions.modern.Nbt.NbtCompound;
import cn.starry.narcissus.functions.modern.Nbt.NbtList;
import cn.starry.narcissus.functions.modern.Nbt.NbtString;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import java.io.IOException;

/** Plain and clickable components use unnamed network NBT, never JSON strings. */
public record PacketPlayOutSystemChat774(Nbt component, boolean overlay) implements PacketOut {
    public PacketPlayOutSystemChat774(String text, boolean overlay) { this(new NbtString(text), overlay); }
    public static PacketPlayOutSystemChat774 clickable(String text, String action, String value, boolean overlay) {
        return new PacketPlayOutSystemChat774(clickableComponent(text, action, value), overlay);
    }
    public static PacketPlayOutSystemChat774 clickableSuffix(String text, String suffix,
            String action, String value, boolean overlay) {
        NbtCompound root = textComponent(text);
        root.put("extra", new NbtList().add(clickableComponent(suffix, action, value)));
        return new PacketPlayOutSystemChat774(root, overlay);
    }
    private static NbtCompound textComponent(String text) {
        return new NbtCompound().putString("type", "text").putString("text", text);
    }
    private static NbtCompound clickableComponent(String text, String action, String value) {
        String key = switch (action) {
            case "run_command", "suggest_command" -> "command";
            case "open_url" -> "url";
            default -> "value";
        };
        return textComponent(text).put("click_event", new NbtCompound()
                .putString("action", action).putString(key, value == null ? "" : value));
    }
    @Override public void write(PacketSerializer out) throws IOException {
        out.writeRawBytes(component.toNetworkBytes());
        out.writeBoolean(overlay);
    }
}
