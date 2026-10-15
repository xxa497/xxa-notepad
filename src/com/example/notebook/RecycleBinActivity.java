package com.example.notebook;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 回收站：
 * 条目与主页记事同款式；设置菜单只有“恢复”；
 * 右上角垃圾桶进入多选，可永久删除。
 */
public class RecycleBinActivity extends Activity implements NoteAdapter.Callback {

    /** 弹窗统一使用浅色 Material 风格 */
    private static final int DIALOG_THEME = android.R.style.Theme_Material_Light_Dialog_Alert;

    private NoteStorage storage;
    private NoteAdapter adapter;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recycle);
        storage = NoteStorage.get(this);

        ListView listView = (ListView) findViewById(R.id.list_recycle);
        tvEmpty = (TextView) findViewById(R.id.tv_empty);
        listView.setEmptyView(tvEmpty);

        adapter = new NoteAdapter(this, storage.getRecycle(), this);
        listView.setAdapter(adapter);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.btn_delete_forever).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPermanentDeleteDialog();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        adapter.notifyDataSetChanged();
    }

    // ---------- NoteAdapter.Callback ----------

    @Override
    public void onSettings(final Note note, View anchor) {
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("操作")
                .setItems(new String[]{"恢复"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        storage.restore(note);
                        reload();
                        Toast.makeText(RecycleBinActivity.this, "已恢复", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    @Override
    public void onTagDelete(Note note, String tag) {
        storage.removeTag(note, tag);
        reload();
    }

    /** 右上角垃圾桶：多选后永久删除 */
    private void showPermanentDeleteDialog() {
        final List<Note> list = storage.getRecycle();
        if (list.isEmpty()) {
            Toast.makeText(this, "回收站是空的", Toast.LENGTH_SHORT).show();
            return;
        }
        final boolean[] checked = new boolean[list.size()];
        String[] titles = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            String c = list.get(i).content.replace('\n', ' ');
            titles[i] = (c.length() > 24 ? c.substring(0, 24) + "…" : c);
        }
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("选择要永久删除的记事")
                .setMultiChoiceItems(titles, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        checked[which] = isChecked;
                    }
                })
                .setPositiveButton("永久删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        List<Note> toDelete = new ArrayList<Note>();
                        for (int i = 0; i < checked.length; i++) {
                            if (checked[i]) {
                                toDelete.add(list.get(i));
                            }
                        }
                        if (toDelete.isEmpty()) {
                            Toast.makeText(RecycleBinActivity.this, "未选择任何记事", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        storage.permanentDelete(toDelete);
                        reload();
                        Toast.makeText(RecycleBinActivity.this, "已永久删除 " + toDelete.size() + " 条", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
