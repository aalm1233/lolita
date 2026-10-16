package com.lolita.app.domain.model

import org.junit.Assert.*
import org.junit.Test

class ImageGenerationFailureTest {
    @Test fun unknownExceptionsCannotSmuggleSafeLookingCredentialTextIntoTasks() {
        for (raw in listOf("网络连接中断 Bearer sk-private", "生成等待超时 https://secret.invalid/key", "API Key 无效 account-secret")) {
            val message = imageTaskFailureMessage(IllegalStateException(raw), true)
            assertTrue(message.startsWith("生成未完成"))
            assertFalse(message.contains("sk-private")); assertFalse(message.contains("secret.invalid")); assertFalse(message.contains("account-secret"))
            assertTrue(message.contains("计费")); assertTrue(message.contains("不会自动重试"))
        }
    }
    @Test fun trustedFailuresKeepActionableReasonAndSentEvidenceWithoutExceptionCauses() {
        for (kind in ImageGenerationFailureKind.entries) {
            val failure = ImageGenerationFailure(kind)
            failure.initCause(IllegalStateException("Bearer private https://secret.invalid"))
            val message = imageTaskFailureMessage(failure, true)
            assertTrue(message.contains(kind.description)); assertTrue(message.contains("计费"))
            assertFalse(message.contains("secret.invalid")); assertFalse(message.contains("Bearer"))
        }
        val unsent = imageTaskFailureMessage(ImageGenerationFailure(ImageGenerationFailureKind.AUTH), false)
        assertTrue(unsent.contains("API Key")); assertFalse(unsent.contains("计费"))
    }
}
