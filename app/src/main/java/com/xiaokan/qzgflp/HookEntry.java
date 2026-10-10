package com.xiaokan.qzgflp;

import android.util.Log;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * Entry point for the libxposed API 102 module.
 *
 * Loads in system_server (removes secure capture restrictions framework-wide)
 * and in scoped screenshot helper packages (ColorOS / Flyme / MIUI).
 */
public final class HookEntry extends XposedModule {

    private static final String TAG = "ForceCapture";

    public HookEntry() {
    }

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        super.onModuleLoaded(param);
        this.log(Log.INFO, TAG, "loaded in " + param.getProcessName());
        try {
            final android.content.SharedPreferences rp = this.getRemotePreferences("cfg");
            Cfg.setRemote(key -> {
                try {
                    android.os.Bundle b = new android.os.Bundle();
                    boolean any = false;
                    for (String[] s : FeatureKeys.SWITCHES) {
                        if (rp.contains(s[0])) {
                            b.putBoolean("b_" + s[0], rp.getBoolean(s[0],
                                    (Boolean) FeatureKeys.defaultValue(s[0])));
                            any = true;
                        }
                    }
                    for (String ik : FeatureKeys.INT_KEYS) {
                        if (rp.contains(ik)) {
                            b.putInt("i_" + ik, rp.getInt(ik,
                                    (Integer) FeatureKeys.defaultValue(ik)));
                            any = true;
                        }
                    }
                    for (String sk : FeatureKeys.STRING_KEYS) {
                        if (rp.contains(sk)) {
                            b.putString("s_" + sk, rp.getString(sk,
                                    (String) FeatureKeys.defaultValue(sk)));
                            any = true;
                        }
                    }
                    return any ? b : null;
                } catch (Throwable t) {
                    return null;
                }
            });
        } catch (Throwable t) {
            this.log(Log.WARN, TAG, "remote prefs unavailable", t);
        }
    }

    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        super.onSystemServerStarting(param);
        SecureCaptureHooks hooks = new SecureCaptureHooks(this);
        hooks.deoptimizeSystemServer(param.getClassLoader());
        hooks.hookSystemServer(param.getClassLoader());
        try {
            new MultiFeatures(this).onSystemServer(param.getClassLoader());
        } catch (Throwable t) {
            this.log(Log.WARN, TAG, "multi-features system server failed", t);
        }
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        new SecureCaptureHooks(this).hookPackage(param.getPackageName(), param.getClassLoader());
        try {
            new MultiFeatures(this).onPackage(param.getPackageName(), param.getClassLoader());
        } catch (Throwable t) {
            this.log(Log.WARN, TAG, "multi-features package failed", t);
        }
    }
}
