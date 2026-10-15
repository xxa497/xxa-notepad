package com.example.notebook;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 九宫格图案密码页。
 * extra mode = "set" / "verify"。成功返回 RESULT_OK；切换数字密码返回 RESULT_FIRST_USER。
 */
public class PatternLockActivity extends Activity {

    public static final String EXTRA_MODE = "mode";
    public static final int CODE_SWITCH = RESULT_FIRST_USER;

    private PasswordStore pwd;
    private boolean setMode;
    private String firstPattern = null;

    private TextView tvTip;
    private PatternLockView patternView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pattern);
        pwd = PasswordStore.get(this);
        setMode = "set".equals(getIntent().getStringExtra(EXTRA_MODE));

        tvTip = (TextView) findViewById(R.id.tv_tip);
        patternView = (PatternLockView) findViewById(R.id.pattern_view);
        TextView btnSwitch = (TextView) findViewById(R.id.btn_switch);

        tvTip.setText(setMode ? "请绘制新图案密码（至少连 4 个点）" : "请绘制原图案密码");
        // 验证模式下不允许切换密码方式，固定使用已设置的那种
        if (!setMode) {
            btnSwitch.setVisibility(View.GONE);
        }

        patternView.setOnPatternListener(new PatternLockView.OnPatternListener() {
            @Override
            public void onPatternComplete(String sequence, int count) {
                handle(sequence, count);
            }
        });
        btnSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(CODE_SWITCH);
                finish();
            }
        });
    }

    private void handle(String sequence, int count) {
        if (setMode) {
            if (count < 4) {
                Toast.makeText(this, "图案至少连 4 个点", Toast.LENGTH_SHORT).show();
                patternView.reset();
                return;
            }
            if (firstPattern == null) {
                firstPattern = sequence;
                tvTip.setText("请再绘制一次确认");
                patternView.reset();
            } else {
                if (!firstPattern.equals(sequence)) {
                    Toast.makeText(this, "两次图案不一致，请重来", Toast.LENGTH_SHORT).show();
                    firstPattern = null;
                    tvTip.setText("请绘制新图案密码（至少连 4 个点）");
                } else {
                    pwd.setPassword(PasswordStore.TYPE_PATTERN, sequence);
                    Toast.makeText(this, "图案密码已设置", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                }
                patternView.reset();
            }
        } else {
            if (pwd.verify(sequence)) {
                setResult(RESULT_OK);
                finish();
            } else {
                Toast.makeText(this, "图案错误", Toast.LENGTH_SHORT).show();
                patternView.reset();
            }
        }
    }
}
