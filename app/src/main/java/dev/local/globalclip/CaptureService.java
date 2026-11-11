package dev.local.globalclip;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.List;

/**
 * 常驻前台服务。
 *
 * Android 10+ 只允许「有焦点的应用 / 默认输入法 / 特权应用」读剪贴板，
 * 这个限制同时盖住了 getPrimaryClip、getPrimaryClipDescription 和变更回调。
 * 所以后台只能在别的应用里短暂弹一个 1×1、几乎透明的可聚焦悬浮窗，
 * 拿到焦点后读一次，200ms 内移除、把焦点还回去。
 *
 * 自己的界面在前台时直接读，不打扰输入法。
 */
public class CaptureService extends Service {
    static final String ACTION_PAUSE = "dev.local.globalclip.action.PAUSE";
    private static final long DEFAULT_POLL_MS = 5_000L;
    private static final long RETRY_MS = 60_000L;
    private static final int NOTIFICATION_ID = 7;
    private static final String CHANNEL_ID = "capture-v2";

    private static volatile boolean running;

    static boolean isRunning() {
        return running;
    }

    static boolean isEnabled(Context context) {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("enabled", false);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ClipStore.Listener storeListener = new ClipStore.Listener() {
        @Override
        public void onStoreChanged() {
            updateNotification();
        }
    };

    private ClipboardManager clipboard;
    private WindowManager windows;
    private ClipStore store;
    private boolean reading;
    private boolean warnedNoOverlay;
    private boolean listening;
    private String lastText;
    private View probeView;

    private final Runnable quickCheck = new Runnable() {
        @Override
        public void run() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    quickPoll();
                }
            });
        }
    };

    private final Runnable poller = new Runnable() {
        @Override
        public void run() {
            pollOnce();
            handler.postDelayed(this, pollInterval());
        }
    };

    private final Runnable retry = new Runnable() {
        @Override
        public void run() {
            flushPending();
            handler.postDelayed(this, RETRY_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        windows = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        store = ClipStore.get(this);
        store.addListener(storeListener);
        listening = true;
        running = true;
        CutMemory.setQuickCheck(quickCheck);
        createChannel();
        try {
            startForegroundCompat(buildNotification());
        } catch (Exception error) {
            running = false;
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_PAUSE.equals(intent.getAction())) {
            getSharedPreferences("settings", MODE_PRIVATE)
                    .edit().putBoolean("enabled", false).apply();
            stopForegroundCompat();
            stopSelf();
            return START_NOT_STICKY;
        }
        SharedPreferences settings = getSharedPreferences("settings", MODE_PRIVATE);
        if (!settings.getBoolean("enabled", true)) {
            stopForegroundCompat();
            stopSelf();
            return START_NOT_STICKY;
        }
        handler.removeCallbacks(poller);
        handler.removeCallbacks(retry);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // 服务刚启动时通常还在前台，直接读一次；用户刚复制的内容不会等下一轮
                if (isScreenUsable()) handleRead(readTextNow(), "copy");
            }
        }, 600L);
        handler.postDelayed(poller, 1_000L);
        handler.postDelayed(retry, RETRY_MS);
        updateNotification();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        listening = false;
        running = false;
        handler.removeCallbacksAndMessages(null);
        removeProbe();
        store.removeListener(storeListener);
        CutMemory.setQuickCheck(null);
        stopForegroundCompat();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private long pollInterval() {
        long value = getSharedPreferences("settings", MODE_PRIVATE)
                .getLong("intervalMs", DEFAULT_POLL_MS);
        if (value < 2_000L) value = DEFAULT_POLL_MS;
        return value;
    }

    private boolean syncEnabled() {
        return getSharedPreferences("settings", MODE_PRIVATE).getBoolean("sync", false);
    }

    private void quickPoll() {
        if (reading || !listening) return;
        if (!isScreenUsable()) return;
        if (!getSharedPreferences("settings", MODE_PRIVATE).getBoolean("enabled", true)) return;
        if (Settings.canDrawOverlays(this)) {
            if (isSelfForeground()) handleRead(readTextNow(), "copy");
            else readViaOverlay();
        } else {
            handleRead(readTextNow(), "copy");
        }
    }

    private void pollOnce() {
        if (reading || !listening) return;
        if (!isScreenUsable()) return;
        if (Settings.canDrawOverlays(this)) {
            if (isSelfForeground()) {
                handleRead(readTextNow(), "copy");
            } else {
                readViaOverlay();
            }
        } else {
            if (!warnedNoOverlay) {
                warnedNoOverlay = true;
                Toast.makeText(this, "还缺悬浮窗权限，后台读不到剪贴板", Toast.LENGTH_SHORT).show();
            }
            handleRead(readTextNow(), "copy");
        }
    }

    private boolean isScreenUsable() {
        PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (power == null || !power.isInteractive()) return false;
        KeyguardManager keyguard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        return keyguard == null || !keyguard.isKeyguardLocked();
    }

    private boolean isSelfForeground() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return false;
        ActivityManager.RunningAppProcessInfo info = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(info);
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
    }

    private void readViaOverlay() {
        if (reading) return;
        reading = true;
        final View probe = new View(this);
        probe.setFocusableInTouchMode(true);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;
        params.alpha = 0.01f;
        try {
            probeView = probe;
            windows.addView(probe, params);
        } catch (Exception error) {
            reading = false;
            probeView = null;
            return;
        }
        probe.requestFocus();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String text = readTextNow();
                removeProbe();
                reading = false;
                handleRead(text, CutMemory.matches(text) ? "cut" : "copy");
            }
        }, 200L);
    }

    private void removeProbe() {
        if (probeView == null) return;
        try {
            windows.removeView(probeView);
        } catch (Exception ignored) {
        }
        probeView = null;
    }

    private void handleRead(String text, String guessedSource) {
        if (text == null || text.trim().isEmpty()) {
            lastText = null;
            return;
        }
        if (InternalCopy.shouldIgnore(text)) {
            lastText = text;
            return;
        }
        if (text.equals(lastText)) return;
        lastText = text;
        String source = CutMemory.matches(text) ? "cut" : guessedSource;
        ClipStore.AddResult result = store.add(text, System.currentTimeMillis(), source);
        if (result == null) return;
        if (result.fresh) {
            updateNotification();
            if (syncEnabled()) postToHub(result.item);
        } else if (source.equals("cut") && !"cut".equals(result.item.source)) {
            // 同一内容补上了剪切标记
            updateNotification();
        }
    }

    private String readTextNow() {
        try {
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return null;
            ClipDescription description = clip.getDescription();
            if (description != null) {
                PersistableBundle extras = description.getExtras();
                // Android 13+ 对密码框等敏感内容会打标记，跳过不记
                if (extras != null && extras.getBoolean("android.content.extra.IS_SENSITIVE", false)) {
                    return null;
                }
            }
            CharSequence value = clip.getItemAt(0).coerceToText(this);
            return value == null ? null : value.toString();
        } catch (Exception error) {
            return null;
        }
    }

    private void postToHub(final ClipItem item) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String source = "manual".equals(item.source) ? "manual" : "copy";
                    if (HubSync.post(item.text, source)) store.markSynced(item.id);
                } catch (Exception ignored) {
                }
            }
        }, "clip-hub-post").start();
    }

    private void flushPending() {
        if (!syncEnabled()) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<ClipItem> pending = store.pendingSync(24);
                for (ClipItem item : pending) {
                    try {
                        String source = "manual".equals(item.source) ? "manual" : "copy";
                        if (HubSync.post(item.text, source)) store.markSynced(item.id);
                    } catch (Exception error) {
                        return; // 连不上就先停，下一轮再试
                    }
                }
            }
        }, "clip-hub-flush").start();
    }

    private void createChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "剪贴捕获", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("常驻通知；降低后台被系统回收的概率");
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        } catch (Exception ignored) {
        }
    }

    private Notification buildNotification() {
        Intent content = new Intent(this, MainActivity.class);
        content.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, content, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent pause = new Intent(this, CaptureService.class).setAction(ACTION_PAUSE);
        PendingIntent pauseIntent = PendingIntent.getService(
                this, 1, pause, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID);
        return builder
                .setSmallIcon(R.drawable.ic_stat_clip)
                .setColor(Ui.ACCENT)
                .setContentTitle("剪藏正在记录")
                .setContentText("已收下 " + store.size() + " 条 · 点开整理")
                .setContentIntent(contentIntent)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_stat_clip),
                        "暂停", pauseIntent).build())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    private void updateNotification() {
        if (!listening) return;
        try {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification());
        } catch (Exception ignored) {
        }
    }
}
