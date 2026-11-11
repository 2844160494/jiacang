package dev.local.globalclip;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 把启动/崩溃信息写到系统「下载」目录，方便把日志发回给开发者。 */
final class CrashReport {
    private static final String TAG = "JiacangDiag";

    private CrashReport() {
    }

    static String describe(Throwable error) {
        StringWriter writer = new StringWriter();
        PrintWriter print = new PrintWriter(writer);
        print.println("time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                .format(new Date()));
        print.println("device: " + Build.MANUFACTURER + " " + Build.MODEL
                + " / Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
        print.println("package: " + "dev.local.globalclip");
        print.println();
        if (error != null) error.printStackTrace(print);
        print.flush();
        return writer.toString();
    }

    static void write(Context context, String name, String text) {
        if (context == null || text == null) return;
        try {
            Log.e(TAG, name + "\n" + text);
        } catch (Throwable ignored) {
        }
        ContentResolver resolver = context.getContentResolver();
        if (resolver != null) {
            Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                Uri uri = resolver.insert(collection, values);
                if (uri != null) {
                    try (java.io.OutputStream output = resolver.openOutputStream(uri)) {
                        if (output != null) {
                            output.write(text.getBytes(StandardCharsets.UTF_8));
                            output.flush();
                        }
                    }
                    ContentValues done = new ContentValues();
                    done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    resolver.update(uri, done, null, null);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            File dir = context.getExternalFilesDir(null);
            if (dir == null) dir = context.getFilesDir();
            File file = new File(dir, name);
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(text.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {
        }
    }
}
