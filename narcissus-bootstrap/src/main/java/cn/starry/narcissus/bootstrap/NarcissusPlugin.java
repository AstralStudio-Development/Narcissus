package cn.starry.narcissus.bootstrap;

import cn.starry.narcissus.Narcissus;
import cn.starry.narcissus.functions.auth.AuthenticationProvider;
import cn.starry.narcissus.functions.auth.AuthenticationSessionHandler;
import cn.starry.narcissus.functions.config.Configuration;
import cn.starry.narcissus.functions.packets.PlayerListEntry;
import cn.starry.narcissus.util.SkinPropertyPayload;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.event.EventHandler;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * BungeeCord plugin entry point for the Narcissus limbo backend.
 *
 * There is no protocol-translation prerequisite (no ViaVersion/ViaBungee): the
 * Narcissus backend natively speaks every protocol it advertises. BungeeCord
 * forwards each player at their real client protocol number, and the backend
 * selects the matching native implementation. This does require the BungeeCord
 * build itself to understand the client protocol (the proxy passes protocol
 * numbers through unchanged), which is an operator-side prerequisite.
 */
public class NarcissusPlugin extends Plugin implements Listener {

    public static final String SERVER_NAME = "narcissus-limbo";

    private static final long STAFF_CODE_VALIDITY_MILLIS = 15L * 60L * 1000L;
    private static final char[] STAFF_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final SecureRandom random = new SecureRandom();
    private final Set<String> authenticatedSessions = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingLimboRoutes = ConcurrentHashMap.newKeySet();

    private Narcissus narcissus;
    private NarcissusConfig narcissusConfig;
    private AuthenticationProvider authenticationProvider;
    private ServerInfo limboServerInfo;
    private ServerInfo loginTargetServerInfo;
    private Command limboCommand;
    private Command lobbyCommand;
    private Command narcissusCommand;
    private ScheduledTask staffCodeTask;
    private volatile String staffRegistrationCode;
    private volatile long staffRegistrationCodeExpiresAt;

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
        this.refreshStaffRegistrationCode();
        try {
            this.authenticationProvider = new YamlAuthenticationProvider(
                    dataDir.resolve(config.getAccountsFile()),
                    dataDir.resolve(config.getStaffAccountFile()),
                    dataDir.resolve("totp.key"),
                    this::isValidStaffRegistrationCode);
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, "Failed to load authentication files", ex);
            return;
        }

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

        narcissus = new Narcissus(coreConfig, dataDir, this.authenticationProvider,
                new AuthenticationSessionHandler() {
                    @Override
                    public boolean isAuthenticated(String username) {
                        return isAuthenticatedSession(username);
                    }

                    @Override
                    public void onAuthenticated(String username) {
                        markAuthenticatedAndTransfer(username);
                    }
                });
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

        this.loginTargetServerInfo = ProxyServer.getInstance()
                .getServerInfo(config.getLoginTargetServer());
        if (this.loginTargetServerInfo == null
                || SERVER_NAME.equals(this.loginTargetServerInfo.getName())) {
            getLogger().severe("Configured login_target_server does not resolve to a valid non-Narcissus Bungee server: "
                    + config.getLoginTargetServer());
        } else {
            getLogger().info("Successful login target: " + this.loginTargetServerInfo.getName());
        }

        limboCommand = new LimboCommand(this);
        narcissusCommand = new NarcissusAdminCommand(this);
        lobbyCommand = new LobbyCommand(this);
        ProxyServer.getInstance().getPluginManager().registerCommand(this, limboCommand);
        ProxyServer.getInstance().getPluginManager().registerCommand(this, narcissusCommand);
        ProxyServer.getInstance().getPluginManager().registerCommand(this, lobbyCommand);
        staffCodeTask = ProxyServer.getInstance().getScheduler().schedule(
                this, this::refreshStaffRegistrationCode, 15, 15, TimeUnit.MINUTES);

        getLogger().info("Narcissus limbo server ready on port " + port
                + ". Native protocols: 47 (1.8.x), 774 (1.21.11), 776 (26.2, experimental).");
        getLogger().info("Allowed client protocols: " + config.getAllowedVersions());
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
    public void onPostLogin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();
        if (!isAuthenticatedSession(player.getName())) {
            connectToLimbo(player);
        }
    }

    @EventHandler
    public void onServerConnect(ServerConnectEvent event) {
        ProxiedPlayer player = event.getPlayer();
        ServerInfo target = event.getTarget();
        if (target == null) {
            return;
        }

        boolean toLimbo = SERVER_NAME.equals(target.getName());
        boolean authenticated = isAuthenticatedSession(player.getName());
        if (!toLimbo && !authenticated) {
            // Unauthenticated players are always forced into the Limbo server.
            event.setCancelled(true);
            connectToLimbo(player);
        }
        // Authenticated players may freely enter the Limbo server via /limbo;
        // the core recognises their session and does not re-prompt for login.
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
        String username = normalize(event.getPlayer().getName());
        this.authenticatedSessions.remove(username);
        this.pendingLimboRoutes.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Redirects players to the Limbo server instead of disconnecting them when
     * their current server goes down or kicks them (e.g. a backend shutdown).
     * The kick is cancelled and the player is sent to Limbo, preserving their
     * proxy connection and authenticated session.
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
        copyResourceIfAbsent(dataDir, "accounts.yml");
        copyResourceIfAbsent(dataDir, "staffAccount.yml");
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

    private void connectToLoginTarget(ProxiedPlayer player) {
        ServerInfo target = this.loginTargetServerInfo;
        if (target == null || SERVER_NAME.equals(target.getName())) {
            player.sendMessage(new TextComponent("§c登录目标服务器未配置或不存在，请联系管理员。"));
            return;
        }
        if (player.getServer() != null && target.getName().equals(player.getServer().getInfo().getName())) {
            return;
        }
        player.connect(target, (success, cause) -> {
            if (!success) {
                String reason = cause == null ? "unknown error" : cause.getMessage();
                player.sendMessage(new TextComponent("§c无法连接登录目标服务器：" + reason));
            }
        });
    }

    private void markAuthenticatedAndTransfer(String username) {
        String normalized = normalize(username);
        if (normalized.isEmpty()) {
            return;
        }
        this.authenticatedSessions.add(normalized);
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(username);
        if (player != null) {
            connectToLoginTarget(player);
        }
    }

    private boolean isAuthenticatedSession(String username) {
        return this.authenticatedSessions.contains(normalize(username));
    }

    private void refreshStaffRegistrationCode() {
        StringBuilder code = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            code.append(STAFF_CODE_ALPHABET[this.random.nextInt(STAFF_CODE_ALPHABET.length)]);
        }
        this.staffRegistrationCode = code.toString();
        this.staffRegistrationCodeExpiresAt = System.currentTimeMillis() + STAFF_CODE_VALIDITY_MILLIS;
        getLogger().info("Staff registration code refreshed; use /narcissus code in the Bungee console to view it.");
    }

    private boolean isValidStaffRegistrationCode(String code) {
        return code != null
                && System.currentTimeMillis() < this.staffRegistrationCodeExpiresAt
                && code.trim().equalsIgnoreCase(this.staffRegistrationCode);
    }

    private String getStaffRegistrationCode() {
        if (System.currentTimeMillis() >= this.staffRegistrationCodeExpiresAt) {
            this.refreshStaffRegistrationCode();
        }
        return this.staffRegistrationCode;
    }

    private long getStaffRegistrationCodeRemainingMinutes() {
        long remaining = Math.max(0L, this.staffRegistrationCodeExpiresAt - System.currentTimeMillis());
        return (remaining + 59_999L) / 60_000L;
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void rollback() {
        ProxyServer.getInstance().getPluginManager().unregisterListener(this);
        if (this.staffCodeTask != null) {
            this.staffCodeTask.cancel();
            this.staffCodeTask = null;
        }
        this.authenticatedSessions.clear();
        this.pendingLimboRoutes.clear();
        if (limboCommand != null) {
            ProxyServer.getInstance().getPluginManager().unregisterCommand(limboCommand);
            limboCommand = null;
        }
        if (lobbyCommand != null) {
            ProxyServer.getInstance().getPluginManager().unregisterCommand(lobbyCommand);
            lobbyCommand = null;
        }
        if (narcissusCommand != null) {
            ProxyServer.getInstance().getPluginManager().unregisterCommand(narcissusCommand);
            narcissusCommand = null;
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
     * Returns an authenticated player to the configured login target server.
     * Registered at the proxy so it works on every backend server (Limbo
     * included) regardless of the connected game server.
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
            if (!plugin.isAuthenticatedSession(player.getName())) {
                player.sendMessage(new TextComponent("§c请先完成登录后再返回大厅。"));
                return;
            }
            ServerInfo targetServer = plugin.loginTargetServerInfo;
            if (targetServer == null || SERVER_NAME.equals(targetServer.getName())) {
                player.sendMessage(new TextComponent("§c登录目标服务器未配置或不存在，请联系管理员。"));
                return;
            }
            if (player.getServer() != null
                    && targetServer.getName().equals(player.getServer().getInfo().getName())) {
                player.sendMessage(new TextComponent("§e你已经在大厅服务器。"));
                return;
            }
            plugin.connectToLoginTarget(player);
        }
    }

    private static final class NarcissusAdminCommand extends Command {
        private final NarcissusPlugin plugin;

        NarcissusAdminCommand(NarcissusPlugin plugin) {
            super("narcissus");
            this.plugin = plugin;
        }

        @Override
        public void execute(net.md_5.bungee.api.CommandSender sender, String[] args) {
            if (sender instanceof ProxiedPlayer) {
                sender.sendMessage(new TextComponent("§c该命令只能在 BungeeCord 控制台执行。"));
                return;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("code")) {
                sender.sendMessage(new TextComponent("§aStaff 注册码：§e" + plugin.getStaffRegistrationCode()));
                sender.sendMessage(new TextComponent("§7剩余有效时间约：" + plugin.getStaffRegistrationCodeRemainingMinutes() + " 分钟"));
                return;
            }
            sender.sendMessage(new TextComponent("§e用法：/narcissus code"));
        }
    }
}
