package com.xiaokan.qzgflp;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * 跨进程开关通道（导出 Provider，LSPosed/FPA/LSPatch 全兼容）。
 *
 * Hook 侧调用：
 *   call(Uri("content://com.xiaokan.qzgflp.hookstate"), "enabled", 目标包名, null) → {v:boolean}
 *   call(Uri(...), "cfgall", null, null) → 全量功能配置（b_ 布尔 / i_ 整型 / s_ 字符串 前缀扁平化）
 */
public class HookStateProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle b = new Bundle();
        Context c = getContext();
        if (c == null) return b;
        if ("enabled".equals(method) && arg != null) {
            boolean v = true;
            try {
                SharedPreferences settings = c.getSharedPreferences("settings",
                        Context.MODE_PRIVATE);
                v = settings.getBoolean("hook_" + arg, true)
                        && settings.getBoolean("hook_master", true);
            } catch (Throwable ignored) {
            }
            b.putBoolean("v", v);
        } else if ("cfgall".equals(method)) {
            try {
                SharedPreferences p = c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
                SharedPreferences settings = c.getSharedPreferences("settings",
                        Context.MODE_PRIVATE);
                b.putBoolean("b_hook_master", settings.getBoolean("hook_master", true));
                for (String[] s : FeatureKeys.SWITCHES) {
                    b.putBoolean("b_" + s[0], p.getBoolean(s[0],
                            (Boolean) FeatureKeys.defaultValue(s[0])));
                }
                b.putInt("i_" + FeatureKeys.LAYOUT_ROWS, p.getInt(FeatureKeys.LAYOUT_ROWS,
                        (Integer) FeatureKeys.defaultValue(FeatureKeys.LAYOUT_ROWS)));
                b.putInt("i_" + FeatureKeys.LAYOUT_COLS, p.getInt(FeatureKeys.LAYOUT_COLS,
                        (Integer) FeatureKeys.defaultValue(FeatureKeys.LAYOUT_COLS)));
                b.putString("s_" + FeatureKeys.TILE_SCRIPT,
                        p.getString(FeatureKeys.TILE_SCRIPT, ""));
            } catch (Throwable ignored) {
            }
        }
        return b;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        return 0;
    }
}
