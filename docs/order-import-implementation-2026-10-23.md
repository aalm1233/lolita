# 淘宝订单导入与后续整理实施记录

设计基线：2.67.0/142、Room32、备份13，源码55b251c4。实现版本：2.68.0/143、Room33、42个实体、备份14。前置艺术方向、14页原型与七皮肤浅暗已先于代码归档于[完整设计](plans/2026-10-22-order-import-complete-design.md)；本文记录最终采用范围，原型不替代Android证据。

## 最终流程与数据边界

| 场景 | 实际实现 |
| --- | --- |
| 入口与续办 | 本机Room保存会话、文件、逐件输入、图片、配对、筛选与当前步骤；返回flush，短防抖保存，revision拒绝陈旧覆盖；恢复不自动联网或入账。 |
| 文件预检 | 显式表头、文本长订单号、数量与文件限额；先检查XLSX解压边界再交POI；失败文件单列且可重选，继续按钮说明只使用成功文件。 |
| 来源查重 | 规范公开商品ID/规格/件号，跨会话回执查重；完全相同快照合并，差异版本明确选用；保留永久来源证据。原物品删除后来源墓碑保留，重新登记必须确认。 |
| 金额与日期 | 保留原金额字符串，空白／异常不冒充0；整数分分摊，余分按稳定来源顺序分配。下单时间不作为付款日，正实付缺真实付款日期留草稿；输入“12.”等半成品不可确认。 |
| 选择与品牌 | 名称／店铺／文本订单号搜索，店铺／日期区间／日期未知及状态筛选；隐藏选择保留。已有品牌可搜索，未核验店铺不自动成为真实品牌，新建候选到正式导入才创建。 |
| 核对与批量 | 逐件／列表与品牌／类型批量操作分开；问题原因筛选及下一条待处理；金额、日期和手填尾款通栏，固定唯一文字主操作，焦点字段在IME和输入变化后保持可见。 |
| 提交与恢复 | 冻结operationId及会话revision；Item、账本、来源和成功回执同一事务；失败整批回滚。并发同源拒绝重复，提交成功未返回可读回执恢复结果，未成功不自动重发。 |
| 已有物品关联 | 明确选择原物品后只补来源证据，物品、订单和实付不变；关联项与本次新建项分别列出。 |
| 结果与收纳 | 结果按提交回执的真实新增ID展示；已删除新增保留历史数量。收纳只作用于本次新增且目前已拥有的项，提交前再核对修改时间和拥有状态。 |
| 购买核对 | 原ItemPurchaseReview投影按缺金额／实付待核对／日期未知／购买关系待核对筛选，计数允许重叠，共享订单只处理一次；跳转原价格或实付页面，跳过不写入推测值。 |
| 原物品补资料 | 绑定targetItemId，复用公开网页提取，禁用逐SKU新建队列；SKU明确确认后选择名称／品牌／类型／颜色／尺码／参数说明／照片／详情图。原字段默认保留，照片SHA去重追加且保留封面，完整Fit及手势放大。 |
| 同一编辑草稿 | 已有ItemDraft时只向同一草稿合并，实际编辑保存才写永久来源；独立补资料直接事务提交。Item与草稿基线同时做CAS，删除或陈旧均阻止覆盖。 |

页面通过Screen/LolitaNavHost接线，使用GradientTopAppBar、LolitaCard/LolitaSection/SectionHeader、EditorSaveBar、皮肤图标与ObserveSkinScroll。没有新增付费服务、后台抓取或账号权限；没有修改旧账本的历史未知日期规则、图片生成协议或费用确认。

## 与设计的具体实现差异

- 导入会话按“会话根JSON＋显式行／文件表”存储，不逐字段复制原型中的拟议列；根JSON不含图片或业务实体副本，图片路径可被备份枚举。来源行ID采用会话ID与位置，来源身份另存稳定键。六张新增表全部只追加。
- 行重排可稳定归一；旧系统没有回执的历史物品仍需用户明确关联，不能靠同名断言重复。数量或来源身份不确定时保留问题并要求人工核对。
- 文件替换保留人工输入并使受影响的财务确认失效；移除前列出未提交影响。重新选择订单时保留已经编辑的资料，避免选择切换静默丢失输入；最终以核对清单与确认范围为准。
- 网页材质等参数由用户明确选中后追加到备注，保留来源说明，不猜测映射到WardrobeSpecifications的结构化单位。图片按商品照片与详情图两组选择追加，不提供新的封面快捷操作；封面继续由既有编辑页主动设置。
- 原型中的“重新合并”落实为重新读取当前目标／草稿再比较，不自动把陈旧候选覆盖到最新资料。活动比较控件的勾选状态尚未提交时属于当前页面状态，已读取候选及图片保存在草稿，重开可继续选择。
- 本轮未增加诊断文件导出、新备份格式自动降级或全应用重做。损坏会话提示处理失败，可保留主衣橱并删除其未完成草稿。

## 数据、备份与图片

32→33新增OrderImportSession/File/Row/Commit/Receipt及ItemSourceRecord，DAO、Repository、AppModule与迁移均接线；不修改原36表和历史账本。会话清理不删除永久提交／来源回执；Item删除将来源回执的Item引用置空，保留代际证据。草稿和来源图片接入ImageFileHelper引用保护，删除物品回收没有其他引用的来源图片。

Backup14包含六张表、完整导入输入与候选证据；预览增加会话／待核对资料／来源数量，详细数量继续折叠。JSON/ZIP恢复前校验版本、ID、关系、代际、配对、白名单与显式图片；图片路径逐项重映射，恢复不自动提交。旧1—13缺新增集合时补空，不补造回执；旧ItemDraft缺补充字段继续可用。CSV增加公开商品链接与来源明细，保留公式转义，不输出内部来源键／操作ID／importToken；CSV不能恢复。

## 定向验证

设备全部使用独立AVD和独立Room／UUID设置；没有卸载、清空用户设备，没有使用用户淘宝订单或账号。受影响范围如下，未运行无关全量回归。

| 验证 | 范围及结果 |
| --- | --- |
| Debug | assembleDebug、assembleDebugAndroidTest通过；KSP生成33.json。 |
| JVM | OrderImportReliabilityTest与TaobaoOrderParserTest共12项通过；BackupFormatTest、WardrobeBackupFormatTest、ImportAccountingTest定向回归通过。覆盖金额、来源重排、表头、付款日期、解压限制、日期未知筛选及补充证据白名单。 |
| 会话 | OrderImportSessionTest 10项：部分完成续办、提交回执恢复、重复／跨会话并发、同会话CAS、已删除代际、整批回滚、来源冲突与原输入保留、图片引用、Backup14/旧13及非法关系拒绝；CSV公开明细与内部键排除。 |
| 补充 | ItemSourceSupplementTest 4项：真实图片SHA去重与原字段／账本保全、已有草稿单写入及延迟证据、陈旧／SKU阻止、待补候选ZIP图片往返。 |
| 迁移 | OrderImportSessionMigrationTest用32库填充原36表、原列与关联，按真实Migration升级33，逐字段／图片保全，外键和完整性检查通过。 |
| 原功能 | ItemDraftBaselineTest、ItemDraftDeletionRaceTest、LegacyDraftBackupTest的JSON/ZIP旧草稿往返及TaobaoProductViewModelTest共22项通过，覆盖旧编辑基线与商品规格队列。 |
| Compose | 六个定向方法覆盖十二页七皮肤浅暗、搜索隐藏选择、实时金额隐私、付款日期、半成品金额、320dp两倍字体、真实IME及完整照片／长图预览。原图片字节不变。 |

IME初次仅可见性断言不足，实际截图暴露金额更新后焦点可能移出视口；修复后增加输入框底部必须位于保存按钮上方的几何断言。暗色初稿宿主遗漏LocalContentColor，已按主应用Surface语义修正并重新导出，旧错误图不作为验收证据。

## 原生视觉对照

原始截图保留于本机`artifacts/order-import-design-2026-10-22/native-pages/`，公开归档只含虚构业务资料。十二页默认尺寸为1440×3200、560dpi；小屏960×2133、480dpi＝320dp，页面使用真实2倍fontScale。七皮肤详细核对页面另导出，首次屏幕未显示的长内容可滚动，不用缩字号压缩到一屏。

- [实际主流程与后续整理](visuals/2026-10-23-order-import-implementation/native-main-light.png)
- [实际七皮肤浅色](visuals/2026-10-23-order-import-implementation/native-skins-light.png)、[实际七皮肤暗色](visuals/2026-10-23-order-import-implementation/native-skins-dark.png)
- [320dp两倍字](visuals/2026-10-23-order-import-implementation/native-small-font2.png)、[真实IME](visuals/2026-10-23-order-import-implementation/native-real-ime.png)
- [商品照片与长详情完整Fit](visuals/2026-10-23-order-import-implementation/native-image-fit.png)、[184张原始截图摘要](visuals/2026-10-23-order-import-implementation/verification.json)

原型14页中的品牌映射、配对、原因核对与逐件／列表在原生中合并到现有页面及弹窗，不逐个新增导航路由。已核对标题、文字层级、长店铺、财务通栏、主操作可达性与暗色文字；未宣称已做TalkBack人工朗读认证。图片用程序生成条纹夹具验证完整Fit、长图比例与字节，不冒充真实淘宝图片质量。

## 升级与发布

正式包从本次已提交源码的git archive隔离目录clean构建，沿用原签名；项目素材／七背景与依赖素材分别验证。独立正式AVD保全2.67.0的36张旧表、14张原图及3份设置基准；覆盖安装、两次启动、逐字段／SHA和应用版本核验以最终发布回执为准。

正式源码、双渠道附件、匿名下载／HEAD／SHA、公开版本接口与应用实际检查分别记录于本轮发布回执。发布页或API可读不代表应用检查成功，遇外部403如实保留，不改网络逻辑绕过。本轮未删除旧发布附件，应用AI付费调用0次。

### 本轮交付结果

源码`fa7797c3`与`v2.68.0`已推送。43项去重设备方法通过，Debug与提交归档clean原签名Release通过；2.67.0同签名覆盖并两次启动，36旧表逐字段、14原图SHA及3份设置SHA保持，Room33／42表。37主包assets、7背景RGBA、依赖和profile与已提交源码核验通过；正式App导入入口与Debug主NavHost原物品补充入口已操作。

[GitHub 2.68.0正式版](https://github.com/aalm1233/lolita/releases/tag/v2.68.0)的同一APK／校验文件匿名完整下载、SHA／HEAD和源码标签通过。Gitee上传实际返回HTTP400“文件大小已超出仓库附件配额：1 GB”；新发布保持预发布、没有上传APK，尚未进行正式后的公开版本与App手动检查。已向本轮用户申请仅清理Gitee的2.60.2旧APK，未获得答复前不执行；旧GitHub、本机原包和原Gitee匿名下载副本SHA均为`7044094e…`且一致。不能把GitHub交付描述为双平台全部发布完成。

[正式发布回执](order-import-release-2.68.0-verification.json)、[正式包入口](visuals/2026-10-23-order-import-implementation/release-import-entry.png)、[实际原物品绑定补充入口](visuals/2026-10-23-order-import-implementation/navhost-supplement.png)。
