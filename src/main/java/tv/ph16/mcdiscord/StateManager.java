package tv.ph16.mcdiscord;

import javax.annotation.Nullable;

import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import tv.ph16.discord.AccessToken;

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
    public boolean userHasTokenSaved(@NotNull Player player) {
        return plugin.getConfig().contains(player.getUniqueId().toString(), true);
    }

    @Nullable
    public AccessToken getUserToken(@NotNull Player player) {
        return plugin.getConfig().getObject(player.getUniqueId().toString(), AccessToken.class);
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
     * Gets the redirection for after calls to the Authentication Server.
     */
    @Nullable
    public String getRedirection() {
        return plugin.getConfig().getString("redirection");
    }

    @Nullable
    public String getGuildName() {
        return plugin.getConfig().getString("guildName");
    }
}
