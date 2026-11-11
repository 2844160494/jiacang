package dev.local.globalclip;

import android.app.Application;

/** 崩溃时把堆栈写到系统下载目录，方便排查。 */
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
    }
}
