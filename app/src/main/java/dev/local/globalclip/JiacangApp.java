package dev.local.globalclip;

import android.app.Application;
import android.os.Build;

/** 诊断版：安装全局崩溃记录，把堆栈写到系统下载目录。 */
public class JiacangApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable error) {
                try {
                    CrashReport.write(JiacangApp.this, "jiacang-crash.txt",
                            CrashReport.describe(error));
                } catch (Throwable ignored) {
                }
                if (previous != null) previous.uncaughtException(thread, error);
            }
        });
        try {
            CrashReport.write(this, "jiacang-boot.txt",
                    "boot\n" + CrashReport.describe(null)
                            + "\napp started, versionCode=" + 3 + ", SDK=" + Build.VERSION.SDK_INT);
        } catch (Throwable ignored) {
        }
    }
}
