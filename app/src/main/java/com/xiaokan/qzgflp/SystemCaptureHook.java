package com.xiaokan.qzgflp;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Binder;
import android.view.SurfaceControl;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.function.Supplier;

/* JADX INFO: loaded from: classes.dex */
final class SystemCaptureHook {
    private static final ThreadLocal<Deque<Frame>> calls = ThreadLocal.withInitial(new Supplier() { // from class: com.xiaokan.qzgflp.SystemCaptureHook$$ExternalSyntheticLambda0
        @Override // java.util.function.Supplier
        public final Object get() {
            return SystemCaptureHook.m0$r8$lambda$RweyG_ongA23Ja7AuIK1AWEgHw();
        }
    });

    /* JADX INFO: renamed from: $r8$lambda$RweyG_ongA-23Ja7AuIK1AWEgHw, reason: not valid java name */
    public static /* synthetic */ ArrayDeque m0$r8$lambda$RweyG_ongA23Ja7AuIK1AWEgHw() {
        return new ArrayDeque();
    }

    SystemCaptureHook() {
    }

    private static final class Frame {
        final Object owner;
        SurfaceControl replacement;

        Frame(Object obj) {
            this.owner = obj;
        }
    }

    static void install(ClassLoader classLoader) throws Exception {
        Class clsFindClass = XposedHelpers.findClass("com.android.server.wm.ContentRecorder", classLoader);
        String[] strArr = {"mDisplayContent", "mContentRecordingSession", "mRecordedWindowContainer", "mRecordedSurface"};
        for (int i = 0; i < 4; i++) {
            clsFindClass.getDeclaredField(strArr[i]).setAccessible(true);
        }
        Method declaredMethod = clsFindClass.getDeclaredMethod("startRecordingIfNeeded", new Class[0]);
        if (declaredMethod.getReturnType() != Void.TYPE) {
            throw new NoSuchMethodException("ContentRecorder start signature");
        }
        ArrayList arrayList = new ArrayList();
        for (Method method : SurfaceControl.class.getDeclaredMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if ("mirrorSurface".equals(method.getName()) && Modifier.isStatic(method.getModifiers()) && method.getReturnType() == SurfaceControl.class && ((parameterTypes.length == 1 || parameterTypes.length == 2) && parameterTypes[0] == SurfaceControl.class && (parameterTypes.length == 1 || parameterTypes[1] == SurfaceControl.class))) {
                arrayList.add(method);
            }
        }
        if (arrayList.isEmpty()) {
            throw new NoSuchMethodException("SurfaceControl mirrorSurface");
        }
        DisplayMirror.preflight();
        ArrayList arrayList2 = new ArrayList();
        try {
            arrayList2.add(XposedBridge.hookMethod(declaredMethod, new XC_MethodHook() { // from class: com.xiaokan.qzgflp.SystemCaptureHook.1
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    ((Deque) SystemCaptureHook.calls.get()).push(new Frame(methodHookParam.thisObject));
                }

                protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Deque deque = (Deque) SystemCaptureHook.calls.get();
                    Frame frame = deque.isEmpty() ? null : (Frame) deque.pop();
                    if (deque.isEmpty()) {
                        SystemCaptureHook.calls.remove();
                    }
                    if (frame == null || frame.replacement == null) {
                        return;
                    }
                    try {
                        Object objectField = XposedHelpers.getObjectField(frame.owner, "mRecordedSurface");
                        if (!methodHookParam.hasThrowable() && objectField == frame.replacement) {
                            HookEntry.log("CAPTURE_SOURCE_REPLACED; WMS 已接入原虚拟显示", null);
                            return;
                        }
                        long jClearCallingIdentity = Binder.clearCallingIdentity();
                        try {
                            SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
                            try {
                                XposedHelpers.callMethod(transaction, "remove", new Object[]{frame.replacement});
                                transaction.apply();
                                transaction.close();
                                Binder.restoreCallingIdentity(jClearCallingIdentity);
                                frame.replacement.release();
                                HookEntry.log("CAPTURE_SOURCE_DISCARDED; WMS 未接受新捕获源", null);
                            } catch (Throwable th) {
                                try {
                                    transaction.close();
                                } catch (Throwable th2) {
                                    th.addSuppressed(th2);
                                }
                                throw th;
                            }
                        } catch (Throwable th3) {
                            Binder.restoreCallingIdentity(jClearCallingIdentity);
                            frame.replacement.release();
                            throw th3;
                        }
                    } catch (Throwable th4) {
                        HookEntry.log("CAPTURE_SOURCE_CLEANUP_FAILED", th4);
                    }
                }
            }));
            XC_MethodHook xC_MethodHook = new XC_MethodHook() { // from class: com.xiaokan.qzgflp.SystemCaptureHook.2
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    Deque deque = (Deque) SystemCaptureHook.calls.get();
                    if (deque.isEmpty()) {
                        SystemCaptureHook.calls.remove();
                        return;
                    }
                    Frame frame = (Frame) deque.peek();
                    Object obj = frame.owner;
                    try {
                        Object objectField = XposedHelpers.getObjectField(obj, "mContentRecordingSession");
                        if (objectField == null) {
                            return;
                        }
                        Object objectField2 = XposedHelpers.getObjectField(obj, "mDisplayContent");
                        Object objCallMethod = XposedHelpers.callMethod(objectField2, "getDisplayInfo", new Object[0]);
                        String str = (String) XposedHelpers.getObjectField(objCallMethod, "ownerPackageName");
                        String str2 = (String) XposedHelpers.getObjectField(objCallMethod, "name");
                        int iIntValue = ((Integer) XposedHelpers.callMethod(objectField, "getContentToRecord", new Object[0])).intValue();
                        int iIntValue2 = ((Integer) XposedHelpers.callMethod(objectField, "getDisplayToRecord", new Object[0])).intValue();
                        int iIntValue3 = ((Integer) XposedHelpers.callMethod(objectField2, "getDisplayId", new Object[0])).intValue();
                        if (!CapturePolicy.eligible(str, str2, iIntValue, iIntValue2, iIntValue3, ((Integer) XposedHelpers.callMethod(objectField, "getVirtualDisplayId", new Object[0])).intValue(), ((Boolean) XposedHelpers.callMethod(objectField, "isWaitingForConsent", new Object[0])).booleanValue(), ((Integer) XposedHelpers.callMethod(objectField, "getTargetUid", new Object[0])).intValue())) {
                            if ("com.oplus.screenrecorder".equals(str)) {
                                HookEntry.log("RECORDER_SESSION_SKIPPED name=" + str2 + " content=" + iIntValue + " source=" + iIntValue2 + " virtual=" + iIntValue3, null);
                                return;
                            }
                            return;
                        }
                        Object objectField3 = XposedHelpers.getObjectField(obj, "mRecordedWindowContainer");
                        if (objectField3 == null) {
                            return;
                        }
                        SurfaceControl surfaceControl = (SurfaceControl) XposedHelpers.callMethod(objectField3, "getSurfaceControl", new Object[0]);
                        SurfaceControl surfaceControl2 = (SurfaceControl) methodHookParam.args[0];
                        if (surfaceControl != null && surfaceControl2 != null && ((Boolean) XposedHelpers.callMethod(surfaceControl, "isSameSurface", new Object[]{surfaceControl2})).booleanValue()) {
                            Context context = (Context) XposedHelpers.getObjectField(XposedHelpers.getObjectField(objectField2, "mWmService"), "mContext");
                            long jClearCallingIdentity = Binder.clearCallingIdentity();
                            try {
                                PackageInfo packageInfo = context.getPackageManager().getPackageInfo("com.oplus.screenrecorder", 0);
                                Binder.restoreCallingIdentity(jClearCallingIdentity);
                                if (!HookEntry.supported(packageInfo)) {
                                    HookEntry.log("UNSUPPORTED_VERSION in system_server", null);
                                    return;
                                }
                                if (packageInfo.applicationInfo != null && packageInfo.applicationInfo.uid == XposedHelpers.getIntField(objCallMethod, "ownerUid")) {
                                    Object objCallMethod2 = XposedHelpers.callMethod(objectField3, "getDisplayInfo", new Object[0]);
                                    Object objectField4 = XposedHelpers.getObjectField(objCallMethod2, "address");
                                    if (objectField4 == null || !"android.view.DisplayAddress$Physical".equals(objectField4.getClass().getName())) {
                                        throw new IllegalStateException("Source display is not physical");
                                    }
                                    long jLongValue = ((Long) XposedHelpers.callMethod(objectField4, "getPhysicalDisplayId", new Object[0])).longValue();
                                    int intField = XposedHelpers.getIntField(objCallMethod, "layerStack");
                                    if (intField == XposedHelpers.getIntField(objCallMethod2, "layerStack")) {
                                        throw new IllegalStateException("Source and destination layer stacks coincide");
                                    }
                                    SurfaceControl surfaceControlCreate = DisplayMirror.create(jLongValue, intField);
                                    frame.replacement = surfaceControlCreate;
                                    methodHookParam.setResult(surfaceControlCreate);
                                    HookEntry.log("CAPTURE_SOURCE_CREATED physical=" + jLongValue + " virtual=" + iIntValue3 + "; 原编码器、音频、生命周期保留", null);
                                }
                            } catch (Throwable th) {
                                Binder.restoreCallingIdentity(jClearCallingIdentity);
                                throw th;
                            }
                        }
                    } catch (Throwable th2) {
                        HookEntry.log("CAPTURE_SOURCE_FALLBACK; 保留系统原捕获源", th2);
                    }
                }
            };
            Iterator it = arrayList.iterator();
            while (it.hasNext()) {
                arrayList2.add(XposedBridge.hookMethod((Method) it.next(), xC_MethodHook));
            }
            HookEntry.log("SYSTEM_CAPTURE_READY Android 16; 仅 Oplus 16.5.12 全屏会话", null);
        } catch (Throwable th) {
            Iterator it2 = arrayList2.iterator();
            while (it2.hasNext()) {
                ((XC_MethodHook.Unhook) it2.next()).unhook();
            }
            throw new IllegalStateException("撤销不完整的系统捕获 Hook", th);
        }
    }
}
