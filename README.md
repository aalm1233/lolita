# 我的Lolita 👗

一款专为 Lolita 时尚爱好者打造的 Android 衣橱管理应用，帮助你轻松管理服饰、搭配穿搭、追踪消费。

## 功能特性

### 服饰管理
- 支持列表/网格视图切换（1/2/3列）
- 多条件筛选：季节、风格、颜色、品牌、分类（服装/配饰）
- 文本搜索、状态追踪（已拥有/愿望单）
- 价格展示与总消费统计

### 愿望单
- 独立的愿望单页面
- 优先级标记（高/中/低），颜色区分

### 穿搭日志
- 基于照片的穿搭记录
- 日期追踪，支持编辑与删除

### 套装搭配
- 命名套装组合，预览缩略图
- 关联服饰管理

### 统计面板
- 已拥有数量、愿望单数量、套装数量、穿搭日志数量
- 总消费金额展示（可选）

### 设置与数据
- 品牌/分类/风格/季节管理（预设 + 自定义）
- 数据备份与恢复（JSON / CSV）
- 淘宝订单导入（Excel 文件解析）
- 付款提醒与日历事件同步

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 2.1.0 |
| UI | Jetpack Compose (BOM 2024.12.01) |
| 架构 | MVVM + Repository，手动 DI (AppModule) |
| 数据库 | Room 2.7.0 |
| 导航 | Navigation Compose 2.8.5 |
| 图片加载 | Coil 2.7.0 |
| Excel 解析 | Apache POI 5.2.5 |
| 偏好存储 | DataStore Preferences 1.1.1 |

- Compile SDK 35 / Min SDK 26 / Target SDK 35
- JVM Target 17

## 构建

```bash
# Debug 构建
./gradlew.bat assembleDebug

# Release 构建
./gradlew.bat assembleRelease

# 清理后构建
./gradlew.bat clean assembleDebug
```

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
├── data/           # Room 实体、DAO、数据库、仓库
├── di/             # AppModule 手动依赖注入
├── ui/
│   ├── screens/    # 各功能页面 Composable
│   ├── components/ # 通用 UI 组件
│   ├── navigation/ # 导航图与路由定义
│   └── theme/      # 主题、颜色、字体
├── util/           # 工具类（图片、日历、备份等）
└── LolitaApplication.kt
```

## License

Private project. All rights reserved.
