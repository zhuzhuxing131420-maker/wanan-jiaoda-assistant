# 晚安交大助手 · WanAnJiaoDa

一个用于“晚安交大地学人”微信小程序签到流程的 Android 辅助工具，使用 Kotlin、Jetpack Compose 和无障碍服务实现页面导航、手机端文字识别、定时任务与本地记录。

**当前为 Preview 预览版。** 部分流程已在真机验证，长期后台运行、不同厂商设备和真实定时提交仍需继续测试。

[下载 Preview](https://github.com/zhuzhuxing131420-maker/wanan-jiaoda-assistant/releases/tag/v0.1.0-preview.1) · [全部发布版本](https://github.com/zhuzhuxing131420-maker/wanan-jiaoda-assistant/releases) · [反馈问题](https://github.com/zhuzhuxing131420-maker/wanan-jiaoda-assistant/issues)

## 功能

- **页面导航**：从微信进入发现、小程序、最近使用和目标签到页，核对完整目标名称，支持有限的入口恢复。
- **页面识别与单次签到**：结合无障碍节点和手机端 OCR 判断页面状态，两帧确认后最多提交一次，再核对结果。
- **定时任务**：按北京时间、选定日期和时间调度；锁屏或服务未连接时保存待执行任务，恢复后重新检查执行条件。
- **本地状态与历史**：通过 DataStore 保存设置，通过 Room 保存签到记录。
- **诊断与测试入口**：提供页面观察、“仅测试打开路径（不打卡）”及“1分钟后测试定时导航（不打卡）”，便于先验证设备环境。

## 下载与使用

在 [Preview 发布页](https://github.com/zhuzhuxing131420-maker/wanan-jiaoda-assistant/releases/tag/v0.1.0-preview.1) 展开 **Assets**，下载 `WanAnJiaoDa-v0.1.0-preview.1-arm64-debug.apk`。同页提供 `SHA256SUMS.txt` 和 `build-manifest.json`，用于核对下载文件、源码提交和构建信息。

| 项目 | 当前 Preview |
| --- | --- |
| 应用版本 | `0.1.0`，`versionCode 1` |
| 发布标签 | `v0.1.0-preview.1` |
| 安装要求 | Android 10 / API 29 及以上，`arm64-v8a` |
| 视觉识别流程 | 需要 Android 11 / API 30 及以上；Android 10 仅保留节点观察等支持的功能 |
| 构建类型 | Debug 签名测试包 |

首次使用建议：

1. 安装应用，根据系统提示开启助手的无障碍服务；定时与提醒功能按需授予闹钟、通知等权限。
2. 确认使用的微信账号，先手动打开一次目标小程序，使其出现在“最近使用”中。
3. 保持手机解锁，先使用“仅测试打开路径（不打卡）”检查页面导航。
4. 确认页面和账号正确后，再按需使用单次测试或设置定时任务。启用真实签到入口可能实际提交签到。

已有同包名应用的签名若不同，可能无法直接更新。保留现有数据并评估迁移后再处理安装问题，避免直接卸载造成记录丢失。请在本人授权的账号及允许的使用场景下使用本工具。

## 当前验证与限制

`v0.1.0-preview.1` 从提交 `8cee209a0a54c8a52b6d4d21147968a93c5cba36` 在独立目录构建：**126 项单元测试通过**，45 项 Gradle 任务实际执行，APK 签名和文件校验通过。此结果对应该次发布，不代表未来每次修改都已验证。

- 已有一加设备上的页面导航、单场景签到和锁屏等待后恢复导航记录；这些结果不代表所有设备都兼容。
- Vivo 等厂商的无障碍服务、后台限制和分身行为仍需分别验证。
- 长期后台、重启恢复、闹钟精度及开放时段真实定时提交仍需单独验证；不能保证任务一定准时或成功执行。
- 用户强行停止应用或关闭无障碍服务后，不能保证自动恢复。
- 小程序或微信页面变化、网络状态、页面遮挡和权限状态可能影响识别；未知或无法确认的状态应停止并人工检查。

## 从源码构建

源码目录为 [`WanAnJiaoDa-Handoff/source/WanAnJiaoDa`](WanAnJiaoDa-Handoff/source/WanAnJiaoDa)。当前验证环境使用 JDK 21、Android SDK Platform 36、Build Tools 36.0.0；项目 JVM 目标为 Java 17，Gradle Wrapper 为 8.13。

在 Windows 上建议使用英文路径检出，并通过 Android Studio 配置本机 SDK 路径，或配置好 `JAVA_HOME`、`ANDROID_HOME` 等环境变量。`local.properties` 是本机配置，不应提交。

```powershell
git clone https://github.com/zhuzhuxing131420-maker/wanan-jiaoda-assistant.git D:\Projects\WanAnJiaoDa
Set-Location D:\Projects\WanAnJiaoDa\WanAnJiaoDa-Handoff\source\WanAnJiaoDa

.\gradlew.bat testDebugUnitTest assembleDebug --console=plain
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。首次构建需要下载 Gradle 与依赖；当前 Debug 配置仅包含 `arm64-v8a` 原生库。

仓库根目录的开发脚本包含原开发电脑的固定路径，换电脑时应先阅读并适配。更多版本管理与迁移说明见[使用与迁移指南](docs/version-management/使用与迁移.md)。

## 数据与反馈

设置、签到历史与诊断记录保存在手机本地。页面识别使用手机端处理；分享日志、页面内容或诊断导出前，请核查其中是否含个人信息。

反馈时建议提供应用版本、设备型号、Android / 微信版本、复现步骤和经过脱敏的错误信息。不要上传账号密码、Token、私人配置、完整页面记录或包含姓名、学号等信息的截图。

## 许可证与美术素材

本项目采用**分项授权**：

| 内容 | 授权 |
| --- | --- |
| 本项目自行编写的程序代码 | **GNU GPL v3.0 only**，SPDX：`GPL-3.0-only`，见 [LICENSE](LICENSE) |
| 来自 Maintune 的美术素材及本项目对该素材的美术改编 | **CC BY-NC-SA 4.0**，见[美术素材署名与授权](ARTWORK-NOTICE.md)及[许可全文](LICENSES/CC-BY-NC-SA-4.0.txt) |
| 第三方库、SDK 与工具 | 保留其各自的许可证和版权声明 |

美术素材作者为 **[mcxianyujun](https://github.com/mcxianyujun)**，来自 **[mcxianyujun/maintune](https://github.com/mcxianyujun/maintune)**。本项目使用 `frontend/src/assets/theme/luo-tianyi-nav-sprite.webp` 的第一个 `256×256` 单元，裁剪并重新编码为 `tianyi_chibi.webp`，用于头像、空状态、启动画面和应用图标。

素材要求**署名、非商业性使用、相同方式共享**，许可范围以作者拥有或控制的权利为限。洛天依 / VSINGER 的角色名称、设定、形象、官方设计、商标和底层 IP 权利不包含在该素材许可中；这些同人视觉不代表官方背书。[上游素材授权清单](https://github.com/mcxianyujun/maintune/blob/main/docs/licensing/asset-attribution.md) · [CC 许可说明](https://creativecommons.org/licenses/by-nc-sa/4.0/deed.zh-hans)

**素材不因放入本仓库而改为 GPL 授权，也不是 GPL 与 CC 的任选双重许可。** 包含该素材的 APK 不能仅凭代码的 GPL 标识视为整体可商用或可仅按 GPL 再分发；再分发时应核对各部分授权。需要商业使用或仅包含 GPL 兼容内容的发行版时，应先替换相应素材或取得所需的额外授权。

Copyright (C) 2026 zhuzhuxing，限本项目自有代码；第三方素材版权归各自权利人所有。
