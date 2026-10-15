# 正面人台图实验设计（2026-10-15）

## 用户授权与目标

用户要求“再多找几个测试数据看一下，觉得可以接受就追加一个实验功能到裙子详情，生成人台图，并保存到裙子图片里”。用户已明确停止三视图。本轮采用其授权的判断：三件新样本、四次生成达到有人工预览的实验功能标准；不需要再讨论是否开始。

目标是在已有裙子详情一键进入生成页，选择一张已有参考图，复用现有官方 GPT 图像编辑服务与个人 Key，生成统一正面人台效果。用户确认后追加到这件裙子的图片末尾，原图片、主图顺序及其他服饰字段不变。

## 样本结论

虞美人 OP：同一图含正背，成功取正面，长袖、奶油领片和大花印花主体保留，花纹/褶皱/腰结仍重绘。
昆虫记 OP：真人穿着转无头无臂人台，未带入头饰、皮肤或手，泡袖和三层裙摆保留，细印花仍重绘。
古董蛋糕 OP：默认提示仍混入独立手袖，不可直接接受；加入“排除独立手袖”后修正，主裙与袖端蕾丝保留。由此增加可选补充说明和保存前预览。
玻璃窗 SP：原图裁切且含独立头饰，仅检查，未提交生成；不把缺失结构当作可验证来源。
这些是聊天内置工具样本，未验证付费 API 效果、账号权限与实际费用。

## 页面与交互（实现选择）

- 有图片的 JSK / OP / SK 与明确中文裙类详情显示“生成人台图（实验）”；不限制已拥有，愿望裙也可生成。类别必须是 CLOTHING，不从商品名宽泛猜测。中文类别匹配固定集合：裙子、连衣裙、吊带裙、背带裙、半身裙、长裙、短裙；英文规范去空白、大小写后精确匹配。
- 独立 Screen.Mannequin(itemId) 页面；详情编辑 ViewModel 不持有生成草稿。
- 选择一张当前 Item 已有图片，默认第一张，可切换；仅上传明确选中图片与固定空白正面人台模板。保留原图缩略图与模板说明。
- 可选“补充说明”，最多 200 字，例如排除独立手袖；保存到 SavedStateHandle，密钥不存入其中。
- 沿用七皮肤、GradientTopAppBar、LolitaCard/Section、LolitaShimmerImage、ImageFrame、ObserveSkinScroll；不新增图标键，复用 Outfit/Image/Add/Save 等已有键。
- 显示“仅生成正面；AI可能改绘印花、蕾丝和比例，请核对后保存”，上传与独立 API 计费提示。没有 Key 时可在本页配置，共用加密存储和密钥对话框，不回填旧密钥。
- 生成中显示阶段和取消等待入口，禁止再生成/改参考图/改说明。返回时确认继续等待或取消，底栏与快捷穿搭全局锁定复用现有机制。
- 成功显示完整 Fit 预览并可放大，“添加到裙子图片”与“放弃预览”。预览未处理前不能再次生成。保存成功返回详情，图库订阅自动刷新。
- 放弃/离开未保存预览需明确确认，随后清理未引用文件；旋转不清理。进程重建时可恢复已生成待保存预览的路径和所属 Item，须验证该路径是应用自有 images 目录中的有效文件，不接受任意路径。
- 生成失败留在页内显示中文诊断，无自动付费重试；取消等待不保证服务端停止计费。

## 数据与文件

- 没有新实体、字段、Migration 或备份格式。已追加图片正常随现有 Item JSON/ZIP/CSV/恢复和删除清理流程。
- 新预览通过 ImageFileHelper.saveGeneratedImage 校验完整 PNG，并保存 UUID 到 filesDir/images。
- 新增 ItemRepository.appendGeneratedImage(itemId,path)：强制真实 Room 事务，读最新 Item，校验文件位于本应用 images 目录且存在可读；路径已存在幂等成功；只更新 image_urls 和 updated_at，保留最新业务字段/其他新图。Item 删除则拒绝。
- 清理与追加依赖同一数据库事务串行；保存过程所有权必须明确。ViewModel 保存开始后即持有任务，onCleared 不得在保存完成前抢先删除。回传取消时已经提交的图片仍由引用保护保留。
- 不写 TryOnResult 历史、不自动替换首图、不更改服饰名/价格/所属/状态。生成预览尚未入库，不能声称它已备份。

## 生成管线

- 复用 TryOnImageApi 官方 endpoint/model/medium/1024×1536/n=1/PNG，无自动重试或重定向；API 实例与进程付费生成 gate 共用，防两入口并发。
- TryOnImages 新增 prepareMannequin(paths) 或可控模板参数，默认原试衣逻辑不变；人台模板为 assets/mannequin/front-template.png，来自已归档正面模板。
- MannequinRequest(itemId: Long, imagePath: String, instructions: String = "")；验证 ID/路径及说明长度。支持函数 supportsMannequin(category: Category?): Boolean。
- MannequinGenerator.generate(request, onPhase): String 返回待保存预览路径；生成前重新查询 Item/Category，校验资格、图片仍属于此 Item、文件有效、Key 有效。使用最新 Item 并不要求 OWNED。
- 共享 ImageGenerationGate：runExclusive suspend 块 + isGenerating StateFlow<Boolean>，TryOnGenerator 继续转发同一全局状态；释放与取消边界必须覆盖。
- 上游保存预览期间取消或交付前取消要清理未交付文件；不能遗失已落盘 PNG。
- 补充说明追加到固定提示后，原始商品名称不作为自由指令插入；只按选定参考图识别本裙。

## 验证与发布

先补有意义的红测：共享锁、取消清理、来源过期、资格、说明限制、保存幂等/并发最新字段保全、Item删除、预览旋转/恢复/放弃、Key遮罩与导航保护。
JVM 全套 + assembleDebug；独立 AVD 真实 Room/Compose，七皮肤浅暗、大字、已有试衣回归及 ZIP 图片保全。
按 AGENTS 发布 2.49.0 / 107，clean assembleRelease、同签名覆盖 2.48.0，核对旧数据与原图片 SHA。GitHub/Gitee 同步正式 Release、附件下载及更新入口验证。无真实 Key 不声称付费联调通过。
