package com.example.notebook;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 给外部应用（系统安装器 / 系统相机）读取文件用的最小 FileProvider。
 * Android 7.0+ 不允许把 file:// Uri 传给外部应用，必须用 content://。
 * 为了不依赖 AndroidX，这里自己实现一个最简 Provider：
 *   /updates/xxx.apk  -> externalFilesDir/updates/ （更新包）
 *   /images/xxx.jpg   -> externalFilesDir/images/   （拍照输出）
 */
public class ApkFileProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String path = uri.getPath(); // 形如 /updates/update.apk 或 /images/cam_tmp.jpg 或 /files/xxx
        String dirName;
        if (path != null && path.startsWith("/images/")) {
            dirName = "images";
        } else if (path != null && path.startsWith("/files/")) {
            dirName = "files";
        } else {
            dirName = "updates";
        }
        File dir = getContext().getExternalFilesDir(dirName);
        if (dir != null && !dir.exists()) dir.mkdirs();
        File f = new File(dir, uri.getLastPathSegment());
        // 相机写入时文件还不存在，先创建空文件，否则 ParcelFileDescriptor.open 会失败
        try {
            if (!f.exists()) f.createNewFile();
        } catch (Exception e) {
            throw new FileNotFoundException(e.getMessage());
        }
        int access = "r".equals(mode) ? ParcelFileDescriptor.MODE_READ_ONLY : ParcelFileDescriptor.MODE_READ_WRITE;
        return ParcelFileDescriptor.open(f, access);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
