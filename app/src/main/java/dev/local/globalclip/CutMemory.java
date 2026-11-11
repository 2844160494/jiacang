package dev.local.globalclip;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * 剪切“嫌疑”缓存：无障碍服务看到编辑框里被整段删掉的文字后先记在这里，
 * 捕获服务读到同样内容的剪贴板时，就把它标成“剪切”。只在内存里留 9 秒。
 */
final class CutMemory {
    private static final long WINDOW_MS = 9_000L;
    private static final int MAX_ENTRIES = 10;
    private static final int MAX_LENGTH = 4_000;

    private static final ArrayDeque<Entry> ENTRIES = new ArrayDeque<>();
    private static volatile Runnable quickCheck;

    private static class Entry {
        final String text;
        final long ts;

        Entry(String text, long ts) {
            this.text = text;
            this.ts = ts;
        }
    }

    private CutMemory() {
    }

    /** 捕获服务注册的回调：选中的文字被删掉时立刻去读一次剪贴板。 */
    static void setQuickCheck(Runnable callback) {
        quickCheck = callback;
    }

    static synchronized void record(String text) {
        if (text == null) return;
        String value = text.trim();
        if (value.length() < 2 || value.length() > MAX_LENGTH) return;
        prune();
        ENTRIES.addFirst(new Entry(value, System.currentTimeMillis()));
        while (ENTRIES.size() > MAX_ENTRIES) ENTRIES.removeLast();
    }

    /** 确认这次删除来自一段事先选中的文字，安排一次即刻读取。 */
    static void requestQuickCheck() {
        Runnable callback = quickCheck;
        if (callback != null) {
            try {
                callback.run();
            } catch (Exception ignored) {
            }
        }
    }

    static synchronized boolean matches(String text) {
        if (text == null) return false;
        String value = text.trim();
        if (value.isEmpty()) return false;
        prune();
        long now = System.currentTimeMillis();
        for (Entry entry : ENTRIES) {
            if (now - entry.ts <= WINDOW_MS && entry.text.equals(value)) return true;
        }
        return false;
    }

    static synchronized void clear() {
        ENTRIES.clear();
    }

    private static void prune() {
        long now = System.currentTimeMillis();
        Iterator<Entry> iterator = ENTRIES.iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().ts > WINDOW_MS) iterator.remove();
        }
    }
}
