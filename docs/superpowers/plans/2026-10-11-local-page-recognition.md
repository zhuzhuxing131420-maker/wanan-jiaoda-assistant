# Local mini-program page recognition

**Goal:** Recognize the observed check-in layout when accessibility nodes expose no button text. This stage reads and exports page state; actual submission remains outside this change.

**Architecture:** Keep the existing Kotlin/Compose/AccessibilityService stack. A pure recognizer consumes text lines with screen bounds and Beijing time. A bundled ML Kit Chinese model processes screenshots locally. Capture is limited to AppBrandUI windows during the existing two-minute observation session.

**Constraints:** minSdk 29 unchanged; screenshot recognition is supported on API 30+ and reports unsupported on API 29. No device-time changes, remote OCR server, automatic gesture or click. Three delayed capture attempts per mini-program entry, at least three seconds apart, one recognition in flight, at most 80 exported lines. Close hardware buffers and bitmaps, redact name/student-number lines, and discard results after session stop or window change.

## Tasks

- [x] Add pure ScreenTextLine and ScreenBounds data types plus CheckInPageRecognition enum. Write tests for outside-time state, same-day completion, historical completion rejection, header/button distinction, stale dates, and unrelated pages.
- [x] Implement CheckInPageRecognizer.recognize(lines,now). Require the observed layout labels, today's displayed date, and a candidate button between account fields and the records heading. A past date's green status must never count as today's completion. Enforce 19:00 inclusive to 22:00 exclusive before reporting a clickable candidate.
- [x] Add bundled com.google.mlkit:text-recognition-chinese:16.0.1. Request Android's canTakeScreenshot capability and use takeScreenshot only while connected, observing and focused on the mini-program.
- [x] Integrate MiniProgramVision with the service lifecycle. Delay initial capture for page loading and stop on session/window changes. Save only text/bounds/status to files/diagnostics/wechat-ocr.json using AtomicFile; do not save screenshot pixels or claim that OCR verifies program identity.
- [x] Display the recognition status on the existing diagnostic screen. Run local tests and assembleDebug, then update the same-signed APK on the explicit phone serial only.
- [x] Enter the known mini-program, export OCR JSON and confirm its text/status against the visible page. Stop observation, document actual results and any permission or screenshot failures.

Primary references: https://developers.google.com/ml-kit/vision/text-recognition/v2/android and https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#takeScreenshot(int,java.util.concurrent.Executor,android.accessibilityservice.AccessibilityService.TakeScreenshotCallback).

Verified: actual phone result OUTSIDE_TIME after page load; no tap executed. Debug build restricted to the verified arm64-v8a phone ABI to reduce APK size to 50,465,216 bytes; installed and launched. Button-open validation planned for 2026-10-12 19:00-22:00 at the user's request.

