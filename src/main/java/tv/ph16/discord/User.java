package tv.ph16.discord;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class User {
    private String id;

    private String username;

    private String discriminator;

    private String avatar;

    private boolean verified;

    private String email;

    private int flags;

    private int premiumType;

    private int publicFlags;

    public void setId(String id){
        this.id = id;
    }
    public String getId(){
        return this.id;
    }
    public void setUsername(String username){
        this.username = username;
    }
    public String getUsername(){
        return this.username;
    }
    public void setDiscriminator(String discriminator){
        this.discriminator = discriminator;
    }
    public String getDiscriminator(){
        return this.discriminator;
    }
    public void setAvatar(String avatar){
        this.avatar = avatar;
    }
    public String getAvatar(){
        return this.avatar;
    }
    public void setVerified(boolean verified){
        this.verified = verified;
    }
    public boolean getVerified(){
        return this.verified;
    }
    public void setEmail(String email){
        this.email = email;
    }
    public String getEmail(){
        return this.email;
    }
    public void setFlags(int flags){
        this.flags = flags;
    }
    public int getFlags(){
        return this.flags;
    }
    public void setPremiumType(int premiumType){
        this.premiumType = premiumType;
    }
    public int getPremiumType(){
        return this.premiumType;
    }
    public void setPublicFlags(int publicFlags){
        this.publicFlags = publicFlags;
    }
    public int getPublicFlags(){
        return this.publicFlags;
    }
    public static User fill(JSONObject jsonObject){
        User entity = new User();
        if (jsonObject.has("id")) {
            entity.setId(jsonObject.getString("id"));
        }
        if (jsonObject.has("username")) {
            entity.setUsername(jsonObject.getString("username"));
        }
        entity.setDiscriminator(getValueSafe(jsonObject, "discriminator", String.class));
        entity.setAvatar(getValueSafe(jsonObject, "avatar", String.class));
        entity.setVerified(getValueSafe(jsonObject, "verified", Boolean.class));
        entity.setEmail(getValueSafe(jsonObject, "email", String.class));
        entity.setFlags(getValueSafe(jsonObject, "flags", Integer.class));
        entity.setPremiumType(getValueSafe(jsonObject, "premium_type", Integer.class));
        entity.setPublicFlags(getValueSafe(jsonObject, "public_flags", Integer.class));
        return entity;
    }
    public static List<User> fillList(JSONArray jsonArray) {
        if (jsonArray == null || jsonArray.length() == 0)
            return new ArrayList<>();
        List<User> users = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            users.add(fill(jsonArray.getJSONObject(i)));
        }
        return users;
    }

    @NotNull
    private static <T> T getValueSafe(@NotNull JSONObject jsonObject, @NotNull String key, @NotNull Class<T> clazz) {
        if (jsonObject.has(key)) {
            Object value = jsonObject.get(key);
            if (clazz == value.getClass()) {
                return clazz.cast(value);
            }
        }
        if (clazz == String.class) {
            return clazz.cast("");
        }
        if (clazz == Boolean.class) {
            return clazz.cast(false);
        }
        if (clazz.isPrimitive()) {
            return clazz.cast(0);
        }
        throw new ClassCastException();
    }
}
