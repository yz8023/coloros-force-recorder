package com.xiaokan.qzgflp;

import android.content.pm.PackageInfo;
import android.os.Build;
import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.util.concurrent.atomic.AtomicBoolean;

/* JADX INFO: loaded from: classes.dex */
public final class HookEntry implements IXposedHookLoadPackage {
    static final String LOG_TAG = "ColorOSForceRecorder";
    static final long SUPPORTED_VERSION = 160005012;
    static final String TARGET = "com.oplus.screenrecorder";
    private static final AtomicBoolean installed = new AtomicBoolean();

    static boolean supported(PackageInfo packageInfo) {
        return packageInfo.getLongVersionCode() == SUPPORTED_VERSION && "16.5.12".equals(packageInfo.versionName);
    }

    public static void log(String str, Throwable th) {
        Log.i(LOG_TAG, str, th);
        XposedBridge.log("[ColorOSForceRecorder] " + str);
        if (th != null) {
            XposedBridge.log(th);
        }
    }

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (Build.VERSION.SDK_INT == 36 && "android".equals(loadPackageParam.packageName)) {
            if (("android".equals(loadPackageParam.processName) || "system_server".equals(loadPackageParam.processName)) && installed.compareAndSet(false, true)) {
                try {
                    SystemCaptureHook.install(loadPackageParam.classLoader);
                } catch (Throwable th) {
                    log("SYSTEM_HOOK_UNAVAILABLE; 保留原捕获源", th);
                }
            }
        }
    }
}
