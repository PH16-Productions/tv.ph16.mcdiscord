package tv.ph16.discord;

import com.github.scribejava.apis.DiscordApi;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.spencerwi.either.Either;
import com.spencerwi.either.Result;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public final class Client {
    private final OAuth20Service oauthService;

    public Client(@NotNull String clientId, @NotNull String clientSecret, @NotNull String scopes, @NotNull String callback) {
        oauthService = new ServiceBuilder(clientId).apiSecret(clientSecret).defaultScope(scopes).callback(callback).build(DiscordApi.instance());
    }

    @NotNull
    public AccessToken getAccessToken(@NotNull String code) throws IOException, InterruptedException, ExecutionException {
        return new AccessToken(oauthService.getAccessToken(code));
    }

    @NotNull
    public String getAuthorizationUrl(@NotNull String state) {
        return oauthService.getAuthorizationUrl(state);
    }

    @NotNull
    public AccessToken refreshAccessToken(@NotNull AccessToken token) throws IOException, InterruptedException, ExecutionException {
        return new AccessToken(oauthService.refreshAccessToken(token.getRefreshToken()));
    }

    @NotNull
    public Result<User> getCurrentUser(@NotNull AccessToken token) {
        OAuthRequest request = new OAuthRequest(Verb.GET, "https://discord.com/api/v8/users/@me");
        oauthService.signRequest(token.getAccessToken(), request);
        return Result.attempt(() -> {
            try (Response response = oauthService.execute(request)) {
                String responseBody = response.getBody();
                JSONObject responseJson = new JSONObject(responseBody);
                if (response.isSuccessful()) {
                    return User.fill(responseJson);
                }
                throw new ParsedOAuthException(responseJson.getInt("code"), response.getCode(), responseJson.getString("message"));
            }
        });
    }

    @NotNull
    public Optional<List<PartialGuild>> getCurrentUsersGuilds(@NotNull AccessToken token) throws InterruptedException, ExecutionException, IOException {
        OAuthRequest request = new OAuthRequest(Verb.GET, "https://discord.com/api/v8/users/@me/guilds");
        oauthService.signRequest(token.getAccessToken(), request);
        try (Response response = oauthService.execute(request)) {
            String responseBody = response.getBody();
            if (response.isSuccessful()) {
                return Optional.of(PartialGuild.fillList(new JSONArray(responseBody)));
            }
            return Optional.empty();
        }
    }

    @NotNull
    public Optional<Instant> getAuthorizationInformation(@NotNull AccessToken token) throws InterruptedException, ExecutionException, IOException {
        OAuthRequest request = new OAuthRequest(Verb.GET, "https://discord.com/api/v8/oauth2/@me");
        oauthService.signRequest(token.getAccessToken(), request);
        try (Response response = oauthService.execute(request)) {
            String responseBody = response.getBody();
            if (response.isSuccessful()) {
                JSONObject responseJson = new JSONObject(responseBody);
                if (responseJson.has("expires")) {
                    String expiresStr = responseJson.getString("expires");
                    if (expiresStr != null) {
                        try {
                            return Optional.of(Instant.parse(expiresStr));
                        } catch (DateTimeParseException ex) {
                            return Optional.empty();
                        }
                    }
                }
            }
            return Optional.empty();
        }
    }
}
