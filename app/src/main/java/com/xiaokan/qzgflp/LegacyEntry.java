package com.xiaokan.qzgflp;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Legacy XposedBridge entry (assets/xposed_init), loaded by Xposed-family
 * frameworks without modern libxposed API support. Mirrors HookEntry.
 */
public final class LegacyEntry implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log("ForceCapture: legacy entry loaded in " + lpparam.packageName);
        if ("android".equals(lpparam.packageName)) {
            SecureCaptureHooksLegacy.deoptimizeSystemServer(lpparam.classLoader);
            SecureCaptureHooksLegacy.hookSystemServer(lpparam.classLoader);
        } else {
            SecureCaptureHooksLegacy.hookPackage(lpparam.packageName, lpparam.classLoader);
        }
    }
}
