package com.example.notebook;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 开源许可证页：展示 MIT 许可证全文。
 */
public class LicenseActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_license);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        TextView tvContent = (TextView) findViewById(R.id.tv_content);
        tvContent.setText(readRawText(R.raw.license));
    }

    private String readRawText(int resId) {
        try {
            InputStream is = getResources().openRawResource(resId);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            is.close();
            return new String(baos.toByteArray(), "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
            return "暂无许可证信息。";
        }
    }
}
