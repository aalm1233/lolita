package com.lolita.app.domain.model

/** Trusted local categories only. Never construct a durable message from exception/server text. */
enum class ImageGenerationFailureKind(val description: String) {
    NETWORK("网络连接中断，请检查网络及后台耗电限制"),
    TIMEOUT("生成等待超时，请检查网络及后台耗电限制"),
    AUTH("API Key 无效或没有图像模型权限，请检查服务配置"),
    QUOTA("图像服务额度不足，请检查账户余额"),
    RATE_LIMIT("图像服务繁忙或额度受限，请检查账户用量"),
    SEEDREAM_LIMIT("火山方舟已达到用量上限，请检查安全体验模式或额度限制"),
    INVALID_IMAGE("图像服务未返回完整、有效的 PNG 图片"),
    MODEL("图像服务未完成请求，请检查模型权限、参考图片或服务状态")
}

class ImageGenerationFailure(val kind: ImageGenerationFailureKind) : IllegalStateException(kind.description)

fun imageTaskFailureMessage(failure: Exception, requestSent: Boolean): String {
    val reason = (failure as? ImageGenerationFailure)?.kind?.description
        ?: "生成未完成，请检查服饰、图片和服务配置"
    return reason + if (requestSent) "；已发送的请求可能仍在处理或计费，应用不会自动重试，请核对后再决定是否重新发起"
        else "；应用不会自动重试，请核对后重新发起"
}
