package tv.ph16.discord;

import com.github.scribejava.core.model.OAuth2AccessToken;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class AccessToken implements ConfigurationSerializable {
    private final OAuth2AccessToken token;

    public AccessToken(@NotNull OAuth2AccessToken token) {
        this.token = token;
    }
    /**
     * Creates a Map representation of this class.
     * <p>
     * This class must provide a method to restore this class, as defined in
     * the {@link ConfigurationSerializable} interface javadocs.
     *
     * @return Map containing the current state of this class
     */
    @NotNull
    @Override
    public Map<String, Object> serialize() {
        HashMap<String, Object> state = new HashMap<>();
        state.put("accessToken", token.getAccessToken());
        state.put("tokenType", token.getTokenType());
        state.put("expiresIn", token.getExpiresIn());
        state.put("refreshToken", token.getRefreshToken());
        state.put("scope", token.getScope());
        state.put("rawResponse", token.getRawResponse());
        return state;
    }

    @NotNull
    public static AccessToken deserialize(@NotNull Map<String, Object> state) {
        return new AccessToken(new OAuth2AccessToken((String)state.get("accessToken"),
                (String)state.get("tokenType"), (Integer)state.get("expiresIn"), (String)state.get("refreshToken"),
                (String)state.get("scope"), (String)state.get("rawResponse")));
    }

    @NotNull
    String getAccessToken() {
        return token.getAccessToken();
    }

    @NotNull
    String getTokenType() {
        return token.getTokenType();
    }

    @NotNull
    Integer getExpiresIn() {
        return token.getExpiresIn();
    }

    @NotNull
    String getRefreshToken() {
        return token.getRefreshToken();
    }

    @NotNull
    String getScope() {
        return token.getScope();
    }

    @NotNull
    @Override
    public boolean equals(Object o) {
        return token.equals(o);
    }

    @NotNull
    @Override
    public int hashCode() {
        return token.hashCode();
    }
}
