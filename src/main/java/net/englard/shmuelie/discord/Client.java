package net.englard.shmuelie.discord;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public final class Client {
    private final OAuth20Service oauthService;

    private static class API extends DefaultApi20 {
        /**
         * Returns the URL that receives the access token requests.
         *
         * @return access token URL
         */
        @Override
        @NotNull
        public String getAccessTokenEndpoint() {
            return "https://discord.com/api/oauth2/token";
        }

        @Override
        @NotNull
        protected String getAuthorizationBaseUrl() {
            return "https://discord.com/api/oauth2/authorize";
        }
    }

    public Client(@NotNull String clientId, @NotNull String clientSecret, @NotNull String scopes, @NotNull String callback) {
        oauthService = new ServiceBuilder(clientId).apiSecret(clientSecret).defaultScope(scopes).callback(callback).build(new API());
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
    public Optional<User> getCurrentUser(@NotNull AccessToken token) throws InterruptedException, ExecutionException, IOException {
        OAuthRequest request = new OAuthRequest(Verb.GET, "https://discord.com/api/v8/users/@me");
        oauthService.signRequest(token.getAccessToken(), request);
        try (Response response = oauthService.execute(request)) {
            String responseBody = response.getBody();
            if (response.isSuccessful()) {
                return Optional.of(User.fill(new JSONObject(responseBody)));
            }
            return Optional.empty();
        }
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
}
