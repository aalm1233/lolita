# 皮肤系统设计文档

## 概述

为 Lolita App 新增皮肤系统，支持 4 套内置皮肤（默认粉色、哥特风、中华风、Classic Lolita），每套皮肤全面定制颜色、字体、形状、装饰符号，且均支持浅色/深色模式。

## 实现方案

采用 CompositionLocal 主题扩展方案：在现有 Material3 主题基础上，通过 `CompositionLocal` 注入自定义 `LolitaSkinConfig`，与 Material3 无缝集成。

## 数据模型

```kotlin
enum class SkinType { DEFAULT, GOTHIC, CHINESE, CLASSIC }

data class LolitaSkinConfig(
    val skinType: SkinType,
    val name: String,                    // 显示名称
    // 颜色
    val lightColorScheme: ColorScheme,
    val darkColorScheme: ColorScheme,
    val gradientColors: List<Color>,     // TopAppBar 渐变色（浅色）
    val gradientColorsDark: List<Color>, // TopAppBar 渐变色（深色）
    val accentColor: Color,              // 强调色（浅色）
    val accentColorDark: Color,          // 强调色（深色）
    val cardColor: Color,
    val cardColorDark: Color,
    // 字体
    val fontFamily: FontFamily,
    val typography: Typography,
    // 形状
    val cardShape: Shape,
    val buttonShape: Shape,
    // 装饰
    val topBarDecoration: String,        // TopAppBar 装饰符号
    val topBarDecorationAlpha: Float,
)
```

## CompositionLocal 提供

```kotlin
val LocalLolitaSkin = staticCompositionLocalOf { defaultSkinConfig() }

object LolitaSkin {
    val current: LolitaSkinConfig
        @Composable get() = LocalLolitaSkin.current
}
```

`LolitaTheme` 根据用户选择的 `SkinType` 注入对应配置：

```kotlin
@Composable
fun LolitaTheme(
    skinType: SkinType = SkinType.DEFAULT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val skin = getSkinConfig(skinType)
    val colorScheme = if (darkTheme) skin.darkColorScheme else skin.lightColorScheme
    CompositionLocalProvider(LocalLolitaSkin provides skin) {
        MaterialTheme(colorScheme = colorScheme, typography = skin.typography, content = content)
    }
}
```

## 四套皮肤定义

### 1. 默认粉色（保持现有）
- 颜色：Pink400 (#FF69B4) 体系不变
- 字体：系统默认字体
- 形状：圆角 16dp
- 装饰：✿

### 2. 哥特风 Gothic
- 浅色：主色深紫 #4A0E4E，背景浅灰 #F5F0F5，强调色血红 #8B0000
- 深色：主色亮紫 #9B59B6，背景近黑 #1A1A2E，强调色暗红 #C0392B
- 渐变：深紫→黑色
- 字体：内置哥特风格衬线体（如 Cinzel）
- 形状：圆角 8dp（锐利）
- 装饰：✝

### 3. 中华风 Chinese
- 浅色：主色朱红 #C41E3A，背景米白 #FFF8F0，强调色金色 #DAA520
- 深色：主色暗红 #8B2500，背景深褐 #2C1810，强调色暗金 #B8860B
- 渐变：朱红→金色
- 字体：内置书法/宋体风格字体（如思源宋体子集）
- 形状：圆角 4dp（方正端庄）
- 装饰：☁（云纹）

### 4. Classic Lolita
- 浅色：主色酒红 #722F37，背景奶油白 #FFFDD0，强调色棕色 #8B4513
- 深色：主色暗酒红 #5B2333，背景深棕 #2C1A0E，强调色深棕 #6B3410
- 渐变：酒红→棕色
- 字体：内置优雅衬线体（如 Playfair Display）
- 形状：圆角 12dp（柔和优雅）
- 装饰：♠（黑桃）

## 持久化

`AppPreferences` 新增：

```kotlin
val skinType: Flow<SkinType>  // 默认 SkinType.DEFAULT
suspend fun setSkinType(skinType: SkinType)
```

`MainActivity` 中收集并传入 `LolitaTheme`：

```kotlin
val skinType by appPreferences.skinType.collectAsState(initial = SkinType.DEFAULT)
LolitaTheme(skinType = skinType) { ... }
```

## 设置界面

- SettingsScreen "显示设置"下方新增"皮肤选择"入口
- 新增 `ThemeSelectScreen`：2列网格，每个皮肤一张预览卡片
- 卡片包含：皮肤名称、主色渐变预览条、装饰符号、字体预览文字
- 当前选中皮肤有高亮边框，点击即切换实时生效
- 导航新增 `Screen.ThemeSelect` 路由

## 现有组件改造

- **GradientTopAppBar**：渐变色、装饰符号、透明度改为从 LolitaSkin.current 读取
- **LolitaCard**：圆角形状改为从 LolitaSkin.current.cardShape 读取
- **LolitaNavHost 底部导航**：选中颜色、指示器颜色改为从 skin config 读取
- **全局**：搜索所有 Pink400/Pink300/PinkGradient 硬编码引用，替换为 skin config
- **SettingsScreen**：菜单项图标颜色保持不变（功能区分色，不属于皮肤）

## 字体文件

- 放置于 `app/src/main/res/font/`
- 3 个字体文件：哥特、中华、Classic 各一个
- 选择体积较小的字体子集控制 APK 增量
