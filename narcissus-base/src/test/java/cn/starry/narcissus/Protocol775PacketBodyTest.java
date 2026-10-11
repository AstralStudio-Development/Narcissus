package cn.starry.narcissus;

import cn.starry.narcissus.functions.config.Configuration;
import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.Status;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import cn.starry.narcissus.functions.packets.out.protocol.v774.*;
import cn.starry.narcissus.functions.packets.out.protocol.v776.PacketPlayOutSetTime776;
import cn.starry.narcissus.functions.packets.protocols.ModernProtocol;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Mojang 26.1 codecs reject the extra login/session fields introduced in 26.2. */
class Protocol775PacketBodyTest {
    @Test void protocol775UsesOlderLoginJoinAndTeamLayoutsAtItsOwnIds() throws Exception {
        var backend = new NetworkManager(null, Configuration.builder().build()).getProtocolByVersionId(775);
        var protocol = (ModernProtocol) backend;
        UUID uuid = new UUID(0, 1);
        PacketOut login = protocol.createLoginSuccess(uuid, "Steve");
        assertInstanceOf(PacketLoginOutSuccess774.class, login);
        assertArrayEquals(body(new PacketLoginOutSuccess774(uuid, "Steve")), body(login));
        assertEquals(23, body(login).length); // UUID + username + zero properties, no session UUID
        PacketOut join = protocol.createJoinGame(1, new String[]{"minecraft:the_end"}, 20, 2, 2,
                false, 2, "minecraft:the_end", 1, 63);
        assertInstanceOf(PacketPlayOutJoinGame774.class, join);
        assertEquals(0x31, backend.getPacketOutByClass(join.getClass(), Status.PLAY));
        PacketOut team = protocol.createHiddenNameTagTeam("nar_hidden", "NPC");
        assertInstanceOf(PacketPlayOutSetPlayerTeam774.class, team);
        assertEquals(0x6d, backend.getPacketOutByClass(team.getClass(), Status.PLAY));
        assertArrayEquals(body(new PacketPlayOutSetPlayerTeam774("nar_hidden", "NPC")), body(team));
        assertInstanceOf(PacketPlayOutSetTime776.class, protocol.createSetTime(1, 2, 3));
        assertTrue(protocol.hasSectionBlockCountMarker());
    }
    private static byte[] body(PacketOut packet) throws Exception {
        var bytes = new ByteArrayOutputStream();
        packet.write(new PacketSerializer(bytes));
        return bytes.toByteArray();
    }
}
