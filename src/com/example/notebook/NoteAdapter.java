package com.example.notebook;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * 记事列表 / 回收站列表 共用适配器。
 * 条目款式一致：时间 + 正文 + 标签圆（可横滑）+ 设置按钮。
 * 具体操作由页面通过 Callback 决定。
 */
public class NoteAdapter extends BaseAdapter {

    public interface Callback {
        void onSettings(Note note, View anchor);

        void onTagDelete(Note note, String tag);
    }

    private final Activity activity;
    private final List<Note> data;
    private final Callback callback;

    public NoteAdapter(Activity activity, List<Note> data, Callback callback) {
        this.activity = activity;
        this.data = data;
        this.callback = callback;
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public Object getItem(int position) {
        return data.get(position);
    }

    @Override
    public long getItemId(int position) {
        return data.get(position).id;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = LayoutInflater.from(activity).inflate(R.layout.item_note, parent, false);
        }
        final Note note = data.get(position);

        TextView tvTime = (TextView) v.findViewById(R.id.tv_time);
        TextView tvContent = (TextView) v.findViewById(R.id.tv_content);
        LinearLayout tagsContainer = (LinearLayout) v.findViewById(R.id.tags_container);
        ImageButton btnSettings = (ImageButton) v.findViewById(R.id.btn_settings);

        tvTime.setText(note.timeText);
        tvContent.setText(note.content);

        // 标签：一个圆一个圆排开，可横向滑动；每个圆右上角有灰色小叉可删除该标签
        tagsContainer.removeAllViews();
        for (final String tag : note.tags) {
            View tagView = LayoutInflater.from(activity).inflate(R.layout.item_tag, tagsContainer, false);
            TextView tvTag = (TextView) tagView.findViewById(R.id.tv_tag);
            TextView btnTagDel = (TextView) tagView.findViewById(R.id.btn_tag_del);
            tvTag.setText(tag);
            btnTagDel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v1) {
                    if (callback != null) {
                        callback.onTagDelete(note, tag);
                    }
                }
            });
            tagsContainer.addView(tagView);
        }

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v1) {
                if (callback != null) {
                    callback.onSettings(note, btnSettings);
                }
            }
        });
        return v;
    }
}
