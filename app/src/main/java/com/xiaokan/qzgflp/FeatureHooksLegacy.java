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
        hookPinVerify72h(cl);
        hookUntrustedTouch(cl);
        hookIgnoreAudioFocus(cl);
        hookRootCheck(cl);
        hookSplitScreen(cl);
        hookPmsChecks(cl);
        hookUninstallBlacklist(cl);
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
                            if (!Boolean.FALSE.equals(param.getResult())) return;
                            if (Cfg.bool(FeatureKeys.DISABLE_VERIFY)) {
                                param.setResult(Boolean.TRUE);
                                return;
                            }
                            if (Cfg.bool(FeatureKeys.ALLOW_SIG_MISMATCH_UPDATE)) {
                                try {
                                    Object pkgArg = param.args.length > 0 ? param.args[0] : null;
                                    Object parsed = param.args.length > 1 ? param.args[1] : null;
                                    if (pkgArg != null && parsed != null) {
                                        Object name = XposedHelpers.callMethod(parsed, "getPackageName");
                                        if (pkgArg.equals(name)) param.setResult(Boolean.TRUE);
                                    }
                                } catch (Throwable ignored) {
                                }
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

    // ── 应用卸载黑名单（OShin xm0 移植）──

    private static void hookUninstallBlacklist(ClassLoader cl) {
        try {
            Class<?> c = cl.loadClass("com.android.server.pm.OplusUninstallableConfigManager");
            int n = 0;
            for (Method m : ms(c, "loadUninstallableConfig")) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!Cfg.bool(FeatureKeys.REMOVE_APP_UNINSTALL_BLACKLIST)) return;
                        try {
                            clearUninstallSets(param.thisObject);
                        } catch (Throwable t) {
                            log("uninstall-blacklist clear failed", t);
                        }
                    }
                });
                n++;
            }
            if (n > 0) log("uninstall-blacklist x" + n, null);
        } catch (ClassNotFoundException e) {
            // 当前进程无该类
        } catch (Throwable t) {
            log("uninstall-blacklist hook failed", t);
        }
    }

    private static void clearUninstallSets(Object self) throws Exception {
        if (self == null) return;
        Class<?> cls = self.getClass();
        for (String fn : new String[]{"mHideUninstallIcon", "mHideUninstallIconSoft"}) {
            Field f = findField(cls, fn);
            if (f == null) continue;
            Object holder = f.get(self);
            if (holder == null) continue;
            Field lf = findField(holder.getClass(), "mList");
            if (lf == null) continue;
            Object list = lf.get(holder);
            if (list instanceof java.util.Set) ((java.util.Set<?>) list).clear();
        }
    }

    // ── 智慧侧边栏（OShin e02 移植）──

    private static void hookSmartSidebar(ClassLoader cl) {
        int n = 0;
        try {
            Class<?> upv = cl.loadClass(
                    "com.oplus.smartsidebar.panelview.edgepanel.mainpanel.UserPanelView");
            if (Cfg.bool(FeatureKeys.REMOVE_APP_ADD_LIMIT)) {
                Field maxEntry = findField(upv, "MAX_USER_ENTRY");
                if (maxEntry != null) {
                    setStaticInt(upv, maxEntry, 999);
                    n++;
                }
            }
            n += repCanAdd(upv);
            for (Method m : ms(upv, "performAdd")) {
                if (m.getParameterTypes().length != 1
                        || !m.getParameterTypes()[0].getName().endsWith("AppLabelData")) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!Cfg.bool(FeatureKeys.REMOVE_APP_ADD_LIMIT)) return;
                        try {
                            Object self = param.thisObject;
                            if (self != null) {
                                Object list = fieldValue(self, "mPanelData");
                                Object item = fieldValue(self, "mEditOccupancyData");
                                if (list instanceof java.util.List && item != null) {
                                    ((java.util.List<Object>) list).add(item);
                                }
                            }
                        } catch (Throwable t) {
                            log("sidebar performAdd failed", t);
                        }
                    }
                });
                n++;
            }
        } catch (Throwable t) {
            log("smartsidebar hook failed", t);
        }
        try {
            Class<?> pmv = cl.loadClass("com.oplus.smartsidebar.panelview.edgepanel.PanelMainView");
            n += repCanAdd(pmv);
        } catch (Throwable ignored) {
        }
        log("smartsidebar x" + n, null);
    }

    private static int repCanAdd(Class<?> c) {
        int n = 0;
        for (Method m : ms(c, "canAdd")) {
            if (m.getParameterTypes().length != 1
                    || m.getParameterTypes()[0] != String.class
                    || m.getReturnType() != boolean.class) continue;
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (Cfg.bool(FeatureKeys.REMOVE_APP_ADD_LIMIT)) param.setResult(Boolean.TRUE);
                }
            });
            n++;
        }
        return n;
    }

    private static Object fieldValue(Object self, String name) throws Exception {
        Field f = findField(self.getClass(), name);
        return f == null ? null : f.get(self);
    }

    private static void setStaticInt(Class<?> owner, Field f, int v) throws Exception {
        try {
            f.setInt(null, v);
        } catch (IllegalAccessException e) {
            Field uf = findField(sun.misc.Unsafe.class, "theUnsafe");
            if (uf == null) throw e;
            sun.misc.Unsafe u = (sun.misc.Unsafe) uf.get(null);
            u.putInt(owner, u.staticFieldOffset(f), v);
        }
    }

    static void onPackage(String pkg, ClassLoader cl) {
        try {
            hookUninstallBlacklist(cl);
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
            } else if (pkg.equals("com.coloros.ocrscanner")) {
                hookFullScreenTranslation(cl);
            } else if (pkg.equals("com.heytap.themestore")) {
                hookThemeStore(cl);
            } else if (pkg.equals("com.coloros.smartsidebar")) {
                hookSmartSidebar(cl);
            }
        } catch (Throwable t) {
            log("multi-features failed in " + pkg, t);
        }
    }

    // ── 系统服务（OShin 移植，legacy 镜像）──

    private static void hookPinVerify72h(ClassLoader cl) {
        int n = 0;
        try {
            Class<?> c = cl.loadClass(
                    "com.android.server.locksettings.LockSettingsStrongAuth");
            for (Method m : ms(c, "rescheduleStrongAuthTimeoutAlarm")) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (Cfg.bool(FeatureKeys.DISABLE_PIN_72H)) param.setResult(null);
                    }
                });
                n++;
            }
        } catch (Throwable ignored) {
        }
        log("pin-72h x" + n, null);
    }

    private static void hookUntrustedTouch(ClassLoader cl) {
        int n = 0;
        try {
            Class<?> c = cl.loadClass("com.android.server.wm.WindowState");
            for (Method m : ms(c, "getTouchOcclusionMode")) {
                if (m.getParameterTypes().length != 0
                        || m.getReturnType() != int.class) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (Cfg.bool(FeatureKeys.ALLOW_UNTRUSTED_TOUCH)) {
                            param.setResult(2);
                        }
                    }
                });
                n++;
            }
        } catch (Throwable ignored) {
        }
        log("untrusted-touch x" + n, null);
    }

    private static void hookIgnoreAudioFocus(ClassLoader cl) {
        int n = 0;
        try {
            Class<?> c = cl.loadClass("com.android.server.audio.MediaFocusControl");
            for (Method m : c.getDeclaredMethods()) {
                if (!"requestAudioFocus".equals(m.getName())) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!Cfg.bool(FeatureKeys.IGNORE_AUDIO_FOCUS)) return;
                        if (param.args.length > 0
                                && param.args[0] instanceof android.media.AudioAttributes) {
                            int usage = ((android.media.AudioAttributes) param.args[0]).getUsage();
                            boolean allowed = usage == android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION
                                    || usage == android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
                                    || usage == android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
                            if (!allowed) {
                                param.setResult(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
                            }
                        }
                    }
                });
                n++;
            }
        } catch (Throwable ignored) {
        }
        log("ignore-audio-focus x" + n, null);
    }

    private static void hookRootCheck(ClassLoader cl) {
        int n = repVal(cl, "com.android.server.oplus.heimdall.HeimdallService",
                FeatureKeys.DISABLE_ROOT_CHECK, Boolean.FALSE, "isRootEnable");
        log("root-check x" + n, null);
    }

    private static void hookSplitScreen(ClassLoader cl) {
        try {
            Class<?> fwu = cl.loadClass("com.android.server.wm.FlexibleWindowUtils");
            final String rk = FeatureKeys.REMOVE_SMALL_WIN_RESTRICT;
            int n = 0;
            n += repVal(cl, "com.android.server.wm.FlexibleWindowUtils", rk,
                    Boolean.TRUE, "isUnSupportCallerFlexibleWindow");
            n += repVal(cl, "com.android.server.wm.FlexibleWindowUtils", rk,
                    Boolean.TRUE, "isSupportFlexibleWindow");
            n += repVal(cl, "com.android.server.wm.FlexibleWindowUtils", rk,
                    Boolean.FALSE, "isInFlexibleWindowBlackList");
            n += repVal(cl, "com.android.server.wm.FlexibleWindowUtils", rk,
                    Boolean.FALSE, "isInMultiWindowFlexibleBlackList");
            n += repVal(cl, "com.android.server.wm.FlexibleWindowUtils", rk,
                    Boolean.FALSE, "isFlexibleTaskInPSBlackList");
            for (Method m : ms(fwu, "getUnSupportRatiosInFlexibleTask")) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (Cfg.bool(rk)) param.setResult("");
                    }
                });
                n++;
            }
            log("small-window x" + n, null);
        } catch (Throwable t) {
            log("small-window failed", t);
        }
        try {
            Class<?> fwm = cl.loadClass("com.android.server.wm.FlexibleWindowManagerService");
            int n = 0;
            for (Method m : fwm.getDeclaredMethods()) {
                Class<?>[] ps = m.getParameterTypes();
                if (ps.length != 1 || m.getReturnType() != int.class) continue;
                String name = m.getName();
                String key;
                if ("getMaxWinNum".equals(name)) key = FeatureKeys.MAX_SMALL_WINDOWS;
                else if ("getCornerRadius".equals(name)) key = FeatureKeys.SMALL_WIN_CORNER_RADIUS;
                else if ("getShadowRadiusFocused".equals(name)) key = FeatureKeys.SMALL_WIN_FOCUSED_SHADOW;
                else if ("getShadowRadiusUnfocused".equals(name)) key = FeatureKeys.SMALL_WIN_UNFOCUSED_SHADOW;
                else continue;
                final String k = key;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        int v = Cfg.intv(k);
                        if (v != -1) param.setResult(v);
                    }
                });
                n++;
            }
            n += repVal(cl, "com.android.server.wm.FlexibleWindowManagerService",
                    FeatureKeys.FORCE_MULTI_WINDOW_MODE, Boolean.TRUE, "isSupportMultiMode");
            log("small-window-svc x" + n, null);
        } catch (Throwable ignored) {
        }
    }

    // ── 核心破解扩充（legacy 镜像）──

    private static void hookPmsChecks(ClassLoader cl) {
        try {
            Class<?> u = cl.loadClass("com.android.server.pm.PackageManagerServiceUtils");
            int n = 0;
            for (Method m : ms(u, "checkDowngrade")) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (Cfg.bool(FeatureKeys.ALLOW_DOWNGRADE)) param.setResult(null);
                    }
                });
                n++;
            }
            if (n > 0) log("downgrade-utils x" + n, null);
        } catch (Throwable ignored) {
        }
        try {
            Class<?> sjv = cl.loadClass("android.util.jar.StrictJarVerifier");
            final String k = FeatureKeys.DISABLE_JAR_VERIFIER;
            int n = 0;
            for (Method m : ms(sjv, "verifyMessageDigest", "verify")) {
                if (m.getReturnType() != boolean.class) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (Cfg.bool(k)) param.setResult(Boolean.TRUE);
                    }
                });
                n++;
            }
            for (java.lang.reflect.Constructor<?> ctor : sjv.getDeclaredConstructors()) {
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!Cfg.bool(k)) return;
                        try {
                            Field f = findField(sjv, "signatureSchemeRollbackProtectionsEnforced");
                            if (f != null) f.setBoolean(param.thisObject, false);
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
            if (n > 0) log("jar-verifier x" + n, null);
        } catch (Throwable t) {
            log("jar-verifier failed", t);
        }
        try {
            Class<?> md = Class.forName("java.security.MessageDigest");
            for (Method m : ms(md, "isEqual")) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (Cfg.bool(FeatureKeys.DISABLE_MESSAGE_DIGEST)) {
                            param.setResult(Boolean.TRUE);
                        }
                    }
                });
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> am = Class.forName("android.content.res.AssetManager");
            for (Method m : ms(am, "containsAllocatedTable")) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (Cfg.bool(FeatureKeys.BYPASS_ARSC_CHECK)) {
                            param.setResult(Boolean.FALSE);
                        }
                    }
                });
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> asv = cl.loadClass("android.util.apk.ApkSignatureVerifier");
            int n = 0;
            for (Method m : ms(asv, "getMinimumSignatureSchemeVersionForTargetSdk")) {
                if (m.getParameterTypes().length != 1
                        || m.getReturnType() != int.class) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (Cfg.bool(FeatureKeys.BYPASS_MIN_SIG_VERSION)) param.setResult(0);
                    }
                });
                n++;
            }
            Class<?> spu = cl.loadClass("com.android.server.pm.ScanPackageUtils");
            for (Method m : ms(spu, "assertMinSignatureSchemeIsValid")) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (Cfg.bool(FeatureKeys.BYPASS_MIN_SIG_VERSION)
                                && param.throwable != null) {
                            param.setThrowable(null);
                        }
                    }
                });
                n++;
            }
            if (n > 0) log("min-sig-version x" + n, null);
        } catch (Throwable ignored) {
        }
        try {
            Class<?> sd = cl.loadClass("android.content.pm.SigningDetails");
            int n = 0;
            for (Method m : ms(sd, "checkCapability")) {
                if (m.getParameterTypes().length != 2) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!Cfg.bool(FeatureKeys.ALLOW_SIG_MISMATCH_UPDATE)) return;
                        if (param.args.length >= 2 && param.args[1] instanceof Integer) {
                            int cap = (Integer) param.args[1];
                            if (cap != 4 && cap != 16) param.setResult(Boolean.TRUE);
                        }
                    }
                });
                n++;
            }
            Class<?> ksm = cl.loadClass("com.android.server.pm.KeySetManagerService");
            for (Method m : ms(ksm, "shouldCheckUpgradeKeySetLocked")) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!Cfg.bool(FeatureKeys.ALLOW_SIG_MISMATCH_UPDATE)) return;
                        boolean fromPrepare = false;
                        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
                            if (e.getMethodName().startsWith("preparePackage")) {
                                fromPrepare = true;
                                break;
                            }
                        }
                        KS_BYPASS_KEYSET.set(fromPrepare);
                        if (fromPrepare) param.setResult(Boolean.TRUE);
                    }
                });
                n++;
            }
            for (Method m : ms(ksm, "checkUpgradeKeySetLocked")) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (Cfg.bool(FeatureKeys.ALLOW_SIG_MISMATCH_UPDATE)
                                && Boolean.TRUE.equals(KS_BYPASS_KEYSET.get())) {
                            param.setResult(Boolean.TRUE);
                        }
                    }
                });
                n++;
            }
            if (n > 0) log("sig-mismatch-update x" + n, null);
        } catch (Throwable ignored) {
        }
        try {
            int n = repVal(cl, "android.content.pm.SigningDetails",
                    FeatureKeys.ALLOW_SPLIT_SIG_MISMATCH, Boolean.TRUE, "signaturesMatchExactly");
            if (n > 0) log("split-sig x" + n, null);
        } catch (Throwable ignored) {
        }
        try {
            Class<?> ai = android.content.pm.ApplicationInfo.class;
            int n = 0;
            for (Method m : ms(ai, "isPackageWhitelistedForHiddenApis")) {
                if (m.getReturnType() != boolean.class) continue;
                final boolean isStatic = java.lang.reflect.Modifier.isStatic(m.getModifiers());
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!Cfg.bool(FeatureKeys.ALLOW_HIDDEN_API)) return;
                        Object info = isStatic ? null : param.thisObject;
                        if (info instanceof android.content.pm.ApplicationInfo) {
                            int flags = ((android.content.pm.ApplicationInfo) info).flags;
                            if ((flags & 1) != 0 || (flags & 128) != 0) {
                                param.setResult(Boolean.TRUE);
                            }
                        }
                    }
                });
                n++;
            }
            if (n > 0) log("hidden-api x" + n, null);
        } catch (Throwable ignored) {
        }
        if (Cfg.bool(FeatureKeys.ALLOW_NONSYSTEM_SHARED_UID)) {
            try {
                Class<?> rpu = cl.loadClass("com.android.server.pm.ReconcilePackageUtils");
                Field f = findField(rpu, "ALLOW_NON_PRELOADS_SYSTEM_SHAREDUIDS");
                if (f != null && f.getType() == boolean.class) {
                    f.setAccessible(true);
                    f.setBoolean(null, true);
                    log("shared-uid x1", null);
                }
            } catch (Throwable t) {
                log("shared-uid failed", t);
            }
        }
        try {
            Class<?> vs = cl.loadClass("com.android.server.pm.VerifyingSession");
            int n = 0;
            for (Method m : ms(vs, "isVerificationEnabled")) {
                if (m.getReturnType() != boolean.class) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (Cfg.bool(FeatureKeys.DISABLE_INSTALL_VERIFICATION)
                                || Cfg.bool(FeatureKeys.DISABLE_VERIFY)) {
                            param.setResult(Boolean.FALSE);
                        }
                    }
                });
                n++;
            }
            if (n > 0) log("install-verification x" + n, null);
        } catch (Throwable ignored) {
        }
        try {
            Class<?> asv = cl.loadClass("android.util.apk.ApkSignatureVerifier");
            Class<?> ppe = null;
            try {
                ppe = cl.loadClass("android.content.pm.PackageParser$PackageParserException");
            } catch (Throwable ignored) {
            }
            final Class<?> ppeClass = ppe;
            final Field errField = ppe != null ? findField(ppe, "error") : null;
            int n = 0;
            for (Method m : ms(asv, "verifyV1Signature")) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.throwable == null) return;
                        if (!Cfg.bool(FeatureKeys.BYPASS_V1_SIG_ERRORS)) return;
                        Throwable cur = param.throwable;
                        boolean v1err = false;
                        for (int i = 0; i < 3 && cur != null; i++) {
                            if (ppeClass != null && cur.getClass() == ppeClass
                                    && errField != null) {
                                try {
                                    if (errField.getInt(cur) == -103) v1err = true;
                                } catch (Throwable ignored) {
                                }
                            }
                            if (v1err) break;
                            cur = cur.getCause();
                        }
                        if (!v1err) return;
                        Object sd = newSigningDetailsV1(cl);
                        if (sd != null) {
                            param.setThrowable(null);
                            param.setResult(sd);
                        }
                    }
                });
                n++;
            }
            if (n > 0) log("v1-sig-errors x" + n, null);
        } catch (Throwable t) {
            log("v1-sig-errors failed", t);
        }
        hookPmsCommand(cl);
    }

    private static final java.util.concurrent.atomic.AtomicReference<Object> PMS_INSTANCE =
            new java.util.concurrent.atomic.AtomicReference<>();
    private static final ThreadLocal<Boolean> KS_BYPASS_KEYSET = new ThreadLocal<>();

    private static Object newSigningDetailsV1(ClassLoader cl) {
        try {
            Class<?> sigC = cl.loadClass("android.content.pm.Signature");
            Object sig = sigC.getConstructor(String.class).newInstance(MultiFeatures.COREPATCH_CERT);
            Object sigArr = java.lang.reflect.Array.newInstance(sigC, 1);
            java.lang.reflect.Array.set(sigArr, 0, sig);
            Class<?> sd = cl.loadClass("android.content.pm.SigningDetails");
            for (java.lang.reflect.Constructor<?> c : sd.getDeclaredConstructors()) {
                Class<?>[] ps = c.getParameterTypes();
                if (ps.length == 2 && ps[0].isArray() && ps[1] == int.class) {
                    c.setAccessible(true);
                    return c.newInstance(sigArr, 1);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void hookPmsCommand(ClassLoader cl) {
        if (!Cfg.bool(FeatureKeys.PMS_COMMAND)) return;
        try {
            Class<?> pms = cl.loadClass("com.android.server.pm.PackageManagerService");
            for (java.lang.reflect.Constructor<?> ctor : pms.getDeclaredConstructors()) {
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        PMS_INSTANCE.set(param.thisObject);
                    }
                });
            }
            Class<?> pmsc = cl.loadClass("com.android.server.pm.PackageManagerShellCommand");
            for (Method m : ms(pmsc, "onCommand")) {
                if (m.getParameterTypes().length != 1) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Object cmd = param.args.length > 0 ? param.args[0] : null;
                        if (!"pms".equals(cmd)) return;
                        Object localPms = PMS_INSTANCE.get();
                        if (localPms == null) {
                            param.setResult(0);
                            return;
                        }
                        Object shell = param.thisObject;
                        try {
                            Object pw = XposedHelpers.callMethod(shell, "getOutPrintWriter");
                            String type = (String) XposedHelpers.callMethod(shell, "getNextArgRequired");
                            Object settings = XposedHelpers.getObjectField(localPms, "mSettings");
                            if ("p".equals(type) || "package".equals(type)) {
                                String name = (String) XposedHelpers.callMethod(shell, "getNextArgRequired");
                                Object ps = XposedHelpers.callMethod(settings, "getPackageLPr", name);
                                if (ps != null) dumpSettingSignatures(ps, pw);
                                else XposedHelpers.callMethod(pw, "println", "no package " + name + " found");
                            } else if ("su".equals(type) || "shareduser".equals(type)) {
                                String name = (String) XposedHelpers.callMethod(shell, "getNextArgRequired");
                                Object su = XposedHelpers.getObjectField(settings, "mSharedUsers");
                                Object target = su instanceof java.util.Map
                                        ? ((java.util.Map<?, ?>) su).get(name) : null;
                                if (target != null) dumpSettingSignatures(target, pw);
                                else XposedHelpers.callMethod(pw, "println", "no shared user " + name + " found");
                            } else {
                                XposedHelpers.callMethod(pw, "println", "usage: <p|package|su|shareduser> <name>");
                            }
                        } catch (Throwable t) {
                            log("pms command failed", t);
                        }
                        param.setResult(0);
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private static void dumpSettingSignatures(Object setting, Object pw) {
        try {
            Object signingDetails = XposedHelpers.getObjectField(setting, "signatures");
            if (signingDetails != null) {
                signingDetails = XposedHelpers.getObjectField(signingDetails, "mSigningDetails");
            }
            XposedHelpers.callMethod(pw, "println", "signing for " + setting);
            if (signingDetails == null) return;
            Object[] sigs = (Object[]) XposedHelpers.callMethod(signingDetails, "getSignatures");
            if (sigs == null) {
                XposedHelpers.callMethod(pw, "println", "Could not get signatures.");
                return;
            }
            for (int i = 0; i < sigs.length; i++) {
                XposedHelpers.callMethod(pw, "println", (i + 1) + ": "
                        + XposedHelpers.callMethod(sigs[i], "toCharsString"));
            }
        } catch (Throwable t) {
            XposedHelpers.callMethod(pw, "println", "dump failed: " + t);
        }
    }

    // ── 小布扫一扫 / 主题商店（legacy 镜像）──

    private static void hookFullScreenTranslation(ClassLoader cl) {
        if (!Cfg.bool(FeatureKeys.FULL_SCREEN_TRANSLATION)) return;
        int n = 0;
        try {
            Class<?> root = cl.loadClass(
                    "com.oplus.scanner.screentrans.ui.ScreenTranslationRootView");
            for (Method m : root.getDeclaredMethods()) {
                if (!"s0".equals(m.getName()) || m.getParameterTypes().length != 1
                        || m.getReturnType() != boolean.class) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(Boolean.FALSE);
                    }
                });
                n++;
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> inner = cl.loadClass(
                    "com.oplus.scanner.screentrans.ui.ScreenTranslationRootView$onNotSupportApp$1");
            for (Method m : inner.getDeclaredMethods()) {
                if (!"invokeSuspend".equals(m.getName())
                        || m.getParameterTypes().length != 1) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(Boolean.FALSE);
                    }
                });
                n++;
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> cap = cl.loadClass(
                    "com.oplus.scanner.screentrans.ui.ScreenTranslationToolCapsule");
            for (Method m : cap.getDeclaredMethods()) {
                if (m.getParameterTypes().length != 0
                        || m.getReturnType() != boolean.class) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(Boolean.TRUE);
                    }
                });
                n++;
            }
        } catch (Throwable ignored) {
        }
        log("full-screen-translation x" + n, null);
    }

    private static void hookThemeStore(ClassLoader cl) {
        if (Cfg.bool(FeatureKeys.THEME_UNLOCK_VIP)) hookThemeVip(cl);
        if (Cfg.bool(FeatureKeys.THEME_REMOVE_SPLASH_ADS)) hookThemeSplashAds(cl);
        if (Cfg.bool(FeatureKeys.THEME_REMOVE_UPGRADE)) hookThemeUpgrade(cl);
    }

    private static void hookThemeVip(ClassLoader cl) {
        try {
            Class<?> wpr = cl.loadClass("com.oppo.cdo.card.theme.dto.page.WeatherPageResponseDto");
            for (Method m : ms(wpr, "getVipStatus")) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            XposedHelpers.setIntField(param.thisObject, "vipStatus", 1);
                        } catch (Throwable ignored) {
                        }
                        param.setResult(1);
                    }
                });
            }
            Class<?> vud = cl.loadClass("com.oppo.cdo.card.theme.dto.vip.VipUserDto");
            for (Method m : ms(vud, "getVipStatus", "getVipDays")) {
                final boolean status = "getVipStatus".equals(m.getName());
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            XposedHelpers.setIntField(param.thisObject, "vipStatus", 1);
                            XposedHelpers.setIntField(param.thisObject, "vipDays", 99999);
                            XposedHelpers.setLongField(param.thisObject, "endTime", 999999999L);
                        } catch (Throwable ignored) {
                        }
                        param.setResult(status ? (Object) 1 : (Object) 99999);
                    }
                });
            }
            Class<?> rid = cl.loadClass("com.oppo.cdo.theme.domain.dto.response.ResourceItemDto");
            for (Method m : ms(rid, "getIsVip", "getIsVipAvailable")) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            XposedHelpers.setIntField(param.thisObject, "isVip", 1);
                            XposedHelpers.setIntField(param.thisObject, "isVipAvailable", 1);
                        } catch (Throwable ignored) {
                        }
                        param.setResult(1);
                    }
                });
            }
            log("theme-vip-dto x1", null);
        } catch (Throwable t) {
            log("theme-vip-dto failed", t);
        }
        try {
            Class<?> uim = cl.loadClass("com.nearme.themespace.UserInfoManager");
            for (Method m : uim.getDeclaredMethods()) {
                if ("w".equals(m.getName()) && m.getParameterTypes().length == 0) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(1);
                        }
                    });
                } else if ("D".equals(m.getName()) && m.getParameterTypes().length == 0) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Class<?> vs = cl.loadClass("com.nearme.themespace.account.VipUserStatus");
                                param.setResult(XposedHelpers.getStaticObjectField(vs, "VALID"));
                            } catch (Throwable ignored) {
                            }
                        }
                    });
                }
            }
            log("theme-vip-user x1", null);
        } catch (Throwable ignored) {
        }
        try {
            Class<?> dr = cl.loadClass("com.nearme.themespace.download.mvvm.DownloadRepository");
            for (Method m : dr.getDeclaredMethods()) {
                Class<?>[] ps = m.getParameterTypes();
                if (ps.length != 1 || !ps[0].getName().contains("LocalProductInfo")) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Object info = param.args.length > 0 ? param.args[0] : null;
                        if (info == null) return;
                        try {
                            XposedHelpers.setIntField(info, "mPurchaseStatus", 1);
                            XposedHelpers.setIntField(info, "mResourceVipType", 0);
                            XposedHelpers.setIntField(info, "forceVip", 0);
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> ter = cl.loadClass("com.nearme.themespace.trial.ThemeTrialExpireReceiver");
            for (Method m : ms(ter, "onReceive")) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (param.args.length > 1 && param.args[1] instanceof android.content.Intent) {
                            ((android.content.Intent) param.args[1]).setAction("");
                        }
                    }
                });
            }
        } catch (Throwable ignored) {
        }
        try {
            int n = 0;
            for (Method m : DexLocator.findGlobal(cl, c ->
                    "getPrice".equals(c.name) && c.paramCount == 0
                            && "D".equals(c.returnType))) {
                m.setAccessible(true);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        param.setResult(0.0d);
                    }
                });
                n++;
            }
            log("theme-vip-price x" + n, null);
        } catch (Throwable t) {
            log("theme-vip-price failed", t);
        }
    }

    private static void hookThemeSplashAds(ClassLoader cl) {
        try {
            Class<?> sd = cl.loadClass("com.oppo.cdo.card.theme.dto.SplashDto");
            int n = 0;
            for (Method m : ms(sd, "getAdData", "getImage")) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        param.setResult(null);
                    }
                });
                n++;
            }
            for (Method m : ms(sd, "getStartTime")) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        param.setResult(System.currentTimeMillis() + 86400000L);
                    }
                });
                n++;
            }
            for (Method m : ms(sd, "getEndTime")) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        param.setResult(System.currentTimeMillis() - 86400000L);
                    }
                });
                n++;
            }
            log("theme-splash-ads x" + n, null);
        } catch (Throwable ignored) {
        }
    }

    private static void hookThemeUpgrade(ClassLoader cl) {
        int n = 0;
        try {
            Class<?> us = cl.loadClass("com.heytap.upgrade.UpgradeSDK");
            for (Method m : ms(us, "checkUpgrade")) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(null);
                    }
                });
                n++;
            }
        } catch (Throwable ignored) {
        }
        log("theme-upgrade x" + n, null);
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
