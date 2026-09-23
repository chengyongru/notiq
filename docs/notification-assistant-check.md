# Notification assistant availability

**English** · [简体中文](notification-assistant-check.zh-CN.md)

Checked on 2026-09-24 with a vivo V2454A running Android 16 (API 36).

A build replacing the existing listener with `NotificationAssistantService` compiled and installed. Package Manager resolved the service with `BIND_NOTIFICATION_ASSISTANT_SERVICE`. Both full and abbreviated component names were tried with `cmd notification allow_assistant`, including an explicit user ID of 0.

The grant command returned without an error, but `get_approved_assistant` and `dumpsys notification` continued to show only `android.ext.services/android.ext.services.notification.Assistant`. Notiq was not bound as an assistant. No pre-post adjustment or banner suppression was verified.

The device declares `REQUEST_NOTIFICATION_ASSISTANT_SERVICE` as `signature|privileged|role`. Android 16 checks this permission before granting assistant access. The corresponding notification intelligence role is system-only. An ordinary application install plus `allow_assistant` is therefore insufficient on this device.

The installed application and source were restored to the existing listener implementation. The failed assistant implementation is not retained as a second runtime path. No hidden API enforcement, role qualification, source-app notification settings, or system package privileges were changed.

References:

- [Android 16 notification manager: assistant access and required permission](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/notification/NotificationManagerService.java)
- [System-only notification intelligence role](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/android16-qpr2-release/PermissionController/res/xml/roles.xml)
