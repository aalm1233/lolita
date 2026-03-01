# 自定义属性系统设计

日期: 2026-03-01

## 概述

支持用户在属性管理中新增自定义属性模板，并为单品（Item）和穿搭（Coordinate）关联填写属性值。属性支持多种数据类型：文本、数字、选项（枚举）、日期、开关。

## 数据库设计

### 新增表

#### CustomAttribute（自定义属性模板）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long (PK, auto) | 主键 |
| name | String | 属性名，如"面料" |
| type | AttributeType | TEXT, NUMBER, OPTION, DATE, BOOLEAN |
| isRequired | Boolean | 是否必填 |
| sortOrder | Int | 排序 |
| entityType | EntityType | ITEM, COORDINATE, BOTH |

#### CustomAttributeOption（选项类型的枚举值）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long (PK, auto) | 主键 |
| attributeId | Long (FK) | 关联属性模板，CASCADE 删除 |
| value | String | 选项文本 |
| sortOrder | Int | 排序 |

#### CustomAttributeValue（属性值）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long (PK, auto) | 主键 |
| attributeId | Long (FK) | 关联属性模板，CASCADE 删除 |
| entityType | EntityType | ITEM 或 COORDINATE |
| entityId | Long | 对应实体 ID |
| valueJson | String | JSON 编码的值 |

### 枚举定义

```kotlin
enum class AttributeType { TEXT, NUMBER, OPTION, DATE, BOOLEAN }
enum class EntityType { ITEM, COORDINATE, BOTH }
```

### valueJson 存储示例

| 类型 | JSON 示例 |
|------|-----------|
| TEXT | `"纯棉"` |
| NUMBER | `80.5` |
| OPTION | `"option_id_123"` 或 `["id1","id2"]`（多选） |
| DATE | `"2026-03-01"` |
| BOOLEAN | `true` |

### 索引

- `CustomAttributeValue`: `(entityType, entityId)` 复合索引，加速按实体查询

### 数据库迁移

版本 14 → 15，新增 3 张表，无破坏性变更。

## UI 设计

### 1. 自定义属性管理页 (CustomAttributeManageScreen)

**路由**: `settings/custom-attributes`

**入口**: 设置页 → "自定义属性"（新增独立设置项）

**功能**:
- 列表展示所有自定义属性模板
- 显示：属性名、类型图标、适用范围、是否必填标记
- 支持新增/编辑/删除/拖拽排序
- 长按进入编辑模式，支持批量删除

**空状态**: "暂无自定义属性，点击右上角 + 添加"

### 2. 属性编辑对话框 (CustomAttributeEditDialog)

**触发**: 管理页点击新增/编辑

**字段**:
- 属性名（必填文本框）
- 数据类型（下拉选择：文本/数字/选项/日期/开关）
- 适用范围（下拉选择：单品/穿搭/两者）
- 是否必填（开关）
- 选项列表（仅类型=选项时显示）:
  - 可新增/删除/拖拽排序
  - 每个选项一个文本框

**交互**:
- 类型切换时，选项列表自动显示/隐藏
- 删除选项需二次确认（若已有值关联）
- 保存时校验属性名不重复

### 3. ItemEditScreen 集成

**位置**: 现有字段下方新增"自定义属性"区块

**展示**:
- 根据 `entityType` 筛选适用的属性模板（ITEM 或 BOTH）
- 按 `sortOrder` 排序
- 必填属性标记 `*` 号

**输入组件**:
| 类型 | 组件 |
|------|------|
| TEXT |OutlinedTextField |
| NUMBER | OutlinedTextField (keyboardType = KeyboardType.Number) |
| OPTION | 单选：RadioButton 组 / 多选：Checkbox 组 |
| DATE | DatePicker 对话框 |
| BOOLEAN | Switch |

**保存逻辑**:
- 保存 Item 时，一并保存/更新属性值
- 校验必填属性是否已填写，未填写则阻止保存并提示
- 删除 Item 时，CASCADE 删除属性值

### 4. CoordinateEditScreen 集成

同 ItemEditScreen，但筛选 `entityType` 为 COORDINATE 或 BOTH 的属性。

### 5. 详情页展示

**ItemDetailScreen / CoordinateDetailScreen**:
- 新增"自定义属性"区块（位于现有字段下方）
- 按模板排序展示
- 仅显示已填写值的属性
- 空值不显示该行

## 数据流

### Repository 层

```kotlin
class CustomAttributeRepository(
    private val customAttributeDao: CustomAttributeDao,
    private val customAttributeOptionDao: CustomAttributeOptionDao,
    private val customAttributeValueDao: CustomAttributeValueDao
) {
    // 属性模板 CRUD
    fun getAllAttributes(): Flow<List<CustomAttributeWithOptionCount>>
    suspend fun insertAttribute(attribute: CustomAttribute, options: List<String>): Long
    suspend fun updateAttribute(attribute: CustomAttribute, options: List<String>)
    suspend fun deleteAttribute(id: Long)
    suspend fun reorderAttributes(ids: List<Long>)
    
    // 属性值
    fun getValuesForEntity(entityType: EntityType, entityId: Long): Flow<List<AttributeValueWithMeta>>
    suspend fun saveValue(attributeId: Long, entityType: EntityType, entityId: Long, valueJson: String)
    suspend fun saveValues(values: List<CustomAttributeValue>)
    suspend fun deleteValue(id: Long)
}
```

### DAO 查询

```kotlin
@Dao
interface CustomAttributeDao {
    @Query("SELECT * FROM CustomAttribute ORDER BY sortOrder ASC")
    fun getAll(): Flow<List<CustomAttribute>>
    
    @Query("SELECT * FROM CustomAttribute WHERE entityType IN (:types) ORDER BY sortOrder ASC")
    fun getByEntityType(types: List<EntityType>): Flow<List<CustomAttribute>>
    
    @Insert
    suspend fun insert(attribute: CustomAttribute): Long
    
    @Update
    suspend fun update(attribute: CustomAttribute)
    
    @Delete
    suspend fun delete(attribute: CustomAttribute)
}

@Dao
interface CustomAttributeOptionDao {
    @Query("SELECT * FROM CustomAttributeOption WHERE attributeId = :attributeId ORDER BY sortOrder ASC")
    fun getByAttribute(attributeId: Long): Flow<List<CustomAttributeOption>>
    
    @Insert
    suspend fun insert(option: CustomAttributeOption): Long
    
    @Insert
    suspend fun insertAll(options: List<CustomAttributeOption>)
    
    @Query("DELETE FROM CustomAttributeOption WHERE attributeId = :attributeId")
    suspend fun deleteByAttribute(attributeId: Long)
}

@Dao
interface CustomAttributeValueDao {
    @Query("""
        SELECT cav.*, ca.name as attributeName, ca.type as attributeType
        FROM CustomAttributeValue cav
        JOIN CustomAttribute ca ON cav.attributeId = ca.id
        WHERE cav.entityType = :entityType AND cav.entityId = :entityId
    """)
    fun getValuesWithMeta(entityType: EntityType, entityId: Long): Flow<List<AttributeValueWithMeta>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(value: CustomAttributeValue)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(values: List<CustomAttributeValue>)
    
    @Query("DELETE FROM CustomAttributeValue WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun deleteByEntity(entityType: EntityType, entityId: Long)
}
```

### ViewModel 扩展

```kotlin
// 新增 ViewModel
class CustomAttributeManageViewModel(
    private val repository: CustomAttributeRepository = AppModule.customAttributeRepository()
) : ViewModel() {
    val attributes: StateFlow<List<CustomAttributeWithOptionCount>>
    val uiState: StateFlow<UiState>
    
    fun loadAttributes()
    fun createAttribute(name: String, type: AttributeType, entityType: EntityType, isRequired: Boolean, options: List<String>)
    fun updateAttribute(id: Long, name: String, type: AttributeType, entityType: EntityType, isRequired: Boolean, options: List<String>)
    fun deleteAttribute(id: Long)
    fun reorderAttributes(fromIndex: Int, toIndex: Int)
}

// ItemEditViewModel 扩展
class ItemEditViewModel(...) : ViewModel() {
    // 新增
    val customAttributeValues: StateFlow<List<AttributeValueWithMeta>>
    
    fun loadCustomAttributes(itemId: Long?)
    fun saveCustomAttributeValues(itemId: Long)
    fun validateCustomAttributes(): Boolean
}
```

## 备份兼容

### BackupManager 扩展

**导出**:
```kotlin
data class BackupData(
    // 现有字段...
    val customAttributes: List<CustomAttribute>,
    val customAttributeOptions: List<CustomAttributeOption>,
    val customAttributeValues: List<CustomAttributeValue>
)
```

**导入**:
- 检测备份版本，若缺少新字段则初始化为空列表
- 先导入属性模板和选项，再导入属性值（保证外键约束）

## 皮肤系统适配

### 图标新增

为自定义属性管理页新增图标：

| IconKey | 描述 |
|---------|------|
| CustomAttribute | 属性管理入口图标 |

在 5 个皮肤中实现：
- DEFAULT: 标签/卡片图标
- GOTHIC: 铭牌/印记风格
- CHINESE: 印章风格
- CLASSIC: 卷轴/徽章风格
- NAVY: 漂流瓶/信件风格

### 组件复用

- 使用 `GradientTopAppBar` (compact = true)
- 使用 `LolitaCard` 包裹列表项
- 使用 `SkinClickable` 处理点击
- 使用 `SkinItemAppear` 列表项动画

## 实施步骤

1. 数据库层：新增 Entity、DAO、Migration
2. Repository 层：CustomAttributeRepository
3. AppModule 注册
4. 皮肤图标：新增 CustomAttribute 图标
5. UI 层：CustomAttributeManageScreen + Dialog
6. 导航：新增路由，设置页新增入口
7. ItemEditScreen/CoordinateEditScreen 集成
8. ItemDetailScreen/CoordinateDetailScreen 展示
9. 备份系统扩展
10. 版本号升级：2.20 → 2.21

## 测试要点

- [ ] 新增各类型属性模板
- [ ] 编辑属性模板（含选项增删改）
- [ ] 删除属性模板（CASCADE 删除值）
- [ ] 单品编辑页填写/保存属性值
- [ ] 穿搭编辑页填写/保存属性值
- [ ] 必填校验
- [ ] 详情页展示
- [ ] 备份/恢复兼容性
- [ ] 升级迁移