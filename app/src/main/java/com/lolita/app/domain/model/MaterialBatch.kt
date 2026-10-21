package com.lolita.app.domain.model

import java.util.UUID

data class MaterialBatchIdentity(val id: String, val name: String) {
    init { require(UUID.fromString(id).toString() == id && name.isNotBlank() && name.length <= 80) }
    fun taskId(operationId: String, itemId: Long): String {
        require(UUID.fromString(operationId).toString() == operationId && itemId > 0)
        return UUID.nameUUIDFromBytes("$id:$operationId:$itemId".toByteArray(Charsets.UTF_8)).toString()
    }
    companion object { const val MAX_ITEMS = 30 }
}

data class BatchMaterialInput(val itemId: Long, val input: PreparedMaterialInput, val originalPath: String,
    val expectedMaterialId: String?, val instructions: String, val position: Int)
data class BatchLayerInput(val taskId: String, val input: PreparedMaterialInput)
data class BatchMaterialConfirmation(val taskId: String, val imagePath: String, val expectedMaterialId: String?)
