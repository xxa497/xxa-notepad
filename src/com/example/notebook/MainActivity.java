package com.example.notebook;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
 * 记事本主页：
 * 顶部小栏（左侧 ☰ 拉出抽屉菜单：回收站 + 版本号）+ 记事列表 + 底部发送栏。
 * 每条记事：时间 + 正文 + 标签圆（可横滑）+ 设置按钮（编辑/添加标签/向下插入/删除）。
 */
public class MainActivity extends Activity implements NoteAdapter.Callback {

    /** 弹窗统一使用浅色 Material 风格（白底、蓝色按钮，避免黑色标题栏） */
    private static final int DIALOG_THEME = android.R.style.Theme_Material_Light_Dialog_Alert;

    private NoteStorage storage;
    private NoteAdapter adapter;
    private EditText etInput;
    private TextView tvEmpty;
    private TextView tvInsertHint;

    private View drawerMenu;
    private View drawerScrim;
    private boolean drawerOpen = false;

    /** 向下插入模式：-1 表示普通发送，>=0 表示插到该位置下方 */
    private int insertBelowIndex = -1;

    /** 进入私密记录的请求码与当前流程（set=首次设置，verify=验证） */
    private static final int REQ_ENTER_PRIVATE = 20;
    private static final int REQ_PICK_IMAGE = 30;   // 相册选图
    private static final int REQ_TAKE_PHOTO = 31;   // 拍照
    private static final int REQ_PICK_FILE = 32;    // 选择附件文件
    private static final int REQ_PERM_FILE = 33;    // 文件读取权限申请
    private static final int REQ_PERM_CAMERA = 34;   // 拍照权限申请
    private String pendingEnterMode = "verify";

    /** 待随下一条记事一起保存的图片文件名列表（空=无图） */
    private java.util.List<String> pendingImages = new java.util.ArrayList<String>();
    /** 待随下一条记事一起保存的附件文件名列表 */
    private java.util.List<String> pendingFiles = new java.util.ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 首次启动或版本升级后，先弹权限与隐私协议页
        android.content.SharedPreferences sp =
                getSharedPreferences(FirstRunActivity.PREFS, MODE_PRIVATE);
        int agreed = sp.getInt(FirstRunActivity.KEY_AGREED_VERSION, 0);
        if (agreed < 16) {
            startActivity(new Intent(this, FirstRunActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        storage = NoteStorage.get(this);
        initViews();
        reload();
    }

    private void initViews() {
        ListView listView = (ListView) findViewById(R.id.list_notes);
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
        final View batchBar = findViewById(R.id.batch_bar);
        final TextView tvBatchCount = (TextView) findViewById(R.id.tv_batch_count);
        findViewById(R.id.btn_batch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enterBatchMode(!adapter.isBatchMode());
                tvBatchCount.setText("已选 " + adapter.getSelected().size() + " 条");
            }
        });
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (adapter.isBatchMode()) {
                adapter.toggleSelection(position);
                tvBatchCount.setText("已选 " + adapter.getSelected().size() + " 条");
            }
        });
        findViewById(R.id.btn_batch_cancel).setOnClickListener(v -> enterBatchMode(false));
        findViewById(R.id.btn_batch_delete).setOnClickListener(v -> {
            final java.util.List<Integer> positions = new java.util.ArrayList<>(adapter.getSelected());
            if (positions.isEmpty()) {
                Toast.makeText(this, "还没勾选任何记录", Toast.LENGTH_SHORT).show();
                return;
            }
            showConfirmDialog("确定把选中的 " + positions.size() + " 条记录删到回收站吗？", () -> {
                java.util.List<Note> all = storage.getNotes();
                java.util.List<Note> toMove = new java.util.ArrayList<>();
                java.util.Collections.sort(positions, java.util.Collections.reverseOrder());
                for (int p : positions) toMove.add(all.get(p));
                for (Note n : toMove) storage.moveToRecycle(n);
                enterBatchMode(false);
                reload();
            });
        });

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendNote();
            }
        });

        // 软键盘的“发送”键也可提交
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

        // ---- 左侧抽屉菜单：宽度 = 手机屏宽 / 3 ----
        drawerMenu = findViewById(R.id.drawer_menu);
        drawerScrim = findViewById(R.id.drawer_scrim);
        ViewGroup.LayoutParams lp = drawerMenu.getLayoutParams();
        lp.width = getResources().getDisplayMetrics().widthPixels / 3;
        drawerMenu.setLayoutParams(lp);
        drawerMenu.setTranslationX(-lp.width); // 初始藏在屏幕左侧外

        findViewById(R.id.btn_drawer).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDrawer(!drawerOpen);
            }
        });
        drawerScrim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDrawer(false);
            }
        });

        // 双击抽屉里的版本号卡片：联网检查 GitHub 上是否有新版本
        findViewById(R.id.tv_version).setOnClickListener(new View.OnClickListener() {
            private long lastTap = 0;
            @Override
            public void onClick(View v) {
                long now = System.currentTimeMillis();
                if (now - lastTap < 500) {
                    lastTap = 0;
                    new UpdateChecker(MainActivity.this).check();
                } else {
                    lastTap = now;
                }
            }
        });

        findViewById(R.id.card_recycle).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDrawer(false);
                startActivity(new Intent(MainActivity.this, RecycleBinActivity.class));
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
            }
        });

        // 私密记录：未设密码先设置，已设密码先验证
        findViewById(R.id.card_private).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDrawer(false);
                enterPrivate();
            }
        });

        // 设置
        findViewById(R.id.card_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDrawer(false);
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });

        // 点击提示条取消“向下插入”模式
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
        w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
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
                startCamera();
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

    /** 启动系统文件选择；第一次先申请存储权限 */
    private void startFilePicker() {
        if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQ_PERM_FILE);
            return;
        }
        launchFileIntent();
    }

    /** 启动拍照；先申请相机权限，避免国产 ROM 直接闪退 */
    private void startCamera() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA},
                    REQ_PERM_CAMERA);
            return;
        }
        launchCamera();
    }

    private void launchCamera() {
        try {
            Intent it = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            Uri out = Uri.parse("content://com.example.notebook.apkprovider/images/cam_tmp.jpg");
            it.putExtra(MediaStore.EXTRA_OUTPUT, out);
            startActivityForResult(it, REQ_TAKE_PHOTO);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开相机：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
        } else if (requestCode == REQ_PERM_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                Toast.makeText(this, "需要相机权限才能拍照，请在系统设置里授权", Toast.LENGTH_LONG).show();
            }
        }
    }

    /** 打开/关闭抽屉 */
    private void toggleDrawer(boolean open) {
        int w = getResources().getDisplayMetrics().widthPixels / 3;
        if (open) {
            drawerMenu.bringToFront();
        }
        ObjectAnimator anim;
        if (open) {
            anim = ObjectAnimator.ofFloat(drawerMenu, "translationX", -w, 0f);
        } else {
            anim = ObjectAnimator.ofFloat(drawerMenu, "translationX", 0f, -w);
        }
        anim.setDuration(200);
        anim.start();
        drawerScrim.setVisibility(open ? View.VISIBLE : View.GONE);
        drawerOpen = open;
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen) {
            toggleDrawer(false);
        } else {
            super.onBackPressed();
        }
    }

    /** 进入私密记录：未设密码走设置流程，已设密码走验证流程 */
    private void enterPrivate() {
        PasswordStore pwd = PasswordStore.get(this);
        pendingEnterMode = pwd.isSet() ? "verify" : "set";
        startLockActivity(pendingEnterMode);
    }

    /** 按当前密码方式（或默认数字）启动密码页 */
    private void startLockActivity(String mode) {
        PasswordStore pwd = PasswordStore.get(this);
        Intent it;
        if (PasswordStore.TYPE_PATTERN.equals(pwd.getType())) {
            it = new Intent(this, PatternLockActivity.class);
        } else {
            it = new Intent(this, PinLockActivity.class);
        }
        it.putExtra(PinLockActivity.EXTRA_MODE, mode);
        startActivityForResult(it, REQ_ENTER_PRIVATE);
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
                // 拍照结果直接写到了 images/cam_tmp.jpg，改名挂到 pendingImages
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
        } else if (requestCode == REQ_ENTER_PRIVATE) {
            if (resultCode == RESULT_OK) {
                // 密码通过（或设置完成），进入私密记录
                startActivity(new Intent(this, PrivateNotesActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            } else if (resultCode == PinLockActivity.CODE_SWITCH) {
                // 用户想切换密码方式：从数字换成图案或反过来
                PasswordStore pwd = PasswordStore.get(this);
                Intent it;
                if (PasswordStore.TYPE_PATTERN.equals(pwd.getType())) {
                    it = new Intent(this, PinLockActivity.class);
                } else {
                    it = new Intent(this, PatternLockActivity.class);
                }
                it.putExtra(PinLockActivity.EXTRA_MODE, pendingEnterMode);
                startActivityForResult(it, REQ_ENTER_PRIVATE);
            }
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

    /** 弹出美化过的确认删除对话框 */
    private void showConfirmDialog(String message, Runnable onConfirm) {
        android.app.Dialog d = new android.app.Dialog(this);
        d.setContentView(R.layout.dialog_confirm);
        ((TextView) d.findViewById(R.id.dlg_message)).setText(message);
        d.findViewById(R.id.dlg_cancel).setOnClickListener(v -> d.dismiss());
        d.findViewById(R.id.dlg_ok).setOnClickListener(v -> {
            d.dismiss();
            onConfirm.run();
        });
        // 让弹窗宽度撑满屏幕并留边距
        if (d.getWindow() != null) {
            android.view.WindowManager.LayoutParams lp = d.getWindow().getAttributes();
            lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
            d.getWindow().setAttributes(lp);
            d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        }
        d.show();
    }

    /** 进入/退出批量选择模式 */
    private void enterBatchMode(boolean on) {
        adapter.setBatchMode(on);
        findViewById(R.id.batch_bar).setVisibility(on ? View.VISIBLE : View.GONE);
        findViewById(R.id.bottom_input_bar).setVisibility(on ? View.GONE : View.VISIBLE);
        TextView tvBatchCount = (TextView) findViewById(R.id.tv_batch_count);
        if (tvBatchCount != null) tvBatchCount.setText("已选 0 条");
    }

    /** 发送新记事（普通发送 或 向下插入） */
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
        reload();
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

    /** 挂起条里的小方块缩略图（带右上角叉） */
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
        if (imgFile.exists()) {
            iv.setImageBitmap(decodeSampledThumb(imgFile));
        }
        wrap.addView(iv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView x = new TextView(this);
        x.setText("✕");
        x.setTextSize(12);
        x.setTextColor(0xFFFFFFFF);
        x.setBackgroundColor(0xAA000000);
        x.setGravity(android.view.Gravity.CENTER);
        int pad = (int) (2 * d);
        x.setPadding(pad, pad, pad, pad);
        FrameLayout.LayoutParams xp = new FrameLayout.LayoutParams(
                (int) (22 * d), (int) (22 * d), android.view.Gravity.TOP | android.view.Gravity.END);
        wrap.addView(x, xp);
        x.setOnClickListener(v -> onRemove.run());
        return wrap;
    }

    /** 挂起条里的文件长条（带右上角叉） */
    private View buildPendingFileRow(File f, Runnable onRemove) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
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
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        box.setLayoutParams(bp);

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
        String date = f.exists() ? new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
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

    private Bitmap decodeSampledThumb(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        int s = 1;
        while (o.outWidth / s > 400 || o.outHeight / s > 400) s *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = s;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private void reload() {
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload(); // 从回收站恢复数据后回到主页时刷新
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
                                startInsertBelow(storage.getNotes().indexOf(note));
                                break;
                            case 3:
                                deleteNote(note);
                                break;
                        }
                    }
                })
                .show();
    }

    @Override
    public void onTagDelete(Note note, String tag) {
        storage.removeTag(note, tag);
        reload();
    }

    /** 编辑本条内容 */
    private void showEditDialog(final Note note) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_input, null);
        final EditText et = (EditText) dialogView.findViewById(R.id.et_dialog_input);
        et.setHint("编辑内容");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setMaxLines(6);
        et.setText(note.content);
        et.setSelection(note.content.length());

        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("编辑记事")
                .setView(dialogView)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String c = et.getText().toString().trim();
                        if (c.length() == 0) {
                            Toast.makeText(MainActivity.this, "内容不能为空", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        storage.updateContent(note, c);
                        reload();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 添加标签：浅色弹窗，输入框撑满宽度，确定后加标签 */
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
                            Toast.makeText(MainActivity.this, "标签不能为空", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        storage.addTag(note, tag);
                        reload();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 向下插入：不弹输入框，复用底部发送栏编辑 */
    private void startInsertBelow(int index) {
        if (index < 0) {
            return;
        }
        insertBelowIndex = index;
        tvInsertHint.setVisibility(View.VISIBLE);
        etInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    /** 删除：移入回收站 */
    private void deleteNote(Note note) {
        storage.moveToRecycle(note);
        reload();
        Toast.makeText(this, "已移入回收站", Toast.LENGTH_SHORT).show();
    }
}
