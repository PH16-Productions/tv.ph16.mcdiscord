package tv.ph16.mcdiscord;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import tv.ph16.discord.AccessToken;
import tv.ph16.discord.Client;
import tv.ph16.discord.PartialGuild;

public class AuthenticationManager {
    private JavaPlugin plugin;
    private StateManager stateManager;

    /**
     * Initializes a new instance of the AuthenticationManager.
     * @param plugin The plugin that is using the AuthenticationManager.
     * @param stateManager The StateManager for token access.
     */
    public AuthenticationManager(@NotNull JavaPlugin plugin, @NotNull StateManager stateManager) {
        this.plugin = plugin;
        this.stateManager = stateManager;
    }

    /**
     * Gets a players Discord token. Always refreshes the token.
     * @param player the player to get for.
     * @param discordClient the Discord client.
     * @return the token if one is had, otherwise null.
     */
    @Nullable
    public AccessToken getUserToken(@NotNull Player player, @NotNull Client discordClient) {
        AccessToken token = stateManager.getUserToken(player);
        if (token != null) {
            try {
                token = discordClient.refreshAccessToken(token);
                stateManager.setUserTokens(player, token);
            } catch (IOException | InterruptedException | ExecutionException ex) {
                plugin.getLogger().severe("Fetch Guilds Error:\n" + ex);
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return token;
    }

    public boolean getUserInRequiredGuild(@NotNull AccessToken userAccessToken, @NotNull Client discordClient) {
        Optional<PartialGuild> requiredGuild = Optional.empty();
        try {
            Optional<List<PartialGuild>> partialGuilds = discordClient.getCurrentUsersGuilds(userAccessToken);
            if (partialGuilds.isPresent()) {
                requiredGuild = partialGuilds.get().stream().filter(pg -> pg.getName().equalsIgnoreCase(stateManager.getGuildName())).findFirst();
            }
        } catch (IOException | InterruptedException | ExecutionException ex) {
            plugin.getLogger().severe("Fetch Guilds Error:\n" + ex);
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return requiredGuild.isPresent();
    }

    /**
     * Checks if a player should be explicitly allowed or explicitly banned.
     * @param player The player to check for.
     * @return True if the player is explicitly allowed, False if the player is explicitly banned, or null if the player uses Discord authentication.
     */
    @Nullable
    public Boolean getUserExplicitlyAllowedOrBanned(@NotNull Player player) {
        Server server = plugin.getServer();
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
}
