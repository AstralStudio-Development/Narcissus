package cn.starry.narcissus;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.protocol.v777.PacketPlayOutJoinGame777;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class Protocol777JoinGameTest {
    @Test
    void official263JoinGameLayoutIncludesBothTrailingBooleans() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new PacketPlayOutJoinGame777(1, new String[]{"minecraft:the_end"}, 20,
                2, 2, true, 2, "minecraft:the_end", 2, 63).write(new PacketSerializer(bytes));
        PacketSerializer in = new PacketSerializer(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(1, in.readInt());
        assertFalse(in.readBoolean()); // hardcore
        assertEquals(1, in.readVarInt());
        assertEquals("minecraft:the_end", in.readString());
        assertEquals(20, in.readVarInt());
        assertEquals(2, in.readVarInt());
        assertEquals(2, in.readVarInt());
        assertTrue(in.readBoolean()); // reduced debug info
        assertTrue(in.readBoolean()); // show death screen
        assertFalse(in.readBoolean()); // limited crafting
        assertEquals(2, in.readVarInt()); // dimension type registry id
        assertEquals("minecraft:the_end", in.readString());
        assertEquals(0, in.readLong()); // hashed seed
        assertEquals(2, in.readVarInt()); // game mode
        assertEquals(0, in.readVarInt(), "26.3 encodes absent previous game mode as OPTIONAL_VAR_INT zero");
        assertFalse(in.readBoolean()); // debug
        assertFalse(in.readBoolean()); // flat
        assertFalse(in.readBoolean()); // last death location absent
        assertEquals(0, in.readVarInt()); // portal cooldown
        assertEquals(63, in.readVarInt()); // sea level
        assertFalse(in.readBoolean()); // online mode
        assertFalse(in.readBoolean()); // NEW: enforces secure chat
        assertEquals(0, in.available(), "26.3 client must consume the entire join packet");
    }

    @Test
    void joinGameCopiesWorldNamesRatherThanObservingLaterMutations() throws Exception {
        String[] worlds = {"minecraft:overworld", "minecraft:the_end"};
        PacketPlayOutJoinGame777 packet = new PacketPlayOutJoinGame777(1, worlds, 20,
                2, 2, false, 2, "minecraft:the_end", 2, 63);
        worlds[0] = "invalid";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        packet.write(new PacketSerializer(bytes));
        PacketSerializer in = new PacketSerializer(new ByteArrayInputStream(bytes.toByteArray()));
        in.readInt();
        in.readBoolean();
        assertEquals(2, in.readVarInt());
        assertEquals("minecraft:overworld", in.readString());
        assertEquals("minecraft:the_end", in.readString());
    }
}
