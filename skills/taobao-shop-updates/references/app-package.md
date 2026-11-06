# 采集输入 → Lolita广场 v1

当前消费者为Android 2.74.0及以后兼容v1版本。**公开包不是私人备份，不能生成SQUARE_WISH、Item、订单或付款。** App字段和实际校验器以工程`app/src/main/java/com/lolita/app/data/square/SquarePackage.kt`为准；阅读工程`docs/square-package-format.md`可看完整边界。

## 输入文件

每轮根目录`square-input.json`。顶层字段必须为：

```json
{
  "format": "lolita.square.collector",
  "schemaVersion": 1,
  "packageId": "沿用request.appPackage.packageId",
  "title": "本次公开目录名称",
  "capturedAt": "实际采集ISO日期或时间",
  "shops": [],
  "events": [],
  "products": [],
  "evidence": {},
  "deferred": []
}
```

shops：`id / name / directory / evidenceIds`，directory必须是request.shops中的本轮目录。官方身份未确认时不要加入shops；在deferred保存请求目录与原因。已有品牌沿用上下文id，不按店名相似性合并。

events：`id / shopId / designId / batchId / title / subtitle / kind / state / date / price / priceNote / note / material / detail / captured / productIds / evidenceIds`。文字字段全部为字符串；未知展示文字可空。kind只能为上新/再贩/现货/尾款；state如预热/已开售/已结束依明文填写。date保留公开日期原文，无年份不补。captured是实际采集日期。每个动态包含完整的关联商品组，每个product都须归入动态。新增批次新建batchId与event id，同批复采沿用；旧designId/batchId不改。不要凭商品ID大小或排序猜首发。

products：`id / shopId / title / url / capturedAt / pricePhase / priceType / skus / info / images / materials / coverage / evidenceIds`。id与原始itemId相同，url仅`https://item.taobao.com/item.htm?id=数字`或`https://detail.tmall.com/item.htm?id=数字`，去除跟踪和用户参数。pricePhase为FULL/DEPOSIT/BALANCE/PLACEHOLDER；priceType是给人看的真实价格说明，如“全款”“三团定金”“一二批尾款”，不能再填FULL等内部代码。原始products.json及CSV也使用同一可读说明，机器阶段另存pricePhase；原始网页证据不改。coverage为`{"skuComplete":true,"detailsComplete":true,"skuCount":实际完整数量}`。未完整则移入deferred，不假填true。revision由打包器计算，不让模型指定。

info只可用这些公开资料键：care/components/craft/excluded/gifts/included/materials/season/sizes/source/styles。里面保留结构化明文，但不嵌入原始SSR/HTML、买家、账号或认证字段。

images每项为`{localPath,role,order,url}`。localPath相对本轮根目录，如`shops/01-店名/images/商品ID/sku/001.webp`，必须与原始image-manifest逐项对应；role为main/sku/detail/promo，order为非负整数，url保留实际公开原图来源（https/http或页面原有的//形式）。完整原图不裁切、不重绘、不以截图替代；不包含raw/evidence目录。SHA、bytes、width/height和最终images/SHA路径由工具计算。

## SKU、单品和资料

SKU字段沿用App：`id / key / itemId / props / rawProperties / price / priceType / stock / content / color / size / form / parts / styleFacts / notSoldAlone`，另可提供`variantId / variantEvidenceIds / photoPaths / photoEvidenceIds`。

- id=原始skuId字符串，不接受0；key=`商品ID:SKU ID`；itemId=当前商品ID。props与rawProperties完全相同，保留每个公开维度及原值；所有实际SKU必须与原始products.json集合完全一致，不做颜色×尺码笛卡尔积。
- price使用精确十进制**字符串或null**，不把未知写0、不把起价当该SKU价；priceType与原始采集一致。stock是公开库存原文，非实时承诺。
- content是实际购买内容名称，form=单品/小物/套装/组合；notSoldAlone布尔值明确是否仅组合内提供。color/size为明确规范值或null，不把购买条款当尺码，但原文完整保留在props。
- 明确“一组/N件”的商家购买内容属于套装或组合，即便暂作为一个整体部件保留，也不能仅按“小物”单件标记。只有成员明细有依据才拆parts，未确认的成员规格不借用整体值；整组报价不分摊。
- parts每项`{key,name,category,isAccessory,color,size,relation,uncertainty}`。key在店铺设计/批次内稳定；category未知为null。每件自己的color/size独立，不能把裙子的L复制到KC。商家售卖套餐与用户未来自主组合分开。
- 一个可独立售卖的单品/小物SKU只有唯一部件、SKU色码已明确且部件无uncertainty时，工具可以将这条SKU已知色码补到该唯一部件，并在质量报告逐项记录；套装/组合、多部件、非独立销售及原本未知值均不继承。
- styleFacts每项`{value,source,field,evidenceIds}`，仅商家明文支持的风格，不凭图猜测。
- photoPaths为本商品images的**输入localPath**数组，明确单品/颜色图才给；填写时photoEvidenceIds必填。缺独立图用空数组，系列图仍留images，不能充当单品图。
- 无明确配对时省略variantId，工具按店铺/商品/SKU稳定生成。跨链接复用variantId必须同店、同动态批次、部件/颜色/尺码一致，并给每条variantEvidenceIds；不凭名称或金额猜测。继承上下文已核验的对应关系时保留其来源摘录。

materials每项`{partKey,detailPaths,facts,source,evidenceIds,sizeTable?}`。partKey须存在当前SKU部件中；detailPaths也是本商品images输入localPath。facts只放该部件明确参数和购买限制；source为公开来源说明。

sizeTable为`{columns:[字符串],rows:[{size:字符串,values:[字符串]}],note:字符串,evidenceIds:[证据ID]}`。每行values与列一一对应。1—12列、1—50行；列名和尺码≤100字、值≤200字、note≤1000字。单位写入列名；区间、不等号、小数点按原表；人体推荐和成衣平铺在note区分。长/短款只选对应列；共享整套表无法拆清时保留完整detailPaths，不编造单件表。每部件≤30资料图，每SKU≤30独立图。

## 证据、缺失和完成

evidence对象：`"ev-1":{"path":"shops/01-店名/raw/ocr/001.txt","quote":"原文摘录与必要图中文字定位"}`。path必须是本轮实际存在、根目录内、非符号链接文件；quote为1—2000字定位，不使用“已核验”代替证据。用于店铺、动态、商品、风格、照片归属、部件资料和表格的evidenceIds均须引用这里。证据只供本地核对，**不进入ZIP**。自动检查证明引用与结构有效，不能代替人工核对图文事实。

deferred每项`{shopId, itemId?, directory?, reason}`。整店失败必须给request目录directory；商品没采全/批次不明/图片下载失败列明原因，不能默默丢弃。凡已保存候选商品页却未采用的itemId也须逐件说明，包括收藏跳转、通用页和不属于本轮的候选；不要求为所有店铺历史商品建待办。未知字段不必捏造；少映射可浏览系列图并加入待补心愿，真正不完整商品不能装作完整上新发布。

## 主助手收尾（dsh不要推进基线）

1. 读取关键原图、SKU报价/颜色/尺码与表格，核对事实。
2. `uv run --offline --with pillow python <技能>/scripts/workflow.py package --output <本轮目录>`。
3. 工具核验原始JSON/CSV/图片，对照SKU与原图完整集合，计算SHA，合并历史公开目录并按实际变化升revision，再编译调用工程原版SquarePackage.kt验收候选ZIP。成功后才发布app-package/ZIP与quality.json并原子推进current.json；任何错误保留旧基线，候选文件仅供排错。
4. 查看quality.json：businessChanged排除仅采集时间变化；deferred与missingMappings单列。无实际变化不重复通知旧商品。交付资料包和需要处理的事项，不把自动结构校验写成人工全量事实审核。

上限：ZIP600MiB/清单8MiB/4000图/每图20MiB/单商品80MiB/每商品1000SKU及300图/总20000SKU/200店/2000商品及动态。超限停止并报告，不静默截断或自动重开淘宝。旧记录本轮未出现仍保留，不能据此删除或判断下架；私人物品完全独立。
