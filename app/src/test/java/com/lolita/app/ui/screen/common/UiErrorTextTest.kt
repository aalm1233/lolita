package com.lolita.app.ui.screen.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.FileNotFoundException

class UiErrorTextTest {
    @Test fun actionableValidationIsKeptVerbatim() {
        assertEquals("请先解除小物关联", uiErrorText(IllegalStateException("请先解除小物关联")))
        assertEquals("余额未知，请先核对付款记录", uiErrorText(IllegalArgumentException("余额未知，请先核对付款记录")))
    }
    @Test fun fileAndPermissionFailuresGiveRecoveryActionWithoutPrivatePaths() {
        assertEquals("所需文件暂不可用，请重新选择文件后重试", uiErrorText(FileNotFoundException("/data/user/0/private.png")))
        assertEquals("无法访问所需资料，请检查授权后重试", uiErrorText(SecurityException("private URI")))
    }
    @Test fun providerResponsesPathsAndExceptionChainsNeverReachUserText() {
        for (message in listOf("失败 /data/user/0/a.png", "失败 https://provider.invalid?key=secret",
            "请求失败 Authorization: secret", "解析失败 {\"error\":\"secret\"}", "失败 SQLiteException: detail",
            "SQLITE_CONSTRAINT_FOREIGNKEY", "错误\nat org.example.Private.run()", "服务拒绝 sk-secret")) {
            assertEquals("导入未完成，请重试", uiErrorText(IllegalStateException(message), "导入未完成，请重试"))
        }
    }
}
