# 解除截图录像限制 (v1.1.1)

基于 **libxposed API 102** 的 LSPosed 模块：在 system_server 层解除系统截图与屏幕录像的**安全层限制**，让涉及 `FLAG_SECURE` / Secure Layer 的窗口在任何截图、录屏、投屏路径下都能被完整捕获。

> 前身是 coloros 专用强制录屏（v1.0.4，对原 APK 的源码级逆向复原）；v1.1.0 起按 LSPosed 官方 `DisableFlagSecure` 方案（LuckyTool 集成版）重写功能。v1.1.1 补齐现代 Xposed API 的模块识别声明。

## 工作原理

模块入口 `com.xiaokan.qzgflp.HookEntry` 继承 `io.github.libxposed.api.XposedModule`，在 `android` 包（system_server）与截图相关组件激活：

| Hook 目标 | 作用 |
| --- | --- |
| `WindowState.isSecureLocked` | 窗口级 FLAG_SECURE 解除（Surface 创建路径保留原语义，防止破坏 DRM） |
| `ScreenCapture[Internal]$CaptureArgs` | 捕获参数强制包含安全层（`mSecureContentPolicy=1` / `mCaptureSecureLayers=true`），覆盖 `nativeCaptureDisplay` / `nativeCaptureLayers` |
| `ScreenshotHardwareBuffer.containsSecureLayers` | 捕获结果不再被判定为含安全层 |
| `DisplayControl.createVirtualDisplay` / `SurfaceControl.createDisplay` | 系统虚拟显示强制 secure 属性 |
| `VirtualDisplayAdapter.createVirtualDisplayLocked` | MediaProjection 录屏虚拟屏补 `VIRTUAL_DISPLAY_FLAG_SECURE`，录屏不再黑屏 |
| `WindowManagerService.registerScreenRecordingCallback` (V+) | 屏蔽录屏检测回调 |
| `ActivityTaskManagerService.registerScreenCaptureObserver` (U+) | 屏蔽截屏监听通知 |
| `ActivityManagerService.checkPermission` (S~T) | `CAPTURE_BLACKOUT_CONTENT` 放行为 `READ_FRAME_BUFFER` |
| HyperOS `WindowManagerServiceImpl.notAllowCaptureDisplay` / One UI `WmScreenshotController.canBeScreenshotTarget` / ColorOS `OplusLongshotMainWindow.hasSecure` | 厂商私有限制解除 |
| `OplusScreenCapture$CaptureArgs$Builder.setUid` (ColorOS 15+) | 截图 uid 校验旁路 |

system_server 内先对 `WindowStateAnimator.createSurfaceLocked`、`WindowManagerService.relayoutWindow` 及 lambda 合成类做 **deoptimize**，保证 hook 生效。

覆盖范围：framework 层解除（安全层由图形栈在内核之上绘制，第三方 root 机型同样适用），已内置 ColorOS 16 / HyperOS / One UI / AOSP 路径。

## 权限与安全

- Manifest **零 uses-permission**：无网络、无存储
- 唯一组件为 exported 的 `MainActivity`（纯展示 + LSPosed 模块设置入口）
- 入口声明（现代 Xposed API）：`META-INF/xposed/java_init.list`（入口类）、`META-INF/xposed/module.prop`（`minApiVersion=100` / `targetApiVersion=102` / `staticScope=false`）、`META-INF/xposed/scope.list`（默认作用域）
- 兼容声明（旧版 Xposed 管理器）：Manifest 保留 `xposedmodule` / `xposedminversion` meta-data
- 依赖：`compileOnly(files("libs/libxposed-api-102.0.0.jar"))`（已内置，无需外网仓库）

## 构建

```bash
./gradlew :app:assembleRelease
```

- compileSdk = 36，minSdk = 28，targetSdk = 36
- Xposed API：libxposed API 102（`io.github.libxposed:api:102.0.0`）
- Java 11 兼容

## 安装要求

1. 已 root 并安装支持 libxposed 新 API 的 LSPosed
2. 在 LSPosed 中启用模块，作用域勾选「系统框架」，可选加截图相关组件，重启系统框架
3. 在 LSPosed 停用模块并重启即可完全恢复系统默认行为

## 签名说明

v1.0.4 / v1.1.0 / v1.1.1 使用同一本地开发签名（SHA-256 `db7c3393...`），可直接覆盖安装。

## 免责声明

仅供学习 Android framework / SurfaceFlinger / libxposed 技术研究，请勿用于侵犯他人隐私的场景，使用带来的后果由使用者自行承担。
