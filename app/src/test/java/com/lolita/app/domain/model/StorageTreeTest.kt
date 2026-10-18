package com.lolita.app.domain.model

import com.lolita.app.data.local.entity.Location
import com.lolita.app.data.local.entity.StorageNode
import org.junit.Assert.*
import org.junit.Test

class StorageTreeTest {
    private val rooms = listOf(Location(id = 1, name = "玄关"), Location(id = 2, name = "卧室"))
    private fun node(id: Long, name: String, parent: Long? = null, room: Long = 1) =
        StorageNode(id = id, locationId = room, parentNodeId = parent, name = name)

    @Test fun rejectsDuplicateRootAndChildNamesAfterTrimmingAndIgnoringCase() {
        assertThrows(IllegalArgumentException::class.java) {
            StorageTree.validate(listOf(node(1, " Shoe Rack "), node(2, "shoe rack")), setOf(1L, 2L))
        }
        assertThrows(IllegalArgumentException::class.java) {
            StorageTree.validate(listOf(node(1, "鞋柜"), node(2, "第二层", 1), node(3, " 第二层 ", 1)), setOf(1L))
        }
        StorageTree.validate(listOf(node(1, "鞋柜"), node(2, "鞋柜", room = 2)), setOf(1L, 2L))
    }

    @Test fun rejectsMissingParentCrossRoomParentAndInconsistentScope() {
        listOf(
            listOf(node(1, "第二层", 99)),
            listOf(node(1, "鞋柜"), node(2, "第二层", 1, room = 2)),
            listOf(node(1, "鞋柜").copy(siblingScopeId = 99))
        ).forEach { nodes ->
            assertThrows(IllegalArgumentException::class.java) { StorageTree.validate(nodes, setOf(1L, 2L)) }
        }
    }

    @Test fun rejectsCyclesAndAThirdStorageLevel() {
        listOf(
            listOf(node(1, "鞋柜", 2), node(2, "第二层", 1)),
            listOf(node(1, "鞋柜"), node(2, "第二层", 1), node(3, "第四级", 2)),
            listOf(node(1, "自己", 1))
        ).forEach { nodes ->
            assertThrows(IllegalArgumentException::class.java) { StorageTree.validate(nodes, setOf(1L)) }
        }
    }

    @Test fun derivesPathsFromCurrentNamesAndIncludesOnlyTheSelectedSubtree() {
        val nodes = listOf(node(1, "鞋柜"), node(2, "第二层", 1), node(3, "另一个柜子"))
        assertEquals("玄关 → 鞋柜 → 第二层", StorageTree.pathFor(1, 2, rooms, nodes))
        assertEquals("玄关 → 常穿鞋柜 → 第二层", StorageTree.pathFor(1, 2, rooms, nodes.map {
            if (it.id == 1L) it.copy(name = "常穿鞋柜") else it
        }))
        assertEquals(setOf(1L, 2L), StorageTree.descendantIds(1, nodes))
        assertEquals("玄关", StorageTree.pathFor(1, null, rooms, nodes))
        assertEquals("未设置位置", StorageTree.pathFor(null, null, rooms, nodes))
    }
}
