# 我的Lolita 👗

一款专为 Lolita 时尚爱好者打造的 Android 衣橱管理应用，帮助你轻松管理服饰、搭配穿搭、追踪消费。

## 功能特性

### 服饰管理
- 列表 / 网格 / 画廊三种视图切换（1/2/3 列）
- 多条件筛选：季节、风格、颜色、品牌、分类（服装 / 配饰）、位置
- 文本搜索、状态追踪（已拥有 / 愿望单 / 待付尾款）
- 价格展示（全款 / 定金-尾款模式）与总消费统计
- 品牌 Logo 展示，滑动删除（RESTRICT 约束保护）

### 套装搭配
- 命名套装组合，预览缩略图
- 关联多件服饰，支持编辑与删除

### 图鉴
- 独立图鉴库，收录服饰参考信息与图片

### 穿搭日志
- 基于照片的每日穿搭记录，支持多图
- 日期追踪，关联服饰单品（多对多）
- 快速穿搭记录入口，主屏幕桌面小部件（Glance）

### 统计面板（5 页签）
- **总览** — 已拥有 / 愿望单 / 套装 / 穿搭数量，消费概览，最贵单品，品牌 Top 5
- **付款年历** — 按月卡片展示付款日程
- **消费分布** — 按月份 / 品牌 / 分类 / 季节 / 风格维度统计
- **消费趋势** — 月度 / 季度消费走势图
- **愿望单分析** — 愿望单优先级分布与消费预估

### 智能推荐
- 基于余弦相似度的搭配推荐引擎
- 风格 / 颜色 / 季节向量 + 穿搭历史共现加成

### 皮肤系统
- 7 套完整皮肤：甜美粉、哥特暗黑、中华风韵、经典优雅、清风水手、田园牧歌、维多利亚
- 每套皮肤独立配色、字体、形状、图标（45+ Canvas 手绘图标）、动画（入场 / 转场 / 涟漪 / 粒子）
- 深色模式自适应

### 设置与数据
- 品牌 / 分类 / 风格 / 季节 / 位置 / 购买渠道管理（预设 + 自定义）
- 数据备份与恢复（JSON / CSV），导入预览
- 淘宝订单导入（Excel .xlsx 解析，Apache POI）
- 付款提醒（AlarmManager 精确闹钟）+ 日历事件同步
- 每日穿搭提醒
- 开机自动恢复提醒调度

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 2.1.0 |
| UI | Jetpack Compose (BOM 2024.12.01) |
| 架构 | MVVM + Repository，手动 DI (AppModule) |
| 数据库 | Room 2.7.0 (版本 19，23 实体) |
| 导航 | Navigation Compose 2.8.5 |
| 图片加载 | Coil 2.7.0 + Landscapist 2.4.7 (shimmer / circular reveal) |
| 模糊效果 | Haze 1.6.9 (毛玻璃顶栏 / 底栏 / 对话框) |
| 共享元素过渡 | Compose Animation 1.7.x (Hero Shared Element) |
| 缩放手势 | Telephoto 0.14.0 |
| Excel 解析 | Apache POI 5.2.5 |
| 序列化 | Gson 2.11.0 |
| 偏好存储 | DataStore Preferences 1.1.1 |
| 桌面小部件 | Glance 1.1.1 |
| 骨架屏 | compose-shimmer 1.3.2 |
| 协程 | Kotlin Coroutines 1.9.0 |

- Compile SDK 35 / Min SDK 26 / Target SDK 35
- JVM Target 17

## 构建

```bash
# Debug 构建
./gradlew.bat assembleDebug

# Release 构建（需先 clean）
./gradlew.bat clean assembleRelease
```

Release 签名需要 `local.properties` 配置 `STORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`，密钥库为项目根目录的 `lolita-release.jks`。
输出：`app/build/outputs/apk/release/app-release.apk`

## 权限说明

| 权限 | 用途 |
|------|------|
| READ_MEDIA_IMAGES | 读取图片（SDK ≤32 使用 READ/WRITE_EXTERNAL_STORAGE） |
| CAMERA | 拍照添加服饰图片 |
| SCHEDULE_EXACT_ALARM | 付款提醒闹钟 |
| POST_NOTIFICATIONS | 推送提醒通知 |
| READ/WRITE_CALENDAR | 同步付款日期到日历 |
| RECEIVE_BOOT_COMPLETED | 开机后重新调度提醒 |
| INTERNET | 网络图片下载 |

## 项目结构

```
app/src/main/java/com/lolita/app/
├── data/
│   ├── local/        # Room 实体、DAO、数据库、TypeConverter
│   ├── notification/ # 提醒调度、日历同步、广播接收器
│   ├── preferences/  # DataStore 偏好设置
│   └── repository/   # 数据仓库层
├── di/               # AppModule 手动依赖注入
├── domain/
│   ├── model/        # 领域模型
│   └── usecase/      # 业务逻辑（MatchingEngine）
├── ui/
│   ├── component/    # 通用 UI 组件
│   ├── navigation/   # 导航图与路由定义
│   ├── screen/
│   │   ├── calendar/ # 付款年历
│   │   ├── catalog/  # 图鉴
│   │   ├── common/   # 共享组件（LolitaCard、ImageFrame、Shimmer 等）
│   │   ├── coordinate/ # 套装
│   │   ├── item/     # 服饰列表、详情、编辑
│   │   ├── location/ # 位置管理
│   │   ├── outfitlog/ # 穿搭日志
│   │   ├── price/    # 价格与付款管理
│   │   ├── recommend/ # 智能推荐
│   │   ├── settings/ # 设置页
│   │   ├── source/   # 购买渠道
│   │   └── stats/    # 统计面板
│   └── theme/
│       └── skin/     # 7 套皮肤（颜色、字体、图标、动画、粒子）
├── util/             # 工具类（图片、备份、淘宝解析等）
└── LolitaApplication.kt
```

## License

Private project. All rights reserved.
