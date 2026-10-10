package com.xiaokan.qzgflp;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * 设置主页 —— 悬浮磨砂底栏 + 4 Tab（状态 / 桌面 / 核心破解 / 系统）。
 * 纯原生 View 实现，零第三方依赖；配置写入 SharedPreferences("cfg")，
 * 经 HookStateProvider 提供给 hook 侧。
 */
public class MainActivity extends Activity {

    private static final String[] TAB_NAMES = {"状态", "桌面", "核心破解", "系统"};
    private static final int[] TAB_ICONS = {
            R.drawable.ic_tab_status, R.drawable.ic_tab_desktop,
            R.drawable.ic_tab_core, R.drawable.ic_tab_system};

    private int PAGE_BG, ACCENT, INK, SUB, BAR_BG, CARD_BG, FIELD_BG, IDLE_TINT, PILL_TAB, RIPPLE;
    private boolean dark;

    private FrameLayout pageHost;
    private LinearLayout bar;
    private View pill;
    private LinearLayout[] tabItems;
    private ImageView[] tabIcons;
    private TextView[] tabLabels;
    private int curTab = -1;

    private SharedPreferences cfg;
    private SharedPreferences hookPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int mask = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        dark = mask == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        applyTheme();

        cfg = getSharedPreferences("cfg", Context.MODE_PRIVATE);
        hookPrefs = getSharedPreferences("settings", Context.MODE_PRIVATE);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(pageBg());

        Blobs blobs = new Blobs(this);
        root.addView(blobs, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(420), Gravity.TOP));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        pageHost = new FrameLayout(this);
        scroll.addView(pageHost, new ScrollView.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        sp.setMargins(0, 0, 0, dp(104));
        root.addView(scroll, sp);

        buildBar(root);
        setContentView(root);
        getWindow().setStatusBarColor(PAGE_BG);
        if (Build.VERSION.SDK_INT >= 23 && !dark) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        bar.post(() -> select(0, false));
    }

    private void applyTheme() {
        PAGE_BG = dark ? 0xFF121212 : 0xFFF6F5F2;
        ACCENT = dark ? 0xFFA99BFF : 0xFF6C5CE7;
        INK = dark ? 0xFFF0F0F0 : 0xFF1A1A1A;
        SUB = dark ? 0xFF9A9A9A : 0xFF8A8A8A;
        BAR_BG = (dark ? 0xF2 : 0xF2) << 24 | (dark ? 0x1E1E1E : 0xFFFFFF);
        CARD_BG = dark ? 0xFF1E1E1E : 0xFFFFFFFF;
        FIELD_BG = dark ? 0xFF262626 : 0xFFF0EFEB;
        IDLE_TINT = dark ? 0xFF8A8A8A : 0xFFB4B4B4;
        PILL_TAB = dark ? 0xFF2C2C2C : 0xFFFFFFFF;
        RIPPLE = (dark ? 0x33 : 0x1F) << 24 | (ACCENT & 0xFFFFFF);
    }

    private GradientDrawable pageBg() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{dark ? 0xFF16161E : 0xFFF2F0FA, PAGE_BG});
        return g;
    }

    // ── 底栏 ──

    private void buildBar(FrameLayout root) {
        FrameLayout wrap = new FrameLayout(this);
        FrameLayout.LayoutParams wp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, dp(64), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        wp.setMargins(dp(16), 0, dp(16), dp(20));
        root.addView(wrap, wp);

        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(32));
        bg.setColor(BAR_BG);
        bg.setStroke(dp(1), (dark ? 0x14 : 0x0A) << 24);
        bar.setBackground(bg);
        bar.setElevation(dp(10));
        int pad = dp(6);
        bar.setPadding(pad, pad, pad, pad);
        wrap.addView(bar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        pill = new View(this);
        GradientDrawable pg = new GradientDrawable();
        pg.setCornerRadius(dp(26));
        pg.setColor(PILL_TAB);
        pill.setBackground(pg);
        pill.setAlpha(0f);
        bar.addView(pill, new FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT));

        tabItems = new LinearLayout[TAB_NAMES.length];
        tabIcons = new ImageView[TAB_NAMES.length];
        tabLabels = new TextView[TAB_NAMES.length];
        for (int i = 0; i < TAB_NAMES.length; i++) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setBackground(ripple());
            final int idx = i;
            item.setOnClickListener(v -> select(idx, true));
            ImageView icon = new ImageView(this);
            icon.setImageResource(TAB_ICONS[i]);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(22), dp(22));
            ip.topMargin = dp(7);
            item.addView(icon, ip);
            TextView label = new TextView(this);
            label.setText(TAB_NAMES[i]);
            label.setTextSize(10);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(3);
            lp.bottomMargin = dp(7);
            item.addView(label, lp);

            LinearLayout.LayoutParams itemP = new LinearLayout.LayoutParams(
                    0, FrameLayout.LayoutParams.MATCH_PARENT, 1f);
            itemP.setMargins(dp(3), 0, dp(3), 0);
            bar.addView(item, itemP);
            tabItems[i] = item;
            tabIcons[i] = icon;
            tabLabels[i] = label;
        }
    }

    private RippleDrawable ripple() {
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(dp(26));
        mask.setColor(0xFFFFFFFF);
        return new RippleDrawable(ColorStateList.valueOf(RIPPLE), null, mask);
    }

    private void select(int idx, boolean animate) {
        if (idx == curTab && animate) return;
        curTab = idx;
        int w = bar.getWidth() / TAB_NAMES.length;
        float target = idx * w + (w - dp(52)) / 2f;
        if (pill.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams pp = (FrameLayout.LayoutParams) pill.getLayoutParams();
            pp.width = dp(52);
            pill.setLayoutParams(pp);
        }
        pill.setAlpha(1f);
        if (animate) {
            ValueAnimator va = ValueAnimator.ofFloat(pill.getX() == 0 ? target : pill.getX(), target);
            va.addUpdateListener(a -> pill.setX((Float) a.getAnimatedValue()));
            va.setInterpolator(new OvershootInterpolator(0.8f));
            va.setDuration(360);
            va.start();
            for (int i = 0; i < TAB_NAMES.length; i++) {
                if (i != idx) continue;
                final int fi = i;
                tabIcons[i].animate().scaleX(1.25f).scaleY(1.25f).setDuration(140)
                        .withEndAction(() -> tabIcons[fi].animate().scaleX(1f).scaleY(1f)
                                .setInterpolator(new OvershootInterpolator(3f)).setDuration(260).start())
                        .start();
            }
        } else {
            pill.setX(target);
        }
        for (int i = 0; i < TAB_NAMES.length; i++) {
            boolean on = i == idx;
            tabIcons[i].setColorFilter(on ? ACCENT : IDLE_TINT);
            tabLabels[i].setTextColor(on ? ACCENT : IDLE_TINT);
        }
        swapPage(idx, animate);
    }

    private void swapPage(int idx, boolean animate) {
        View page;
        switch (idx) {
            case 0: page = pageStatus(); break;
            case 1: page = pageDesktop(); break;
            case 2: page = pageCore(); break;
            default: page = pageSystem(); break;
        }
        pageHost.removeAllViews();
        pageHost.addView(page, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        if (animate) {
            page.setAlpha(0f);
            page.setTranslationY(dp(14));
            page.animate().alpha(1f).translationY(0f).setDuration(240)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    // ── 页面 ──

    private LinearLayout column() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        col.setPadding(p, dp(20), p, dp(8));
        return col;
    }

    private View pageStatus() {
        LinearLayout col = column();
        TextView big = new TextView(this);
        big.setText(getString(R.string.app_name));
        big.setTextSize(24);
        big.setTypeface(Typeface.DEFAULT_BOLD);
        big.setTextColor(INK);
        col.addView(big, match());

        String ver = "v" + BuildConfigVersion();
        TextView verView = new TextView(this);
        verView.setText("ColorOS16 截屏解锁 + LuckyTool 功能包 " + ver);
        verView.setTextSize(12);
        verView.setTextColor(SUB);
        col.addView(verView, matchWrap(0, dp(4), 0, dp(12)));

        LinearLayout card = card();
        card.addView(header("模块状态"));
        Switch master = switchRow(card, "总开关（所有作用域）",
                "关闭后 hook 全部放行", "hook_master");
        TextView tips = tip("开关与功能配置经 ContentProvider 实时下发，改动即生效；"
                + "标注“需重启”的开关在重启对应进程后生效。");
        card.addView(tips, matchWrap(0, dp(8), 0, 0));
        col.addView(card, matchWrap(0, 0, 0, dp(12)));

        LinearLayout scopeCard = card();
        scopeCard.addView(header("作用域应用"));
        String[] scopes = getResources().getStringArray(R.array.xposed_scope);
        for (String s : scopes) {
            if ("android".equals(s)) continue;
            scopeCard.addView(appToggleRow(s));
        }
        col.addView(scopeCard, matchWrap(0, 0, 0, dp(12)));

        LinearLayout steps = card();
        steps.addView(header("使用步骤"));
        steps.addView(tip("1. 在 LSPosed 中启用本模块并勾选作用域应用\n"
                + "2. 打开各页功能开关\n"
                + "3. 重启作用域应用或 system_server 使“需重启”项生效"));
        col.addView(steps, matchWrap(0, 0, 0, dp(8)));
        return col;
    }

    private View pageDesktop() {
        LinearLayout col = column();
        col.addView(headerBig("桌面"));
        LinearLayout card = card();
        for (String key : FeatureKeys.groupSwitches("桌面")) {
            if (FeatureKeys.LAYOUT_CUSTOM.equals(key)) {
                card.addView(switchRow(card, FeatureKeys.title(key),
                        "支持 4-7 列 / 6-10 行布局组合", key));
                if (cfg.getBoolean("b_" + key, false) || cfg.getBoolean(key, false)) {
                    card.addView(stepperRow("行数", FeatureKeys.LAYOUT_ROWS, 6, 10));
                    card.addView(stepperRow("列数", FeatureKeys.LAYOUT_COLS, 4, 7));
                }
            } else {
                card.addView(switchRow(card, FeatureKeys.title(key), subOf(key), key));
            }
        }
        col.addView(card, matchWrap(0, 0, 0, dp(8)));
        col.addView(tip("布局行列数在系统启动器“设置-布局”中生效，需重启桌面刷新配置缓存。"), matchWrap(0, dp(4), 0, 0));
        return col;
    }

    private View pageCore() {
        LinearLayout col = column();
        col.addView(headerBig("核心破解"));
        LinearLayout card = card();
        for (String key : FeatureKeys.groupSwitches("核心破解")) {
            card.addView(switchRow(card, FeatureKeys.title(key), subOf(key), key));
        }
        col.addView(card, matchWrap(0, 0, 0, dp(8)));
        col.addView(tip("签名/降级相关开关依赖 system_server，修改后需重启手机。"
                + "自动点击与广告移除在安装器界面即时生效。"), matchWrap(0, dp(4), 0, 0));
        return col;
    }

    private View pageSystem() {
        LinearLayout col = column();
        col.addView(headerBig("截屏"));
        LinearLayout sc = card();
        for (String key : FeatureKeys.groupSwitches("截屏")) {
            sc.addView(switchRow(sc, FeatureKeys.title(key), subOf(key), key));
        }
        col.addView(sc, matchWrap(0, 0, 0, dp(12)));

        col.addView(headerBig("系统"));
        LinearLayout card = card();
        for (String key : FeatureKeys.groupSwitches("系统")) {
            card.addView(switchRow(card, FeatureKeys.title(key), subOf(key), key));
        }
        col.addView(card, matchWrap(0, 0, 0, dp(12)));

        LinearLayout quick = card();
        quick.addView(header("快捷入口"));
        quick.addView(linkRow("打开开发者选项", () -> startActivity(
                new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))));
        col.addView(quick, matchWrap(0, 0, 0, dp(8)));
        return col;
    }

    private String subOf(String key) {
        switch (key) {
            case FeatureKeys.FOLDER_BG: return "移除文件夹展开的半透明底图";
            case FeatureKeys.BADGE_SHORTCUT: return "不再显示快捷方式小红标";
            case FeatureKeys.BADGE_WORK: return "不再显示工作空间徽标";
            case FeatureKeys.BADGE_CLONE: return "不再显示应用分身徽标";
            case FeatureKeys.ENABLE_32BIT: return "允许安装 32 位应用";
            case FeatureKeys.REMOVE_ADB_CONFIRM: return "ADB 安装时跳过确认弹窗";
            case FeatureKeys.ALLOW_DOWNGRADE: return "允许高版本覆盖安装回低版本";
            case FeatureKeys.DISABLE_VERIFY: return "跳过安装签名校验与病毒扫描验证";
            case FeatureKeys.FIX_INSTALL_BUTTON: return "修复安装按钮异常置灰";
            case FeatureKeys.SKIP_APK_SCAN: return "跳过安装前病毒扫描";
            case FeatureKeys.DISABLE_APPDETAIL: return "拦截“应用详情”跳转（安装页）";
            case FeatureKeys.AUTO_INSTALL: return "安装/完成页自动点击按钮";
            case FeatureKeys.AUTO_UNINSTALL: return "卸载确认自动点击";
            case FeatureKeys.REMOVE_INSTALL_ADS: return "隐藏安装完成页推荐位";
            case FeatureKeys.SHOW_APK_INFO: return "安装页展示包名/版本等信息";
            case FeatureKeys.ALLOW_DISABLE_SYSAPPS: return "设置中允许停用系统应用";
            case FeatureKeys.SCREENSHOT_PRIVACY: return "长截图包含 FLAG_SECURE 隐私页";
            case FeatureKeys.SCREENSHOT_NO_DELAY: return "长按电源+音量下立即截屏";
            case FeatureKeys.UNLOCK_RESTRICTED: return "权限管理页自动解除受限应用";
            case FeatureKeys.EYE_TEXTURE: return "护眼纸纹特性强制可用";
            default: return "";
        }
    }

    // ── 控件 ──

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(18));
        g.setColor(CARD_BG);
        int p = dp(14);
        c.setPadding(p, p, p, dp(4));
        c.setBackground(g);
        c.setElevation(dp(1));
        return c;
    }

    private TextView headerBig(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(20);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(INK);
        return t;
    }

    private TextView header(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(ACCENT);
        t.setAllCaps(false);
        return t;
    }

    private TextView tip(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setLineSpacing(dp(2), 1f);
        t.setTextColor(SUB);
        return t;
    }

    private Switch switchRow(LinearLayout host, String title, String sub, String key) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(ripple());
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(15);
        t.setTextColor(INK);
        textCol.addView(t, matchWrap(0, 0, 0, 0));
        if (sub != null && !sub.isEmpty()) {
            TextView s = new TextView(this);
            s.setText(sub);
            s.setTextSize(11);
            s.setTextColor(SUB);
            textCol.addView(s, matchWrap(0, dp(2), 0, 0));
        }
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tp.rightMargin = dp(8);
        row.addView(textCol, tp);

        Switch sw = new Switch(this);
        sw.setChecked(readBool(key));
        sw.getThumbDrawable().setTintList(ColorStateList.valueOf(readBool(key) ? ACCENT : 0xFFCFCFCF));
        sw.getTrackDrawable().setTintList(ColorStateList.valueOf((readBool(key) ? 0x55 : 0x22) << 24 | (ACCENT & 0xFFFFFF)));
        sw.setOnCheckedChangeListener((b, v) -> {
            writeBool(key, v);
            sw.getThumbDrawable().setTintList(ColorStateList.valueOf(v ? ACCENT : 0xFFCFCFCF));
            sw.getTrackDrawable().setTintList(ColorStateList.valueOf((v ? 0x55 : 0x22) << 24 | (ACCENT & 0xFFFFFF)));
        });
        row.addView(sw, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(10);
        rp.bottomMargin = dp(6);
        host.addView(row, rp);
        return sw;
    }

    private View stepperRow(String label, final String key, int min, int max) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        g.setColor(FIELD_BG);
        row.setBackground(g);

        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(13);
        t.setTextColor(INK);
        row.addView(t, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final TextView val = new TextView(this);
        val.setText(String.valueOf(cfg.getInt("i_" + key,
                (Integer) FeatureKeys.defaultValue(key))));
        val.setTextSize(15);
        val.setTypeface(Typeface.DEFAULT_BOLD);
        val.setTextColor(ACCENT);
        val.setMinWidth(dp(28));
        val.setGravity(Gravity.CENTER);

        row.addView(minusBtn(key, min, val), new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        vp.setMargins(dp(8), 0, dp(8), 0);
        row.addView(val, vp);
        row.addView(plusBtn(key, max, val), new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(6);
        rp.bottomMargin = dp(8);
        return row;
    }

    private TextView minusBtn(final String key, final int min, final TextView val) {
        return btn("−", () -> {
            int v = cfg.getInt("i_" + key, (Integer) FeatureKeys.defaultValue(key));
            if (v > min) {
                cfg.edit().putInt("i_" + key, v - 1).apply();
                val.setText(String.valueOf(v - 1));
            }
        });
    }

    private TextView plusBtn(final String key, final int max, final TextView val) {
        return btn("+", () -> {
            int v = cfg.getInt("i_" + key, (Integer) FeatureKeys.defaultValue(key));
            if (v < max) {
                cfg.edit().putInt("i_" + key, v + 1).apply();
                val.setText(String.valueOf(v + 1));
            }
        });
    }

    private TextView btn(String text, Runnable onClick) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(18);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(ACCENT);
        b.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(19));
        g.setColor((ACCENT & 0xFFFFFF) | 0x18000000);
        b.setBackground(g);
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    private View appToggleRow(final String pkg) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(4));
        String label = pkg;
        try {
            PackageManager pm = getPackageManager();
            PackageInfo info = pm.getPackageInfo(pkg, 0);
            CharSequence name = pm.getApplicationLabel(info.applicationInfo);
            if (name != null) label = name.toString() + " (" + pkg + ")";
        } catch (Throwable ignored) {
        }
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(13);
        t.setTextColor(pkg.contains("launcher") || pkg.contains("installer")
                || pkg.contains("settings") || pkg.contains("securitypermission")
                || pkg.contains("eyeprotect") ? INK : SUB);
        t.setMaxLines(1);
        row.addView(t, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = new Switch(this);
        final String prefKey = "hook_" + pkg;
        sw.setChecked(hookPrefs.getBoolean(prefKey, true));
        sw.setOnCheckedChangeListener((b, v) ->
                hookPrefs.edit().putBoolean(prefKey, v).apply());
        row.addView(sw, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private View linkRow(String title, Runnable onClick) {
        TextView t = new TextView(this);
        t.setText(title + "  ›");
        t.setTextSize(14);
        t.setTextColor(ACCENT);
        t.setPadding(dp(4), dp(10), dp(4), dp(10));
        t.setOnClickListener(v -> onClick.run());
        return t;
    }

    // ── 配置读写 ──

    private boolean readBool(String key) {
        if ("hook_master".equals(key)) {
            return hookPrefs.getBoolean("hook_master", true);
        }
        return cfg.getBoolean(key, false);
    }

    private void writeBool(String key, boolean v) {
        if ("hook_master".equals(key)) {
            hookPrefs.edit().putBoolean("hook_master", v).apply();
            cfg.edit().putBoolean("b_hook_master", v).apply();
            return;
        }
        cfg.edit().putBoolean(key, v).putBoolean("b_" + key, v).apply();
    }

    private String BuildConfigVersion() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.versionName + " (" + pi.versionCode + ")";
        } catch (Throwable t) {
            return "?";
        }
    }

    // ── 光斑背景 ──

    private static final class Blobs extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Blobs(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            blob(canvas, getWidth() * 0.85f, dp2(80), dp2(150),
                    ((MainActivity) getContext()).ACCENT & 0xFFFFFF, 0x26);
            blob(canvas, getWidth() * 0.12f, dp2(220), dp2(170),
                    0x00B894, 0x20);
            blob(canvas, getWidth() * 0.55f, dp2(60), dp2(130),
                    0xFD79A8, 0x1E);
        }

        private void blob(Canvas canvas, float cx, float cy, float r, int rgb, int alpha) {
            paint.setShader(new RadialGradient(cx, cy, r,
                    new int[]{alpha << 24 | rgb, 0x00000000}, null, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r, paint);
        }

        private float dp2(int v) {
            return v * getResources().getDisplayMetrics().density;
        }
    }

    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrap(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
