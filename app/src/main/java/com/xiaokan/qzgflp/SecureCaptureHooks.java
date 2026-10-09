package com.xiaokan.qzgflp;

import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

/**
 * Port of LSPosed DisableFlagSecure (io.github.lsposed.disableflagsecure.DisableFlagSecure),
 * as integrated by LuckyTool, targeting the libxposed API 102.
 *
 * Removes secure-layer restrictions for screenshots and screen recordings at the
 * system_server level, so FLAG_SECURE / secure SurfaceFlinger layers (drawn above
 * the kernel by the graphics stack) are captured on any rooted device.
 */
final class SecureCaptureHooks {
    private static final String TAG = "ForceCapture";

    private static final String SYSTEMUI = "com.android.systemui";
    private static final String OPLUS_APPPLATFORM = "com.oplus.appplatform";
    private static final String OPLUS_SCREENSHOT = "com.oplus.screenshot";
    private static final String FLYME_SYSTEMUIEX = "com.flyme.systemuiex";
    private static final String MIUI_SCREENSHOT = "com.miui.screenshot";

    private final XposedInterface module;

    SecureCaptureHooks(XposedInterface module) {
        this.module = module;
    }

    private void logError(String msg, Throwable t) {
        module.log(Log.ERROR, TAG, msg, t);
    }

    void deoptimizeSystemServer(ClassLoader cl) {
        try {
            deoptimizeMethods(cl.loadClass("com.android.server.wm.WindowStateAnimator"), "createSurfaceLocked");
            deoptimizeMethods(cl.loadClass("com.android.server.wm.WindowManagerService"), "relayoutWindow");
        } catch (Throwable t) {
            logError("deoptimize system server failed", t);
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

    void hookSystemServer(ClassLoader cl) {
        if (Build.VERSION.SDK_INT >= 35) {
            try {
                hookWindowManagerService(cl);
            } catch (Throwable t) {
                logError("hook WindowManagerService failed", t);
            }
        }

        if (Build.VERSION.SDK_INT >= 34) {
            try {
                hookActivityTaskManagerService(cl);
            } catch (Throwable t) {
                logError("hook ActivityTaskManagerService failed", t);
            }
            try {
                hookHyperOS(cl);
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable t) {
                logError("hook HyperOS failed", t);
            }
        }

        try {
            hookScreenCapture(cl);
        } catch (Throwable t) {
            logError("hook ScreenCapture failed", t);
        }

        if (Build.VERSION.SDK_INT < 34) {
            try {
                hookActivityManagerService(cl);
            } catch (Throwable t) {
                logError("hook ActivityManagerService failed", t);
            }
        }

        try {
            hookDisplayControl(cl);
        } catch (Throwable t) {
            logError("hook DisplayControl failed", t);
        }

        try {
            hookVirtualDisplayAdapter(cl);
        } catch (Throwable t) {
            logError("hook VirtualDisplayAdapter failed", t);
        }

        try {
            hookScreenshotHardwareBuffer(cl);
        } catch (Throwable t) {
            if (!(t instanceof ClassNotFoundException)) {
                logError("hook ScreenshotHardwareBuffer failed", t);
            }
        }

        try {
            hookOneUI(cl);
        } catch (Throwable t) {
            if (!(t instanceof ClassNotFoundException)) {
                logError("hook OneUI failed", t);
            }
        }

        try {
            hookWindowState(cl);
        } catch (Throwable t) {
            logError("hook WindowState failed", t);
        }

        try {
            hookOplusLongshot(cl);
        } catch (Throwable t) {
            if (!(t instanceof ClassNotFoundException)) {
                logError("hook Oplus failed", t);
            }
        }
    }

    void hookPackage(String packageName, ClassLoader cl) {
        boolean oplus = OPLUS_APPPLATFORM.equals(packageName) || OPLUS_SCREENSHOT.equals(packageName);
        switch (packageName) {
            case OPLUS_SCREENSHOT:
                if (Build.VERSION.SDK_INT >= 35) {
                    try {
                        hookOplusScreenCapture(cl);
                    } catch (Throwable t) {
                        if (!(t instanceof ClassNotFoundException)) {
                            logError("hook OplusScreenCapture failed", t);
                        }
                    }
                }
            case FLYME_SYSTEMUIEX:
            case OPLUS_APPPLATFORM:
                try {
                    hookScreenshotHardwareBuffer(cl);
                } catch (Throwable t) {
                    if (!(t instanceof ClassNotFoundException)) {
                        logError("hook ScreenshotHardwareBuffer failed", t);
                    }
                }
            case SYSTEMUI:
            case MIUI_SCREENSHOT:
                if (oplus || Build.VERSION.SDK_INT < 34) {
                    try {
                        hookScreenCapture(cl);
                    } catch (Throwable t) {
                        logError("hook ScreenCapture failed", t);
                    }
                }
                break;
            default:
                module.log(Log.INFO, TAG, "app-level hooks for " + packageName);
                try {
                    hookSurfaceViewSecure(cl);
                } catch (Throwable t) {
                    logError("hook SurfaceView.setSecure failed", t);
                }
                try {
                    hookTransactionSetSecure(cl);
                } catch (Throwable t) {
                    if (!(t instanceof ClassNotFoundException) && !(t instanceof NoSuchMethodException)) {
                        logError("hook Transaction.setSecure failed", t);
                    }
                }
                try {
                    hookSurfaceControlBuilderSetSecure(cl);
                } catch (Throwable t) {
                    if (!(t instanceof ClassNotFoundException)) {
                        logError("hook SurfaceControl.Builder.setSecure failed", t);
                    }
                }
                try {
                    hookSurfaceViewChildPackage(cl);
                } catch (Throwable t) {
                    logError("hook SurfaceView.setChildSurfacePackage failed", t);
                }
                try {
                    hookNativeSurfacePackageSecure(cl);
                } catch (Throwable t) {
                    if (!(t instanceof ClassNotFoundException)) {
                        logError("hook native SurfacePackage secure failed", t);
                    }
                }
                break;
        }
    }

    private void hookSurfaceViewSecure(ClassLoader cl) throws ClassNotFoundException, NoSuchMethodException {
        Class<?> surfaceViewClazz = cl.loadClass("android.view.SurfaceView");
        Method method = surfaceViewClazz.getDeclaredMethod("setSecure", boolean.class);
        module.hook(method).intercept(chain -> {
            module.log(Log.INFO, TAG, "SurfaceView.setSecure(" + chain.getArg(0) + ") -> false");
            return chain.proceed(new Object[]{Boolean.FALSE});
        });
    }

    private void hookTransactionSetSecure(ClassLoader cl) throws ClassNotFoundException, NoSuchMethodException {
        Class<?> transactionClazz = cl.loadClass("android.view.SurfaceControl$Transaction");
        Method method = transactionClazz.getDeclaredMethod("setSecure", SurfaceControl.class, boolean.class);
        module.hook(method).intercept(chain -> {
            if (Boolean.TRUE.equals(chain.getArg(1))) {
                module.log(Log.INFO, TAG, "Transaction.setSecure(true) -> false");
            }
            return chain.proceed(new Object[]{chain.getArg(0), Boolean.FALSE});
        });
    }

    private void hookSurfaceControlBuilderSetSecure(ClassLoader cl) throws ClassNotFoundException {
        Class<?> builderClazz = cl.loadClass("android.view.SurfaceControl$Builder");
        int hooked = 0;
        for (Method method : builderClazz.getDeclaredMethods()) {
            if ("setSecure".equals(method.getName()) && method.getParameterCount() == 1) {
                module.hook(method).intercept(chain -> chain.proceed(new Object[]{Boolean.FALSE}));
                hooked++;
            }
        }
        module.log(Log.INFO, TAG, "SurfaceControl.Builder.setSecure hooked x" + hooked);
    }

    private void hookSurfaceViewChildPackage(ClassLoader cl) throws ClassNotFoundException, NoSuchMethodException {
        Class<?> surfaceViewClazz = cl.loadClass("android.view.SurfaceView");
        Method method = surfaceViewClazz.getDeclaredMethod("setChildSurfacePackage",
                cl.loadClass("android.view.SurfaceControlViewHost$SurfacePackage"));
        module.hook(method).intercept(chain -> {
            Object result = chain.proceed();
            Object pkg = chain.getArg(0);
            if (pkg != null) {
                clearSecureDelayed(pkg, 0);
            }
            return result;
        });
    }

    private void clearSecureDelayed(Object pkg, int round) {
        try {
            SurfaceControl sc = ((SurfaceControlViewHost.SurfacePackage) pkg).getSurfaceControl();
            Class<?> txClazz = Class.forName("android.view.SurfaceControl$Transaction");
            Object tx = txClazz.getDeclaredConstructor().newInstance();
            Method setSecure = txClazz.getDeclaredMethod("setSecure", SurfaceControl.class, boolean.class);
            setSecure.setAccessible(true);
            setSecure.invoke(tx, sc, Boolean.FALSE);
            txClazz.getMethod("apply").invoke(tx);
            if (round < 6) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> clearSecureDelayed(pkg, round + 1), 300);
            }
        } catch (Throwable ignored) {
        }
    }

    private void hookNativeSurfacePackageSecure(ClassLoader cl) throws ClassNotFoundException {
        Class<?> pkgClazz = cl.loadClass("android.view.SurfaceControlViewHost$SurfacePackage");
        int hooked = 0;
        String[] targets = {"yyds.C3555"};
        for (String name : targets) {
            try {
                Class<?> clazz = cl.loadClass(name);
                for (Method method : clazz.getDeclaredMethods()) {
                    boolean takesPkg = false;
                    for (Class<?> p : method.getParameterTypes()) {
                        if (p == pkgClazz) {
                            takesPkg = true;
                            break;
                        }
                    }
                    if (!takesPkg) continue;
                    try {
                        module.hook(method).intercept(chain -> Boolean.FALSE);
                        hooked++;
                    } catch (Throwable t) {
                        module.log(Log.INFO, TAG, "native hook failed for " + name + "." + method.getName() + ": " + t);
                    }
                }
            } catch (ClassNotFoundException e) {
                continue;
            }
        }
        module.log(Log.INFO, TAG, "native SurfacePackage secure hooks x" + hooked);
    }

    private void hookWindowState(ClassLoader cl) throws ClassNotFoundException, NoSuchMethodException {
        Class<?> windowStateClazz = cl.loadClass("com.android.server.wm.WindowState");
        ClassLoader systemServerCl = windowStateClazz.getClassLoader();
        Method isSecureLocked = windowStateClazz.getDeclaredMethod("isSecureLocked");
        module.hook(isSecureLocked).intercept(chain -> {
            if (Build.VERSION.SDK_INT >= 34) {
                var walker = java.lang.StackWalker.getInstance(java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE);
                boolean match = walker.walk(frames -> frames.anyMatch(frame ->
                        frame.getDeclaringClass() != null
                                && frame.getDeclaringClass().getClassLoader() == systemServerCl
                                && ("setInitialSurfaceControlProperties".equals(frame.getMethodName())
                                || "createSurfaceLocked".equals(frame.getMethodName()))));
                if (match) return chain.proceed();
            } else {
                for (StackTraceElement frame : new Throwable().getStackTrace()) {
                    String name = frame.getMethodName();
                    try {
                        if (("setInitialSurfaceControlProperties".equals(name) || "createSurfaceLocked".equals(name))
                                && cl.loadClass(frame.getClassName()).getClassLoader() == systemServerCl) {
                            return chain.proceed();
                        }
                    } catch (ClassNotFoundException ignored) {
                    }
                }
            }
            return false;
        });
    }

    private void hookScreenCapture(ClassLoader cl) throws ClassNotFoundException, NoSuchFieldException {
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

        XposedInterface.Hooker hooker = chain -> {
            Object captureArgs = chain.getArg(0);
            try {
                if (setAsInt) {
                    field.setInt(captureArgs, 1);
                } else {
                    field.setBoolean(captureArgs, true);
                }
            } catch (IllegalAccessException t) {
                module.log(Log.ERROR, TAG, "ScreenCaptureHooker failed", t);
            }
            return chain.proceed();
        };
        hookMethods(screenCaptureClazz, hooker, "nativeCaptureDisplay", "nativeCaptureLayers");
    }

    private void hookDisplayControl(ClassLoader cl) throws ClassNotFoundException, NoSuchMethodException {
        Class<?> displayControlClazz;
        if (Build.VERSION.SDK_INT >= 34) {
            displayControlClazz = cl.loadClass("com.android.server.display.DisplayControl");
        } else {
            displayControlClazz = SurfaceControl.class;
        }
        ClassLoader systemServerCl = displayControlClazz.getClassLoader();
        String methodName = Build.VERSION.SDK_INT >= 35 ? "createVirtualDisplay" : "createDisplay";
        Method method = displayControlClazz.getDeclaredMethod(methodName, String.class, boolean.class);
        module.hook(method).intercept(chain -> {
            if (Build.VERSION.SDK_INT < 34) {
                boolean target = false;
                for (StackTraceElement frame : new Throwable().getStackTrace()) {
                    try {
                        if ("createVirtualDisplayLocked".equals(frame.getMethodName())
                                && cl.loadClass(frame.getClassName()).getClassLoader() == systemServerCl) {
                            target = true;
                            break;
                        }
                    } catch (ClassNotFoundException ignored) {
                    }
                }
                if (!target) return chain.proceed();
            }
            Object[] args = chain.getArgs().toArray();
            args[1] = Boolean.TRUE;
            return chain.proceed(args);
        });
    }

    private void hookVirtualDisplayAdapter(ClassLoader cl) throws ClassNotFoundException {
        Class<?> adapterClazz = cl.loadClass("com.android.server.display.VirtualDisplayAdapter");
        hookMethods(adapterClazz, chain -> {
            int caller = (Integer) chain.getArg(2);
            if (caller >= 10000 && chain.getArg(1) == null) {
                return chain.proceed();
            }
            List<Object> argsList = chain.getArgs();
            for (int i = 3; i < argsList.size(); i++) {
                Object arg = argsList.get(i);
                if (arg instanceof Integer) {
                    int flags = (Integer) arg | DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE;
                    Object[] args = argsList.toArray();
                    args[i] = flags;
                    return chain.proceed(args);
                }
            }
            module.log(Log.WARN, TAG, "flag not found in CreateVirtualDisplayLockedHooker", null);
            return chain.proceed();
        }, "createVirtualDisplayLocked");
    }

    private void hookActivityTaskManagerService(ClassLoader cl) throws ClassNotFoundException {
        Class<?> atmsClazz = cl.loadClass("com.android.server.wm.ActivityTaskManagerService");
        int hooked = hookMethods(atmsClazz, chain -> null, "registerScreenCaptureObserver");
        module.log(Log.INFO, TAG, "registerScreenCaptureObserver hooked x" + hooked);
        if (hooked == 0) {
            dumpCandidates(atmsClazz, "screencapture");
        }
    }

    private void hookWindowManagerService(ClassLoader cl) throws ClassNotFoundException {
        Class<?> wmsClazz = cl.loadClass("com.android.server.wm.WindowManagerService");
        int hooked = hookMethods(wmsClazz, chain -> false, "registerScreenRecordingCallback");
        module.log(Log.INFO, TAG, "registerScreenRecordingCallback hooked x" + hooked);
        if (hooked == 0) {
            dumpCandidates(wmsClazz, "screenrecording");
        }
    }

    private void dumpCandidates(Class<?> clazz, String keyword) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().toLowerCase().contains(keyword)) {
                module.log(Log.INFO, TAG, "  candidate: " + method.toGenericString());
            }
        }
    }

    private void hookActivityManagerService(ClassLoader cl) throws ClassNotFoundException, NoSuchMethodException {
        Class<?> amsClazz = cl.loadClass("com.android.server.am.ActivityManagerService");
        Method method = amsClazz.getDeclaredMethod("checkPermission", String.class, int.class, int.class);
        module.hook(method).intercept(chain -> {
            String permission = (String) chain.getArg(0);
            if ("android.permission.CAPTURE_BLACKOUT_CONTENT".equals(permission)) {
                Object[] args = chain.getArgs().toArray();
                args[0] = "android.permission.READ_FRAME_BUFFER";
                return chain.proceed(args);
            }
            return chain.proceed();
        });
    }

    private void hookHyperOS(ClassLoader cl) throws ClassNotFoundException {
        Class<?> wmsImplClazz = cl.loadClass("com.android.server.wm.WindowManagerServiceImpl");
        hookMethods(wmsImplClazz, chain -> false, "notAllowCaptureDisplay");
    }

    private void hookScreenshotHardwareBuffer(ClassLoader cl) throws ClassNotFoundException, NoSuchMethodException {
        String className;
        if (Build.VERSION.SDK_INT >= 34) {
            className = "android.window.ScreenCapture$ScreenshotHardwareBuffer";
        } else {
            className = "android.view.SurfaceControl$ScreenshotHardwareBuffer";
        }
        Class<?> bufferClazz = cl.loadClass(className);
        Method method = bufferClazz.getDeclaredMethod("containsSecureLayers");
        module.hook(method).intercept(chain -> false);
    }

    private void hookOplusScreenCapture(ClassLoader cl) throws ClassNotFoundException, NoSuchMethodException {
        Class<?> builderClazz = cl.loadClass("com.oplus.screenshot.OplusScreenCapture$CaptureArgs$Builder");
        Method method = builderClazz.getDeclaredMethod("setUid", long.class);
        module.hook(method).intercept(chain -> {
            Object[] args = chain.getArgs().toArray();
            args[0] = -1L;
            return chain.proceed(args);
        });
    }

    private void hookOplusLongshot(ClassLoader cl) throws ClassNotFoundException {
        Class<?> longshotClazz = cl.loadClass("com.android.server.wm.OplusLongshotMainWindow");
        hookMethods(longshotClazz, chain -> false, "hasSecure");
    }

    private void hookOneUI(ClassLoader cl) throws ClassNotFoundException {
        Class<?> controllerClazz = cl.loadClass("com.android.server.wm.WmScreenshotController");
        hookMethods(controllerClazz, chain -> true, "canBeScreenshotTarget");
    }

    private void deoptimizeMethods(Class<?> clazz, String... names) {
        List<String> list = Arrays.asList(names);
        for (Method method : clazz.getDeclaredMethods()) {
            if (list.contains(method.getName())) {
                module.deoptimize(method);
            }
        }
    }

    private int hookMethods(Class<?> clazz, XposedInterface.Hooker hooker, String... names) {
        List<String> list = Arrays.asList(names);
        int count = 0;
        for (Method method : clazz.getDeclaredMethods()) {
            if (list.contains(method.getName())) {
                module.hook(method).intercept(hooker);
                count++;
            }
        }
        return count;
    }
}
