package com.xiaokan.qzgflp;

import android.content.SharedPreferences;
import android.os.Bundle;

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
        Cfg.setRemote(key -> legacyCfg());
        if ("android".equals(lpparam.packageName)) {
            SecureCaptureHooksLegacy.deoptimizeSystemServer(lpparam.classLoader);
            SecureCaptureHooksLegacy.hookSystemServer(lpparam.classLoader);
            FeatureHooksLegacy.onSystemServer(lpparam.classLoader);
        } else {
            SecureCaptureHooksLegacy.hookPackage(lpparam.packageName, lpparam.classLoader);
            FeatureHooksLegacy.onPackage(lpparam.packageName, lpparam.classLoader);
        }
    }

    private static Bundle legacyCfg() {
        try {
            de.robv.android.xposed.XSharedPreferences p =
                    new de.robv.android.xposed.XSharedPreferences("com.xiaokan.qzgflp", "cfg");
            p.reload();
            Bundle b = new Bundle();
            boolean any = false;
            for (String[] s : FeatureKeys.SWITCHES) {
                if (p.contains(s[0])) {
                    b.putBoolean("b_" + s[0], p.getBoolean(s[0],
                            (Boolean) FeatureKeys.defaultValue(s[0])));
                    any = true;
                }
            }
            for (String ik : new String[]{FeatureKeys.LAYOUT_ROWS, FeatureKeys.LAYOUT_COLS}) {
                if (p.contains(ik)) {
                    b.putInt("i_" + ik, p.getInt(ik,
                            (Integer) FeatureKeys.defaultValue(ik)));
                    any = true;
                }
            }
            if (p.contains(FeatureKeys.TILE_SCRIPT)) {
                b.putString("s_" + FeatureKeys.TILE_SCRIPT,
                        p.getString(FeatureKeys.TILE_SCRIPT,
                                (String) FeatureKeys.defaultValue(FeatureKeys.TILE_SCRIPT)));
                any = true;
            }
            return any ? b : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
