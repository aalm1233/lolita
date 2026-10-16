package com.lolita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lolita.app.domain.model.PersonalModelDraftData
import java.util.UUID

@Entity(tableName = "personal_model_drafts")
data class PersonalModelDraft(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "data_json") val dataJson: String,
    @ColumnInfo(name = "source_image_paths", defaultValue = "'[]'") val sourceImagePaths: List<String>,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
) {
    fun data(): PersonalModelDraftData = PersonalModelDraftData.decode(dataJson)
    fun validate() {
        require(UUID.fromString(id).toString() == id) { "人物草稿标识无效" }
        require(data().photoRoles.size == sourceImagePaths.size && sourceImagePaths.all(String::isNotBlank)) { "人物草稿照片不完整" }
        require(sourceImagePaths.distinct().size == sourceImagePaths.size) { "人物草稿照片重复" }
    }
}
