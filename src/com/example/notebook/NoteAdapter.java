package com.example.notebook;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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

    /** 勾选状态变化时回调（用于刷新"已选 N 条"） */
    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }

    private OnSelectionChangedListener selectionListener;

    public void setOnSelectionChangedListener(OnSelectionChangedListener l) {
        this.selectionListener = l;
    }

    private final Activity activity;
    private final List<Note> data;
    private final Callback callback;

    /** 是否处于批量选择模式 */
    private boolean batchMode = false;
    /** 已勾选的位置集合 */
    private final java.util.Set<Integer> selected = new java.util.HashSet<Integer>();

    public NoteAdapter(Activity activity, List<Note> data, Callback callback) {
        this.activity = activity;
        this.data = data;
        this.callback = callback;
    }

    public void setBatchMode(boolean on) {
        batchMode = on;
        selected.clear();
        notifyDataSetChanged();
    }

    public boolean isBatchMode() {
        return batchMode;
    }

    public void toggleSelection(int position) {
        if (selected.contains(position)) selected.remove(position);
        else selected.add(position);
        notifyDataSetChanged();
        if (selectionListener != null) selectionListener.onSelectionChanged(selected.size());
    }

    public boolean isSelected(int position) {
        return selected.contains(position);
    }

    public java.util.Set<Integer> getSelected() {
        return selected;
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
        final TextView checkbox = (TextView) v.findViewById(R.id.checkbox_circle);
        android.widget.HorizontalScrollView imagesScroll =
                (android.widget.HorizontalScrollView) v.findViewById(R.id.images_scroll);
        LinearLayout imagesContainer = (LinearLayout) v.findViewById(R.id.images_container);

        tvTime.setText(note.timeText);
        tvContent.setText(note.content);

        // 批量模式下显示圆形勾选框，点圆圈本身也能切换
        if (batchMode) {
            checkbox.setVisibility(View.VISIBLE);
            boolean on = selected.contains(position);
            checkbox.setText(on ? "✓" : "");
            checkbox.setBackgroundResource(on
                    ? R.drawable.bg_circle_checked : R.drawable.bg_circle_empty);
            checkbox.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { toggleSelection(position); }
            });
        } else {
            checkbox.setVisibility(View.GONE);
            checkbox.setOnClickListener(null);
        }

        // 附图：横向小方块横排，点可全屏预览
        imagesContainer.removeAllViews();
        if (note.images != null && !note.images.isEmpty()) {
            imagesScroll.setVisibility(View.VISIBLE);
            File dir = activity.getExternalFilesDir("images");
            for (String imgName : note.images) {
                imagesContainer.addView(buildImageThumb(new File(dir, imgName)));
            }
        } else {
            imagesScroll.setVisibility(View.GONE);
        }

        // 附件文件：一行一行列出，点可调系统打开
        LinearLayout filesContainer = (LinearLayout) v.findViewById(R.id.files_container);
        filesContainer.removeAllViews();
        if (note.files != null && !note.files.isEmpty()) {
            filesContainer.setVisibility(View.VISIBLE);
            File dir = activity.getExternalFilesDir("files");
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            for (String storedName : note.files) {
                File f = new File(dir, storedName);
                int us = storedName.indexOf('_');
                String displayName = (us > 0 && us < 14) ? storedName.substring(us + 1) : storedName;
                View row = buildFileRow(displayName, f.exists() ? f : null, fmt);
                row.setClickable(true);
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View w) {
                        openFile(f);
                    }
                });
                filesContainer.addView(row);
            }
        } else {
            filesContainer.setVisibility(View.GONE);
        }

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

    /** 按目标最大边长采样解码，防止大图 OOM */
    private Bitmap decodeSampled(File f, int targetMax) {
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), opt);
        int sample = 1;
        while (opt.outWidth / sample > targetMax || opt.outHeight / sample > targetMax) {
            sample *= 2;
        }
        opt.inJustDecodeBounds = false;
        opt.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), opt);
    }

    /** 列表里的小方块缩略图，点击全屏预览 */
    private View buildImageThumb(final File imgFile) {
        float density = activity.getResources().getDisplayMetrics().density;
        ImageView iv = new ImageView(activity);
        int size = (int) (72 * density);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(0, 0, (int) (6 * density), 0);
        iv.setLayoutParams(lp);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundColor(Color.parseColor("#F0F0F0"));
        if (imgFile.exists()) {
            iv.setImageBitmap(decodeSampled(imgFile, 400));
            iv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View w) {
                    Intent it = new Intent(Intent.ACTION_VIEW);
                    Uri u = Uri.parse("content://com.example.notebook.apkprovider/images/"
                            + imgFile.getName());
                    it.setDataAndType(u, "image/*");
                    it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
                        activity.startActivity(it);
                    } catch (Exception ignored) {
                    }
                }
            });
        }
        return iv;
    }

    /** 调系统应用打开附件文件 */
    private void openFile(File f) {
        if (!f.exists()) return;
        Intent it = new Intent(Intent.ACTION_VIEW);
        Uri u = Uri.parse("content://com.example.notebook.apkprovider/files/" + f.getName());
        it.setDataAndType(u, "*/*");
        it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivity(it);
        } catch (Exception e) {
            android.widget.Toast.makeText(activity, "没有可打开此文件的应用",
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /** 构造一行附件条目：📎 + 文件名 + 修改日期/大小 */
    private View buildFileRow(String displayName, File f, SimpleDateFormat fmt) {
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int pad = (int) (10 * density);
        row.setPadding(pad, pad, pad, pad);
        row.setBackgroundColor(Color.parseColor("#F0F4FF"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (6 * density);
        row.setLayoutParams(lp);

        TextView icon = new TextView(activity);
        icon.setText("📎");
        icon.setTextSize(22);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                (int) (36 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
        icon.setLayoutParams(ip);

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView name = new TextView(activity);
        name.setText(displayName);
        name.setTextColor(Color.parseColor("#333333"));
        name.setTextSize(14);
        name.setSingleLine(true);
        box.addView(name);

        TextView meta = new TextView(activity);
        String date = (f != null) ? fmt.format(new Date(f.lastModified())) : "未知";
        String size = (f != null) ? humanSize(f.length()) : "未知";
        meta.setText("修改时间：" + date + "　大小：" + size);
        meta.setTextColor(Color.parseColor("#888888"));
        meta.setTextSize(11);
        box.addView(meta);

        row.addView(icon);
        row.addView(box);
        return row;
    }

    /** 字节数转可读大小 */
    private String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
