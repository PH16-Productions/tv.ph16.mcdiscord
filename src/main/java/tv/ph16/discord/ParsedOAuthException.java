package tv.ph16.discord;

import com.github.scribejava.core.exceptions.OAuthException;

public class ParsedOAuthException extends OAuthException {
    private final int code;
    private final int httpCode;

    public ParsedOAuthException(int code, int httpCode, String msg) {
        super(msg);
        this.code = code;
        this.httpCode = httpCode;
    }

    public int getCode() {
        return code;
    }

    public int getHttpCode() {
        return httpCode;
    }
}
