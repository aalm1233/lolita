package com.lolita.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lolita.app.data.local.converters.Converters
import com.lolita.app.data.local.dao.*
import com.lolita.app.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Coordinate::class,
        Item::class,
        Brand::class,
        Category::class,
        Price::class,
        Payment::class,
        OutfitLog::class,
        OutfitItemCrossRef::class
    ],
    version = 1,
    exportSchema = true
)
@androidx.room.TypeConverters(Converters::class)
abstract class LolitaDatabase : RoomDatabase() {
    abstract fun coordinateDao(): CoordinateDao
    abstract fun itemDao(): ItemDao
    abstract fun brandDao(): BrandDao
    abstract fun categoryDao(): CategoryDao
    abstract fun priceDao(): PriceDao
    abstract fun paymentDao(): PaymentDao
    abstract fun outfitLogDao(): OutfitLogDao

    companion object {
        @Volatile
        private var INSTANCE: LolitaDatabase? = null

        fun getDatabase(context: Context): LolitaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LolitaDatabase::class.java,
                    "lolita_database"
                )
                    .addCallback(DatabaseCallback())
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Pre-populated data will be inserted via Repository on first launch
            }
        }
    }
}
