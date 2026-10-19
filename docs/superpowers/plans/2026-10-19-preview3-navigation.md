# Preview 3 Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** 修复导航超时和已复现的入口识别问题，交付可追踪的 Preview 3。

**Architecture:** 用独立的协程超时回调消除协调器与 CoroutineScope.cancel 的调用歧义。用受限的本机标签诊断取得真实 OCR 差异，再以精确入口规则或区域识别解决；沿用已有双帧与目标确认策略。

**Tech Stack:** Kotlin、Android AccessibilityService、ML Kit 中文 OCR、JUnit4、Gradle。

## Global Constraints

- 不安装卸载其他应用、不触发签到。只导航动作获用户批准。
- 不上传设备原始诊断、私钥和本机配置。
- 不用固定坐标或截断名称替代完整目标名称确认。
- minSdk 29 / targetSdk 35，签名 v1/v2/v3，versionCode 3。
- 工作区已在仓库外保护，当前任务使用 codex/preview3-navigation-fix。

### Task 1: 导航超时

**Files:** 新建 automation/NavigationTimeout.kt 与同目录测试；修改 automation/WeChatNavigationTest.kt（均位于现有 app/src/main 或 test/java/com/wananjiaoda/assistant 下）。

**Interfaces:** 接收 CoroutineScope、等待毫秒与普通无接收者回调，返回 Job。调用方通过取消旧 Job 防止旧会话停止新会话。

- [ ] 写回归测试，使用 runBlocking、CompletableDeferred 和 withTimeout 断言短期限后回调触发、取消后不触发。
- [ ] 运行测试确认缺失实现失败；实现 `scope.launch { delay(timeoutMillis); onTimeout() }`。
- [ ] 协调器用显式 `WeChatNavigationTest.cancel(...)` 回调，检查全部取消入口。
- [ ] 运行目标测试与全部单元测试并保存结果。

### Task 2: 本机入口诊断与实测修复

**Files:** 修改 automation/MiniProgramVision.kt；按证据新增纯 Kotlin 候选标签诊断或入口解析器及单元测试。

**Interfaces:** 输入 `List<ScreenTextLine>`，输出仅“最近”“晚安”前缀的短候选标签与边界。诊断只落本机文件；不改变点击授权。

- [ ] 先测试候选范围，姓名、学号、聊天正文、长标签不进入诊断。
- [ ] 将诊断接到已有 navigation JSON 字段，构建唯一诊断目录，验证签名后覆盖安装已授权手机。
- [ ] 仅导航测试，收集实际候选后补精确入口回归测试，再实现最小修复。不得在证据不足时扩大自动点击条件。
- [ ] 验证重复名称、不完整名称和其他窗口仍拒绝操作；确认导航进入目标页但不提交。

### Task 3: 交付

**Files:** app/build.gradle.kts、README.md。

- [ ] 将版本设为 3 / 0.1.0-preview.3，记录实测范围和限制。
- [ ] 在唯一输出目录执行 `gradlew.bat testDebugUnitTest assembleDebug --console=plain`；Junction 目标已核对，不运行外部覆盖脚本。
- [ ] 验证 APK 结构、对齐、版本和原证书下的 v1/v2/v3；最终真机仅导航。
- [ ] 审核明确路径、提交中文版本；审查将发布的全部变更和附件后同步独立 Preview，保留旧版本。
