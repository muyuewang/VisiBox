package com.visibox.tv.ui.activity;

import android.os.Bundle;
import android.text.TextUtils;

import androidx.appcompat.app.AlertDialog;
import androidx.viewbinding.ViewBinding;

import com.visibox.tv.R;
import com.visibox.tv.databinding.ActivityCrashBinding;
import com.visibox.tv.ui.base.BaseActivity;
import com.visibox.tv.utils.CrashLog;
import com.visibox.tv.utils.Util;
import com.github.catvod.utils.Prefers;

import java.util.Objects;

import cat.ereza.customactivityoncrash.CustomActivityOnCrash;

public class CrashActivity extends BaseActivity {

    private ActivityCrashBinding mBinding;

    @Override
    protected boolean customWall() {
        return false;
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCrashBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        saveLog();
        setCrash();
    }

    @Override
    protected void initEvent() {
        mBinding.details.setOnClickListener(v -> showError());
        mBinding.copy.setOnClickListener(v -> Util.copy(details()));
        mBinding.restart.setOnClickListener(v -> CustomActivityOnCrash.restartApplication(this, Objects.requireNonNull(CustomActivityOnCrash.getConfigFromIntent(getIntent()))));
    }

    /**
     * 崩溃详情只活在 intent 里，用户点一次「重新启动」就没了。
     * 在 onCreate 先落盘，之后可以在设置页「崩溃日志」里翻出来。
     */
    private void saveLog() {
        try {
            CrashLog.save(details());
        } catch (Throwable ignored) {
            // 已经在崩溃路径上，这里再抛就彻底没界面了
        }
    }

    private String details() {
        String details = CustomActivityOnCrash.getAllErrorDetailsFromIntent(this, getIntent());
        return details == null ? "" : details;
    }

    private void setCrash() {
        String log = CustomActivityOnCrash.getActivityLogFromIntent(getIntent());
        if (TextUtils.isEmpty(log)) return;
        String[] lines = log.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].isEmpty()) continue;
            if (lines[i].contains(HomeActivity.class.getSimpleName())) {
                Prefers.put("crash", true);
                break;
            }
        }
    }

    private void showError() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.crash_details_title)
                .setMessage(details())
                .setPositiveButton(R.string.crash_details_close, null)
                .show();
    }
}
