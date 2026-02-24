package com.lolita.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lolita.app.data.local.converters.Converters
import com.lolita.app.data.local.dao.*
import com.lolita.app.data.local.entity.*

@Database(
    entities = [
        Coordinate::class,
        Item::class,
        Brand::class,
        Category::class,
        Price::class,
        Payment::class,
        OutfitLog::class,
        OutfitItemCrossRef::class,
        Style::class,
        Season::class,
        Location::class,
        Source::class
    ],
    version = 15,
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
    abstract fun styleDao(): StyleDao
    abstract fun seasonDao(): SeasonDao
    abstract fun locationDao(): LocationDao
    abstract fun sourceDao(): SourceDao

    companion object {
        @Volatile
        private var INSTANCE: LolitaDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN color TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE items ADD COLUMN season TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE items ADD COLUMN style TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE prices ADD COLUMN purchase_date INTEGER DEFAULT NULL")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create styles table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS styles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        is_preset INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_styles_name ON styles (name)")

                // Create seasons table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS seasons (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        is_preset INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_seasons_name ON seasons (name)")

                // Add size and sizeChartImageUrl to items
                db.execSQL("ALTER TABLE items ADD COLUMN size TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE items ADD COLUMN size_chart_image_url TEXT DEFAULT NULL")

                // Insert preset styles
                val now = System.currentTimeMillis()
                listOf("甜系", "古典", "哥特", "田园", "中华", "其他").forEach { name ->
                    db.execSQL("INSERT OR IGNORE INTO styles (name, is_preset, created_at) VALUES ('$name', 1, $now)")
                }
                // Insert preset seasons
                listOf("春", "夏", "秋", "冬", "四季").forEach { name ->
                    db.execSQL("INSERT OR IGNORE INTO seasons (name, is_preset, created_at) VALUES ('$name', 1, $now)")
                }
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add category_group column to categories
                db.execSQL("ALTER TABLE categories ADD COLUMN category_group TEXT NOT NULL DEFAULT 'CLOTHING'")

                // Update accessory categories
                val accessories = listOf("KC", "斗篷", "披肩", "发带", "Bonnet", "其他头饰", "袜子", "手套", "其他配饰")
                accessories.forEach { name ->
                    db.execSQL("UPDATE categories SET category_group = 'ACCESSORY' WHERE name = '$name'")
                }
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE payments ADD COLUMN calendar_event_id INTEGER DEFAULT NULL")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE coordinates ADD COLUMN image_url TEXT DEFAULT NULL")
            }
        }
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create locations table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `locations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL DEFAULT '',
                        `image_url` TEXT DEFAULT NULL,
                        `sort_order` INTEGER NOT NULL DEFAULT 0,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_locations_name` ON `locations` (`name`)")

                // 2. Recreate items table with location_id FK
                //    (ALTER TABLE ADD COLUMN cannot add foreign keys in SQLite)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `items_new` (
                        `brand_id` INTEGER NOT NULL,
                        `category_id` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `description` TEXT NOT NULL,
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `image_url` TEXT DEFAULT NULL,
                        `name` TEXT NOT NULL,
                        `priority` TEXT NOT NULL DEFAULT 'MEDIUM',
                        `status` TEXT NOT NULL,
                        `coordinate_id` INTEGER DEFAULT NULL,
                        `color` TEXT DEFAULT NULL,
                        `season` TEXT DEFAULT NULL,
                        `style` TEXT DEFAULT NULL,
                        `size` TEXT DEFAULT NULL,
                        `size_chart_image_url` TEXT DEFAULT NULL,
                        `location_id` INTEGER DEFAULT NULL,
                        `updated_at` INTEGER NOT NULL,
                        FOREIGN KEY(`coordinate_id`) REFERENCES `coordinates`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT,
                        FOREIGN KEY(`brand_id`) REFERENCES `brands`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`location_id`) REFERENCES `locations`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `items_new` (`brand_id`,`category_id`,`created_at`,`description`,`id`,`image_url`,`name`,`priority`,`status`,`coordinate_id`,`color`,`season`,`style`,`size`,`size_chart_image_url`,`location_id`,`updated_at`)
                    SELECT `brand_id`,`category_id`,`created_at`,`description`,`id`,`image_url`,`name`,`priority`,`status`,`coordinate_id`,`color`,`season`,`style`,`size`,`size_chart_image_url`,NULL,`updated_at` FROM `items`
                """.trimIndent())
                db.execSQL("DROP TABLE `items`")
                db.execSQL("ALTER TABLE `items_new` RENAME TO `items`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_name` ON `items` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_coordinate_id` ON `items` (`coordinate_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_brand_id` ON `items` (`brand_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_category_id` ON `items` (`category_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_status` ON `items` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_priority` ON `items` (`priority`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_location_id` ON `items` (`location_id`)")
            }
        }

        // Fix for users who already ran the broken v6→v7 migration (ALTER TABLE without FK)
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Recreate items table to ensure location_id FK exists
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `items_new` (
                        `brand_id` INTEGER NOT NULL,
                        `category_id` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `description` TEXT NOT NULL,
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `image_url` TEXT DEFAULT NULL,
                        `name` TEXT NOT NULL,
                        `priority` TEXT NOT NULL DEFAULT 'MEDIUM',
                        `status` TEXT NOT NULL,
                        `coordinate_id` INTEGER DEFAULT NULL,
                        `color` TEXT DEFAULT NULL,
                        `season` TEXT DEFAULT NULL,
                        `style` TEXT DEFAULT NULL,
                        `size` TEXT DEFAULT NULL,
                        `size_chart_image_url` TEXT DEFAULT NULL,
                        `location_id` INTEGER DEFAULT NULL,
                        `updated_at` INTEGER NOT NULL,
                        FOREIGN KEY(`coordinate_id`) REFERENCES `coordinates`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT,
                        FOREIGN KEY(`brand_id`) REFERENCES `brands`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`location_id`) REFERENCES `locations`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `items_new` (`brand_id`,`category_id`,`created_at`,`description`,`id`,`image_url`,`name`,`priority`,`status`,`coordinate_id`,`color`,`season`,`style`,`size`,`size_chart_image_url`,`location_id`,`updated_at`)
                    SELECT `brand_id`,`category_id`,`created_at`,`description`,`id`,`image_url`,`name`,`priority`,`status`,`coordinate_id`,`color`,`season`,`style`,`size`,`size_chart_image_url`,`location_id`,`updated_at` FROM `items`
                """.trimIndent())
                db.execSQL("DROP TABLE `items`")
                db.execSQL("ALTER TABLE `items_new` RENAME TO `items`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_name` ON `items` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_coordinate_id` ON `items` (`coordinate_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_brand_id` ON `items` (`brand_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_category_id` ON `items` (`category_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_status` ON `items` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_priority` ON `items` (`priority`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_location_id` ON `items` (`location_id`)")
                // Ensure locations table exists (in case v6→v7 partially failed)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `locations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL DEFAULT '',
                        `image_url` TEXT DEFAULT NULL,
                        `sort_order` INTEGER NOT NULL DEFAULT 0,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_locations_name` ON `locations` (`name`)")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create sources table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sources (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        is_preset INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sources_name ON sources (name)")

                // Add source column to items
                db.execSQL("ALTER TABLE items ADD COLUMN source TEXT DEFAULT NULL")

                // Insert preset sources
                val now = System.currentTimeMillis()
                listOf("淘宝", "咸鱼", "线下").forEach { name ->
                    db.execSQL("INSERT OR IGNORE INTO sources (name, is_preset, created_at) VALUES ('$name', 1, $now)")
                }
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE brands ADD COLUMN logo_url TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new colors column
                db.execSQL("ALTER TABLE items ADD COLUMN colors TEXT DEFAULT NULL")
                // Migrate existing color data: single value -> JSON array
                // e.g. "粉色" -> ["粉色"]
                db.execSQL("""UPDATE items SET colors = '["' || color || '"]' WHERE color IS NOT NULL AND color != ''""")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Fix corrupted JSON from migration 10->11 that had literal backslashes
                db.execSQL("""UPDATE items SET colors = REPLACE(colors, '\"', '"') WHERE colors IS NOT NULL AND colors LIKE '%\%'""")

                // Rebuild items table to remove the old 'color' column
                // Room schema validation requires exact column match — extra columns cause crash
                db.execSQL("""CREATE TABLE IF NOT EXISTS items_new (
                    brand_id INTEGER NOT NULL,
                    category_id INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    description TEXT NOT NULL,
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    image_url TEXT,
                    name TEXT NOT NULL,
                    priority TEXT NOT NULL DEFAULT 'MEDIUM',
                    status TEXT NOT NULL,
                    coordinate_id INTEGER,
                    colors TEXT,
                    season TEXT,
                    style TEXT,
                    size TEXT,
                    size_chart_image_url TEXT,
                    location_id INTEGER,
                    source TEXT,
                    updated_at INTEGER NOT NULL,
                    FOREIGN KEY(coordinate_id) REFERENCES coordinates(id) ON UPDATE CASCADE ON DELETE RESTRICT,
                    FOREIGN KEY(brand_id) REFERENCES brands(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(category_id) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(location_id) REFERENCES locations(id) ON UPDATE NO ACTION ON DELETE SET NULL
                )""")
                db.execSQL("""INSERT INTO items_new (brand_id, category_id, created_at, description, id, image_url, name, priority, status, coordinate_id, colors, season, style, size, size_chart_image_url, location_id, source, updated_at)
                    SELECT brand_id, category_id, created_at, description, id, image_url, name, priority, status, coordinate_id, colors, season, style, size, size_chart_image_url, location_id, source, updated_at FROM items""")
                db.execSQL("DROP TABLE items")
                db.execSQL("ALTER TABLE items_new RENAME TO items")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_name ON items (name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_coordinate_id ON items (coordinate_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_brand_id ON items (brand_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_category_id ON items (category_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_status ON items (status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_priority ON items (priority)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_location_id ON items (location_id)")
            }
        }

        // For users who already ran the old MIGRATION_11_12 (data-only fix),
        // their DB is at version 12 but still has the extra 'color' column.
        // This migration rebuilds the table to remove it.
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Check if old 'color' column still exists
                val cursor = db.query("PRAGMA table_info(items)")
                var hasOldColorColumn = false
                while (cursor.moveToNext()) {
                    val colName = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    if (colName == "color") { hasOldColorColumn = true; break }
                }
                cursor.close()
                if (!hasOldColorColumn) return

                // Rebuild items table to remove the old 'color' column
                db.execSQL("""CREATE TABLE IF NOT EXISTS items_new (
                    brand_id INTEGER NOT NULL,
                    category_id INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    description TEXT NOT NULL,
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    image_url TEXT,
                    name TEXT NOT NULL,
                    priority TEXT NOT NULL DEFAULT 'MEDIUM',
                    status TEXT NOT NULL,
                    coordinate_id INTEGER,
                    colors TEXT,
                    season TEXT,
                    style TEXT,
                    size TEXT,
                    size_chart_image_url TEXT,
                    location_id INTEGER,
                    source TEXT,
                    updated_at INTEGER NOT NULL,
                    FOREIGN KEY(coordinate_id) REFERENCES coordinates(id) ON UPDATE CASCADE ON DELETE RESTRICT,
                    FOREIGN KEY(brand_id) REFERENCES brands(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(category_id) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(location_id) REFERENCES locations(id) ON UPDATE NO ACTION ON DELETE SET NULL
                )""")
                db.execSQL("""INSERT INTO items_new (brand_id, category_id, created_at, description, id, image_url, name, priority, status, coordinate_id, colors, season, style, size, size_chart_image_url, location_id, source, updated_at)
                    SELECT brand_id, category_id, created_at, description, id, image_url, name, priority, status, coordinate_id, colors, season, style, size, size_chart_image_url, location_id, source, updated_at FROM items""")
                db.execSQL("DROP TABLE items")
                db.execSQL("ALTER TABLE items_new RENAME TO items")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_name ON items (name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_coordinate_id ON items (coordinate_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_brand_id ON items (brand_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_category_id ON items (category_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_status ON items (status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_priority ON items (priority)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_location_id ON items (location_id)")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Case A: purchaseDate is not null — use it as paidDate
                db.execSQL("""
                    INSERT INTO payments (price_id, amount, due_date, is_paid, paid_date, reminder_set, created_at)
                    SELECT p.id, p.total_price, p.purchase_date, 1, p.purchase_date, 0, p.created_at
                    FROM prices p
                    WHERE p.id NOT IN (SELECT price_id FROM payments)
                      AND p.purchase_date IS NOT NULL
                """.trimIndent())

                // Case B: purchaseDate is null — use createdAt as fallback
                db.execSQL("""
                    INSERT INTO payments (price_id, amount, due_date, is_paid, paid_date, reminder_set, created_at)
                    SELECT p.id, p.total_price, p.created_at, 1, p.created_at, 0, p.created_at
                    FROM prices p
                    WHERE p.id NOT IN (SELECT price_id FROM payments)
                      AND p.purchase_date IS NULL
                """.trimIndent())

                // Rebuild Price table without purchaseDate column
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS prices_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        item_id INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        total_price REAL NOT NULL,
                        deposit REAL,
                        balance REAL,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY(item_id) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO prices_new (id, item_id, type, total_price, deposit, balance, created_at)
                    SELECT id, item_id, type, total_price, deposit, balance, created_at FROM prices
                """.trimIndent())
                db.execSQL("DROP TABLE prices")
                db.execSQL("ALTER TABLE prices_new RENAME TO prices")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_prices_item_id ON prices(item_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_prices_type ON prices(type)")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Rebuild items table: image_url -> image_urls, add coordinate_order
                db.execSQL("""CREATE TABLE IF NOT EXISTS items_new (
                    brand_id INTEGER NOT NULL,
                    category_id INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    description TEXT NOT NULL,
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    image_urls TEXT NOT NULL DEFAULT '[]',
                    coordinate_order INTEGER NOT NULL DEFAULT 0,
                    name TEXT NOT NULL,
                    priority TEXT NOT NULL DEFAULT 'MEDIUM',
                    status TEXT NOT NULL,
                    coordinate_id INTEGER,
                    colors TEXT,
                    season TEXT,
                    style TEXT,
                    size TEXT,
                    size_chart_image_url TEXT,
                    location_id INTEGER,
                    source TEXT,
                    updated_at INTEGER NOT NULL,
                    FOREIGN KEY(coordinate_id) REFERENCES coordinates(id) ON UPDATE CASCADE ON DELETE RESTRICT,
                    FOREIGN KEY(brand_id) REFERENCES brands(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(category_id) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(location_id) REFERENCES locations(id) ON UPDATE NO ACTION ON DELETE SET NULL
                )""")
                db.execSQL("""INSERT INTO items_new (brand_id, category_id, created_at, description, id, image_urls, coordinate_order, name, priority, status, coordinate_id, colors, season, style, size, size_chart_image_url, location_id, source, updated_at)
                    SELECT brand_id, category_id, created_at, description, id,
                    CASE WHEN image_url IS NOT NULL AND image_url != '' THEN '["' || REPLACE(REPLACE(image_url, '\', '\\'), '"', '\"') || '"]' ELSE '[]' END,
                    0, name, priority, status, coordinate_id, colors, season, style, size, size_chart_image_url, location_id, source, updated_at FROM items""")
                db.execSQL("DROP TABLE items")
                db.execSQL("ALTER TABLE items_new RENAME TO items")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_name ON items (name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_coordinate_id ON items (coordinate_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_brand_id ON items (brand_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_category_id ON items (category_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_status ON items (status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_priority ON items (priority)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_location_id ON items (location_id)")

                // Rebuild coordinates table: image_url -> image_urls
                db.execSQL("""CREATE TABLE IF NOT EXISTS coordinates_new (
                    created_at INTEGER NOT NULL,
                    description TEXT NOT NULL,
                    image_urls TEXT NOT NULL DEFAULT '[]',
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    updated_at INTEGER NOT NULL
                )""")
                db.execSQL("""INSERT INTO coordinates_new (created_at, description, image_urls, id, name, updated_at)
                    SELECT created_at, description,
                    CASE WHEN image_url IS NOT NULL AND image_url != '' THEN '["' || REPLACE(REPLACE(image_url, '\', '\\'), '"', '\"') || '"]' ELSE '[]' END,
                    id, name, updated_at FROM coordinates""")
                db.execSQL("DROP TABLE coordinates")
                db.execSQL("ALTER TABLE coordinates_new RENAME TO coordinates")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_coordinates_name ON coordinates (name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_coordinates_created_at ON coordinates (created_at)")
            }
        }

        fun getDatabase(context: Context): LolitaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LolitaDatabase::class.java,
                    "lolita_database"
                )
                    .addCallback(DatabaseCallback())
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                val now = System.currentTimeMillis()

                // Insert preset brands synchronously via raw SQL
                listOf(
                            "古典玩偶（Classical Puppets）",
                            "仲夏物语（Elpress L）",
                            "玻璃纸之夜（NDC）",
                            "摇篮曲（Lullaby）",
                            "圆点点小姐 Lolita 洋服社",
                            "小宝石洋服（Petit Bijou）",
                            "表面咒语（Surface Spell）",
                            "酒心可可（Chocolate Lovers）",
                            "花与珍珠匣",
                            "莉莉子 Lolita",
                            "新路德维希（Neo Ludwig）",
                            "CuteQ",
                            "纪念岛",
                            "Nololita",
                            "平成魔女",
                            "七愚",
                            "织锦园",
                            "无名诗（东之森）",
                            "白雪姬 Lolita",
                            "樱桃爆弹",
                            "告白气球家",
                            "绵羊泡芙（Sheep Puff）",
                            "Honey Honey Lolita",
                            "沉默编织",
                            "悠尼蒂鹿（Unideer）",
                            "JS 洋装贩售",
                            "暗星之岛",
                            "晴木",
                            "银河有迹可循",
                            "玻璃花信",
                            "十二月暖暖",
                            "九歌 Lolita",
                            "ALICE GIRL 原创工作室",
                            "小熊星座洋服",
                            "果鹿 Lolita",
                            "四叶工房",
                            "Precious Clove",
                            "IchigoMikou",
                            "Pink up Lolita",
                            "Morning Glory 原创设计",
                            "鹿隐洋装",
                            "樱洛芙",
                            "Choker Rabbit（CR）",
                            "星芙颂",
                            "浮世镜",
                            "月亮浮士德",
                            "朝鹿",
                            "榛果可可花束",
                            "星夜彩虹",
                            "梦境人偶",
                            "奶油橘喵酱",
                            "星辰猫 Lolita",
                            "甜心贩售机",
                            "猫咪杀手工作室",
                            "魔法裙子猫",
                            "雾雨风",
                            "海月社",
                            "风之诗 Lolita",
                            "幽夜森林",
                            "月光酒馆",
                            "蒲公英的约定",
                            "绮丽梦境",
                            "天使之书",
                            "蔷薇十字",
                            "胡桃夹子 Lolita",
                            "糖果匣子",
                            "落日情书",
                            "微光之晨",
                            "云间月",
                            "山茶与兔",
                            "Angelic Pretty（AP）",
                            "Baby, the Stars Shine Bright（BABY）",
                            "Alice and the Pirates（ANP）",
                            "Innocent World（IW）",
                            "Metamorphose Temps de Fille（Meta）",
                            "Victorian Maiden",
                            "Juliette et Justine（JEJ）",
                            "Mary Magdalene（MM）",
                            "Atelier Boz",
                            "Atelier Pierrot",
                            "Moi-même-Moitié（MmM）",
                            "Jane Marple",
                            "Emily Temple Cute",
                            "Pink House",
                            "Bodyline",
                            "Antique Beast",
                            "Excentrique",
                            "Heart E",
                            "Millefleurs",
                            "Putumayo",
                            "h.NAOTO",
                            "MAXICIMAM",
                            "Grimoire",
                            "Sheglit",
                            "MILK",
                            "Chocochip Cookie",
                            "Triple Fortune",
                            "Royal Princess Alice",
                            "Enchantlic Enchantilly",
                            "Q-pot.",
                            "Physical Drop",
                            "Fairy Wish",
                            "Leur Getter",
                            "Aria",
                            "Chantilly",
                            "Lyrical Bunny",
                            "Honey Cake",
                            "Milk Cocoa",
                            "Sugar Dream",
                            "Black Peace Now",
                            "Listen Flavor",
                            "Kinji",
                            "Angelic Imprint",
                            "Princess Puff",
                            "Misty Rose",
                            "Lilac Time",
                            "Rose Cross",
                            "Velvet Opera",
                            "Crystal Doll",
                            "Silver Rain",
                            "Starry Night",
                            "Gothic Lolita Wigs（服饰线）",
                            "Sweet Devil",
                            "Dark Rose",
                            "White Swan",
                            "Red Ribbon",
                            "Blue Bird",
                            "Golden Crown",
                            "Purple Butterfly",
                            "Green Garden",
                            "Chantilly Korea",
                            "Lace Market（自有线）",
                            "Angelic Pretty Korea（韩线）",
                            "Baby Korea",
                            "Metamorphose Korea",
                            "Lolita Collective",
                            "Victorian Rose",
                            "Gothic Angel",
                            "Sweet Lolita Land",
                            "Dark Lolita Couture",
                            "Princess Lolita",
                            "Elegant Lolita",
                            "Romantic Lolita",
                            "Classic Lolita Co.",
                            "Pastel Lolita",
                            "Gothic Princess",
                            "Sweetheart Lolita",
                            "Lace & Ribbon",
                            "Bow & Bustle",
                            "Ruffled Romance",
                            "Velvet & Lace",
                            "Satin & Silk",
                            "Cotton Candy Lolita",
                            "Strawberry Shortcake",
                            "Cherry Blossom Lolita",
                            "Lavender Dream",
                            "Mint Chocolate",
                            "Peach Parfait",
                            "Blueberry Muffin",
                            "Raspberry Tart",
                            "Blackberry Sorbet",
                            "Plum Pudding",
                            "Apple Pie",
                            "Orange Marmalade",
                            "Lemon Tart",
                            "Grape Jelly",
                            "Pineapple Upside Down",
                            "Coconut Cream",
                            "Vanilla Bean",
                            "Chocolate Mousse",
                            "Gothic Lolita Couture",
                            "Lolita Fashion Co.",
                            "Cute Lolita",
                            "Pretty Lolita",
                            "Lovely Lolita",
                            "Adorable Lolita",
                            "Charming Lolita",
                            "Delightful Lolita",
                            "Enchanting Lolita",
                            "Fascinating Lolita",
                            "Gorgeous Lolita",
                            "Heavenly Lolita",
                            "Irresistible Lolita",
                            "Joyful Lolita",
                            "Kind Lolita",
                            "Lively Lolita",
                            "Magical Lolita",
                            "Nice Lolita",
                            "Outstanding Lolita",
                            "Perfect Lolita",
                            "Quaint Lolita",
                            "Radiant Lolita",
                            "Shining Lolita",
                            "Tender Lolita",
                            "Unique Lolita",
                            "Vivacious Lolita",
                            "Wonderful Lolita",
                            "Xquisite Lolita",
                            "Youthful Lolita",
                            "Zesty Lolita",
                            "OZZ on japan"
                        ).forEach { name ->
                            val escaped = name.replace("'", "''")
                            db.execSQL("INSERT OR IGNORE INTO brands (name, is_preset, created_at) VALUES ('$escaped', 1, $now)")
                        }

                        // Insert preset categories
                        val clothingCategories = listOf("JSK", "OP", "SK")
                        val accessoryCategories = listOf("KC", "斗篷", "披肩", "发带", "Bonnet", "其他头饰", "袜子", "手套", "其他配饰")
                        clothingCategories.forEach { name ->
                            db.execSQL("INSERT OR IGNORE INTO categories (name, is_preset, created_at, category_group) VALUES ('$name', 1, $now, 'CLOTHING')")
                        }
                        accessoryCategories.forEach { name ->
                            db.execSQL("INSERT OR IGNORE INTO categories (name, is_preset, created_at, category_group) VALUES ('$name', 1, $now, 'ACCESSORY')")
                        }

                        // Insert preset styles
                        listOf("甜系", "古典", "哥特", "田园", "中华", "其他").forEach { name ->
                            db.execSQL("INSERT OR IGNORE INTO styles (name, is_preset, created_at) VALUES ('$name', 1, $now)")
                        }

                        // Insert preset seasons
                        listOf("春", "夏", "秋", "冬", "四季").forEach { name ->
                            db.execSQL("INSERT OR IGNORE INTO seasons (name, is_preset, created_at) VALUES ('$name', 1, $now)")
                        }

                        // Insert preset sources
                        listOf("淘宝", "咸鱼", "线下").forEach { name ->
                            db.execSQL("INSERT OR IGNORE INTO sources (name, is_preset, created_at) VALUES ('$name', 1, $now)")
                        }
            }
        }
    }
}
