package tv.ph16.discord;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PartialGuild
{
    private String id;
    private String name;

    public void setId(@NotNull String id){
        this.id = id;
    }
    @NotNull
    public String getId(){
        return this.id;
    }

    public void setName(@NotNull String name){
        this.name = name;
    }
    @NotNull
    public String getName(){
        return this.name;
    }

    @NotNull
    public static PartialGuild fill(@NotNull JSONObject jsonObject){
        PartialGuild entity = new PartialGuild();
        if (jsonObject.has("id")) {
            entity.setId(jsonObject.getString("id"));
        }
        if (jsonObject.has("name")) {
            entity.setName(jsonObject.getString("name"));
        }
        return entity;
    }

    @NotNull
    public static List<PartialGuild> fillList(JSONArray jsonArray) {
        if (jsonArray == null || jsonArray.length() == 0)
            return new ArrayList<>();
        List<PartialGuild> partialGuilds = new ArrayList<PartialGuild>();
        for (int i = 0; i < jsonArray.length(); i++) {
            partialGuilds.add(fill(jsonArray.getJSONObject(i)));
        }
        return partialGuilds;
    }
}
