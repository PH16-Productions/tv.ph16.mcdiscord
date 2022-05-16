package tv.ph16.mcdiscord;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import javax.annotation.Nullable;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.apache.commons.codec.Charsets;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;

import tv.ph16.discord.AccessToken;
import tv.ph16.discord.Client;

public class AuthenticationServer implements HttpHandler {
    private StateManager stateManager;
    private Client discordClient;
    private Plugin plugin;

    private AuthenticationServer(@NotNull StateManager stateManager, @NotNull Client discordClient, @NotNull Plugin plugin) {
        this.stateManager = stateManager;
        this.discordClient = discordClient;
        this.plugin = plugin;
    }

    @Nullable
    public static AuthenticationServer create(@NotNull StateManager stateManager, @NotNull Client discordClient, @NotNull Plugin plugin, @NotNull PluginManager pluginManager) {
        org.bukkit.plugin.Plugin webServerPlugin = pluginManager.getPlugin("Bukkit-Web-Server");
        if (webServerPlugin instanceof tv.ph16.bukkitwebserver.Plugin) {
            AuthenticationServer instance = new AuthenticationServer(stateManager, discordClient, plugin);
            tv.ph16.bukkitwebserver.Plugin webServer = (tv.ph16.bukkitwebserver.Plugin)webServerPlugin;
            webServer.addHandler("/discord/", instance);
            return instance;
        }
        plugin.getLogger().severe(tv.ph16.bukkitwebserver.Plugin.class.getCanonicalName() + " is required to run this");
        return null;
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
        responseHeaders.add("X-Powered-By", plugin.getDescription().getFullName());
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

    @NotNull
    private static Map<String, String> getParams(@NotNull URI uri) {
        List<NameValuePair> paramPairs = URLEncodedUtils.parse(uri, Charsets.UTF_8);
        Map<String, String> params = new HashMap<>(paramPairs.size());
        for (NameValuePair pair : paramPairs) {
            params.put(pair.getName(), pair.getValue());
        }
        return params;
    }


    private void handleOAuthRequest(@NotNull Map<String, String> params) {
        Player player = plugin.getServer().getPlayer(UUID.fromString(params.get("state")));
        if (player != null) {
            if (params.containsKey("error")) {
                plugin.kickPlayer(player, params.getOrDefault("error_description", params.get("error")));
            } else if (params.containsKey("code")) {
                handleSuccessfulOAuthRequest(params, player);
            }
        }
    }

    private void handleSuccessfulOAuthRequest(@NotNull Map<String, String> params, @NotNull Player player) {
        try {
            AccessToken tokens = discordClient.getAccessToken(params.get("code"));
            stateManager.setUserTokens(player, tokens);
            if (plugin.userAllowed(player)) {
                String s = player.customName().toString();
                if (s == null) {
                    plugin.getLogger().severe("Player Mode missing");
                    plugin.kickPlayer(player, "Issue authenticating with Discord. Please try again later.");
                } else {
                    plugin.logPlayerAction(player, "authenticated successfully with Discord and will be allowed in.");
                    Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                        player.customName(null);
                        player.setGameMode(GameMode.valueOf(s));
                        plugin.setUserNameFromDiscord(player);
                    });
                }
            } else {
                plugin.kickPlayer(player, "To join this server you must be part of the PH16 Discord Server.");
            }
        } catch (IOException | ExecutionException | InterruptedException ex) {
            plugin.getLogger().severe(ex.toString());
            plugin.kickPlayer(player, "Issue authenticating with Discord. Please try again later.");
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
