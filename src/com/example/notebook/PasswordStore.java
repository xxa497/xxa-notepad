package com.example.notebook;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.security.MessageDigest;

/**
 * 密码存储（单例）。
 * <p>
 * 保存位置：应用包名目录 /password/password.dat
 * 只保存密码的 SHA-256 摘要，不保存明文。
 * 与普通记事 history 完全分开存放，避免主文件被读出后连带泄露。
 */
public class PasswordStore {

    public static final String TYPE_PIN = "pin";        // 数字密码
    public static final String TYPE_PATTERN = "pattern"; // 九宫格图案密码

    private static PasswordStore instance;

    private final File file;
    private String type = "";
    private String hash = "";

    public static synchronized PasswordStore get(Context context) {
        if (instance == null) {
            instance = new PasswordStore(context.getApplicationContext());
        }
        return instance;
    }

    private PasswordStore(Context context) {
        File pkgDir = new File(context.getApplicationInfo().dataDir);
        File pwdDir = new File(pkgDir, "password");
        if (!pwdDir.exists()) {
            pwdDir.mkdirs();
        }
        file = new File(pwdDir, "password.dat");
        load();
    }

    private void load() {
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
            JSONObject obj = new JSONObject(sb.toString());
            type = obj.optString("type", "");
            hash = obj.optString("hash", "");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void save() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", type);
            obj.put("hash", hash);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            FileOutputStream fos = new FileOutputStream(file);
            OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8");
            writer.write(obj.toString());
            writer.flush();
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 是否已经设置过密码 */
    public boolean isSet() {
        return type.length() > 0 && hash.length() > 0;
    }

    /** 当前密码方式：pin / pattern */
    public String getType() {
        return type;
    }

    /** 设置新密码（覆盖旧的） */
    public void setPassword(String pwdType, String raw) {
        this.type = pwdType;
        this.hash = sha256(raw);
        save();
    }

    /** 校验密码是否正确 */
    public boolean verify(String raw) {
        return isSet() && sha256(raw).equals(hash);
    }

    /** 重置：清空密码 */
    public void clear() {
        type = "";
        hash = "";
        save();
    }

    private static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(raw.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(raw.hashCode());
        }
    }
}
