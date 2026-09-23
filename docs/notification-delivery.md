# Notification delivery verification

**English** · [简体中文](notification-delivery.zh-CN.md)

Date: 2026-09-24. Device: vivo V2454A, Android 16. Debug and Release share `NotiqListener` and `NotificationRelay`; there is no separate experimental service, entrypoint or fixture-specific processing path.

## Current behavior

- Observe mode evaluates and records decisions, leaving original notifications in place.
- Filtering first saves recoverable text, confirms that the original is still the current version, requests removal and checks the result, then calls the selected decision service.
- Decisions meeting the rule and threshold show “Filtered”. Keep decisions, uncertainty, request failures and busy handling produce a Notiq notification and show “Forwarded”.
- If Notiq lacks notification permission, the original remains. If permission changes after removal and forwarding fails, the pending delivery is retained.
- Ongoing notifications, group summaries, calls, alarms, media notifications, and unreadable or overly long content remain unchanged.
- Repeated content is processed again. When a notification updates during evaluation, the latest version takes precedence.
- If the process stops after removal, locally saved pending records are delivered when the app resumes. Recovery opens Notiq history; the original notification’s PendingIntent cannot be durably restored.

## Device results

| Check | Result |
| --- | --- |
| Launch fixture from the home screen and tap delivery | PASS: three separate taps produced forwarded notifications in approximately 2.56, 0.58 and 0.73 seconds; Notiq banners were visible |
| Repeat the same delivery content and ID | PASS: no permanent deduplication; subsequent sends were forwarded |
| Tap delivery rapidly | PASS: latest content was forwarded and no source test notification remained; Android may coalesce rapid updates to the same ID |
| Tap promotion | PASS: `action_filtered / reason_filter`, without forwarding the ad |
| Service connection failure | PASS: FastJev’s ADB forwarding was temporarily pointed at an unused port; `action_forwarded / reason_connection` and a delivered notification were confirmed |
| Stop the process after removal | PASS: force-stopped Notiq after its removal log; reopening produced `action_forwarded / reason_recovered` and the notification reappeared |
| Main-screen status | PASS: “Filtered” and “Forwarded” appeared; “Kept” was no longer used to imply successful forwarding |
| Installation upgrade | PASS: existing database migrated to version 2; service configuration and app selection were preserved |
| Build | PASS: Debug and Release builds, 5 existing protocol tests, and Debug Lint passed |

After testing, official Jev and filtering mode were restored, and ADB port 8000 was restored to its original target. The phone ran the unified implementation.

## Boundaries

### Silence the source app, then let Notiq remind

The app-list bell shortcut was added on the same date. English and Chinese instructions explain how to keep notifications allowed while disabling message banners, sound and vibration, without disabling call alerts. The shortcut opens Android’s per-app notification settings. It neither changes settings automatically nor treats returning from Settings as proof of completion.

On the same vivo, the bell opened system settings for “Notiq 测试通知”. Notification permission stayed enabled; the system’s silent-notification switch was enabled and floating notifications were confirmed off. Tests used official Jev in filtering mode:

| Check | Result |
| --- | --- |
| Bell → instructions → open notification settings | PASS: opened the correct fixture settings; notification permission remained enabled |
| Promotions 801 and 803 | PASS: both records showed Filtered; sampled recordings showed no original banner and no corresponding Notiq delivery |
| Deliveries 802 and 804 | PASS: both records showed Forwarded; recordings showed Notiq banners with no observed original banners; corresponding Notiq notifications existed in the system list |
| Enable observe mode with silent settings, then send promotion 805 | PASS: the record showed “Observe · Would filter”; the original remained and no replacement was sent |

Local evidence is in the ignored directory: `artifacts/quiet-{ad-801,ad-803,delivery-802,delivery-804}.mp4` and corresponding `-frames.jpg` files. Shortcut and settings screenshots are `quiet-instructions.png` and `quiet-fixture-settings.png`. Each of the four recordings is 8 seconds long; contact sheets sample 2–4.5 seconds. This is not a general guarantee of zero visible frames. Sound and vibration were not physically verified. WeChat settings were not changed in that round, and real WeChat messages had not yet been tested; later results follow below.

Silencing is a persistent system setting. Observe mode, deselection, service disconnection or stopping Notiq does not automatically restore source-app alerts. Protected and unreadable notifications remain unchanged and are not forwarded. Restore source-app alerts through the same shortcut when stopping use of Notiq.

### Listener recovery after background cleanup

A later check found that the system terminated Notiq at 01:29:22 with `USER REQUESTED / FORCE STOP`, described as `single-cleaner`. Notification access remained granted, but the service was unbound. The fixture’s silent notification (importance=2) remained in the system list. This failure cannot be attributed to silent notifications being unreadable.

On Android 14 and later, returning to Notiq with permission already granted but no listener connection first requests removal of the stale binding, then requests rebinding. The disconnected UI shows “Disconnected · Notifications are not being processed” instead of “Filtering is on”. Android 10–13 retain the existing rebind request and were not tested on hardware in this round.

After the fix, force-stopping and reopening Notiq on the same phone restored the service without toggling notification access or granting it again through ADB. Silent delivery 901 produced the corresponding Notiq notification; silent promotion 902 was filtered. Processing logs and database status confirmed these results. WeChat notifications at 01:33:45 and 01:33:54 were subsequently processed, both recorded as `action_filtered / reason_filter`. Only source, timestamp and result were checked; message bodies were not exported. This proves listener recovery for WeChat, not correct classification or successful forwarding of keep-worthy WeChat messages. Diagnostic database copies were deleted.

This check covers new notifications after the app resumes, not notifications missed while stopped. It does not guarantee operation during a force-stop.

Fast removal does not mean Android never displayed a banner. In an earlier set of three immediate-removal tests on the same phone, one original banner still appeared even though the removal callback took only 8 ms. First banners, sound and vibration therefore cannot be guaranteed suppressed by removal alone.

The functional checks used synthetic notifications; they do not validate every WeChat or third-party message type. Quick replies, grouping, attachments and withdrawal state cannot be fully reproduced across apps. The phone was muted, so there was no acoustic verification. Long-running background operation, automatic recovery after reboot and other devices remain unverified systematically. Recovery cannot guarantee timely alerts while the process is stopped.

Screenshots and build logs are local files in the ignored `artifacts/` directory: `unified-delivery-2.png`, `unified-delivery-3.png`, `unified-burst.png`, and `unified-build.log`. Diagnostic database copies were cleaned up.
