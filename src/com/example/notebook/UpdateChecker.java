package com.example.notebook;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 应用内更新检查：双击抽屉里的版本号触发。
 * 流程：请求 GitHub 最新 Release -> 版本号对比 -> 有新版则弹框 ->
 *       下载 APK 到本应用私有目录 -> 唤起系统安装界面。
 * 数据源：https://api.github.com/repos/xxa497/xxa-notepad/releases/latest
 */
public class UpdateChecker {

    private static final String API =
            "https://api.github.com/repos/xxa497/xxa-notepad/releases/latest";
    private static final String AUTHORITY = "com.example.notebook.apkprovider";
    private static final int DIALOG_THEME = android.R.style.Theme_Material_Light_Dialog_Alert;

    private final Activity activity;

    public UpdateChecker(Activity activity) {
        this.activity = activity;
    }

    /** 双击版本号时调用，必须在主线程调用 */
    public void check() {
        Toast.makeText(activity, "正在检查更新…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    conn = (HttpURLConnection) new URL(API).openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setRequestProperty("User-Agent", "xxa-notepad");
                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line).append('\n');
                    br.close();

                    JSONObject json = new JSONObject(sb.toString());
                    String tag = json.optString("tag_name", "");          // 例如 v2.4.5
                    String notes = json.optString("body", "");
                    String apkUrl = "";
                    if (json.optJSONArray("assets") != null
                            && json.optJSONArray("assets").length() > 0) {
                        apkUrl = json.optJSONArray("assets").getJSONObject(0)
                                .optString("browser_download_url", "");
                    }

                    int[] remote = parseVersion(tag);
                    int[] local = parseVersion(getLocalVersionName());
                    if (compare(remote, local) > 0) {
                        showUpdateFound(tag, notes, apkUrl);
                    } else {
                        toastOnMain("已是最新版本");
                    }
                } catch (Exception e) {
                    toastOnMain("检查更新失败，请检查网络");
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }

    private void showUpdateFound(final String tag, final String notes, final String apkUrl) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(activity, DIALOG_THEME)
                        .setTitle("发现新版本 " + tag)
                        .setMessage((notes.length() > 0 ? notes : "暂无更新说明")
                                + "\n\n将自动下载更新包并进入系统安装界面。")
                        .setPositiveButton("立即更新", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                startDownload(apkUrl);
                            }
                        })
                        .setNegativeButton("以后再说", null)
                        .show();
            }
        });
    }

    private void startDownload(String apkUrl) {
        if (apkUrl.length() == 0) {
            Toast.makeText(activity, "这个发布里没有安装包，请联系作者", Toast.LENGTH_LONG).show();
            return;
        }
        // Android 8.0+ 需要先在系统设置里允许"安装未知应用"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(activity, "请先在设置里允许本应用安装未知应用", Toast.LENGTH_LONG).show();
            Intent it = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(it);
            return;
        }
        final String url = apkUrl;
        Toast.makeText(activity, "正在下载更新包…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    File dir = activity.getExternalFilesDir("updates");
                    dir.mkdirs();
                    final File out = new File(dir, "update.apk");
                    conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.setRequestProperty("User-Agent", "xxa-notepad");
                    InputStream in = conn.getInputStream();
                    FileOutputStream fos = new FileOutputStream(out);
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
                    fos.close();
                    in.close();
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Intent it = new Intent(Intent.ACTION_VIEW);
                            it.setDataAndType(
                                    Uri.parse("content://" + AUTHORITY + "/update.apk"),
                                    "application/vnd.android.package-archive");
                            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            activity.startActivity(it);
                        }
                    });
                } catch (Exception e) {
                    toastOnMain("下载失败，请稍后重试");
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }

    private void toastOnMain(final String msg) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String getLocalVersionName() {
        try {
            PackageInfo pi = activity.getPackageManager().getPackageInfo(
                    activity.getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    /** "v2.4.5" / "2.4.5" -> [2, 4, 5] */
    private int[] parseVersion(String s) {
        String[] parts = s.split("[^0-9]+");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = parts[i].length() > 0 ? Integer.parseInt(parts[i]) : 0;
        }
        return out.length == 0 ? new int[]{0} : out;
    }

    private int compare(int[] a, int[] b) {
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) return x > y ? 1 : -1;
        }
        return 0;
    }
}
