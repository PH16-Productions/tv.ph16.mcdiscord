package tv.ph16.mcdiscord;

import tv.ph16.discord.AccessToken;
import tv.ph16.discord.Client;
import tv.ph16.discord.PartialGuild;
import tv.ph16.discord.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;

public final class Plugin extends JavaPlugin implements Listener {
    private StateManager stateManager;
    private AuthenticationServer authenticationServer;
    private Client discordClient;

    public Plugin() {
        stateManager = new StateManager(this);
    }

    @Override
    public void onEnable() {
        String clientId = stateManager.getClientId();
        String clientSecret = stateManager.getClientSecret();
        String scopes = stateManager.getScopes();
        String callback = stateManager.getCallback();
        if (clientId == null || clientSecret == null || scopes == null || callback == null) {
            getLogger().severe("Missing configuration");
            return;
        }
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(this, this);
        discordClient = new Client(clientId, clientSecret, scopes, callback);
        authenticationServer = AuthenticationServer.create(stateManager, discordClient, this, pluginManager);
    }

    @Override
    public void onLoad() {
        stateManager.onLoad();
    }

    protected boolean userAllowed(@NotNull Player player) {
        AccessToken token = stateManager.getUserTokens(player, discordClient);
        if (token == null) {
            return false;
        }

        Optional<PartialGuild> ph16Guild = Optional.empty();
        try {
            Optional<List<PartialGuild>> partialGuilds = discordClient.getCurrentUsersGuilds(token);
            if (partialGuilds.isPresent()) {
                ph16Guild = partialGuilds.get().stream().filter(pg -> pg.getName().equalsIgnoreCase(getConfig().getString("guildName"))).findFirst();
            }
        } catch (IOException | InterruptedException | ExecutionException ex) {
            getLogger().severe("Fetch Guilds Error:\n" + ex);
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return ph16Guild.isPresent();
    }

    /**
     * Set a player to have their Discord name.
     * @param player
     */
    protected void setUserNameFromDiscord(@NotNull Player player) {
        AccessToken token = stateManager.getUserTokens(player, discordClient);
        if (token != null) {
            try {
                Optional<User> userOptional = discordClient.getCurrentUser(token);
                if (userOptional.isPresent()) {
                    User user = userOptional.get();
                    player.playerListName(Component.text(user.getUsername()));
                }
            } catch (IOException | InterruptedException | ExecutionException ex) {
                getLogger().severe("Fetch User Error:\n" + ex);
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Prevents user from changing out of spectater mode if not allowed.
     * @param event
     */
    @EventHandler
    public void onGameModeChange(@NotNull PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (player.customName() != null && event.getNewGameMode() != GameMode.SPECTATOR) {
            event.setCancelled(true);
            player.setGameMode(GameMode.SPECTATOR);
        }
    }

    /**
     * Prevents user login if Discord or HTTP Server are not operational.
     * @param event
     */
    @EventHandler
    public void onLogin(@NotNull PlayerLoginEvent event) {
        if (discordClient == null || authenticationServer == null) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, Component.text("Server Not Ready"));
        }
    }

    /**
     * Checks if a player should be explicitly allowed or explicitly banned.
     * @param player The player to check for.
     * @return True if the player is explicitly allowed, False if the player is explicitly banned, or null if the player uses Discord authentication.
     */
    @Nullable
    private Boolean explicitAuthentication(@NotNull Player player) {
        Server server = this.getServer();
        for (OfflinePlayer bannedPlayer : server.getBannedPlayers()) {
            if (player.getUniqueId().equals(bannedPlayer.getUniqueId())) {
                return Boolean.FALSE;
            }
        }
        for (OfflinePlayer allowedPlayer : server.getWhitelistedPlayers()) {
            if (player.getUniqueId().equals(allowedPlayer.getUniqueId())) {
                return Boolean.TRUE;
            }
        }
        return null;
    }

    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Check if user needs discord authentication.
        Boolean explicitAuth = explicitAuthentication(player);
        if (explicitAuth != null) {
            if (explicitAuth.booleanValue()) {
                // User explicitly allowed.
                return;
            } else {
                // User explicitly banned.
                kickPlayer(player, "User Banned");
                return;
            }
        }

        if (!stateManager.getIsUserApproved(player)) {
            logPlayerAction(player, "has not linked with Discord.");
            player.customName(Component.text(player.getGameMode().name()));
            player.setGameMode(GameMode.SPECTATOR);
            String url = discordClient.getAuthorizationUrl(player.getUniqueId().toString());
            logPlayerAction(player, "can authenticate with url: " + url);
            player.showTitle(Title.title(Component.text("Please open chat (t) and login with Discord."), Component.text("")));
            player.sendMessage(
                Component.text("To user the server, please ").
                append(
                    Component.text("login with Discord (Click On This)").
                    style(
                        Style.style().
                        decorate(TextDecoration.BOLD, TextDecoration.UNDERLINED).
                        clickEvent(ClickEvent.openUrl(url)))));
        } else if (!userAllowed(player)) {
            logPlayerAction(player, "is not part of PH16.");
            kickPlayer(player, "To join this server you must be part of the PH16 Discord Server.");
        } else {
            logPlayerAction(player, "authenticated successfully with Discord");
            setUserNameFromDiscord(player);
            player.customName(null);
        }
    }

    protected void kickPlayer(@NotNull Player player, @NotNull String msg) {
        logPlayerAction(player, "kicked: " + msg);
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> player.kick(Component.text(msg)));
    }

    protected void logPlayerAction(@NotNull Player player, @NotNull String msg) {
        getLogger().info(() -> "Player " + player.getName() + " " + msg);
    }
}
