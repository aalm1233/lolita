package com.lolita.app.domain.usecase

object ReleaseVersion {
    fun compare(remote: String, local: String): Int {
        val remoteParts = parts(remote)
        val localParts = parts(local)
        return remoteParts.zip(localParts).firstOrNull { (a, b) -> a != b }
            ?.let { (a, b) -> a.compareTo(b) } ?: 0
    }

    private fun parts(version: String): List<Int> {
        require(Regex("v?[0-9]+\\.[0-9]+\\.[0-9]+").matches(version)) {
            "无法识别版本号，请前往发布页查看"
        }
        return version.removePrefix("v").split('.').map {
            it.toIntOrNull() ?: throw IllegalArgumentException("无法识别版本号，请前往发布页查看")
        }
    }
}
