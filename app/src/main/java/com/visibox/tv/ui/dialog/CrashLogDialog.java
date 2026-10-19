package com.visibox.tv.ui.dialog;

import android.app.Activity;

import com.visibox.tv.R;
import com.visibox.tv.utils.CrashLog;
import com.visibox.tv.utils.Notify;
import com.visibox.tv.utils.ResUtil;
import com.visibox.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 崩溃日志查看器。
 *
 * <p>设置页的入口：列出本机保存过的崩溃记录，可以逐条查看、一键复制、清空。
 * 用户报「闪退」时，让他到这里点「复制全部」再粘贴过来即可，不用教他抓 logcat。
 */
public final class CrashLogDialog {

    private static final String PATTERN = "MM-dd HH:mm:ss";

    private CrashLogDialog() {
    }

    public static void show(Activity activity) {
        List<File> files = CrashLog.list();
        if (files.isEmpty()) {
            Notify.show(R.string.crash_log_empty);
            return;
        }
        String[] labels = new String[files.size()];
        SimpleDateFormat format = new SimpleDateFormat(PATTERN, Locale.US);
        for (int i = 0; i < files.size(); i++) labels[i] = format.format(new Date(files.get(i).lastModified()));
        new MaterialAlertDialogBuilder(activity)
                .setTitle(ResUtil.getString(R.string.crash_log) + " (" + files.size() + ")")
                .setItems(labels, (dialog, which) -> detail(activity, files.get(which)))
                .setPositiveButton(R.string.crash_log_copy_all, (dialog, which) -> Util.copy(CrashLog.all()))
                .setNeutralButton(R.string.crash_log_clear, (dialog, which) -> {
                    CrashLog.clear();
                    Notify.show(R.string.crash_log_cleared);
                })
                .setNegativeButton(R.string.crash_details_close, null)
                .show();
    }

    private static void detail(Activity activity, File file) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(file.getName())
                .setMessage(CrashLog.read(file))
                .setPositiveButton(R.string.crash_log_copy_all, (dialog, which) -> Util.copy(CrashLog.read(file)))
                .setNegativeButton(R.string.crash_details_close, null)
                .show();
    }
}
