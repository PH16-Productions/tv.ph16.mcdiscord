package tv.ph16.discord;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PartialGuild
{
    private String id;

    private String name;

    public void setId(String id){
        this.id = id;
    }
    public String getId(){
        return this.id;
    }
    public void setName(String name){
        this.name = name;
    }
    public String getName(){
        return this.name;
    }
    public static PartialGuild fill(JSONObject jsonObject){
        PartialGuild entity = new PartialGuild();
        if (jsonObject.has("id")) {
            entity.setId(jsonObject.getString("id"));
        }
        if (jsonObject.has("name")) {
            entity.setName(jsonObject.getString("name"));
        }
        return entity;
    }
    public static List<PartialGuild> fillList(JSONArray jsonArray) {
        if (jsonArray == null || jsonArray.length() == 0)
            return null;
        List<PartialGuild> partialGuilds = new ArrayList<PartialGuild>();
        for (int i = 0; i < jsonArray.length(); i++) {
            partialGuilds.add(fill(jsonArray.getJSONObject(i)));
        }
        return partialGuilds;
    }
}
