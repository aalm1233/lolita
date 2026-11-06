---
name: taobao-shop-updates
description: 查询淘宝店铺上新、品牌最新款或接续采集时，委派本机dsh的DeepSeek采集公开资料与原图，按Lolita广场契约生成可导入ZIP；主助手只读本地文件核对。支持已有自动化调用，不自行新建调度或购买。
---

# 淘宝店铺上新核查

## 用户固定要求：角色隔离

**主助手不得自行操作浏览器查看淘宝。** 不通过浏览器工具、桌面 UI、AppleScript、CDP、Playwright、页面 DOM 或自行请求淘宝页面/API来代替采集。也不因 dsh 失败而接管浏览器。

- **本地 dsh 的 DeepSeek**：负责官方店确认、正常访问公开页面、下载文字/原图、提取图片文字、保存证据、清理自己创建的临时进程和调试接口。
- **主助手**：可以发现/调用本地 dsh CLI、用命令提交或接续任务、读取进度和日志；只从落地文件、截图、原图进行商品分析、文件校验和报告生成。读取本地截图不等于操作浏览器。
- 这一任务的授权不包括购买、加购、联系客服、改账号设置、导出登录凭证、访问私人订单、绕过验证。保持已有权限，不自动放宽 dsh 的权限或修改全局配置。

普通自然语言触发即可，不要求用户每次指定技能或重述限制。接续同一请求时继承店铺和范围。除非用户提出新要求，不额外设置审批、调度、上传或应用开发。

## 执行

1. 简短说明由本地 dsh / DeepSeek 采集，自己只读本地证据。取用户上下文日期、Asia/Shanghai 时区；为本次采集创建独立目录，按店铺分目录，保留旧采集。
2. 用 `scripts/workflow.py prepare --shop '店名' --date YYYY-MM-DD` 生成请求及完整委派提示。多店重复 `--shop`。默认工程为`~/code/lolita`，可用`--project`指定；`--state`可指定独立目录基线。prepare附带现有公开目录上下文，沿用店铺/设计/批次/商品/部件/variantId，不按日期重造ID。读取 [collector-notes.md](references/collector-notes.md) 中本机经验；实际委派读取 [collector-task.md](references/collector-task.md) 和 [app-package.md](references/app-package.md)。
3. 用 `scripts/workflow.py run --output 'prepare返回的目录'` 启动本地 dsh。它优先查找已安装 CLI，不安装软件、不读密钥、不更改权限。已有授权的专用 dsh 会话可通过 `--session-id` 接续，已有配置覆盖可显式传 `--patch`，但必须先核实是用户已授权的配置，不得自动创建全权限覆盖。
4. 定期用 `status --output ...` 读取进度；不转述思考文本、原始账号对象或完整工具响应。向用户更新真实发现，避免重复承诺“马上完成”。需要修正时，将具体补充指令写入本次目录的 `followup.txt`，在上一进程退出后用 `run --output ... --session-id ... --task-file followup.txt` 接续，不并发写同一会话。重复技术错误应换现成读取入口，停止无限逆向、端口探测或反复重采集。
5. dsh 先确认官方店和最新依据，再收集相关款式及配件。主店跳二店须保存明确跳转证据。**新品排序、商品ID、修改时间不能单独证明首发时间。** 区分新设计、再贩、预热、现货/掉落、尾款、优惠券/权益卡和展示汇总。未证实最新时报告“本次可核实的在售/再贩/补尾款”，不冒称最新首发。
6. 主助手开始复读落地资料，不只复述 dsh 的最终回答：用本地图片查看工具读价格图、款式/材质图、尺码图、发货/预约说明；图片多时可生成联系表，再放大关键原图。OCR只用于查找，必须核对小数点、`<`/范围、尺码行列、单位、成衣/人体尺码和适用款式，不插值补齐缺失数据。
7. 运行本地验收：`workflow.py verify --output ... --report .../verification.json`。需要 Pillow 时使用本机已安装环境，或 `uv run --with pillow python .../workflow.py verify ...`。脚本校验 JSON、商品/SKU标识、CSV对应、图片路径/SHA/尺寸/解码；不会浏览或联网。修复已观察到的失败后再验，不因追求无关“全覆盖”不断重开网页。
8. dsh同时完成`square-input.json`，包含上新/再贩/现货/尾款、真实完整SKU、部件、图片归属、风格依据、参数、独立尺码表与证据引用。未知映射留空，不拿整搭图补KC封面，不把裙码给小物，组价不分摊。详细契约在[app-package.md](references/app-package.md)，不是复用首包的硬编码商品ID脚本。
9. 用`uv run --offline --with pillow python scripts/workflow.py package --output ...`只读本地完成原始清单/CSV/原图核验、App输入对照、SHA打包、稳定revision合并、实际Android Kotlin格式验收。成功才发布`app-package/*.zip`、`.quality.json`并更新本机目录基线。当前基线已变化时重新prepare，不覆盖新资料；原图或格式失败不推进基线。原始证据、OCR、日志、私人物品来源协议不得放入公开ZIP。
10. 主助手完成中文报告，交付ZIP及质量报告；dsh可先写初稿，主助手仍负责读关键原图与结论。dsh清理自己的临时进程并写`cleanup.json`、`DONE.json`。部分店铺阻塞时，完成店可打包，失败店和未确认商品必须列`deferred`，不能把旧包当作本轮新采集成功。结构/证据引用自动通过不等于语义已人工复核。

## 自动化复用

现有自动化提示词需要更新时复用[automation-prompt.md](references/automation-prompt.md)，并用自动化工具修改已确认的任务，保留原调度/范围/通知设置；没有实际任务不宣称已更新。既有自动化每次调用同一prepare→dsh→本地复核→package流程，时区Asia/Shanghai；沿用原调度、店铺范围及通知设置，不在此技能中新建定时任务。使用`quality.json`的`businessChanged`区分实际内容变化，只有采集时间变化不当作新品。首次基线、再贩、现货和尾款分别报告，不凭链接新发现断言首发。没有变化时不重复报告旧商品；有实际变化、失败或需要处理的核对项才报告。

工具和提示词的维护源在Lolita工程`skills/taobao-shop-updates/`，全局安装目录是该源的同步副本。通用打包入口为工程`scripts/square/collector_package.py`。首次接续已有目录时用其`configure --baseline <已验收广场ZIP> --project <工程>`建立基线；已有基线禁止自动替换。不更改App、云端或私人衣橱。

## 广场与历史资料

- 明确下架、仅展示和结束的预约批次先不在广场展示，公开资料与原图保留，供后续博物馆使用；博物馆入口尚未实现。占位价不是在售。部分缺货、页面暂时失败、本轮未出现均不能推断下架。
- 商品售卖状态使用带来源/观察时间/证据的listing；已存商品可用listingUpdates只更新状态，不捏造新SKU或假称重新采全。重新在售须有新证据明确ACTIVE；已归档记录不能被字段缺失或陈旧观察复活。详细格式见[App契约](references/app-package.md)。

## 数据与价格规则

- 收集商品标题/ID/URL/官方店身份、采集时间、上新依据、全部公开款式/颜色/尺码及完整 SKU 对应报价；面料、工艺、套装包含/不包含、赠品条件、开售/截团/发货、洗护、尺码表。
- **配件起价不是整裙价；定金/尾款不是全款；展示占位价不是售价。** 分别保存本次实际页面报价、详情图预约价及批次。不同批次或阶段金额不擅自相加，差额不猜测为优惠。
- 标题“现货”与页面“预售N天内发货”并列记录。无年份旧预约日期不补写年份；过期预约、当前补尾款、未来预热分别标注。
- 保留主图、SKU图、全部详情原图及顺序/来源；使用真实懒加载源，排除占位/推荐图。店铺推广图另标角色，不冒充商品详情。缩略/裁切联系表另存，不覆盖原图。
- 数量分别报：设计数、商品/补款链接数、SKU数、图片文件数和不同 SHA 数。不要把 manifest.json 当图片，也不要把跨链接重复副本称为全局唯一图。
- **脱敏只针对实际买家字段/区域**，禁止在全文对“像手机号的连续数字”替换。保留商品ID、SKU ID、卖家/店铺ID、公开日期、CDN URL、路径和价格。脱敏后检查这些值与目录、CSV、manifest一致；被脱敏的网页证据注明是副本。

## 交付

每店：`README.md`、`products.json`、`sku-prices.csv`、`image-manifest.json`、`images/<商品ID>/{main,sku,detail,promo}/`、`raw/`（含逐图OCR）、`evidence/`、`DONE.json`。结构要求在委派模板中。根目录保留 `request.json`、`progress.txt`、`verification.json`、`cleanup.json`、`DONE.json`。

最终回答以商品信息为主：先说是否证实最新、目前购买状态；列主要款式/价格/颜色、必要的尺码/材质/发货和未知项，链接本地报告及原图。说明由本机 dsh 采集、自己只读本地文件。内部工具排障过程不占主要篇幅。
