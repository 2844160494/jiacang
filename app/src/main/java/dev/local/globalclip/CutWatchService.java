package dev.local.globalclip;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.HashMap;

/**
 * 可选的“剪切识别”无障碍服务。
 *
 * Android 的剪贴板 API 不区分复制与剪切。这里只旁听编辑框：
 * - 用户先选中一段文字（selection change），随后它被整段删掉（text change）；
 * - 就把删掉的这段记到 CutMemory，并让捕获服务立刻去读一次剪贴板；
 * - 读到同样内容即标记为剪切。
 *
 * 只观察非密码编辑框；不读通知、不读聊天内容、不联网、不保存文本。
 */
public class CutWatchService extends AccessibilityService {
    private static final int MAX_TRACKED_VIEWS = 32;
    private static final long SELECTION_TTL_MS = 15_000L;
    private static final int MAX_SELECTION = 4_000;

    private static class Selection {
        final String text;
        final long ts;

        Selection(String text, long ts) {
            this.text = text;
            this.ts = ts;
        }
    }

    private final HashMap<String, String> lastTexts = new HashMap<>();
    private final HashMap<String, Selection> selections = new HashMap<>();

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        SharedPreferences settings = getSharedPreferences("settings", MODE_PRIVATE);
        if (!settings.getBoolean("cutWatch", false)) {
            if (!lastTexts.isEmpty()) lastTexts.clear();
            if (!selections.isEmpty()) selections.clear();
            return;
        }
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                && type != AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) return;

        CharSequence packageName = event.getPackageName();
        if (packageName != null && TextUtils.equals(packageName, getPackageName())) return;

        AccessibilityNodeInfo source = event.getSource();
        if (source == null) return;
        try {
            if (source.isPassword() || !source.isEditable()) return;
            CharSequence value = source.getText();
            if (value == null) return;
            String current = value.toString();
            if (current.length() > 20_000) return;
            String key = keyOf(event, source);
            if (type == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
                trackSelection(key, source, current);
            } else {
                trackTextChange(key, current);
            }
        } catch (Exception ignored) {
        } finally {
            try {
                source.recycle();
            } catch (Exception ignored) {
            }
        }
    }

    private void trackSelection(String key, AccessibilityNodeInfo source, String current) {
        int start = source.getTextSelectionStart();
        int end = source.getTextSelectionEnd();
        if (start < 0 || end < 0 || end > current.length()) {
            return;
        }
        if (end <= start) {
            // 光标收起不一定代表“没选过”：剪切时系统可能先发一条 collapse，
            // 再发 text change。保留上一段选区，等 15 秒或下一次选区覆盖。
            return;
        }
        int length = end - start;
        if (length < 2 || length > MAX_SELECTION) {
            selections.remove(key);
            return;
        }
        selections.put(key, new Selection(current.substring(start, end),
                System.currentTimeMillis()));
        if (selections.size() > MAX_TRACKED_VIEWS) selections.clear();
    }

    private void trackTextChange(String key, String current) {
        String previous = lastTexts.get(key);
        lastTexts.put(key, current);
        if (lastTexts.size() > MAX_TRACKED_VIEWS) lastTexts.clear();
        if (previous == null || previous.length() <= current.length()) {
            selections.remove(key);
            return;
        }

        int prefix = commonPrefix(previous, current);
        int suffix = commonSuffix(previous, current, prefix);
        int removedEnd = previous.length() - suffix;
        if (removedEnd <= prefix) return;
        if (current.length() != prefix + suffix) return; // 同一次变化里还插入了别的内容
        String removed = previous.substring(prefix, removedEnd);
        if (removed.length() < 2 || removed.length() > MAX_SELECTION) return;
        CutMemory.record(removed);

        Selection selection = selections.remove(key);
        long now = System.currentTimeMillis();
        if (selection != null && now - selection.ts <= SELECTION_TTL_MS
                && normalize(removed).equals(normalize(selection.text))) {
            // 这很像一次剪切：选中 → 整段消失，立刻读剪贴板
            CutMemory.requestQuickCheck();
        }
    }

    private static String keyOf(AccessibilityEvent event, AccessibilityNodeInfo source) {
        String viewId = source.getViewIdResourceName();
        if (viewId != null) return event.getWindowId() + "|" + viewId;
        String className = source.getClassName() == null ? "edit" : source.getClassName().toString();
        Rect bounds = new Rect();
        source.getBoundsInScreen(bounds);
        return event.getWindowId() + "|" + className + "|" + bounds.left + "," + bounds.top;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replace("\r", "");
    }

    private static int commonPrefix(String a, String b) {
        int limit = Math.min(a.length(), b.length());
        int i = 0;
        while (i < limit && a.charAt(i) == b.charAt(i)) i++;
        return i;
    }

    private static int commonSuffix(String a, String b, int prefix) {
        int limit = Math.min(a.length() - prefix, b.length() - prefix);
        int i = 0;
        while (i < limit && a.charAt(a.length() - 1 - i) == b.charAt(b.length() - 1 - i)) i++;
        return i;
    }

    @Override
    public void onInterrupt() {
    }
}
