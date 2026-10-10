package com.xiaokan.qzgflp;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * MultiFeatures 的经典 XposedBridge 镜像（无 libxposed API 环境）。
 * 配置经 Cfg（XSharedPreferences 注入）读取。
 */
final class FeatureHooksLegacy {

    private FeatureHooksLegacy() {
    }

    private static void log(String msg, Throwable t) {
        XposedBridge.log("[ForceCapture] " + msg + (t == null ? "" : ": " + t));
    }

    private static List<Method> ms(Class<?> c, String... names) {
        List<String> list = Arrays.asList(names);
        List<Method> out = new ArrayList<>();
        for (Method m : c.getDeclaredMethods()) {
            if (list.contains(m.getName())) out.add(m);
        }
        return out;
    }

    private interface Decision {
        Object run(XC_MethodHook.MethodHookParam param) throws Throwable;
    }

    private static int rep(Class<?> c, String key, Decision d, String... names) {
        int n = 0;
        for (Method m : ms(c, names)) {
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object r;
                    try {
                        r = d.run(param);
                    } catch (Throwable t) {
                        return;
                    }
                    if (r != SKIP) param.setResult(r);
                }
            });
            n++;
        }
        return n;
    }

    private static final Object SKIP = new Object();

    private static Decision when(String key, Object val) {
        return param -> Cfg.bool(key) ? val : SKIP;
    }

    private static int repVal(ClassLoader cl, String cn, String key, Object val, String name) {
        try {
            return rep(cl.loadClass(cn), key, when(key, val), name);
        } catch (Throwable t) {
            return 0;
        }
    }

    static void onSystemServer(ClassLoader cl) {
        hook32Bit(cl);
        hookAdbConfirm(cl);
        hookScreenshotPrivacy(cl);
        hookScreenshotDelay(cl);
        hookDowngrade(cl);
        hookVerify(cl);
        hookFeatureConfig(cl);
    }

    private static void hook32Bit(ClassLoader cl) {
        int n = repVal(cl, "com.android.server.pm.OplusPackageManagerHelper",
                FeatureKeys.ENABLE_32BIT, Boolean.TRUE, "allowInstall32BitApp");
        log("32bit x" + n, null);
    }

    private static void hookAdbConfirm(ClassLoader cl) {
        int n = 0;
        n += repVal(cl, "com.android.server.pm.OplusPackageInstallInterceptManager",
                FeatureKeys.REMOVE_ADB_CONFIRM, Boolean.FALSE, "allowInterceptAdbInstallInInstallStage");
        n += repVal(cl, "com.android.server.pm.ColorPackageInstallInterceptManager",
                FeatureKeys.REMOVE_ADB_CONFIRM, Boolean.FALSE, "allowInterceptAdbInstallInInstallStage");
        log("adb-confirm x" + n, null);
    }

    private static void hookScreenshotPrivacy(ClassLoader cl) {
        int n = repVal(cl, "com.android.server.wm.OplusLongshotMainWindow",
                FeatureKeys.SCREENSHOT_PRIVACY, Boolean.FALSE, "hasSecure");
        log("screenshot-privacy x" + n, null);
    }

    private static void hookScreenshotDelay(ClassLoader cl) {
        int n = repVal(cl, "com.android.server.policy.PhoneWindowManager",
                FeatureKeys.SCREENSHOT_NO_DELAY, 0L, "getScreenshotChordLongPressDelay");
        log("screenshot-delay x" + n, null);
    }

    private static void hookDowngrade(ClassLoader cl) {
        try {
            Class<?> pms = cl.loadClass("com.android.server.pm.PackageManagerService");
            int n = 0;
            for (Method m : ms(pms, "checkDowngrade")) {
                final boolean boolRet = m.getReturnType() == boolean.class;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!Cfg.bool(FeatureKeys.ALLOW_DOWNGRADE)) return;
                        param.setResult(boolRet ? (Object) Boolean.FALSE : null);
                    }
                });
                n++;
            }
            log("downgrade x" + n, null);
        } catch (Throwable t) {
            log("downgrade failed", t);
        }
    }

    private static void hookVerify(ClassLoader cl) {
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
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (Cfg.bool(FeatureKeys.DISABLE_VERIFY)) {
                                try {
                                    int f = mInstallFlags.getInt(param.thisObject);
                                    mInstallFlags.setInt(param.thisObject, f | 0x00080000);
                                } catch (Throwable ignored) {
                                }
                            }
                        }
                    });
                    n++;
                }
            }
            n += repVal(cl, "com.android.server.pm.VerifyingSession",
                    FeatureKeys.DISABLE_VERIFY, Boolean.FALSE, "isAdbVerificationEnabled");
            n += repVal(cl, "com.android.server.pm.PackageManagerService",
                    FeatureKeys.DISABLE_VERIFY, Boolean.FALSE, "isVerificationEnabled");
            try {
                Class<?> iph = cl.loadClass("com.android.server.pm.InstallPackageHelper");
                for (Method m : ms(iph, "doesSignatureMatchForPermissions")) {
                    if (m.getReturnType() != boolean.class) continue;
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (Cfg.bool(FeatureKeys.DISABLE_VERIFY)
                                    && Boolean.FALSE.equals(param.getResult())) {
                                param.setResult(Boolean.TRUE);
                            }
                        }
                    });
                    n++;
                }
            } catch (Throwable ignored) {
            }
            log("verify x" + n, null);
        } catch (Throwable t) {
            log("verify failed", t);
        }
    }

    private static void hookFeatureConfig(ClassLoader cl) {
        try {
            Class<?> fc = cl.loadClass("com.oplus.content.OplusFeatureConfigManager");
            rep(fc, FeatureKeys.EYE_TEXTURE, param -> {
                Object[] args = param.args;
                if (args.length > 0 && MultiFeatures.eyeFeature(args[0])) return Boolean.TRUE;
                return SKIP;
            }, "hasFeature");
        } catch (Throwable ignored) {
        }
    }

    static void onPackage(String pkg, ClassLoader cl) {
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
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (Cfg.bool(FeatureKeys.ALLOW_DISABLE_SYSAPPS)
                                        && param.args.length > 0) {
                                    param.args[0] = Boolean.TRUE;
                                }
                            }
                        });
                        n++;
                    }
                } catch (Throwable ignored) {
                }
                log("disable-sysapps x" + n, null);
            } else if (pkg.equals("com.oplus.securitypermission")) {
                hookPermissionUnlock(cl);
            } else if (pkg.equals("com.oplus.eyeprotect")) {
                hookFeatureConfig(cl);
            }
        } catch (Throwable t) {
            log("multi-features failed in " + pkg, t);
        }
    }

    private static void hookFolderBg(ClassLoader cl) {
        try {
            Class<?> bg = cl.loadClass("com.android.launcher3.folder.OplusPreviewBackground");
            final Field f = findField(bg, "mBgDrawable");
            int n = 0;
            for (Method m : ms(bg, "setBackground", "setup")) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (Cfg.bool(FeatureKeys.FOLDER_BG) && f != null) {
                            try {
                                f.set(param.thisObject, null);
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                });
                n++;
            }
            for (Method m : ms(bg, "drawBackground")) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (Cfg.bool(FeatureKeys.FOLDER_BG)) param.setResult(null);
                    }
                });
                n++;
            }
            log("folder-bg x" + n, null);
        } catch (Throwable t) {
            log("folder-bg failed", t);
        }
        try {
            Class<?> am = cl.loadClass("com.android.launcher3.folder.OplusFolderAnimationManager");
            for (Method m : ms(am, "getFolderBackgroundAnimator")) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (Cfg.bool(FeatureKeys.FOLDER_BG)) param.setResult(null);
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private static void hookLauncherLayout(ClassLoader cl) {
        try {
            Class<?> ui = cl.loadClass("com.android.launcher.UiConfig");
            repVal(cl, "com.android.launcher.UiConfig",
                    FeatureKeys.LAYOUT_CUSTOM, Boolean.FALSE, "isSupportLayout");
            int n = 1;
            for (Method m : ms(ui, "getSupportLayout")) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!Cfg.bool(FeatureKeys.LAYOUT_CUSTOM)) return;
                        int maxCols = Cfg.intv(FeatureKeys.LAYOUT_COLS);
                        int maxRows = Cfg.intv(FeatureKeys.LAYOUT_ROWS);
                        ArrayList<Object> out = new ArrayList<>();
                        for (int col = 4; col <= maxCols; col++) {
                            for (int row = 6; row <= maxRows; row++) {
                                out.add(android.util.Pair.create(col,
                                        android.util.Pair.create(row, row + 1)));
                            }
                        }
                        param.setResult(out);
                    }
                });
                n++;
            }
            try {
                Class<?> ifu = cl.loadClass("com.android.launcher.iconfallen.IconFallenUtils");
                for (Method m : ms(ifu, "getLogicCellX")) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (Cfg.bool(FeatureKeys.LAYOUT_CUSTOM)
                                    && param.getResult() instanceof Integer
                                    && (Integer) param.getResult() > 4) {
                                param.setResult(4);
                            }
                        }
                    });
                    n++;
                }
            } catch (Throwable ignored) {
            }
            try {
                Class<?> tb = cl.loadClass(
                        "com.android.launcher.togglebar.adapter.ToggleBarLayoutAdapter");
                final Field cols = findField(tb, "MIN_MAX_COLUMN");
                final Field rows = findField(tb, "MIN_MAX_ROW");
                for (Method m : ms(tb, "initToggleBarLayoutConfigs")) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!Cfg.bool(FeatureKeys.LAYOUT_CUSTOM)) return;
                            try {
                                if (cols != null) setArr(cols, param.thisObject,
                                        Cfg.intv(FeatureKeys.LAYOUT_COLS));
                                if (rows != null) setArr(rows, param.thisObject,
                                        Cfg.intv(FeatureKeys.LAYOUT_ROWS));
                            } catch (Throwable ignored) {
                            }
                        }
                    });
                    n++;
                }
            } catch (Throwable ignored) {
            }
            log("layout x" + n, null);
        } catch (Throwable t) {
            log("layout failed", t);
        }
    }

    private static void setArr(Field f, Object self, int v) {
        try {
            int[] arr = (int[]) f.get(self);
            if (arr != null && arr.length > 1) arr[1] = v;
        } catch (Throwable ignored) {
        }
    }

    private static void hookBadges(ClassLoader cl) {
        try {
            Class<?> bi = cl.loadClass("com.android.launcher3.icons.BitmapInfo");
            final Field flagsF = findField(bi, "flags");
            final Field badgeF = findField(bi, "badgeInfo");
            int n = 0;
            for (Method m : ms(bi, "applyFlags")) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!Cfg.bool(FeatureKeys.BADGE_SHORTCUT)
                                && !Cfg.bool(FeatureKeys.BADGE_WORK)
                                && !Cfg.bool(FeatureKeys.BADGE_CLONE)) return;
                        Object self = param.thisObject;
                        if (self == null || flagsF == null) return;
                        int flag;
                        try {
                            flag = flagsF.getInt(self);
                        } catch (Throwable t) {
                            return;
                        }
                        int creation = 0;
                        if (param.args != null) {
                            for (Object a : param.args) {
                                if (a instanceof Integer) creation = (Integer) a;
                            }
                        }
                        if ((creation & 2) != 0) return;
                        Object badge = null;
                        if (badgeF != null) {
                            try {
                                badge = badgeF.get(self);
                            } catch (Throwable ignored) {
                            }
                        }
                        if (badge != null && Cfg.bool(FeatureKeys.BADGE_SHORTCUT)) {
                            param.setResult(null);
                            return;
                        }
                        if ((flag & 4) == 0) {
                            if ((flag & 1) != 0 && Cfg.bool(FeatureKeys.BADGE_WORK)) {
                                param.setResult(null);
                            }
                        } else if (Cfg.bool(FeatureKeys.BADGE_CLONE)) {
                            param.setResult(null);
                        }
                    }
                });
                n++;
            }
            log("badges x" + n, null);
        } catch (Throwable t) {
            log("badges failed", t);
        }
        try {
            List<Method> found = DexLocator.findGlobal(cl, c ->
                    c.paramCount == 1
                            && "Landroid/graphics/drawable/Drawable;".equals(c.returnType)
                            && "Landroid/os/UserHandle;".equals(
                                    c.paramTypes.isEmpty() ? "" : c.paramTypes.get(0)));
            for (Method m : found) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (Cfg.bool(FeatureKeys.BADGE_CLONE)) param.setResult(null);
                    }
                });
            }
            log("clone-badge x" + found.size(), null);
        } catch (Throwable ignored) {
        }
    }

    private static void hookInstaller(ClassLoader cl) {
        hookInstallButtonFix(cl);
        hookSkipScan(cl);
        hookAllowReplaceInstall(cl);
        hookAutoClick(cl);
        hookDisableAppDetail(cl);
        hookInstallProgress(cl);
    }

    private static void hookInstallButtonFix(ClassLoader cl) {
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
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!Cfg.bool(FeatureKeys.FIX_INSTALL_BUTTON)) return;
                            try {
                                if (fix != null) {
                                    fix.setAccessible(true);
                                    fix.invoke(param.thisObject, Boolean.TRUE);
                                }
                                if (rndF != null) {
                                    rndF.setAccessible(true);
                                    rndF.set(param.thisObject, new java.security.SecureRandom());
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
                    n++;
                }
            } catch (Throwable ignored) {
            }
        }
        log("install-button x" + n, null);
    }

    private static void hookSkipScan(ClassLoader cl) {
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
                log("skip-scan candidates not found", null);
                return;
            }
            scan.setAccessible(true);
            XposedBridge.hookMethod(scan, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!Cfg.bool(FeatureKeys.SKIP_APK_SCAN)) return;
                    try {
                        init.invoke(param.thisObject);
                        param.setResult(null);
                    } catch (Throwable t) {
                        log("skip-scan invoke failed", t);
                    }
                }
            });
            log("skip-scan x1", null);
        } catch (Throwable t) {
            log("skip-scan failed", t);
        }
    }

    private static void hookAllowReplaceInstall(ClassLoader cl) {
        if (!Cfg.bool(FeatureKeys.ALLOW_DOWNGRADE)) return;
        try {
            Class<?> act = cl.loadClass(
                    "com.android.packageinstaller.oplus.OPlusPackageInstallerActivity");
            final Method parse = DexLocator.firstInClass(cl, act, c ->
                    c.paramCount == 0 && c.hasStrings("currentVersionCode", "apkVersioncode"));
            final Method preSafe = DexLocator.firstInClass(cl, act, c ->
                    c.paramCount == 0 && c.hasStrings("startAppdetail", "reason"));
            if (parse == null || preSafe == null) {
                log("allow-replace candidates not found", null);
                return;
            }
            parse.setAccessible(true);
            XposedBridge.hookMethod(parse, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!Cfg.bool(FeatureKeys.ALLOW_DOWNGRADE)) return;
                    try {
                        preSafe.invoke(param.thisObject);
                        param.setResult(null);
                    } catch (Throwable t) {
                        log("allow-replace invoke failed", t);
                    }
                }
            });
            log("allow-replace x1", null);
        } catch (Throwable t) {
            log("allow-replace failed", t);
        }
    }

    private static void hookAutoClick(ClassLoader cl) {
        if (Cfg.bool(FeatureKeys.AUTO_INSTALL)) {
            try {
                Class<?> act = cl.loadClass(
                        "com.android.packageinstaller.oplus.OPlusPackageInstallerActivity");
                String initName = null;
                List<Method> inits = DexLocator.findInClass(cl, act, c ->
                        c.paramCount == 0 && c.returnsVoid
                                && c.hasCallerNamed("onClick")
                                && c.hasFieldTypes("Landroid/content/pm/PackageManager;",
                                        "Landroid/content/pm/PackageInfo;",
                                        "Landroid/content/pm/ApplicationInfo;", "Z"));
                if (!inits.isEmpty()) initName = inits.get(0).getName();
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
                    XposedBridge.hookMethod(confirm, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            click((Activity) param.thisObject, "ok_button");
                        }
                    });
                    log("auto-install x1", null);
                } else {
                    log("auto-install not found", null);
                }
            } catch (Throwable t) {
                log("auto-install failed", t);
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
                    XposedBridge.hookMethod(found.get(0), new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            click((Activity) param.thisObject, "ok_button");
                        }
                    });
                    log("auto-uninstall-confirm x1", null);
                }
                Class<?> prog = cl.loadClass(
                        "com.android.packageinstaller.oplus.OPlusUninstallAppProgress");
                List<Method> pv = DexLocator.findInClass(cl, prog, c ->
                        c.paramCount == 0 && c.returnsVoid
                                && c.hasStrings("source_info", "package_name", "package_size"));
                if (!pv.isEmpty()) {
                    pv.get(0).setAccessible(true);
                    XposedBridge.hookMethod(pv.get(0), new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            click((Activity) param.thisObject, "complete_button");
                        }
                    });
                    log("auto-uninstall-done x1", null);
                }
            } catch (Throwable t) {
                log("auto-uninstall failed", t);
            }
        }
    }

    private static void click(Activity a, String idName) {
        try {
            int id = a.getResources().getIdentifier(idName, "id", a.getPackageName());
            if (id != 0) {
                View v = a.findViewById(id);
                if (v instanceof Button) v.performClick();
            }
        } catch (Throwable t) {
            log("auto-click failed", t);
        }
    }

    private static void hookDisableAppDetail(ClassLoader cl) {
        if (!Cfg.bool(FeatureKeys.DISABLE_APPDETAIL)) return;
        try {
            List<Method> found = DexLocator.findGlobal(cl, c ->
                    c.hasStrings("count_canceled_by_app_detail", "com.oplus.appdetail"));
            for (Method m : found) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (Cfg.bool(FeatureKeys.DISABLE_APPDETAIL)) param.setResult(9);
                    }
                });
            }
            log("disable-appdetail x" + found.size(), null);
        } catch (Throwable t) {
            log("disable-appdetail failed", t);
        }
    }


    /** 安装完成页：移除推荐广告 + 自动点击完成 */
    private static void hookInstallProgress(ClassLoader cl) {
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
                    log("install-progress class not found", null);
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
                    XposedBridge.hookMethod(iv, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            hideAds(param.thisObject);
                        }
                    });
                }
                Method hm = DexLocator.firstInClass(cl, prog, c ->
                        c.name.equals("handleMessage")
                                && c.hasStrings("oplus.intent.action.VIRUS_APK_INSTALLED",
                                        "oplus.permission.OPLUS_COMPONENT_SAFE"));
                if (hm != null) {
                    hm.setAccessible(true);
                    XposedBridge.hookMethod(hm, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            hideAds(param.thisObject);
                        }
                    });
                }
                log("remove-ads x1", null);
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
                        XposedBridge.hookMethod(opi, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                if (param.args.length > 0
                                        && Integer.valueOf(0).equals(param.args[0])) {
                                    click((Activity) param.thisObject, "done_button");
                                }
                            }
                        });
                        log("auto-done x1", null);
                    }
                }
            }
        } catch (Throwable t) {
            log("install-progress failed", t);
        }
    }

    private static void hideAds(Object self) {
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

    private static void hookPermissionUnlock(ClassLoader cl) {
        try {
            Class<?> act = cl.loadClass(
                    "com.oplusos.securitypermission.permission.PermissionGroupsActivity");
            for (Method m : ms(act, "onCreate")) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!Cfg.bool(FeatureKeys.UNLOCK_RESTRICTED)) return;
                        try {
                            android.content.Intent it =
                                    (android.content.Intent) param.args[0];
                            android.os.Bundle ex = it == null ? null : it.getExtras();
                            String pkg = null;
                            if (ex != null) {
                                pkg = ex.getString("packageName");
                                if (pkg == null) pkg = ex.getString("mPackageName");
                            }
                            if (pkg != null) {
                                EcmLike.autoUnlock(
                                        (Activity) param.thisObject, pkg);
                            }
                        } catch (Throwable t) {
                            log("ecm unlock failed", t);
                        }
                    }
                });
            }
            log("perm-unlock x1", null);
        } catch (Throwable t) {
            log("perm-unlock failed", t);
        }
    }

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
