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
 * 私密记录存储（单例）。
 * <p>
 * 保存位置：应用包名目录 /private/private_notes.txt
 * 与普通记事 history 完全分开：单独文件、单独目录。
 * 私密记事没有回收站，删除即永久删除。
 */
public class PrivateNoteStorage {

    private static PrivateNoteStorage instance;

    private final File file;
    private final List<Note> notes = new ArrayList<Note>();

    public static synchronized PrivateNoteStorage get(Context context) {
        if (instance == null) {
            instance = new PrivateNoteStorage(context.getApplicationContext());
        }
        return instance;
    }

    private PrivateNoteStorage(Context context) {
        File pkgDir = new File(context.getApplicationInfo().dataDir);
        File dir = new File(pkgDir, "private");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        file = new File(dir, "private_notes.txt");
        load();
    }

    private void load() {
        notes.clear();
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
            JSONArray arr = root.optJSONArray("normal");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    notes.add(Note.fromJson(arr.getJSONObject(i)));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void save() {
        try {
            JSONObject root = new JSONObject();
            JSONArray arr = new JSONArray();
            for (Note n : notes) {
                arr.put(n.toJson());
            }
            root.put("normal", arr);
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

    public List<Note> getNotes() {
        return notes;
    }

    /** 清空全部私密记录（重置密码时使用，不可恢复） */
    public void clearAll() {
        notes.clear();
        save();
    }

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

    public void deleteNote(Note note) {
        notes.remove(note);
        save();
    }

    public void updateContent(Note note, String content) {
        note.content = content;
        save();
    }

    public void addTag(Note note, String tag) {
        if (!note.tags.contains(tag)) {
            note.tags.add(tag);
            save();
        }
    }

    public void removeTag(Note note, String tag) {
        note.tags.remove(tag);
        save();
    }
}
