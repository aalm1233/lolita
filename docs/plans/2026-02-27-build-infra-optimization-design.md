# 构建基础设施优化设计方案

日期: 2026-02-27
策略: 保守（仅构建工具链优化，依赖版本不变）

## 变更范围

### 1. Kapt → KSP 迁移
- 根 `build.gradle.kts`: 添加 KSP 插件 `2.1.0-1.0.29`
- `app/build.gradle.kts`: `kotlin.kapt` → `com.google.devtools.ksp`
- Room compiler 依赖: `kapt()` → `ksp()`
- Schema 导出配置: `kapt {}` → `ksp {}`

### 2. 启用 R8 代码缩减
- `isMinifyEnabled = true`
- `isShrinkResources = true`

### 3. 补全 ProGuard 规则
- Gson: 保留 BackupData + 所有 Entity 类字段名
- Apache POI: dontwarn + keep
- Kotlin Coroutines: dontwarn
- Enum 保护
- 调试堆栈信息保留
