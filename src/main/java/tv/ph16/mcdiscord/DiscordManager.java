package tv.ph16.mcdiscord;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import tv.ph16.discord.AccessToken;
import tv.ph16.discord.Client;

class DiscordManager {
    @Nullable
    private Client discordClient;
    @NotNull
    private final Plugin plugin;

    DiscordManager(@NotNull Plugin plugin) {
        this.plugin = plugin;
        loadClient();
        com.github.scribejava.core
    }

    private void setUserToken(@NotNull Player player, @NotNull AccessToken tokens) {
        plugin.getConfig().set(player.getUniqueId().toString(), tokens);
        plugin.saveConfig();
    }

    public boolean isEnabled() {
        return discordClient != null;
    }

    public void isEnabled(boolean value) {
        if (value) {
            loadClient();
        } else {
            discordClient = null;
        }
    }

    private void loadClient() {
        if (discordClient != null) {
            return;
        }
        plugin.reloadConfig();
        String clientId = plugin.getConfig().getString("clientId");
        if (clientId == null) {
            plugin.getLogger().warning("No clientId");
        }
        String clientSecret = plugin.getConfig().getString("clientSecret");
        if (clientSecret == null) {
            plugin.getLogger().warning("No clientSecret");
        }
        String scopes = plugin.getConfig().getString("scopes");
        if (scopes == null) {
            plugin.getLogger().warning("No scopes");
        }
        String callback = plugin.getConfig().getString("callback");
        if (callback == null) {
            plugin.getLogger().warning("No callback");
        }
        if (clientId == null || clientSecret == null || scopes == null || callback == null) {
            plugin.getLogger().warning("Missing configuration, no enabling discord client.");
            discordClient = null;
            return;
        }
        discordClient = new Client(clientId, clientSecret, scopes, callback);
    }
}
