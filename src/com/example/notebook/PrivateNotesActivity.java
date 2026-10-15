package com.example.notebook;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
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
 * 私密记录主页：进入前已通过密码校验。
 * 界面与主页一致：时间 + 正文 + 标签 + 设置（编辑/添加标签/向下插入/删除）。
 * 私密记录删除即永久删除，不进回收站。
 */
public class PrivateNotesActivity extends Activity implements NoteAdapter.Callback {

    private static final int DIALOG_THEME = android.R.style.Theme_Material_Light_Dialog_Alert;

    private PrivateNoteStorage storage;
    private NoteAdapter adapter;
    private EditText etInput;
    private TextView tvEmpty;
    private TextView tvInsertHint;
    private int insertBelowIndex = -1;

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
            storage.insertNoteBelow(insertBelowIndex, content, now, timeText);
            insertBelowIndex = -1;
            tvInsertHint.setVisibility(View.GONE);
        } else {
            storage.addNoteTop(content, now, timeText);
        }
        etInput.setText("");
        adapter.notifyDataSetChanged();
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
