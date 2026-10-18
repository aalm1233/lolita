package com.lolita.app.data.local.converters

import androidx.room.TypeConverter
import com.lolita.app.data.local.entity.ImageSourceType
import com.lolita.app.domain.model.*

/** Unknown semantic/task enums fail closed instead of inventing a different paid operation. */
class WardrobeConverters {
    @TypeConverter fun semantic(value: String): CategorySemantic = CategorySemantic.valueOf(value)
    @TypeConverter fun semantic(value: CategorySemantic): String = value.name
    @TypeConverter fun composition(value: String): ItemComposition = ItemComposition.valueOf(value)
    @TypeConverter fun composition(value: ItemComposition): String = value.name
    @TypeConverter fun wearRole(value: String?): TryOnRole? = value?.let(TryOnRole::valueOf)
    @TypeConverter fun wearRole(value: TryOnRole?): String? = value?.name
    @TypeConverter fun recognitionStatus(value: String): RecognitionStatus = RecognitionStatus.valueOf(value)
    @TypeConverter fun recognitionStatus(value: RecognitionStatus): String = value.name
    @TypeConverter fun recognitionField(value: String): RecognitionField = RecognitionField.valueOf(value)
    @TypeConverter fun recognitionField(value: RecognitionField): String = value.name
    @TypeConverter fun imageSource(value: String): ImageSourceType = ImageSourceType.valueOf(value)
    @TypeConverter fun imageSource(value: ImageSourceType): String = value.name
}
