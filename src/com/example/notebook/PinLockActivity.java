package com.example.notebook;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 数字密码页。
 * extra mode = "set"（设置新密码，需输入两次确认）/ "verify"（校验已设密码）。
 * 成功后 setResult(RESULT_OK)；点“改用图案密码”返回 RESULT_FIRST_USER 由调用方切换。
 */
public class PinLockActivity extends Activity {

    public static final String EXTRA_MODE = "mode";
    public static final int CODE_SWITCH = RESULT_FIRST_USER;

    private PasswordStore pwd;
    private boolean setMode;
    private String firstInput = null; // set 模式下第一次输入

    private TextView tvTip;
    private EditText etPin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);
        pwd = PasswordStore.get(this);
        setMode = "set".equals(getIntent().getStringExtra(EXTRA_MODE));

        tvTip = (TextView) findViewById(R.id.tv_tip);
        etPin = (EditText) findViewById(R.id.et_pin);
        Button btnOk = (Button) findViewById(R.id.btn_ok);
        TextView btnSwitch = (TextView) findViewById(R.id.btn_switch);

        tvTip.setText(setMode ? "请设置新数字密码（至少 4 位）" : "请输入原数字密码");
        // 验证模式下不允许切换密码方式，固定使用已设置的那种
        if (!setMode) {
            btnSwitch.setVisibility(View.GONE);
        }

        btnOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit();
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

    private void submit() {
        String input = etPin.getText().toString().trim();
        if (input.length() < 4) {
            Toast.makeText(this, "密码至少 4 位数字", Toast.LENGTH_SHORT).show();
            return;
        }
        if (setMode) {
            if (firstInput == null) {
                firstInput = input;
                etPin.setText("");
                tvTip.setText("请再输入一次确认");
            } else {
                if (!firstInput.equals(input)) {
                    Toast.makeText(this, "两次输入不一致，请重来", Toast.LENGTH_SHORT).show();
                    firstInput = null;
                    etPin.setText("");
                    tvTip.setText("请设置新数字密码（至少 4 位）");
                } else {
                    pwd.setPassword(PasswordStore.TYPE_PIN, input);
                    Toast.makeText(this, "数字密码已设置", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                }
            }
        } else {
            if (pwd.verify(input)) {
                setResult(RESULT_OK);
                finish();
            } else {
                Toast.makeText(this, "密码错误", Toast.LENGTH_SHORT).show();
                etPin.setText("");
            }
        }
    }
}
