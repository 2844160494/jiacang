package dev.local.globalclip;

/** 从剪藏界面里复制出去的内容，不要让捕获服务又收一遍。 */
final class InternalCopy {
    private static String text;
    private static long until;

    private InternalCopy() {
    }

    static synchronized void mark(String value) {
        if (value == null || value.isEmpty()) return;
        text = value;
        until = System.currentTimeMillis() + 60_000L;
    }

    static synchronized boolean shouldIgnore(String value) {
        if (value == null || text == null) return false;
        return System.currentTimeMillis() <= until && text.equals(value);
    }
}
