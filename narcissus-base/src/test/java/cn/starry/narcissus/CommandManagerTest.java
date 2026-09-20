package cn.starry.narcissus;

import cn.starry.narcissus.functions.commands.CommandManager;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandManagerTest {

    private final CommandManager commandManager = new CommandManager();

    @Test
    void limboCommandMatchesLegacyOutput() {
        List<String> messages = this.commandManager.dispatch("/Limbo");

        assertEquals(12, messages.size());
        assertEquals(Arrays.asList(
                " ",
                " ",
                " ",
                " ",
                " ",
                " ",
                " ",
                "&c你正在尝试加入的大厅人数已满或处于离线状态。",
                "&e因此，你来到了Limbo，一个存在你想象中的服务器。",
                "&d事实上，这个地方并不存在，所以你可以想呆多久就呆多久。",
                "&6想要返回“现实”，请输入 &b/lobby&6。",
                "&4不过，要小心，Limbo好像居住着什么东西。"
        ), messages);
    }

    @Test
    void versionCommandIsCaseInsensitiveAndTrimmed() {
        assertEquals(Arrays.asList(
                "&bNarcissus &f- " + Narcissus.VERSION,
                "&bPowered By Stalyer"
        ), this.commandManager.dispatch("  /vErSiOn  "));
    }

    @Test
    void unknownAndPlainChatKeepLegacyFeedback() {
        assertEquals(Arrays.asList("&fUnknown command."), this.commandManager.dispatch("/does_not_exist"));
        assertEquals(Arrays.asList("&c通信功能在此服务器上不可用！"), this.commandManager.dispatch("hello"));
        assertEquals(Arrays.asList("&fUnknown command."), this.commandManager.dispatch("/"));
    }

    @Test
    void builtInCommandsRejectUnexpectedArguments() {
        assertEquals(Arrays.asList("&fUnknown command."), this.commandManager.dispatch("/version extra"));
        assertEquals(Arrays.asList("&fUnknown command."), this.commandManager.dispatch("/limbo extra"));
        assertEquals(Arrays.asList("&e用法：/time <HH:mm|0-23999|sync>"),
                this.commandManager.dispatch("/time"));
    }

    @Test
    void suggestionsUseCommandTokenRange() {
        assertEquals(Arrays.asList("limbo", "version", "time", "register", "login", "2fa", "lobby", "hub"),
                this.commandManager.getCommandLabels());

        CommandManager.Suggestion root = this.commandManager.suggest("/");
        assertEquals(1, root.getStart());
        assertEquals(0, root.getLength());
        assertEquals(Arrays.asList("limbo", "version", "time", "register", "login", "2fa", "lobby", "hub"), root.getMatches());

        CommandManager.Suggestion partial = this.commandManager.suggest("/ver");
        assertEquals(1, partial.getStart());
        assertEquals(3, partial.getLength());
        assertEquals(Arrays.asList("version"), partial.getMatches());

        assertEquals(Arrays.asList("/limbo", "/login", "/lobby"), this.commandManager.suggestLegacy("/l"));
    }
}
