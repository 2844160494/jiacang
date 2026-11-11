package dev.local.globalclip;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/** 开机或应用更新后，如果捕获是开着的，就把前台服务拉起来。 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        SharedPreferences settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        if (!settings.getBoolean("enabled", false)) return;
        try {
            Intent service = new Intent(context, CaptureService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
            else context.startService(service);
        } catch (Exception ignored) {
            // 部分系统不允许开机直接起前台服务，手动打开应用即可恢复
        }
    }
}
