package dev.local.globalclip;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SQLite + 内存索引的剪贴历史。
 * 同一个内容只保留一行，重复复制会把次数 +1、时间刷新到最前。
 */
public class ClipStore {
    public static final int MAX_TEXT = 12_000;
    public static final int MAX_ROWS = 2_000;

    public interface Listener {
        void onStoreChanged();
    }

    public static final class AddResult {
        public final ClipItem item;
        public final boolean fresh;

        AddResult(ClipItem item, boolean fresh) {
            this.item = item;
            this.fresh = fresh;
        }
    }

    public static final class Stats {
        public int total;
        public int today;
        public int week;
        public int pinned;
        public int pendingSync;
        public long dbBytes;
    }

    private static ClipStore instance;

    public static synchronized ClipStore get(Context context) {
        if (instance == null) instance = new ClipStore(context.getApplicationContext());
        return instance;
    }

    private final Object lock = new Object();
    private final Context context;
    private final SharedPreferences legacy;
    private final Db db;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayList<ClipItem> items = new ArrayList<>();
    private final HashMap<String, ClipItem> byHash = new HashMap<>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private ClipStore(Context context) {
        this.context = context;
        this.legacy = context.getSharedPreferences("clips", Context.MODE_PRIVATE);
        this.db = new Db(context);
        load();
        migrateLegacy();
        synchronized (lock) {
            trimLocked();
        }
    }

    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void notifyChanged() {
        if (listeners.isEmpty()) return;
        main.post(new Runnable() {
            @Override
            public void run() {
                for (Listener listener : listeners) {
                    try {
                        listener.onStoreChanged();
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }

    private void load() {
        synchronized (lock) {
            Cursor cursor = null;
            try {
                cursor = db.getReadableDatabase().rawQuery(
                        "SELECT id, hash, text, category, title, detail, extract, source, ts, "
                                + "count, pinned, synced FROM items ORDER BY ts DESC LIMIT " + MAX_ROWS,
                        null);
                while (cursor.moveToNext()) {
                    ClipItem item = new ClipItem();
                    item.id = cursor.getString(0);
                    String hash = cursor.getString(1);
                    item.text = cursor.getString(2);
                    item.category = cursor.getString(3);
                    item.title = cursor.getString(4);
                    item.detail = cursor.getString(5);
                    item.extract = cursor.getString(6);
                    item.source = cursor.getString(7);
                    item.ts = cursor.getLong(8);
                    item.count = Math.max(1, cursor.getInt(9));
                    item.pinned = cursor.getInt(10) != 0;
                    item.synced = cursor.getInt(11) != 0;
                    if (item.text == null || item.text.isEmpty()) continue;
                    if (item.category == null) {
                        ClipClassifier.Result result = ClipClassifier.analyze(item.text);
                        if (result != null) {
                            item.category = result.category;
                            item.title = result.title;
                            item.detail = result.detail;
                            item.extract = result.extract;
                        }
                    }
                    if (hash == null) hash = sha256(item.text);
                    items.add(item);
                    byHash.put(hash, item);
                }
            } catch (Exception ignored) {
            } finally {
                if (cursor != null) cursor.close();
            }
            sortItems();
        }
    }

    /** 把 1.0 的 SharedPreferences JSON 历史搬进数据库，只搬一次。 */
    private void migrateLegacy() {
        synchronized (lock) {
            if (legacy.getBoolean("migrated_v2", false)) return;
            if (!items.isEmpty()) {
                legacy.edit().putBoolean("migrated_v2", true).apply();
                return;
            }
            try {
                String raw = legacy.getString("items", "[]");
                JSONArray array = new JSONArray(raw);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject object = array.optJSONObject(i);
                    if (object == null) continue;
                    String text = ClipClassifier.clean(object.optString("text", ""));
                    if (text.isEmpty()) continue;
                    if (text.length() > MAX_TEXT) text = text.substring(0, MAX_TEXT);
                    String hash = sha256(text);
                    if (byHash.containsKey(hash)) continue;
                    ClipClassifier.Result result = ClipClassifier.analyze(text);
                    if (result == null) continue;
                    ClipItem item = new ClipItem();
                    item.id = object.optString("id", UUID.randomUUID().toString());
                    item.text = text;
                    item.category = result.category;
                    item.title = result.title;
                    item.detail = result.detail;
                    item.extract = result.extract;
                    item.source = "copy";
                    item.ts = object.optLong("ts", System.currentTimeMillis());
                    item.count = 1;
                    item.pinned = false;
                    item.synced = object.optBoolean("synced", false);
                    insert(item, hash);
                    items.add(item);
                    byHash.put(hash, item);
                }
                sortItems();
                legacy.edit().putBoolean("migrated_v2", true).apply();
            } catch (Exception ignored) {
                // 数据没搬成功就不打标记，下次再试
            }
        }
    }

    private void sortItems() {
        Collections.sort(items, new Comparator<ClipItem>() {
            @Override
            public int compare(ClipItem a, ClipItem b) {
                if (a.ts != b.ts) return a.ts > b.ts ? -1 : 1;
                return a.id.compareTo(b.id);
            }
        });
    }

    private static void sortInsert(ArrayList<ClipItem> list, ClipItem item) {
        int index = 0;
        while (index < list.size() && list.get(index).ts >= item.ts) index++;
        list.add(index, item);
    }

    public AddResult add(String raw, long ts, String source) {
        String text = ClipClassifier.clean(raw);
        if (text.isEmpty()) return null;
        if (text.length() > MAX_TEXT) text = text.substring(0, MAX_TEXT);
        String hash = sha256(text);
        AddResult result;
        synchronized (lock) {
            ClipItem existing = byHash.get(hash);
            if (existing != null) {
                existing.ts = ts;
                existing.count++;
                if ("cut".equals(source)) existing.source = "cut";
                else if (existing.source == null) existing.source = "copy";
                items.remove(existing);
                sortInsert(items, existing);
                update(existing);
                result = new AddResult(existing, false);
            } else {
                ClipClassifier.Result verdict = ClipClassifier.analyze(text);
                if (verdict == null) return null;
                ClipItem item = new ClipItem();
                item.id = UUID.randomUUID().toString();
                item.text = text;
                item.category = verdict.category;
                item.title = verdict.title;
                item.detail = verdict.detail;
                item.extract = verdict.extract;
                item.source = "cut".equals(source) ? "cut" : ("manual".equals(source) ? "manual" : "copy");
                item.ts = ts;
                item.count = 1;
                item.pinned = false;
                item.synced = false;
                insert(item, hash);
                sortInsert(items, item);
                byHash.put(hash, item);
                result = new AddResult(item, true);
            }
            trimLocked();
        }
        notifyChanged();
        return result;
    }

    private void trimLocked() {
        while (items.size() > MAX_ROWS) {
            ClipItem victim = null;
            for (int i = items.size() - 1; i >= 0; i--) {
                ClipItem candidate = items.get(i);
                if (!candidate.pinned) {
                    victim = candidate;
                    break;
                }
            }
            if (victim == null) break;
            items.remove(victim);
            byHash.remove(sha256(victim.text));
            try {
                db.getWritableDatabase().delete("items", "id=?", new String[]{victim.id});
            } catch (Exception ignored) {
            }
        }
    }

    private void insert(ClipItem item, String hash) {
        try {
            ContentValues values = toValues(item, hash);
            db.getWritableDatabase().insertWithOnConflict(
                    "items", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception ignored) {
        }
    }

    private void update(ClipItem item) {
        try {
            String hash = sha256(item.text);
            ContentValues values = toValues(item, hash);
            db.getWritableDatabase().update("items", values, "id=?", new String[]{item.id});
        } catch (Exception ignored) {
        }
    }

    private static ContentValues toValues(ClipItem item, String hash) {
        ContentValues values = new ContentValues();
        values.put("id", item.id);
        values.put("hash", hash);
        values.put("text", item.text);
        values.put("category", item.category);
        values.put("title", item.title);
        values.put("detail", item.detail);
        values.put("extract", item.extract);
        values.put("source", item.source);
        values.put("ts", item.ts);
        values.put("count", item.count);
        values.put("pinned", item.pinned ? 1 : 0);
        values.put("synced", item.synced ? 1 : 0);
        return values;
    }

    public List<ClipItem> all() {
        synchronized (lock) {
            return new ArrayList<>(items);
        }
    }

    public int size() {
        synchronized (lock) {
            return items.size();
        }
    }

    public HashMap<String, Integer> counts() {
        synchronized (lock) {
            HashMap<String, Integer> map = new HashMap<>();
            for (ClipItem item : items) {
                String key = item.category == null ? ClipCategory.TEXT : item.category;
                Integer count = map.get(key);
                map.put(key, count == null ? 1 : count + 1);
            }
            return map;
        }
    }

    public Stats stats() {
        Stats stats = new Stats();
        Calendar now = Calendar.getInstance();
        Calendar todayStart = (Calendar) now.clone();
        todayStart.set(Calendar.HOUR_OF_DAY, 0);
        todayStart.set(Calendar.MINUTE, 0);
        todayStart.set(Calendar.SECOND, 0);
        todayStart.set(Calendar.MILLISECOND, 0);
        Calendar weekStart = (Calendar) todayStart.clone();
        int dow = weekStart.get(Calendar.DAY_OF_WEEK);
        int back = (dow + 5) % 7;
        weekStart.add(Calendar.DAY_OF_YEAR, -back);
        synchronized (lock) {
            for (ClipItem item : items) {
                stats.total++;
                if (item.ts >= todayStart.getTimeInMillis()) stats.today++;
                if (item.ts >= weekStart.getTimeInMillis()) stats.week++;
                if (item.pinned) stats.pinned++;
                if (!item.synced) stats.pendingSync++;
            }
        }
        File file = context.getDatabasePath("clips-v2.db");
        stats.dbBytes = file.exists() ? file.length() : 0;
        return stats;
    }

    public void setPinned(String id, boolean pinned) {
        synchronized (lock) {
            ClipItem item = findById(id);
            if (item == null) return;
            item.pinned = pinned;
            update(item);
        }
        notifyChanged();
    }

    public ClipItem remove(String id) {
        ClipItem removed = null;
        synchronized (lock) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).id.equals(id)) {
                    removed = items.remove(i);
                    break;
                }
            }
            if (removed == null) return null;
            byHash.remove(sha256(removed.text));
            try {
                db.getWritableDatabase().delete("items", "id=?", new String[]{id});
            } catch (Exception ignored) {
            }
        }
        notifyChanged();
        return removed;
    }

    /** 撤销删除用：按原时间、原次数、原置顶状态放回去。 */
    public void restore(ClipItem item) {
        if (item == null || item.text == null) return;
        String hash = sha256(item.text);
        synchronized (lock) {
            if (byHash.containsKey(hash)) {
                ClipItem existing = byHash.get(hash);
                existing.ts = Math.max(existing.ts, item.ts);
                update(existing);
            } else {
                insert(item, hash);
                sortInsert(items, item);
                byHash.put(hash, item);
            }
        }
        notifyChanged();
    }

    public void clear(boolean keepPinned) {
        synchronized (lock) {
            if (keepPinned) {
                ArrayList<ClipItem> remaining = new ArrayList<>();
                for (ClipItem item : items) {
                    if (item.pinned) remaining.add(item);
                    else byHash.remove(sha256(item.text));
                }
                items.clear();
                items.addAll(remaining);
                try {
                    db.getWritableDatabase().delete("items", "pinned=0", null);
                } catch (Exception ignored) {
                }
            } else {
                items.clear();
                byHash.clear();
                try {
                    db.getWritableDatabase().delete("items", null, null);
                } catch (Exception ignored) {
                }
            }
        }
        notifyChanged();
    }

    public List<ClipItem> pendingSync(int limit) {
        ArrayList<ClipItem> pending = new ArrayList<>();
        synchronized (lock) {
            for (ClipItem item : items) {
                if (!item.synced) pending.add(item);
                if (pending.size() >= limit) break;
            }
        }
        return pending;
    }

    public void markSynced(String id) {
        synchronized (lock) {
            ClipItem item = findById(id);
            if (item == null || item.synced) return;
            item.synced = true;
            update(item);
        }
    }

    private ClipItem findById(String id) {
        for (ClipItem item : items) {
            if (item.id.equals(id)) return item;
        }
        return null;
    }

    public static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) builder.append(String.format(Locale.ROOT, "%02x", value));
            return builder.toString();
        } catch (Exception error) {
            return Integer.toHexString(text.hashCode());
        }
    }

    private static class Db extends SQLiteOpenHelper {
        Db(Context context) {
            super(context, "clips-v2.db", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE items ("
                    + "id TEXT PRIMARY KEY,"
                    + "hash TEXT UNIQUE,"
                    + "text TEXT NOT NULL,"
                    + "category TEXT,"
                    + "title TEXT,"
                    + "detail TEXT,"
                    + "extract TEXT,"
                    + "source TEXT,"
                    + "ts INTEGER NOT NULL,"
                    + "count INTEGER NOT NULL DEFAULT 1,"
                    + "pinned INTEGER NOT NULL DEFAULT 0,"
                    + "synced INTEGER NOT NULL DEFAULT 0)");
            database.execSQL("CREATE INDEX idx_items_ts ON items(ts DESC)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
            try {
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_items_ts ON items(ts DESC)");
            } catch (Exception ignored) {
            }
        }
    }
}
