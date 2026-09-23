package dev.notiq.data

import dev.notiq.R

/** Stable status keys; legacy labels remain readable across language changes. */
object RecordText {
    fun resource(value: String): Int? = when (value) {
        "action_pending", "正在判断" -> R.string.action_pending
        "action_kept", "已保留" -> R.string.action_kept
        "action_observed", "观察 · 建议过滤" -> R.string.action_observed
        "action_removal", "已请求移除" -> R.string.action_removal
        "reason_unreadable", "通知内容不可读取" -> R.string.reason_unreadable
        "reason_long", "内容过长，未发送判断" -> R.string.reason_long
        "reason_protected", "持续通知、分组摘要、通话、闹钟与媒体通知受保护" -> R.string.reason_protected
        "reason_busy", "当前请求繁忙，保留通知" -> R.string.reason_busy
        "reason_uncertain", "模型无法确定" -> R.string.reason_uncertain
        "reason_threshold", "未达到过滤阈值" -> R.string.reason_threshold
        "reason_keep", "模型建议保留" -> R.string.reason_keep
        "reason_observe", "观察模式未移除通知" -> R.string.reason_observe
        "reason_changed", "通知或设置已变化，旧判断不再执行" -> R.string.reason_changed
        "reason_filter", "判断满足过滤规则与当前阈值" -> R.string.reason_filter
        "reason_gone", "通知已更新或消失" -> R.string.reason_gone
        "reason_timeout", "服务超时，保留通知" -> R.string.reason_timeout
        "reason_connection", "服务连接失败，保留通知" -> R.string.reason_connection
        "reason_invalid", "服务配置或返回结果异常，保留通知" -> R.string.reason_invalid
        "reason_interrupted", "处理已中断，保留通知" -> R.string.reason_interrupted
        "action_forwarded" -> R.string.action_forwarded
        "action_filtered" -> R.string.action_filtered
        "action_relay_pending" -> R.string.action_relay_pending
        "reason_relay_permission" -> R.string.reason_relay_permission
        "reason_not_removed" -> R.string.reason_not_removed
        "reason_superseded" -> R.string.reason_superseded
        "reason_recovered" -> R.string.reason_recovered
        else -> null
    }
}
