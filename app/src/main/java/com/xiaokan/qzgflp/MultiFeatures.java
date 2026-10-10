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
        hookPinVerify72h(cl);
        hookUntrustedTouch(cl);
        hookIgnoreAudioFocus(cl);
        hookRootCheck(cl);
        hookSplitScreen(cl);
        hookPmsChecks(cl);
        hookUninstallBlacklist(cl);
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
                        if (Boolean.FALSE.equals(r)) {
                            if (Cfg.bool(FeatureKeys.DISABLE_VERIFY)) return Boolean.TRUE;
                            if (Cfg.bool(FeatureKeys.ALLOW_SIG_MISMATCH_UPDATE)) {
                                try {
                                    java.util.List<Object> args = chain.getArgs();
                                    Object pkgArg = args.size() > 0 ? args.get(0) : null;
                                    Object parsed = args.size() > 1 ? args.get(1) : null;
                                    if (pkgArg != null && parsed != null) {
                                        Method gp = parsed.getClass().getMethod("getPackageName");
                                        Object name = gp.invoke(parsed);
                                        if (pkgArg.equals(name)) return Boolean.TRUE;
                                    }
                                } catch (Throwable ignored) {
                                }
                            }
                        }
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

    // ── 应用卸载黑名单（OShin xm0 移植）──

    /**
     * 清空 OplusUninstallableConfigManager 的卸载图标隐藏名单（mHideUninstallIcon /
     * mHideUninstallIconSoft 各自内层 mList ArraySet）。类存在于多个 ColorOS 进程，
     * 故每个作用域进程都尝试挂载，缺类时静默跳过（与 OShin 全进程加载行为一致）。
     */
    private void hookUninstallBlacklist(ClassLoader cl) {
        try {
            Class<?> c = cl.loadClass("com.android.server.pm.OplusUninstallableConfigManager");
            int n = 0;
            for (Method m : ms(c, "loadUninstallableConfig")) {
                module.hook(m).intercept(chain -> {
                    if (!Cfg.bool(FeatureKeys.REMOVE_APP_UNINSTALL_BLACKLIST)) return chain.proceed();
                    Object res = chain.proceed();
                    try {
                        clearUninstallSets(chain.getThisObject());
                    } catch (Throwable t) {
                        module.log(Log.WARN, TAG, "uninstall-blacklist clear failed", t);
                    }
                    return res;
                });
                n++;
            }
            if (n > 0) logOk("uninstall-blacklist", n);
        } catch (ClassNotFoundException e) {
            // 当前进程无该类
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "uninstall-blacklist hook failed", t);
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

    // ── 应用内分发 ──

    void onPackage(String pkg, ClassLoader cl) {
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
            } else if (pkg.equals("com.coloros.ocrscanner")) {
                hookFullScreenTranslation(cl);
            } else if (pkg.equals("com.heytap.themestore")) {
                hookThemeStore(cl);
            } else if (pkg.equals("com.coloros.smartsidebar")) {
                hookSmartSidebar(cl);
            }
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "multi-features failed in " + pkg, t);
        }
    }

    // ── 系统服务（OShin 移植）──

    /** 移除 72 小时强验证重排闹钟：rescheduleStrongAuthTimeoutAlarm 置空 */
    private void hookPinVerify72h(ClassLoader cl) {
        int n = 0;
        try {
            Class<?> c = cl.loadClass(
                    "com.android.server.locksettings.LockSettingsStrongAuth");
            for (Method m : ms(c, "rescheduleStrongAuthTimeoutAlarm")) {
                module.hook(m).intercept(chain ->
                        Cfg.bool(FeatureKeys.DISABLE_PIN_72H) ? null : chain.proceed());
                n++;
            }
        } catch (Throwable ignored) {
        }
        logOk("pin-72h", n);
    }

    /** 允许不受信任的触摸：getTouchOcclusionMode 恒返回 2 */
    private void hookUntrustedTouch(ClassLoader cl) {
        int n = 0;
        try {
            Class<?> c = cl.loadClass("com.android.server.wm.WindowState");
            for (Method m : ms(c, "getTouchOcclusionMode")) {
                if (m.getParameterCount() != 0 || m.getReturnType() != int.class) continue;
                module.hook(m).intercept(chain ->
                        Cfg.bool(FeatureKeys.ALLOW_UNTRUSTED_TOUCH) ? 2 : chain.proceed());
                n++;
            }
        } catch (Throwable ignored) {
        }
        logOk("untrusted-touch", n);
    }

    /** 忽略音频焦点：非通话/无障碍/导航用法直接返回已获得焦点 */
    private void hookIgnoreAudioFocus(ClassLoader cl) {
        int n = 0;
        try {
            Class<?> c = cl.loadClass("com.android.server.audio.MediaFocusControl");
            for (Method m : c.getDeclaredMethods()) {
                if (!"requestAudioFocus".equals(m.getName())) continue;
                module.hook(m).intercept(chain -> {
                    if (!Cfg.bool(FeatureKeys.IGNORE_AUDIO_FOCUS)) return chain.proceed();
                    java.util.List<Object> args = chain.getArgs();
                    if (!args.isEmpty() && args.get(0) instanceof android.media.AudioAttributes) {
                        int usage = ((android.media.AudioAttributes) args.get(0)).getUsage();
                        boolean allowed = usage == android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION
                                || usage == android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
                                || usage == android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
                        if (!allowed) return android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
                    }
                    return chain.proceed();
                });
                n++;
            }
        } catch (Throwable ignored) {
        }
        logOk("ignore-audio-focus", n);
    }

    /** 禁用 Root 检测：HeimdallService.isRootEnable 恒 false */
    private void hookRootCheck(ClassLoader cl) {
        int n = repFalse(cl, "com.android.server.oplus.heimdall.HeimdallService",
                FeatureKeys.DISABLE_ROOT_CHECK, "isRootEnable");
        logOk("root-check", n);
    }

    /** 分屏与小窗：FlexibleWindowUtils/FlexibleWindowManagerService 黑白名单与参数覆写 */
    private void hookSplitScreen(ClassLoader cl) {
        try {
            Class<?> fwu = cl.loadClass("com.android.server.wm.FlexibleWindowUtils");
            int n = 0;
            final String rk = FeatureKeys.REMOVE_SMALL_WIN_RESTRICT;
            n += rep(fwu, rk, Boolean.TRUE, "isUnSupportCallerFlexibleWindow");
            n += rep(fwu, rk, Boolean.TRUE, "isSupportFlexibleWindow");
            n += rep(fwu, rk, Boolean.FALSE, "isInFlexibleWindowBlackList",
                    "isInMultiWindowFlexibleBlackList", "isFlexibleTaskInPSBlackList");
            for (Method m : ms(fwu, "getUnSupportRatiosInFlexibleTask")) {
                module.hook(m).intercept(chain ->
                        Cfg.bool(rk) ? "" : chain.proceed());
                n++;
            }
            logOk("small-window", n);
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "small-window hook failed", t);
        }
        try {
            Class<?> fwm = cl.loadClass("com.android.server.wm.FlexibleWindowManagerService");
            int n = 0;
            for (Method m : fwm.getDeclaredMethods()) {
                if (m.getParameterCount() != 1 || m.getReturnType() != int.class) continue;
                String name = m.getName();
                String key;
                if ("getMaxWinNum".equals(name)) key = FeatureKeys.MAX_SMALL_WINDOWS;
                else if ("getCornerRadius".equals(name)) key = FeatureKeys.SMALL_WIN_CORNER_RADIUS;
                else if ("getShadowRadiusFocused".equals(name)) key = FeatureKeys.SMALL_WIN_FOCUSED_SHADOW;
                else if ("getShadowRadiusUnfocused".equals(name)) key = FeatureKeys.SMALL_WIN_UNFOCUSED_SHADOW;
                else continue;
                module.hook(m).intercept(chain -> {
                    int v = Cfg.intv(key);
                    return v != -1 ? v : chain.proceed();
                });
                n++;
            }
            n += rep(fwm, FeatureKeys.FORCE_MULTI_WINDOW_MODE, Boolean.TRUE,
                    "isSupportMultiMode");
            logOk("small-window-svc", n);
        } catch (Throwable ignored) {
        }
    }

    // ── 核心破解扩充（OShin PMS 检查移植）──

    private void hookPmsChecks(ClassLoader cl) {
        hookDowngradeUtils(cl);
        hookJarVerifier(cl);
        hookArscCheck(cl);
        hookMinSigVersion(cl);
        hookSigMismatchUpdate(cl);
        hookSplitSigMismatch(cl);
        hookHiddenApi(cl);
        hookSharedUid(cl);
        hookInstallVerification(cl);
        hookV1SigErrors(cl);
        hookPmsCommand(cl);
    }

    /** PackageManagerServiceUtils.checkDowngrade（另一处降级检查入口） */
    private void hookDowngradeUtils(ClassLoader cl) {
        int n = 0;
        try {
            Class<?> u = cl.loadClass("com.android.server.pm.PackageManagerServiceUtils");
            for (Method m : ms(u, "checkDowngrade")) {
                module.hook(m).intercept(chain ->
                        Cfg.bool(FeatureKeys.ALLOW_DOWNGRADE) ? null : chain.proceed());
                n++;
            }
        } catch (Throwable ignored) {
        }
        if (n > 0) logOk("downgrade-utils", n);
    }

    /** StrictJarVerifier 校验置真 + 关闭签名方案回滚保护 */
    private void hookJarVerifier(ClassLoader cl) {
        try {
            Class<?> sjv = cl.loadClass("android.util.jar.StrictJarVerifier");
            final String k = FeatureKeys.DISABLE_JAR_VERIFIER;
            int n = 0;
            for (Method m : ms(sjv, "verifyMessageDigest", "verify")) {
                if (m.getReturnType() != boolean.class) continue;
                module.hook(m).intercept(chain ->
                        Cfg.bool(k) ? Boolean.TRUE : chain.proceed());
                n++;
            }
            for (java.lang.reflect.Constructor<?> ctor : sjv.getDeclaredConstructors()) {
                module.hook(ctor).intercept(chain -> {
                    Object r = chain.proceed();
                    if (Cfg.bool(k)) {
                        Field f = findField(sjv, "signatureSchemeRollbackProtectionsEnforced");
                        if (f != null) f.setBoolean(chain.getThisObject(), false);
                    }
                    return r;
                });
            }
            if (n > 0) logOk("jar-verifier", n);
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "jar-verifier failed", t);
        }
        try {
            Class<?> md = Class.forName("java.security.MessageDigest");
            for (Method m : ms(md, "isEqual")) {
                module.hook(m).intercept(chain ->
                        Cfg.bool(FeatureKeys.DISABLE_MESSAGE_DIGEST) ? Boolean.TRUE : chain.proceed());
            }
        } catch (Throwable ignored) {
        }
    }

    /** 绕过 resources.arsc 未压缩检查 */
    private void hookArscCheck(ClassLoader cl) {
        int n = 0;
        try {
            Class<?> am = Class.forName("android.content.res.AssetManager");
            for (Method m : ms(am, "containsAllocatedTable")) {
                module.hook(m).intercept(chain ->
                        Cfg.bool(FeatureKeys.BYPASS_ARSC_CHECK) ? Boolean.FALSE : chain.proceed());
                n++;
            }
        } catch (Throwable ignored) {
        }
        if (n > 0) logOk("arsc-check", n);
    }

    /** 绕过最低签名方案版本：目标 SDK 阈值归零 + 跳过断言异常 */
    private void hookMinSigVersion(ClassLoader cl) {
        try {
            Class<?> asv = cl.loadClass("android.util.apk.ApkSignatureVerifier");
            int n = 0;
            for (Method m : ms(asv, "getMinimumSignatureSchemeVersionForTargetSdk")) {
                if (m.getParameterCount() != 1 || m.getReturnType() != int.class) continue;
                module.hook(m).intercept(chain ->
                        Cfg.bool(FeatureKeys.BYPASS_MIN_SIG_VERSION) ? 0 : chain.proceed());
                n++;
            }
            Class<?> spu = cl.loadClass("com.android.server.pm.ScanPackageUtils");
            for (Method m : ms(spu, "assertMinSignatureSchemeIsValid")) {
                module.hook(m).intercept(chain -> {
                    try {
                        return chain.proceed();
                    } catch (Throwable t) {
                        if (Cfg.bool(FeatureKeys.BYPASS_MIN_SIG_VERSION)) return null;
                        throw t;
                    }
                });
                n++;
            }
            if (n > 0) logOk("min-sig-version", n);
        } catch (Throwable ignored) {
        }
    }

    /** 覆盖安装签名不一致：checkCapability 放行 + KeySet 检查绕过 */
    private void hookSigMismatchUpdate(ClassLoader cl) {
        try {
            Class<?> sd = cl.loadClass("android.content.pm.SigningDetails");
            int n = 0;
            for (Method m : ms(sd, "checkCapability")) {
                if (m.getParameterCount() != 2) continue;
                module.hook(m).intercept(chain -> {
                    if (!Cfg.bool(FeatureKeys.ALLOW_SIG_MISMATCH_UPDATE)) return chain.proceed();
                    java.util.List<Object> args = chain.getArgs();
                    if (args.size() >= 2 && args.get(1) instanceof Integer) {
                        int cap = (Integer) args.get(1);
                        if (cap != 4 && cap != 16) return Boolean.TRUE;
                    }
                    return chain.proceed();
                });
                n++;
            }
            Class<?> ksm = cl.loadClass("com.android.server.pm.KeySetManagerService");
            for (Method m : ms(ksm, "shouldCheckUpgradeKeySetLocked")) {
                module.hook(m).intercept(chain -> {
                    Object r = chain.proceed();
                    if (!Cfg.bool(FeatureKeys.ALLOW_SIG_MISMATCH_UPDATE)) return r;
                    boolean fromPrepare = false;
                    for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
                        if (e.getMethodName().startsWith("preparePackage")) {
                            fromPrepare = true;
                            break;
                        }
                    }
                    KS_BYPASS_KEYSET.set(fromPrepare);
                    return fromPrepare ? Boolean.TRUE : r;
                });
                n++;
            }
            for (Method m : ms(ksm, "checkUpgradeKeySetLocked")) {
                module.hook(m).intercept(chain -> {
                    Object r = chain.proceed();
                    if (Cfg.bool(FeatureKeys.ALLOW_SIG_MISMATCH_UPDATE)
                            && Boolean.TRUE.equals(KS_BYPASS_KEYSET.get())) return Boolean.TRUE;
                    return r;
                });
                n++;
            }
            if (n > 0) logOk("sig-mismatch-update", n);
        } catch (Throwable ignored) {
        }
    }

    /** Split APK 签名不一致放行 */
    private void hookSplitSigMismatch(ClassLoader cl) {
        int n = repTrue(cl, "android.content.pm.SigningDetails",
                FeatureKeys.ALLOW_SPLIT_SIG_MISMATCH, "signaturesMatchExactly");
        if (n > 0) logOk("split-sig", n);
    }

    /** 系统应用隐藏 API 白名单 */
    private void hookHiddenApi(ClassLoader cl) {
        int n = 0;
        try {
            Class<?> ai = android.content.pm.ApplicationInfo.class;
            for (Method m : ms(ai, "isPackageWhitelistedForHiddenApis")) {
                if (m.getReturnType() != boolean.class) continue;
                final boolean isStatic = java.lang.reflect.Modifier.isStatic(m.getModifiers());
                module.hook(m).intercept(chain -> {
                    if (!Cfg.bool(FeatureKeys.ALLOW_HIDDEN_API)) return chain.proceed();
                    Object info = isStatic ? null : chain.getThisObject();
                    if (info instanceof android.content.pm.ApplicationInfo) {
                        int flags = ((android.content.pm.ApplicationInfo) info).flags;
                        if ((flags & 1) != 0 || (flags & 128) != 0) return Boolean.TRUE;
                    }
                    return chain.proceed();
                });
                n++;
            }
        } catch (Throwable ignored) {
        }
        if (n > 0) logOk("hidden-api", n);
    }

    /** 非系统预装共享 UID 放行 */
    private void hookSharedUid(ClassLoader cl) {
        if (!Cfg.bool(FeatureKeys.ALLOW_NONSYSTEM_SHARED_UID)) return;
        try {
            Class<?> rpu = cl.loadClass("com.android.server.pm.ReconcilePackageUtils");
            Field f = findField(rpu, "ALLOW_NON_PRELOADS_SYSTEM_SHAREDUIDS");
            if (f != null && f.getType() == boolean.class) {
                f.setAccessible(true);
                f.setBoolean(null, true);
                logOk("shared-uid", 1);
            }
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "shared-uid failed", t);
        }
    }

    /** 安装验证关闭：isVerificationEnabled（与跳过签名验证共用效果） */
    private void hookInstallVerification(ClassLoader cl) {
        int n = 0;
        try {
            Class<?> vs = cl.loadClass("com.android.server.pm.VerifyingSession");
            for (Method m : ms(vs, "isVerificationEnabled")) {
                if (m.getReturnType() != boolean.class) continue;
                module.hook(m).intercept(chain -> {
                    if (Cfg.bool(FeatureKeys.DISABLE_INSTALL_VERIFICATION)
                            || Cfg.bool(FeatureKeys.DISABLE_VERIFY)) return Boolean.FALSE;
                    return chain.proceed();
                });
                n++;
            }
        } catch (Throwable ignored) {
        }
        if (n > 0) logOk("install-verification", n);
    }

    private static final java.util.concurrent.atomic.AtomicReference<Object> PMS_INSTANCE =
            new java.util.concurrent.atomic.AtomicReference<>();
    private static final ThreadLocal<Boolean> KS_BYPASS_KEYSET = new ThreadLocal<>();

    /** V1 签名错误兜底：抛出 error=-103 时替换为 CorePatch 证书签名 */
    private void hookV1SigErrors(ClassLoader cl) {
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
                module.hook(m).intercept(chain -> {
                    try {
                        return chain.proceed();
                    } catch (Throwable t) {
                        if (!Cfg.bool(FeatureKeys.BYPASS_V1_SIG_ERRORS)) throw t;
                        if (!isV1Error(t, ppeClass, errField)) throw t;
                        Object sd = newSigningDetailsV1(cl);
                        if (sd == null) throw t;
                        return sd;
                    }
                });
                n++;
            }
            if (n > 0) logOk("v1-sig-errors", n);
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "v1-sig-errors failed", t);
        }
    }

    private static boolean isV1Error(Throwable t, Class<?> ppe, Field errField) {
        if (ppe == null || errField == null) return false;
        Throwable cur = t;
        for (int i = 0; i < 3 && cur != null; i++) {
            if (cur.getClass() == ppe) {
                try {
                    if (errField.getInt(cur) == -103) return true;
                } catch (Throwable ignored) {
                }
            }
            cur = cur.getCause();
        }
        return false;
    }

    static final String COREPATCH_CERT =
            "308203c6308202aea003020102021426d148b7c65944abcf3a683b4c3dd3b139c4ec85300d06092a864886f70d01010b05003074310b3009060355040613025553311330110603550408130a43616c69666f726e6961311630140603550407130d4d6f756e7461696e205669657731143012060355040a130b476f6f676c6520496e632e3110300e060355040b1307416e64726f69643110300e06035504031307416e64726f6964301e170d3139303130323138353233385a170d3439303130323138353233385a3074310b3009060355040613025553311330110603550408130a43616c69666f726e6961311630140603550407130d4d6f756e7461696e205669657731143012060355040a130b476f6f676c6520496e632e3110300e060355040b1307416e64726f69643110300e06035504031307416e64726f696430820122300d06092a864886f70d01010105000382010f003082010a028201010087fcde48d9beaeba37b733a397ae586fb42b6c3f4ce758dc3ef1327754a049b58f738664ece587994f1c6362f98c9be5fe82c72177260c390781f74a10a8a6f05a6b5ca0c7c5826e15526d8d7f0e74f2170064896b0cf32634a388e1a975ed6bab10744d9b371cba85069834bf098f1de0205cdee8e715759d302a64d248067a15b9beea11b61305e367ac71b1a898bf2eec7342109c9c5813a579d8a1b3e6a3fe290ea82e27fdba748a663f73cca5807cff1e4ad6f3ccca7c02945926a47279d1159599d4ecf01c9d0b62e385c6320a7a1e4ddc9833f237e814b34024b9ad108a5b00786ea15593a50ca7987cbbdc203c096eed5ff4bf8a63d27d33ecc963990203010001a350304e300c0603551d13040530030101ff301d0603551d0e04160414a361efb002034d596c3a60ad7b0332012a16aee3301f0603551d23041830168014a361efb002034d596c3a60ad7b0332012a16aee3300d06092a864886f70d01010b0500038201010022ccb684a7a8706f3ee7c81d6750fd662bf39f84805862040b625ddf378eeefae5a4f1f283deea61a3c7f8e7963fd745415153a531912b82b596e7409287ba26fb80cedba18f22ae3d987466e1fdd88e440402b2ea2819db5392cadee501350e81b8791675ea1a2ed7ef7696dff273f13fb742bb9625fa12ce9c2cb0b7b3d94b21792f1252b1d9e4f7012cb341b62ff556e6864b40927e942065d8f0f51273fcda979b8832dd5562c79acf719de6be5aee2a85f89265b071bf38339e2d31041bc501d5e0c034ab1cd9c64353b10ee70b49274093d13f733eb9d3543140814c72f8e003f301c7a00b1872cc008ad55e26df2e8f07441002c4bcb7dc746745f0db";

    private static Object newSigningDetailsV1(ClassLoader cl) {
        try {
            Class<?> sigC = cl.loadClass("android.content.pm.Signature");
            Object sig = sigC.getConstructor(String.class).newInstance(COREPATCH_CERT);
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

    /** adb shell pm pms 调试命令：转储包/共享用户签名信息 */
    private void hookPmsCommand(ClassLoader cl) {
        if (!Cfg.bool(FeatureKeys.PMS_COMMAND)) return;
        try {
            Class<?> pms = cl.loadClass("com.android.server.pm.PackageManagerService");
            for (java.lang.reflect.Constructor<?> ctor : pms.getDeclaredConstructors()) {
                module.hook(ctor).intercept(chain -> {
                    Object r = chain.proceed();
                    PMS_INSTANCE.set(chain.getThisObject());
                    return r;
                });
            }
            Class<?> pmsc = cl.loadClass("com.android.server.pm.PackageManagerShellCommand");
            for (Method m : ms(pmsc, "onCommand")) {
                if (m.getParameterCount() != 1) continue;
                module.hook(m).intercept(chain -> {
                    java.util.List<Object> args = chain.getArgs();
                    Object cmd = args.isEmpty() ? null : args.get(0);
                    if (!"pms".equals(cmd)) return chain.proceed();
                    Object localPms = PMS_INSTANCE.get();
                    if (localPms == null) return 0;
                    Object shell = chain.getThisObject();
                    try {
                        Object pw = shell.getClass().getMethod("getOutPrintWriter").invoke(shell);
                        String type = (String) shell.getClass()
                                .getMethod("getNextArgRequired").invoke(shell);
                        Object settings = findField(localPms.getClass(), "mSettings") == null
                                ? null : findField(localPms.getClass(), "mSettings").get(localPms);
                        if (settings == null) {
                            pw.getClass().getMethod("println", String.class)
                                    .invoke(pw, "Error: Could not get mSettings from PMS.");
                            return 0;
                        }
                        if ("p".equals(type) || "package".equals(type)) {
                            String name = (String) shell.getClass()
                                    .getMethod("getNextArgRequired").invoke(shell);
                            Object ps = null;
                            for (Method gm : settings.getClass().getDeclaredMethods()) {
                                if ("getPackageLPr".equals(gm.getName())
                                        && gm.getParameterCount() == 1
                                        && gm.getParameterTypes()[0] == String.class) {
                                    gm.setAccessible(true);
                                    ps = gm.invoke(settings, name);
                                    break;
                                }
                            }
                            if (ps != null) dumpSettingSignatures(cl, ps, pw);
                            else pw.getClass().getMethod("println", String.class)
                                    .invoke(pw, "no package " + name + " found");
                        } else if ("su".equals(type) || "shareduser".equals(type)) {
                            String name = (String) shell.getClass()
                                    .getMethod("getNextArgRequired").invoke(shell);
                            Field suField = findField(settings.getClass(), "mSharedUsers");
                            Object su = suField == null ? null
                                    : suField.get(settings);
                            Object target = null;
                            if (su instanceof java.util.Map) {
                                target = ((java.util.Map<?, ?>) su).get(name);
                            }
                            if (target != null) dumpSettingSignatures(cl, target, pw);
                            else pw.getClass().getMethod("println", String.class)
                                    .invoke(pw, "no shared user " + name + " found");
                        } else {
                            pw.getClass().getMethod("println", String.class)
                                    .invoke(pw, "usage: <p|package|su|shareduser> <name>");
                        }
                    } catch (Throwable t) {
                        module.log(Log.WARN, TAG, "pms command failed", t);
                    }
                    return 0;
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private static void dumpSettingSignatures(ClassLoader cl, Object setting, Object pw) throws Exception {
        java.io.PrintWriter w = pw instanceof java.io.PrintWriter
                ? (java.io.PrintWriter) pw : null;
        if (w == null) return;
        Object sigsObj = findField(setting.getClass(), "signatures") != null
                ? findField(setting.getClass(), "signatures").get(setting) : null;
        Object signingDetails = sigsObj;
        if (signingDetails != null) {
            Field sdf = findField(signingDetails.getClass(), "mSigningDetails");
            if (sdf != null) signingDetails = sdf.get(signingDetails);
        }
        w.println("signing for " + setting);
        if (signingDetails == null) return;
        Object[] sigs = null;
        try {
            sigs = (Object[]) signingDetails.getClass().getMethod("getSignatures").invoke(signingDetails);
        } catch (Throwable ignored) {
        }
        if (sigs == null) {
            w.println("Could not get signatures.");
            return;
        }
        for (int i = 0; i < sigs.length; i++) {
            Object s = sigs[i];
            String hex = null;
            try {
                hex = (String) s.getClass().getMethod("toCharsString").invoke(s);
            } catch (Throwable ignored) {
            }
            w.println((i + 1) + ": " + hex);
        }
    }

    // ── 小布扫一扫（OShin 移植）──

    /** 全屏翻译：绕过不支持应用判定 */
    private void hookFullScreenTranslation(ClassLoader cl) {
        if (!Cfg.bool(FeatureKeys.FULL_SCREEN_TRANSLATION)) return;
        int n = 0;
        try {
            Class<?> root = cl.loadClass(
                    "com.oplus.scanner.screentrans.ui.ScreenTranslationRootView");
            for (Method m : root.getDeclaredMethods()) {
                if (!"s0".equals(m.getName()) || m.getParameterCount() != 1
                        || m.getReturnType() != boolean.class) continue;
                module.hook(m).intercept(chain -> Boolean.FALSE);
                n++;
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> inner = cl.loadClass(
                    "com.oplus.scanner.screentrans.ui.ScreenTranslationRootView$onNotSupportApp$1");
            for (Method m : inner.getDeclaredMethods()) {
                if (!"invokeSuspend".equals(m.getName())
                        || m.getParameterCount() != 1) continue;
                module.hook(m).intercept(chain -> Boolean.FALSE);
                n++;
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> cap = cl.loadClass(
                    "com.oplus.scanner.screentrans.ui.ScreenTranslationToolCapsule");
            for (Method m : cap.getDeclaredMethods()) {
                if (m.getParameterCount() != 0 || m.getReturnType() != boolean.class) continue;
                module.hook(m).intercept(chain -> Boolean.TRUE);
                n++;
            }
        } catch (Throwable ignored) {
        }
        logOk("full-screen-translation", n);
    }

    // ── 主题商店（OShin 移植）──

    private void hookThemeStore(ClassLoader cl) {
        if (Cfg.bool(FeatureKeys.THEME_UNLOCK_VIP)) hookThemeVip(cl);
        if (Cfg.bool(FeatureKeys.THEME_REMOVE_SPLASH_ADS)) hookThemeSplashAds(cl);
        if (Cfg.bool(FeatureKeys.THEME_REMOVE_UPGRADE)) hookThemeUpgrade(cl);
    }

    private int repDto(ClassLoader cl, String cn, String method, Object val) {
        int n = 0;
        try {
            Class<?> c = cl.loadClass(cn);
            for (Method m : ms(c, method)) {
                module.hook(m).intercept(chain -> val);
                n++;
            }
        } catch (Throwable ignored) {
        }
        return n;
    }

    /** 解锁 VIP：DTO 字段改写 + UserInfoManager 状态伪造 + 价格归零 */
    private void hookThemeVip(ClassLoader cl) {
        try {
            Class<?> wpr = cl.loadClass("com.oppo.cdo.card.theme.dto.page.WeatherPageResponseDto");
            for (Method m : ms(wpr, "getVipStatus")) {
                module.hook(m).intercept(chain -> {
                    Field f = findField(wpr, "vipStatus");
                    if (f != null) f.setInt(chain.getThisObject(), 1);
                    return 1;
                });
            }
            Class<?> vud = cl.loadClass("com.oppo.cdo.card.theme.dto.vip.VipUserDto");
            for (Method m : ms(vud, "getVipStatus", "getVipDays")) {
                module.hook(m).intercept(chain -> {
                    Object self = chain.getThisObject();
                    Field fs = findField(vud, "vipStatus");
                    if (fs != null) fs.setInt(self, 1);
                    Field fd = findField(vud, "vipDays");
                    if (fd != null) fd.setInt(self, 99999);
                    Field fe = findField(vud, "endTime");
                    if (fe != null) fe.setLong(self, 999999999L);
                    return "getVipStatus".equals(m.getName()) ? 1 : 99999;
                });
            }
            Class<?> rid = cl.loadClass("com.oppo.cdo.theme.domain.dto.response.ResourceItemDto");
            for (Method m : ms(rid, "getIsVip", "getIsVipAvailable")) {
                module.hook(m).intercept(chain -> {
                    Object self = chain.getThisObject();
                    Field f1 = findField(rid, "isVip");
                    if (f1 != null) f1.setInt(self, 1);
                    Field f2 = findField(rid, "isVipAvailable");
                    if (f2 != null) f2.setInt(self, 1);
                    return 1;
                });
            }
            logOk("theme-vip-dto", 1);
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "theme-vip-dto failed", t);
        }
        try {
            Class<?> uim = cl.loadClass("com.nearme.themespace.UserInfoManager");
            for (Method m : uim.getDeclaredMethods()) {
                if (!"w".equals(m.getName()) || m.getParameterCount() != 0) continue;
                module.hook(m).intercept(chain -> 1);
            }
            for (Method m : uim.getDeclaredMethods()) {
                if (!"D".equals(m.getName()) || m.getParameterCount() != 0) continue;
                module.hook(m).intercept(chain -> {
                    Object r = chain.proceed();
                    try {
                        Class<?> vs = cl.loadClass("com.nearme.themespace.account.VipUserStatus");
                        Field valid = findField(vs, "VALID");
                        if (valid != null && r != null) return valid.get(r);
                    } catch (Throwable ignored) {
                    }
                    return r;
                });
            }
            logOk("theme-vip-user", 1);
        } catch (Throwable ignored) {
        }
        try {
            Class<?> dr = cl.loadClass("com.nearme.themespace.download.mvvm.DownloadRepository");
            for (Method m : dr.getDeclaredMethods()) {
                Class<?>[] ps = m.getParameterTypes();
                if (ps.length != 1 || !ps[0].getName().contains("LocalProductInfo")) continue;
                module.hook(m).intercept(chain -> {
                    java.util.List<Object> args = chain.getArgs();
                    Object info = args.isEmpty() ? null : args.get(0);
                    if (info != null) {
                        Field f1 = findField(info.getClass(), "mPurchaseStatus");
                        if (f1 != null) f1.setInt(info, 1);
                        Field f2 = findField(info.getClass(), "mResourceVipType");
                        if (f2 != null) f2.setInt(info, 0);
                        Field f3 = findField(info.getClass(), "forceVip");
                        if (f3 != null) f3.setInt(info, 0);
                    }
                    return chain.proceed();
                });
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> ter = cl.loadClass("com.nearme.themespace.trial.ThemeTrialExpireReceiver");
            for (Method m : ms(ter, "onReceive")) {
                module.hook(m).intercept(chain -> {
                    java.util.List<Object> args = chain.getArgs();
                    if (args.size() > 1 && args.get(1) instanceof android.content.Intent) {
                        ((android.content.Intent) args.get(1)).setAction("");
                    }
                    return chain.proceed();
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
                module.hook(m).intercept(chain -> 0.0d);
                n++;
            }
            logOk("theme-vip-price", n);
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "theme-vip-price failed", t);
        }
    }

    /** 移除开屏广告：SplashDto 广告数据置空 + 时间窗错开 */
    private void hookThemeSplashAds(ClassLoader cl) {
        try {
            Class<?> sd = cl.loadClass("com.oppo.cdo.card.theme.dto.SplashDto");
            int n = 0;
            for (Method m : ms(sd, "getAdData", "getImage")) {
                module.hook(m).intercept(chain -> null);
                n++;
            }
            for (Method m : ms(sd, "getStartTime")) {
                module.hook(m).intercept(chain -> System.currentTimeMillis() + 86400000L);
                n++;
            }
            for (Method m : ms(sd, "getEndTime")) {
                module.hook(m).intercept(chain -> System.currentTimeMillis() - 86400000L);
                n++;
            }
            logOk("theme-splash-ads", n);
        } catch (Throwable ignored) {
        }
    }

    /** 移除升级弹窗：UpgradeSDK.checkUpgrade 置空 */
    private void hookThemeUpgrade(ClassLoader cl) {
        int n = 0;
        try {
            Class<?> us = cl.loadClass("com.heytap.upgrade.UpgradeSDK");
            for (Method m : ms(us, "checkUpgrade")) {
                module.hook(m).intercept(chain -> null);
                n++;
            }
        } catch (Throwable ignored) {
        }
        logOk("theme-upgrade", n);
    }

    // ── 智慧侧边栏（OShin e02 移植）──

    private void hookSmartSidebar(ClassLoader cl) {
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
            n += repCanAdd(upv, FeatureKeys.REMOVE_APP_ADD_LIMIT);
            for (Method m : ms(upv, "performAdd")) {
                if (m.getParameterCount() != 1
                        || !m.getParameterTypes()[0].getName().endsWith("AppLabelData")) continue;
                module.hook(m).intercept(chain -> {
                    if (!Cfg.bool(FeatureKeys.REMOVE_APP_ADD_LIMIT)) return chain.proceed();
                    try {
                        Object self = chain.getThisObject();
                        if (self != null) {
                            Object list = fieldValue(self, "mPanelData");
                            Object item = fieldValue(self, "mEditOccupancyData");
                            if (list instanceof java.util.List && item != null) {
                                ((java.util.List<Object>) list).add(item);
                            }
                        }
                    } catch (Throwable t) {
                        module.log(Log.WARN, TAG, "sidebar performAdd failed", t);
                    }
                    return chain.proceed();
                });
                n++;
            }
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "smartsidebar hook failed", t);
        }
        try {
            Class<?> pmv = cl.loadClass("com.oplus.smartsidebar.panelview.edgepanel.PanelMainView");
            n += repCanAdd(pmv, FeatureKeys.REMOVE_APP_ADD_LIMIT);
        } catch (Throwable ignored) {
        }
        logOk("smartsidebar", n);
    }

    private int repCanAdd(Class<?> c, final String key) {
        int n = 0;
        for (Method m : ms(c, "canAdd")) {
            if (m.getParameterCount() != 1
                    || m.getParameterTypes()[0] != String.class
                    || m.getReturnType() != boolean.class) continue;
            module.hook(m).intercept(chain ->
                    Cfg.bool(key) ? Boolean.TRUE : chain.proceed());
            n++;
        }
        return n;
    }

    private static Object fieldValue(Object self, String name) throws Exception {
        Field f = findField(self.getClass(), name);
        return f == null ? null : f.get(self);
    }

    /** 写静态 int 字段；final 字段回退 Unsafe（ART 上可用） */
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
