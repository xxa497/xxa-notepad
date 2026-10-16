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
    public List<String> images = new ArrayList<String>(); // 附带图片文件名列表（存于应用 images 目录）
    public List<String> files = new ArrayList<String>(); // 附带附件文件名列表（存于应用 files 目录）

    public Note() {
    }

    /** 从 JSON 还原 */
    public static Note fromJson(JSONObject obj) throws JSONException {
        Note n = new Note();
        n.id = obj.optLong("id");
        n.timeMillis = obj.optLong("time");
        n.timeText = obj.optString("timeText");
        n.content = obj.optString("content");
        // 兼容旧版单图字段
        String oldImg = obj.optString("image", "");
        if (oldImg.length() > 0) n.images.add(oldImg);
        JSONArray arr = obj.optJSONArray("tags");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                n.tags.add(arr.optString(i));
            }
        }
        JSONArray imgArr = obj.optJSONArray("images");
        if (imgArr != null) {
            for (int i = 0; i < imgArr.length(); i++) {
                String s = imgArr.optString(i);
                if (s.length() > 0 && !n.images.contains(s)) n.images.add(s);
            }
        }
        JSONArray farr = obj.optJSONArray("files");
        if (farr != null) {
            for (int i = 0; i < farr.length(); i++) {
                n.files.add(farr.optString(i));
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
        obj.put("images", new JSONArray(images));
        obj.put("files", new JSONArray(files));
        return obj;
    }
}
