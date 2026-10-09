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
    }

    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        super.onSystemServerStarting(param);
        SecureCaptureHooks hooks = new SecureCaptureHooks(this);
        hooks.deoptimizeSystemServer(param.getClassLoader());
        hooks.hookSystemServer(param.getClassLoader());
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        if (!param.isFirstPackage()) return;
        new SecureCaptureHooks(this).hookPackage(param.getPackageName(), param.getClassLoader());
    }
}
