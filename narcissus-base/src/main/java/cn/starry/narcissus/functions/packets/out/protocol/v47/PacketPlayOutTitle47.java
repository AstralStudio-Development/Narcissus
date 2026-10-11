package cn.starry.narcissus.functions.packets.out.protocol.v47;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;

import java.io.IOException;

/**
 * 1.8 Title (0x45): VarInt action (0 set title, 1 subtitle, 2 times, 3 hide,
 * 4 reset) followed by the action payload.
 */
public final class PacketPlayOutTitle47 implements PacketOut {
    public static final int SET_TITLE = 0;
    public static final int SET_SUBTITLE = 1;
    public static final int SET_TIMES = 2;
    public static final int HIDE = 3;
    public static final int RESET = 4;

    private final int action;
    private final String json;
    private final int fadeIn;
    private final int stay;
    private final int fadeOut;

    public PacketPlayOutTitle47(int action, String json) {
        this(action, json, 0, 0, 0);
    }

    public PacketPlayOutTitle47(int action, String json, int fadeIn, int stay, int fadeOut) {
        this.action = action;
        this.json = json;
        this.fadeIn = fadeIn;
        this.stay = stay;
        this.fadeOut = fadeOut;
    }

    @Override
    public void write(PacketSerializer out) throws IOException {
        out.writeVarInt(this.action);
        switch (this.action) {
            case SET_TITLE:
            case SET_SUBTITLE:
                out.writeString(this.json);
                break;
            case SET_TIMES:
                out.writeInt(this.fadeIn);
                out.writeInt(this.stay);
                out.writeInt(this.fadeOut);
                break;
            case HIDE:
            case RESET:
            default:
                break;
        }
    }
}
