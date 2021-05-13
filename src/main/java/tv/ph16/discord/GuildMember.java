package tv.ph16.discord;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class GuildMember
{
    private User user;

    private String nick;

    private List<String> roles;

    private String joinedAt;

    private boolean deaf;

    private boolean mute;

    public void setUser(User user){
        this.user = user;
    }
    public User getUser(){
        return this.user;
    }
    public void setNick(String nick){
        this.nick = nick;
    }
    public String getNick(){
        return this.nick;
    }
    public void setRoles(List<String> roles){
        this.roles = roles;
    }
    public List<String> getRoles(){
        return this.roles;
    }
    public void setJoinedAt(String joinedAt){
        this.joinedAt = joinedAt;
    }
    public String getJoinedAt(){
        return this.joinedAt;
    }
    public void setDeaf(boolean deaf){
        this.deaf = deaf;
    }
    public boolean getDeaf(){
        return this.deaf;
    }
    public void setMute(boolean mute){
        this.mute = mute;
    }
    public boolean getMute(){
        return this.mute;
    }
    public static GuildMember fill(JSONObject jsonObject){
        GuildMember entity = new GuildMember();
        if (jsonObject.has("user")) {
            entity.setUser(User.fill(jsonObject.getJSONObject("user")));
        }
        if (jsonObject.has("nick")) {
            entity.setNick(jsonObject.getString("nick"));
        }
        if (jsonObject.has("roles")) {
            JSONArray jsonArray = jsonObject.getJSONArray("roles");
            List<String> roles = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                roles.add(jsonArray.getString(i));
            }
            entity.setRoles(roles);
        }
        if (jsonObject.has("joined_at")) {
            entity.setJoinedAt(jsonObject.getString("joined_at"));
        }
        if (jsonObject.has("deaf")) {
            entity.setDeaf(jsonObject.getBoolean("deaf"));
        }
        if (jsonObject.has("mute")) {
            entity.setMute(jsonObject.getBoolean("mute"));
        }
        return entity;
    }
    public static List<GuildMember> fillList(JSONArray jsonArray) {
        if (jsonArray == null || jsonArray.length() == 0)
            return null;
        List<GuildMember> olist = new ArrayList<GuildMember>();
        for (int i = 0; i < jsonArray.length(); i++) {
            olist.add(fill(jsonArray.getJSONObject(i)));
        }
        return olist;
    }
}
