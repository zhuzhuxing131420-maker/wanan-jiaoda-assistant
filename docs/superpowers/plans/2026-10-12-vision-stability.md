# Vision stability implementation plan

Goal: one capture loop, window-safe fresh frames, faster verified navigation, and non-submitting physical tests.
Stack: existing Kotlin/Compose/ML Kit/AccessibilityService. Inline execution, backup retained, no Git.

- [x] Reproduce Discover-without-Moments and faster post-gesture stable frames in WeChatNavigationAttemptTest. Add CaptureThrottleTest for650ms minimum, monotonic clock and no reset across sessions; WeChatWindowTrackerTest for generic widgets, mini-program list transitions and foreign package reset.
- [x] Run testDebugUnitTest and preserve expected failing assertions before policy implementation.
- [x] Implement CaptureThrottle.tryAcquire(nowElapsed) and WeChatWindowTracker.update(packageName,className)/isMiniProgram; integrate single loop in service with750/1000/3500ms intervals, ordinary per-window quota and sustained foreign-window timeout.
- [x] Bind screenshots to request windowId in MiniProgramVision; reject late changed-window results, cap image at2000px and export per-session counters/latencies in wechat-ocr.json.
- [x] Add navigationOnly start mode and pure handoff disposition test; UI exposes “仅测试打开路径（不打卡）”, receipts indicate submissionEnabled=false.
- [x] Test and build, install same-signed APK on3B15AP019ZK00000; run three non-submitting native navigation trials, including full WeChat home route. Compare timing/counters, preserve receipts, stop observation and update docs with actual limits.

## Verified outcome

64 tests pass; expected red assertions preserved before fixes. Three native, non-submitting phone runs reached READY: 9.030s, 2.469s, 11.503s. Capture counters:28 requests /28 recognized /0 screenshot errors /0 discarded. Third run has the same five actions as morning baseline25.512s, about54.9% lower total duration. All receipts submissionEnabled=false. Observation stopped; service remains bound. Evidence: D:/AndroidDev/captures/wanan-2026-10-12-vision-speed/验证记录.md. Long-term service survival and scheduled execution are not claimed.

