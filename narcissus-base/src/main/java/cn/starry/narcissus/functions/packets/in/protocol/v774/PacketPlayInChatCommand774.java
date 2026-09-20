package cn.starry.narcissus.functions.packets.in.protocol.v774;

import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.in.PacketIn;

import java.io.IOException;

/**
 * Serverbound chat_command/chat_command_signed for protocol 774+.
 *
 * <p>The command text is the first field in both packet shapes. Signed
 * command metadata, when present, is deliberately ignored because the frame
 * has already been isolated by NetworkManager and Narcissus does not perform
 * chat-signature verification.</p>
 */
public class PacketPlayInChatCommand774 implements PacketIn {

    private String command;

    @Override
    public void readPacket(PacketSerializer packetSerializer) throws IOException {
        this.command = packetSerializer.readString();
    }

    @Override
    public void handlePacket(NetworkManager networkManager, PlayerManager playerManager) {
        String input = this.command == null ? "" : this.command.trim();
        if (!input.startsWith("/")) {
            input = "/" + input;
        }
        networkManager.getCommandManager().handleChat(networkManager, playerManager, input);
    }

    public String getCommand() {
        return this.command;
    }
}
