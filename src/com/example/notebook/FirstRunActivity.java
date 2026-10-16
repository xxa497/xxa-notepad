package com.example.notebook;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

/**
 * 首次启动 / 版本升级后弹出的权限说明 + 隐私协议页。
 * 用户点"同意并继续"后才进入主界面，并记录已同意的版本号。
 */
public class FirstRunActivity extends Activity {

    public static final String PREFS = "first_run";
    public static final String KEY_AGREED_VERSION = "agreed_version";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first_run);

        Button btnAgree = (Button) findViewById(R.id.btn_agree);
        btnAgree.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
                sp.edit().putInt(KEY_AGREED_VERSION, 16).apply();
                Intent it = new Intent(FirstRunActivity.this, MainActivity.class);
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(it);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
        });
    }
}
