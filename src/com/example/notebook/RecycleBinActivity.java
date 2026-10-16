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
import java.util.Collections;
import java.util.List;

/**
 * 回收站：
 * 条目与主页记事同款式；扳手菜单有"恢复"和"彻底删除"；
 * 右上角垃圾桶进入多选模式，行左边出现圆圈勾选，批量彻底删除带二次警告。
 */
public class RecycleBinActivity extends Activity implements NoteAdapter.Callback {

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
        adapter.setOnSelectionChangedListener(count -> {
            TextView tv = (TextView) findViewById(R.id.tv_batch_count);
            if (tv != null) tv.setText("已选 " + count + " 条");
        });

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        final TextView tvBatchCount = (TextView) findViewById(R.id.tv_batch_count);
        // 右上角垃圾桶：切换批量选择模式
        findViewById(R.id.btn_delete_forever).setOnClickListener(v -> {
            if (storage.getRecycle().isEmpty()) {
                Toast.makeText(this, "回收站是空的", Toast.LENGTH_SHORT).show();
                return;
            }
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
            List<Integer> positions = new ArrayList<>(adapter.getSelected());
            if (positions.isEmpty()) {
                Toast.makeText(this, "还没勾选任何记录", Toast.LENGTH_SHORT).show();
                return;
            }
            confirmPermanentDelete(positions);
        });
    }

    private void enterBatchMode(boolean on) {
        adapter.setBatchMode(on);
        findViewById(R.id.batch_bar).setVisibility(on ? View.VISIBLE : View.GONE);
        TextView tvBatchCount = (TextView) findViewById(R.id.tv_batch_count);
        if (tvBatchCount != null) tvBatchCount.setText("已选 0 条");
    }

    /** 二次警告：彻底删除 */
    private void confirmPermanentDelete(List<Integer> positions) {
        List<Note> list = storage.getRecycle();
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("彻底删除")
                .setMessage("确定把选中的 " + positions.size() + " 条记录彻底删除吗？\n此操作不可恢复！")
                .setNegativeButton("取消", null)
                .setPositiveButton("再确认一次删除", (d, w) -> {
                    List<Note> toDelete = new ArrayList<>();
                    List<Integer> sorted = new ArrayList<>(positions);
                    Collections.sort(sorted, Collections.reverseOrder());
                    for (int p : sorted) toDelete.add(list.get(p));
                    storage.permanentDelete(toDelete);
                    enterBatchMode(false);
                    reload();
                    Toast.makeText(this, "已彻底删除 " + toDelete.size() + " 条", Toast.LENGTH_SHORT).show();
                })
                .show();
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
                .setItems(new String[]{"恢复", "彻底删除"}, (dialog, which) -> {
                    if (which == 0) {
                        storage.restore(note);
                        reload();
                        Toast.makeText(this, "已恢复", Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        new AlertDialog.Builder(this, DIALOG_THEME)
                                .setTitle("彻底删除")
                                .setMessage("确定把这条记录彻底删除吗？此操作不可恢复！")
                                .setNegativeButton("取消", null)
                                .setPositiveButton("删除", (d, w) -> {
                                    List<Note> one = new ArrayList<>();
                                    one.add(note);
                                    storage.permanentDelete(one);
                                    reload();
                                })
                                .show();
                    }
                })
                .show();
    }

    @Override
    public void onTagDelete(Note note, String tag) {
        storage.removeTag(note, tag);
        reload();
    }
}
