package cn.starry.narcissus.functions.packets.in.protocol.v774;

import cn.starry.narcissus.functions.commands.CommandManager;
import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.in.PacketIn;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutCommandSuggestions774;

import java.io.IOException;

/** Serverbound command_suggestion request for protocol 774/776. */
public class PacketPlayInCommandSuggestion774 implements PacketIn {

    private int transactionId;
    private String command;

    @Override
    public void readPacket(PacketSerializer packetSerializer) throws IOException {
        this.transactionId = packetSerializer.readVarInt();
        this.command = packetSerializer.readString();
    }

    @Override
    public void handlePacket(NetworkManager networkManager, PlayerManager playerManager) {
        CommandManager.Suggestion suggestion = networkManager.getCommandManager().suggest(this.command);
        networkManager.sendPacket(playerManager, new PacketPlayOutCommandSuggestions774(
                this.transactionId,
                suggestion.getStart(),
                suggestion.getLength(),
                suggestion.getMatches()));
    }

    public int getTransactionId() {
        return this.transactionId;
    }

    public String getCommand() {
        return this.command;
    }
}
