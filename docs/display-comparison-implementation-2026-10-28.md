# 展示图同屏对照实施

采用[前置方案](plans/2026-10-28-display-comparison.md)。2.71.1／147，仅调整批量展示图和相应任务详情的本地核对体验。

## 最终界面

新批次核对和已保存阶段一件一行，原图／结果同卡等大完整Fit；点击图片或“放大对比”默认并排，原图／生成图单图切换保留缩放与相对平移，提供缩小、放大、还原和裁切前完整原图。两图共享状态但不做像素配准。放大视图按2048像素请求解码，避免只把窄列缩略图放大；完整原图继续使用原全屏查看组件。当前模式与缩放可恢复。

`DisplayImageComparison`取冻结任务最后一张实际商品输入，不取固定人台模板、不查询后来变更的相册封面。未返回／已移除结果不以原图顶替；已保存结果仍受当前相册存在校验。任务详情复用相同预览与查看器。旧透明素材、输入网格、三项核对、保存事务与显式设封面保持原逻辑。

实际Compose：[浅色七皮肤](visuals/2026-10-28-display-comparison/native-light.png)、[暗色七皮肤](visuals/2026-10-28-display-comparison/native-dark.png)、[核对整页](visuals/2026-10-28-display-comparison/review-images-default-light.png)、[放大对照](visuals/2026-10-28-display-comparison/viewer-default-light.png)、[320dp两倍字与缺图](visuals/2026-10-28-display-comparison/viewer-small-font2-missing.png)。各皮肤复用实际组件、图标和令牌；相比示意图，原生保留现有批次摘要、搜索和三项核对，未扩大到整个工作台重排。图片为受控原创夹具，运输端返回受控图片，不代表真实模型保真度。

## 验证范围

- `DisplayComparisonScreenTest`：冻结输入跳过模板、相册后改仍指向本次输入、缺结果不回退原图；并排与单图、同步缩放／拖动／还原、SavedState恢复和七皮肤浅暗；320dp两倍字体下完整原图的打开返回及操作可达。
- `MaterialBatchScreenTest`：实际批次生成受控运输、失败项独立、放大返回不变选择、三项核对与批量追加相册、保存前后七皮肤；真实小屏搜索键盘与费用确认。
- `MaterialBatchNavigationTest`：实际NavHost中的任务详情对比、进入原批次和返回、新批次入口，继续不触发分层。
- 截图等待使用实际图片前景检查，并主动推进Compose帧和宿主重绘，避免异步Coil完成但测试快照停在空白；对比弹窗断言限定弹窗祖先，避免把背景卡片同名图片计入。
- 实际图稿对照发现将Coil `ImageRequest`作为Landscapist通用图片model会显示读取失败，已改为放大专用`SubcomposeAsyncImage`，保留2048请求尺寸；普通预览继续复用`LolitaShimmerImage`。最终放大矩阵逐图校验原图与结果的实际解码像素，拒绝仅有语义节点或错误占位的通过。

不新增图片、持久字段或数据库操作；Room34／备份15不变，原备份图片映射继续使用。未修改账本、位置、拥有状态、来源、删除清理、配置或付费请求，不重复全库逐字段／全部照片SHA／备份回归。本轮真实付费生成0次，无新网络出口，原用户图片和封面保留。

## 交付记录

源码`a2a03b32`与`v2.71.1`已普通推送，6项去重定向设备方法及Debug、提交归档clean原签名Release通过。原2.71.0覆盖与两次启动保留3份设置SHA；37主包素材、7背景RGBA、依赖与profile一致。独立AVD宿主退出后保留原数据，以host GLES禁用Vulkan恢复验证，未清数据；本次启动的模拟器已关闭。

GitHub／Gitee正式同一APK与SHA256SUMS匿名完整下载、摘要、HEAD及源码标签／公开最高正式版本通过；正式App一次手动检查显示当前和最新均为2.71.1、完整更新说明可见，[实拍](visuals/2026-10-28-display-comparison/release-update.png)。本机正式App核对实验室与原批次入口，新增对比界面以真实Compose仪器化验收；未虚构真实生图品质。最终结果见[发布回执](display-comparison-release-2.71.1-verification.json)。Gitee配额400后沿用户持续授权仅删除最旧2.63.0 APK，释放81,193,252字节；先核验旧GitHub摘要与匿名HEAD、本机三份完整副本，旧发布页和校验保留，[清理回执](gitee-apk-cleanup-2.63.0-2026-10-28.json)。

设计完成检查：前置图稿及七皮肤先于实现；真实Compose、小屏大字／IME、完整图片及放大、缺图／恢复和原资料边界均按本次范围检查。新入口均为已有页内对照，不增加新导航路由或业务流程。真实生成质量未在本轮重新测试。
