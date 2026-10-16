# Node diagnostics implementation plan

> **For agentic workers:** Execute this authorized plan inline, task by task, with test and device verification checkpoints.

**Goal:** Capture the complete WeChat accessibility tree from the app's own service and export bounded diagnostic records to app-private files.

**Architecture:** A pure bounded walker determines traversal and release behavior. An Android adapter reads rootInActiveWindow on the service thread. Opt-in observation takes throttled snapshots and atomically saves the latest JSON, independently of the Compose page being visible.

**Tech Stack:** Existing Kotlin, Compose, StateFlow, AccessibilityService; Android built-in JSON; JUnit 4 for local tests.

## Global constraints

- Keep minSdk 29, targetSdk 35, compileSdk 36 and the existing application ID.
- No automatic click, screenshot OCR, network call, or actual check-in in this change.
- Snapshots only while WeChat observation is enabled, only for com.tencent.mm.
- Maximum 200 nodes and depth 12; one snapshot per 1500 ms; keep only latest snapshot on disk.
- Release acquired child references even if reading a node throws; don't retain AccessibilityNodeInfo instances.
- Do not print personal node text into Logcat. Log only node count, capture result and alarm timing metadata.
- The extracted project has no Git repository. Preserve a source ZIP backup and work in the current checkout.

## Task 1: Bounded traversal

Files: app/src/main/java/com/wananjiaoda/assistant/automation/BoundedTreeWalker.kt; app/src/test/java/com/wananjiaoda/assistant/automation/BoundedTreeWalkerTest.kt; app/build.gradle.kts.

Interface: collect(root, childCount, childAt, describe, release, maxNodes=200, maxDepth=12): TreeWalkResult<R>, where describe(node,index,parentIndex,depth):R, and release applies to acquired children only.

- [x] Original assembleDebug baseline passed; the original archive had no unit tests.
- [x] Add JUnit 4.13.2 and tests for parent relationships, node cap, depth cap and child release on exception. Watch failure before implementing traversal.
- [x] Implement bounded depth-first traversal; run testDebugUnitTest and require all tests passing.

## Task 2: Service capture and export

Files: new automation/WeChatTreeDiagnostics.kt; existing automation/WeChatObserver.kt and service/WanAnJiaoDaAccessibilityService.kt; res/xml/accessibility_service_config.xml.

Interface: WeChatTreeDiagnostics.capture(service):Unit; report:StateFlow<String>; resetReport():Unit. Capture only if observing is true and root package is com.tencent.mm; timestamp measured with elapsedRealtime for throttle. JSON path is files/diagnostics/wechat-tree.json; bounded events are embedded in the same JSON.

- [x] Capture values for index,parentIndex,depth,text,contentDescription,viewId,className,bounds,clickable,enabled,packageName.
- [x] Use at most 200 nodes and depth 12; write bounded event list and latest tree using AtomicFile in a single IO coroutine with one capture in flight. Do not let a stopped/restarted session publish an old result.
- [x] Add content-change events for WeChat observation and a 1500 ms throttle. Report disconnected/no-root/read errors and skip other-package roots.
- [x] Log connection and capture counts; clear live state when the service disconnects. Retain the last explicitly captured file for developer export.

## Task 3: UI and alarm evidence

Files: ui/screen/WeChatObserverScreen.kt; ui/navigation/AppNavigation.kt; scheduler/AlarmReceiver.kt.

- [x] Show latest capture status on the existing observer screen. State the sampling behavior and local export location in the diagnostic UI.
- [x] Record scheduled trigger, actual receiver timestamp and lateness from the received alarm intent; do not change scheduling policy in this diagnostics change.
- [x] Run testDebugUnitTest and assembleDebug; install the same-signed APK with adb install -r.
- [x] Enable observation and enter the confirmed mini-program, without running uiautomator dump during the capture. Pull JSON using run-as for the debug package. Confirm package filter, node cap and tree content against the visible page.
- [x] Stop observation and update the device debug report. If the page remains inaccessible, report that evidence instead of asserting a stable button selector.

Verified: 20 total local tests now pass; service tree and local OCR device evidence are in D:/AndroidDev/captures/wanan-2026-10-11. Observation has a two-minute deadline, tested with a monotonic fake clock.

