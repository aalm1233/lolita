package com.lolita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lolita.app.domain.model.*
import java.util.UUID

@Entity(tableName = "recognition_tasks", indices = [Index("draft_id"), Index("target_item_id")])
data class RecognitionTask(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "draft_id") val draftId: String,
    @ColumnInfo(name = "target_item_id") val targetItemId: Long?,
    @ColumnInfo(name = "request_json") val requestJson: String,
    @ColumnInfo(name = "region_image_paths", defaultValue = "'[]'") val regionImagePaths: List<String>,
    val status: RecognitionStatus = RecognitionStatus.PREPARED,
    @ColumnInfo(name = "request_sent", defaultValue = "0") val requestSent: Boolean = false,
    @ColumnInfo(name = "result_json") val resultJson: String? = null,
    @ColumnInfo(name = "review_json") val reviewJson: String? = null,
    val error: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
    @ColumnInfo(name = "actual_model", defaultValue = "NULL") val actualModel: String? = null
) {
    fun request() = RecognitionRequest.decode(requestJson)
    fun result() = resultJson?.let { RecognitionResult.decode(it, request().regions.map { it.id }.toSet()) }
    fun validate() {
        require(actualModel == null || actualModel.matches(Regex("[A-Za-z0-9._:/-]{1,160}")))
        require(UUID.fromString(id).toString() == id)
        val request = request()
        require(request.draftId == draftId && request.targetItemId == targetItemId)
        require(regionImagePaths.none(String::isBlank))
        if (status != RecognitionStatus.DISCARDED) require(regionImagePaths.size == request.regions.size)
        val result = result()
        reviewJson?.let {
            val review=RecognitionReview.decode(it, result?.candidates.orEmpty().map { candidate -> candidate.id }.toSet())
            require(review.appliedValues.keys.all { field -> result?.candidates.orEmpty().any { candidate -> candidate.field==field } })
        }
        require(status !in setOf(RecognitionStatus.AWAITING_REVIEW, RecognitionStatus.APPLIED) || result != null)
    }
}
