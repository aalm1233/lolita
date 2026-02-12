# 我的Lolita - 研究发现

## 技术选型

### Android 开发框架
- **选择**: Jetpack Compose
- **理由**: 现代化声明式UI，Google官方推荐，学习资源丰富

### 本地数据存储方案
- **选择**: Room Database
- **理由**: Google官方ORM框架，与Compose集成良好，类型安全

### 日历提醒实现
- **选择**: Android系统AlarmManager + NotificationManager
- **理由**: 无需依赖第三方日历应用，用户可控性更高

---

## UI/UX 设计方向

### 视觉风格：Lolita甜美风
- **主色调**: 粉色系 (#FFC0CB, #FFB6C1)
- **辅助色**: 奶油白 (#FFFDD0)、淡紫色 (#E6E6FA)
- **装饰元素**: 蕾丝边、蝴蝶结、花朵图案
- **字体**: 圆润可爱的字体

### 界面结构
```
┌─────────────────────────────────────┐
│           🎀 我的Lolita 🎀            │
├─────────────────────────────────────┤
│  [📦 套装]  [👗 服饰]  [⚙️ 设置]      │
├─────────────────────────────────────┤
│                                     │
│           内容展示区域               │
│                                     │
└─────────────────────────────────────┘
```

---

## 数据模型设计

### 实体关系图
```
┌─────────────┐     1:N     ┌─────────────┐
│  Coordinate │◄────────────│    Item     │
│    (套装)    │              │   (服饰)     │
└─────────────┘              └─────────────┘
                                      │
                                      │ 1:N
                                      ▼
                               ┌─────────────┐
                               │    Price    │
                               │   (价格)     │
                               └─────────────┘
                                      │
                                      │ 1:N
                                      ▼
                               ┌─────────────┐
                               │  Payment    │
                               │  (付款记录)  │
                               └─────────────┘

┌─────────────┐     1:N     ┌─────────────┐
│   Brand     │◄────────────│    Item     │
│  (店家)      │              │   (服饰)     │
└─────────────┘              └─────────────┘

┌─────────────┐     1:N     ┌─────────────┐
│  Category   │◄────────────│    Item     │
│  (类型)      │              │   (服饰)     │
└─────────────┘              └─────────────┘
```

### 详细实体设计

#### 1. Coordinate (套装)
```kotlin
data class Coordinate(
    id: Long,           // 主键
    name: String,       // 套装名称
    description: String,// 描述
    createdAt: Long,    // 创建时间
    updatedAt: Long     // 更新时间
)
```

#### 2. Item (服饰)
```kotlin
data class Item(
    id: Long,           // 主键
    coordinateId: Long?, // 所属套装ID (可为空，表示不属于任何套装)
    brandId: Long,      // 店家ID
    categoryId: Long,   // 类型ID
    name: String,       // 服饰名称
    description: String,// 描述
    imageUrl: String?,  // 图片URL
    status: ItemStatus, // 状态：已拥有/想要
    createdAt: Long,
    updatedAt: Long
)

enum class ItemStatus { OWNED, WISHED }
```

#### 3. Brand (店家)
```kotlin
data class Brand(
    id: Long,
    name: String,       // 店家名称
    createdAt: Long
)
```

#### 4. Category (服饰类型)
```kotlin
data class Category(
    id: Long,
    name: String,       // 类型名称 (如：JSK, OP, SK, KC等)
    createdAt: Long
)
```

#### 5. Price (价格)
```kotlin
data class Price(
    id: Long,
    itemId: Long,       // 关联的服饰ID
    type: PriceType,    // 价格类型：全价/定金+尾款
    totalPrice: Double, // 总价
    deposit: Double?,   // 定金 (仅定金+尾款模式)
    balance: Double?,   // 尾款 (仅定金+尾款模式)
    createdAt: Long
)

enum class PriceType { FULL, DEPOSIT_BALANCE }
```

#### 6. Payment (付款记录)
```kotlin
data class Payment(
    id: Long,
    priceId: Long,      // 关联的价格ID
    amount: Double,     // 付款金额
    dueDate: Long,      // 应付款时间
    isPaid: Boolean,    // 是否已付款
    paidDate: Long?,    // 实际付款时间
    reminderSet: Boolean, // 是否设置提醒
    createdAt: Long
)
```

---

## 推荐学习资源

### Jetpack Compose 入门
- [官方Compose教程](https://developer.android.com/jetpack/compose/tutorial)
- [Compose示例代码](https://github.com/android/compose-samples)

### Room Database 入门
- [Room基础指南](https://developer.android.com/training/data-storage/room)
- [Room与Compose集成](https://developer.android.com/training/data-storage/room#kotlin)

---

## 待解决问题
- [x] 确定具体的Lolita服饰类型枚举值 (JSK/OP/SK/KC/Bonnet等)
- [x] 确定店家列表的初始数据
- [x] 日历提醒的具体触发方式

---

## 详细需求确认 (2025-02-12)

### 服饰类型分类
| 大类 | 包含类型 |
|------|----------|
| 基础款 | JSK(无袖连衣裙), OP(有袖连衣裙), SK(半身裙) |
| KC/斗篷类 | KC(短斗篷), 斗篷, 披肩 |
| 头饰类 | 发带, Bonnet, 其他头饰 |
| 配饰类 | 袜子, 手套, 其他配饰 |

### 店家管理
- **模式**: 混合模式（预置常见品牌 + 用户自定义）
- **预置品牌列表**:
  ```
  - Baby, the Stars Shine Bright (Baby)
  - Angelic Pretty (AP)
  - Metamorphose (Meta)
  - Mary Magdalene (MM)
  - Innocent World (IW)
  - Victorian Maiden (VM)
  - JetJET
  - 汉洋元素 (国牌)
  - 魔魔 (国牌)
  ```
- **用户操作**: 支持添加/编辑/删除自定义品牌

### 日历提醒功能
- **提醒方式**: 应用内通知 (NotificationManager)
- **提醒时机**:
  - 当天提醒
  - 提前提醒 (默认1-3天)
  - 用户可自定义提前天数
- **实现技术**: AlarmManager + NotificationManager

### 图片处理
- **支持方式**: 拍照 + 相册选择
- **存储方案**: 本地文件存储 + 数据库保存路径

### 套装管理界面
- **列表视图**: 简洁列表展示所有套装
- **卡片视图**: 卡片形式展示（含封面图）
- **详情页**: 点击可查看套装内所有服饰详情
- **切换功能**: 支持在列表/卡片视图间切换

---

## 功能模块拆解

### 1. 配置中心模块
```
配置中心
├── 店家管理
│   ├── 查看所有店家列表
│   ├── 添加新店家
│   ├── 编辑店家名称
│   └── 删除店家
└── 服饰类型管理
    ├── 查看所有类型列表
    ├── 添加新类型
    ├── 编辑类型名称
    └── 删除类型
```

### 2. 套装管理模块
```
套装管理
├── 套装列表
│   ├── 列表视图
│   └── 卡片视图
├── 添加套装
│   ├── 输入套装名称
│   ├── 输入描述
│   └── 添加服饰到套装
├── 编辑套装
└── 删除套装
```

### 3. 服饰管理模块
```
服饰管理
├── 服饰列表
│   ├── 筛选：全部/已拥有/想要
│   └── 按店家/类型筛选
├── 添加服饰
│   ├── 选择店家
│   ├── 选择类型
│   ├── 输入服饰名称
│   ├── 上传图片 (拍照/相册)
│   ├── 选择状态 (已拥有/想要)
│   └── 可选：关联到套装
├── 服饰详情
│   ├── 基本信息
│   ├── 图片展示
│   └── 价格信息
└── 编辑/删除服饰
```

### 4. 价格管理模块
```
价格管理
├── 添加价格
│   ├── 选择价格类型 (全价/定金+尾款)
│   ├── 全价模式：输入总价
│   └── 定金+尾款模式：输入定金和尾款
├── 付款管理
│   ├── 添加付款记录
│   ├── 输入付款金额
│   ├── 设置应付款时间
│   ├── 设置提醒 (当天/提前N天/自定义)
│   ├── 标记付款状态
│   └── 查看付款历史
└── 统计面板
    ├── 总价统计
    ├── 已付款金额
    └── 未付款金额
```

### 5. 穿搭日记模块 (新增)
```
穿搭日记
├── 日记列表
│   ├── 按日期倒序排列
│   └── 日历视图查看
├── 添加日记
│   ├── 选择日期
│   ├── 上传穿搭照片 (拍照/相册)
│   ├── 添加文字备注/心得
│   └── 关联当天穿的服饰 (多选)
├── 日记详情
│   ├── 日期显示
│   ├── 照片展示 (支持多图)
│   ├── 文字内容
│   └── 关联服饰列表
└── 编辑/删除日记
```

### 6. 愿望单管理模块 (新增)
```
愿望单管理
├── 愿望单列表
│   ├── 按优先级排序 (高/中/低)
│   └── 筛选显示
├── 设置优先级
│   ├── 高优先级 (非常想要)
│   ├── 中优先级 (想要)
│   └── 低优先级 (观望)
└── 快速筛选
    ├── 只看高优先级
    ├── 只看中优先级
    └── 只看低优先级
```

### 7. 搜索功能模块 (新增)
```
搜索功能
├── 搜索入口
│   ├── 顶部搜索栏
│   └── 搜索图标
├── 搜索范围
│   ├── 按名称搜索
│   ├── 按店家搜索
│   ├── 按类型搜索
│   └── 按套装搜索
└── 搜索结果
    ├── 高亮关键词
    └── 支持模糊搜索
```

### 8. 数据统计面板 (新增)
```
数据统计
├── 消费统计
│   ├── 总消费金额
│   ├── 按店家统计
│   ├── 按类型统计
│   └── 按月份统计 (图表展示)
├── 收藏统计
│   ├── 已拥有数量
│   ├── 愿望单数量
│   ├── 按类型分布
│   └── 按店家分布
├── 套装统计
│   ├── 套装总数
│   ├── 单件服饰数
│   └── 穿搭记录数
└── 付款统计
    ├── 已付款总额
    ├── 未付款总额
    └── 待付款提醒
```

### 9. 数据备份/恢复模块 (新增)
```
数据备份/恢复
├── 数据导出
│   ├── 导出为JSON格式
│   │   ├── 包含所有数据
│   │   └── 方便完整备份
│   └── 导出为CSV格式
│       ├── 分别导出各表数据
│       └── 方便Excel查看
├── 数据恢复
│   ├── 从JSON恢复
│   │   ├── 选择备份文件
│   │   ├── 预览数据内容
│   │   └── 确认恢复
│   └── 恢复选项
│       ├── 覆盖现有数据
│       └── 合并现有数据
└── 自动备份
    ├── 设置自动备份
    ├── 选择备份频率
    └── 备份文件管理
```

---

## 更新的数据模型设计

### 新增实体

#### 7. OutfitLog (穿搭日记)
```kotlin
data class OutfitLog(
    id: Long,           // 主键
    date: Long,         // 穿搭日期
    note: String,       // 文字备注/心得
    imageUrls: List<String>, // 照片URL列表 (支持多图)
    createdAt: Long,    // 创建时间
    updatedAt: Long     // 更新时间
)

// 穿搭日记与服饰的多对多关联表
data class OutfitItemCrossRef(
    outfitLogId: Long,  // 日记ID
    itemId: Long        // 服饰ID
)
```

#### 更新 Item 实体
```kotlin
data class Item(
    id: Long,
    coordinateId: Long?,
    brandId: Long,
    categoryId: Long,
    name: String,
    description: String,
    imageUrl: String?,
    status: ItemStatus,
    priority: ItemPriority, // 新增：愿望单优先级
    createdAt: Long,
    updatedAt: Long
)

enum class ItemStatus { OWNED, WISHED }
enum class ItemPriority { HIGH, MEDIUM, LOW } // 新增
```

---

## Room Database 完整设计

### 数据库配置
```kotlin
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
@TypeConverters(
    Converters::class
)
abstract class LolitaDatabase : RoomDatabase() {
    abstract fun coordinateDao(): CoordinateDao
    abstract fun itemDao(): ItemDao
    abstract fun brandDao(): BrandDao
    abstract fun categoryDao(): CategoryDao
    abstract fun priceDao(): PriceDao
    abstract fun paymentDao(): PaymentDao
    abstract fun outfitLogDao(): OutfitLogDao
}
```

### 类型转换器 (TypeConverters)
```kotlin
class Converters {
    @TypeConverter
    fun fromItemStatus(value: ItemStatus): String = value.name

    @TypeConverter
    fun toItemStatus(value: String): ItemStatus = ItemStatus.valueOf(value)

    @TypeConverter
    fun fromItemPriority(value: ItemPriority): String = value.name

    @TypeConverter
    fun toItemPriority(value: String): ItemPriority = ItemPriority.valueOf(value)

    @TypeConverter
    fun fromPriceType(value: PriceType): String = value.name

    @TypeConverter
    fun toPriceType(value: String): PriceType = PriceType.valueOf(value)

    @TypeConverter
    fun fromStringList(value: List<String>): String = Gson().toJson(value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        Gson().fromJson(value, object : TypeToken<List<String>>() {}.type)
}
```

### 完整实体定义 (Room注解)

#### 1. Coordinate (套装)
```kotlin
@Entity(
    tableName = "coordinates",
    indices = [
        Index(value = ["name"]),
        Index(value = ["createdAt"])
    ]
)
data class Coordinate(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
```

#### 2. Item (服饰)
```kotlin
@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = Coordinate::class,
            parentColumns = ["id"],
            childColumns = ["coordinate_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Brand::class,
            parentColumns = ["id"],
            childColumns = ["brand_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["name"]),
        Index(value = ["coordinate_id"]),
        Index(value = ["brand_id"]),
        Index(value = ["category_id"]),
        Index(value = ["status"]),
        Index(value = ["priority"])
    ]
)
data class Item(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "coordinate_id")
    val coordinateId: Long? = null,

    @ColumnInfo(name = "brand_id")
    val brandId: Long,

    @ColumnInfo(name = "category_id")
    val categoryId: Long,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "image_url")
    val imageUrl: String? = null,

    @ColumnInfo(name = "status")
    val status: ItemStatus,

    @ColumnInfo(name = "priority", defaultValue = "MEDIUM")
    val priority: ItemPriority = ItemPriority.MEDIUM,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

enum class ItemStatus { OWNED, WISHED }
enum class ItemPriority { HIGH, MEDIUM, LOW }
```

#### 3. Brand (店家)
```kotlin
@Entity(
    tableName = "brands",
    indices = [Index(value = ["name"], unique = true)]
)
data class Brand(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "is_preset", defaultValue = "0")
    val isPreset: Boolean = false,  // 是否为预置品牌

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
```

#### 4. Category (服饰类型)
```kotlin
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "is_preset", defaultValue = "0")
    val isPreset: Boolean = false,  // 是否为预置类型

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
```

#### 5. Price (价格)
```kotlin
@Entity(
    tableName = "prices",
    foreignKeys = [
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["item_id"]),
        Index(value = ["type"])
    ]
)
data class Price(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "item_id")
    val itemId: Long,

    @ColumnInfo(name = "type")
    val type: PriceType,

    @ColumnInfo(name = "total_price")
    val totalPrice: Double,

    @ColumnInfo(name = "deposit")
    val deposit: Double? = null,

    @ColumnInfo(name = "balance")
    val balance: Double? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

enum class PriceType { FULL, DEPOSIT_BALANCE }
```

#### 6. Payment (付款记录)
```kotlin
@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = Price::class,
            parentColumns = ["id"],
            childColumns = ["price_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["price_id"]),
        Index(value = ["due_date"]),
        Index(value = ["is_paid"])
    ]
)
data class Payment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "price_id")
    val priceId: Long,

    @ColumnInfo(name = "amount")
    val amount: Double,

    @ColumnInfo(name = "due_date")
    val dueDate: Long,

    @ColumnInfo(name = "is_paid", defaultValue = "0")
    val isPaid: Boolean = false,

    @ColumnInfo(name = "paid_date")
    val paidDate: Long? = null,

    @ColumnInfo(name = "reminder_set", defaultValue = "0")
    val reminderSet: Boolean = false,

    @ColumnInfo(name = "custom_reminder_days")
    val customReminderDays: Int? = null,  // 自定义提前提醒天数

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
```

#### 7. OutfitLog (穿搭日记)
```kotlin
@Entity(
    tableName = "outfit_logs",
    indices = [
        Index(value = ["date"]),
        Index(value = ["created_at"])
    ]
)
data class OutfitLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "date")
    val date: Long,

    @ColumnInfo(name = "note")
    val note: String,

    @ColumnInfo(name = "image_urls")
    val imageUrls: List<String> = emptyList(),

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
```

#### 8. OutfitItemCrossRef (穿搭日记-服饰关联)
```kotlin
@Entity(
    tableName = "outfit_item_cross_ref",
    primaryKeys = ["outfit_log_id", "item_id"],
    foreignKeys = [
        ForeignKey(
            entity = OutfitLog::class,
            parentColumns = ["id"],
            childColumns = ["outfit_log_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["outfit_log_id"]),
        Index(value = ["item_id"])
    ]
)
data class OutfitItemCrossRef(
    @ColumnInfo(name = "outfit_log_id")
    val outfitLogId: Long,

    @ColumnInfo(name = "item_id")
    val itemId: Long
)
```

---

### DAO 接口定义

#### CoordinateDao
```kotlin
@Dao
interface CoordinateDao {
    @Query("SELECT * FROM coordinates ORDER BY updated_at DESC")
    fun getAllCoordinates(): Flow<List<Coordinate>>

    @Query("SELECT * FROM coordinates WHERE id = :id")
    suspend fun getCoordinateById(id: Long): Coordinate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoordinate(coordinate: Coordinate): Long

    @Update
    suspend fun updateCoordinate(coordinate: Coordinate)

    @Delete
    suspend fun deleteCoordinate(coordinate: Coordinate)

    @Transaction
    @Query("SELECT * FROM coordinates WHERE id = :id")
    fun getCoordinateWithItems(id: Long): Flow<CoordinateWithItems>
}

// 关系数据类
data class CoordinateWithItems(
    @Embedded val coordinate: Coordinate,
    @Relation(
        parentColumn = "id",
        entityColumn = "coordinate_id"
    )
    val items: List<Item>
)
```

#### ItemDao
```kotlin
@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY updated_at DESC")
    fun getAllItems(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getItemById(id: Long): Item?

    @Query("SELECT * FROM items WHERE status = :status ORDER BY updated_at DESC")
    fun getItemsByStatus(status: ItemStatus): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE coordinate_id = :coordinateId")
    fun getItemsByCoordinate(coordinateId: Long): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE brand_id = :brandId")
    fun getItemsByBrand(brandId: Long): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE category_id = :categoryId")
    fun getItemsByCategory(categoryId: Long): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchItemsByName(query: String): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE status = 'WISHED' ORDER BY priority DESC, updated_at DESC")
    fun getWishlistByPriority(): Flow<List<Item>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: Item): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<Item>)

    @Update
    suspend fun updateItem(item: Item)

    @Delete
    suspend fun deleteItem(item: Item)

    @Transaction
    @Query("SELECT * FROM items WHERE id = :id")
    fun getItemWithPrice(id: Long): Flow<ItemWithPrice>

    @Transaction
    @Query("SELECT * FROM items WHERE id = :id")
    fun getItemWithFullDetails(id: Long): Flow<ItemWithFullDetails>
}

data class ItemWithPrice(
    @Embedded val item: Item,
    @Relation(
        parentColumn = "id",
        entityColumn = "item_id"
    )
    val prices: List<Price>
)

data class ItemWithFullDetails(
    @Embedded val item: Item,
    @Relation(parentColumn = "brand_id", entityColumn = "id")
    val brand: Brand,
    @Relation(parentColumn = "category_id", entityColumn = "id")
    val category: Category,
    @Relation(parentColumn = "id", entityColumn = "item_id")
    val prices: List<Price>
)
```

#### BrandDao
```kotlin
@Dao
interface BrandDao {
    @Query("SELECT * FROM brands ORDER BY name ASC")
    fun getAllBrands(): Flow<List<Brand>>

    @Query("SELECT * FROM brands WHERE id = :id")
    suspend fun getBrandById(id: Long): Brand?

    @Query("SELECT * FROM brands WHERE is_preset = 1 ORDER BY name ASC")
    fun getPresetBrands(): Flow<List<Brand>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBrand(brand: Brand): Long

    @Update
    suspend fun updateBrand(brand: Brand)

    @Delete
    suspend fun deleteBrand(brand: Brand)

    @Query("SELECT * FROM brands WHERE name = :name LIMIT 1")
    suspend fun getBrandByName(name: String): Brand?
}
```

#### CategoryDao
```kotlin
@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): Category?

    @Query("SELECT * FROM categories WHERE is_preset = 1 ORDER BY name ASC")
    fun getPresetCategories(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)
}
```

#### PriceDao
```kotlin
@Dao
interface PriceDao {
    @Query("SELECT * FROM prices WHERE item_id = :itemId")
    fun getPricesByItem(itemId: Long): Flow<List<Price>>

    @Query("SELECT * FROM prices WHERE id = :id")
    suspend fun getPriceById(id: Long): Price?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrice(price: Price): Long

    @Update
    suspend fun updatePrice(price: Price)

    @Delete
    suspend fun deletePrice(price: Price)

    @Transaction
    @Query("SELECT * FROM prices WHERE id = :id")
    fun getPriceWithPayments(id: Long): Flow<PriceWithPayments>
}

data class PriceWithPayments(
    @Embedded val price: Price,
    @Relation(
        parentColumn = "id",
        entityColumn = "price_id"
    )
    val payments: List<Payment>
)
```

#### PaymentDao
```kotlin
@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY due_date ASC")
    fun getAllPayments(): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE price_id = :priceId")
    fun getPaymentsByPrice(priceId: Long): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE is_paid = 0 ORDER BY due_date ASC")
    fun getUnpaidPayments(): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE is_paid = 0 AND reminder_set = 1 ORDER BY due_date ASC")
    fun getPendingReminderPayments(): Flow<List<Payment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: Payment): Long

    @Update
    suspend fun updatePayment(payment: Payment)

    @Delete
    suspend fun deletePayment(payment: Payment)
}
```

#### OutfitLogDao
```kotlin
@Dao
interface OutfitLogDao {
    @Query("SELECT * FROM outfit_logs ORDER BY date DESC")
    fun getAllOutfitLogs(): Flow<List<OutfitLog>>

    @Query("SELECT * FROM outfit_logs WHERE id = :id")
    suspend fun getOutfitLogById(id: Long): OutfitLog?

    @Query("SELECT * FROM outfit_logs WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getOutfitLogsByDateRange(startDate: Long, endDate: Long): Flow<List<OutfitLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutfitLog(outfitLog: OutfitLog): Long

    @Update
    suspend fun updateOutfitLog(outfitLog: OutfitLog)

    @Delete
    suspend fun deleteOutfitLog(outfitLog: OutfitLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutfitItemCrossRef(crossRef: OutfitItemCrossRef)

    @Delete
    suspend fun deleteOutfitItemCrossRef(crossRef: OutfitItemCrossRef)

    @Transaction
    @Query("SELECT * FROM outfit_logs WHERE id = :id")
    fun getOutfitLogWithItems(id: Long): Flow<OutfitLogWithItems>
}

data class OutfitLogWithItems(
    @Embedded val outfitLog: OutfitLog,
    @Relation(
        parentColumn = "id",
        entityColumn = "outfit_log_id",
        associateBy = Junction(
            value = OutfitItemCrossRef::class,
            parentColumn = "outfit_log_id",
            entityColumn = "item_id"
        )
    )
    val items: List<Item>
)
```

---

### 数据库初始化与预置数据

```kotlin
class DatabaseCallback(private val context: Context) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // 在IO线程执行预置数据插入
        CoroutineScope(Dispatchers.IO).launch {
            populateDatabase(context)
        }
    }

    private suspend fun populateDatabase(context: Context) {
        val database = Room.databaseBuilder(
            context,
            LolitaDatabase::class.java,
            "lolita_database"
        ).build()

        // 插入预置品牌
        val presetBrands = listOf(
            Brand(name = "Baby, the Stars Shine Bright", isPreset = true),
            Brand(name = "Angelic Pretty", isPreset = true),
            Brand(name = "Metamorphose", isPreset = true),
            Brand(name = "Mary Magdalene", isPreset = true),
            Brand(name = "Innocent World", isPreset = true),
            Brand(name = "Victorian Maiden", isPreset = true),
            Brand(name = "JetJET", isPreset = true),
            Brand(name = "汉洋元素", isPreset = true),
            Brand(name = "魔魔", isPreset = true)
        )
        presetBrands.forEach { database.brandDao().insertBrand(it) }

        // 插入预置类型
        val presetCategories = listOf(
            Category(name = "JSK", isPreset = true),
            Category(name = "OP", isPreset = true),
            Category(name = "SK", isPreset = true),
            Category(name = "KC", isPreset = true),
            Category(name = "斗篷", isPreset = true),
            Category(name = "披肩", isPreset = true),
            Category(name = "发带", isPreset = true),
            Category(name = "Bonnet", isPreset = true),
            Category(name = "其他头饰", isPreset = true),
            Category(name = "袜子", isPreset = true),
            Category(name = "手套", isPreset = true),
            Category(name = "其他配饰", isPreset = true)
        )
        presetCategories.forEach { database.categoryDao().insertCategory(it) }

        database.close()
    }
}

fun getDatabase(context: Context): LolitaDatabase {
    return Room.databaseBuilder(
        context,
        LolitaDatabase::class.java,
        "lolita_database"
    )
        .addCallback(DatabaseCallback(context))
        .fallbackToDestructiveMigration()  // 开发阶段使用
        // .addMigrations(MIGRATION_1_2)  // 生产环境需要迁移策略
        .build()
}
```

---

### 数据库设计总结

| 项目 | 内容 |
|------|------|
| 数据库名称 | lolita_database |
| 版本 | 1 |
| 实体数量 | 7个主实体 + 1个关联表 |
| 关系类型 | 1:N (套装-服饰), 1:N (服饰-价格), N:M (日记-服饰) |
| 索引 | 已在关键字段(name, date, status等)上添加索引 |
| 外键约束 | 级联删除/更新已配置 |
| 预置数据 | 9个品牌 + 12个类型 |
| TypeConverter | 枚举类 + List<String>转换器 |

---

## 应用架构设计 (MVVM)

### 架构分层图
```
┌─────────────────────────────────────────────────────────────┐
│                      UI Layer (Jetpack Compose)            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ CoordinateScreen│  │   ItemScreen  │  │ SettingsScreen│  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ OutfitLogScreen│  │ SearchScreen  │  │ StatsScreen    │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │ observes
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    ViewModel Layer                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │CoordinateViewModel│ │ItemViewModel │ │OutfitViewModel │ │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │SettingsViewModel│ │SearchViewModel│ │StatsViewModel  │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │ calls
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   Repository Layer                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │CoordinateRepository│ │ItemRepository│ │OutfitRepository│ │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ BrandRepository│ │PriceRepository│ │PaymentRepository│ │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │ uses
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    Data Layer                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │          LolitaDatabase (Room)                      │  │
│  │  CoordinateDao │ ItemDao │ BrandDao │ ...          │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │          FileManager (图片本地存储)                  │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │          NotificationScheduler (日历提醒)            │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 包结构设计
```
com.lolita.app/
├── data/
│   ├── local/
│   │   ├── entity/           # Room实体
│   │   │   ├── Coordinate.kt
│   │   │   ├── Item.kt
│   │   │   ├── Brand.kt
│   │   │   ├── Category.kt
│   │   │   ├── Price.kt
│   │   │   ├── Payment.kt
│   │   │   ├── OutfitLog.kt
│   │   │   └── OutfitItemCrossRef.kt
│   │   ├── dao/               # DAO接口
│   │   │   ├── CoordinateDao.kt
│   │   │   ├── ItemDao.kt
│   │   │   ├── BrandDao.kt
│   │   │   ├── CategoryDao.kt
│   │   │   ├── PriceDao.kt
│   │   │   ├── PaymentDao.kt
│   │   │   └── OutfitLogDao.kt
│   │   ├── converters/        # 类型转换器
│   │   │   └── Converters.kt
│   │   └── LolitaDatabase.kt
│   ├── repository/             # Repository层
│   │   ├── CoordinateRepository.kt
│   │   ├── ItemRepository.kt
│   │   ├── BrandRepository.kt
│   │   ├── CategoryRepository.kt
│   │   ├── PriceRepository.kt
│   │   ├── PaymentRepository.kt
│   │   └── OutfitLogRepository.kt
│   ├── file/                  # 文件管理
│   │   ├── ImageFileManager.kt
│   │   └── BackupManager.kt
│   └── notification/           # 通知调度
│       └── PaymentReminderScheduler.kt
├── domain/
│   ├── model/                 # 领外模型 (如需要)
│   └── usecase/              # 业务用例 (如需要)
├── ui/
│   ├── navigation/            # 导航配置
│   │   ├── Screen.kt
│   │   ├── NavRoute.kt
│   │   └── LolitaNavHost.kt
│   ├── theme/                 # 主题配置
│   │   ├── Color.kt
│   │   ├── Type.kt
│   │   └── Theme.kt
│   ├── components/            # 可复用组件
│   │   ├── ItemCard.kt
│   │   ├── CoordinateCard.kt
│   │   ├── SearchBar.kt
│   │   └── ...
│   ├── screen/
│   │   ├── coordinate/
│   │   │   ├── CoordinateListScreen.kt
│   │   │   ├── CoordinateDetailScreen.kt
│   │   │   └── CoordinateViewModel.kt
│   │   ├── item/
│   │   │   ├── ItemListScreen.kt
│   │   │   ├── ItemDetailScreen.kt
│   │   │   ├── ItemEditScreen.kt
│   │   │   └── ItemViewModel.kt
│   │   ├── outfit/
│   │   │   ├── OutfitLogListScreen.kt
│   │   │   ├── OutfitLogDetailScreen.kt
│   │   │   └── OutfitLogViewModel.kt
│   │   ├── settings/
│   │   │   ├── SettingsScreen.kt
│   │   │   ├── BrandManageScreen.kt
│   │   │   ├── CategoryManageScreen.kt
│   │   │   └── SettingsViewModel.kt
│   │   ├── search/
│   │   │   ├── SearchScreen.kt
│   │   │   └── SearchViewModel.kt
│   │   └── stats/
│   │       ├── StatsScreen.kt
│   │       └── StatsViewModel.kt
│   └── LolitaApp.kt
├── di/                      # 依赖注入 (手动DI/Hilt)
│   └── AppModule.kt
└── util/                    # 工具类
    ├── DateUtils.kt
    └── ImageUtils.kt
```

### Repository设计

#### CoordinateRepository
```kotlin
class CoordinateRepository(
    private val coordinateDao: CoordinateDao,
    private val itemDao: ItemDao
) {
    fun getAllCoordinates(): Flow<List<Coordinate>> =
        coordinateDao.getAllCoordinates()

    suspend fun getCoordinateById(id: Long): Coordinate? =
        coordinateDao.getCoordinateById(id)

    fun getCoordinateWithItems(id: Long): Flow<CoordinateWithItems> =
        coordinateDao.getCoordinateWithItems(id)

    @Transaction
    suspend fun insertCoordinate(coordinate: Coordinate): Long {
        val id = coordinateDao.insertCoordinate(coordinate)
        return id
    }

    suspend fun updateCoordinate(coordinate: Coordinate) =
        coordinateDao.updateCoordinate(coordinate.copy(updatedAt = System.currentTimeMillis()))

    @Transaction
    suspend fun deleteCoordinate(coordinate: Coordinate) {
        // 先解除服饰关联
        coordinateDao.getCoordinateWithItems(coordinate.id).first()?.items?.forEach { item ->
            itemDao.updateItem(item.copy(coordinateId = null))
        }
        coordinateDao.deleteCoordinate(coordinate)
    }
}
```

#### ItemRepository
```kotlin
class ItemRepository(
    private val itemDao: ItemDao,
    private val brandDao: BrandDao,
    private val categoryDao: CategoryDao,
    private val priceDao: PriceDao
) {
    fun getAllItems(): Flow<List<Item>> = itemDao.getAllItems()

    fun getItemsByStatus(status: ItemStatus): Flow<List<Item>> =
        itemDao.getItemsByStatus(status)

    fun getWishlistByPriority(): Flow<List<Item>> =
        itemDao.getWishlistByPriority()

    fun searchItemsByName(query: String): Flow<List<Item>> =
        itemDao.searchItemsByName(query)

    suspend fun insertItem(item: Item): Long = itemDao.insertItem(item)

    suspend fun updateItem(item: Item) =
        itemDao.updateItem(item.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteItem(item: Item) = itemDao.deleteItem(item)

    fun getItemWithFullDetails(id: Long): Flow<ItemWithFullDetails> =
        itemDao.getItemWithFullDetails(id)
}
```

#### BrandRepository
```kotlin
class BrandRepository(
    private val brandDao: BrandDao
) {
    fun getAllBrands(): Flow<List<Brand>> = brandDao.getAllBrands()

    fun getPresetBrands(): Flow<List<Brand>> = brandDao.getPresetBrands()

    suspend fun insertBrand(brand: Brand): Long = brandDao.insertBrand(brand)

    suspend fun updateBrand(brand: Brand) = brandDao.updateBrand(brand)

    suspend fun deleteBrand(brand: Brand) {
        require(!brand.isPreset) { "预置品牌不可删除" }
        brandDao.deleteBrand(brand)
    }
}
```

#### PaymentReminderRepository
```kotlin
class PaymentReminderRepository(
    private val paymentDao: PaymentDao,
    private val context: Context
) {
    fun getUnpaidPayments(): Flow<List<Payment>> =
        paymentDao.getUnpaidPayments()

    fun getPendingReminderPayments(): Flow<List<Payment>> =
        paymentDao.getPendingReminderPayments()

    suspend fun updatePayment(payment: Payment) {
        paymentDao.updatePayment(payment)
        if (payment.isPaid) {
            cancelReminder(payment)
        } else if (payment.reminderSet) {
            scheduleReminder(payment)
        }
    }

    private fun scheduleReminder(payment: Payment) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PaymentReminderReceiver::class.java).apply {
            putExtra("payment_id", payment.id)
            putExtra("amount", payment.amount)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            payment.id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val reminderTime = payment.dueDate - (payment.customReminderDays ?: 1) * DAY_IN_MILLIS
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminderTime,
            pendingIntent
        )
    }

    private fun cancelReminder(payment: Payment) {
        val intent = Intent(context, PaymentReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            payment.id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }
}
```

---

### 导航架构设计

#### Screen定义
```kotlin
sealed interface Screen {
    @Serializable
    data object CoordinateList : Screen

    @Serializable
    data class CoordinateDetail(val coordinateId: Long) : Screen

    @Serializable
    data object ItemList : Screen

    @Serializable
    data class ItemDetail(val itemId: Long) : Screen

    @Serializable
    data object Wishlist : Screen

    @Serializable
    data object OutfitLogList : Screen

    @Serializable
    data class OutfitLogDetail(val logId: Long) : Screen

    @Serializable
    data object Search : Screen

    @Serializable
    data object Stats : Screen

    @Serializable
    data object Settings : Screen

    @Serializable
    data object BrandManage : Screen

    @Serializable
    data object CategoryManage : Screen
}
```

#### 导航主机
```kotlin
@Composable
fun LolitaNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.ItemList,
        modifier = modifier
    ) {
        // 套装管理
        composable<Screen.CoordinateList> {
            CoordinateListScreen(
                onNavigateToDetail = { coordinateId ->
                    navController.navigate(Screen.CoordinateDetail(coordinateId))
                }
            )
        }

        composable<Screen.CoordinateDetail> { backStackEntry ->
            val coordinateId = backStackEntry.arguments?.getLong("coordinateId") ?: return@composable
            CoordinateDetailScreen(
                coordinateId = coordinateId,
                onBack = { navController.popBackStack() }
            )
        }

        // 服饰管理
        composable<Screen.ItemList> {
            ItemListScreen(
                onNavigateToDetail = { itemId ->
                    navController.navigate(Screen.ItemDetail(itemId))
                },
                onNavigateToWishlist = {
                    navController.navigate(Screen.Wishlist)
                }
            )
        }

        composable<Screen.ItemDetail> { backStackEntry ->
            val itemId = backStackEntry.arguments?.getLong("itemId") ?: return@composable
            ItemDetailScreen(
                itemId = itemId,
                onBack = { navController.popBackStack() }
            )
        }

        // 愿望单
        composable<Screen.Wishlist> {
            WishlistScreen(
                onNavigateToDetail = { itemId ->
                    navController.navigate(Screen.ItemDetail(itemId))
                }
            )
        }

        // 穿搭日记
        composable<Screen.OutfitLogList> {
            OutfitLogListScreen(
                onNavigateToDetail = { logId ->
                    navController.navigate(Screen.OutfitLogDetail(logId))
                }
            )
        }

        composable<Screen.OutfitLogDetail> { backStackEntry ->
            val logId = backStackEntry.arguments?.getLong("logId") ?: return@composable
            OutfitLogDetailScreen(
                logId = logId,
                onBack = { navController.popBackStack() }
            )
        }

        // 搜索
        composable<Screen.Search> {
            SearchScreen(
                onNavigateToItem = { itemId ->
                    navController.navigate(Screen.ItemDetail(itemId))
                }
            )
        }

        // 统计
        composable<Screen.Stats> {
            StatsScreen()
        }

        // 设置
        composable<Screen.Settings> {
            SettingsScreen(
                onNavigateToBrand = {
                    navController.navigate(Screen.BrandManage)
                },
                onNavigateToCategory = {
                    navController.navigate(Screen.CategoryManage)
                }
            )
        }

        composable<Screen.BrandManage> {
            BrandManageScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<Screen.CategoryManage> {
            CategoryManageScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
```

---

### 依赖注入方案

#### 手动DI模块 (适合初学者)
```kotlin
// di/AppModule.kt
object AppModule {
    private lateinit var database: LolitaDatabase
    private lateinit var imageFileManager: ImageFileManager

    fun init(context: Context) {
        database = getDatabase(context)
        imageFileManager = ImageFileManager(context)
    }

    // Repositories
    fun coordinateRepository() = CoordinateRepository(
        database.coordinateDao(),
        database.itemDao()
    )

    fun itemRepository() = ItemRepository(
        database.itemDao(),
        database.brandDao(),
        database.categoryDao(),
        database.priceDao()
    )

    fun brandRepository() = BrandRepository(database.brandDao())

    fun categoryRepository() = CategoryRepository(database.categoryDao())

    fun priceRepository() = PriceRepository(database.priceDao())

    fun paymentRepository() = PaymentRepository(
        database.paymentDao(),
        database.priceDao()
    )

    fun paymentReminderRepository(context: Context) = PaymentReminderRepository(
        database.paymentDao(),
        context
    )

    fun outfitLogRepository() = OutfitLogRepository(
        database.outfitLogDao(),
        database.itemDao()
    )

    // Utilities
    fun imageFileManager() = imageFileManager

    fun backupManager(context: Context) = BackupManager(
        database,
        imageFileManager
    )
}
```

#### Application类初始化
```kotlin
// LolitaApplication.kt
class LolitaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppModule.init(applicationContext)
    }
}

// AndroidManifest.xml
<application
    android:name=".LolitaApplication"
    ... >
    ...
</application>
```

---

### UI状态管理 (State + UiState模式)

#### ViewModel基类
```kotlin
abstract class LolitaViewModel<T : UiState> : ViewModel() {
    private val _uiState = mutableStateOf<T?>(null)
    val uiState: State<T?> = _uiState.asState()

    protected fun updateUiState(newState: T) {
        _uiState.value = newState
    }
}

interface UiState
```

#### 示例: ItemListViewModel
```kotlin
@HiltViewModel
class ItemListViewModel(
    private val itemRepository: ItemRepository
) : ViewModel() {

    private val _uiState = mutableStateOf(ItemListUiState())
    val uiState: State<ItemListUiState> = _uiState.asState()

    init {
        loadItems()
    }

    private fun loadItems() {
        viewModelScope.launch {
            itemRepository.getAllItems().collect { items ->
                _uiState.update { it.copy(items = items) }
            }
        }
    }

    fun filterByStatus(status: ItemStatus?) {
        viewModelScope.launch {
            val flow = if (status == null) {
                itemRepository.getAllItems()
            } else {
                itemRepository.getItemsByStatus(status)
            }
            flow.collect { items ->
                _uiState.update { it.copy(items = items, filterStatus = status) }
            }
        }
    }

    fun deleteItem(item: Item) {
        viewModelScope.launch {
            itemRepository.deleteItem(item)
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                itemRepository.getAllItems().collect { items ->
                    _uiState.update { it.copy(items = items, searchQuery = "") }
                }
            } else {
                itemRepository.searchItemsByName(query).collect { items ->
                    _uiState.update { it.copy(items = items, searchQuery = query) }
                }
            }
        }
    }
}

data class ItemListUiState(
    val items: List<Item> = emptyList(),
    val isLoading: Boolean = true,
    val filterStatus: ItemStatus? = null,
    val searchQuery: String = ""
) : UiState
```

---

### 主题设计 (Lolita甜美风)

```kotlin
// ui/theme/Color.kt
val Pink30 = Color(0xFFFFF0F5)
val Pink50 = Color(0xFFFFD6E7)
val Pink100 = Color(0xFFFFC0CB)
val Pink200 = Color(0xFFFFB6C1)
val Pink300 = Color(0xFFFF91A4)
val Pink400 = Color(0xFFFF69B4)
val Pink500 = Color(0xFFFF1493)

val Cream = Color(0xFFFFFDD0)
val Lavender = Color(0xFFE6E6FA)
val White = Color(0xFFFFFFFF)
val Black = Color(0xFF000000)

val LolitaColors = darkColors(
    primary = Pink400,
    primaryContainer = Pink100,
    secondary = Lavender,
    secondaryContainer = Cream,
    background = Pink30,
    surface = White,
    error = Color(0xFFD32F2F)
)

// ui/theme/Type.kt
val LolitaTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

// ui/theme/Theme.kt
@Composable
fun LolitaTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) darkColors(
        primary = Pink400,
        ...
    ) else LolitaColors

    MaterialTheme(
        colorScheme = colors,
        typography = LolitaTypography,
        content = content
    )
}
```

---

### 架构设计总结

| 层次 | 技术 | 职责 |
|------|------|------|
| UI Layer | Jetpack Compose + Navigation | 界面展示、用户交互 |
| ViewModel Layer | State + UiState | 业务逻辑、状态管理 |
| Repository Layer | Kotlin Coroutines + Flow | 数据聚合、业务用例 |
| Data Layer | Room Database + FileManager | 数据持久化 |
| DI | 手动单例 (AppModule) | 依赖管理 |
| Theme | Material3 + 自定义颜色 | Lolita甜美风格 |
