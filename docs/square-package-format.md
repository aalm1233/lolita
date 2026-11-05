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

登记复用既有ItemDraft和Item.importToken：默认心愿，每次登记整组或明确的其中一件。相同店铺批次/variantId/登记部件复用原草稿或打开已保存物品；删除后用户主动重新登记可创建新记录。完整主图/规格图与全部详情图复制为私人UUID文件。图片、来源说明和去重标识进入原JSON/ZIP备份，公开目录需保留原广场ZIP重新导入。Room34、备份15均不变；不自动建立账本、实付、衣橱套装或小物关联。

首包转换：`python3 scripts/square/build_first_package.py <输出ZIP>`。它只读取已归档的三店公开商品资料和原图，不联网。源目录为本机Downloads下既有档案；公共仓库仅存脚本和去隐私的校验回执，不存本机原始页面或大型ZIP。
