package com.lolita.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lolita.app.domain.model.ImageGenerationProvider
import com.lolita.app.domain.model.PersonalModelRequest

/** Confirmed reference image; historical snapshots deliberately do not reference it by foreign key. */
@Entity(tableName = "personal_models")
data class PersonalModel(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "request_json") val requestJson: String,
    @ColumnInfo(name = "source_image_paths", defaultValue = "'[]'") val sourceImagePaths: List<String>,
    @ColumnInfo(name = "image_url") val imageUrl: String,
    val provider: ImageGenerationProvider,
    val model: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt
) {
    fun request(): PersonalModelRequest = PersonalModelRequest.decode(requestJson)
    fun protectedImagePaths(): Set<String> = (sourceImagePaths + imageUrl).filter(String::isNotBlank).toSet()
}
