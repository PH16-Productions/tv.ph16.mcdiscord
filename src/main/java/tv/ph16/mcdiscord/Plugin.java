package tv.ph16.mcdiscord;

import tv.ph16.discord.AccessToken;
import tv.ph16.discord.Client;
import tv.ph16.discord.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * The Discord Authentication Plugin.
 */
public final class Plugin extends JavaPlugin implements Listener {
    private StateManager stateManager;
    private AuthenticationServer authenticationServer;
    private Client discordClient;
    private AuthenticationManager authenticationManager;

    /**
     * Initializes a new instance of the Plugin class.
     */
    public Plugin() {
        stateManager = new StateManager(this);
        authenticationManager = new AuthenticationManager(this, stateManager);
    }

    @Override
    public void onEnable() {
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(this, this);

        String clientId = stateManager.getClientId();
        String clientSecret = stateManager.getClientSecret();
        String scopes = stateManager.getScopes();
        String callback = stateManager.getCallback();
        if (clientId == null || clientSecret == null || scopes == null || callback == null) {
            getLogger().severe("Missing configuration");
            return;
        }
        discordClient = new Client(clientId, clientSecret, scopes, callback);
        authenticationServer = AuthenticationServer.create(stateManager, discordClient, this, pluginManager, authenticationManager);
    }

    @Override
    public void onLoad() {
        stateManager.onLoad();
    }

    /**
     * Set a player to have their Discord name.
     * @param player
     */
    protected void setUserNameFromDiscord(@NotNull Player player) {
        AccessToken token = authenticationManager.getUserToken(player, discordClient);
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
     * Check players authentication state when they join the server.
     * @param event
     */
    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Check if user needs discord authentication.
        Boolean explicitAuth = authenticationManager.getUserExplicitlyAllowedOrBanned(player);
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

        AccessToken token = authenticationManager.getUserToken(player, discordClient);
        if (token == null) {
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
        } else if (!authenticationManager.getUserInRequiredGuild(token, discordClient)) {
            logPlayerAction(player, "is not part of PH16.");
            kickPlayer(player, "To join this server you must be part of the PH16 Discord Server.");
        } else {
            logPlayerAction(player, "authenticated successfully with Discord");
            setUserNameFromDiscord(player);
            player.customName(null);
        }
    }

    /**
     * Kicks a player of the server with a log.
     * @param player The player to kick.
     * @param msg The reason.
     */
    protected void kickPlayer(@NotNull Player player, @NotNull String msg) {
        logPlayerAction(player, "kicked: " + msg);
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> player.kick(Component.text(msg)));
    }

    /**
     * Log an action related to a player.
     * @param player The related player.
     * @param msg The message.
     */
    protected void logPlayerAction(@NotNull Player player, @NotNull String msg) {
        getLogger().info(() -> "Player " + player.getName() + " " + msg);
    }
}
