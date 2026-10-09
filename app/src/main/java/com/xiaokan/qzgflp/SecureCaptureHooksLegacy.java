package com.xiaokan.qzgflp;

import android.hardware.display.DisplayManager;
import android.os.Build;
import android.view.SurfaceControl;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Legacy XposedBridge port of SecureCaptureHooks (LSPosed DisableFlagSecure).
 * Same targets, same semantics; uses XposedBridge.hookMethod directly.
 */
final class SecureCaptureHooksLegacy {
    private static final String TAG = "ForceCapture";

    private static final String SYSTEMUI = "com.android.systemui";
    private static final String OPLUS_APPPLATFORM = "com.oplus.appplatform";
    private static final String OPLUS_SCREENSHOT = "com.oplus.screenshot";
    private static final String FLYME_SYSTEMUIEX = "com.flyme.systemuiex";
    private static final String MIUI_SCREENSHOT = "com.miui.screenshot";

    private SecureCaptureHooksLegacy() {
    }

    private static void log(String msg, Throwable t) {
        XposedBridge.log(TAG + ": " + msg);
        if (t != null) {
            XposedBridge.log(t);
        }
    }

    private static void deoptimize(Method method) {
        try {
            XposedBridge.class.getMethod("deoptimizeMethod", Member.class).invoke(null, method);
        } catch (Throwable ignored) {
        }
    }

    private static void deoptimizeMethods(Class<?> clazz, String... names) {
        for (Method method : clazz.getDeclaredMethods()) {
            for (String name : names) {
                if (method.getName().equals(name)) {
                    deoptimize(method);
                }
            }
        }
    }

    static void deoptimizeSystemServer(ClassLoader cl) {
        try {
            deoptimizeMethods(cl.loadClass("com.android.server.wm.WindowStateAnimator"), "createSurfaceLocked");
            deoptimizeMethods(cl.loadClass("com.android.server.wm.WindowManagerService"), "relayoutWindow");
        } catch (Throwable t) {
            log("deoptimize system server failed", t);
        }
        for (int i = 0; i < 20; i++) {
            try {
                Class<?> lambda = cl.loadClass("com.android.server.wm.RootWindowContainer$$ExternalSyntheticLambda" + i);
                if (java.util.function.BiConsumer.class.isAssignableFrom(lambda)) {
                    deoptimizeMethods(lambda, "accept");
                }
            } catch (ClassNotFoundException ignored) {
            }
            try {
                Class<?> inner = cl.loadClass("com.android.server.wm.DisplayContent$" + i);
                if (java.util.function.BiPredicate.class.isAssignableFrom(inner)) {
                    deoptimizeMethods(inner, "test");
                }
            } catch (ClassNotFoundException ignored) {
            }
        }
    }

    static void hookSystemServer(ClassLoader cl) {
        if (Build.VERSION.SDK_INT >= 35) {
            try {
                hookWindowManagerService(cl);
            } catch (Throwable t) {
                log("hook WindowManagerService failed", t);
            }
        }

        if (Build.VERSION.SDK_INT >= 34) {
            try {
                hookActivityTaskManagerService(cl);
            } catch (Throwable t) {
                log("hook ActivityTaskManagerService failed", t);
            }
            try {
                hookHyperOS(cl);
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable t) {
                log("hook HyperOS failed", t);
            }
        }

        try {
            hookScreenCapture(cl);
        } catch (Throwable t) {
            log("hook ScreenCapture failed", t);
        }

        if (Build.VERSION.SDK_INT < 34) {
            try {
                hookActivityManagerService(cl);
            } catch (Throwable t) {
                log("hook ActivityManagerService failed", t);
            }
        }

        try {
            hookDisplayControl(cl);
        } catch (Throwable t) {
            log("hook DisplayControl failed", t);
        }

        try {
            hookVirtualDisplayAdapter(cl);
        } catch (Throwable t) {
            log("hook VirtualDisplayAdapter failed", t);
        }

        try {
            hookScreenshotHardwareBuffer(cl);
        } catch (Throwable t) {
            if (!(t instanceof ClassNotFoundException)) {
                log("hook ScreenshotHardwareBuffer failed", t);
            }
        }

        try {
            hookOneUI(cl);
        } catch (Throwable t) {
            if (!(t instanceof ClassNotFoundException)) {
                log("hook OneUI failed", t);
            }
        }

        try {
            hookWindowState(cl);
        } catch (Throwable t) {
            log("hook WindowState failed", t);
        }

        try {
            hookOplusLongshot(cl);
        } catch (Throwable t) {
            if (!(t instanceof ClassNotFoundException)) {
                log("hook Oplus failed", t);
            }
        }
    }

    static void hookPackage(String packageName, ClassLoader cl) {
        if (OPLUS_SCREENSHOT.equals(packageName)) {
            if (Build.VERSION.SDK_INT >= 35) {
                try {
                    hookOplusScreenCapture(cl);
                } catch (Throwable t) {
                    if (!(t instanceof ClassNotFoundException)) {
                        log("hook OplusScreenCapture failed", t);
                    }
                }
            }
            try {
                hookScreenshotHardwareBuffer(cl);
            } catch (Throwable t) {
                if (!(t instanceof ClassNotFoundException)) {
                    log("hook ScreenshotHardwareBuffer failed", t);
                }
            }
        } else if (FLYME_SYSTEMUIEX.equals(packageName) || OPLUS_APPPLATFORM.equals(packageName)) {
            try {
                hookScreenshotHardwareBuffer(cl);
            } catch (Throwable t) {
                if (!(t instanceof ClassNotFoundException)) {
                    log("hook ScreenshotHardwareBuffer failed", t);
                }
            }
        } else if (SYSTEMUI.equals(packageName) || MIUI_SCREENSHOT.equals(packageName)) {
            if (Build.VERSION.SDK_INT < 34) {
                try {
                    hookScreenCapture(cl);
                } catch (Throwable t) {
                    log("hook ScreenCapture failed", t);
                }
            }
        } else {
            try {
                hookSurfaceViewSecure(cl);
            } catch (Throwable t) {
                log("hook SurfaceView.setSecure failed", t);
            }
        }
    }

    private static void hookSurfaceViewSecure(ClassLoader cl) throws ClassNotFoundException, NoSuchMethodException {
        Class<?> surfaceViewClazz = cl.loadClass("android.view.SurfaceView");
        Method method = surfaceViewClazz.getDeclaredMethod("setSecure", boolean.class);
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.args[0] = Boolean.FALSE;
            }
        });
    }

    private static void hookWindowState(ClassLoader cl) throws ClassNotFoundException, NoSuchMethodException {
        Class<?> windowStateClazz = cl.loadClass("com.android.server.wm.WindowState");
        ClassLoader systemServerCl = windowStateClazz.getClassLoader();
        Method isSecureLocked = windowStateClazz.getDeclaredMethod("isSecureLocked");
        XposedBridge.hookMethod(isSecureLocked, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (Build.VERSION.SDK_INT >= 34) {
                    java.lang.StackWalker walker = java.lang.StackWalker.getInstance(
                            java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE);
                    boolean match = walker.walk(frames -> frames.anyMatch(frame ->
                            frame.getDeclaringClass() != null
                                    && frame.getDeclaringClass().getClassLoader() == systemServerCl
                                    && ("setInitialSurfaceControlProperties".equals(frame.getMethodName())
                                    || "createSurfaceLocked".equals(frame.getMethodName()))));
                    if (match) return;
                } else {
                    for (StackTraceElement frame : new Throwable().getStackTrace()) {
                        String name = frame.getMethodName();
                        try {
                            if (("setInitialSurfaceControlProperties".equals(name)
                                    || "createSurfaceLocked".equals(name))
                                    && cl.loadClass(frame.getClassName()).getClassLoader() == systemServerCl) {
                                return;
                            }
                        } catch (ClassNotFoundException ignored) {
                        }
                    }
                }
                param.setResult(Boolean.FALSE);
            }
        });
    }

    private static void hookScreenCapture(ClassLoader cl) throws ClassNotFoundException, NoSuchFieldException {
        Class<?> screenCaptureClazz;
        Class<?> captureArgsClazz;
        try {
            screenCaptureClazz = cl.loadClass("android.window.ScreenCaptureInternal");
            captureArgsClazz = cl.loadClass("android.window.ScreenCaptureInternal$CaptureArgs");
        } catch (ClassNotFoundException e) {
            try {
                screenCaptureClazz = cl.loadClass("android.window.ScreenCapture");
                captureArgsClazz = cl.loadClass("android.window.ScreenCapture$CaptureArgs");
            } catch (ClassNotFoundException e2) {
                screenCaptureClazz = SurfaceControl.class;
                captureArgsClazz = cl.loadClass("android.view.SurfaceControl$CaptureArgs");
            }
        }

        Field captureSecureLayersField;
        boolean intMode;
        try {
            captureSecureLayersField = captureArgsClazz.getDeclaredField("mSecureContentPolicy");
            intMode = true;
        } catch (NoSuchFieldException e) {
            captureSecureLayersField = captureArgsClazz.getDeclaredField("mCaptureSecureLayers");
            intMode = false;
        }
        captureSecureLayersField.setAccessible(true);
        final Field field = captureSecureLayersField;
        final boolean setAsInt = intMode;

        hookMethods(screenCaptureClazz, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Object captureArgs = param.args[0];
                try {
                    if (setAsInt) {
                        field.setInt(captureArgs, 1);
                    } else {
                        field.setBoolean(captureArgs, true);
                    }
                } catch (IllegalAccessException t) {
                    log("ScreenCaptureHooker failed", t);
                }
            }
        }, "nativeCaptureDisplay", "nativeCaptureLayers");
    }

    private static void hookDisplayControl(ClassLoader cl) throws ClassNotFoundException, NoSuchMethodException {
        Class<?> displayControlClazz;
        if (Build.VERSION.SDK_INT >= 34) {
            displayControlClazz = cl.loadClass("com.android.server.display.DisplayControl");
        } else {
            displayControlClazz = SurfaceControl.class;
        }
        String methodName = Build.VERSION.SDK_INT >= 35 ? "createVirtualDisplay" : "createDisplay";
        Method method = displayControlClazz.getDeclaredMethod(methodName, String.class, boolean.class);
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (Build.VERSION.SDK_INT < 34) {
                    boolean target = false;
                    for (StackTraceElement frame : new Throwable().getStackTrace()) {
                        try {
                            if ("createVirtualDisplayLocked".equals(frame.getMethodName())
                                    && cl.loadClass(frame.getClassName()).getClassLoader()
                                            == displayControlClazz.getClassLoader()) {
                                target = true;
                                break;
                            }
                        } catch (ClassNotFoundException ignored) {
                        }
                    }
                    if (!target) return;
                }
                param.args[1] = Boolean.TRUE;
            }
        });
    }

    private static void hookVirtualDisplayAdapter(ClassLoader cl) throws ClassNotFoundException {
        Class<?> adapterClazz = cl.loadClass("com.android.server.display.VirtualDisplayAdapter");
        hookMethods(adapterClazz, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                int caller = (Integer) param.args[2];
                if (caller >= 10000 && param.args[1] == null) {
                    return;
                }
                for (int i = 3; i < param.args.length; i++) {
                    if (param.args[i] instanceof Integer) {
                        param.args[i] = (Integer) param.args[i] | DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE;
                        return;
                    }
                }
                log("flag not found in CreateVirtualDisplayLockedHooker", null);
            }
        }, "createVirtualDisplayLocked");
    }

    private static void hookActivityTaskManagerService(ClassLoader cl) throws ClassNotFoundException, NoSuchMethodException {
        Class<?> atmsClazz = cl.loadClass("com.android.server.wm.ActivityTaskManagerService");
        Class<?> iBinderClazz = cl.loadClass("android.os.IBinder");
        Class<?> iScreenCaptureObserverClazz = cl.loadClass("android.app.IScreenCaptureObserver");
        Method method = atmsClazz.getDeclaredMethod("registerScreenCaptureObserver", iBinderClazz, iScreenCaptureObserverClazz);
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult(null);
            }
        });
    }

    private static void hookWindowManagerService(ClassLoader cl) throws ClassNotFoundException, NoSuchMethodException {
        Class<?> wmsClazz = cl.loadClass("com.android.server.wm.WindowManagerService");
        Class<?> iScreenRecordingCallbackClazz = cl.loadClass("android.window.IScreenRecordingCallback");
        Method method = wmsClazz.getDeclaredMethod("registerScreenRecordingCallback", iScreenRecordingCallbackClazz);
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult(Boolean.FALSE);
            }
        });
    }

    private static void hookActivityManagerService(ClassLoader cl) throws ClassNotFoundException, NoSuchMethodException {
        Class<?> amsClazz = cl.loadClass("com.android.server.am.ActivityManagerService");
        Method method = amsClazz.getDeclaredMethod("checkPermission", String.class, int.class, int.class);
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if ("android.permission.CAPTURE_BLACKOUT_CONTENT".equals(param.args[0])) {
                    param.args[0] = "android.permission.READ_FRAME_BUFFER";
                }
            }
        });
    }

    private static void hookHyperOS(ClassLoader cl) throws ClassNotFoundException {
        Class<?> wmsImplClazz = cl.loadClass("com.android.server.wm.WindowManagerServiceImpl");
        hookMethods(wmsImplClazz, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult(Boolean.FALSE);
            }
        }, "notAllowCaptureDisplay");
    }

    private static void hookScreenshotHardwareBuffer(ClassLoader cl) throws ClassNotFoundException, NoSuchMethodException {
        String className;
        if (Build.VERSION.SDK_INT >= 34) {
            className = "android.window.ScreenCapture$ScreenshotHardwareBuffer";
        } else {
            className = "android.view.SurfaceControl$ScreenshotHardwareBuffer";
        }
        Class<?> bufferClazz = cl.loadClass(className);
        Method method = bufferClazz.getDeclaredMethod("containsSecureLayers");
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult(Boolean.FALSE);
            }
        });
    }

    private static void hookOplusScreenCapture(ClassLoader cl) throws ClassNotFoundException, NoSuchMethodException {
        Class<?> builderClazz = cl.loadClass("com.oplus.screenshot.OplusScreenCapture$CaptureArgs$Builder");
        Method method = builderClazz.getDeclaredMethod("setUid", long.class);
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.args[0] = -1L;
            }
        });
    }

    private static void hookOplusLongshot(ClassLoader cl) throws ClassNotFoundException {
        Class<?> longshotClazz = cl.loadClass("com.android.server.wm.OplusLongshotMainWindow");
        hookMethods(longshotClazz, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult(Boolean.FALSE);
            }
        }, "hasSecure");
    }

    private static void hookOneUI(ClassLoader cl) throws ClassNotFoundException {
        Class<?> controllerClazz = cl.loadClass("com.android.server.wm.WmScreenshotController");
        hookMethods(controllerClazz, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult(Boolean.TRUE);
            }
        }, "canBeScreenshotTarget");
    }

    private static void hookMethods(Class<?> clazz, XC_MethodHook hooker, String... names) {
        for (Method method : clazz.getDeclaredMethods()) {
            for (String name : names) {
                if (method.getName().equals(name)) {
                    XposedBridge.hookMethod(method, hooker);
                }
            }
        }
    }
}
