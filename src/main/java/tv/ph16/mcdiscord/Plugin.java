package tv.ph16.mcdiscord;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import tv.ph16.discord.AccessToken;
import tv.ph16.discord.Client;
import tv.ph16.discord.PartialGuild;
import tv.ph16.discord.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

import org.apache.commons.codec.Charsets;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
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
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;

public final class Plugin extends JavaPlugin implements Listener, HttpHandler {
    private HttpContext httpContext;
    private Client discordClient;

    @Override
    public void onEnable() {
        String clientId = getConfig().getString("clientId");
        String clientSecret = getConfig().getString("clientSecret");
        String scopes = getConfig().getString("scopes");
        String callback = getConfig().getString("callback");
        if (clientId == null || clientSecret == null || scopes == null || callback == null) {
            getLogger().severe("Missing configuration");
            return;
        }
        discordClient = new Client(clientId, clientSecret, scopes, callback);
        PluginManager pluginManager = getServer().getPluginManager();
        org.bukkit.plugin.Plugin webServerPlugin = pluginManager.getPlugin("Bukkit-Web-Server");
        if (webServerPlugin instanceof tv.ph16.bukkitwebserver.Plugin) {
            pluginManager.registerEvents(this, this);
            tv.ph16.bukkitwebserver.Plugin webServer = (tv.ph16.bukkitwebserver.Plugin)webServerPlugin;
            httpContext = webServer.addHandler("/discord/", this);
        } else {
            getLogger().severe(tv.ph16.bukkitwebserver.Plugin.class.getCanonicalName() + " is required to run this");
        }
    }

    @Override
    public void onLoad() {
        ConfigurationSerialization.registerClass(AccessToken.class);
    }

    public boolean getIsUserApproved(@NotNull Player player) {
        return getConfig().contains(player.getUniqueId().toString(), true);
    }

    public void setUserTokens(@NotNull Player player, @NotNull AccessToken tokens) {
        getConfig().set(player.getUniqueId().toString(), tokens);
        saveConfig();
    }

    @Nullable
    public AccessToken getUserTokens(@NotNull Player player) {
        AccessToken token = getConfig().getObject(player.getUniqueId().toString(), AccessToken.class);
        if (token != null) {
            try {
                token = discordClient.refreshAccessToken(token);
                setUserTokens(player, token);
            } catch (IOException | InterruptedException | ExecutionException ex) {
                getLogger().severe("Fetch Guilds Error:\n" + ex);
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return token;
    }

    /**
     * Handle the given request and generate an appropriate response.
     * See {@link HttpExchange} for a description of the steps
     * involved in handling an exchange.
     *
     * @param httpExchange the exchange containing the request from the
     *                 client and used to send the response
     * @throws NullPointerException if exchange is <code>null</code>
     */
    @Override
    public void handle(@NotNull HttpExchange httpExchange) throws IOException {
        Headers responseHeaders = httpExchange.getResponseHeaders();
        responseHeaders.add("X-Powered-By", getDescription().getFullName());
        responseHeaders.add("Tk", "N");
        //noinspection SpellCheckingInspection
        responseHeaders.add("X-Content-Type-Options", "nosniff");
        if (!httpExchange.getRequestMethod().equalsIgnoreCase("GET")) {
            responseHeaders.add("Allow", "GET");
            httpExchange.sendResponseHeaders(405, -1);
            return;
        }
        URI uri = httpExchange.getRequestURI();
        Path path = Paths.get(uri.getPath());
        Map<String, String> params = getParams(uri);
        if (path.getNameCount() == 2 && path.getName(1).toString().equalsIgnoreCase("oauth") && params.containsKey("state")) {
            handleOAuthRequest(params);
        }
        responseHeaders.add("Location", "https://ph16.englard.net/");
        httpExchange.sendResponseHeaders(302, -1);
    }

    private void handleOAuthRequest(@NotNull Map<String, String> params) {
        Player player = getServer().getPlayer(UUID.fromString(params.get("state")));
        if (player != null) {
            if (params.containsKey("error")) {
                kickPlayer(player, params.getOrDefault("error_description", params.get("error")));
            } else if (params.containsKey("code")) {
                handleSuccessfulOAuthRequest(params, player);
            }
        }
    }

    private void handleSuccessfulOAuthRequest(@NotNull Map<String, String> params, @NotNull Player player) {
        try {
            AccessToken tokens = discordClient.getAccessToken(params.get("code"));
            setUserTokens(player, tokens);
            if (userAllowed(player)) {
                String s = player.customName().toString();
                if (s == null) {
                    getLogger().severe("Player Mode missing");
                    kickPlayer(player, "Issue authenticating with Discord. Please try again later.");
                } else {
                    logPlayerAction(player, "authenticated successfully with Discord and will be allowed in.");
                    Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
                        player.customName(null);
                        player.setGameMode(GameMode.valueOf(s));
                        setUserNameFromDiscord(player);
                    });
                }
            } else {
                kickPlayer(player, "To join this server you must be part of the PH16 Discord Server.");
            }
        } catch (IOException | ExecutionException | InterruptedException ex) {
            getLogger().severe(ex.toString());
            kickPlayer(player, "Issue authenticating with Discord. Please try again later.");
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @NotNull
    private static Map<String, String> getParams(@NotNull URI uri) {
        List<NameValuePair> paramPairs = URLEncodedUtils.parse(uri, Charsets.UTF_8);
        Map<String, String> params = new HashMap<>(paramPairs.size());
        for (NameValuePair pair : paramPairs) {
            params.put(pair.getName(), pair.getValue());
        }
        return params;
    }

    private boolean userAllowed(@NotNull Player player) {
        AccessToken token = getUserTokens(player);
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
    private void setUserNameFromDiscord(@NotNull Player player) {
        AccessToken token = getUserTokens(player);
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
        if (discordClient == null || httpContext == null) {
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

        if (!getIsUserApproved(player)) {
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

    private void kickPlayer(@NotNull Player player, @NotNull String msg) {
        logPlayerAction(player, "kicked: " + msg);
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> player.kick(Component.text(msg)));
    }

    private void logPlayerAction(@NotNull Player player, @NotNull String msg) {
        getLogger().info(() -> "Player " + player.getName() + " " + msg);
    }
}
