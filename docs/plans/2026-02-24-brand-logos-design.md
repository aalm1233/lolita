# 品牌商标实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 201 个内置品牌从淘宝获取店铺头像，打包为 APK assets，更新预置数据代码填充 logoUrl。

**Architecture:** 图片存放在 `assets/brand_logos/`，通过 `file:///android_asset/` URI 被 Coil AsyncImage 加载。`BrandRepository.ensurePresetBrands()` 在启动时为 logoUrl 为空的预置品牌填充 asset 路径。`LolitaDatabase.DatabaseCallback.onCreate()` 在新安装时直接写入 logoUrl。

**Tech Stack:** Kotlin, Room, Coil, Android Assets

---

### Task 1: 搜索并下载品牌淘宝店铺头像

**目标：** 对 201 个品牌逐一搜索淘宝店铺，下载头像图片到 assets 目录。

**Step 1: 创建 assets 目录**

```bash
mkdir -p app/src/main/assets/brand_logos
```

**Step 2: 逐批搜索品牌淘宝店铺**

对每个品牌：
1. 在淘宝搜索店铺名（用品牌中文名或英文名）
2. 找到官方店铺页面
3. 提取店铺头像图片 URL（通常是 `img.alicdn.com` 域名）
4. 下载图片到 `app/src/main/assets/brand_logos/{品牌名}.webp`

优先处理有实际淘宝店铺的中国独立品牌（前70个左右），然后是日牌（有淘宝代购店的），最后是其余品牌。找不到的跳过。

**Step 3: Commit 下载的图片**

```bash
git add app/src/main/assets/brand_logos/
git commit -m "assets: add brand logo images from Taobao shop avatars"
```

---

### Task 2: 创建品牌 Logo 映射表

**Files:**
- Create: `app/src/main/java/com/lolita/app/data/BrandLogoMapping.kt`

**Step 1: 创建映射对象**

根据 Task 1 实际下载到的图片文件，创建品牌名→asset 路径的映射：

```kotlin
package com.lolita.app.data

object BrandLogoMapping {
    private const val PREFIX = "file:///android_asset/brand_logos/"

    /** 品牌名 → asset logo 路径，仅包含有 logo 文件的品牌 */
    val logoMap: Map<String, String> = mapOf(
        "古典玩偶（Classical Puppets）" to "${PREFIX}古典玩偶.webp",
        "仲夏物语（Elpress L）" to "${PREFIX}仲夏物语.webp",
        // ... 所有实际下载到图片的品牌
    )

    fun getLogoUrl(brandName: String): String? = logoMap[brandName]
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/BrandLogoMapping.kt
git commit -m "feat: add brand logo asset mapping"
```

---
### Task 3: 更新 BrandDao 添加批量更新 logoUrl 方法

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/local/dao/BrandDao.kt`

**Step 1: 添加按名称更新 logoUrl 的方法**

在 `BrandDao` 接口中添加：

```kotlin
@Query("UPDATE brands SET logo_url = :logoUrl WHERE name = :name AND is_preset = 1 AND logo_url IS NULL")
suspend fun updatePresetBrandLogo(name: String, logoUrl: String)
```

这个方法只更新 `isPreset=true` 且 `logoUrl` 为空的品牌，不会覆盖用户手动设置的 logo。

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/local/dao/BrandDao.kt
git commit -m "feat: add DAO method to update preset brand logo"
```

---

### Task 4: 更新 BrandRepository.ensurePresetBrands()

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/repository/BrandRepository.kt`

**Step 1: 在 ensurePresetBrands 中填充 logoUrl**

在现有的 `presetNames.forEach` 循环之后，添加 logo 填充逻辑：

```kotlin
// 为预置品牌填充 logo（不覆盖用户手动设置的）
BrandLogoMapping.logoMap.forEach { (name, logoUrl) ->
    brandDao.updatePresetBrandLogo(name, logoUrl)
}
```

同时更新新品牌插入逻辑，创建时就带上 logoUrl：

```kotlin
presetNames.forEach { name ->
    if (brandDao.getBrandByName(name) == null) {
        brandDao.insertBrand(Brand(
            name = name,
            isPreset = true,
            logoUrl = BrandLogoMapping.getLogoUrl(name)
        ))
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/repository/BrandRepository.kt
git commit -m "feat: populate preset brand logos on startup"
```

---

### Task 5: 更新 LolitaDatabase.DatabaseCallback.onCreate()

**Files:**
- Modify: `app/src/main/java/com/lolita/app/data/local/LolitaDatabase.kt` (lines 443-648)

**Step 1: 修改 INSERT SQL 包含 logo_url**

将现有的品牌插入 SQL 从：
```sql
INSERT OR IGNORE INTO brands (name, is_preset, created_at) VALUES ('...', 1, $now)
```
改为：
```sql
INSERT OR IGNORE INTO brands (name, is_preset, created_at, logo_url) VALUES ('...', 1, $now, '...')
```

使用 `BrandLogoMapping.getLogoUrl(name)` 获取对应的 logo 路径，没有的传 NULL。

**Step 2: Commit**

```bash
git add app/src/main/java/com/lolita/app/data/local/LolitaDatabase.kt
git commit -m "feat: include brand logos in database creation"
```

---

### Task 6: 版本号更新 + Release 构建

**Files:**
- Modify: `app/build.gradle.kts`

**Step 1: 更新版本号**

按 CLAUDE.md 规则，新功能 bump minor version：
- versionCode: 23 → 24（根据当前实际值 +1）
- versionName: 更新为下一个 minor 版本

**Step 2: Release 构建**

```bash
./gradlew.bat assembleRelease
```

**Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version for brand logos feature"
```
