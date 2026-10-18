package com.lolita.app.data.file

import org.junit.Assert.*
import org.junit.Test

class WardrobeImportCandidatesTest {
    @Test fun explicitNewWearablesRemainSeparateCandidates() {
        assertTrue(TaobaoProductParser.explicitCategoryCandidates("全头假发 长卷", emptyMap()).contains("全头假发"))
        assertTrue(TaobaoProductParser.explicitCategoryCandidates("刘海片", emptyMap()).contains("刘海片"))
        assertFalse(TaobaoProductParser.explicitCategoryCandidates("刘海片", emptyMap()).contains("全头假发"))
        assertTrue(TaobaoProductParser.explicitCategoryCandidates("过膝袜", emptyMap()).contains("过膝袜"))
        assertTrue(TaobaoProductParser.explicitCategoryCandidates("袜套", emptyMap()).contains("袜套"))
        assertTrue(TaobaoProductParser.explicitCategoryCandidates("玛丽珍鞋", emptyMap()).contains("玛丽珍鞋"))
        assertTrue(TaobaoProductParser.explicitCategoryCandidates("裙撑", emptyMap()).contains("裙撑"))
    }
    @Test fun plainColorOrSizeNeverCreatesGarmentCandidates() {
        assertTrue(TaobaoProductParser.explicitCategoryCandidates("黑色 EU 37", emptyMap()).isEmpty())
        assertTrue(TaobaoProductParser.explicitCategoryCandidates("奶茶色 60 cm", emptyMap()).isEmpty())
    }
}
