package com.lolita.app.ui.screen.common

import java.io.FileNotFoundException

/** Keep actionable domain validation; never present paths, provider responses or exception chains. */
fun uiErrorText(failure: Throwable, fallback: String = "操作未完成，请重试"): String {
    if (failure is FileNotFoundException) return "所需文件暂不可用，请重新选择文件后重试"
    if (failure is SecurityException) return "无法访问所需资料，请检查授权后重试"
    val message = failure.message?.trim().orEmpty()
    val internalDetails = Regex(
        "https?://|[/\\\\](?:data|storage|sdcard|Users|private|tmp)[/\\\\]|[A-Za-z]:\\\\|" +
            "api[_-]?key|authorization|bearer|exception|sqlite|(?:select|insert|update|delete)\\s|" +
            "at\\s+[a-z]+\\.|\\{[\\s\\S]*[\"']|sk-[A-Za-z0-9]",
        RegexOption.IGNORE_CASE
    )
    return message.takeIf {
        it.length in 1..240 && it.any { char -> char in '\u3400'..'\u9fff' } &&
            !internalDetails.containsMatchIn(it) && !it.contains('\n') && !it.contains('\r')
    } ?: fallback
}
