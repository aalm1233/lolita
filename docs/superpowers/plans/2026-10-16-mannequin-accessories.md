# 无杆人台与小物展示 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans inline. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 裙装人台无杆/底座，配饰生成单独展示图，保持任务、原图与备份兼容。
**Architecture:** 复用MANNEQUIN任务与单Item确认追加。v3请求追加不可变MannequinSubject，旧v1/v2结构不变；媒体准备和专用API按展示类型选择1张或2张输入。
**Tech Stack:** Kotlin 2.1.0、Compose/Material3、Room29、Gson、StateFlow；无新增依赖。
**Spec:** docs/superpowers/specs/2026-10-16-mannequin-accessories-design.md

## Global Constraints

- 中文UI、七皮肤、现有Screen/NavHost；不新增Room列/实体或备份格式号（仍9）。
- ACCESSORY含自定义及独立/挂载记录，CLOTHING仍仅明确裙类。保存只追加原Item图片，不改封面。
- 新任务严格v3；旧v1/v2不多写字段、不默认接受陌生枚举。API旧generate仍2..9，个人模特仍1..5，新增展示图1..2。
- 不做付费API试验，不清用户设备数据。Gradle与设备验证由root串行；工作区既存未跟踪文件不触碰。

## Review Focus

1. 类别/图片在确认、排队或解码期间改变，不得以另一展示类型发送付费请求（任务1、2）。
2. ACCESSORY任意中文/自定义类别与parentId不影响入口；CLOTHING非裙仍拒绝（任务1、3）。
3. v2个人模特请求encode不能因新增null字段而破坏旧严格解析（任务1）。
4. 旧裙装待确认任务与新小物任务JSON/ZIP往返后仍可幂等保存（任务1、3）。
5. 模板无杆不能通过裁切裙摆实现；小物不上传人台、不混用模板索引或补造裙装（任务2、3）。

### Task 1: 分类与持久展示快照

**Files:** domain/model/MannequinRequest.kt、domain/model/ImageTask.kt、data/generation/ImageTaskCoordinator.kt、ImageTaskRunner.kt；MannequinRequestTest、ImageTaskTest、ImageTaskBackupFormatTest、ImageTaskExecutionTest。
**Interfaces:** 新增`enum class MannequinPresentation { DRESS_FORM, ACCESSORY }`，`MannequinSubject(categoryId: Long, categoryName: String, presentation: MannequinPresentation)`，`mannequinSubject(category: Category?): MannequinSubject?`。MannequinRequest追加`subject: MannequinSubject? = null`，null代表旧裙装。ImageTaskRequest追加`mannequinSubject: MannequinSubject? = null`，新MANNEQUIN v3；Runner传入快照。

- [ ] 先补准入、v1/v2原样兼容、v3严格枚举/跨类型拒绝、快照持久往返测试；运行定向JVM观察RED。
- [ ] 实现准入/提示词分支、v3显式字段解析与v2编码移除新字段；Coordinator事务中核对当前类别与页面快照，Runner重建同一subject。仅MANNEQUIN可用v3。
- [ ] 定向JVM GREEN；真实Room验证快照与分类改动拒绝，旧预览仍可确认。提交`feat: persist mannequin presentation snapshots`。

### Task 2: 无杆模板、图像准备与服务边界

**Files:** assets/mannequin/front-template.png、data/tryon/TryOnImages.kt、data/mannequin/MannequinGenerator.kt、data/remote/{ImageGenerationApi,TryOnImageApi,SeedreamImageApi}.kt、di/AppModule.kt；MannequinGeneratorTest、MannequinInfrastructureTest、两家API测试。
**Interfaces:** `TryOnImages.prepareAccessory(paths: List<String>)`只归一化1张原图；`ImageGenerationApi.generateMannequin(credentials, images, prompt)`允许1..2；两provider提供对应方法。MannequinGenerator追加可选`prepareAccessoryImages`注入，null subject走DRESS_FORM，两次校验subject与最新类别相符。Executor追加可选accessory准备/专用生成注入，原构造兼容。

- [ ] 先补真实Generator输入顺序/数量、分类在准备时改变阻止发送、配饰去人物提取提示、API专用1..2与旧2..9拒绝测试，运行观察RED。
- [ ] 内置imagegen编辑原模板仅去杆/底座，目视确认完整躯干/原构图；保留完整提示词和SHA来源记录。
- [ ] 实现单图准备、两provider专用入口、AppModule/Executor接线；原安全/取消/保存生命周期不改。
- [ ] 定向JVM及独立AVD图片准备GREEN；提交`feat: generate standalone accessory images without mannequin stands`。

### Task 3: 详情、生成与任务确认页面

**Files:** ui/screen/item/ItemDetailScreen.kt、ui/screen/mannequin/{MannequinViewModel,MannequinScreen}.kt、ui/screen/tasks/{ImageTaskScreen,ImageTaskDetailScreen,ImageTaskViewModel}.kt；MannequinDetailEntryTest、MannequinViewModelTest、MannequinScreenTest、ImageTaskBackupTest。
**Interfaces:** UiState从当前category派生subject、title与上传张数；提交时传完整subject。配饰界面不显示空白人台图；上传确认只列1张原图。任务详情使用“添加到原物品图片”。

- [ ] 先补独立/挂载配饰入口、有图/无图、裙装2图与小物1图确认、分类变化与旧重试回归，在独立AVD观察RED。
- [ ] 复用现有皮肤组件调整文案/模板区和确认输入；展示类型变更后重新核对，不沿用旧确认。
- [ ] 新小物待确认预览JSON/ZIP往返/恢复后幂等追加，旧裙装/个人模特回归；浅暗七皮肤320dp大字截图核对。提交`feat: expose accessory display generation in item details`。

### Task 4: 集成、审查与发布

**Files:** AGENTS.md、app/build.gradle.kts、功能说明/来源/发布回执。

- [ ] 全量`./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest`通过；定向相关独立AVD回归，无付费云调用。
- [ ] 全分支fresh gpt-6-astra审查（spec/plan/ledger/Review Focus）；对实质问题一次RED→GREEN修复并全量JVM，不把图像质量当作通过。
- [ ] 依据未占用版本递增（预期2.54.0/113）、更新AGENTS和发布说明，提交源码并clean assembleRelease核验原签名；独立AVD从2.53.0覆盖升级，逐字段/图片/设置保全。
- [ ] 集成master普通推送与源码标签，GitHub/Gitee同一APK和校验文件匿名下载/HEAD核验。若Gitee配额仍不足，先备份下一候选并精确请求授权，不擅自清其他APK；公开列表403如实报告。
- [ ] 保存证据，提交推送回执；归档本次worktree及本计划scratch，保留原工作区未跟踪内容。

## Self-review

所有用户要求分配到任务1–3；五项Review Focus均有归属。subject由任务1定义，任务2/3消费相同类型；v3只供MANNEQUIN，v2个人模特保持原字段集合。任务2的单图边界不影响试衣和个人模特。root执行，独立调查/最终审查可并行，所有Gradle/设备命令由root串行。
