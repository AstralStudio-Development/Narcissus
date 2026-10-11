package cn.starry.narcissus.functions.packets.out;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.world.SignTextUtil;
import cn.starry.narcissus.functions.world.SignTileEntity;
import java.io.IOException;

/** Legacy Update Sign, including its pre-1.14 X/Y/Z packed position. */
public final class PacketPlayOutUpdateSign implements PacketOut {
    private final long position;
    private final String[] lines;

    public PacketPlayOutUpdateSign(SignTileEntity sign) {
        this(sign, "");
    }

    public PacketPlayOutUpdateSign(SignTileEntity sign, String version) {
        var pos = sign.getBlockPosition();
        this.position = ((long) (pos.getX() & 0x3ffffff) << 38)
                | ((long) (pos.getY() & 0xfff) << 26) | (pos.getZ() & 0x3ffffffL);
        this.lines = new String[]{sign.getText1(), sign.getText2(), sign.getText3(), sign.getText4()};
        for (int i = 0; i < this.lines.length; i++) {
            this.lines[i] = SignTextUtil.replaceVersionText(this.lines[i], version);
        }
    }

    @Override
    public void write(PacketSerializer out) throws IOException {
        out.writeLong(this.position);
        for (String line : this.lines) {
            String text = line == null ? "" : line;
            String trimmed = text.trim();
            boolean component = trimmed.startsWith("{\"") || trimmed.startsWith("[") || trimmed.startsWith("\"");
            out.writeString(component ? text : cn.starry.narcissus.util.TextUtil.toJsonComponent(text));
        }
    }
}
