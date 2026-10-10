package com.xiaokan.qzgflp;

import java.util.HashMap;
import java.util.Map;

/**
 * Feature switch keys + defaults, shared by the settings UI, HookStateProvider
 * and the hook-side config readers (modern remote prefs / legacy XSharedPreferences).
 * Type markers: value keys return non-boolean defaults.
 */
public final class FeatureKeys {

    // ── 桌面 ──
    public static final String FOLDER_BG = "folder_preview_bg_remove";
    public static final String LAYOUT_CUSTOM = "launcher_layout_custom";
    public static final String LAYOUT_ROWS = "launcher_layout_max_rows";
    public static final String LAYOUT_COLS = "launcher_layout_max_columns";
    public static final String BADGE_SHORTCUT = "remove_app_shortcut_badge";
    public static final String BADGE_WORK = "remove_app_work_badge";
    public static final String BADGE_CLONE = "remove_app_clone_badge";

    // ── 核心破解 / 安装器 ──
    public static final String ENABLE_32BIT = "force_enable_32_bit_support";
    public static final String REMOVE_ADB_CONFIRM = "remove_adb_install_confirm";
    public static final String ALLOW_DOWNGRADE = "allow_downgrade_install";
    public static final String DISABLE_VERIFY = "disable_signature_verify";
    public static final String FIX_INSTALL_BUTTON = "fix_install_button_display";
    public static final String SKIP_APK_SCAN = "skip_apk_scan";
    public static final String DISABLE_APPDETAIL = "disable_start_appdetail";
    public static final String AUTO_INSTALL = "auto_click_install_button";
    public static final String AUTO_UNINSTALL = "auto_click_uninstall_button";
    public static final String REMOVE_INSTALL_ADS = "remove_install_ads";
    public static final String SHOW_APK_INFO = "show_more_apk_info";
    public static final String ALLOW_DISABLE_SYSAPPS = "allow_disabling_system_apps";

    // ── 截屏 ──
    public static final String SCREENSHOT_PRIVACY = "remove_screenshot_privacy_limit";
    public static final String SCREENSHOT_NO_DELAY = "remove_system_screenshot_delay";

    // ── 系统 ──
    public static final String UNLOCK_RESTRICTED = "auto_unlock_restricted_settings";
    public static final String EYE_TEXTURE = "enable_eyeprotect_paper_texture_support";

    // ── 值类型（非开关）──
    public static final String TILE_SCRIPT = "tile_script_command";

    private static final Map<String, Object> DEFAULTS = new HashMap<>();
    static {
        DEFAULTS.put(FOLDER_BG, Boolean.FALSE);
        DEFAULTS.put(LAYOUT_CUSTOM, Boolean.FALSE);
        DEFAULTS.put(LAYOUT_ROWS, 6);
        DEFAULTS.put(LAYOUT_COLS, 4);
        DEFAULTS.put(BADGE_SHORTCUT, Boolean.FALSE);
        DEFAULTS.put(BADGE_WORK, Boolean.FALSE);
        DEFAULTS.put(BADGE_CLONE, Boolean.FALSE);
        DEFAULTS.put(ENABLE_32BIT, Boolean.FALSE);
        DEFAULTS.put(REMOVE_ADB_CONFIRM, Boolean.FALSE);
        DEFAULTS.put(ALLOW_DOWNGRADE, Boolean.FALSE);
        DEFAULTS.put(DISABLE_VERIFY, Boolean.FALSE);
        DEFAULTS.put(FIX_INSTALL_BUTTON, Boolean.FALSE);
        DEFAULTS.put(SKIP_APK_SCAN, Boolean.FALSE);
        DEFAULTS.put(DISABLE_APPDETAIL, Boolean.FALSE);
        DEFAULTS.put(AUTO_INSTALL, Boolean.FALSE);
        DEFAULTS.put(AUTO_UNINSTALL, Boolean.FALSE);
        DEFAULTS.put(REMOVE_INSTALL_ADS, Boolean.FALSE);
        DEFAULTS.put(SHOW_APK_INFO, Boolean.FALSE);
        DEFAULTS.put(ALLOW_DISABLE_SYSAPPS, Boolean.FALSE);
        DEFAULTS.put(SCREENSHOT_PRIVACY, Boolean.FALSE);
        DEFAULTS.put(SCREENSHOT_NO_DELAY, Boolean.FALSE);
        DEFAULTS.put(UNLOCK_RESTRICTED, Boolean.FALSE);
        DEFAULTS.put(EYE_TEXTURE, Boolean.FALSE);
        DEFAULTS.put(TILE_SCRIPT, "");
    }

    /** 开关类 key 的中文标题（UI 展示 + 勾选页排序） */
    public static final String[][] SWITCHES = {
            // { key, 标题, 分组 }
            {FOLDER_BG, "移除文件夹预览背景", "桌面"},
            {LAYOUT_CUSTOM, "桌面布局行列数自定义", "桌面"},
            {BADGE_SHORTCUT, "移除快捷方式徽标", "桌面"},
            {BADGE_WORK, "移除工作空间徽标", "桌面"},
            {BADGE_CLONE, "移除应用分身徽标", "桌面"},
            {ENABLE_32BIT, "强制启用 32 位支持（需重启）", "核心破解"},
            {REMOVE_ADB_CONFIRM, "移除 ADB 安装确认", "核心破解"},
            {ALLOW_DOWNGRADE, "允许降级安装", "核心破解"},
            {DISABLE_VERIFY, "跳过签名/病毒验证（需重启）", "核心破解"},
            {FIX_INSTALL_BUTTON, "修复安装按钮显示异常", "核心破解"},
            {SKIP_APK_SCAN, "跳过 APK 安全扫描", "核心破解"},
            {DISABLE_APPDETAIL, "禁止启动 AppDetail 定制扫描", "核心破解"},
            {AUTO_INSTALL, "自动点击安装按钮", "核心破解"},
            {AUTO_UNINSTALL, "自动点击卸载按钮", "核心破解"},
            {REMOVE_INSTALL_ADS, "移除安装完成广告", "核心破解"},
            {SHOW_APK_INFO, "安装页顶部显示包信息", "核心破解"},
            {ALLOW_DISABLE_SYSAPPS, "允许卸载/停用系统应用", "核心破解"},
            {SCREENSHOT_PRIVACY, "解除长截图/隐私页截屏限制", "截屏"},
            {SCREENSHOT_NO_DELAY, "移除系统截屏长按延迟", "截屏"},
            {UNLOCK_RESTRICTED, "自动解锁受限制的设置", "系统"},
            {EYE_TEXTURE, "护眼模式纸质纹理支持（需重启）", "系统"},
    };

    private static final Map<String, String> TITLES = new HashMap<>();
    static {
        for (String[] s : SWITCHES) TITLES.put(s[0], s[1]);
    }

    public static Object defaultValue(String key) {
        Object v = DEFAULTS.get(key);
        return v != null ? v : Boolean.FALSE;
    }

    public static String title(String key) {
        String t = TITLES.get(key);
        return t != null ? t : key;
    }

    public static boolean isSwitch(String key) {
        return TITLES.containsKey(key);
    }

    /** 分组顺序与每组开关 key 列表（UI 用） */
    public static final String[] GROUP_ORDER = {"桌面", "核心破解", "截屏", "系统"};

    public static String[] groupSwitches(String group) {
        int n = 0;
        for (String[] s : SWITCHES) if (s[2].equals(group)) n++;
        String[] out = new String[n];
        int i = 0;
        for (String[] s : SWITCHES) if (s[2].equals(group)) out[i++] = s[0];
        return out;
    }

    private FeatureKeys() {
    }
}
