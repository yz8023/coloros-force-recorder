package com.xiaokan.qzgflp;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** EcmUtils 的 Java 移植（进程内反射实现） */
final class EcmLike {
    static void autoUnlock(Activity activity, String packName) {
        if (isEcmMode()) {
            Object ecm = activity.getSystemService("ecm_enhanced_confirmation");
            if (ecm == null) return;
            try {
                Method isRestricted = ecm.getClass().getMethod("isRestricted",
                        String.class, String.class);
                boolean restricted = (Boolean) isRestricted.invoke(ecm, packName,
                        "android:bind_accessibility_service");
                if (!restricted) return;
                invokeEcm(ecm, "isClearRestrictionAllowed", packName);
                invokeEcm(ecm, "setClearRestrictionAllowed", packName);
                invokeEcm(ecm, "clearRestriction", packName);
            } catch (Throwable ignored) {
            }
        } else {
            try {
                AppOpsManager appOps =
                        (AppOpsManager) activity.getSystemService(Context.APP_OPS_SERVICE);
                Field f = AppOpsManager.class.getDeclaredField("OPSTR_ACCESS_RESTRICTED_SETTINGS");
                f.setAccessible(true);
                String op = (String) f.get(null);
                int uid = activity.getPackageManager()
                        .getPackageUid(packName, 0);
                int note = appOps.noteOpNoThrow(op, uid, packName, null, null);
                if (note == 0 || note == 3) return;
                Method setMode = appOps.getClass().getMethod("setMode",
                        int.class, int.class, String.class, int.class);
                setMode.invoke(appOps, 119, uid, packName, 0);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void invokeEcm(Object ecm, String name, String arg) throws Exception {
        Method m = ecm.getClass().getMethod(name, String.class);
        m.invoke(ecm, arg);
    }

    private static boolean isEcmMode() {
        try {
            Class<?> flags = Class.forName(
                    "com.android.internal.hidden_from_bootclasspath.android.permission.flags.Flags");
            Method m = flags.getMethod("enhancedConfirmationModeApisEnabled");
            return (Boolean) m.invoke(null);
        } catch (Throwable t) {
            return false;
        }
    }
}
