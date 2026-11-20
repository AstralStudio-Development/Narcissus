package cn.starry.narcissus.bootstrap;

import cn.starry.narcissus.Narcissus;
import cn.starry.narcissus.functions.config.Configuration;
import cn.starry.narcissus.functions.packets.PlayerListEntry;
import cn.starry.narcissus.util.SkinPropertyPayload;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * BungeeCord plugin entry point for the Narcissus limbo backend.
 *
 * <p>Narcissus is a pure fallback server: players are routed by BungeeCord's own
 * default-server rules on join, and only land in Limbo when their current server
 * kicks them or when they run {@code /limbo} explicitly. There is no account,
 * password or 2FA layer, and joining the proxy never redirects to a separate
 * login server.</p>
 *
 * <p>There is no protocol-translation prerequisite (no ViaVersion/ViaBungee): the
 * Narcissus backend natively speaks every protocol it advertises. BungeeCord
 * forwards each player at their real client protocol number, and the backend
 * selects the matching native implementation. This does require the BungeeCord
 * build itself to understand the client protocol (the proxy passes protocol
 * numbers through unchanged), which is an operator-side prerequisite.</p>
 */
public class NarcissusPlugin extends Plugin implements Listener {

    public static final String SERVER_NAME = "narcissus-limbo";

    private final Set<UUID> pendingLimboRoutes = ConcurrentHashMap.newKeySet();

    private Narcissus narcissus;
    private NarcissusConfig narcissusConfig;
    private ServerInfo limboServerInfo;
    private ServerInfo lobbyServerInfo;
    private Command limboCommand;
    private Command lobbyCommand;

    @Override
    public void onEnable() {
        Path dataDir = getDataFolder().toPath();
        try {
            setupDataFolder(dataDir);
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, "Failed to set up data folder", ex);
            return;
        }

        NarcissusConfig config;
        try {
            config = NarcissusConfig.load(dataDir.resolve("narcissus.yml"));
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Failed to load narcissus.yml", ex);
            return;
        }
        this.narcissusConfig = config;

        int port = config.getPort();
        Configuration coreConfig = Configuration.builder()
                .ip("127.0.0.1")
                .port(port)
                .schematicFile(config.getSchematicFile())
                .dimension(config.getDimension())
                .gameMode(config.getGameMode())
                .maxSlots(config.getMaxSlots())
                .allowedVersions(config.getAllowedVersions())
                .spawn(config.getSpawnX(), config.getSpawnY(), config.getSpawnZ(),
                        config.getSpawnYaw(), config.getSpawnPitch())
                .build();

        narcissus = new Narcissus(coreConfig, dataDir);
        try {
            narcissus.start();
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Failed to start Narcissus", ex);
            narcissus = null;
            return;
        }

        ProxyServer.getInstance().getPluginManager().registerListener(this, this);

        limboServerInfo = ProxyServer.getInstance().constructServerInfo(
                SERVER_NAME, new InetSocketAddress("127.0.0.1", port), "Narcissus Limbo", false);
        ProxyServer.getInstance().getServers().put(SERVER_NAME, limboServerInfo);
        getLogger().info("Registered server " + SERVER_NAME + " at 127.0.0.1:" + port);

        this.lobbyServerInfo = ProxyServer.getInstance()
                .getServerInfo(config.getLobbyServer());
        if (this.lobbyServerInfo == null
                || SERVER_NAME.equals(this.lobbyServerInfo.getName())) {
            getLogger().severe("Configured lobby_server does not resolve to a valid non-Narcissus Bungee server: "
                    + config.getLobbyServer());
        } else {
            getLogger().info("Lobby target: " + this.lobbyServerInfo.getName());
        }

        limboCommand = new LimboCommand(this);
        lobbyCommand = new LobbyCommand(this);
        ProxyServer.getInstance().getPluginManager().registerCommand(this, limboCommand);
        ProxyServer.getInstance().getPluginManager().registerCommand(this, lobbyCommand);

        getLogger().info("Narcissus limbo server ready on port " + port
                + ". Native protocols: 47 (1.8.x), 774 (1.21.11), "
                + "775 (26.1 / 26.1.1 / 26.1.2), 776 (26.2), 777 (26.3). "
                + "26.x adapters still require real-client end-to-end validation.");
        getLogger().info("Allowed client protocols: " + config.getAllowedVersions());
        getLogger().info("Login is disabled: players keep BungeeCord's normal join routing and only "
                + "reach Limbo through /limbo or a server-kick fallback.");
        getLogger().info("Note: players must be able to reach this BungeeCord build with their client "
                + "protocol; the proxy forwards protocol numbers unchanged (no translation).");
    }

    @Override
    public void onDisable() {
        rollback();
    }

    @EventHandler
    public void onPreLogin(PreLoginEvent event) {
        NarcissusConfig config = this.narcissusConfig;
        if (config == null)
            return;

        int protocol = event.getConnection().getVersion();
        if (!config.getAllowedVersions().contains(protocol)) {
            event.setCancelled(true);
            event.setCancelReason("§c此服务器不允许你的客户端版本（协议 " + protocol
                    + "）。允许的协议版本：" + config.getAllowedVersions());
        }
    }

    @EventHandler
    public void onServerConnected(ServerConnectedEvent event) {
        if (event.getServer() == null || event.getServer().getInfo() == null
                || !SERVER_NAME.equals(event.getServer().getInfo().getName())) {
            return;
        }

        Optional<PlayerListEntry.ProfileProperty> property =
                BungeeSkinProfileReader.read(event.getPlayer());
        if (property.isEmpty()) {
            getLogger().fine("No textures property found for " + event.getPlayer().getName());
            return;
        }

        try {
            event.getServer().sendData(SkinPropertyPayload.CHANNEL,
                    SkinPropertyPayload.encode(property.get()));
            getLogger().fine("Forwarded textures property for " + event.getPlayer().getName());
        } catch (IOException ex) {
            getLogger().log(Level.FINE, "Failed to forward textures property", ex);
        }
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        this.pendingLimboRoutes.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Redirects players to the Limbo server instead of disconnecting them when
     * their current server goes down or kicks them (e.g. a backend shutdown).
     * The kick is cancelled and the player is sent to Limbo, preserving their
     * proxy connection.
     */
    @EventHandler
    public void onServerKick(ServerKickEvent event) {
        ServerInfo fallback = this.limboServerInfo;
        if (fallback == null) {
            return;
        }
        // If the Limbo server itself kicked the player, there is nowhere else
        // to send them; leave the default disconnect behaviour untouched.
        if (event.getKickedFrom() != null
                && SERVER_NAME.equals(event.getKickedFrom().getName())) {
            return;
        }
        event.setCancelled(true);
        event.setCancelServer(fallback);
        event.getPlayer().sendMessage(
                new TextComponent("§e你所在的服务器已关闭，已将你重定向至 Limbo。"));
    }

    private void setupDataFolder(Path dataDir) throws IOException {
        Files.createDirectories(dataDir);
        Files.createDirectories(dataDir.resolve("languages"));
        copyResourceIfAbsent(dataDir, "narcissus.yml");
        copyResourceIfAbsent(dataDir, "world.schematic");
        copyResourceIfAbsent(dataDir.resolve("languages"), "en_US.yml");
        copyResourceIfAbsent(dataDir.resolve("languages"), "zh_CN.yml");
    }

    private void copyResourceIfAbsent(Path destDir, String name) throws IOException {
        Path dest = destDir.resolve(name);
        if (Files.exists(dest)) return;
        InputStream in = getClass().getClassLoader().getResourceAsStream(name);
        if (in == null) in = getClass().getClassLoader().getResourceAsStream("languages/" + name);
        if (in == null) {
            getLogger().warning("Resource not found: " + name);
            return;
        }
        try (InputStream s = in) {
            Files.copy(s, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void connectToLimbo(ProxiedPlayer player) {
        ServerInfo target = this.limboServerInfo;
        if (target == null) {
            player.sendMessage(new TextComponent("§cNarcissus Limbo 当前不可用。"));
            return;
        }
        if (player.getServer() != null && SERVER_NAME.equals(player.getServer().getInfo().getName())) {
            return;
        }
        if (!this.pendingLimboRoutes.add(player.getUniqueId())) {
            return;
        }
        player.connect(target, (success, cause) -> {
            this.pendingLimboRoutes.remove(player.getUniqueId());
            if (!success) {
                String reason = cause == null ? "unknown error" : cause.getMessage();
                player.sendMessage(new TextComponent("§c无法连接 Narcissus Limbo：" + reason));
            }
        });
    }

    private void connectToLobby(ProxiedPlayer player) {
        ServerInfo target = this.lobbyServerInfo;
        if (target == null || SERVER_NAME.equals(target.getName())) {
            player.sendMessage(new TextComponent("§c大厅服务器未配置或不存在，请联系管理员。"));
            return;
        }
        if (player.getServer() != null && target.getName().equals(player.getServer().getInfo().getName())) {
            return;
        }
        player.connect(target, (success, cause) -> {
            if (!success) {
                String reason = cause == null ? "unknown error" : cause.getMessage();
                player.sendMessage(new TextComponent("§c无法连接大厅服务器：" + reason));
            }
        });
    }

    private void rollback() {
        ProxyServer.getInstance().getPluginManager().unregisterListener(this);
        this.pendingLimboRoutes.clear();
        if (limboCommand != null) {
            ProxyServer.getInstance().getPluginManager().unregisterCommand(limboCommand);
            limboCommand = null;
        }
        if (lobbyCommand != null) {
            ProxyServer.getInstance().getPluginManager().unregisterCommand(lobbyCommand);
            lobbyCommand = null;
        }
        if (limboServerInfo != null) {
            ProxyServer.getInstance().getServers().remove(SERVER_NAME, limboServerInfo);
            limboServerInfo = null;
            getLogger().info("Removed server " + SERVER_NAME + " from BungeeCord");
        }
        if (narcissus != null && narcissus.isRunning()) {
            try {
                narcissus.stop();
            } catch (Exception ex) {
                getLogger().log(Level.WARNING, "Error stopping Narcissus", ex);
            }
            narcissus = null;
        }
    }

    private static final class LimboCommand extends Command {
        private final NarcissusPlugin plugin;

        LimboCommand(NarcissusPlugin plugin) {
            super("limbo");
            this.plugin = plugin;
        }

        @Override
        public void execute(net.md_5.bungee.api.CommandSender sender, String[] args) {
            if (!(sender instanceof ProxiedPlayer player)) {
                sender.sendMessage(new TextComponent("§cThis command can only be used by players."));
                return;
            }
            ServerInfo targetServer = plugin.limboServerInfo;
            if (targetServer == null) {
                player.sendMessage(new TextComponent("§cThe limbo server is not available."));
                return;
            }
            if (player.getServer() != null && SERVER_NAME.equals(player.getServer().getInfo().getName())) {
                player.sendMessage(new TextComponent("§eYou are already in the limbo server."));
                return;
            }
            plugin.connectToLimbo(player);
        }
    }

    /**
     * Returns a player from Limbo to the configured lobby server. Registered at
     * the proxy so it works on every backend server regardless of the connected
     * game server.
     */
    private static final class LobbyCommand extends Command {
        private final NarcissusPlugin plugin;

        LobbyCommand(NarcissusPlugin plugin) {
            super("lobby", null, "hub");
            this.plugin = plugin;
        }

        @Override
        public void execute(net.md_5.bungee.api.CommandSender sender, String[] args) {
            if (!(sender instanceof ProxiedPlayer player)) {
                sender.sendMessage(new TextComponent("§cThis command can only be used by players."));
                return;
            }
            ServerInfo targetServer = plugin.lobbyServerInfo;
            if (targetServer == null || SERVER_NAME.equals(targetServer.getName())) {
                player.sendMessage(new TextComponent("§c大厅服务器未配置或不存在，请联系管理员。"));
                return;
            }
            if (player.getServer() != null
                    && targetServer.getName().equals(player.getServer().getInfo().getName())) {
                player.sendMessage(new TextComponent("§e你已经在大厅服务器。"));
                return;
            }
            plugin.connectToLobby(player);
        }
    }
}
