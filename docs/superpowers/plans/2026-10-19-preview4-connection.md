# Preview 4 Connection Protection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement and independently review each task.

**Goal:** 降低后台清理导致的无障碍断连，恢复保护并准确显示授权和连接状态。

**Architecture:** 独立前台保护服务和持久开关管理普通进程恢复；已有无障碍服务仍只由系统绑定。权限模型分离授权与连接，并订阅连接变化。主协调者集成设置卡片、构建和真机验证。

**Tech Stack:** Kotlin、Android Service / Notification / Settings.Secure、Compose、StateFlow、JUnit4、Gradle。

## Global Constraints

- 用户明确同意常驻通知；默认关闭，主动开启，能关闭且持久生效。
- 不修改安全设置、不授予隐藏权限、不触发真实签到、不上传设备原始记录。
- 空闲保护不读取屏幕、不新增定时器/唤醒锁/重启循环。
- 同签名升级 versionCode 4 / versionName 0.1.0-preview.4；minSdk29 / targetSdk35。
- 复用当前工作区和 codex/preview4-connection-protection 任务分支；仓库外工作区保护已核验。

### Task 1: 准确授权与连接状态

**Files:** permission/PermissionState.kt、PermissionChecker.kt、PermissionViewModel.kt；配置检查/设置/首页/无障碍测试相关 UI，必要的纯 Kotlin 状态解析器与测试。

**Interface:** 保留 accessibilityEnabled 表示授权，增加 accessibilityConnected 表示连接；读取失败必须区别未授权。ViewModel 合并连接 StateFlow 与返回应用时的权限快照。其他任务不编辑这些文件。

- [ ] 先测试完全/缩写组件名解析、其他服务不算本应用授权、已授权断开不显示待开启、连接回调更新、读取失败不显示未授权。
- [ ] 将授权来源改为系统安全设置中的组件集合，运行时已连接也是有效证据；移除把未运行服务当未授权的判断。
- [ ] 统一配置检查/设置/首页/测试页状态，保留打开系统设置入口，不自动开关授权。
- [ ] 执行目标测试（先红后绿），独立审查并仅提交本任务路径。

### Task 2: 用户可控前台保护服务

**Files:** 新建 service/ConnectionProtectionController.kt、ConnectionProtectionService.kt、ConnectionProtectionPolicy.kt；新建 ui/component/ConnectionProtectionCard.kt；修改 AndroidManifest.xml、MainActivity.kt；新增策略测试。

**Interface:** `ConnectionProtectionController.state: StateFlow<ConnectionProtectionState>`；`setEnabled(context: Context, enabled: Boolean)`；`resume(context: Context)`。`ConnectionProtectionState` 含 enabled/running/message。`ConnectionProtectionCard(state, onEnabledChange)` 为纯展示组件。主协调者接入现有 SettingsScreen/AppNavigation。

- [ ] 纯策略测试默认关闭、已开启的普通重建恢复、关闭后 null intent 不恢复、未连接不显示成功、启动失败可见；先红后绿。
- [ ] 独立 SharedPreferences 只保存保护偏好，不改动现有 DataStore/数据库。正常停止先持久关闭；启动失败保留明确状态并允许用户重试。
- [ ] 新服务非导出，FOREGROUND_SERVICE 和 SPECIAL_USE 声明完整、subtype 用途明确；及时 startForeground，enabled 时 START_STICKY，禁用时 STOP_FOREGROUND_REMOVE/stopSelf/START_NOT_STICKY。
- [ ] 常驻低重要性通知使用显式不可变 PendingIntent，显示连接状态、提供停止保护；观察 connected StateFlow，无轮询/画面读取。
- [ ] Activity 可见时 resume 根据持久开关恢复；后台启动限制被拒绝时展示，不崩溃、不循环重试。关闭保护不关闭无障碍。
- [ ] 目标测试与编译通过后独立审查，仅提交本任务文件。

### Task 3: 集成、真机与发布

**Files:** SettingsScreen.kt、AppNavigation.kt、app/build.gradle.kts、README.md、本计划；必要修订由主协调者完成。

- [ ] 在设置加入保护卡片并订阅 Controller.state；记录默认关闭、通知与停止后的体验。
- [ ] 更新版本4，在仓库外唯一构建目录运行全部单测和 assembleDebug，保留旧 APK；核验 v1/v2/v3、原证书、版本、ZIP 和16KiB对齐。
- [ ] 已连接手机同签名覆盖升级，核实显示“已授权未连接”；启用保护、初次恢复系统绑定（如必要，明确记录这一次手动开关），之后测试后台/普通进程中断后的实际恢复，并确认关闭后不重启。失败不得宣称彻底解决。
- [ ] 仅导航验证不提交；整体审查、秘密/路径审核后提交并同步 main，独立 Preview4 标签和发布，核对上传摘要与远端提交。

## 官方依据

- https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- https://developer.android.com/develop/background-work/services/fgs/service-types#special-use
- https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping
