package dev.local.globalclip;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PREFS = "settings";
    private static final int REQ_NOTIFICATION = 101;

    private ClipStore store;
    private SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private ListView list;
    private SectionAdapter adapter;
    private LinearLayout setupCard;
    private LinearLayout insightCard;
    private LinearLayout searchRow;
    private TextView setupTitle;
    private TextView setupDesc;
    private TextView setupAction;
    private TextView statToday;
    private TextView statWeek;
    private TextView statPin;
    private TextView insightLine;
    private EditText search;
    private View searchClear;
    private Ui.StatusPill statusPill;
    private HorizontalScrollView chipsScroll;
    private LinearLayout chipsRow;
    private LinearLayout emptyState;
    private TextView emptyTitle;
    private TextView emptySub;
    private TextView emptyAction;
    private TextView emptyAction2;

    private String category = ClipCategory.ALL;
    private String query = "";
    private String chipsSignature = "";
    private boolean pendingEnable;
    private int emptyMode;
    private ClipItem undoItem;
    private LinearLayout snackbar;
    private boolean failed;

    private ClipStore.Listener storeListener;
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (failed) return;
            try {
                if (adapter != null) adapter.notifyDataSetChanged();
                if (store != null) updateStatus(store.size());
                ui.postDelayed(this, 20_000L);
            } catch (Throwable error) {
                showFatal(error);
            }
        }
    };
    private final Runnable hideSnack = new Runnable() {
        @Override
        public void run() {
            dismissSnack();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            startUi();
        } catch (Throwable error) {
            showFatal(error);
        }
    }

    private void startUi() {
        Ui.setBar(this);
        store = ClipStore.get(this);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        setContentView(buildUi());
        storeListener = new ClipStore.Listener() {
            @Override
            public void onStoreChanged() {
                safeRefresh();
            }
        };
        store.addListener(storeListener);
        safeRefresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (failed) return;
        try {
            if (storeListener == null) {
                storeListener = new ClipStore.Listener() {
                    @Override
                    public void onStoreChanged() {
                        safeRefresh();
                    }
                };
            }
            store.addListener(storeListener);
            safeRefresh();
            ui.removeCallbacks(ticker);
            ui.postDelayed(ticker, 20_000L);
            if (pendingEnable) {
                pendingEnable = false;
                if (Settings.canDrawOverlays(this)) {
                    enableCapture();
                }
            }
        } catch (Throwable error) {
            showFatal(error);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(ticker);
        if (store != null) store.removeListener(storeListener);
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        if (store != null) store.removeListener(storeListener);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        safeRefresh();
    }

    private void safeRefresh() {
        if (failed) return;
        try {
            refreshAll();
        } catch (Throwable error) {
            showFatal(error);
        }
    }

    /** 诊断兜底：即使主界面构建/刷新失败，也把错误留在屏幕上，并尝试写入下载目录。 */
    private void showFatal(final Throwable error) {
        if (failed) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            ui.post(new Runnable() {
                @Override
                public void run() {
                    showFatal(error);
                }
            });
            return;
        }
        failed = true;
        String report = CrashReport.describe(error);
        try {
            CrashReport.write(this, "jiacang-crash.txt", report);
        } catch (Throwable ignored) {
        }
        ui.removeCallbacksAndMessages(null);
        try {
            int pad = (int) (16 * getResources().getDisplayMetrics().density + 0.5f);
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(0xFFF7F3EC);
            root.setPadding(pad, pad, pad, pad);

            TextView title = new TextView(this);
            title.setText("剪藏启动失败");
            title.setTextSize(20);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextColor(0xFF1E1C19);
            root.addView(title, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView hint = new TextView(this);
            hint.setText("请把下面这段错误信息截图或复制发给开发者；同时已尝试写入「下载」目录的 jiacang-crash.txt。");
            hint.setTextSize(12);
            hint.setTextColor(0xFF776F62);
            hint.setLineSpacing(pad / 2f, 1f);
            LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hintParams.topMargin = pad / 2;
            root.addView(hint, hintParams);

            TextView content = new TextView(this);
            content.setText(report);
            content.setTextSize(11);
            content.setTextColor(0xFF2A2620);
            content.setTextIsSelectable(true);
            content.setPadding(pad, pad, pad, pad);
            content.setBackgroundColor(0xFFFFFFFF);
            android.widget.ScrollView scroll = new android.widget.ScrollView(this);
            scroll.addView(content, new android.widget.ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
            scrollParams.topMargin = pad / 2;
            root.addView(scroll, scrollParams);

            Button copy = new Button(this);
            copy.setText("复制错误信息");
            copy.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    try {
                        ClipboardManager clipboard = (ClipboardManager)
                                getSystemService(CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("jiacang-crash", report));
                            android.widget.Toast.makeText(MainActivity.this,
                                    "已复制，请发给开发者", android.widget.Toast.LENGTH_LONG).show();
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            copyParams.topMargin = pad / 2;
            root.addView(copy, copyParams);
            setContentView(root);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onBackPressed() {
        if (search != null && search.hasFocus() && !search.getText().toString().isEmpty()) {
            search.setText("");
            search.clearFocus();
            hideKeyboard();
            return;
        }
        super.onBackPressed();
    }

    // ------------------------------------------------------------------ 布局

    private View buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.PAPER);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setBackgroundColor(Ui.PAPER);
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, Ui.dp(this, 26));

        View header = buildHeader();
        emptyState = buildEmptyState();
        list.addHeaderView(header, null, false);
        list.addFooterView(emptyState, null, false);

        adapter = new SectionAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                Row row = rowAt(position);
                if (row != null && row.item != null) {
                    Ui.tapFeedback(view);
                    Sheets.showDetail(MainActivity.this, row.item);
                }
            }
        });
        list.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                Row row = rowAt(position);
                if (row != null && row.item != null) {
                    Ui.tapFeedback(view);
                    Sheets.showDetail(MainActivity.this, row.item);
                    return true;
                }
                return false;
            }
        });
        list.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(android.widget.AbsListView view, int scrollState) {
                if (scrollState != SCROLL_STATE_IDLE && search != null && search.hasFocus()) {
                    hideKeyboard();
                    search.clearFocus();
                }
            }

            @Override
            public void onScroll(android.widget.AbsListView view, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount) {
            }
        });

        content.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);

        // 标题行
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(Ui.dp(this, 18), Ui.dp(this, 22), Ui.dp(this, 16), 0);

        TextView title = Ui.text(this, "剪藏", 31, Ui.INK, Ui.serif());
        title.setLetterSpacing(0.02f);
        titleRow.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View titleDot = new View(this);
        titleDot.setBackground(Ui.circleRipple(Ui.CORAL, 0, this));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 6), Ui.dp(this, 6));
        dotParams.gravity = Gravity.BOTTOM;
        dotParams.setMargins(Ui.dp(this, 8), 0, 0, Ui.dp(this, 7));
        titleRow.addView(titleDot, dotParams);

        View spacer = new View(this);
        titleRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

        Ui.SettingsIcon gear = new Ui.SettingsIcon(this);
        gear.setContentDescription("设置与权限");
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Ui.tapFeedback(view);
                Sheets.showSettings(MainActivity.this);
            }
        });
        titleRow.addView(gear, new LinearLayout.LayoutParams(
                Ui.dp(this, 44), Ui.dp(this, 44)));
        header.addView(titleRow);

        // 副标题 + 状态
        LinearLayout subRow = new LinearLayout(this);
        subRow.setOrientation(LinearLayout.HORIZONTAL);
        subRow.setGravity(Gravity.CENTER_VERTICAL);
        subRow.setPadding(Ui.dp(this, 19), Ui.dp(this, 5), Ui.dp(this, 16), 0);

        TextView subtitle = Ui.text(this, "全局剪贴板 · 自动分类", 11.5f, Ui.MUTED, Ui.medium());
        subtitle.setLetterSpacing(0.02f);
        subtitle.setMaxLines(1);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subRow.addView(subtitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        statusPill = new Ui.StatusPill(this);
        statusPill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Ui.tapFeedback(view);
                toggleCapture();
            }
        });
        subRow.addView(statusPill);
        header.addView(subRow);

        // 首次/权限提示
        setupCard = buildSetupCard();
        header.addView(setupCard);

        // 概览
        insightCard = buildInsightCard();
        header.addView(insightCard);

        // 搜索
        searchRow = buildSearchRow();
        header.addView(searchRow);

        // 分类标签
        chipsScroll = new HorizontalScrollView(this);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        chipsScroll.setFillViewport(false);
        chipsScroll.setClipToPadding(false);
        chipsRow = new LinearLayout(this);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        chipsScroll.addView(chipsRow, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams chipsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 34));
        chipsParams.topMargin = Ui.dp(this, 14);
        header.addView(chipsScroll, chipsParams);

        Ui.addSpace(header, 2);
        return header;
    }

    private LinearLayout buildSetupCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 15), Ui.dp(this, 14), Ui.dp(this, 15), Ui.dp(this, 14));
        card.setBackground(Ui.ripple(Ui.round(Ui.CORAL_SOFT, 16, this), 0x14000000, 16, this));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), 0);
        card.setLayoutParams(params);
        card.setVisibility(View.GONE);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Ui.GlyphBadge badge = new Ui.GlyphBadge(this);
        badge.setData("!", Ui.CORAL);
        badge.setSizeDp(24);
        top.addView(badge);
        setupTitle = Ui.text(this, "还差一步", 13, Ui.INK, Ui.medium());
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMarginStart(Ui.dp(this, 9));
        top.addView(setupTitle, titleParams);
        card.addView(top);

        setupDesc = Ui.text(this, "允许悬浮窗后，其他应用里复制的内容也能自动收下。",
                11.5f, Ui.INK_SOFT, Typeface.DEFAULT);
        setupDesc.setLineSpacing(Ui.dp(this, 3), 1f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descParams.topMargin = Ui.dp(this, 7);
        card.addView(setupDesc, descParams);

        setupAction = Ui.pill(this, "去允许", 11.5f, Ui.WHITE, Ui.ACCENT, 0x33FFFFFF);
        setupAction.setPadding(Ui.dp(this, 14), Ui.dp(this, 7),
                Ui.dp(this, 14), Ui.dp(this, 7));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = Ui.dp(this, 10);
        setupAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Ui.tapFeedback(view);
                if (!Settings.canDrawOverlays(MainActivity.this)) {
                    pendingEnable = true;
                    openOverlaySettings();
                } else {
                    toggleCapture();
                }
            }
        });
        card.addView(setupAction, actionParams);
        return card;
    }

    private LinearLayout buildInsightCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 15), Ui.dp(this, 16), Ui.dp(this, 14));
        card.setBackground(Ui.round(Ui.SURFACE, 18, this));
        card.setElevation(Ui.dp(this, 1.2f));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), 0);
        card.setLayoutParams(params);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setGravity(Gravity.CENTER_VERTICAL);
        stats.addView(statBlock("今日", true));
        stats.addView(statDivider());
        stats.addView(statBlock("本周", false));
        stats.addView(statDivider());
        stats.addView(statBlock("置顶", false));
        card.addView(stats);

        insightLine = Ui.text(this, "", 11, Ui.MUTED, Typeface.DEFAULT);
        insightLine.setMaxLines(1);
        insightLine.setEllipsize(android.text.TextUtils.TruncateAt.END);
        insightLine.setLineSpacing(Ui.dp(this, 2), 1f);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lineParams.topMargin = Ui.dp(this, 10);
        card.addView(insightLine, lineParams);
        return card;
    }

    private LinearLayout statBlock(String label, boolean first) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView value = Ui.text(this, "0", 21, Ui.INK, Ui.serif());
        if (label.equals("今日")) statToday = value;
        else if (label.equals("本周")) statWeek = value;
        else statPin = value;
        block.addView(value);
        TextView caption = Ui.text(this, label, 10, Ui.MUTED, Ui.medium());
        caption.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        captionParams.topMargin = Ui.dp(this, 1);
        block.addView(caption, captionParams);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        block.setLayoutParams(params);
        return block;
    }

    private View statDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Ui.dp(this, 1), Ui.dp(this, 30));
        params.setMargins(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
        divider.setLayoutParams(params);
        return divider;
    }

    private LinearLayout buildSearchRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 6), 0);
        row.setBackground(Ui.roundStroke(Ui.SURFACE, Ui.LINE, 23, 1, this));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46));
        params.setMargins(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), 0);
        row.setLayoutParams(params);
        row.setElevation(Ui.dp(this, 0.6f));

        Ui.SearchIcon icon = new Ui.SearchIcon(this);
        icon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                focusSearch();
            }
        });
        row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 19), Ui.dp(this, 19)));

        search = new EditText(this);
        search.setSingleLine(true);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setBackground(null);
        search.setHint("搜内容、链接、验证码…");
        search.setHintTextColor(Ui.FAINT);
        search.setTextColor(Ui.INK);
        search.setTextSize(13);
        search.setTypeface(Typeface.DEFAULT);
        search.setIncludeFontPadding(false);
        search.setPadding(Ui.dp(this, 9), 0, Ui.dp(this, 4), 0);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        row.addView(search, searchParams);

        searchClear = new Ui.CloseIcon(this);
        searchClear.setVisibility(View.GONE);
        searchClear.setContentDescription("清空搜索");
        searchClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                search.setText("");
                search.clearFocus();
                hideKeyboard();
            }
        });
        row.addView(searchClear, new LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28)));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                focusSearch();
            }
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString().trim();
                if (searchClear != null) {
                    searchClear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                }
                applyFilter();
            }
        });
        search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView view, int actionId, android.view.KeyEvent event) {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    hideKeyboard();
                    search.clearFocus();
                    return true;
                }
                return false;
            }
        });
        return row;
    }

    private LinearLayout buildEmptyState() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        container.setPadding(Ui.dp(this, 30), Ui.dp(this, 10), Ui.dp(this, 30), Ui.dp(this, 34));

        Ui.EmptyArt art = new Ui.EmptyArt(this);
        LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 168), Ui.dp(this, 142));
        artParams.topMargin = Ui.dp(this, 6);
        container.addView(art, artParams);

        emptyTitle = Ui.text(this, "还没有收下内容", 17.5f, Ui.INK, Ui.medium());
        emptyTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = Ui.dp(this, 10);
        container.addView(emptyTitle, titleParams);

        emptySub = Ui.text(this, "复制或剪切一段文字，开启后台记录后，它会自动出现在这里。",
                12.5f, Ui.MUTED, Typeface.DEFAULT);
        emptySub.setGravity(Gravity.CENTER);
        emptySub.setLineSpacing(Ui.dp(this, 4), 1f);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = Ui.dp(this, 6);
        container.addView(emptySub, subParams);

        emptyAction = Ui.pill(this, "读取当前剪贴板", 13, Ui.WHITE, Ui.ACCENT, 0x33FFFFFF);
        emptyAction.setPadding(Ui.dp(this, 18), Ui.dp(this, 9),
                Ui.dp(this, 18), Ui.dp(this, 9));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = Ui.dp(this, 16);
        emptyAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Ui.tapFeedback(view);
                if (emptyMode == 1) {
                    resetFilters();
                } else {
                    readClipboardNow();
                }
            }
        });
        container.addView(emptyAction, actionParams);

        emptyAction2 = Ui.pill(this, "开启后台记录", 12, Ui.ACCENT, Ui.SOFT, 0x1A23594E);
        emptyAction2.setPadding(Ui.dp(this, 16), Ui.dp(this, 8),
                Ui.dp(this, 16), Ui.dp(this, 8));
        LinearLayout.LayoutParams action2Params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        action2Params.topMargin = Ui.dp(this, 9);
        emptyAction2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Ui.tapFeedback(view);
                if (!Settings.canDrawOverlays(MainActivity.this)) {
                    pendingEnable = true;
                    openOverlaySettings();
                } else {
                    toggleCapture();
                }
            }
        });
        container.addView(emptyAction2, action2Params);
        return container;
    }

    // ------------------------------------------------------------------ 数据

    void refreshAll() {
        if (adapter == null || isFinishing()) return;
        HashMap<String, Integer> counts = store.counts();
        ClipStore.Stats stats = store.stats();
        if (stats.total == 0) {
            category = ClipCategory.ALL;
            query = "";
            if (search != null && !search.getText().toString().isEmpty()) search.setText("");
        }
        if (category != null && !category.equals(ClipCategory.ALL)
                && (counts.get(category) == null || counts.get(category) == 0)) {
            category = ClipCategory.ALL;
        }
        updateStatus(store.size());
        updateInsight(stats, counts);
        updateChips(counts);
        applyFilter();
    }

    private void applyFilter() {
        if (adapter == null) return;
        List<ClipItem> all = store.all();
        ArrayList<ClipItem> filtered = new ArrayList<>();
        String needle = query.toLowerCase(Locale.ROOT);
        for (ClipItem item : all) {
            if (!category.equals(ClipCategory.ALL) && !category.equals(item.category)) continue;
            if (!needle.isEmpty() && !matches(item, needle)) continue;
            filtered.add(item);
        }
        ArrayList<Row> rows = buildRows(filtered);
        adapter.setRows(rows);
        updateEmpty(all.size(), filtered.size());
        updateInsightLine(filtered.size());
    }

    private boolean matches(ClipItem item, String needle) {
        if (item.text != null && item.text.toLowerCase(Locale.ROOT).contains(needle)) return true;
        if (item.title != null && item.title.toLowerCase(Locale.ROOT).contains(needle)) return true;
        if (item.detail != null && item.detail.toLowerCase(Locale.ROOT).contains(needle)) return true;
        if (item.sourceLabel().contains(needle)) return true;
        return ClipCategory.label(item.category).contains(needle);
    }

    private ArrayList<Row> buildRows(List<ClipItem> filtered) {
        ArrayList<Row> rows = new ArrayList<>();
        ArrayList<ClipItem> pins = new ArrayList<>();
        ArrayList<ClipItem> rest = new ArrayList<>();
        for (ClipItem item : filtered) {
            if (item.pinned) pins.add(item);
            else rest.add(item);
        }
        if (!pins.isEmpty()) {
            rows.add(Row.header("置顶", pins.size(), true));
            for (ClipItem item : pins) rows.add(Row.item(item));
        }
        Group current = null;
        for (ClipItem item : rest) {
            String key = ClipItem.dayKey(item.ts);
            if (current == null || !current.key.equals(key)) {
                if (current != null) {
                    rows.add(Row.header(current.label, current.items.size(), false));
                    for (ClipItem member : current.items) rows.add(Row.item(member));
                }
                current = new Group();
                current.key = key;
                current.label = ClipItem.dayLabel(item.ts);
            }
            current.items.add(item);
        }
        if (current != null) {
            rows.add(Row.header(current.label, current.items.size(), false));
            for (ClipItem member : current.items) rows.add(Row.item(member));
        }
        return rows;
    }

    private void updateStatus(int total) {
        if (statusPill == null) return;
        boolean active = CaptureService.isRunning();
        statusPill.render(active);
        updateSetup(total, active);
    }

    private void updateSetup(int total, boolean active) {
        if (setupCard == null) return;
        boolean overlay = Settings.canDrawOverlays(this);
        boolean show = (!overlay || !active) && total > 0;
        int previous = setupCard.getVisibility();
        setupCard.setVisibility(show ? View.VISIBLE : View.GONE);
        if (previous != setupCard.getVisibility() && list != null) list.requestLayout();
        if (!show) return;
        if (!overlay) {
            setupTitle.setText("后台记录还没打通");
            setupDesc.setText("系统只允许有焦点的应用读剪贴板。允许「显示在其他应用上层」后，剪藏才能在你复制时借 0.2 秒焦点读取。");
            setupAction.setText("去允许");
        } else {
            setupTitle.setText("记录已暂停");
            setupDesc.setText("点亮记录开关，复制或剪切的内容会自动收进这里。");
            setupAction.setText("开启记录");
        }
    }

    private void updateInsight(ClipStore.Stats stats, HashMap<String, Integer> counts) {
        statToday.setText(String.valueOf(stats.today));
        statWeek.setText(String.valueOf(stats.week));
        statPin.setText(String.valueOf(stats.pinned));
        SpannableStringBuilder builder = new SpannableStringBuilder();
        ArrayList<String> keys = new ArrayList<>();
        int total = 0;
        for (String key : ClipCategory.ORDER) {
            Integer count = counts.get(key);
            if (count == null || count == 0) continue;
            keys.add(key);
            total += count;
        }
        if (total == 0) {
            builder.append("复制或剪切一段内容，几秒后会自动整理到这里");
        } else {
            int shown = 0;
            for (String key : keys) {
                if (shown >= 3) break;
                if (shown > 0) builder.append("   ");
                int start = builder.length();
                builder.append("●");
                builder.setSpan(new ForegroundColorSpan(ClipCategory.color(key)),
                        start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.append(" ").append(ClipCategory.label(key)).append(" ")
                        .append(String.valueOf(counts.get(key)));
                shown++;
            }
            if (keys.size() > 3) {
                int others = 0;
                for (int i = 3; i < keys.size(); i++) others += counts.get(keys.get(i));
                builder.append("   其他 ").append(String.valueOf(others));
            }
        }
        insightLine.setText(builder);
    }

    private void updateInsightLine(int filtered) {
        if (insightLine == null) return;
        if (!query.isEmpty() || !category.equals(ClipCategory.ALL)) {
            String where = category.equals(ClipCategory.ALL)
                    ? "搜索 “" + ClipClassifier.shorten(query, 12) + "”"
                    : ClipCategory.label(category);
            insightLine.setText(where + " · " + filtered + " 条");
        } else {
            updateInsight(store.stats(), store.counts());
        }
    }

    private void updateChips(HashMap<String, Integer> counts) {
        if (chipsRow == null) return;
        int total = 0;
        for (Integer value : counts.values()) total += value;
        StringBuilder signature = new StringBuilder(category).append('|').append(total);
        for (String key : ClipCategory.ORDER) {
            signature.append('|').append(key).append(':').append(counts.get(key) == null ? 0 : counts.get(key));
        }
        if (signature.toString().equals(chipsSignature)) return;
        chipsSignature = signature.toString();
        final int scrollX = chipsScroll.getScrollX();
        chipsRow.removeAllViews();
        addChip("全部", total, ClipCategory.ALL);
        for (String key : ClipCategory.ORDER) {
            Integer count = counts.get(key);
            if (count == null || count == 0) continue;
            addChip(ClipCategory.label(key), count, key);
        }
        chipsScroll.post(new Runnable() {
            @Override
            public void run() {
                chipsScroll.scrollTo(scrollX, 0);
            }
        });
    }

    private void addChip(String label, int count, final String key) {
        boolean selected = category.equals(key);
        TextView chip = Ui.text(this, label + " " + count, 11.5f,
                selected ? Ui.WHITE : Ui.INK_SOFT, Ui.medium());
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding(Ui.dp(this, 13), 0, Ui.dp(this, 13), 0);
        if (selected) {
            chip.setBackground(Ui.ripple(Ui.round(Ui.ACCENT, 16, this), 0x33FFFFFF, 16, this));
        } else {
            chip.setBackground(Ui.ripple(Ui.roundStroke(Ui.SURFACE, Ui.LINE, 16, 1, this),
                    0x12000000, 16, this));
        }
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Ui.tapFeedback(view);
                category = key;
                chipsSignature = "";
                updateChips(store.counts());
                applyFilter();
                list.setSelection(0);
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 32));
        params.setMarginEnd(Ui.dp(this, 8));
        chipsRow.addView(chip, params);
    }

    private void updateEmpty(int total, int filtered) {
        if (emptyState == null) return;
        boolean empty = filtered == 0;
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        boolean blank = total == 0;
        if (insightCard != null) insightCard.setVisibility(blank ? View.GONE : View.VISIBLE);
        if (searchRow != null) searchRow.setVisibility(blank ? View.GONE : View.VISIBLE);
        if (chipsScroll != null) chipsScroll.setVisibility(blank ? View.GONE : View.VISIBLE);
        if (list != null) list.requestLayout();
        if (!empty) return;
        if (list != null) list.setSelection(0);
        if (total == 0) {
            emptyMode = 0;
            emptyTitle.setText("还没有收下内容");
            emptySub.setText("复制或剪切一段文字，开启后台记录后，它会自动出现在这里。");
            emptyAction.setText("读取当前剪贴板");
            boolean overlay = Settings.canDrawOverlays(this);
            boolean active = CaptureService.isRunning();
            emptyAction2.setVisibility(overlay && active ? View.GONE : View.VISIBLE);
            emptyAction2.setText(overlay ? "开启后台记录" : "先允许悬浮窗");
        } else {
            emptyMode = 1;
            emptyTitle.setText("没有找到匹配内容");
            emptySub.setText(category.equals(ClipCategory.ALL)
                    ? "换个关键词试试，或者清空搜索看看全部内容。"
                    : "「" + ClipCategory.label(category) + "」分类里还没有内容，切回全部看看。");
            emptyAction.setText("清除筛选");
            emptyAction2.setVisibility(View.GONE);
        }
    }

    private void resetFilters() {
        category = ClipCategory.ALL;
        query = "";
        if (search != null) search.setText("");
        chipsSignature = "";
        updateChips(store.counts());
        applyFilter();
        list.setSelection(0);
        hideKeyboard();
    }

    // ------------------------------------------------------------------ 交互

    void toggleCapture() {
        boolean active = CaptureService.isRunning();
        if (active) {
            prefs.edit().putBoolean("enabled", false).apply();
            stopService(new Intent(this, CaptureService.class));
            Ui.toast(this, "已暂停记录");
            safeRefresh();
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            pendingEnable = true;
            Ui.toast(this, "先允许悬浮窗，后台才能读到剪贴板");
            openOverlaySettings();
            return;
        }
        enableCapture();
    }

    void enableCapture() {
        requestNotificationPermissionIfNeeded();
        prefs.edit().putBoolean("enabled", true).apply();
        startCaptureService();
        Ui.toast(this, "开始记录，去复制一段试试");
        safeRefresh();
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                safeRefresh();
            }
        }, 900L);
    }

    private void startCaptureService() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(new Intent(this, CaptureService.class));
            } else {
                startService(new Intent(this, CaptureService.class));
            }
        } catch (Exception error) {
            prefs.edit().putBoolean("enabled", false).apply();
            Ui.toast(this, "启动被系统拦住了，请检查通知/后台权限");
            refreshAll();
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFICATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshAll();
    }

    void readClipboardNow() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Ui.toast(this, "剪贴板不可用");
            return;
        }
        try {
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                Ui.toast(this, "剪贴板里还没有文字");
                return;
            }
            ClipDescription description = clip.getDescription();
            if (description != null) {
                PersistableBundle extras = description.getExtras();
                if (extras != null && extras.getBoolean("android.content.extra.IS_SENSITIVE", false)) {
                    Ui.toast(this, "这是敏感内容，剪藏不会记录");
                    return;
                }
            }
            CharSequence value = clip.getItemAt(0).coerceToText(this);
            if (value == null || value.toString().trim().isEmpty()) {
                Ui.toast(this, "剪贴板里还没有文字");
                return;
            }
            ClipStore.AddResult result = store.add(value.toString(), System.currentTimeMillis(), "manual");
            if (result == null) {
                Ui.toast(this, "没有识别到内容");
            } else if (result.fresh) {
                Ui.toast(this, "已收下，分类好了");
            } else {
                Ui.toast(this, "这条已经在列表里了");
            }
        } catch (Exception error) {
            Ui.toast(this, "读取剪贴板失败");
        }
    }

    void showUndo(ClipItem item) {
        if (item == null || root == null) return;
        dismissSnack();
        undoItem = item;
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 15), Ui.dp(this, 11), Ui.dp(this, 8), Ui.dp(this, 11));
        bar.setBackground(Ui.round(0xF21F1C19, 14, this));
        bar.setElevation(Ui.dp(this, 5));

        TextView text = Ui.text(this, "已删除一条内容", 12, 0xFFF7F3EC, Ui.medium());
        text.setMaxLines(1);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bar.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView undo = Ui.pill(this, "撤销", 12, 0xFFA9D8CB, 0x00000000, 0x33FFFFFF);
        undo.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        undo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Ui.tapFeedback(view);
                if (undoItem != null) {
                    store.restore(undoItem);
                    undoItem = null;
                    Ui.toast(MainActivity.this, "已恢复");
                }
                dismissSnack();
            }
        });
        bar.addView(undo);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM;
        params.setMargins(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 22));
        snackbar = bar;
        root.addView(bar, params);
        bar.setAlpha(0f);
        bar.setTranslationY(Ui.dp(this, 18));
        bar.animate().alpha(1f).translationY(0f).setDuration(200).start();
        ui.removeCallbacks(hideSnack);
        ui.postDelayed(hideSnack, 5_000L);
    }

    private void dismissSnack() {
        ui.removeCallbacks(hideSnack);
        if (snackbar == null) return;
        final LinearLayout bar = snackbar;
        snackbar = null;
        bar.animate().alpha(0f).translationY(Ui.dp(this, 14)).setDuration(160)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            root.removeView(bar);
                        } catch (Exception ignored) {
                        }
                    }
                }).start();
    }

    private Row rowAt(int position) {
        int index = position - list.getHeaderViewsCount();
        if (index < 0 || index >= adapter.getCount()) return null;
        return (Row) adapter.getItem(index);
    }

    private void focusSearch() {
        if (search == null) return;
        search.requestFocus();
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) manager.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus == null) return;
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(focus.getWindowToken(), 0);
    }

    // ------------------------------------------------------------------ 权限

    void openOverlaySettings() {
        if (Settings.canDrawOverlays(this)) {
            Ui.toast(this, "悬浮窗权限已允许");
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception error) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (Exception ignored) {
                Ui.toast(this, "请到系统设置里手动开启悬浮窗权限");
            }
        }
    }

    void requestNotificationOrSettings() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFICATION);
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
        } catch (Exception error) {
            Ui.toast(this, "请到系统设置里打开通知权限");
        }
    }

    void openBatterySettings() {
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        if (power != null && power.isIgnoringBatteryOptimizations(getPackageName())) {
            Ui.toast(this, "已经在电池白名单里");
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception error) {
            Ui.toast(this, "请到系统设置 → 电池 → 不限制后台 里手动设置");
        }
    }

    void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception error) {
            Ui.toast(this, "无法打开无障碍设置");
        }
    }

    boolean isAccessibilityEnabled() {
        try {
            String enabled = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null) return false;
            String full = getPackageName() + "/" + CutWatchService.class.getName();
            String shortName = getPackageName() + "/.CutWatchService";
            return enabled.contains(full) || enabled.contains(shortName);
        } catch (Exception error) {
            return false;
        }
    }

    boolean isNotificationGranted() {
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                == PackageManager.PERMISSION_GRANTED;
    }

    boolean isBatteryIgnoring() {
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        return power != null && power.isIgnoringBatteryOptimizations(getPackageName());
    }

    ClipStore store() {
        return store;
    }

    SharedPreferences prefs() {
        return prefs;
    }

    // ------------------------------------------------------------------ 列表

    static class Row {
        static final int TYPE_HEADER = 0;
        static final int TYPE_ITEM = 1;

        int type;
        String label;
        int count;
        boolean pinnedSection;
        ClipItem item;

        static Row header(String label, int count, boolean pinned) {
            Row row = new Row();
            row.type = TYPE_HEADER;
            row.label = label;
            row.count = count;
            row.pinnedSection = pinned;
            return row;
        }

        static Row item(ClipItem item) {
            Row row = new Row();
            row.type = TYPE_ITEM;
            row.item = item;
            return row;
        }
    }

    static class Group {
        String key;
        String label;
        final ArrayList<ClipItem> items = new ArrayList<>();
    }

    class SectionAdapter extends BaseAdapter {
        private final ArrayList<Row> rows = new ArrayList<>();

        void setRows(ArrayList<Row> next) {
            rows.clear();
            rows.addAll(next);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).type;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return rows.get(position).type == Row.TYPE_ITEM;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row row = rows.get(position);
            if (row.type == Row.TYPE_HEADER) {
                return buildSectionHeader(row, convertView);
            }
            Holder holder;
            if (convertView == null || convertView.getTag() == null) {
                holder = new Holder();
                LinearLayout wrapper = new LinearLayout(MainActivity.this);
                wrapper.setPadding(Ui.dp(MainActivity.this, 16), 0,
                        Ui.dp(MainActivity.this, 16), Ui.dp(MainActivity.this, 9));
                LinearLayout card = new LinearLayout(MainActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(Ui.dp(MainActivity.this, 14), Ui.dp(MainActivity.this, 13),
                        Ui.dp(MainActivity.this, 14), Ui.dp(MainActivity.this, 12));
                card.setBackground(Ui.cardBackground(MainActivity.this));
                card.setElevation(Ui.dp(MainActivity.this, 1.4f));
                card.setClickable(true);

                LinearLayout top = new LinearLayout(MainActivity.this);
                top.setOrientation(LinearLayout.HORIZONTAL);
                top.setGravity(Gravity.CENTER_VERTICAL);
                holder.badge = new Ui.GlyphBadge(MainActivity.this);
                top.addView(holder.badge, new LinearLayout.LayoutParams(
                        Ui.dp(MainActivity.this, 27), Ui.dp(MainActivity.this, 27)));
                holder.title = Ui.text(MainActivity.this, "", 14.8f, Ui.INK, Ui.medium());
                holder.title.setMaxLines(2);
                holder.title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                holder.title.setLineSpacing(Ui.dp(MainActivity.this, 2), 1f);
                LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                titleParams.setMarginStart(Ui.dp(MainActivity.this, 10));
                top.addView(holder.title, titleParams);
                holder.time = Ui.text(MainActivity.this, "", 10.5f, Ui.FAINT, Typeface.DEFAULT);
                LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                timeParams.setMarginStart(Ui.dp(MainActivity.this, 8));
                top.addView(holder.time, timeParams);
                card.addView(top);

                holder.body = Ui.text(MainActivity.this, "", 12.5f, Ui.INK_SOFT, Typeface.DEFAULT);
                holder.body.setMaxLines(2);
                holder.body.setEllipsize(android.text.TextUtils.TruncateAt.END);
                holder.body.setLineSpacing(Ui.dp(MainActivity.this, 3), 1f);
                LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                bodyParams.topMargin = Ui.dp(MainActivity.this, 7);
                card.addView(holder.body, bodyParams);

                LinearLayout bottom = new LinearLayout(MainActivity.this);
                bottom.setOrientation(LinearLayout.HORIZONTAL);
                bottom.setGravity(Gravity.CENTER_VERTICAL);
                holder.meta = Ui.text(MainActivity.this, "", 10.5f, Ui.MUTED, Typeface.DEFAULT);
                holder.meta.setMaxLines(1);
                holder.meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
                bottom.addView(holder.meta, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                holder.action = Ui.pill(MainActivity.this, "复制", 10.8f,
                        Ui.ACCENT_DEEP, Ui.ACCENT_SOFT, 0x1A23594E);
                holder.action.setPadding(Ui.dp(MainActivity.this, 11), Ui.dp(MainActivity.this, 5),
                        Ui.dp(MainActivity.this, 11), Ui.dp(MainActivity.this, 5));
                bottom.addView(holder.action, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                bottomParams.topMargin = Ui.dp(MainActivity.this, 9);
                card.addView(bottom, bottomParams);

                wrapper.addView(card, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                wrapper.setTag(holder);
                holder.card = card;
                convertView = wrapper;
            } else {
                holder = (Holder) convertView.getTag();
            }
            bindCard(holder, row.item);
            return convertView;
        }

        private View buildSectionHeader(Row row, View convertView) {
            LinearLayout line;
            HeaderHolder holder;
            if (convertView == null || convertView.getTag() == null
                    || !(convertView.getTag() instanceof HeaderHolder)) {
                line = new LinearLayout(MainActivity.this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setGravity(Gravity.CENTER_VERTICAL);
                line.setPadding(Ui.dp(MainActivity.this, 18), Ui.dp(MainActivity.this, 14),
                        Ui.dp(MainActivity.this, 18), Ui.dp(MainActivity.this, 7));
                TextView label = Ui.text(MainActivity.this, "", 10.5f, Ui.MUTED, Ui.medium());
                label.setLetterSpacing(0.08f);
                View rule = new View(MainActivity.this);
                rule.setBackgroundColor(Ui.LINE);
                LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(
                        0, Ui.dp(MainActivity.this, 1), 1);
                ruleParams.setMarginStart(Ui.dp(MainActivity.this, 10));
                line.addView(label);
                line.addView(rule, ruleParams);
                holder = new HeaderHolder(label, rule);
                line.setTag(holder);
                convertView = line;
            } else {
                holder = (HeaderHolder) convertView.getTag();
            }
            holder.label.setText(row.label + " · " + row.count);
            holder.label.setTextColor(row.pinnedSection ? Ui.ACCENT : Ui.MUTED);
            return convertView;
        }

        private void bindCard(final Holder holder, final ClipItem item) {
            holder.badge.setData(ClipCategory.glyph(item.category), ClipCategory.color(item.category));
            holder.title.setText(item.title == null ? item.text : item.title);
            holder.time.setText(ClipItem.timeText(item.ts));
            String preview = item.preview();
            String body = preview;
            String trimmedTitle = item.title == null ? "" : item.title.trim();
            if (!trimmedTitle.isEmpty() && trimmedTitle.equals(item.text.trim())) body = "";
            holder.body.setText(body);
            holder.body.setVisibility(body.isEmpty() ? View.GONE : View.VISIBLE);
            boolean mono = ClipCategory.CODE.equals(item.category)
                    || ClipCategory.CMD.equals(item.category)
                    || ClipCategory.PATH.equals(item.category);
            holder.body.setTypeface(mono ? Ui.mono() : Typeface.DEFAULT);
            holder.body.setTextSize(mono ? 11.8f : 12.5f);

            StringBuilder meta = new StringBuilder();
            meta.append(item.sourceLabel());
            if (item.detail != null && !item.detail.isEmpty()) {
                meta.append(" · ").append(item.detail);
            }
            if (item.count > 1) meta.append(" · ").append(item.count).append(" 次");
            if (item.pinned) meta.append(" · 置顶");
            holder.meta.setText(meta.toString());

            holder.card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Ui.tapFeedback(view);
                    Sheets.showDetail(MainActivity.this, item);
                }
            });
            holder.card.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    Ui.tapFeedback(view);
                    Sheets.showDetail(MainActivity.this, item);
                    return true;
                }
            });
            bindAction(holder.action, item);
        }

        class Holder {
            LinearLayout card;
            Ui.GlyphBadge badge;
            TextView title;
            TextView time;
            TextView body;
            TextView meta;
            TextView action;
        }

        class HeaderHolder {
            final TextView label;
            final View rule;

            HeaderHolder(TextView label, View rule) {
                this.label = label;
                this.rule = rule;
            }
        }
    }

    private void bindAction(TextView action, final ClipItem item) {
        String key = item.category == null ? ClipCategory.TEXT : item.category;
        if (ClipCategory.OTP.equals(key) && item.extract != null) {
            styleAction(action, "复制验证码", ClipCategory.OTP);
            action.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Ui.tapFeedback(view);
                    Ui.copy(MainActivity.this, item.extract);
                    Ui.toast(MainActivity.this, "验证码已复制");
                }
            });
        } else if (ClipCategory.LINK.equals(key) && item.extract != null) {
            styleAction(action, "打开", ClipCategory.LINK);
            action.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Ui.tapFeedback(view);
                    Ui.openUrl(MainActivity.this, item.extract);
                }
            });
        } else if (ClipCategory.CONTACT.equals(key) && item.extract != null) {
            boolean mail = item.extract.contains("@");
            styleAction(action, mail ? "写邮件" : "拨号", ClipCategory.CONTACT);
            action.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Ui.tapFeedback(view);
                    if (item.extract.contains("@")) Ui.mail(MainActivity.this, item.extract);
                    else Ui.dial(MainActivity.this, item.extract);
                }
            });
        } else if (ClipCategory.ADDRESS.equals(key) && item.extract != null) {
            styleAction(action, "地图", ClipCategory.ADDRESS);
            action.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Ui.tapFeedback(view);
                    Ui.map(MainActivity.this, item.extract);
                }
            });
        } else if (ClipCategory.MONEY.equals(key) && item.extract != null) {
            styleAction(action, "复制金额", ClipCategory.MONEY);
            action.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Ui.tapFeedback(view);
                    Ui.copy(MainActivity.this, item.extract);
                    Ui.toast(MainActivity.this, "金额已复制");
                }
            });
        } else if (ClipCategory.NUMBER.equals(key) && item.extract != null) {
            styleAction(action, "复制号码", ClipCategory.NUMBER);
            action.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Ui.tapFeedback(view);
                    Ui.copy(MainActivity.this, item.extract);
                    Ui.toast(MainActivity.this, "号码已复制");
                }
            });
        } else {
            styleAction(action, "复制", ClipCategory.TEXT);
            action.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Ui.tapFeedback(view);
                    Ui.copy(MainActivity.this, item.text);
                    Ui.toast(MainActivity.this, "已复制");
                }
            });
        }
    }

    private void styleAction(TextView action, String label, String category) {
        action.setText(label);
        int color = ClipCategory.color(category);
        action.setTextColor(color);
        action.setBackground(Ui.ripple(Ui.round(ClipCategory.tint(category), 99, this),
                0x14000000, 99, this));
    }
}
