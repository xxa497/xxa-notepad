package com.example.notebook;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * 记事数据存储（单例）。
 * <p>
 * 保存位置：应用包名目录 /history/notebook_history.txt
 * 例如：/data/data/com.example.notebook/history/notebook_history.txt
 * <p>
 * 文件内容是一个 JSON 对象，普通记事与回收站分开记录、同存一个文件：
 * {"normal":[...], "recycle":[...]}
 */
public class NoteStorage {

    private static NoteStorage instance;

    private final File file;
    private final List<Note> notes = new ArrayList<Note>();   // 普通记事，列表顺序即显示顺序（新的在前）
    private final List<Note> recycle = new ArrayList<Note>(); // 回收站（新的在前）

    public static synchronized NoteStorage get(Context context) {
        if (instance == null) {
            instance = new NoteStorage(context.getApplicationContext());
        }
        return instance;
    }

    private NoteStorage(Context context) {
        // 包名目录下创建 history 文件夹
        File pkgDir = new File(context.getApplicationInfo().dataDir);
        File historyDir = new File(pkgDir, "history");
        if (!historyDir.exists()) {
            historyDir.mkdirs();
        }
        file = new File(historyDir, "notebook_history.txt");
        load();
    }

    /** 历史文件对象（便于查看/分享） */
    public File getHistoryFile() {
        return file;
    }

    // ---------- 读取 / 写入 ----------

    private void load() {
        notes.clear();
        recycle.clear();
        if (!file.exists()) {
            return;
        }
        try {
            FileInputStream fis = new FileInputStream(file);
            InputStreamReader reader = new InputStreamReader(fis, "UTF-8");
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int len;
            while ((len = reader.read(buf)) != -1) {
                sb.append(buf, 0, len);
            }
            reader.close();

            JSONObject root = new JSONObject(sb.toString());
            JSONArray nArr = root.optJSONArray("normal");
            JSONArray rArr = root.optJSONArray("recycle");
            if (nArr != null) {
                for (int i = 0; i < nArr.length(); i++) {
                    notes.add(Note.fromJson(nArr.getJSONObject(i)));
                }
            }
            if (rArr != null) {
                for (int i = 0; i < rArr.length(); i++) {
                    recycle.add(Note.fromJson(rArr.getJSONObject(i)));
                }
            }
        } catch (Exception e) {
            e.printStackTrace(); // 文件损坏时放弃旧数据，避免崩溃
        }
    }

    private void save() {
        try {
            JSONObject root = new JSONObject();
            JSONArray nArr = new JSONArray();
            for (Note n : notes) {
                nArr.put(n.toJson());
            }
            JSONArray rArr = new JSONArray();
            for (Note n : recycle) {
                rArr.put(n.toJson());
            }
            root.put("normal", nArr);
            root.put("recycle", rArr);

            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            FileOutputStream fos = new FileOutputStream(file);
            OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8");
            writer.write(root.toString());
            writer.flush();
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------- 数据操作 ----------

    public List<Note> getNotes() {
        return notes;
    }

    public List<Note> getRecycle() {
        return recycle;
    }

    /** 新建记事，放在最上面（最新在前），可附带图片和附件 */
    public void addNoteTop(String content, long timeMillis, String timeText,
                           java.util.List<String> images, java.util.List<String> files) {
        Note n = new Note();
        n.id = timeMillis;
        n.timeMillis = timeMillis;
        n.timeText = timeText;
        n.content = content;
        if (images != null) n.images.addAll(images);
        if (files != null) n.files.addAll(files);
        notes.add(0, n);
        save();
    }

    /** 在指定位置下方一条，可附带图片和附件 */
    public void insertNoteBelow(int index, String content, long timeMillis, String timeText,
                                java.util.List<String> images, java.util.List<String> files) {
        Note n = new Note();
        n.id = timeMillis;
        n.timeMillis = timeMillis;
        n.timeText = timeText;
        n.content = content;
        if (images != null) n.images.addAll(images);
        if (files != null) n.files.addAll(files);
        int pos = index + 1;
        if (pos > notes.size()) {
            pos = notes.size();
        }
        notes.add(pos, n);
        save();
    }

    /** 删除：移入回收站（回收站里新的在前） */
    public void moveToRecycle(Note note) {
        if (notes.remove(note)) {
            recycle.add(0, note);
            save();
        }
    }

    /** 恢复：放回普通记事最上面 */
    public void restore(Note note) {
        if (recycle.remove(note)) {
            notes.add(0, note);
            save();
        }
    }

    /** 永久删除（回收站） */
    public void permanentDelete(List<Note> list) {
        recycle.removeAll(list);
        save();
    }

    /** 编辑内容 */
    public void updateContent(Note note, String content) {
        note.content = content;
        save();
    }

    /** 添加标签（重复标签不添加） */
    public void addTag(Note note, String tag) {
        if (!note.tags.contains(tag)) {
            note.tags.add(tag);
            save();
        }
    }

    /** 删除标签 */
    public void removeTag(Note note, String tag) {
        note.tags.remove(tag);
        save();
    }
}
