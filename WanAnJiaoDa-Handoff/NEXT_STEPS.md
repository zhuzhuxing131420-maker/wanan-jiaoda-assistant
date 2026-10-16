# WanAnJiaoDa 后续开发步骤

## 当前状态

- Android 10+，minSdk 29
- Kotlin、Jetpack Compose、Material 3
- DataStore 设置系统，Room 历史记录框架
- AlarmManager 本地定时调度
- AccessibilityService 框架与微信观察器
- UI-Final 已完成；Android 14 AVD 基础回归通过
- 尚未验证真实微信页面节点；尚未实现实际签到

## Phase 4B：真实微信页面采集

1. 在真实 Android 设备安装 Debug APK，开启 WanAnJiaoDa 无障碍服务。
2. 进入设置中的微信观察测试页面并开启观察模式。
3. 打开微信，进入“晚安交大”小程序，手动走完整签到流程。
4. 记录 packageName、eventType、className、text、viewIdResourceName、bounds。
5. 确认“签到”按钮文本是否暴露、节点是否 clickable、是否有稳定 View ID、页面使用 WebView / Canvas / 原生节点，以及已签到和签到成功状态的表现。

## Phase 4C：实现自动签到

根据 Phase 4B 采集结果决定定位策略，优先级：稳定 viewId、text / contentDescription、节点层级关系、bounds / 坐标兜底，OCR 最后考虑。不要一开始依赖绝对坐标。

建议状态机：IDLE、LAUNCHING_WECHAT、OPENING_MINI_PROGRAM、WAITING_FOR_PAGE、LOCATING_CHECK_IN、CHECKING_ALREADY_DONE、CLICKING_CHECK_IN、VERIFYING_RESULT、SUCCESS、FAILED、RETRY_PENDING。

接入 CheckInTask、WeChatObserver / AccessibilityService，以及自动模式和半自动模式。

## Phase 5：稳定性

真实签到打通后，再处理自动重试、微信启动与小程序加载超时、已签到检测、失败通知、锁屏状态、vivo / Xiaomi / OPPO 后台限制、真机精确闹钟和 Android 10 至最新版本兼容性。

## 约束

- 调度统一使用北京时间 UTC+8。
- 锁屏密码状态无法保证完全自动 UI 操作。
- 微信版本和小程序更新可能改变节点结构。
- 不要长期监听微信；仅在任务执行期间启用自动化。
- 保持轻量，不做持续 OCR 或轮询。
