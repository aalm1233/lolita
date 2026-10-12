# 内置店铺图标与房间 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 为测试数据涉及的店铺提供可核验的内置真实图标，添加六个默认房间与生成图片。
**Architecture:** 打包素材与精确别名清单，内置资料协调器一次性事务初始化，现有实体字段保存UUID本地图片。AppModule提供服务，Application启动；BackupManager等待初始化屏障。
**Tech Stack:** Kotlin2.1、Room/KSP、Compose/Material3、Gson、内置imagegen。
**Spec:** docs/superpowers/specs/2026-10-12-brand-room-assets-design.md

## Global Constraints

- 数据库23、备份格式5不变；图片保存至filesDir/images中的UUID文件。
- 六房间为卧室、客厅、衣帽间、书房、玄关、储物间，默认图片可更换/移除，删除或改名后不得复活。
- 品牌仅按已核验的精确名称或明确别名匹配；保留所有用户已有图片与现有记录，禁止品牌合并。
- 恢复准确还原源备份；初始化必须先于备份操作完成，恢复后不追加默认数据。
- 只修改本轮文件，不碰用户模拟器及其他会话改动，不自动发布。

## Review Focus

- 空字符串logo与null同属缺图；空白处理不得影响用户图片。
- 用户删除默认房间、移除图片或改名后，重启和再次初始化不能补回。
- 相似店铺名/衣柜名不能误匹配或归并，已存在图片不能覆盖。
- 复制图片或数据库事务失败时无残余文件与完成标记；取消必须传播。
- 恢复刚结束、启动初始化仍运行时不能发生迟到追加；ZIP须包含所有持久默认图片。

### Task1: 官方图标与生成房间素材（控制代理）

**Files:** app/src/main/assets/builtin/catalog.json、builtin/brands/*、builtin/rooms/*、docs/builtin-assets-2026-10-12.md。
**Produces:** manifest formatVersion=1；rooms[{name,description,assetPath}]；brands[{key,names,assetPath}]。所有assetPath相对于Android assets根目录，未经核验的logo不进入brands条目。文档单列未找到的店铺。

- [x] 从测试ZIP和Excel实际关联名取清单，官方源逐项核验，原图下载并查看、记录SHA256。
- [x] 分别生成六张房间图片，复制最终图到工程；记录六份提示词，查看实际生成图。
- [x] 写清单并核对别名唯一、图片存在、内容可识别；更新AGENTS资料资源约定。

### Task2: 一次性初始化与新品牌默认图（实现代理）

**Files:** create data/file/BuiltInLibrary.kt、androidTest/data/BuiltInLibraryTest.kt及独立test assets；modify ImageFileHelper.kt、BrandRepository.kt、AppModule.kt、LolitaApplication.kt、BackupManager.kt。
**Consumes:** Task1资产清单。素材生成期间可编写/验证独立test资产，不改控制代理的正式清单。
**Produces:** BuiltInLibrary(context:Context,database:LolitaDatabase).initialize():Unit、withDefaultBrandLogo(brand:Brand):Brand；可自行选择清晰的等效命名，写报告供控制代理集成。

- [x] 先写真实内存Room/隔离Context回归，确认缺实现时失败；构建只在独立工作树，设备定向运行由控制代理安排。
- [x] 新增ImageFileHelper.copyAssetToInternalStorage(context,assetPath):String，UUID文件且失败清理。
- [x] 实现Gson清单解析、一次性初始化锁与Room事务、空图补充及成功后本机标记。保留现有所有字段；新房间排序追加，时间仅用于新增记录。
- [x] 新品牌插入在无自选logo时准备匹配图；失败清理新文件，编辑不自动补图；AppModule接线，Application后台初始化。
- [x] BackupManager增加可选初始化屏障（既有构造/测试兼容），serialized开始前await；确保不在恢复后追加。
- [x] 验证初始化、保全、删除/改名/移除、精确匹配、失败/取消、ZIP恢复等Review Focus用例；编译和JVM全套测试；记录证据并本地提交本任务源代码。

### Task3: 集成与真实Android验收（控制代理）

**Files:** docs/builtin-assets-2026-10-12.md、AGENTS.md、docs/visuals/2026-10-12-builtin-*。

- [x] 运行assembleDebug、testDebugUnitTest、assembleDebugAndroidTest，确认所有资产能解码。
- [x] 用专属AVD/同签名包运行BuiltInLibraryTest及必要备份测试，验证Test ZIP源记录/图片保留。
- [x] 实拍六房间列表、详情和17店铺商标管理页，独立审阅生成图与代码。
- [x] 完成检查后同步仅本轮文件至主库，保留其他会话原始字节；交付图片与核验结果。
