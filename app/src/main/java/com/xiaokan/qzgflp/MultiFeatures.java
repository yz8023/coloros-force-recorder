package com.xiaokan.qzgflp;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Button;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * LuckyTool 功能移植 + CorePatch 核心破解（libxposed API 102 版）。
 * 全部开关经 Cfg 读取导出 ContentProvider 配置，未开启的 hook 直接放行。
 */
final class MultiFeatures {
    private static final String TAG = "ForceCapture";

    private static final Set<String> EYE_FEATURES = new HashSet<>(Arrays.asList(
            "oplus.software.display.smart_color_temperature_rhythm_health_support",
            "oplus.software.display.eyeprotect_paper_texture_support"));

    private final XposedModule module;

    MultiFeatures(XposedModule module) {
        this.module = module;
    }

    private void logOk(String what, int n) {
        module.log(Log.INFO, TAG, what + " hooked x" + n);
    }

    private List<Method> ms(Class<?> c, String... names) {
        List<String> list = Arrays.asList(names);
        List<Method> out = new ArrayList<>();
        for (Method m : c.getDeclaredMethods()) {
            if (list.contains(m.getName())) out.add(m);
        }
        return out;
    }

    private int rep(Class<?> c, final String key, final Object val, String... names) {
        int n = 0;
        for (Method m : ms(c, names)) {
            module.hook(m).intercept(chain -> Cfg.bool(key) ? val : chain.proceed());
            n++;
        }
        return n;
    }

    private int repTrue(ClassLoader cl, String className, final String key, String name) {
        try {
            return rep(cl.loadClass(className), key, Boolean.TRUE, name);
        } catch (Throwable t) {
            return 0;
        }
    }

    private int repFalse(ClassLoader cl, String className, final String key, String name) {
        try {
            return rep(cl.loadClass(className), key, Boolean.FALSE, name);
        } catch (Throwable t) {
            return 0;
        }
    }

    private int repLong(ClassLoader cl, String className, final String key, String name, final long val) {
        try {
            return rep(cl.loadClass(className), key, val, name);
        } catch (Throwable t) {
            return 0;
        }
    }


    static boolean eyeFeature(Object o) {
        return EYE_FEATURES.contains(String.valueOf(o));
    }

    // ── system_server ──

    void onSystemServer(ClassLoader cl) {
        hook32Bit(cl);
        hookAdbConfirm(cl);
        hookScreenshotPrivacy(cl);
        hookScreenshotDelay(cl);
        hookDowngrade(cl);
        hookVerify(cl);
        hookFeatureConfig(cl, null);
    }

    private void hook32Bit(ClassLoader cl) {
        int n = repTrue(cl, "com.android.server.pm.OplusPackageManagerHelper",
                FeatureKeys.ENABLE_32BIT, "allowInstall32BitApp");
        logOk("32bit", n);
    }

    private void hookAdbConfirm(ClassLoader cl) {
        int n = 0;
        n += repFalse(cl, "com.android.server.pm.OplusPackageInstallInterceptManager",
                FeatureKeys.REMOVE_ADB_CONFIRM, "allowInterceptAdbInstallInInstallStage");
        n += repFalse(cl, "com.android.server.pm.ColorPackageInstallInterceptManager",
                FeatureKeys.REMOVE_ADB_CONFIRM, "allowInterceptAdbInstallInInstallStage");
        logOk("adb-confirm", n);
    }

    private void hookScreenshotPrivacy(ClassLoader cl) {
        int n = repFalse(cl, "com.android.server.wm.OplusLongshotMainWindow",
                FeatureKeys.SCREENSHOT_PRIVACY, "hasSecure");
        logOk("screenshot-privacy", n);
    }

    private void hookScreenshotDelay(ClassLoader cl) {
        int n = repLong(cl, "com.android.server.policy.PhoneWindowManager",
                FeatureKeys.SCREENSHOT_NO_DELAY, "getScreenshotChordLongPressDelay", 0L);
        logOk("screenshot-delay", n);
    }

    private void hookDowngrade(ClassLoader cl) {
        try {
            Class<?> pms = cl.loadClass("com.android.server.pm.PackageManagerService");
            int n = 0;
            for (Method m : ms(pms, "checkDowngrade")) {
                final boolean boolRet = m.getReturnType() == boolean.class;
                module.hook(m).intercept(chain -> {
                    if (!Cfg.bool(FeatureKeys.ALLOW_DOWNGRADE)) return chain.proceed();
                    return boolRet ? Boolean.FALSE : null;
                });
                n++;
            }
            logOk("downgrade", n);
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "downgrade hook failed", t);
        }
    }

    private void hookVerify(ClassLoader cl) {
        try {
            Class<?> vs = cl.loadClass("com.android.server.pm.VerifyingSession");
            Field flags = null;
            try {
                flags = vs.getDeclaredField("mInstallFlags");
                flags.setAccessible(true);
            } catch (Throwable ignored) {
            }
            final Field mInstallFlags = flags;
            int n = 0;
            if (mInstallFlags != null) {
                for (Method m : ms(vs, "handleStartVerify")) {
                    module.hook(m).intercept(chain -> {
                        if (Cfg.bool(FeatureKeys.DISABLE_VERIFY)) {
                            Object self = chain.getThisObject();
                            if (self != null) {
                                int f = mInstallFlags.getInt(self);
                                mInstallFlags.setInt(self, f | 0x00080000);
                            }
                        }
                        return chain.proceed();
                    });
                    n++;
                }
            }
            n += repFalse(cl, "com.android.server.pm.VerifyingSession",
                    FeatureKeys.DISABLE_VERIFY, "isAdbVerificationEnabled");
            n += repFalse(cl, "com.android.server.pm.PackageManagerService",
                    FeatureKeys.DISABLE_VERIFY, "isVerificationEnabled");
            try {
                Class<?> iph = cl.loadClass("com.android.server.pm.InstallPackageHelper");
                for (Method m : ms(iph, "doesSignatureMatchForPermissions")) {
                    if (m.getReturnType() != boolean.class) continue;
                    module.hook(m).intercept(chain -> {
                        Object r = chain.proceed();
                        if (Cfg.bool(FeatureKeys.DISABLE_VERIFY)
                                && Boolean.FALSE.equals(r)) return Boolean.TRUE;
                        return r;
                    });
                    n++;
                }
            } catch (Throwable ignored) {
            }
            logOk("verify", n);
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "verify hook failed", t);
        }
    }

    /** OplusFeatureConfigManager.hasFeature：护眼纸纹等特性强制开启 */
    private void hookFeatureConfig(ClassLoader cl, final String key) {
        try {
            Class<?> fc = cl.loadClass("com.oplus.content.OplusFeatureConfigManager");
            for (Method m : ms(fc, "hasFeature")) {
                module.hook(m).intercept(chain -> {
                    String keyToUse = key != null ? key : FeatureKeys.EYE_TEXTURE;
                    if (Cfg.bool(keyToUse)) {
                        java.util.List<Object> args = chain.getArgs();
                        if (!args.isEmpty() && EYE_FEATURES.contains(String.valueOf(args.get(0)))) {
                            return Boolean.TRUE;
                        }
                    }
                    return chain.proceed();
                });
            }
        } catch (Throwable ignored) {
        }
    }

    // ── 应用内分发 ──

    void onPackage(String pkg, ClassLoader cl) {
        try {
            if (pkg.startsWith("com.android.launcher")) {
                hookFolderBg(cl);
                hookLauncherLayout(cl);
                hookBadges(cl);
            } else if (pkg.startsWith("com.android.packageinstaller")) {
                hookInstaller(cl);
            } else if (pkg.equals("com.android.settings")) {
                int n = 0;
                try {
                    Class<?> c = cl.loadClass(
                            "com.oplus.settings.adaptor.AppButtonsPreferenceControllerAdaptor");
                    for (Method m : ms(c, "setUninstallButtonEnabled")) {
                        module.hook(m).intercept(chain -> {
                            if (Cfg.bool(FeatureKeys.ALLOW_DISABLE_SYSAPPS)) {
                                java.util.List<Object> args = chain.getArgs();
                                if (!args.isEmpty()) args.set(0, Boolean.TRUE);
                                return chain.proceed(args.toArray());
                            }
                            return chain.proceed();
                        });
                        n++;
                    }
                } catch (Throwable ignored) {
                }
                logOk("disable-sysapps", n);
            } else if (pkg.equals("com.oplus.securitypermission")) {
                hookPermissionUnlock(cl);
            } else if (pkg.equals("com.oplus.eyeprotect")) {
                hookFeatureConfig(cl, null);
            }
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "multi-features failed in " + pkg, t);
        }
    }

    // ── 桌面 ──

    private void hookFolderBg(ClassLoader cl) {
        try {
            Class<?> bg = cl.loadClass("com.android.launcher3.folder.OplusPreviewBackground");
            final Field f = findField(bg, "mBgDrawable");
            int n = 0;
            for (Method m : ms(bg, "setBackground", "setup")) {
                module.hook(m).intercept(chain -> {
                    if (Cfg.bool(FeatureKeys.FOLDER_BG) && f != null) {
                        Object self = chain.getThisObject();
                        if (self != null) f.set(self, null);
                    }
                    return chain.proceed();
                });
                n++;
            }
            for (Method m : ms(bg, "drawBackground")) {
                module.hook(m).intercept(chain ->
                        Cfg.bool(FeatureKeys.FOLDER_BG) ? null : chain.proceed());
                n++;
            }
            logOk("folder-bg", n);
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "folder-bg failed", t);
        }
        try {
            Class<?> am = cl.loadClass("com.android.launcher3.folder.OplusFolderAnimationManager");
            for (Method m : ms(am, "getFolderBackgroundAnimator")) {
                module.hook(m).intercept(chain ->
                        Cfg.bool(FeatureKeys.FOLDER_BG) ? null : chain.proceed());
            }
        } catch (Throwable ignored) {
        }
    }

    private void hookLauncherLayout(ClassLoader cl) {
        try {
            Class<?> ui = cl.loadClass("com.android.launcher.UiConfig");
            rep(ui, FeatureKeys.LAYOUT_CUSTOM, Boolean.FALSE, "isSupportLayout");
            int n = 1;
            if (Build.VERSION.SDK_INT >= 37) {
                for (Method m : ms(ui, "getSupportLayout")) {
                    module.hook(m).intercept(chain -> {
                        if (!Cfg.bool(FeatureKeys.LAYOUT_CUSTOM)) return chain.proceed();
                        int maxCols = Cfg.intv(FeatureKeys.LAYOUT_COLS);
                        int maxRows = Cfg.intv(FeatureKeys.LAYOUT_ROWS);
                        ArrayList<Pair<Integer, Pair<Integer, Integer>>> out = new ArrayList<>();
                        for (int col = 4; col <= maxCols; col++) {
                            for (int row = 6; row <= maxRows; row++) {
                                out.add(new Pair<>(col, new Pair<>(row, row + 1)));
                            }
                        }
                        return out;
                    });
                    n++;
                }
                try {
                    Class<?> ifu = cl.loadClass("com.android.launcher.iconfallen.IconFallenUtils");
                    for (Method m : ms(ifu, "getLogicCellX")) {
                        module.hook(m).intercept(chain -> {
                            Object r = chain.proceed();
                            if (Cfg.bool(FeatureKeys.LAYOUT_CUSTOM)
                                    && r instanceof Integer && (Integer) r > 4) return 4;
                            return r;
                        });
                        n++;
                    }
                } catch (Throwable ignored) {
                }
            } else {
                try {
                    Class<?> tb = cl.loadClass(
                            "com.android.launcher.togglebar.adapter.ToggleBarLayoutAdapter");
                    final Field cols = findField(tb, "MIN_MAX_COLUMN");
                    final Field rows = findField(tb, "MIN_MAX_ROW");
                    for (Method m : ms(tb, "initToggleBarLayoutConfigs")) {
                        module.hook(m).intercept(chain -> {
                            if (Cfg.bool(FeatureKeys.LAYOUT_CUSTOM)) {
                                Object self = chain.getThisObject();
                                if (self != null) {
                                    if (cols != null) setArr(cols, self, Cfg.intv(FeatureKeys.LAYOUT_COLS));
                                    if (rows != null) setArr(rows, self, Cfg.intv(FeatureKeys.LAYOUT_ROWS));
                                }
                            }
                            return chain.proceed();
                        });
                        n++;
                    }
                } catch (Throwable ignored) {
                }
            }
            logOk("layout", n);
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "layout failed", t);
        }
    }

    private static void setArr(Field f, Object self, int v) {
        try {
            int[] arr = (int[]) f.get(self);
            if (arr != null && arr.length > 1) arr[1] = v;
        } catch (Throwable ignored) {
        }
    }

    private void hookBadges(ClassLoader cl) {
        try {
            Class<?> bi = cl.loadClass("com.android.launcher3.icons.BitmapInfo");
            final Field flagsF = findField(bi, "flags");
            final Field badgeF = findField(bi, "badgeInfo");
            int n = 0;
            for (Method m : ms(bi, "applyFlags")) {
                module.hook(m).intercept(chain -> {
                    if (!Cfg.bool(FeatureKeys.BADGE_SHORTCUT)
                            && !Cfg.bool(FeatureKeys.BADGE_WORK)
                            && !Cfg.bool(FeatureKeys.BADGE_CLONE)) return chain.proceed();
                    Object self = chain.getThisObject();
                    if (self == null || flagsF == null) return chain.proceed();
                    int flag = flagsF.getInt(self);
                    java.util.List<Object> args = chain.getArgs();
                    int creation = 0;
                    for (Object a : args) if (a instanceof Integer) creation = (Integer) a;
                    if ((creation & 2) != 0) return chain.proceed();
                    Object badge = badgeF == null ? null : badgeF.get(self);
                    if (badge != null && Cfg.bool(FeatureKeys.BADGE_SHORTCUT)) return null;
                    if ((flag & 4) == 0) {
                        if ((flag & 1) != 0 && Cfg.bool(FeatureKeys.BADGE_WORK)) return null;
                    } else if (Cfg.bool(FeatureKeys.BADGE_CLONE)) {
                        return null;
                    }
                    return chain.proceed();
                });
                n++;
            }
            logOk("badges", n);
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "badges failed", t);
        }
        try {
            List<Method> found = DexLocator.findGlobal(cl, c ->
                    c.paramCount == 1
                            && "Landroid/graphics/drawable/Drawable;".equals(c.returnType)
                            && "Landroid/os/UserHandle;".equals(
                                    c.paramTypes.isEmpty() ? "" : c.paramTypes.get(0)));
            for (Method m : found) {
                module.hook(m).intercept(chain ->
                        Cfg.bool(FeatureKeys.BADGE_CLONE) ? null : chain.proceed());
            }
            logOk("clone-badge", found.size());
        } catch (Throwable ignored) {
        }
    }

    // ── 安装器 ──

    private void hookInstaller(ClassLoader cl) {
        hookInstallButtonFix(cl);
        hookSkipScan(cl);
        hookAllowReplaceInstall(cl);
        hookAutoClick(cl);
        hookDisableAppDetail(cl);
        hookInstallProgress(cl);
    }

    private void hookInstallButtonFix(ClassLoader cl) {
        int n = 0;
        for (String cn : new String[]{
                "com.android.packageinstaller.oplus.view.ConfusedButton",
                "com.android.packageinstaller.oplus.view.ConfusedTextView"}) {
            try {
                Class<?> c = cl.loadClass(cn);
                Method setCts = null;
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals("setCts")) {
                        setCts = m;
                        break;
                    }
                }
                Field rnd = null;
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType() == java.security.SecureRandom.class) {
                        rnd = f;
                        break;
                    }
                }
                final Method fix = setCts;
                final Field rndF = rnd;
                for (Method m : ms(c, "getAccessibilityViewId", "getText")) {
                    module.hook(m).intercept(chain -> {
                        Object self = chain.getThisObject();
                        if (Cfg.bool(FeatureKeys.FIX_INSTALL_BUTTON) && self != null) {
                            if (fix != null) {
                                fix.setAccessible(true);
                                fix.invoke(self, Boolean.TRUE);
                            }
                            if (rndF != null) {
                                rndF.setAccessible(true);
                                rndF.set(self, new java.security.SecureRandom());
                            }
                        }
                        return chain.proceed();
                    });
                    n++;
                }
            } catch (Throwable ignored) {
            }
        }
        logOk("install-button", n);
    }

    private void hookSkipScan(ClassLoader cl) {
        if (!Cfg.bool(FeatureKeys.SKIP_APK_SCAN)) return;
        try {
            Class<?> act = cl.loadClass(
                    "com.android.packageinstaller.oplus.OPlusPackageInstallerActivity");
            final Method scan = DexLocator.firstInClass(cl, act, c ->
                    c.paramCount == 0 && c.returnsVoid
                            && c.hasCallerNamed("onClick")
                            && c.hasFieldTypes("J", "Z", "Landroid/view/View;"));
            final Method init = DexLocator.firstInClass(cl, act, c ->
                    c.paramCount == 0 && c.returnsVoid
                            && c.hasCallerNamed("onClick")
                            && c.hasFieldTypes("Landroid/content/pm/PackageManager;",
                                    "Landroid/content/pm/PackageInfo;",
                                    "Landroid/content/pm/ApplicationInfo;", "Z"));
            if (scan == null || init == null) {
                module.log(Log.WARN, TAG, "skip-scan candidates not found");
                return;
            }
            scan.setAccessible(true);
            module.hook(scan).intercept(chain -> {
                if (Cfg.bool(FeatureKeys.SKIP_APK_SCAN)) {
                    Object self = chain.getThisObject();
                    if (self != null) init.invoke(self);
                    return null;
                }
                return chain.proceed();
            });
            logOk("skip-scan", 1);
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "skip-scan failed", t);
        }
    }

    private void hookAllowReplaceInstall(ClassLoader cl) {
        if (!Cfg.bool(FeatureKeys.ALLOW_DOWNGRADE)) return;
        try {
            Class<?> act = cl.loadClass(
                    "com.android.packageinstaller.oplus.OPlusPackageInstallerActivity");
            final Method parse = DexLocator.firstInClass(cl, act, c ->
                    c.paramCount == 0 && c.hasStrings("currentVersionCode", "apkVersioncode"));
            final Method preSafe = DexLocator.firstInClass(cl, act, c ->
                    c.paramCount == 0 && c.hasStrings("startAppdetail", "reason"));
            if (parse == null || preSafe == null) {
                module.log(Log.WARN, TAG, "allow-replace candidates not found");
                return;
            }
            parse.setAccessible(true);
            module.hook(parse).intercept(chain -> {
                if (Cfg.bool(FeatureKeys.ALLOW_DOWNGRADE)) {
                    Object self = chain.getThisObject();
                    if (self != null) preSafe.invoke(self);
                    return null;
                }
                return chain.proceed();
            });
            logOk("allow-replace", 1);
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "allow-replace failed", t);
        }
    }

    private void hookAutoClick(ClassLoader cl) {
        if (Cfg.bool(FeatureKeys.AUTO_INSTALL)) {
            try {
                Class<?> act = cl.loadClass(
                        "com.android.packageinstaller.oplus.OPlusPackageInstallerActivity");
                String initName = initiateName(cl, act);
                Method confirm = null;
                if (initName != null) {
                    final String ini = initName;
                    confirm = DexLocator.firstInClass(cl, act, c ->
                            c.paramCount == 0 && c.returnsVoid
                                    && c.hasCallerNamed(ini)
                                    && c.hasFieldTypes("Landroid/view/View;", "Z",
                                            "Ljava/util/ArrayList;"));
                }
                if (confirm == null) {
                    List<Method> cands = DexLocator.findInClass(cl, act, c ->
                            c.paramCount == 0 && c.returnsVoid
                                    && c.hasFieldTypes("Landroid/view/View;", "Z",
                                            "Ljava/util/ArrayList;")
                                    && !c.callerNames.isEmpty());
                    if (!cands.isEmpty()) confirm = cands.get(0);
                }
                if (confirm != null) {
                    confirm.setAccessible(true);
                    module.hook(confirm).intercept(chain -> autoClick(chain, "ok_button"));
                    logOk("auto-install", 1);
                } else {
                    module.log(Log.WARN, TAG, "auto-install not found");
                }
            } catch (Throwable t) {
                module.log(Log.WARN, TAG, "auto-install failed", t);
            }
        }
        if (Cfg.bool(FeatureKeys.AUTO_UNINSTALL)) {
            try {
                Class<?> act = cl.loadClass("com.android.packageinstaller.UninstallerActivity");
                List<Method> found = DexLocator.findInClass(cl, act, c ->
                        c.paramCount == 1 && c.returnsVoid
                                && "Landroid/content/Intent;".equals(
                                        c.paramTypes.isEmpty() ? "" : c.paramTypes.get(0))
                                && c.hasStrings("isUninstalledFont"));
                if (!found.isEmpty()) {
                    found.get(0).setAccessible(true);
                    module.hook(found.get(0)).intercept(chain -> autoClick(chain, "ok_button"));
                    logOk("auto-uninstall-confirm", 1);
                }
                Class<?> prog = cl.loadClass(
                        "com.android.packageinstaller.oplus.OPlusUninstallAppProgress");
                List<Method> pv = DexLocator.findInClass(cl, prog, c ->
                        c.paramCount == 0 && c.returnsVoid
                                && c.hasStrings("source_info", "package_name", "package_size"));
                if (!pv.isEmpty()) {
                    pv.get(0).setAccessible(true);
                    module.hook(pv.get(0)).intercept(chain -> autoClick(chain, "complete_button"));
                    logOk("auto-uninstall-done", 1);
                }
            } catch (Throwable t) {
                module.log(Log.WARN, TAG, "auto-uninstall failed", t);
            }
        }
    }

    private String initiateName(ClassLoader cl, Class<?> act) {
        try {
            List<Method> cands = DexLocator.findInClass(cl, act, c ->
                    c.paramCount == 0 && c.returnsVoid
                            && c.hasCallerNamed("onClick")
                            && c.hasFieldTypes("Landroid/content/pm/PackageManager;",
                                    "Landroid/content/pm/PackageInfo;",
                                    "Landroid/content/pm/ApplicationInfo;", "Z"));
            return cands.isEmpty() ? null : cands.get(0).getName();
        } catch (Throwable t) {
            return null;
        }
    }

    private Object autoClick(XposedInterface.Chain chain, String idName) throws Throwable {
        Object r = chain.proceed();
        try {
            Object self = chain.getThisObject();
            if (self instanceof Activity) {
                Activity a = (Activity) self;
                int id = a.getResources().getIdentifier(idName, "id", a.getPackageName());
                if (id != 0) {
                    View v = a.findViewById(id);
                    if (v instanceof Button) v.performClick();
                }
            }
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "auto-click failed", t);
        }
        return r;
    }

    private void hookDisableAppDetail(ClassLoader cl) {
        if (!Cfg.bool(FeatureKeys.DISABLE_APPDETAIL)) return;
        try {
            List<Method> found = DexLocator.findGlobal(cl, c ->
                    c.hasStrings("count_canceled_by_app_detail", "com.oplus.appdetail"));
            for (Method m : found) {
                module.hook(m).intercept(chain ->
                        Cfg.bool(FeatureKeys.DISABLE_APPDETAIL) ? 9 : chain.proceed());
            }
            logOk("disable-appdetail", found.size());
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "disable-appdetail failed", t);
        }
    }


    /** 安装完成页：移除推荐广告 + 自动点击完成 */
    private void hookInstallProgress(ClassLoader cl) {
        boolean ads = Cfg.bool(FeatureKeys.REMOVE_INSTALL_ADS);
        boolean auto = Cfg.bool(FeatureKeys.AUTO_INSTALL);
        if (!ads && !auto) return;
        try {
            Class<?> prog;
            try {
                prog = cl.loadClass("com.android.packageinstaller.oplus.InstallAppProgress");
            } catch (Throwable e) {
                Method iv = DexLocator.firstBySimpleName(cl, "InstallAppProgress", c ->
                        c.paramCount == 0 && c.returnsVoid && c.hasCallerNamed("onCreate")
                                && c.hasStrings("source_info", "type_channel_title",
                                        "type_channel_tips"));
                if (iv == null) {
                    module.log(Log.WARN, TAG, "install-progress class not found", null);
                    return;
                }
                prog = iv.getDeclaringClass();
            }
            if (ads) {
                Method iv = DexLocator.firstInClass(cl, prog, c ->
                        c.paramCount == 0 && c.returnsVoid && c.hasCallerNamed("onCreate")
                                && c.hasStrings("source_info", "type_channel_title",
                                        "type_channel_tips"));
                if (iv != null) {
                    iv.setAccessible(true);
                    module.hook(iv).intercept(chain -> {
                        Object r = chain.proceed();
                        hideAds(chain.getThisObject());
                        return r;
                    });
                }
                Method hm = DexLocator.firstInClass(cl, prog, c ->
                        c.name.equals("handleMessage")
                                && c.hasStrings("oplus.intent.action.VIRUS_APK_INSTALLED",
                                        "oplus.permission.OPLUS_COMPONENT_SAFE"));
                if (hm != null) {
                    hm.setAccessible(true);
                    module.hook(hm).intercept(chain -> {
                        Object r = chain.proceed();
                        hideAds(chain.getThisObject());
                        return r;
                    });
                }
                logOk("remove-ads", 1);
            }
            if (auto) {
                Method ivRef = DexLocator.firstInClass(cl, prog, c ->
                        c.paramCount == 0 && c.returnsVoid && c.hasCallerNamed("onCreate")
                                && c.hasStrings("source_info", "type_channel_title",
                                        "type_channel_tips"));
                final String ivName = ivRef == null ? null : ivRef.getName();
                if (ivName != null) {
                    Method opi = DexLocator.firstInClass(cl, prog, c ->
                            c.paramCount == 1 && "I".equals(c.returnType)
                                    && c.hasCallerNamed(ivName));
                    if (opi != null) {
                        opi.setAccessible(true);
                        module.hook(opi).intercept(chain -> {
                            Object r = chain.proceed();
                            java.util.List<Object> args = chain.getArgs();
                            if (!args.isEmpty() && Integer.valueOf(0).equals(args.get(0))) {
                                clickSelf(chain.getThisObject(), "done_button");
                            }
                            return r;
                        });
                        logOk("auto-done", 1);
                    }
                }
            }
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "install-progress failed", t);
        }
    }

    private void hideAds(Object self) {
        if (!(self instanceof Activity)) return;
        Activity a = (Activity) self;
        for (String id : new String[]{"suggest_A_scroll_layout", "install_done_suggest_B"}) {
            try {
                int res = a.getResources().getIdentifier(id, "id", a.getPackageName());
                if (res != 0) {
                    View v = a.findViewById(res);
                    if (v != null) v.setVisibility(View.GONE);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void clickSelf(Object self, String idName) {
        if (!(self instanceof Activity)) return;
        Activity a = (Activity) self;
        try {
            int id = a.getResources().getIdentifier(idName, "id", a.getPackageName());
            if (id != 0) {
                View v = a.findViewById(id);
                if (v instanceof Button) v.performClick();
            }
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "click-self failed", t);
        }
    }

    // ── 权限管理自动解锁 ──


    private void hookPermissionUnlock(ClassLoader cl) {
        try {
            Class<?> act = cl.loadClass(
                    "com.oplusos.securitypermission.permission.PermissionGroupsActivity");
            for (Method m : ms(act, "onCreate")) {
                module.hook(m).intercept(chain -> {
                    if (Cfg.bool(FeatureKeys.UNLOCK_RESTRICTED)) {
                        try {
                            Object self = chain.getThisObject();
                            java.util.List<Object> args = chain.getArgs();
                            if (self instanceof Activity && !args.isEmpty()
                                    && args.get(0) instanceof Intent) {
                                Intent it = (Intent) args.get(0);
                                Bundle ex = it.getExtras();
                                String pkg = null;
                                if (ex != null) {
                                    pkg = ex.getString("packageName");
                                    if (pkg == null) pkg = ex.getString("mPackageName");
                                }
                                if (pkg != null) EcmLike.autoUnlock((Activity) self, pkg);
                            }
                        } catch (Throwable t) {
                            module.log(Log.WARN, TAG, "ecm unlock failed", t);
                        }
                    }
                    return chain.proceed();
                });
            }
            logOk("perm-unlock", 1);
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "perm-unlock failed", t);
        }
    }

    // ── 反射工具 ──

    private static Field findField(Class<?> c, String name) {
        Class<?> cur = c;
        while (cur != null) {
            try {
                Field f = cur.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }
}
