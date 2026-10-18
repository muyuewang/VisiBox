package com.visibox.tv.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.visibox.tv.Setting;
import com.visibox.tv.ui.activity.HomeActivity;
import com.visibox.tv.ui.activity.LiveActivity;

/**
 * 开机自启：系统启动完成后拉起首页（设置里开启「开机自启」后生效）。
 */
public class BootStartReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!Setting.getBoot()) return;
        Intent launch = new Intent(context, HomeActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(launch);
    }
}
