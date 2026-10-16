package com.example.notebook;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 九宫格图案密码 View：3x3 圆点，手指拖动连线。
 * 完成一次图案（抬手）时通过 OnPatternListener 回调已选中的点序号序列。
 */
public class PatternLockView extends View {

    public interface OnPatternListener {
        /** 手势完成，sequence 为选中点序号拼接（0~8），如 "0,4,8" */
        void onPatternComplete(String sequence, int count);
    }

    private static final int SIZE = 3;          // 3x3
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotSelectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<Integer> path = new ArrayList<Integer>();
    private OnPatternListener listener;
    private float moveX, moveY;
    private boolean drawing = false;

    private float cellW, cellH;

    public PatternLockView(Context context) {
        super(context);
        init();
    }

    public PatternLockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        dotPaint.setColor(0xFFBBBBBB);
        dotSelectedPaint.setColor(0xFF2B7DE9);
        linePaint.setColor(0xFF2B7DE9);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(8f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setOnPatternListener(OnPatternListener l) {
        this.listener = l;
    }

    public void reset() {
        path.clear();
        drawing = false;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cellW = w / (float) SIZE;
        cellH = h / (float) SIZE;
    }

    private float dotCenterX(int index) {
        return (index % SIZE + 0.5f) * cellW;
    }

    private float dotCenterY(int index) {
        return (index / SIZE + 0.5f) * cellH;
    }

    private int hitDot(float x, float y) {
        float r = Math.min(cellW, cellH) * 0.45f;
        for (int i = 0; i < SIZE * SIZE; i++) {
            float dx = x - dotCenterX(i);
            float dy = y - dotCenterY(i);
            if (dx * dx + dy * dy <= r * r) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                path.clear();
                drawing = true;
                int down = hitDot(x, y);
                if (down >= 0 && !path.contains(down)) {
                    path.add(down);
                }
                moveX = x;
                moveY = y;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                moveX = x;
                moveY = y;
                if (drawing) {
                    int hit = hitDot(x, y);
                    if (hit >= 0 && !path.contains(hit)) {
                        path.add(hit);
                    }
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                drawing = false;
                if (listener != null && path.size() >= 1) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < path.size(); i++) {
                        if (i > 0) sb.append(",");
                        sb.append(path.get(i));
                    }
                    listener.onPatternComplete(sb.toString(), path.size());
                }
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // 连线：已选中的点依次相连
        for (int i = 0; i + 1 < path.size(); i++) {
            canvas.drawLine(dotCenterX(path.get(i)), dotCenterY(path.get(i)),
                    dotCenterX(path.get(i + 1)), dotCenterY(path.get(i + 1)), linePaint);
        }
        // 拖动中：连到手指位置
        if (drawing && !path.isEmpty()) {
            int last = path.get(path.size() - 1);
            canvas.drawLine(dotCenterX(last), dotCenterY(last), moveX, moveY, linePaint);
        }
        // 圆点
        float r = Math.min(cellW, cellH) * 0.16f;
        for (int i = 0; i < SIZE * SIZE; i++) {
            boolean selected = path.contains(i);
            canvas.drawCircle(dotCenterX(i), dotCenterY(i), r, selected ? dotSelectedPaint : dotPaint);
        }
    }
}
