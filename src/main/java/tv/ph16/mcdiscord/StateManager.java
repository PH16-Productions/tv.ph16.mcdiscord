package tv.ph16.mcdiscord;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import javax.annotation.Nullable;

import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import tv.ph16.discord.AccessToken;
import tv.ph16.discord.Client;

/**
 * Managers state for the plugin.
 */
public class StateManager {
    private JavaPlugin plugin;

    /**
     * Initializes a new instance of the StateManager class.
     * @param plugin the plugin to manage state for.
     */
    public StateManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void onLoad() {
        ConfigurationSerialization.registerClass(AccessToken.class);
    }

    /**
     * Check if a player is approved.
     * @param player the player to check.
     * @return true if the player is approved, otherwise false.
     */
    public boolean getIsUserApproved(@NotNull Player player) {
        return plugin.getConfig().contains(player.getUniqueId().toString(), true);
    }

    /**
     * Set the Discord token for a player.
     * @param player the player to set the token for.
     * @param token the Discord token.
     */
    public void setUserTokens(@NotNull Player player, @NotNull AccessToken token) {
        plugin.getConfig().set(player.getUniqueId().toString(), token);
        plugin.saveConfig();
    }

    /**
     * Gets the client ID for Discord.
     */
    @Nullable
    public String getClientId() {
        return plugin.getConfig().getString("clientId");
    }

    /**
     * Gets the client secret for Discord.
     */
    @Nullable
    public String getClientSecret() {
        return plugin.getConfig().getString("clientSecret");
    }

    /**
     * Gets the scope for Discord.
     */
    @Nullable
    public String getScopes() {
        return plugin.getConfig().getString("scopes");
    }

    /**
     * Gets the callback for Discord.
     */
    @Nullable
    public String getCallback() {
        return plugin.getConfig().getString("callback");
    }

    /**
     * Gets a players Discord token.
     * @param player the player to get for.
     * @param discordClient the Discord client.
     * @return the token if one is had, otherwise null.
     */
    @Nullable
    public AccessToken getUserTokens(@NotNull Player player, @NotNull Client discordClient) {
        AccessToken token = plugin.getConfig().getObject(player.getUniqueId().toString(), AccessToken.class);
        if (token != null) {
            try {
                token = discordClient.refreshAccessToken(token);
                setUserTokens(player, token);
            } catch (IOException | InterruptedException | ExecutionException ex) {
                plugin.getLogger().severe("Fetch Guilds Error:\n" + ex);
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return token;
    }
}
