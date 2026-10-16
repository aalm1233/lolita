# 个人模特集成回归测试断言修复

## 根因与修改

- `ImageTaskServiceTest` 原先查找旧的任务中心完整说明文案。当前任务中心说明增加了个人模特和重启计费提示，导致通知跳转成功后仍等待旧文案超时。测试改用目前仅在任务中心正文出现的稳定前缀「离开页面后继续处理。」，并继续验证通知打开任务中心、首次重建、切换个人页、再次重建，以及消费后的 `navigate_to` 不再重复跳转。
- `TryOnInfrastructureTest` 两处无效 PNG 检查仍捕获 `IllegalArgumentException`。`git show e363bc5:app/src/main/java/com/lolita/app/data/file/ImageFileHelper.kt` 表明基线的 `saveGeneratedImage` 已将校验异常转换为 `ImageGenerationFailure(INVALID_IMAGE)`。现在两处均捕获该精确异常并断言 `kind`，原有文件未新增、原图片未改变等断言保留。

## 现有引用保护证据

`PersonalModelPersistenceTest.draftReopenProtectsPhotosUntilExplicitDiscardAndTaskRetainsDeletedModelImage` 已用 `ImageFileHelper.configure(context, db)`、真实 `PersonalModelRepository` 和 `filesDir/images/` 中的图片，验证删除模特后已被任务引用的图片继续存在、任务删除后图片可清理。本次无需重构另一项执行测试来重复覆盖这一行为。

## 验证交接

- `git diff --check`：通过。
- `:app:compileDebugAndroidTestKotlin`：为避免与主任务的统一构建竞争，在进入 Android 测试源码编译前中止（退出码 130）；未据此声称编译或设备测试通过。
- 主任务统一执行构建和设备回归，核对上述三项原失败结果。
