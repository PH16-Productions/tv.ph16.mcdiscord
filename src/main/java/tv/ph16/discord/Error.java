package tv.ph16.discord;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

public class Error {
    private final int code;
    private final String message;

    private Error(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    @NotNull
    public String getMessage() {
        return message;
    }

    @NotNull
    public static Error fill(@NotNull JSONObject obj) {
        return new Error(obj.getInt("code"), obj.getString("message"));
    }
}
