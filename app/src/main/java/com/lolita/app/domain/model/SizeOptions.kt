package com.lolita.app.domain.model

import java.util.Locale

object SizeOptions {
    val defaults = listOf("XS", "S", "M", "L", "XL", "XXL", "均码")

    fun merge(vararg groups: Iterable<String>): List<String> =
        (defaults + groups.flatMap { it }).map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.ROOT) }
}
