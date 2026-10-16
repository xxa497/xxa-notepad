package com.example.notebook;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 设置页：修改密码 / 重置密码。
 * 修改密码：先输入原密码（按当前密码方式校验），通过后再设置新密码（可选择数字或图案）。
 * 重置密码：二次警告确认后，清除密码并清空私密记录区全部内容。
 */
public class SettingsActivity extends Activity {

    private static final int REQ_VERIFY_OLD = 10;
    private static final int REQ_SET_NEW = 11;
    private static final int DIALOG_THEME = android.R.style.Theme_Material_Light_Dialog_Alert;

    private PasswordStore pwd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        pwd = PasswordStore.get(this);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.item_change_pwd).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pwd.isSet()) {
                    startVerifyOld();
                } else {
                    // 没设过密码，直接进入设置
                    startSetNew();
                }
            }
        });
        findViewById(R.id.item_reset_pwd).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showResetConfirm();
            }
        });
        findViewById(R.id.item_changelog).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, ChangelogActivity.class));
            }
        });
        findViewById(R.id.item_license).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, LicenseActivity.class));
            }
        });
    }

    /** 重置密码警告确认 */
    private void showResetConfirm() {
        new AlertDialog.Builder(this, DIALOG_THEME)
                .setTitle("重置密码")
                .setMessage("此操作将清除密码，并把私密记录区里的所有内容一并删除，且无法恢复。确定要重置吗？")
                .setPositiveButton("重置", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        pwd.clear();
                        PrivateNoteStorage.get(SettingsActivity.this).clearAll();
                        Toast.makeText(SettingsActivity.this, "已重置，私密记录区已清空", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 按当前密码方式启动校验页 */
    private void startVerifyOld() {
        if (PasswordStore.TYPE_PATTERN.equals(pwd.getType())) {
            Intent it = new Intent(this, PatternLockActivity.class);
            it.putExtra(PatternLockActivity.EXTRA_MODE, "verify");
            startActivityForResult(it, REQ_VERIFY_OLD);
        } else {
            Intent it = new Intent(this, PinLockActivity.class);
            it.putExtra(PinLockActivity.EXTRA_MODE, "verify");
            startActivityForResult(it, REQ_VERIFY_OLD);
        }
    }

    /** 启动设置新密码页（默认数字，页内可切换图案） */
    private void startSetNew() {
        Intent it = new Intent(this, PinLockActivity.class);
        it.putExtra(PinLockActivity.EXTRA_MODE, "set");
        startActivityForResult(it, REQ_SET_NEW);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VERIFY_OLD) {
            if (resultCode == RESULT_OK) {
                startSetNew();
            } else if (resultCode == PinLockActivity.CODE_SWITCH) {
                // 用户想换成图案校验
                Intent it = new Intent(this, PatternLockActivity.class);
                it.putExtra(PatternLockActivity.EXTRA_MODE, "verify");
                startActivityForResult(it, REQ_VERIFY_OLD);
            }
        } else if (requestCode == REQ_SET_NEW) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "密码已修改", Toast.LENGTH_SHORT).show();
                finish();
            } else if (resultCode == PinLockActivity.CODE_SWITCH) {
                // 用户想设置成图案密码
                Intent it = new Intent(this, PatternLockActivity.class);
                it.putExtra(PatternLockActivity.EXTRA_MODE, "set");
                startActivityForResult(it, REQ_SET_NEW);
            }
        }
    }
}
