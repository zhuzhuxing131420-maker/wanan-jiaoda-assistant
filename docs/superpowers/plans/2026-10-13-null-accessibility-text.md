# Null accessibility event text crash fix

Evidence: Vivo V2339A / Android16 crash buffer at2026-10-13 09:41:03.888 reports NullPointerException in AccessibilityEventLogger.record lambda line44, event.text.joinToString calling toString on a null list element. This corrects the earlier incomplete attribution of disconnections to system policy. Prior force-stop and system-removal observations remain distinct evidence.

User authorized continued debugging. Keep original stack and explicit no-submission test mode. Backup: D:/AndroidDev/backups/WanAnJiaoDa-before-null-event-20261013.zip. Inline execution, no Git repository.

- [x] Extract shared pure AccessibilityText.summarize(Iterable<CharSequence?>); first reproduce crash with list containing null and assert valid text survives. Cover all-null, mixed nulls with limit3, whitespace normalization and160-character cap.
- [x] Fix both AccessibilityEventLogger.record and WeChatObserver.record (same unsafe expression) to use shared null-safe summary.
- [ ] Run tests/assembleDebug sequentially, install on10AE4R2CNB001EY only. Validate service connection before/after ordinary WeChat chooser and navigation-only test; capture receipts/crash buffer/owner settings. Do not submit check-in.
- [x] Document actual result, including any remaining new-device navigation or permission issue. Do not claim all past disconnections have the same proven cause.

Physical retest remains pending: installed final build82 tests pass, then USB reconnect/foreground changes prevented a valid launch attempt. Most recent exit is single-cleaner FORCE STOP at10:42, not a new crash. Owner helper service is currently disabled; user readiness and other-tool information requested. Evidence and honest limits: D:/AndroidDev/captures/vivo-2026-10-13/调试记录.md.

Additional physical evidence: first fixed build no longer crashed in event text, but the service was still revoked immediately after chooser selection. Its onUnbind cancellation synchronously emitted observation=false and a surviving collector dereferenced null serviceInfo (10:10:02 crash, pid10802). Add connection-generation guard, cancel observation/vision jobs before stopping coordinators, and null-check/contain service-info updates. Three failing lease tests reproduced the missing lifetime guard.

Prefer public LauncherApps.startMainActivity with Process.myUserHandle (owneruser0); generic Intent remains fallback if unsupported, with the verified ordinary chooser selection available. Three launcher-routing tests, owner-success initially red. Public API contract verified: https://developer.android.com/reference/android/content/pm/LauncherApps. Exact exported WeChat main launcher resolved on device tocom.tencent.mm/.ui.LauncherUI.

Current final build:82 tests passed, D:/AndroidDev/downloads/owner-launch-green.log. Updated APK installed successfully. Service currently needs user re-enabling after prior revocation before physical retest; full new-device compatibility not yet claimed.
