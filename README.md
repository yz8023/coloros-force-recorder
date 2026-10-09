# coloros 专用强制录屏 (v1.0.4)

ColorOS 16 / Android 16 下的 LSPosed 模块：把"系统全屏录屏"的捕获源从虚拟屏幕替换为**物理屏幕镜像层**，实现无需确认弹窗、无人值守的强制录屏。

> 本仓库为对原 APK（v1.0.3，commit b214c2c）的**源码级逆向复原**，并做了 UI 重绘与版本号提升（v1.0.4）。

## 工作原理

模块入口 `com.xiaokan.qzgflp.HookEntry` 只在 `android` 包（system_server）进程激活，命中以下条件后替换捕获源：

| 条件 | 期望值 |
| --- | --- |
| 录屏应用包名 | `com.oplus.screenrecorder` |
| 版本（longVersionCode） | `160005012`（16.5.12） |
| 显示名 | `OPLUSScreenRecording` |
| contentToRecord / displayToRecord | 均为 0（全屏模式） |
| displayId | > 0 且等于 virtualDisplayId |
| consent 弹窗 | 无等待（即"免确认"路径） |
| targetUid | == -1 |

Hook 链（`SystemCaptureHook`）：

1. `com.android.server.wm.ContentRecorder.startRecordingIfNeeded()` 的 before 阶段建立会话帧（`Frame`）记录参数；
2. after 阶段用 `DisplayMirror.create()` 以 **system uid（1000）** 直连 `SurfaceFlingerAIDL`（裸 Binder，`CREATE_CONNECTION=3` / `MIRROR_DISPLAY=5`）创建物理屏镜像层；
3. 经 `SurfaceControl.setLayerStack()` + `show()` 接入 WMS，供录屏虚拟屏取流；
4. 会话结束时移除镜像层并解除全部 hook。

任何一步失败都 **fail-closed**：移除镜像层、unhook 全部回调，录屏退回系统默认行为。

`DisplayMirror` 的 Parcel 布局、反射目标、错误文案与原始实现完全一致（逐字符串对账自原 dex）。

## 界面（v1.0.4 重绘）

- 粉紫渐变背景（`#FFE0EB → #FFF1F6 → #F1E8FF`）+ 白色圆角卡片
- 爱心分区标题、粉粉分隔线、二次元萝莉风配色
- 动态显示本机检测到的录屏应用版本与模块版本（104 / 1.0.4）
- 零新增权限、零新增组件

## 权限与安全

- Manifest **零 uses-permission**：无网络、无存储、无摄像头/麦克风
- 唯一组件为 exported 的 `MainActivity`（纯展示）
- LSPosed 作用域：仅"系统框架"（`android`）
- 构建产物可对照 `app/libs/api-82.jar`（编译期 API 桩，`compileOnly`，不打包进 APK）

## 构建

```bash
./gradlew :app:assembleRelease
```

- minSdk = targetSdk = compileSdk = 36（Android 16）
- Xposed API：`compileOnly(files("libs/api-82.jar"))`（已内置，无需外网仓库）
- Java 11 兼容

## 安装要求

1. 已 root 并安装 LSPosed
2. ColorOS 16 / Android 16，录屏应用版本恰为 **16.5.12**（版本不符时模块自动不生效）
3. 在 LSPosed 中启用模块，作用域勾选"系统框架"，重启系统框架

## 签名说明

本仓库发布的 APK 使用本地生成的开发签名（CN=ColorOSForceRecorder Local）。原 APK 为另一自签证书，安装前需卸载旧版。

## 免责声明

仅供学习 Android framework / SurfaceFlinger / LSPosed Hook 技术研究，请勿用于侵犯他人隐私的场景，使用带来的后果由使用者自行承担。
