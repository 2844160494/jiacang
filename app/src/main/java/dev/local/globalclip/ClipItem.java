package dev.local.globalclip;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** 一条剪贴记录。字段直接映射数据库，简单够用。 */
public class ClipItem {
    public String id;
    public String text;
    public String category;
    public String title;
    public String detail;
    public String extract;
    public String source;
    public long ts;
    public int count;
    public boolean pinned;
    public boolean synced;

    public String preview() {
        if (text == null) return "";
        String value = text.trim();
        if (value.length() > 260) value = value.substring(0, 260);
        return value;
    }

    public String categoryLabel() {
        return ClipCategory.label(category);
    }

    public String sourceLabel() {
        if ("cut".equals(source)) return "剪切";
        if ("manual".equals(source)) return "手动";
        return "复制";
    }

    public String countLabel() {
        return count > 1 ? (" · " + count + " 次") : "";
    }

    public String metaText() {
        return sourceLabel() + " · " + timeText(ts) + countLabel();
    }

    public String shortDate() {
        SimpleDateFormat format = new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA);
        return format.format(new Date(ts));
    }

    public static String timeText(long ts) {
        long now = System.currentTimeMillis();
        long diff = now - ts;
        if (diff < 0) diff = 0;
        if (diff < 60_000L) return "刚刚";
        Calendar today = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(ts);
        if (sameDay(today, then)) {
            if (diff < 3_600_000L) return (diff / 60_000L) + " 分钟前";
            SimpleDateFormat format = new SimpleDateFormat("HH:mm", Locale.CHINA);
            return format.format(new Date(ts));
        }
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(yesterday, then)) {
            SimpleDateFormat format = new SimpleDateFormat("昨天 HH:mm", Locale.CHINA);
            return format.format(new Date(ts));
        }
        SimpleDateFormat format = new SimpleDateFormat(
                today.get(Calendar.YEAR) == then.get(Calendar.YEAR) ? "M月d日" : "yyyy年M月d日",
                Locale.CHINA);
        return format.format(new Date(ts));
    }

    public static String dayLabel(long ts) {
        Calendar today = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(ts);
        if (sameDay(today, then)) return "今天";
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(yesterday, then)) return "昨天";
        SimpleDateFormat format = new SimpleDateFormat(
                today.get(Calendar.YEAR) == then.get(Calendar.YEAR) ? "M月d日" : "yyyy年M月d日",
                Locale.CHINA);
        return format.format(new Date(ts));
    }

    public static String dayKey(long ts) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd", Locale.CHINA);
        return format.format(new Date(ts));
    }

    public static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}
