package net.englard.shmuelie.ph16discord;

import com.destroystokyo.paper.Title;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import net.englard.shmuelie.discord.AccessToken;
import net.englard.shmuelie.discord.Client;
import net.englard.shmuelie.discord.PartialGuild;
import net.englard.shmuelie.discord.User;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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
        if (webServerPlugin instanceof net.englard.shmuelie.bukkitwebserver.Plugin) {
            pluginManager.registerEvents(this, this);
            net.englard.shmuelie.bukkitwebserver.Plugin webServer = (net.englard.shmuelie.bukkitwebserver.Plugin)webServerPlugin;
            httpContext = webServer.addHandler("/discord/", this);
        } else {
            getLogger().severe(net.englard.shmuelie.bukkitwebserver.Plugin.class.getCanonicalName() + " is required to run this");
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
                String s = player.getCustomName();
                if (s == null) {
                    getLogger().severe("Player Mode missing");
                    kickPlayer(player, "Issue authenticating with Discord. Please try again later.");
                } else {
                    Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
                        player.setCustomName(null);
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
        List<NameValuePair> paramPairs = URLEncodedUtils.parse(uri, "UTF-8");
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

    private void setUserNameFromDiscord(@NotNull Player player) {
        AccessToken token = getUserTokens(player);
        if (token != null) {
            try {
                Optional<User> userOptional = discordClient.getCurrentUser(token);
                if (userOptional.isPresent()) {
                    User user = userOptional.get();
                    player.setDisplayName(user.getUsername());
                    player.setPlayerListName(user.getUsername());
                }
            } catch (IOException | InterruptedException | ExecutionException ex) {
                getLogger().severe("Fetch User Error:\n" + ex);
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @EventHandler
    public void onGameModeChange(@NotNull PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (player.getCustomName() != null && event.getNewGameMode() != GameMode.SPECTATOR) {
            event.setCancelled(true);
            player.setGameMode(GameMode.SPECTATOR);
        }
    }

    @EventHandler
    public void onLogin(@NotNull PlayerLoginEvent event) {
        if (discordClient == null || httpContext == null) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, "Server Not Ready");
        }
    }

    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.sendMessage("Welcome to the PH16 Minecraft Server.");
        if (!getIsUserApproved(player)) {
            logPlayerAction(player, "has not linked with Discord.");
            player.setCustomName(player.getGameMode().name());
            player.setGameMode(GameMode.SPECTATOR);
            String url = discordClient.getAuthorizationUrl(player.getUniqueId().toString());
            TextComponent clickArea = new TextComponent();
            clickArea.setBold(true);
            clickArea.setUnderlined(true);
            clickArea.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
            clickArea.setText("login with Discord (Click On This)");
            player.sendMessage(new TextComponent("To use the server, please "), clickArea, new TextComponent("."));
            player.sendTitle(Title.builder().title("Please open chat (t) and login with Discord.").stay(10000).build());
        } else if (!userAllowed(player)) {
            logPlayerAction(player, "is not part of PH16.");
            kickPlayer(player, "To join this server you must be part of the PH16 Discord Server.");
        } else {
            logPlayerAction(player, "authenticated successfully with Discord");
            setUserNameFromDiscord(player);
            player.setCustomName(null);
        }
    }

    private void kickPlayer(@NotNull Player player, @NotNull String msg) {
        logPlayerAction(player, "kicked: " + msg);
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> player.kickPlayer(msg));
    }

    private void logPlayerAction(@NotNull Player player, @NotNull String msg) {
        getLogger().info(() -> "Player " + player.getName() + " " + msg);
    }
}
