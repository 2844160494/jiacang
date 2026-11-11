package dev.local.globalclip;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** 详情与设置两个底部弹层。风格和主界面共用 Ui 的色板。 */
final class Sheets {
    private static final long[] INTERVALS = {3_000L, 5_000L, 10_000L, 30_000L};

    private Sheets() {
    }

    // ------------------------------------------------------------------ 详情

    static void showDetail(final MainActivity host, final ClipItem item) {
        if (item == null) return;
        final Dialog[] ref = new Dialog[1];
        final LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.sheetBackground(host));
        root.addView(handle(host));

        LinearLayout header = new LinearLayout(host);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(host, 20), Ui.dp(host, 8), Ui.dp(host, 16), 0);
        Ui.GlyphBadge badge = new Ui.GlyphBadge(host);
        badge.setData(ClipCategory.glyph(item.category), ClipCategory.color(item.category));
        badge.setSizeDp(31);
        header.addView(badge);

        LinearLayout titleBlock = new LinearLayout(host);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleBlockParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleBlockParams.setMarginStart(Ui.dp(host, 11));
        TextView title = Ui.text(host, item.title == null ? item.text : item.title,
                17, Ui.INK, Ui.medium());
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleBlock.addView(title);

        StringBuilder meta = new StringBuilder(item.sourceLabel());
        if (item.detail != null && !item.detail.isEmpty()) meta.append(" · ").append(item.detail);
        meta.append(" · ").append(ClipItem.timeText(item.ts));
        if (item.count > 1) meta.append(" · ").append(item.count).append(" 次");
        if (item.pinned) meta.append(" · 置顶");
        TextView metaText = Ui.text(host, meta.toString(), 10.8f, Ui.MUTED, Typeface.DEFAULT);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = Ui.dp(host, 3);
        titleBlock.addView(metaText, metaParams);
        header.addView(titleBlock, titleBlockParams);

        Ui.CloseIcon close = new Ui.CloseIcon(host);
        close.setContentDescription("关闭");
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ref[0] != null) ref[0].dismiss();
            }
        });
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                Ui.dp(host, 34), Ui.dp(host, 34));
        closeParams.setMarginStart(Ui.dp(host, 8));
        header.addView(close, closeParams);
        root.addView(header);

        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(host, 20), Ui.dp(host, 8), Ui.dp(host, 20), Ui.dp(host, 26));

        final String extract = item.extract == null ? "" : item.extract.trim();
        final boolean hasExtract = !extract.isEmpty();
        if (hasExtract) {
            body.addView(buildRecognized(host, item, extract), margins(host, 0, 10, 0, 0));
        }

        boolean compact = ClipCategory.OTP.equals(item.category)
                || ClipCategory.CONTACT.equals(item.category)
                || ClipCategory.MONEY.equals(item.category)
                || ClipCategory.NUMBER.equals(item.category);
        boolean bodyHidden = hasExtract && compact && item.text.trim().equals(extract);
        if (!bodyHidden) {
            TextView label = Ui.sectionLabel(host, "原文 · " + item.text.length() + " 字");
            body.addView(label, margins(host, 1, hasExtract ? 16 : 8, 0, 0));
            TextView content = Ui.text(host, item.text, 13, Ui.INK_SOFT, Typeface.DEFAULT);
            content.setTextIsSelectable(true);
            content.setLineSpacing(Ui.dp(host, 4), 1f);
            if (ClipCategory.CODE.equals(item.category)
                    || ClipCategory.CMD.equals(item.category)
                    || ClipCategory.PATH.equals(item.category)) {
                content.setTypeface(Ui.mono());
                content.setTextSize(12.2f);
            }
            content.setBackground(Ui.round(Ui.SOFT_2, 14, host));
            content.setPadding(Ui.dp(host, 13), Ui.dp(host, 12),
                    Ui.dp(host, 13), Ui.dp(host, 12));
            LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            contentParams.topMargin = Ui.dp(host, 6);
            body.addView(content, contentParams);
        }

        TextView copyAll = new TextView(host);
        copyAll.setText("复制全文");
        copyAll.setTextSize(13);
        copyAll.setTypeface(Ui.medium());
        copyAll.setTextColor(Ui.WHITE);
        copyAll.setGravity(Gravity.CENTER);
        copyAll.setBackground(Ui.ripple(Ui.round(Ui.ACCENT, 24, host), 0x44FFFFFF, 24, host));
        copyAll.setClickable(true);
        copyAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Ui.tapFeedback(view);
                Ui.copy(host, item.text);
                Ui.toast(host, "已复制全文");
            }
        });
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(host, 46));
        copyParams.topMargin = Ui.dp(host, 18);
        body.addView(copyAll, copyParams);

        LinearLayout secondary = new LinearLayout(host);
        secondary.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams secondaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(host, 40));
        secondaryParams.topMargin = Ui.dp(host, 9);

        String contextual = contextualLabel(item);
        if (contextual != null) {
            addSecondary(secondary, host, contextual, ClipCategory.color(item.category),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Ui.tapFeedback(view);
                            performContext(host, item);
                        }
                    });
        }

        final TextView pin = addSecondary(secondary, host,
                item.pinned ? "取消置顶" : "置顶", Ui.INK_SOFT,
                null);
        pin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Ui.tapFeedback(view);
                boolean next = !item.pinned;
                host.store().setPinned(item.id, next);
                pin.setText(next ? "取消置顶" : "置顶");
                Ui.toast(host, next ? "已置顶" : "已取消置顶");
            }
        });

        addSecondary(secondary, host, "分享", Ui.INK_SOFT,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Ui.tapFeedback(view);
                        Ui.share(host, item.text);
                    }
                });
        body.addView(secondary, secondaryParams);

        TextView delete = Ui.text(host, "删除这条", 12, Ui.DANGER, Ui.medium());
        delete.setGravity(Gravity.CENTER);
        delete.setPadding(0, Ui.dp(host, 9), 0, Ui.dp(host, 9));
        delete.setBackground(Ui.ripple(Ui.round(Ui.DANGER_SOFT, 20, host), 0x22A9473B, 20, host));
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Ui.tapFeedback(view);
                ClipItem removed = host.store().remove(item.id);
                if (ref[0] != null) ref[0].dismiss();
                if (removed != null) host.showUndo(removed);
            }
        });
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        deleteParams.topMargin = Ui.dp(host, 14);
        body.addView(delete, deleteParams);

        Ui.MaxHeightScrollView scroll = new Ui.MaxHeightScrollView(host,
                (int) (maxWindowHeight(host) * 0.80f));
        scroll.addView(body, new android.widget.ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll);

        Dialog dialog = Ui.sheet(host, root, new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                host.refreshAll();
            }
        });
        ref[0] = dialog;
        dialog.show();
    }

    private static View buildRecognized(final MainActivity host, final ClipItem item, final String extract) {
        LinearLayout card = new LinearLayout(host);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(host, 14), Ui.dp(host, 12), Ui.dp(host, 14), Ui.dp(host, 13));
        int color = ClipCategory.color(item.category);
        card.setBackground(Ui.ripple(Ui.round(ClipCategory.tint(item.category), 14, host),
                0x14000000, 14, host));
        card.setClickable(true);

        LinearLayout labelRow = new LinearLayout(host);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = Ui.text(host, "识别为 " + ClipCategory.label(item.category),
                10.5f, color, Ui.medium());
        labelRow.addView(label, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView hint = Ui.text(host, "点按复制", 9.5f, Ui.alpha(color, 0xB0), Typeface.DEFAULT);
        labelRow.addView(hint);
        card.addView(labelRow);

        float size = 17;
        if (ClipCategory.OTP.equals(item.category)) size = 23;
        else if (ClipCategory.MONEY.equals(item.category)) size = 20;
        else if (ClipCategory.LINK.equals(item.category)) size = 13.5f;
        TextView value = Ui.text(host, extract, size, color, ClipCategory.OTP.equals(item.category)
                ? Ui.mono() : Ui.medium());
        value.setMaxLines(3);
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        value.setLineSpacing(Ui.dp(host, 2), 1f);
        if (ClipCategory.OTP.equals(item.category)) value.setLetterSpacing(0.10f);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = Ui.dp(host, 6);
        card.addView(value, valueParams);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Ui.tapFeedback(view);
                Ui.copy(host, extract);
                Ui.toast(host, "已复制" + ClipCategory.label(item.category));
            }
        });
        return card;
    }

    private static String contextualLabel(ClipItem item) {
        String category = item.category == null ? ClipCategory.TEXT : item.category;
        if (ClipCategory.LINK.equals(category)) return "打开链接";
        if (ClipCategory.CONTACT.equals(category)) {
            return item.extract != null && item.extract.contains("@") ? "写邮件" : "拨号";
        }
        if (ClipCategory.ADDRESS.equals(category)) return "打开地图";
        if (ClipCategory.OTP.equals(category)) return "复制验证码";
        if (ClipCategory.MONEY.equals(category)) return "复制金额";
        if (ClipCategory.NUMBER.equals(category)) return "复制号码";
        return null;
    }

    private static void performContext(MainActivity host, ClipItem item) {
        if (item.extract == null) return;
        String key = item.category == null ? ClipCategory.TEXT : item.category;
        if (ClipCategory.LINK.equals(key)) {
            Ui.openUrl(host, item.extract);
        } else if (ClipCategory.CONTACT.equals(key)) {
            if (item.extract.contains("@")) Ui.mail(host, item.extract);
            else Ui.dial(host, item.extract);
        } else if (ClipCategory.ADDRESS.equals(key)) {
            Ui.map(host, item.extract);
        } else {
            Ui.copy(host, item.extract);
            Ui.toast(host, "已复制");
        }
    }

    private static TextView addSecondary(LinearLayout row, Context context, String label,
                                        int color, View.OnClickListener listener) {
        TextView button = new TextView(context);
        button.setText(label);
        button.setTextSize(12);
        button.setTypeface(Ui.medium());
        button.setTextColor(color);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        button.setBackground(Ui.ripple(Ui.round(Ui.SOFT_2, 20, context), 0x14000000, 20, context));
        button.setClickable(true);
        if (listener != null) button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        params.setMarginEnd(Ui.dp(context, 8));
        row.addView(button, params);
        return button;
    }

    // ------------------------------------------------------------------ 设置

    static void showSettings(final MainActivity host) {
        final Dialog[] ref = new Dialog[1];
        final SharedPreferences prefs = host.prefs();

        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.sheetBackground(host));
        root.addView(handle(host));

        LinearLayout header = new LinearLayout(host);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(host, 20), Ui.dp(host, 8), Ui.dp(host, 16), Ui.dp(host, 2));
        TextView title = Ui.text(host, "偏好与权限", 22, Ui.INK, Ui.serif());
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Ui.CloseIcon close = new Ui.CloseIcon(host);
        close.setContentDescription("关闭");
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ref[0] != null) ref[0].dismiss();
            }
        });
        header.addView(close, new LinearLayout.LayoutParams(Ui.dp(host, 34), Ui.dp(host, 34)));
        root.addView(header);

        Ui.MaxHeightScrollView scroll = new Ui.MaxHeightScrollView(host,
                (int) (maxWindowHeight(host) * 0.78f));
        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(host, 20), 0, Ui.dp(host, 20), Ui.dp(host, 26));

        TextView intro = Ui.text(host, "内容只留在本机。只有你打开 DSH 同步时，才会连接 127.0.0.1。",
                11, Ui.MUTED, Typeface.DEFAULT);
        intro.setLineSpacing(Ui.dp(host, 3), 1f);
        LinearLayout.LayoutParams introParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        introParams.topMargin = Ui.dp(host, 6);
        introParams.bottomMargin = Ui.dp(host, 4);
        body.addView(intro, introParams);

        // 记录
        body.addView(section(host, "记录"));
        LinearLayout recordGroup = group(host);
        final TextView autoSub = Ui.text(host, "", 10.5f, Ui.MUTED, Typeface.DEFAULT);
        final Ui.Toggle autoToggle = new Ui.Toggle(host);
        final boolean enabled = prefs.getBoolean("enabled", false);
        autoToggle.setChecked(enabled, false);
        autoSub.setText(enabled ? "记录中 · 后台读取剪贴板" : "已暂停 · 点右侧开关开始记录");
        autoSub.setTextColor(enabled ? Ui.ACCENT : Ui.MUTED);
        LinearLayout autoRow = row(host, "自动记录", autoSub, autoToggle, null);
        autoToggle.setOnCheckedListener(new Ui.Toggle.OnCheckedListener() {
            @Override
            public void onChecked(boolean checked) {
                if (checked && !Settings.canDrawOverlays(host)) {
                    if (ref[0] != null) ref[0].dismiss();
                    host.toggleCapture();
                    return;
                }
                host.toggleCapture();
                boolean actual = prefs.getBoolean("enabled", false);
                autoToggle.setChecked(actual, true);
                autoSub.setText(actual ? "记录中 · 后台读取剪贴板" : "已暂停 · 点右侧开关开始记录");
                autoSub.setTextColor(actual ? Ui.ACCENT : Ui.MUTED);
            }
        });
        autoRow.setOnClickListener(null);
        recordGroup.addView(autoRow);
        recordGroup.addView(divider(host));

        LinearLayout intervalBlock = new LinearLayout(host);
        intervalBlock.setOrientation(LinearLayout.VERTICAL);
        intervalBlock.setPadding(Ui.dp(host, 14), Ui.dp(host, 11), Ui.dp(host, 14), Ui.dp(host, 12));
        intervalBlock.addView(Ui.text(host, "检查间隔", 13, Ui.INK, Ui.medium()));
        TextView intervalSub = Ui.text(host, "越短越及时，也越费电、越容易打扰输入法",
                10.5f, Ui.MUTED, Typeface.DEFAULT);
        LinearLayout.LayoutParams intervalSubParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        intervalSubParams.topMargin = Ui.dp(host, 2);
        intervalBlock.addView(intervalSub, intervalSubParams);

        LinearLayout intervalRow = new LinearLayout(host);
        intervalRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams intervalRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(host, 34));
        intervalRowParams.topMargin = Ui.dp(host, 10);
        long current = prefs.getLong("intervalMs", 5_000L);
        final TextView[] intervalButtons = new TextView[INTERVALS.length];
        for (int i = 0; i < INTERVALS.length; i++) {
            final long value = INTERVALS[i];
            TextView chip = new TextView(host);
            chip.setText(value / 1000 + " 秒");
            chip.setTextSize(11.5f);
            chip.setTypeface(Ui.medium());
            chip.setGravity(Gravity.CENTER);
            chip.setClickable(true);
            final TextView currentChip = chip;
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    prefs.edit().putLong("intervalMs", value).apply();
                    for (int j = 0; j < INTERVALS.length; j++) {
                        styleInterval(intervalButtons[j], INTERVALS[j] == value, host);
                    }
                    Ui.tapFeedback(view);
                }
            });
            styleInterval(chip, value == current, host);
            intervalButtons[i] = chip;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            if (i < INTERVALS.length - 1) params.setMarginEnd(Ui.dp(host, 6));
            intervalRow.addView(chip, params);
        }
        intervalBlock.addView(intervalRow, intervalRowParams);
        recordGroup.addView(intervalBlock);
        body.addView(recordGroup);

        TextView intervalHint = Ui.text(host,
                "后台读取只能靠一个 1×1 的悬浮窗短暂抢焦点，可能让输入法闪一下；调大间隔能缓解。",
                10.3f, Ui.MUTED, Typeface.DEFAULT);
        intervalHint.setLineSpacing(Ui.dp(host, 3), 1f);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.setMargins(Ui.dp(host, 4), Ui.dp(host, 8), Ui.dp(host, 4), 0);
        body.addView(intervalHint, hintParams);

        // 权限
        body.addView(section(host, "权限"));
        LinearLayout permissionGroup = group(host);
        permissionGroup.addView(permissionRow(host, "悬浮窗权限",
                Settings.canDrawOverlays(host) ? "已允许 · 后台读取必需" : "未允许 · 后台读取必需",
                Settings.canDrawOverlays(host), "去允许",
                new Runnable() {
                    @Override
                    public void run() {
                        if (ref[0] != null) ref[0].dismiss();
                        host.openOverlaySettings();
                    }
                }));
        permissionGroup.addView(divider(host));
        permissionGroup.addView(permissionRow(host, "通知权限",
                host.isNotificationGranted() ? "已允许 · 常驻通知使用" : "未允许 · 前台服务仍会运行",
                host.isNotificationGranted(), "去允许",
                new Runnable() {
                    @Override
                    public void run() {
                        if (ref[0] != null) ref[0].dismiss();
                        host.requestNotificationOrSettings();
                    }
                }));
        permissionGroup.addView(divider(host));
        permissionGroup.addView(permissionRow(host, "电池优化",
                host.isBatteryIgnoring() ? "已忽略 · 不容易被系统回收" : "未设置 · 国产 ROM 建议设为无限制",
                host.isBatteryIgnoring(), "去设置",
                new Runnable() {
                    @Override
                    public void run() {
                        if (ref[0] != null) ref[0].dismiss();
                        host.openBatterySettings();
                    }
                }));
        body.addView(permissionGroup);

        // 剪切识别
        body.addView(section(host, "剪切识别"));
        LinearLayout cutGroup = group(host);
        final TextView cutSub = Ui.text(host, "", 10.5f, Ui.MUTED, Typeface.DEFAULT);
        final Ui.Toggle cutToggle = new Ui.Toggle(host);
        final boolean serviceOn = host.isAccessibilityEnabled();
        boolean prefOn = prefs.getBoolean("cutWatch", false);
        cutToggle.setChecked(prefOn, false);
        refreshCutStatus(host, cutSub, prefOn, serviceOn);
        LinearLayout cutRow = row(host, "自动区分复制与剪切", cutSub, cutToggle, null);
        cutRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ref[0] != null) ref[0].dismiss();
                host.openAccessibilitySettings();
            }
        });
        cutToggle.setOnCheckedListener(new Ui.Toggle.OnCheckedListener() {
            @Override
            public void onChecked(boolean checked) {
                prefs.edit().putBoolean("cutWatch", checked).apply();
                boolean service = host.isAccessibilityEnabled();
                refreshCutStatus(host, cutSub, checked, service);
                if (checked && !service) {
                    if (ref[0] != null) ref[0].dismiss();
                    host.openAccessibilitySettings();
                    Ui.toast(host, "请在无障碍列表里启用「剪藏 · 剪切识别」");
                } else if (!checked && service) {
                    Ui.toast(host, "识别已停止；想彻底关闭可到无障碍设置停用服务");
                }
            }
        });
        cutGroup.addView(cutRow);
        TextView cutDesc = Ui.text(host,
                "只用无障碍旁听编辑框里被删掉的文字，和随后出现的剪贴板对上号；不读通知、不联网、不保存。",
                10.3f, Ui.MUTED, Typeface.DEFAULT);
        cutDesc.setLineSpacing(Ui.dp(host, 3), 1f);
        cutDesc.setPadding(Ui.dp(host, 14), 0, Ui.dp(host, 14), Ui.dp(host, 12));
        cutGroup.addView(cutDesc);
        body.addView(cutGroup);

        // DSH 同步
        body.addView(section(host, "同步"));
        LinearLayout syncGroup = group(host);
        final TextView syncSub = Ui.text(host, "", 10.5f, Ui.MUTED, Typeface.DEFAULT);
        final Ui.Toggle syncToggle = new Ui.Toggle(host);
        boolean sync = prefs.getBoolean("sync", true);
        syncToggle.setChecked(sync, false);
        syncSub.setText(sync ? "新内容会同步到 127.0.0.1:3080" : "已关闭，不建立任何连接");
        syncToggle.setOnCheckedListener(new Ui.Toggle.OnCheckedListener() {
            @Override
            public void onChecked(boolean checked) {
                prefs.edit().putBoolean("sync", checked).apply();
                syncSub.setText(checked ? "新内容会同步到 127.0.0.1:3080" : "已关闭，不建立任何连接");
            }
        });
        syncGroup.addView(row(host, "同步到 DSH", syncSub, syncToggle, null));
        syncGroup.addView(divider(host));

        final TextView testStatus = Ui.text(host, "未测试", 10.5f, Ui.MUTED, Typeface.DEFAULT);
        LinearLayout testRow = new LinearLayout(host);
        testRow.setOrientation(LinearLayout.HORIZONTAL);
        testRow.setGravity(Gravity.CENTER_VERTICAL);
        testRow.setPadding(Ui.dp(host, 14), Ui.dp(host, 11), Ui.dp(host, 14), Ui.dp(host, 11));
        testRow.setClickable(true);
        testRow.setBackground(Ui.ripple(new ColorDrawable(Color.TRANSPARENT), 0x12000000, 12, host));
        TextView testTitle = Ui.text(host, "测试连接", 12.5f, Ui.INK, Ui.medium());
        testRow.addView(testTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        testRow.addView(testStatus);
        Ui.Chevron chevron = new Ui.Chevron(host);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(
                Ui.dp(host, 18), Ui.dp(host, 18));
        chevronParams.setMarginStart(Ui.dp(host, 2));
        testRow.addView(chevron, chevronParams);
        testRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                testStatus.setText("测试中…");
                testStatus.setTextColor(Ui.MUTED);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        boolean ok;
                        try {
                            ok = HubSync.check();
                        } catch (Exception error) {
                            ok = false;
                        }
                        final boolean result = ok;
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                testStatus.setText(result ? "已连接" : "未连接");
                                testStatus.setTextColor(result ? Ui.ACCENT : Ui.DANGER);
                                Ui.toast(host, result ? "DSH 在运行，新内容会自动同步" : "没连上 DSH，确认它正在运行");
                            }
                        });
                    }
                }, "clip-hub-test").start();
            }
        });
        syncGroup.addView(testRow);
        body.addView(syncGroup);

        // 数据
        body.addView(section(host, "数据"));
        LinearLayout dataGroup = group(host);
        LinearLayout manualRow = new LinearLayout(host);
        manualRow.setOrientation(LinearLayout.HORIZONTAL);
        manualRow.setGravity(Gravity.CENTER_VERTICAL);
        manualRow.setPadding(Ui.dp(host, 14), Ui.dp(host, 11), Ui.dp(host, 14), Ui.dp(host, 11));
        manualRow.setClickable(true);
        manualRow.setBackground(Ui.ripple(new ColorDrawable(Color.TRANSPARENT), 0x12000000, 12, host));
        LinearLayout manualText = new LinearLayout(host);
        manualText.setOrientation(LinearLayout.VERTICAL);
        manualText.addView(Ui.text(host, "读取当前剪贴板", 13, Ui.INK, Ui.medium()));
        TextView manualSub = Ui.text(host, "没开后台记录时，也能手动收一条", 10.5f, Ui.MUTED, Typeface.DEFAULT);
        LinearLayout.LayoutParams manualSubParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        manualSubParams.topMargin = Ui.dp(host, 2);
        manualText.addView(manualSub, manualSubParams);
        manualRow.addView(manualText, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Ui.Chevron manualChevron = new Ui.Chevron(host);
        LinearLayout.LayoutParams manualChevronParams = new LinearLayout.LayoutParams(
                Ui.dp(host, 18), Ui.dp(host, 18));
        manualChevronParams.setMarginStart(Ui.dp(host, 2));
        manualRow.addView(manualChevron, manualChevronParams);
        manualRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Ui.tapFeedback(view);
                host.readClipboardNow();
            }
        });
        dataGroup.addView(manualRow);
        dataGroup.addView(divider(host));
        ClipStore.Stats stats = host.store().stats();
        LinearLayout dataRow = new LinearLayout(host);
        dataRow.setOrientation(LinearLayout.HORIZONTAL);
        dataRow.setGravity(Gravity.CENTER_VERTICAL);
        dataRow.setPadding(Ui.dp(host, 14), Ui.dp(host, 11), Ui.dp(host, 14), Ui.dp(host, 11));
        LinearLayout dataText = new LinearLayout(host);
        dataText.setOrientation(LinearLayout.VERTICAL);
        dataText.addView(Ui.text(host, "清理历史", 13, Ui.INK, Ui.medium()));
        TextView dataSub = Ui.text(host, "共 " + stats.total + " 条 · 置顶 " + stats.pinned
                + " · " + Ui.bytes(stats.dbBytes), 10.5f, Ui.MUTED, Typeface.DEFAULT);
        LinearLayout.LayoutParams dataSubParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dataSubParams.topMargin = Ui.dp(host, 2);
        dataText.addView(dataSub, dataSubParams);
        dataRow.addView(dataText, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView clear = Ui.text(host, "清空…", 11.5f, Ui.DANGER, Ui.medium());
        clear.setPadding(Ui.dp(host, 10), Ui.dp(host, 5), Ui.dp(host, 10), Ui.dp(host, 5));
        clear.setBackground(Ui.ripple(Ui.round(Ui.DANGER_SOFT, 12, host), 0x22A9473B, 12, host));
        clear.setClickable(true);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmClear(host, ref[0]);
            }
        });
        dataRow.addView(clear);
        dataGroup.addView(dataRow);
        body.addView(dataGroup);

        // 关于
        body.addView(section(host, "关于"));
        TextView about = Ui.text(host, "剪藏 2.0\n本地优先的剪贴板整理器。历史保存在应用私有数据库，卸载即清空；DSH 同步只连接本机 127.0.0.1。",
                10.8f, Ui.MUTED, Typeface.DEFAULT);
        about.setLineSpacing(Ui.dp(host, 4), 1f);
        LinearLayout.LayoutParams aboutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        aboutParams.setMargins(Ui.dp(host, 4), 0, Ui.dp(host, 4), 0);
        body.addView(about, aboutParams);

        scroll.addView(body, new android.widget.ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll);

        Dialog dialog = Ui.sheet(host, root, new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                host.refreshAll();
            }
        });
        ref[0] = dialog;
        dialog.show();
    }

    private static void styleInterval(TextView chip, boolean selected, Context context) {
        chip.setTextColor(selected ? Ui.WHITE : Ui.INK_SOFT);
        if (selected) {
            chip.setBackground(Ui.ripple(Ui.round(Ui.ACCENT, 12, context), 0x33FFFFFF, 12, context));
        } else {
            chip.setBackground(Ui.ripple(Ui.round(Ui.SURFACE, 12, context), 0x12000000, 12, context));
        }
    }

    private static void refreshCutStatus(Context context, TextView status, boolean prefOn, boolean serviceOn) {
        if (!prefOn) {
            status.setText("未开启 · 需要无障碍权限");
            status.setTextColor(Ui.MUTED);
        } else if (!serviceOn) {
            status.setText("待授权 · 在系统无障碍里启用");
            status.setTextColor(Ui.CORAL);
        } else {
            status.setText("已开启 · 只观察编辑框的删除操作");
            status.setTextColor(Ui.ACCENT);
        }
    }

    private static void confirmClear(final MainActivity host, final Dialog sheet) {
        AlertDialog.Builder builder = new AlertDialog.Builder(host);
        builder.setTitle("清空历史");
        builder.setMessage("删除所有内容。置顶的内容可以保留。");
        builder.setPositiveButton("保留置顶并清空", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                host.store().clear(true);
                if (sheet != null) sheet.dismiss();
                host.refreshAll();
                Ui.toast(host, "已清空，置顶内容保留");
            }
        });
        builder.setNegativeButton("全部清空", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                host.store().clear(false);
                if (sheet != null) sheet.dismiss();
                host.refreshAll();
                Ui.toast(host, "已全部清空");
            }
        });
        builder.setNeutralButton("取消", null);
        builder.show();
    }

    // ------------------------------------------------------------------ 组件

    private static int maxWindowHeight(Activity activity) {
        int decor = activity.getWindow().getDecorView().getHeight();
        int display = activity.getResources().getDisplayMetrics().heightPixels;
        int base = decor > 0 ? Math.min(decor, display) : display;
        return base > 0 ? base : Ui.dp(activity, 640);
    }

    private static View handle(Context context) {
        View handle = new View(context);
        handle.setBackground(Ui.round(0xFFD7CFC1, 3, context));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Ui.dp(context, 38), Ui.dp(context, 4));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.topMargin = Ui.dp(context, 9);
        handle.setLayoutParams(params);
        return handle;
    }

    private static TextView section(Context context, String label) {
        TextView view = Ui.sectionLabel(context, label);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(Ui.dp(context, 4), Ui.dp(context, 18), 0, Ui.dp(context, 7));
        view.setLayoutParams(params);
        return view;
    }

    private static LinearLayout group(Context context) {
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackground(Ui.round(Ui.SOFT_2, 16, context));
        return group;
    }

    private static View divider(Context context) {
        View divider = new View(context);
        divider.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 1));
        params.setMargins(Ui.dp(context, 14), 0, Ui.dp(context, 14), 0);
        divider.setLayoutParams(params);
        return divider;
    }

    private static LinearLayout row(Context context, String title, TextView subtitle,
                                    View trailing, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(context, 14), Ui.dp(context, 11), Ui.dp(context, 14), Ui.dp(context, 11));
        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(Ui.text(context, title, 13, Ui.INK, Ui.medium()));
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = Ui.dp(context, 2);
        block.addView(subtitle, subParams);
        row.addView(block, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (trailing != null) {
            LinearLayout.LayoutParams trailingParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            trailingParams.setMarginStart(Ui.dp(context, 10));
            row.addView(trailing, trailingParams);
        }
        if (click != null) {
            row.setClickable(true);
            row.setBackground(Ui.ripple(new ColorDrawable(Color.TRANSPARENT), 0x12000000, 12, context));
            row.setOnClickListener(click);
        }
        return row;
    }

    private static LinearLayout permissionRow(Context context, String title, String subtitle,
                                              boolean granted, String action, final Runnable open) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(context, 14), Ui.dp(context, 11), Ui.dp(context, 14), Ui.dp(context, 11));
        row.setClickable(true);
        row.setBackground(Ui.ripple(new ColorDrawable(Color.TRANSPARENT), 0x12000000, 12, context));

        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(Ui.text(context, title, 13, Ui.INK, Ui.medium()));
        TextView sub = Ui.text(context, subtitle, 10.5f, granted ? Ui.ACCENT : Ui.MUTED, Typeface.DEFAULT);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = Ui.dp(context, 2);
        block.addView(sub, subParams);
        row.addView(block, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView actionView = Ui.text(context, granted ? "已开启" : action, 10.8f,
                granted ? Ui.ACCENT : Ui.CORAL, Ui.medium());
        actionView.setPadding(Ui.dp(context, 8), Ui.dp(context, 4),
                Ui.dp(context, 8), Ui.dp(context, 4));
        row.addView(actionView);
        if (!granted) {
            Ui.Chevron chevron = new Ui.Chevron(context);
            LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(
                    Ui.dp(context, 18), Ui.dp(context, 18));
            chevronParams.setMarginStart(Ui.dp(context, 1));
            row.addView(chevron, chevronParams);
        }
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Ui.tapFeedback(view);
                open.run();
            }
        });
        return row;
    }

    private static LinearLayout.LayoutParams margins(Context context,
                                                    int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(Ui.dp(context, left), Ui.dp(context, top),
                Ui.dp(context, right), Ui.dp(context, bottom));
        return params;
    }
}
