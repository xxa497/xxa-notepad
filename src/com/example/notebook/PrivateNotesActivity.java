package com.example.notebook;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 私密记录主页：进入前已通过密码校验。
 * 界面与主页一致：时间 + 正文 + 标签 + 设置（编辑/添加标签/向下插入/删除）。
 * 私密记录删除即永久删除，不进回收站。
 */
public class PrivateNotesActivity extends Activity implements NoteAdapter.Callback {

    private static final int DIALOG_THEME = android.R.style.Theme_Material_Light_Dialog_Alert;
    private static final int REQ_PICK_IMAGE = 30;
    private static final int REQ_TAKE_PHOTO = 31;
    private static final int REQ_PICK_FILE = 32;
    private static final int REQ_PERM_FILE = 33;

    private PrivateNoteStorage storage;
    private NoteAdapter adapter;
    private EditText etInput;
    private TextView tvEmpty;
    private TextView tvInsertHint;
    private int insertBelowIndex = -1;

    /** 待随下一条私密记录一起保存的图片文件名列表（空=无图） */
    private java.util.List<String> pendingImages = new java.util.ArrayList<String>();
    /** 待随下一条私密记录一起保存的附件文件名列表 */
    private java.util.List<String> pendingFiles = new java.util.ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_private);
        storage = PrivateNoteStorage.get(this);
        initViews();
    }

    private void initViews() {
        ListView listView = (ListView) findViewById(R.id.list_private);
        etInput = (EditText) findViewById(R.id.et_input);
        tvEmpty = (TextView) findViewById(R.id.tv_empty);
        tvInsertHint = (TextView) findViewById(R.id.tv_insert_hint);
        Button btnSend = (Button) findViewById(R.id.btn_send);

        listView.setEmptyView(tvEmpty);
        adapter = new NoteAdapter(this, storage.getNotes(), this);
        listView.setAdapter(adapter);
        adapter.setOnSelectionChangedListener(count -> {
            TextView tv = (TextView) findViewById(R.id.tv_batch_count);
            if (tv != null) tv.setText("已选 " + count + " 条");
        });

        // 批量选择模式
        final TextView tvBatchCount = (TextView) findViewById(R.id.tv_batch_count);
        findViewById(R.id.btn_batch).setOnClickListener(v -> {
            enterBatchMode(!adapter.isBatchMode());
            tvBatchCount.setText("已选 0 条");
        });
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (adapter.isBatchMode()) {
                adapter.toggleSelection(position);
                tvBatchCount.setText("已选 " + adapter.getSelected().size() + " 条");
            }
        });
        findViewById(R.id.btn_batch_cancel).setOnClickListener(v -> enterBatchMode(false));
        findViewById(R.id.btn_batch_delete).setOnClickListener(v -> {
            java.util.List<Integer> positions = new java.util.ArrayList<>(adapter.getSelected());
            if (positions.isEmpty()) {
                Toast.makeText(this, "还没勾选任何记录", Toast.LENGTH_SHORT).show();
                return;
            }
            new android.app.AlertDialog.Builder(this)
                    .setTitle("批量删除")
                    .setMessage("私密记录删除后不可恢复，确定删除选中的 " + positions.size() + " 条吗？")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除", (d, w) -> {
                        java.util.List<com.example.notebook.Note> all = storage.getNotes();
                        java.util.List<com.example.notebook.Note> toDel = new java.util.ArrayList<>();
                        java.util.Collections.sort(positions, java.util.Collections.reverseOrder());
                        for (int p : positions) toDel.add(all.get(p));
                        for (com.example.notebook.Note n : toDel) storage.deleteNote(n);
                        enterBatchMode(false);
                        adapter.notifyDataSetChanged();
                    })
                    .show();
        });

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendNote();
            }
        });
        etInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendNote();
                    return true;
                }
                return false;
            }
        });
        tvInsertHint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                insertBelowIndex = -1;
                tvInsertHint.setVisibility(View.GONE);
            }
        });

        // 加号：从底部弹出“相册选图 / 拍照”菜单
        findViewById(R.id.btn_add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddPhotoSheet();
            }
        });
    }

    /** 底部上拉菜单：相册选图 / 拍照 */
    private void showAddPhotoSheet() {
        final Dialog dialog = new Dialog(this);
        View sheet = getLayoutInflater().inflate(R.layout.dialog_add_photo, null);
        dialog.setContentView(sheet);
        Window w = dialog.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(0));
        w.setGravity(Gravity.BOTTOM);
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        sheet.findViewById(R.id.btn_pick_image).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                Intent it = new Intent(Intent.ACTION_PICK,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(it, REQ_PICK_IMAGE);
            }
        });
        sheet.findViewById(R.id.btn_take_photo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                Intent it = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                Uri out = Uri.parse("content://com.example.notebook.apkprovider/images/cam_tmp.jpg");
                it.putExtra(MediaStore.EXTRA_OUTPUT, out);
                startActivityForResult(it, REQ_TAKE_PHOTO);
            }
        });
        sheet.findViewById(R.id.btn_pick_file).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                startFilePicker();
            }
        });
        dialog.show();
    }

    private void startFilePicker() {
        if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQ_PERM_FILE);
            return;
        }
        launchFileIntent();
    }

    private void launchFileIntent() {
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("*/*");
        it.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(it, REQ_PICK_FILE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERM_FILE) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                launchFileIntent();
            } else {
                Toast.makeText(this, "需要存储权限才能读取文件", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_IMAGE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                String name = "img_" + System.currentTimeMillis() + ".jpg";
                if (copyUriToImages(data.getData(), name)) {
                    pendingImages.add(name);
                    refreshPendingStrip();
                } else {
                    Toast.makeText(this, "读取图片失败", Toast.LENGTH_SHORT).show();
                }
            }
        } else if (requestCode == REQ_TAKE_PHOTO) {
            if (resultCode == RESULT_OK) {
                File dir = getExternalFilesDir("images");
                File tmp = new File(dir, "cam_tmp.jpg");
                if (tmp.exists() && tmp.length() > 0) {
                    String name = "img_" + System.currentTimeMillis() + ".jpg";
                    if (tmp.renameTo(new File(dir, name))) {
                        pendingImages.add(name);
                        refreshPendingStrip();
                    }
                } else {
                    Toast.makeText(this, "拍照未成功", Toast.LENGTH_SHORT).show();
                }
            }
        } else if (requestCode == REQ_PICK_FILE) {
            if (resultCode == RESULT_OK && data != null) {
                int count = 0;
                if (data.getData() != null) {
                    count = copyOneFile(data.getData());
                } else if (data.getClipData() != null) {
                    for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                        count += copyOneFile(data.getClipData().getItemAt(i).getUri());
                    }
                }
                if (count > 0) {
                    refreshPendingStrip();
                }
            }
        }
    }

    /** 把一个选择的文件复制到 files 目录，返回 1 成功 / 0 失败 */
    private int copyOneFile(Uri uri) {
        InputStream in = null;
        FileOutputStream fos = null;
        try {
            String displayName = "文件";
            android.database.Cursor c = getContentResolver().query(uri,
                    new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) displayName = c.getString(0);
                } finally { c.close(); }
            }
            String stored = System.currentTimeMillis() + "_" + displayName.replaceAll("[\\\\/:*?\"<>|]", "_");
            File dir = getExternalFilesDir("files");
            dir.mkdirs();
            in = getContentResolver().openInputStream(uri);
            fos = new FileOutputStream(new File(dir, stored));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
            pendingFiles.add(stored);
            return 1;
        } catch (Exception e) {
            return 0;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (fos != null) fos.close(); } catch (Exception ignored) {}
        }
    }

    /** 把系统相册选来的 Uri 内容复制到本应用 images 目录 */
    private boolean copyUriToImages(Uri src, String outName) {
        InputStream in = null;
        FileOutputStream fos = null;
        try {
            File dir = getExternalFilesDir("images");
            dir.mkdirs();
            in = getContentResolver().openInputStream(src);
            fos = new FileOutputStream(new File(dir, outName));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (fos != null) fos.close(); } catch (Exception ignored) {}
        }
    }

    /** 进入/退出批量选择模式 */
    private void enterBatchMode(boolean on) {
        adapter.setBatchMode(on);
        findViewById(R.id.batch_bar).setVisibility(on ? View.VISIBLE : View.GONE);
        findViewById(R.id.bottom_input_bar).setVisibility(on ? View.GONE : View.VISIBLE);
        TextView tvBatchCount = (TextView) findViewById(R.id.tv_batch_count);
        if (tvBatchCount != null) tvBatchCount.setText("已选 0 条");
    }

    private void sendNote() {
        String content = etInput.getText().toString().trim();
        if (content.length() == 0) {
            Toast.makeText(this, "请输入需要记录的内容", Toast.LENGTH_SHORT).show();
            return;
        }
        long now = System.currentTimeMillis();
        String timeText = new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.getDefault())
                .format(new Date(now));
        if (insertBelowIndex >= 0) {
            storage.insertNoteBelow(insertBelowIndex, content, now, timeText, pendingImages, pendingFiles);
            insertBelowIndex = -1;
            tvInsertHint.setVisibility(View.GONE);
        } else {
            storage.addNoteTop(content, now, timeText, pendingImages, pendingFiles);
        }
        pendingImages = new java.util.ArrayList<String>();
        pendingFiles = new java.util.ArrayList<String>();
        refreshPendingStrip();
        etInput.setText("");
        adapter.notifyDataSetChanged();
    }

    /** 刷新输入栏上方的挂起附件预览条 */
    private void refreshPendingStrip() {
        android.widget.HorizontalScrollView scroll =
                (android.widget.HorizontalScrollView) findViewById(R.id.pending_scroll);
        LinearLayout box = (LinearLayout) findViewById(R.id.pending_container);
        box.removeAllViews();
        if (pendingImages.isEmpty() && pendingFiles.isEmpty()) {
            scroll.setVisibility(View.GONE);
            return;
        }
        scroll.setVisibility(View.VISIBLE);
        File imgDir = getExternalFilesDir("images");
        for (final String name : pendingImages) {
            box.addView(buildPendingThumb(new File(imgDir, name), new Runnable() {
                @Override public void run() {
                    pendingImages.remove(name);
                    refreshPendingStrip();
                }
            }));
        }
        File fileDir = getExternalFilesDir("files");
        for (final String stored : pendingFiles) {
            box.addView(buildPendingFileRow(new File(fileDir, stored), new Runnable() {
                @Override public void run() {
                    pendingFiles.remove(stored);
                    refreshPendingStrip();
                }
            }));
        }
    }

    private View buildPendingThumb(File imgFile, Runnable onRemove) {
        float d = getResources().getDisplayMetrics().density;
        FrameLayout wrap = new FrameLayout(this);
        int size = (int) (64 * d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(0, 0, (int) (8 * d), 0);
        wrap.setLayoutParams(lp);
        android.widget.ImageView iv = new android.widget.ImageView(this);
        iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundColor(0xFFEEEEEE);
        if (imgFile.exists()) iv.setImageBitmap(decodeThumb(imgFile));
        wrap.addView(iv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView x = new TextView(this);
        x.setText("✕");
        x.setTextSize(12);
        x.setTextColor(0xFFFFFFFF);
        x.setBackgroundColor(0xAA000000);
        x.setGravity(Gravity.CENTER);
        int pad = (int) (2 * d);
        x.setPadding(pad, pad, pad, pad);
        wrap.addView(x, new FrameLayout.LayoutParams(
                (int) (22 * d), (int) (22 * d), Gravity.TOP | Gravity.END));
        x.setOnClickListener(v -> onRemove.run());
        return wrap;
    }

    private View buildPendingFileRow(File f, Runnable onRemove) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(0xFFF0F4FF);
        int pad = (int) (10 * d);
        row.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                (int) (220 * d), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, (int) (8 * d), 0);
        row.setLayoutParams(lp);
        TextView icon = new TextView(this);
        icon.setText("📎");
        icon.setTextSize(20);
        row.addView(icon);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        String dn = f.exists() ? f.getName() : "文件";
        int us = dn.indexOf('_');
        if (us > 0 && us < 14) dn = dn.substring(us + 1);
        TextView name = new TextView(this);
        name.setText(dn);
        name.setTextSize(12);
        name.setSingleLine(true);
        name.setTextColor(0xFF333333);
        box.addView(name);
        TextView meta = new TextView(this);
        String date = f.exists() ? new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new Date(f.lastModified())) : "";
        String size = f.exists() ? humanSize(f.length()) : "";
        meta.setText(date + "　" + size);
        meta.setTextSize(10);
        meta.setTextColor(0xFF888888);
        box.addView(meta);
        row.addView(box);
        TextView x = new TextView(this);
        x.setText("✕");
        x.setTextSize(12);
        x.setTextColor(0xFF555555);
        x.setOnClickListener(v -> onRemove.run());
        row.addView(x);
        return row;
    }

    private android.graphics.Bitmap decodeThumb(File f) {
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        int s = 1;
        while (o.outWidth / s > 400 || o.outHeight / s > 400) s *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = s;
        return android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), o);
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    // ---------- NoteAdapter.Callback ----------

    @Override
    public void onSettings(final Note note, View anchor) {
        final String[] items = {"编辑", "添加标签", "向下插入", "删除"};
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("操作")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                showEditDialog(note);
                                break;
                            case 1:
                                showAddTagDialog(note);
                                break;
                            case 2:
                                int idx = storage.getNotes().indexOf(note);
                                if (idx >= 0) {
                                    insertBelowIndex = idx;
                                    tvInsertHint.setVisibility(View.VISIBLE);
                                    etInput.requestFocus();
                                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                                    if (imm != null) {
                                        imm.showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT);
                                    }
                                }
                                break;
                            case 3:
                                storage.deleteNote(note);
                                adapter.notifyDataSetChanged();
                                Toast.makeText(PrivateNotesActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                                break;
                        }
                    }
                })
                .show();
    }

    @Override
    public void onTagDelete(Note note, String tag) {
        storage.removeTag(note, tag);
        adapter.notifyDataSetChanged();
    }

    private void showEditDialog(final Note note) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_input, null);
        final EditText et = (EditText) dialogView.findViewById(R.id.et_dialog_input);
        et.setHint("编辑内容");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setMaxLines(6);
        et.setText(note.content);
        et.setSelection(note.content.length());

        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("编辑私密记录")
                .setView(dialogView)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String c = et.getText().toString().trim();
                        if (c.length() == 0) {
                            Toast.makeText(PrivateNotesActivity.this, "内容不能为空", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        storage.updateContent(note, c);
                        adapter.notifyDataSetChanged();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAddTagDialog(final Note note) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_input, null);
        final EditText et = (EditText) dialogView.findViewById(R.id.et_dialog_input);
        et.setHint("输入标签");
        et.setSingleLine(true);

        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("添加标签")
                .setView(dialogView)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String tag = et.getText().toString().trim();
                        if (tag.length() == 0) {
                            Toast.makeText(PrivateNotesActivity.this, "标签不能为空", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        storage.addTag(note, tag);
                        adapter.notifyDataSetChanged();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
