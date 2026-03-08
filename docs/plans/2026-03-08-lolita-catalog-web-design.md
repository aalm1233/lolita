# Lolita 服饰图鉴管理网站设计文档

## 项目概述

为 Lolita 衣橱管理 Android 应用开发配套的 Web 管理系统，用于维护服饰数据库，支持管理员集中维护服饰信息，方便用户快速导入标准化数据。

## 核心需求

- **使用场景**：管理员维护服饰数据，用于支持后续 App 数据的导入和上传
- **权限模型**：仅管理员可以维护数据（无用户注册/登录系统）
- **数据范围**：服饰目录（包含价格、定金尾款日期），不包含个人化数据（付款状态、穿搭日志、存储位置等）
- **数据导入**：暂不考虑 App 侧集成方案，先完成 Web 端数据维护功能
- **技术栈**：React + Go + SQLite 轻量化集成
- **部署方式**：生产环境单文件打包（Go embed），开发环境前后端分离调试

## 技术架构

### 整体架构

**单体应用架构：**
- 前端：React 18 + TypeScript + Ant Design（打包后嵌入 Go 二进制）
- 后端：Go + Gin 框架 + GORM
- 数据库：SQLite（单文件数据库）
- 部署：单个可执行文件，包含前端资源、后端服务、数据库

**数据流：**
```
管理员浏览器 → React UI → REST API (Gin) → GORM → SQLite
```

**优势：**
- 整个应用打包成一个可执行文件（如 `lolita-catalog.exe`）
- 无需安装数据库，SQLite 文件与程序放在同一目录
- 部署极简：复制可执行文件 + 配置文件即可运行
- 跨平台：可编译 Windows/Linux/macOS 版本

### 技术选型

**后端：**
- Web 框架：Gin（轻量、高性能）
- ORM：GORM（支持 SQLite，自动迁移）
- 认证：JWT token（golang-jwt/jwt）
- 文件上传：本地文件系统存储（`./uploads/images/`）
- 配置管理：Viper（支持 YAML/JSON 配置文件）

**前端：**
- 框架：React 18 + TypeScript
- UI 组件库：Ant Design（完善的表格、表单、上传组件）
- 状态管理：React Query（服务端状态）+ Zustand（客户端状态）
- 路由：React Router v6
- HTTP 客户端：Axios
- 构建工具：Vite

## 数据模型设计

基于 Android App 的数据结构，简化为管理端需要的核心实体。

### 核心实体

**Item（服饰）**
- id: int64 (主键)
- name: string (服饰名称)
- description: string (描述)
- brandId: int64 (外键 → Brand)
- categoryId: int64 (外键 → Category)
- style: string (风格，如"甜美"、"哥特")
- season: string (季节，如"春夏"、"秋冬")
- colors: string (颜色，逗号分隔)
- size: string (尺码)
- sizeChartImageUrl: string (尺码表图片)
- imageUrls: string (JSON 数组，多张图片)
- source: string (来源/店铺)
- createdAt: timestamp
- updatedAt: timestamp

**Brand（品牌）**
- id: int64 (主键)
- name: string (品牌名称)
- isPreset: bool (是否预设数据)

**Category（分类）**
- id: int64 (主键)
- name: string (分类名称)
- group: string (分组：CLOTHING/ACCESSORY)
- isPreset: bool (是否预设数据)

**Style（风格）**
- id: int64 (主键)
- name: string (风格名称)
- isPreset: bool (是否预设数据)

**Season（季节）**
- id: int64 (主键)
- name: string (季节名称)
- isPreset: bool (是否预设数据)

**Price（价格）**
- id: int64 (主键)
- itemId: int64 (外键 → Item，CASCADE 删除)
- priceType: string (FULL=全款 / DEPOSIT_BALANCE=定金尾款)
- fullPrice: float64 (全款金额)
- depositAmount: float64 (定金金额)
- balanceAmount: float64 (尾款金额)
- depositDueDate: timestamp (定金截止日期)
- balanceDueDate: timestamp (尾款截止日期)

**Coordinate（套装）**
- id: int64 (主键)
- name: string (套装名称)
- description: string (描述)
- imageUrls: string (JSON 数组，套装图片)
- createdAt: timestamp
- updatedAt: timestamp

**CoordinateItem（套装-服饰关联）**
- coordinateId: int64 (外键 → Coordinate，CASCADE 删除)
- itemId: int64 (外键 → Item，CASCADE 删除)
- order: int (排序)
- 联合主键：(coordinateId, itemId)

### 不包含的实体（个人化数据）

- Payment（付款记录）
- OutfitLog（穿搭日志）
- Location（存储位置）
- Source（购买来源，仅作为 Item 的字符串字段保留）

### 字段说明

- `imageUrls` 存储为 JSON 数组字符串（与 Android Room 一致）
- `priceType` 枚举：FULL（全款）/ DEPOSIT_BALANCE（定金尾款）
- 不包含 `status`（已拥有/愿望单）和 `priority`（优先级）字段，这些是用户个人状态

## 功能模块设计

### 前端页面结构

**1. 登录页**
- 简单的管理员密码认证（JWT token）
- 记住登录状态（localStorage）

**2. 服饰管理页**
- 列表视图：表格展示，支持分页、排序
- 搜索栏：文本搜索（名称/描述）+ 多条件筛选（品牌/分类/风格/季节/颜色）
- 操作按钮：新增、编辑、删除、批量导入（CSV/Excel）
- 图片上传：支持多图上传，拖拽排序
- 表格列：缩略图、名称、品牌、分类、风格、季节、价格、操作

**3. 预设数据管理页**
- 四个标签页：品牌、分类、风格、季节
- 简单的增删改查表格
- 支持标记为预设数据（isPreset）

**4. 套装管理页**
- 套装列表：卡片视图，显示套装名称和缩略图
- 套装编辑：选择服饰关联，设置顺序
- 拖拽排序服饰顺序

**5. 数据导出页**
- 导出格式选择：JSON（兼容 App BackupManager）/ CSV
- 导出范围选择：全部数据 / 按条件筛选
- 下载按钮

**6. 统计面板**
- 服饰总数、品牌分布、分类分布
- 价格统计：总价值、平均价格、价格区间分布
- 图表展示（Ant Design Charts）

### 后端 API 设计

**认证相关：**
```
POST   /api/auth/login          # 管理员登录
```

**服饰管理：**
```
GET    /api/items               # 获取服饰列表（支持分页、筛选、搜索）
POST   /api/items               # 创建服饰
GET    /api/items/:id           # 获取服饰详情
PUT    /api/items/:id           # 更新服饰
DELETE /api/items/:id           # 删除服饰
POST   /api/items/import        # 批量导入（CSV/Excel）
GET    /api/items/export        # 导出数据（JSON/CSV）
```

**预设数据管理：**
```
GET    /api/brands              # 品牌列表
POST   /api/brands              # 创建品牌
PUT    /api/brands/:id          # 更新品牌
DELETE /api/brands/:id          # 删除品牌

# 同理：/api/categories, /api/styles, /api/seasons
```

**套装管理：**
```
GET    /api/coordinates         # 套装列表
POST   /api/coordinates         # 创建套装
GET    /api/coordinates/:id     # 套装详情（含关联服饰）
PUT    /api/coordinates/:id     # 更新套装
DELETE /api/coordinates/:id     # 删除套装
```

**统计数据：**
```
GET    /api/stats               # 统计数据
```

**文件上传：**
```
POST   /api/upload              # 图片上传
```

### API 响应格式

**成功响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

**错误响应：**
```json
{
  "code": 400,
  "message": "品牌名称不能为空",
  "data": null
}
```

## 项目结构

```
lolita-catalog/
├── backend/
│   ├── cmd/
│   │   └── server/
│   │       └── main.go           # 入口文件
│   ├── internal/
│   │   ├── api/                  # API handlers
│   │   │   ├── auth.go
│   │   │   ├── item.go
│   │   │   ├── brand.go
│   │   │   ├── coordinate.go
│   │   │   └── stats.go
│   │   ├── model/                # GORM models
│   │   │   ├── item.go
│   │   │   ├── brand.go
│   │   │   ├── price.go
│   │   │   └── coordinate.go
│   │   ├── repository/           # 数据访问层
│   │   ├── service/              # 业务逻辑层
│   │   ├── middleware/           # JWT 认证等中间件
│   │   │   ├── auth.go
│   │   │   ├── cors.go
│   │   │   └── error.go
│   │   └── config/               # 配置加载
│   │       └── config.go
│   ├── pkg/
│   │   └── utils/                # 工具函数
│   │       ├── jwt.go
│   │       ├── password.go
│   │       └── file.go
│   ├── web/                      # 前端打包后的静态文件（embed）
│   ├── go.mod
│   └── go.sum
├── frontend/
│   ├── src/
│   │   ├── api/                  # API 调用封装
│   │   │   ├── auth.ts
│   │   │   ├── item.ts
│   │   │   └── coordinate.ts
│   │   ├── components/           # 通用组件
│   │   │   ├── Layout.tsx
│   │   │   ├── ImageUpload.tsx
│   │   │   └── FilterBar.tsx
│   │   ├── pages/                # 页面组件
│   │   │   ├── Login.tsx
│   │   │   ├── ItemList.tsx
│   │   │   ├── ItemEdit.tsx
│   │   │   ├── PresetManage.tsx
│   │   │   ├── CoordinateList.tsx
│   │   │   ├── Export.tsx
│   │   │   └── Stats.tsx
│   │   ├── hooks/                # 自定义 hooks
│   │   │   └── useAuth.ts
│   │   ├── types/                # TypeScript 类型定义
│   │   │   └── models.ts
│   │   ├── utils/                # 工具函数
│   │   │   └── request.ts
│   │   ├── App.tsx
│   │   └── main.tsx
│   ├── package.json
│   ├── tsconfig.json
│   └── vite.config.ts
├── config.yaml                   # 配置文件（端口、数据库路径、管理员密码等）
├── lolita.db                     # SQLite 数据库文件
└── README.md
```

## 开发与部署

### 开发模式

**前端开发：**
```bash
cd frontend
npm install
npm run dev  # Vite dev server，端口 5173
```

**后端开发：**
```bash
cd backend
go mod download
go run cmd/server/main.go  # Gin server，端口 8080，CORS 允许 localhost:5173
```

### 生产构建

```bash
# 1. 构建前端
cd frontend
npm run build  # 输出到 backend/web/

# 2. 构建后端（embed 前端资源）
cd backend
go build -o lolita-catalog cmd/server/main.go

# 3. 分发
# - lolita-catalog（可执行文件）
# - config.yaml（配置文件）
# - lolita.db（首次运行自动创建）
# - uploads/（图片目录，首次运行自动创建）
```

### 配置文件示例

**config.yaml：**
```yaml
server:
  port: 8080
  mode: release  # debug / release

database:
  path: ./lolita.db

auth:
  jwt_secret: your-secret-key-change-me
  admin_password_hash: $2a$10$...  # bcrypt hash

upload:
  max_size: 10485760  # 10MB
  allowed_types:
    - jpg
    - jpeg
    - png
    - webp
  path: ./uploads/images/
```

## 关键实现细节

### 1. Go embed 静态资源

```go
package main

import (
    "embed"
    "net/http"
    "github.com/gin-gonic/gin"
)

//go:embed web/*
var webFS embed.FS

func main() {
    router := gin.Default()

    // API routes
    api := router.Group("/api")
    {
        // ... API handlers
    }

    // Serve static files
    router.StaticFS("/", http.FS(webFS))

    router.Run(":8080")
}
```

### 2. GORM 自动迁移

```go
func initDatabase(dbPath string) (*gorm.DB, error) {
    db, err := gorm.Open(sqlite.Open(dbPath), &gorm.Config{})
    if err != nil {
        return nil, err
    }

    // 自动迁移
    err = db.AutoMigrate(
        &model.Item{},
        &model.Brand{},
        &model.Category{},
        &model.Style{},
        &model.Season{},
        &model.Price{},
        &model.Coordinate{},
        &model.CoordinateItem{},
    )

    return db, err
}
```

### 3. 图片上传处理

```go
func uploadImage(c *gin.Context) {
    file, err := c.FormFile("file")
    if err != nil {
        c.JSON(400, gin.H{"code": 400, "message": "文件上传失败"})
        return
    }

    // 生成 UUID 文件名
    filename := uuid.New().String() + filepath.Ext(file.Filename)
    savePath := filepath.Join("./uploads/images/", filename)

    // 保存文件
    if err := c.SaveUploadedFile(file, savePath); err != nil {
        c.JSON(500, gin.H{"code": 500, "message": "文件保存失败"})
        return
    }

    // 返回相对路径
    c.JSON(200, gin.H{
        "code": 200,
        "message": "success",
        "data": gin.H{"url": "/uploads/images/" + filename},
    })
}
```

### 4. JSON 导出兼容性

导出格式与 Android App 的 `BackupManager` 保持一致：

```json
{
  "version": 1,
  "exportTime": "2026-03-08T10:00:00Z",
  "items": [...],
  "brands": [...],
  "categories": [...],
  "styles": [...],
  "seasons": [...],
  "prices": [...],
  "coordinates": [...],
  "coordinateItems": [...]
}
```

## 安全与错误处理

### 认证与授权

- JWT token 认证，存储在 localStorage
- 请求头携带 `Authorization: Bearer <token>`
- Token 过期时间：7 天
- 密码使用 bcrypt 哈希存储

### 文件上传安全

- 文件类型白名单：jpg, jpeg, png, webp
- 文件大小限制：单个文件 10MB
- 文件名随机化（UUID），防止路径遍历
- 存储路径隔离：`./uploads/images/`

### SQL 注入防护

- GORM 参数化查询，自动防护 SQL 注入

### CORS 配置

- 开发环境：允许 `localhost:5173`
- 生产环境：仅允许同源请求

### 错误处理

- 统一错误响应格式
- 后端使用中间件捕获 panic，返回 500 错误
- 前端使用 Ant Design 的 message 组件显示错误提示

### 数据备份

- SQLite 数据库文件定期备份（手动或脚本）
- 图片文件夹定期备份
- 提供数据导出功能作为额外备份手段

## 未来扩展方向

1. **App 侧集成**：
   - 开发 REST API 供 App 调用
   - App 内浏览和导入服饰数据
   - 用户上传自己的服饰数据到公共库

2. **用户系统**：
   - 支持多用户注册/登录
   - 用户贡献数据，管理员审核

3. **数据同步**：
   - App 与 Web 端数据双向同步
   - 增量更新机制

4. **高级功能**：
   - 服饰推荐算法
   - 搭配建议
   - 价格趋势分析

## 总结

本设计采用 React + Go + SQLite 轻量化技术栈，实现了一个简单高效的 Lolita 服饰图鉴管理系统。核心特点：

- **轻量部署**：单文件可执行程序，无需复杂环境配置
- **功能完整**：涵盖服饰管理、预设数据、套装管理、数据导出、统计面板
- **安全可靠**：JWT 认证、文件上传安全、数据备份机制
- **易于扩展**：清晰的分层架构，便于后续添加新功能

该系统为 Android App 提供了强大的数据维护后台，为用户快速导入标准化服饰数据奠定了基础。
