package dev.local.globalclip;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.animation.ValueAnimator;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** 统一的颜色、字体、圆角、图标和弹层。整屏界面都在这里定调。 */
final class Ui {
    static final int PAPER = 0xFFF7F3EC;
    static final int SURFACE = 0xFFFFFFFF;
    static final int INK = 0xFF1E1C19;
    static final int INK_SOFT = 0xFF4B463E;
    static final int MUTED = 0xFF776F62;
    static final int FAINT = 0xFFA1988A;
    static final int LINE = 0xFFE9E2D6;
    static final int SOFT = 0xFFF0EBE2;
    static final int SOFT_2 = 0xFFF7F2E9;
    static final int ACCENT = 0xFF23594E;
    static final int ACCENT_DEEP = 0xFF17443B;
    static final int ACCENT_SOFT = 0xFFDDECE6;
    static final int CORAL = 0xFFC2603A;
    static final int CORAL_SOFT = 0xFFF4E3DA;
    static final int GOLD = 0xFFB3822F;
    static final int DANGER = 0xFFA9473B;
    static final int DANGER_SOFT = 0xFFF7E2DE;
    static final int WHITE = 0xFFFFFFFF;

    private Ui() {
    }

    static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    static Typeface serif() {
        return Typeface.create("serif", Typeface.BOLD);
    }

    static Typeface medium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    static Typeface bold() {
        return Typeface.create("sans-serif", Typeface.BOLD);
    }

    static Typeface mono() {
        return Typeface.MONOSPACE;
    }

    static Drawable round(int color, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    static GradientDrawable roundStroke(int fill, int stroke, float radiusDp, float width, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (width > 0) drawable.setStroke(dp(context, width), stroke);
        return drawable;
    }

    static Drawable sheetBackground(Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(SURFACE);
        float r = dp(context, 26);
        drawable.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        return drawable;
    }

    static RippleDrawable ripple(Drawable content, int rippleColor, float radiusDp, Context context) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(WHITE);
        mask.setCornerRadius(dp(context, radiusDp));
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    static RippleDrawable cardBackground(Context context) {
        return ripple(round(SURFACE, 18, context), 0x14000000, 18, context);
    }

    static RippleDrawable circleRipple(int fill, int rippleColor, Context context) {
        GradientDrawable content = new GradientDrawable();
        content.setShape(GradientDrawable.OVAL);
        content.setColor(fill);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(WHITE);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    static TextView text(Context context, CharSequence value, float sizeSp,
                         int color, Typeface typeface) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(sizeSp);
        text.setTextColor(color);
        text.setTypeface(typeface == null ? Typeface.DEFAULT : typeface);
        text.setIncludeFontPadding(false);
        return text;
    }

    static TextView pill(Context context, CharSequence value, float sizeSp, int color,
                         int fill, int rippleColor) {
        TextView text = text(context, value, sizeSp, color, medium());
        text.setGravity(Gravity.CENTER);
        text.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6));
        text.setBackground(ripple(round(fill, 99, context), rippleColor, 99, context));
        text.setClickable(true);
        return text;
    }

    static void addSpace(LinearLayout parent, int heightDp) {
        View space = new View(parent.getContext());
        parent.addView(space, new LinearLayout.LayoutParams(1, dp(parent.getContext(), heightDp)));
    }

    static void setBar(Activity activity) {
        Window window = activity.getWindow();
        if (Build.VERSION.SDK_INT >= 29) window.getDecorView().setForceDarkAllowed(false);
        window.setStatusBarColor(PAPER);
        window.setNavigationBarColor(PAPER);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                            | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    static Dialog sheet(Activity activity, View content, DialogInterface.OnDismissListener dismissed) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.38f);
            window.setGravity(Gravity.BOTTOM);
            int width = activity.getWindow().getDecorView().getWidth();
            window.setLayout(width > 0 ? width : WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setWindowAnimations(R.style.SheetAnimation);
        }
        dialog.setCanceledOnTouchOutside(true);
        if (dismissed != null) dialog.setOnDismissListener(dismissed);
        return dialog;
    }

    static void copy(Activity activity, String text) {
        if (text == null || text.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("剪藏", text));
        InternalCopy.mark(text);
    }

    static void openUrl(Activity activity, String url) {
        try {
            String value = url.trim();
            if (!value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) value = "https://" + value;
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(value));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception error) {
            toast(activity, "没有可以打开这个链接的应用");
        }
    }

    static void dial(Activity activity, String number) {
        try {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < number.length(); i++) {
                char c = number.charAt(i);
                if ((c >= '0' && c <= '9') || c == '+' || c == '*' || c == '#') builder.append(c);
            }
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + builder));
            activity.startActivity(intent);
        } catch (Exception error) {
            toast(activity, "无法打开拨号盘");
        }
    }

    static void mail(Activity activity, String address) {
        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + address));
            activity.startActivity(intent);
        } catch (Exception error) {
            toast(activity, "没有可以写邮件的应用");
        }
    }

    static void map(Activity activity, String address) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("geo:0,0?q=" + Uri.encode(address)));
            activity.startActivity(intent);
        } catch (Exception error) {
            toast(activity, "没有可以打开地图的应用");
        }
    }

    static void share(Activity activity, String text) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, text);
            activity.startActivity(Intent.createChooser(intent, "分享到"));
        } catch (Exception error) {
            toast(activity, "分享失败");
        }
    }

    static void toast(Activity activity, String message) {
        if (activity == null || activity.isFinishing()) return;
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    static void tapFeedback(View view) {
        if (view == null) return;
        try {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
        } catch (Exception ignored) {
        }
    }

    static String bytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.CHINA, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.CHINA, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    // ------------------------------------------------------------------ 图标

    static class GlyphBadge extends View {
        private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint foreground = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String glyph = "句";
        private int color = ClipCategory.color(ClipCategory.TEXT);
        private float forcedSize = 26;

        GlyphBadge(Context context) {
            super(context);
            textPaint.setTypeface(medium());
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        void setData(String glyph, int color) {
            this.glyph = glyph;
            this.color = color;
            invalidate();
        }

        void setSizeDp(float size) {
            forcedSize = size;
            requestLayout();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int size = dp(getContext(), forcedSize);
            setMeasuredDimension(size, size);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float size = Math.min(getWidth(), getHeight());
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = size / 2f - dp(getContext(), 0.5f);
            background.setColor(alpha(color, 0x1C));
            canvas.drawCircle(cx, cy, radius, background);
            textPaint.setColor(color);
            textPaint.setTextSize(size * 0.42f);
            float baseline = cy - (textPaint.descent() + textPaint.ascent()) / 2f;
            canvas.drawText(glyph, cx, baseline, textPaint);
        }
    }

    static class SettingsIcon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        SettingsIcon(Context context) {
            super(context);
            paint.setColor(INK);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(context, 1.6f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            setBackground(circleRipple(SURFACE, 0x1A23594E, context));
            setClickable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float left = w * 0.29f;
            float right = w * 0.71f;
            float[] ys = {h * 0.34f, h * 0.5f, h * 0.66f};
            canvas.drawLine(left, ys[0], right, ys[0], paint);
            canvas.drawLine(left, ys[1], right, ys[1], paint);
            canvas.drawLine(left, ys[2], right, ys[2], paint);
            drawKnob(canvas, right - (right - left) * 0.25f, ys[0], w);
            drawKnob(canvas, left + (right - left) * 0.38f, ys[1], w);
            drawKnob(canvas, right - (right - left) * 0.12f, ys[2], w);
        }

        private void drawKnob(Canvas canvas, float x, float y, float w) {
            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setColor(SURFACE);
            fill.setStyle(Paint.Style.FILL);
            canvas.drawCircle(x, y, dp(getContext(), 3.4f), fill);
            canvas.drawCircle(x, y, dp(getContext(), 3.4f), paint);
        }
    }

    static class SearchIcon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        SearchIcon(Context context) {
            super(context);
            paint.setColor(MUTED);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(context, 1.7f));
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            canvas.drawCircle(w * 0.43f, h * 0.43f, w * 0.26f, paint);
            canvas.drawLine(w * 0.62f, h * 0.62f, w * 0.80f, h * 0.80f, paint);
        }
    }

    static class CloseIcon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CloseIcon(Context context) {
            super(context);
            paint.setColor(MUTED);
            paint.setStrokeWidth(dp(context, 1.7f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            setBackground(circleRipple(SOFT, 0x1A000000, context));
            setClickable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float a = w * 0.34f;
            float b = w * 0.66f;
            canvas.drawLine(a, h * 0.34f, b, h * 0.66f, paint);
            canvas.drawLine(b, h * 0.34f, a, h * 0.66f, paint);
        }
    }

    static class Chevron extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Chevron(Context context) {
            super(context);
            paint.setColor(FAINT);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(context, 1.5f));
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            canvas.drawLine(w * 0.38f, h * 0.32f, w * 0.62f, h * 0.5f, paint);
            canvas.drawLine(w * 0.62f, h * 0.5f, w * 0.38f, h * 0.68f, paint);
        }
    }

    static class Check extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Check(Context context) {
            super(context);
            paint.setColor(ACCENT);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(context, 1.8f));
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            Path path = new Path();
            path.moveTo(w * 0.22f, h * 0.52f);
            path.lineTo(w * 0.42f, h * 0.71f);
            path.lineTo(w * 0.78f, h * 0.30f);
            canvas.drawPath(path, paint);
        }
    }

    static class StatusPill extends LinearLayout {
        private final View dot;
        private final TextView label;
        private android.animation.ValueAnimator pulse;

        StatusPill(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(context, 10), dp(context, 6), dp(context, 12), dp(context, 6));
            setClickable(true);
            dot = new View(context);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                    dp(context, 7), dp(context, 7));
            dotParams.setMarginEnd(dp(context, 6));
            addView(dot, dotParams);
            label = text(context, "记录中", 11.5f, ACCENT, medium());
            addView(label);
            render(false);
        }

        void render(boolean active) {
            if (pulse != null) {
                pulse.cancel();
                pulse = null;
            }
            dot.setAlpha(1f);
            if (active) {
                dot.setBackground(circleRipple(ACCENT, 0x00000000, getContext()));
                label.setText("记录中");
                label.setTextColor(ACCENT_DEEP);
                setBackground(round(ACCENT_SOFT, 99, getContext()));
                pulse = android.animation.ValueAnimator.ofFloat(1f, 0.35f);
                pulse.setDuration(1100);
                pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                pulse.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(android.animation.ValueAnimator animation) {
                        dot.setAlpha((Float) animation.getAnimatedValue());
                    }
                });
                pulse.start();
            } else {
                dot.setBackground(circleRipple(0xFFBBB2A3, 0x00000000, getContext()));
                label.setText("已暂停");
                label.setTextColor(0xFF7C7467);
                setBackground(round(0xFFEDE7DC, 99, getContext()));
            }
        }
    }

    static class EmptyArt extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        EmptyArt(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h * 0.52f;
            // 地面阴影
            paint.setColor(0x12000000);
            canvas.drawOval(new RectF(cx - w * 0.19f, cy + h * 0.22f,
                    cx + w * 0.19f, cy + h * 0.27f), paint);

            canvas.save();
            canvas.rotate(-4f, cx, cy);
            float cardW = w * 0.34f;
            float cardH = h * 0.55f;
            RectF card = new RectF(cx - cardW / 2f, cy - cardH / 2f,
                    cx + cardW / 2f, cy + cardH / 2f);
            paint.setColor(0xFFF1EBDF);
            canvas.drawRoundRect(card, dp(getContext(), 12), dp(getContext(), 12), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 1.2f));
            paint.setColor(0xFFD9D1C2);
            canvas.drawRoundRect(card, dp(getContext(), 12), dp(getContext(), 12), paint);
            paint.setStyle(Paint.Style.FILL);

            // 顶部小夹子
            RectF clip = new RectF(cx - cardW * 0.17f, card.top - dp(getContext(), 5),
                    cx + cardW * 0.17f, card.top + dp(getContext(), 7));
            paint.setColor(CORAL);
            canvas.drawRoundRect(clip, dp(getContext(), 6), dp(getContext(), 6), paint);

            // 三条内容线
            paint.setColor(0xFFD7CDBB);
            float lineLeft = card.left + cardW * 0.18f;
            float lineRight = card.right - cardW * 0.18f;
            for (int i = 0; i < 3; i++) {
                float y = card.top + cardH * (0.32f + i * 0.16f);
                float right = i == 2 ? lineLeft + (lineRight - lineLeft) * 0.62f : lineRight;
                RectF line = new RectF(lineLeft, y, right, y + dp(getContext(), 4));
                canvas.drawRoundRect(line, dp(getContext(), 2), dp(getContext(), 2), paint);
            }

            // 珊瑚色星标
            paint.setColor(CORAL);
            drawStar(canvas, card.right - cardW * 0.12f, card.bottom - cardH * 0.12f,
                    dp(getContext(), 7), dp(getContext(), 3));
            canvas.restore();

            // 周围点缀
            paint.setColor(0x3323594E);
            canvas.drawCircle(cx - w * 0.27f, cy - h * 0.2f, dp(getContext(), 3.2f), paint);
            paint.setColor(0x33C2603A);
            canvas.drawCircle(cx + w * 0.3f, cy + h * 0.06f, dp(getContext(), 2.6f), paint);
            paint.setColor(0x2AB3822F);
            canvas.drawCircle(cx + w * 0.22f, cy - h * 0.27f, dp(getContext(), 2.2f), paint);
        }

        private void drawStar(Canvas canvas, float cx, float cy, float outer, float inner) {
            Path path = new Path();
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI / 4 * i - Math.PI / 2;
                float radius = i % 2 == 0 ? outer : inner;
                float x = cx + (float) Math.cos(angle) * radius;
                float y = cy + (float) Math.sin(angle) * radius;
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            path.close();
            canvas.drawPath(path, paint);
        }
    }

    // ------------------------------------------------------------------ 色值

    static int alpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    static TextView sectionLabel(Context context, String value) {
        TextView text = text(context, value.toUpperCase(Locale.CHINA), 11f, MUTED, medium());
        text.setLetterSpacing(0.08f);
        return text;
    }

    static class Toggle extends View {
        private boolean checked;
        private float fraction;
        private ValueAnimator animator;
        private OnCheckedListener listener;
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        interface OnCheckedListener {
            void onChecked(boolean checked);
        }

        Toggle(Context context) {
            super(context);
            setClickable(true);
            setFocusable(true);
            knobPaint.setColor(WHITE);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(context, 1));
            strokePaint.setColor(0x14000000);
            setContentDescription("开关");
        }

        void setOnCheckedListener(OnCheckedListener listener) {
            this.listener = listener;
        }

        boolean isChecked() {
            return checked;
        }

        void setChecked(boolean value, boolean animate) {
            checked = value;
            float target = value ? 1f : 0f;
            if (animator != null) animator.cancel();
            if (!animate) {
                fraction = target;
                invalidate();
                return;
            }
            animator = ValueAnimator.ofFloat(fraction, target);
            animator.setDuration(180);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    fraction = (Float) animation.getAnimatedValue();
                    invalidate();
                }
            });
            animator.start();
        }

        @Override
        public boolean performClick() {
            super.performClick();
            setChecked(!checked, true);
            tapFeedback(this);
            if (listener != null) listener.onChecked(checked);
            return true;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(dp(getContext(), 46), dp(getContext(), 28));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float trackH = h * 0.72f;
            float top = (h - trackH) / 2f;
            RectF track = new RectF(0, top, w, top + trackH);
            trackPaint.setColor(blend(0xFFD8D0C2, ACCENT, fraction));
            canvas.drawRoundRect(track, trackH / 2f, trackH / 2f, trackPaint);
            float radius = trackH / 2f - dp(getContext(), 2.5f);
            float cx = trackH / 2f + (w - trackH) * fraction;
            float cy = h / 2f;
            canvas.drawCircle(cx, cy, radius, knobPaint);
            canvas.drawCircle(cx, cy, radius, strokePaint);
        }

        private int blend(int from, int to, float value) {
            int a = (int) (((from >> 24) & 0xff) + (((to >> 24) & 0xff) - ((from >> 24) & 0xff)) * value);
            int r = (int) (((from >> 16) & 0xff) + (((to >> 16) & 0xff) - ((from >> 16) & 0xff)) * value);
            int g = (int) (((from >> 8) & 0xff) + (((to >> 8) & 0xff) - ((from >> 8) & 0xff)) * value);
            int b = (int) ((from & 0xff) + ((to & 0xff) - (from & 0xff)) * value);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    static class MaxHeightScrollView extends ScrollView {
        private int maxHeight;

        MaxHeightScrollView(Context context, int maxHeightPx) {
            super(context);
            this.maxHeight = maxHeightPx;
            setOverScrollMode(OVER_SCROLL_NEVER);
            setVerticalScrollBarEnabled(false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int capped = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
            super.onMeasure(widthMeasureSpec, capped);
        }
    }
}
