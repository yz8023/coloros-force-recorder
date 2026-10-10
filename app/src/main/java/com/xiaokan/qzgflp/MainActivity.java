package com.xiaokan.qzgflp;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 模块设置页。顶部标题栏 + 全宽底部导航（状态/桌面/安装/系统），
 * 功能按分区着色呈现；配置写入 SharedPreferences("cfg")，
 * 经 HookStateProvider 提供给 hook 侧，行构建器只负责返回视图。
 */
public class MainActivity extends Activity {

    private static final String[] TABS = {"状态", "桌面", "安装", "系统"};
    private static final int[] TAB_ICONS = {
            R.drawable.ic_tab_status, R.drawable.ic_tab_desktop,
            R.drawable.ic_tab_core, R.drawable.ic_tab_system};

    private static final int C_LAYOUT = 0xFF3D8BFF;
    private static final int C_FOLDER = 0xFF34C3FF;
    private static final int C_BADGE = 0xFF9F6CFF;
    private static final int C_LIMIT = 0xFF7C5CFF;
    private static final int C_INSTALLER = 0xFFFF5C7A;
    private static final int C_APPS = 0xFFFF9F43;
    private static final int C_SHOT = 0xFF00B8A9;
    private static final int C_PERM = 0xFF3D8BFF;

    private int PAGE_BG, INK, SUB, LINE, ACCENT, CARD_BG, FIELD_BG, IDLE, NAV_BG, RIPPLE;
    private int HERO_A, HERO_B;
    private boolean dark;

    private LinearLayout pageCol;
    private FrameLayout pageHost;
    private LinearLayout bar;
    private View indicator;
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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PAGE_BG);

        LinearLayout top = topBar();
        root.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        pageHost = new FrameLayout(this);
        scroll.addView(pageHost, new ScrollView.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(bottomNav(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        getWindow().setStatusBarColor(PAGE_BG);
        getWindow().setNavigationBarColor(NAV_BG);
        if (Build.VERSION.SDK_INT >= 23 && !dark) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        bar.post(() -> select(0, false));
    }

    private void applyTheme() {
        PAGE_BG = dark ? 0xFF101014 : 0xFFF3F4F8;
        ACCENT = dark ? 0xFF8E9BFF : 0xFF5B6CFF;
        INK = dark ? 0xFFF2F2F4 : 0xFF191A20;
        SUB = dark ? 0xFF96979F : 0xFF84858E;
        LINE = (dark ? 0x1F : 0x12) << 24;
        CARD_BG = dark ? 0xFF1A1A20 : 0xFFFFFFFF;
        FIELD_BG = dark ? 0xFF232329 : 0xFFF1F2F6;
        IDLE = dark ? 0xFF787980 : 0xFFB0B1BA;
        NAV_BG = (dark ? 0xF5 : 0xFA) << 24 | (dark ? 0x17171C : 0xFFFFFF);
        RIPPLE = (dark ? 0x30 : 0x1E) << 24 | (ACCENT & 0xFFFFFF);
        HERO_A = dark ? 0xFF5F6BFF : 0xFF5B6CFF;
        HERO_B = dark ? 0xFF9C5CFF : 0xFF9C5CFF;
    }

    private LinearLayout topBar() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        top.setPadding(dp(20), dp(14), dp(20), dp(6));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("ColorOS 工具箱");
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(INK);
        col.addView(title, wrap());

        TextView sub = new TextView(this);
        sub.setText("截屏解锁 · 功能破解包  v" + versionName());
        sub.setTextSize(11);
        sub.setTextColor(SUB);
        col.addView(sub, wrap(0, dp(2), 0, 0));

        top.addView(col, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView chip = new TextView(this);
        chip.setText("LSPosed");
        chip.setTextSize(10);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setTextColor(ACCENT);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(20));
        g.setColor((ACCENT & 0xFFFFFF) | 0x1C000000);
        chip.setBackground(g);
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));
        top.addView(chip, wrap());
        return top;
    }

    private View bottomNav() {
        FrameLayout wrap = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadii(new float[]{dp(22), dp(22), dp(22), dp(22), 0, 0, 0, 0});
        bg.setColor(NAV_BG);
        wrap.setBackground(bg);
        wrap.setElevation(dp(14));

        indicator = new View(this);
        GradientDrawable ig = new GradientDrawable();
        ig.setCornerRadius(dp(2));
        ig.setColor(ACCENT);
        indicator.setBackground(ig);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(18), dp(3), Gravity.TOP);
        ip.topMargin = dp(2);
        wrap.addView(indicator, ip);

        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(8), dp(8), dp(8), dp(10));
        wrap.addView(bar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        tabItems = new LinearLayout[TABS.length];
        tabIcons = new ImageView[TABS.length];
        tabLabels = new TextView[TABS.length];
        for (int i = 0; i < TABS.length; i++) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setBackground(ripple(dp(18)));
            final int idx = i;
            item.setOnClickListener(v -> select(idx, true));

            FrameLayout iconWrap = new FrameLayout(this);
            GradientDrawable chipBg = new GradientDrawable();
            chipBg.setCornerRadius(dp(14));
            chipBg.setColor(0x00000000);
            iconWrap.setBackground(chipBg);
            ImageView icon = new ImageView(this);
            icon.setImageResource(TAB_ICONS[i]);
            iconWrap.addView(icon, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
            LinearLayout.LayoutParams iwp = new LinearLayout.LayoutParams(dp(40), dp(32));
            iwp.topMargin = dp(3);
            item.addView(iconWrap, iwp);

            TextView label = new TextView(this);
            label.setText(TABS[i]);
            label.setTextSize(10);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setGravity(Gravity.CENTER);
            item.addView(label, wrap(0, dp(3), 0, 0));

            bar.addView(item, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            tabItems[i] = item;
            tabIcons[i] = (ImageView) iconWrap.getChildAt(0);
            tabLabels[i] = label;
        }
        return wrap;
    }

    private void select(int idx, boolean animate) {
        boolean first = curTab == -1;
        curTab = idx;
        for (int i = 0; i < TABS.length; i++) {
            boolean on = i == idx;
            tabIcons[i].setColorFilter(on ? ACCENT : IDLE);
            tabLabels[i].setTextColor(on ? ACCENT : IDLE);
            View chipWrap = (View) tabIcons[i].getParent();
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(14));
            g.setColor(on ? (ACCENT & 0xFFFFFF) | 0x22000000 : 0x00000000);
            chipWrap.setBackground(g);
        }
        bar.post(() -> {
            if (bar.getWidth() <= 0) return;
            View item = tabItems[idx];
            float target = bar.getX() + item.getX() + item.getWidth() / 2f - dp(9);
            if (animate && !first) {
                ValueAnimator va = ValueAnimator.ofFloat(indicator.getX(), target);
                va.addUpdateListener(a -> indicator.setX((Float) a.getAnimatedValue()));
                va.setInterpolator(new DecelerateInterpolator());
                va.setDuration(280);
                va.start();
            } else {
                indicator.setX(target);
            }
        });
        swapPage(idx, animate && !first);
    }

    private void swapPage(int idx, boolean animate) {
        View page;
        if (idx == 0) page = pageStatus();
        else if (idx == 1) page = pageDesktop();
        else if (idx == 2) page = pageInstall();
        else page = pageSystem();
        pageHost.removeAllViews();
        pageCol = new LinearLayout(this);
        pageCol.setOrientation(LinearLayout.VERTICAL);
        pageCol.setPadding(dp(16), dp(8), dp(16), dp(20));
        pageCol.addView(page, match());
        pageHost.addView(pageCol, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        if (animate) {
            pageCol.setAlpha(0f);
            pageCol.setTranslationY(dp(12));
            pageCol.animate().alpha(1f).translationY(0f).setDuration(220)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    private void refreshPage() {
        if (curTab >= 0) swapPage(curTab, false);
    }

    // ── 页面 ──

    private View pageStatus() {
        LinearLayout col = col();
        col.addView(hero(), match(0, 0, 0, 14));
        col.addView(scopeCard(), match(0, 0, 0, 14));

        LinearLayout steps = card();
        steps.addView(sectionHead(ACCENT, R.drawable.ic_tab_status, "使用步骤", "三步开始使用"));
        addRows(steps, tip("1. 在 LSPosed 中启用本模块并勾选作用域\n"
                + "2. 打开各分区中的功能开关\n"
                + "3. 标注「重启生效」的开关需重启对应应用或手机"));
        col.addView(steps, match(0, 0, 0, 8));
        return col;
    }

    private View hero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{HERO_A, HERO_B});
        g.setCornerRadius(dp(22));
        hero.setBackground(g);
        hero.setPadding(dp(20), dp(18), dp(20), dp(18));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText("模块总开关");
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFFFFFFFF);
        col.addView(t, wrap());
        TextView s = new TextView(this);
        s.setText("关闭后所有作用域的 hook 直接放行");
        s.setTextSize(11);
        s.setTextColor(0xB8FFFFFF);
        col.addView(s, wrap(0, dp(2), 0, 0));
        row.addView(col, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Switch master = new Switch(this);
        master.setChecked(readBool("hook_master"));
        tintWhite(master, readBool("hook_master"));
        master.setOnCheckedChangeListener((b, v) -> {
            writeBool("hook_master", v);
            tintWhite(master, v);
        });
        row.addView(master, wrap());
        hero.addView(row, match());

        LinearLayout stat = new LinearLayout(this);
        stat.setOrientation(LinearLayout.HORIZONTAL);
        stat.setGravity(Gravity.CENTER_VERTICAL);
        ImageView dot = new ImageView(this);
        GradientDrawable dg = new GradientDrawable();
        dg.setShape(GradientDrawable.OVAL);
        dg.setColor(0xFF7DFFB2);
        dot.setBackground(dg);
        stat.addView(dot, new LinearLayout.LayoutParams(dp(7), dp(7)));
        TextView cap = new TextView(this);
        cap.setText("  配置通道实时生效 · 已启用 " + enabledCount() + " / " + FeatureKeys.SWITCHES.length + " 项功能");
        cap.setTextSize(11);
        cap.setTextColor(0xB8FFFFFF);
        stat.addView(cap, wrap());
        hero.addView(stat, match(0, dp(14), 0, 0));
        return hero;
    }

    private int enabledCount() {
        int n = 0;
        for (String[] s : FeatureKeys.SWITCHES) if (readBool(s[0])) n++;
        return n;
    }

    private View scopeCard() {
        LinearLayout card = card();
        card.addView(sectionHead(C_APPS, R.drawable.ic_tab_status,
                "作用域应用", "在 LSPosed 勾选后可在此单独停用"));
        java.util.List<View> rows = new java.util.ArrayList<>();
        String[] scopes = getResources().getStringArray(R.array.xposed_scope);
        for (String pkg : scopes) {
            if ("android".equals(pkg)) {
                rows.add(staticRow("系统框架（android）", "始终启用"));
            } else {
                rows.add(appToggleRow(pkg));
            }
        }
        addRows(card, rows.toArray(new View[0]));
        return card;
    }

    private View pageDesktop() {
        LinearLayout col = col();

        LinearLayout card = card();
        card.addView(sectionHead(C_FOLDER, R.drawable.ic_sec_folder,
                "文件夹与布局", "预览背景与行列数破解"));
        java.util.List<View> rows = new java.util.ArrayList<>();
        rows.add(toggleRow(C_FOLDER, "移除文件夹预览背景",
                "文件夹展开页不再绘制半透明底图", FeatureKeys.FOLDER_BG));
        rows.add(toggleRow(C_LAYOUT, "桌面布局行列数自定义",
                "支持 4-7 列 / 6-10 行组合（重启生效）", FeatureKeys.LAYOUT_CUSTOM));
        if (readBool(FeatureKeys.LAYOUT_CUSTOM)) {
            rows.add(stepperRow("行数", FeatureKeys.LAYOUT_ROWS, 6, 10, C_LAYOUT));
            rows.add(stepperRow("列数", FeatureKeys.LAYOUT_COLS, 4, 7, C_LAYOUT));
        }
        addRows(card, rows.toArray(new View[0]));
        col.addView(card, match(0, 0, 0, 14));

        LinearLayout badges = card();
        badges.addView(sectionHead(C_BADGE, R.drawable.ic_sec_badge,
                "角标管理", "清理桌面角标与徽标"));
        addRows(badges,
                toggleRow(C_BADGE, "移除快捷方式徽标", "不再显示快捷方式小红标", FeatureKeys.BADGE_SHORTCUT),
                toggleRow(C_BADGE, "移除工作空间徽标", "不再显示工作资料标识", FeatureKeys.BADGE_WORK),
                toggleRow(C_BADGE, "移除应用分身徽标", "不再显示应用分身标识", FeatureKeys.BADGE_CLONE));
        col.addView(badges, match(0, 0, 0, 8));
        return col;
    }

    private View pageInstall() {
        LinearLayout col = col();

        LinearLayout limit = card();
        limit.addView(sectionHead(C_LIMIT, R.drawable.ic_sec_shield,
                "安装限制破解", "绕过系统安装校验（重启生效）"));
        addRows(limit,
                toggleRow(C_LIMIT, "强制启用 32 位支持",
                        "允许安装仅含 32 位 so 的应用（重启生效）", FeatureKeys.ENABLE_32BIT),
                toggleRow(C_LIMIT, "移除 ADB 安装确认",
                        "adb install 跳过二次确认弹窗", FeatureKeys.REMOVE_ADB_CONFIRM),
                toggleRow(C_LIMIT, "允许降级安装",
                        "高版本覆盖安装回低版本（重启生效）", FeatureKeys.ALLOW_DOWNGRADE),
                toggleRow(C_LIMIT, "跳过签名 / 病毒验证",
                        "安装时跳过签名比对与验证（重启生效）", FeatureKeys.DISABLE_VERIFY));
        col.addView(limit, match(0, 0, 0, 14));

        LinearLayout installer = card();
        installer.addView(sectionHead(C_INSTALLER, R.drawable.ic_sec_install,
                "安装器增强", "自动化操作与界面清理"));
        addRows(installer,
                toggleRow(C_INSTALLER, "修复安装按钮显示异常", "按钮异常置灰时可修复", FeatureKeys.FIX_INSTALL_BUTTON),
                toggleRow(C_INSTALLER, "跳过 APK 安全扫描", "安装前不再执行病毒扫描", FeatureKeys.SKIP_APK_SCAN),
                toggleRow(C_INSTALLER, "拦截应用详情跳转", "禁止安装页启动 AppDetail 定制扫描", FeatureKeys.DISABLE_APPDETAIL),
                toggleRow(C_INSTALLER, "自动点击安装按钮", "确认页与完成页自动点击", FeatureKeys.AUTO_INSTALL),
                toggleRow(C_INSTALLER, "自动点击卸载按钮", "卸载确认自动完成", FeatureKeys.AUTO_UNINSTALL),
                toggleRow(C_INSTALLER, "移除安装完成广告", "隐藏完成页推荐位", FeatureKeys.REMOVE_INSTALL_ADS),
                toggleRow(C_INSTALLER, "安装页显示包信息", "顶部展示包名与版本", FeatureKeys.SHOW_APK_INFO));
        col.addView(installer, match(0, 0, 0, 14));

        LinearLayout apps = card();
        apps.addView(sectionHead(C_APPS, R.drawable.ic_sec_lock,
                "应用管理", "系统应用控制"));
        addRows(apps,
                toggleRow(C_APPS, "允许卸载 / 停用系统应用", "设置中的应用详情解除限制", FeatureKeys.ALLOW_DISABLE_SYSAPPS));
        col.addView(apps, match(0, 0, 0, 8));
        return col;
    }

    private View pageSystem() {
        LinearLayout col = col();

        LinearLayout shot = card();
        shot.addView(sectionHead(C_SHOT, R.drawable.ic_sec_screenshot,
                "截屏解锁", "隐私页面与延迟限制"));
        addRows(shot,
                toggleRow(C_SHOT, "解除隐私页截屏限制", "长截图包含 FLAG_SECURE 隐私页", FeatureKeys.SCREENSHOT_PRIVACY),
                toggleRow(C_SHOT, "移除截屏长按延迟", "电源+音量下立即截屏", FeatureKeys.SCREENSHOT_NO_DELAY));
        col.addView(shot, match(0, 0, 0, 14));

        LinearLayout perm = card();
        perm.addView(sectionHead(C_PERM, R.drawable.ic_sec_eye,
                "权限与护眼", "受限设置与护眼特性"));
        addRows(perm,
                toggleRow(C_PERM, "自动解锁受限制的设置", "权限管理页自动解除受限应用", FeatureKeys.UNLOCK_RESTRICTED),
                toggleRow(C_PERM, "护眼纸质纹理支持", "护眼模式纸纹特性强制可用（重启生效）", FeatureKeys.EYE_TEXTURE));
        col.addView(perm, match(0, 0, 0, 14));

        LinearLayout quick = card();
        quick.addView(sectionHead(ACCENT, R.drawable.ic_sec_sliders,
                "快捷入口", "直达常用设置页"));
        addRows(quick,
                linkRow("打开开发者选项", () -> startActivity(
                        new Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))),
                linkRow("打开 LSPosed 管理器", () -> {
                    Intent it = getPackageManager().getLaunchIntentForPackage("org.lsposed.manager");
                    if (it != null) startActivity(it);
                    else Toast.makeText(this, "未检测到 LSPosed", Toast.LENGTH_SHORT).show();
                }));
        col.addView(quick, match(0, 0, 0, 8));
        return col;
    }

    // ── 行构建（只返回视图，由调用方添加）──

    private View sectionHead(int color, int iconRes, String title, String desc) {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(10), dp(12), dp(10), dp(10));

        FrameLayout badge = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor((color & 0xFFFFFF) | 0x26000000);
        badge.setBackground(g);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(color);
        badge.addView(icon, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        head.addView(badge, new LinearLayout.LayoutParams(dp(30), dp(30)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(INK);
        col.addView(t, wrap(dp(10), 0, 0, 0));
        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextSize(10);
        d.setTextColor(SUB);
        col.addView(d, wrap(dp(10), dp(1), 0, 0));
        head.addView(col, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return head;
    }

    private View toggleRow(int color, String title, String sub, final String key) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(50));
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(ripple(dp(14)));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(14);
        t.setTextColor(INK);
        textCol.addView(t, wrap());
        if (sub != null && !sub.isEmpty()) {
            TextView s = new TextView(this);
            s.setText(sub);
            s.setTextSize(10);
            s.setTextColor(SUB);
            textCol.addView(s, wrap(0, dp(2), 0, 0));
        }
        row.addView(textCol, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        sw.setChecked(readBool(key));
        tint(sw, color, readBool(key));
        sw.setOnCheckedChangeListener((b, v) -> {
            writeBool(key, v);
            tint(sw, color, v);
            if (FeatureKeys.LAYOUT_CUSTOM.equals(key)) refreshPage();
        });
        row.addView(sw, wrap());
        return row;
    }

    private View stepperRow(String label, final String key, int min, int max, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        g.setColor(FIELD_BG);
        row.setBackground(g);

        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(12);
        t.setTextColor(INK);
        row.addView(t, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final TextView val = new TextView(this);
        val.setText(String.valueOf(cfg.getInt("i_" + key,
                (Integer) FeatureKeys.defaultValue(key))));
        val.setTextSize(14);
        val.setTypeface(Typeface.DEFAULT_BOLD);
        val.setTextColor(color);
        val.setMinWidth(dp(26));
        val.setGravity(Gravity.CENTER);

        row.addView(stepBtn("−", color, () -> {
            int v = cfg.getInt("i_" + key, (Integer) FeatureKeys.defaultValue(key));
            if (v > min) {
                cfg.edit().putInt("i_" + key, v - 1).apply();
                val.setText(String.valueOf(v - 1));
            }
        }), new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout.LayoutParams vp = wrap();
        vp.setMargins(dp(10), 0, dp(10), 0);
        row.addView(val, vp);
        row.addView(stepBtn("+", color, () -> {
            int v = cfg.getInt("i_" + key, (Integer) FeatureKeys.defaultValue(key));
            if (v < max) {
                cfg.edit().putInt("i_" + key, v + 1).apply();
                val.setText(String.valueOf(v + 1));
            }
        }), new LinearLayout.LayoutParams(dp(34), dp(34)));
        return row;
    }

    private TextView stepBtn(String text, int color, Runnable onClick) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(color);
        b.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(CARD_BG);
        b.setBackground(g);
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    private View appToggleRow(final String pkg) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(50));
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(ripple(dp(14)));

        ImageView icon = new ImageView(this);
        try {
            icon.setImageDrawable(getPackageManager().getApplicationIcon(pkg));
        } catch (Throwable ignored) {
        }
        row.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(appLabel(pkg));
        t.setTextSize(13);
        t.setTextColor(INK);
        t.setMaxLines(1);
        textCol.addView(t, wrap());
        TextView p = new TextView(this);
        p.setText(pkg);
        p.setTextSize(9);
        p.setTextColor(SUB);
        p.setMaxLines(1);
        textCol.addView(p, wrap(0, dp(1), 0, 0));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = dp(10);
        row.addView(textCol, tp);

        Switch sw = new Switch(this);
        final String prefKey = "hook_" + pkg;
        sw.setChecked(hookPrefs.getBoolean(prefKey, true));
        tint(sw, ACCENT, hookPrefs.getBoolean(prefKey, true));
        sw.setOnCheckedChangeListener((b, v) -> {
            hookPrefs.edit().putBoolean(prefKey, v).apply();
            tint(sw, ACCENT, v);
        });
        row.addView(sw, wrap());
        return row;
    }

    private View staticRow(String title, String sub) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(50));
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(13);
        t.setTextColor(INK);
        textCol.addView(t, wrap());
        TextView s = new TextView(this);
        s.setText(sub);
        s.setTextSize(10);
        s.setTextColor(SUB);
        textCol.addView(s, wrap(0, dp(1), 0, 0));
        row.addView(textCol, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private View linkRow(String title, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(ripple(dp(14)));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(14);
        t.setTextColor(INK);
        row.addView(t, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView chev = new TextView(this);
        chev.setText("›");
        chev.setTextSize(16);
        chev.setTextColor(SUB);
        row.addView(chev, wrap());
        row.setOnClickListener(v -> onClick.run());
        return row;
    }

    private TextView tip(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setLineSpacing(dp(2), 1f);
        t.setTextColor(SUB);
        t.setPadding(dp(10), dp(10), dp(10), dp(12));
        return t;
    }

    private void addRows(LinearLayout card, View... rows) {
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) {
                View line = new View(this);
                line.setBackgroundColor(LINE);
                card.addView(line, match(12, 0, 12, 0, dp(1)));
            }
            card.addView(rows[i], match());
        }
    }

    // ── 容器 ──

    private LinearLayout col() {
        return new LinearLayout(this);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(20));
        g.setColor(CARD_BG);
        g.setStroke(dp(1), LINE);
        c.setBackground(g);
        c.setElevation(dark ? 0 : dp(1));
        c.setPadding(dp(2), dp(2), dp(2), dp(4));
        return c;
    }

    private RippleDrawable ripple(int radiusDp) {
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(dp(radiusDp));
        mask.setColor(0xFFFFFFFF);
        return new RippleDrawable(ColorStateList.valueOf(RIPPLE), null, mask);
    }

    private void tint(Switch sw, int color, boolean on) {
        if (sw.getThumbDrawable() != null) {
            sw.getThumbDrawable().setTintList(ColorStateList.valueOf(on ? color : 0xFFD4D4DC));
        }
        if (sw.getTrackDrawable() != null) {
            sw.getTrackDrawable().setTintList(ColorStateList.valueOf(
                    (on ? 0x4D : 0x26) << 24 | (color & 0xFFFFFF)));
        }
    }

    private void tintWhite(Switch sw, boolean on) {
        if (sw.getThumbDrawable() != null) {
            sw.getThumbDrawable().setTintList(ColorStateList.valueOf(
                    on ? 0xFFFFFFFF : 0x99FFFFFF));
        }
        if (sw.getTrackDrawable() != null) {
            sw.getTrackDrawable().setTintList(ColorStateList.valueOf(
                    on ? 0x66FFFFFF : 0x33FFFFFF));
        }
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            PackageInfo info = pm.getPackageInfo(pkg, 0);
            CharSequence name = pm.getApplicationLabel(info.applicationInfo);
            if (name != null) return name.toString();
        } catch (Throwable ignored) {
        }
        return pkg;
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

    private String versionName() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.versionName;
        } catch (Throwable t) {
            return "?";
        }
    }

    // ── 布局参数 ──

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrap(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams match(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private LinearLayout.LayoutParams match(int l, int t, int r, int b, int h) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
