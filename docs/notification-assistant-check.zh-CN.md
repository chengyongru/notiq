# 通知助理可用性验证

[English](notification-assistant-check.md) · **简体中文**

验证日期：2026-09-24。设备：vivo V2454A，Android 16（API 36）。

将现有监听服务替换为 `NotificationAssistantService` 的版本成功编译并安装。Package Manager 能识别声明了 `BIND_NOTIFICATION_ASSISTANT_SERVICE` 的服务。通过 `cmd notification allow_assistant` 尝试了完整和缩写组件名，也明确指定了用户 ID 0。

授权命令没有报错，但 `get_approved_assistant` 与 `dumpsys notification` 仍只显示 `android.ext.services/android.ext.services.notification.Assistant`。Notiq 没有被绑定为通知助理，未能验证发布前调整或横幅抑制。

设备将 `REQUEST_NOTIFICATION_ASSISTANT_SERVICE` 声明为 `signature|privileged|role`。Android 16 在授予通知助理访问权限前检查此权限，对应的通知智能角色仅供系统应用使用。因此，在这台设备上，普通安装应用并执行 `allow_assistant` 不足以启用该服务。

手机上的应用和源代码均已恢复为现有通知监听实现，没有保留第二套通知助理运行流程。未修改隐藏 API 限制、角色资格、原应用通知设置或系统应用权限。

参考：

- [Android 16 通知管理器：通知助理授权及所需权限](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/notification/NotificationManagerService.java)
- [仅供系统应用使用的通知智能角色](https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/android16-qpr2-release/PermissionController/res/xml/roles.xml)
