# 广场离线资料包 v1

Android 2.72.0 起支持。入口：底栏「广场」→「导入资料包」，选择ZIP，核对店铺、动态、规格与图片数量后导入。它不是衣橱备份，不走恢复入口。原始采集继续由本机dsh负责，主助手不访问淘宝。

包根目录只有 `manifest.json` 与 `images/<sha256>.<jpg|jpeg|png|webp>`。UTF-8 JSON 顶层字段：

| 字段 | 意义 |
|---|---|
| format / schemaVersion | 固定 `lolita.square` / 整数1 |
| packageId / revision / title / capturedAt | 包稳定标识、正整数版本、名称、采集日期 |
| shops | id、name、revision |
| events | id、revision、shopId、designId、batchId、title、subtitle、kind、state、date、price、priceNote、note、material、detail、captured、productIds |
| products | id、revision、shopId、title、url、capturedAt、pricePhase、priceType、skus、info、images |
| assets | path、sha256、bytes、width、height；每条必须被商品图片引用 |

动态 `kind` 为「上新 / 再贩 / 现货 / 尾款」，销售状态、批次、价格阶段分别维护。`pricePhase` 为 FULL / DEPOSIT / BALANCE / PLACEHOLDER。未知价格为 null，不能用0、起价、定金、尾款或占位价替代全款。

SKU包含 `id / key / itemId / variantId / props / rawProperties / price / priceType / stock / content / color / size / form / parts / styleFacts / notSoldAlone`。`key` 固定 itemId:skuId；`props` 与 `rawProperties` 保存完整原文。规范颜色/尺码允许与原规格说明分离，必须由明确文字核对；不按照片推断。`variantId` 是店铺批次内稳定购买规格标识，仅明确同款同规格的全款/定金链接可共用，不凭名称猜测归并。首包对三组已核实的全款/定金链接保留同一variantId，移除尺码末尾明确购买条款时原文不丢失。

`form` 为「单品 / 小物 / 套装 / 组合」。部件记录 `key/name/category/isAccessory/color/size/relation/uncertainty`；没有独立标明的第二件颜色尺码保持null。风格事实记录 `value/source/field`，来源不明不填。筛选必须在同一实际SKU上同时命中，不能组合出页面不存在的规格。

`info` 保存公开部件、包含/不含、赠品条件、洗护、材质、工艺和尺码资料，不含Cookie、账号、订单、联系方式或原始页面。`images` 保存 path、role、order、url；角色为 main / sku / detail / promo。原图按内容去重，仍保留全部商品引用和原顺序。

导入上限：ZIP600MiB，清单8MiB，4000张原图，每张20MiB，单商品80MiB，200家店、2000动态、2000商品、20000个SKU；单商品最多1000个SKU、300张图片。超过上限明确失败，不截取后假装完整。拒绝重复ZIP条目、目录/路径穿越、额外文件、损坏校验、缺图、版本和引用冲突。

相同记录id按revision合并：更高版本更新，相同版本内容必须相同，较旧版本保留本机新资料；此次包未列出的记录保留。不通过缺席推断下架。不自动改写已登记私人物品。公开目录用AtomicFile一次发布，图片按SHA存放；校验/预览失败不覆盖旧目录。未来云端可使用同一模型与图片哈希分发，网络同步尚未实现。

登记复用既有ItemDraft和Item.importToken：默认心愿，按当前选择逐件编辑确认。相同店铺批次/variantId/登记部件复用原草稿或打开已保存物品；删除后用户主动重新登记可创建新记录。2.72.0原实现复制整链接图片；2.73.1按下文每件主动选择的主图与资料复制为私人UUID文件，未选图片仍保全在广场。图片、来源说明和去重标识进入原JSON/ZIP备份，公开目录需保留原广场ZIP重新导入。Room34、备份15均不变；不自动建立账本、实付、衣橱套装或小物关联。

首包转换：`python3 scripts/square/build_first_package.py <输出ZIP>`。它只读取已归档的三店公开商品资料和原图，不联网。源目录为本机Downloads下既有档案；公共仓库仅存脚本和去隐私的校验回执，不存本机原始页面或大型ZIP。

## 2026-11-06 部件资料的可选扩展

仍是 schemaVersion 1：商品可追加 `materials`，SKU可追加 `photoPaths`。旧v1包缺失这两项时视为未提供映射，仍能完整浏览原图；登记时手动选图，不把整条链接相册或全部详情静默复制到每件。修订包把商品revision升为2，保留原商品/SKU/variantId和批次身份。

- `SquareSku.photoPaths`：明确SKU属性原始image引用对应的本地SHA图片路径，最多30张；不得根据模特整搭猜单件。
- `SquareProduct.materials`：`{partKey,detailPaths,facts,source}`，同一商品内经本地原图核对的部件资料与明文说明。资料原图可含共享表，保留完整图片及来源；不自动提取某行数值写入物品规格。没有证据就留空。
- 所有路径必须已属于当前商品且出现在资产清单；图像与事实均随商品revision更新。旧包导入更高版本目录不能回退资料。

登记输出由当前实际SKU包含的部件多选而成，可分别登记或把任意已选部件合成一个物品；组合名称、类型及每件主图/资料分别核对。最多每类50张，主图首张为封面，两类不得重复，累计副本仍受80MiB限制。每件独立UUID副本沿用ItemDraft显式图片列与现有备份15；公开目录原图全部保全。整体和单部件沿用原稳定去重身份，自选子组合使用排序部件集合的稳定身份，不按图片或名称制造新身份。

分件未知颜色、尺码保持未知；组合价仅保留来源快照，不下放或自动写价格、订单、实付、父子关系或衣橱套装。逐件进入现有编辑器并明确保存；选择流程通过SavedStateHandle恢复，已准备的草稿保存在Room。离开后可在同一导航状态继续；关闭整个任务后未准备的后续件不承诺恢复，已保存记录和持久草稿保留。

## 浏览版可选尺码表（2.74.0）

`products[].materials[]` 可增加 `sizeTable`：`columns: List<String>`、`rows: [{size: String, values: List<String>}]`、`note: String`。每行值与列一一对应；1—12列、1—50行，列名≤100字、值≤200字、说明≤1000字。绑定到明确 `partKey`，不得将整套或其他部件的尺寸作为本件参数。商家人体推荐尺码与成衣平铺尺寸须分清。

旧v1包缺少表格仍可浏览原参数图及直接加入心愿，不强制补映射或选图。`scripts/square/enrich_size_tables.py` 只读已采集资料，以显式核验的表名绑定41张表，不联网、不改变421张图片原字节；无法可靠拆到部件的帝政SET表继续保留原图而不生成部件表。莉莉子长短OP各保留对应裙长列，不交叉显示。

公开包仍为v1；新增私人心愿来源使用备份16，与公开包版本分开。来源快照的成员图片只保存显式图片数组索引，ZIP恢复统一重映射路径。
