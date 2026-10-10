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
    public static final String REMOVE_APP_UNINSTALL_BLACKLIST = "remove_app_uninstall_blacklist";

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

    // ── 系统服务（android 作用域，移植 OShin）──
    public static final String DISABLE_PIN_72H = "disable_pin_verify_per_72_hours";
    public static final String ALLOW_UNTRUSTED_TOUCH = "allow_untrusted_touch";
    public static final String IGNORE_AUDIO_FOCUS = "ignore_audio_focus";
    public static final String DISABLE_ROOT_CHECK = "disable_root_check";

    // ── 核心破解扩充（PMS 签名校验，移植 OShin）──
    public static final String DISABLE_JAR_VERIFIER = "disable_jar_verifier";
    public static final String DISABLE_MESSAGE_DIGEST = "disable_message_digest";
    public static final String BYPASS_ARSC_CHECK = "bypass_arsc_uncompressed_check";
    public static final String BYPASS_MIN_SIG_VERSION = "bypass_min_signature_version_check";
    public static final String BYPASS_V1_SIG_ERRORS = "bypass_v1_signature_errors";
    public static final String ALLOW_SIG_MISMATCH_UPDATE = "allow_signature_mismatch_on_update";
    public static final String ALLOW_SPLIT_SIG_MISMATCH = "allow_mismatched_split_apk_signatures";
    public static final String ALLOW_NONSYSTEM_SHARED_UID = "allow_nonsystem_shared_uid";
    public static final String ALLOW_HIDDEN_API = "allow_system_app_hidden_api";
    public static final String DISABLE_INSTALL_VERIFICATION = "disable_install_verification";
    public static final String PMS_COMMAND = "enable_pms_command";

    // ── 分屏与小窗（android 作用域，移植 OShin）──
    public static final String REMOVE_SMALL_WIN_RESTRICT = "remove_all_small_window_restrictions";
    public static final String FORCE_MULTI_WINDOW_MODE = "force_multi_window_mode";
    public static final String MAX_SMALL_WINDOWS = "max_simultaneous_small_windows";
    public static final String SMALL_WIN_CORNER_RADIUS = "small_window_corner_radius";
    public static final String SMALL_WIN_FOCUSED_SHADOW = "small_window_focused_shadow";
    public static final String SMALL_WIN_UNFOCUSED_SHADOW = "small_window_unfocused_shadow";

    // ── 小布扫一扫（com.coloros.ocrscanner）──
    public static final String FULL_SCREEN_TRANSLATION = "full_screen_translation";

    // ── 智慧侧边栏（com.coloros.smartsidebar，移植 OShin）──
    public static final String REMOVE_APP_ADD_LIMIT = "remove_app_add_limit";

    // ── 主题商店（com.heytap.themestore）──
    public static final String THEME_UNLOCK_VIP = "unlock_themestore_vip_features";
    public static final String THEME_REMOVE_SPLASH_ADS = "remove_themestore_splash_ads";
    public static final String THEME_REMOVE_UPGRADE = "remove_themestore_upgrade";

    // ── 值类型（非开关）──
    public static final String TILE_SCRIPT = "tile_script_command";

    /** 门控开关：关闭时所有 hook 放行（沿用 hook_master 机制，导出为 b_ 前缀） */
    public static final String HOOK_MASTER = "hook_master";

    /** 整数型 key（Cfg.intv 读取 i_ 前缀，-1 = 系统默认不干预） */
    public static final String[] INT_KEYS = {
            LAYOUT_ROWS, LAYOUT_COLS,
            MAX_SMALL_WINDOWS, SMALL_WIN_CORNER_RADIUS,
            SMALL_WIN_FOCUSED_SHADOW, SMALL_WIN_UNFOCUSED_SHADOW,
    };

    private static final Map<String, Object> DEFAULTS = new HashMap<>();
    static {
        DEFAULTS.put(FOLDER_BG, Boolean.FALSE);
        DEFAULTS.put(LAYOUT_CUSTOM, Boolean.FALSE);
        DEFAULTS.put(LAYOUT_ROWS, 6);
        DEFAULTS.put(LAYOUT_COLS, 4);
        DEFAULTS.put(BADGE_SHORTCUT, Boolean.FALSE);
        DEFAULTS.put(BADGE_WORK, Boolean.FALSE);
        DEFAULTS.put(BADGE_CLONE, Boolean.FALSE);
        DEFAULTS.put(REMOVE_APP_UNINSTALL_BLACKLIST, Boolean.FALSE);
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
        // 系统服务
        DEFAULTS.put(DISABLE_PIN_72H, Boolean.FALSE);
        DEFAULTS.put(ALLOW_UNTRUSTED_TOUCH, Boolean.FALSE);
        DEFAULTS.put(IGNORE_AUDIO_FOCUS, Boolean.FALSE);
        DEFAULTS.put(DISABLE_ROOT_CHECK, Boolean.FALSE);
        // 核心破解扩充
        DEFAULTS.put(DISABLE_JAR_VERIFIER, Boolean.FALSE);
        DEFAULTS.put(DISABLE_MESSAGE_DIGEST, Boolean.FALSE);
        DEFAULTS.put(BYPASS_ARSC_CHECK, Boolean.FALSE);
        DEFAULTS.put(BYPASS_MIN_SIG_VERSION, Boolean.FALSE);
        DEFAULTS.put(BYPASS_V1_SIG_ERRORS, Boolean.FALSE);
        DEFAULTS.put(ALLOW_SIG_MISMATCH_UPDATE, Boolean.FALSE);
        DEFAULTS.put(ALLOW_SPLIT_SIG_MISMATCH, Boolean.FALSE);
        DEFAULTS.put(ALLOW_NONSYSTEM_SHARED_UID, Boolean.FALSE);
        DEFAULTS.put(ALLOW_HIDDEN_API, Boolean.FALSE);
        DEFAULTS.put(DISABLE_INSTALL_VERIFICATION, Boolean.FALSE);
        DEFAULTS.put(PMS_COMMAND, Boolean.FALSE);
        // 分屏与小窗
        DEFAULTS.put(REMOVE_SMALL_WIN_RESTRICT, Boolean.FALSE);
        DEFAULTS.put(FORCE_MULTI_WINDOW_MODE, Boolean.FALSE);
        DEFAULTS.put(MAX_SMALL_WINDOWS, -1);
        DEFAULTS.put(SMALL_WIN_CORNER_RADIUS, -1);
        DEFAULTS.put(SMALL_WIN_FOCUSED_SHADOW, -1);
        DEFAULTS.put(SMALL_WIN_UNFOCUSED_SHADOW, -1);
        // 扫一扫 / 主题商店
        DEFAULTS.put(FULL_SCREEN_TRANSLATION, Boolean.FALSE);
        DEFAULTS.put(THEME_UNLOCK_VIP, Boolean.FALSE);
        DEFAULTS.put(THEME_REMOVE_SPLASH_ADS, Boolean.FALSE);
        DEFAULTS.put(THEME_REMOVE_UPGRADE, Boolean.FALSE);
        // 智慧侧边栏
        DEFAULTS.put(REMOVE_APP_ADD_LIMIT, Boolean.FALSE);
        DEFAULTS.put(TILE_SCRIPT, "");
        DEFAULTS.put(HOOK_MASTER, Boolean.TRUE);
    }

    /** 开关类 key 的中文标题（UI 展示 + 勾选页排序） */
    public static final String[][] SWITCHES = {
            // { key, 标题, 分组 }
            {FOLDER_BG, "移除文件夹预览背景", "桌面"},
            {LAYOUT_CUSTOM, "桌面布局行列数自定义", "桌面"},
            {BADGE_SHORTCUT, "移除快捷方式徽标", "桌面"},
            {BADGE_WORK, "移除工作空间徽标", "桌面"},
            {BADGE_CLONE, "移除应用分身徽标", "桌面"},
            {REMOVE_APP_UNINSTALL_BLACKLIST, "移除应用卸载黑名单（需重启）", "桌面"},
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
            // 系统服务
            {DISABLE_PIN_72H, "移除 72 小时密码验证", "系统服务"},
            {ALLOW_UNTRUSTED_TOUCH, "允许不受信任的触摸事件", "系统服务"},
            {IGNORE_AUDIO_FOCUS, "忽略音频焦点请求", "系统服务"},
            {DISABLE_ROOT_CHECK, "禁用系统 Root 检测", "系统服务"},
            // 核心破解扩充
            {DISABLE_INSTALL_VERIFICATION, "禁用安装包验证（需重启）", "核心破解"},
            {DISABLE_JAR_VERIFIER, "禁用 JAR 签名校验（需重启）", "核心破解"},
            {DISABLE_MESSAGE_DIGEST, "禁用消息摘要比对（需重启）", "核心破解"},
            {BYPASS_ARSC_CHECK, "绕过 resources.arsc 压缩检查", "核心破解"},
            {BYPASS_MIN_SIG_VERSION, "绕过最低签名方案版本检查", "核心破解"},
            {BYPASS_V1_SIG_ERRORS, "忽略 V1 签名错误（CorePatch 方式）", "核心破解"},
            {ALLOW_SIG_MISMATCH_UPDATE, "允许覆盖安装签名不一致", "核心破解"},
            {ALLOW_SPLIT_SIG_MISMATCH, "允许 Split APK 签名不一致", "核心破解"},
            {ALLOW_NONSYSTEM_SHARED_UID, "允许非系统应用共享 UID", "核心破解"},
            {ALLOW_HIDDEN_API, "允许系统应用使用隐藏 API", "核心破解"},
            {PMS_COMMAND, "启用 adb shell pm pms 调试命令", "核心破解"},
            // 分屏与小窗
            {REMOVE_SMALL_WIN_RESTRICT, "移除全部小窗限制（需重启）", "分屏与小窗"},
            {FORCE_MULTI_WINDOW_MODE, "强制支持多窗口模式", "分屏与小窗"},
            // 小布扫一扫
            {FULL_SCREEN_TRANSLATION, "全屏翻译（任意应用可用）", "小布扫一扫"},
            // 主题商店
            {THEME_UNLOCK_VIP, "解锁主题商店 VIP 特性", "主题商店"},
            {THEME_REMOVE_SPLASH_ADS, "移除主题商店开屏广告", "主题商店"},
            {THEME_REMOVE_UPGRADE, "移除主题商店升级弹窗", "主题商店"},
            // 智慧侧边栏
            {REMOVE_APP_ADD_LIMIT, "移除侧边栏应用添加上限（需重启）", "智慧侧边栏"},
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
    public static final String[] GROUP_ORDER = {
            "桌面", "核心破解", "截屏", "系统", "系统服务", "分屏与小窗", "智慧侧边栏", "小布扫一扫", "主题商店"};

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
