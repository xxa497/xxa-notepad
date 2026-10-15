package com.example.notebook;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条记事：时间、正文、标签列表。
 */
public class Note {

    public long id;                  // 唯一标识（用创建时间毫秒）
    public long timeMillis;          // 原始时间戳
    public String timeText = "";     // 显示用时间文本：yyyy年MM月dd日 HH:mm:ss
    public String content = "";      // 正文内容
    public List<String> tags = new ArrayList<String>(); // 标签列表

    public Note() {
    }

    /** 从 JSON 还原 */
    public static Note fromJson(JSONObject obj) throws JSONException {
        Note n = new Note();
        n.id = obj.optLong("id");
        n.timeMillis = obj.optLong("time");
        n.timeText = obj.optString("timeText");
        n.content = obj.optString("content");
        JSONArray arr = obj.optJSONArray("tags");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                n.tags.add(arr.optString(i));
            }
        }
        return n;
    }

    /** 转成 JSON */
    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("time", timeMillis);
        obj.put("timeText", timeText);
        obj.put("content", content);
        obj.put("tags", new JSONArray(tags));
        return obj;
    }
}
