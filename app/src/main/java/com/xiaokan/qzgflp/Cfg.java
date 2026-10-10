package com.xiaokan.qzgflp;

import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;

/**
 * Hook 侧配置读取（2 秒缓存）。
 * 优先走 HookStateProvider（app 进程，设置改动即时生效），
 * 失败回退到框架远端偏好（system_server 等无 Application 的进程）。
 *
 * Modern 入口注入 remoteBundle(key)，Legacy 入口注入 XSharedPreferences 读取器。
 */
public final class Cfg {

    /** 远端偏好读取器：返回 {b_...,i_...,s_...} 风格 Bundle，读不到返回 null */
    public interface Remote {
        Bundle read(String key);
    }

    public static final Uri STATE_URI =
            Uri.parse("content://com.xiaokan.qzgflp.hookstate");

    private static Remote sRemote;
    private static android.content.ContentResolver sResolver;
    private static long sLastFetch;
    private static Bundle sCache = new Bundle();
    private static boolean sFromRemote;

    public static void setRemote(Remote remote) {
        sRemote = remote;
    }

    private static void refresh() {
        long now = SystemClock.uptimeMillis();
        if (now - sLastFetch < 2000) return;
        sLastFetch = now;
        Bundle b = null;
        try {
            android.content.ContentResolver r = resolver();
            if (r != null) {
                b = r.call(STATE_URI, "cfgall", null, null);
            }
        } catch (Throwable ignored) {
        }
        sFromRemote = false;
        if (b == null || b.isEmpty()) {
            try {
                if (sRemote != null) {
                    b = sRemote.read("cfgall");
                    sFromRemote = b != null && !b.isEmpty();
                }
            } catch (Throwable ignored) {
            }
        }
        if (b != null && !b.isEmpty()) {
            sCache = b;
        } else if (!sCache.containsKey("bootstrapped")) {
            sCache.putBoolean("bootstrapped", true);
            sCache.putBoolean("b_hook_master", true);
            // 首次无数据：填默认值，保证 hook 行为可预期（默认全部关闭）
            for (String[] s : FeatureKeys.SWITCHES) {
                if (!sCache.containsKey("b_" + s[0])) {
                    sCache.putBoolean("b_" + s[0], (Boolean) FeatureKeys.defaultValue(s[0]));
                }
            }
            sCache.putInt("i_" + FeatureKeys.LAYOUT_ROWS,
                    (Integer) FeatureKeys.defaultValue(FeatureKeys.LAYOUT_ROWS));
            sCache.putInt("i_" + FeatureKeys.LAYOUT_COLS,
                    (Integer) FeatureKeys.defaultValue(FeatureKeys.LAYOUT_COLS));
            sCache.putString("s_" + FeatureKeys.TILE_SCRIPT,
                    (String) FeatureKeys.defaultValue(FeatureKeys.TILE_SCRIPT));
        }
    }

    private static android.content.ContentResolver resolver() {
        if (sResolver != null) return sResolver;
        try {
            Object app = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            if (app instanceof android.content.Context) {
                sResolver = ((android.content.Context) app).getContentResolver();
            }
        } catch (Throwable ignored) {
        }
        if (sResolver == null) {
            try {
                Object at = Class.forName("android.app.ActivityThread")
                        .getMethod("currentActivityThread").invoke(null);
                if (at != null) {
                    Object ctx = at.getClass().getMethod("getSystemContext").invoke(at);
                    if (ctx instanceof android.content.Context) {
                        sResolver = ((android.content.Context) ctx).getContentResolver();
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return sResolver;
    }

    public static boolean bool(String key) {
        refresh();
        if (!sCache.getBoolean("b_hook_master", true)) return false;
        return sCache.getBoolean("b_" + key,
                (Boolean) FeatureKeys.defaultValue(key));
    }

    public static int intv(String key) {
        refresh();
        Integer d = (Integer) FeatureKeys.defaultValue(key);
        return sCache.getInt("i_" + key, d == null ? 0 : d);
    }

    public static String str(String key) {
        refresh();
        Object d = FeatureKeys.defaultValue(key);
        return sCache.getString("s_" + key, d instanceof String ? (String) d : "");
    }

    private Cfg() {
    }
}
