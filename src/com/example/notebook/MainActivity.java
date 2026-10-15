package com.example.notebook;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

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
    private String pendingEnterMode = "verify";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        findViewById(R.id.card_recycle).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDrawer(false);
                startActivity(new Intent(MainActivity.this, RecycleBinActivity.class));
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
        if (requestCode == REQ_ENTER_PRIVATE) {
            if (resultCode == RESULT_OK) {
                // 密码通过（或设置完成），进入私密记录
                startActivity(new Intent(this, PrivateNotesActivity.class));
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
            storage.insertNoteBelow(insertBelowIndex, content, now, timeText);
            insertBelowIndex = -1;
            tvInsertHint.setVisibility(View.GONE);
        } else {
            storage.addNoteTop(content, now, timeText);
        }
        etInput.setText("");
        reload();
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
