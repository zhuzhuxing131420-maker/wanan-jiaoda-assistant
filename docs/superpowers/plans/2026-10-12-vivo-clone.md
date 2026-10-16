# Vivo ordinary WeChat chooser support

User confirmed ordinary WeChat (left), deviceV2339A / Android16 / serial10AE4R2CNB001EY. Current chooser packagecom.vivo.doubleinstance / DoubleAppResolverActivity, owneruser0; cloneprofile999. Preserve Kotlin/Compose/native accessibility stack and no-submission test mode.

Design: recognize only this known system chooser with heading and clearly distinct ordinary/clone labels. Read bounded native nodes before considering OCR; select ordinary WeChat after two stable frames through the same gesture adapter. Keep waiting within navigation deadline rather than treating the chooser as a foreign app. Do not alter system clone defaults or secure accessibility settings. Compare owner service settings before/after and verify selected WeChat runs underuser0; profile999 has separate accessibility state and its empty service list must not be mistaken for owner permission loss.

- [x] Write failing pure tests for correct chooser, missing title, wrong package, ambiguous labels and once-only two-frame primary selection.
- [x] Implement package-aware frames and chooser policy; service gathers bounded text nodes and can dispatch only PRIMARY_WECHAT gestures in the known chooser package.
- [ ] Build/test and install on this new device only. Run native no-submission navigation through chooser, inspect receipts, activity user and owner service state.
- [x] Document actual result and any remaining new-device navigation issue. Original OnePlus evidence does not establish Vivo compatibility.

71 tests pass. Native ordinary-WeChat selection completed and foreground WeChat was user0. About182ms later the owner secure settings no longer contained the helper accessibility component; navigation failed without submission. Full navigation verification remains unfinished. Device USB then disconnected; further system-log investigation requires reconnection. Evidence: D:/AndroidDev/captures/vivo-2026-10-12/调试记录.md.
