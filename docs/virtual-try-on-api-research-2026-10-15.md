# 虚拟试衣 API 选型调研

查阅日期：2026-10-15。目标是固定成年虚拟模特，加“花鸟龙月”裙子、短外搭、发箍、手袖、链条腰带：共六张独立参考图，一次生成完整搭配。本文是接口/公开评测调研，没有付费跨 API 实测。

## 推荐

优先小额试用 **OpenAI `gpt-image-2.5-sunburst`**。它在当前 Artificial Analysis AA-Image-Editing v2.1 榜单中以 max 设置排名第一；官方将其定位为精细编辑和高画质路线，且官方提示词指南有“同一人物＋多件服装参考”的完整实例。接口允许最多16张参考图，六图组合符合规格。这些证据支持“最值得先试”，不能证明它已经在洛丽塔细节专项中击败所有服务。

国内直连和小额试错候选是 **Seedream 5.0 Pro** 与 **Qwen-Image 2.1 Pro**；Google Nano Banana 2.1 是当前通用编辑榜单的强竞争者。初版暂定的 GPT-Image-2 已有更新版本，不应继续被描述成当前最强型号。

- [AA 实时编辑榜单与方法说明](https://artificialanalysis.ai/image/leaderboard/editing)：当前 Sunburst(max) 1198、Flare(max)1177、Nano Banana2.1 1148；这是通用编辑盲评。
- [OpenAI 2026-09-08 发布说明](https://openai.com/index/introducing-chatgpt-images-2-5/)
- [OpenAI 多件换装实例](https://developers.openai.com/api/docs/guides/image-prompting#preserve-identity-and-change-clothing)
- [OpenAI 编辑 API schema](https://developers.openai.com/api/reference/go/resources/images/methods/edit)

## 多参考生成候选

| 服务与正式型号 | 本用例适配 | 价格口径与限制 |
|---|---|---|
| OpenAI GPT-Image-2.5 Sunburst | 最多16图；精细编辑路线，支持人物与多件服装参考 | 按 token：图片输入$8/百万、输出$30/百万，文字输入$5/百万；不同质量/尺寸耗量不同。AA默认1024²标准样例约$0.2107/图，不能当作六图试衣总价 |
| OpenAI GPT-Image-2.5 Flare | 同样多图；更偏速度 | 与Sunburst同token单价，但模型和质量档耗量可能不同，不等于每图同价 |
| Google Nano Banana 2.1 `gemini-nano-banana-2.1` | 最多14图；新项目推荐路线，人物/物体一致性支持 | 2K输出$0.0504、4K输出$0.113；另输入$1.50/百万token，文字/思考输出$7.50/百万 |
| Google Nano Banana Pro `gemini-3-pro-image` | 最多14参考，复杂专业创作、高保真路线 | 六张图输入＋2K输出约$0.14112，＋4K输出约$0.24672；另文字/思考。输入图每张560token，输入$2/百万 |
| BFL FLUX.2 [max] | 官方API总共8输入图，六图足够；没有专用类别枚举限制 | 官方每参考图按1MP×$0.03；输出首MP$0.07、后续每MP$0.03。六图＋1MP输出约$0.25，＋4MP输出约$0.34 |
| Microsoft MAI-Image-2.6 | AA当前Elo1148；官方Foundry编辑端点最多5张输入，不足本用例六张独立参考 | 当前Public Preview，需要Azure付费订阅及部署；未核实本用例总价，不作为首充选择 |
| Seedream 5.0 Pro `doubao-seedream-5-0-pro-260628` | 最多10图，原生最高2K；偏画质 | 六图、输出超过261万像素：输出¥0.60＋额外五输入×¥0.02≈¥0.70；较小输出适用另一档 |
| Seedream 5.0 Flash `doubao-seedream-5-0-flash-260915` | 最多10图，最高2K；偏速度/价格 | 输出¥0.12，输入免费；不能据成本推出最高保真度 |
| Qwen-Image 2.1 Pro `qwen-image-2.1-pro` | 最多10图；官方仓库展示人物＋衣服＋鞋＋包＋帽组成整套 | 北京¥0.25/输出，输入不另计；合资格北京新用户10张免费，以控制台为准 |
| Qwen-Image 2.1 Turbo `qwen-image-2.1-turbo` | 最多10图，低成本对照 | 北京¥0.10/输出，合资格新用户10张免费 |

主要来源：

- [OpenAI 成本与质量说明](https://developers.openai.com/api/docs/guides/image-generation#cost-and-latency)、[价格](https://developers.openai.com/api/docs/pricing)
- [Google 图像生成与参考图限制](https://ai.google.dev/gemini-api/docs/image-generation)、[价格](https://ai.google.dev/gemini-api/docs/pricing)
- [BFL 多参考编辑](https://docs.bfl.ai/flux_2/flux2_image_editing)、[官方 schema](https://api.bfl.ai/openapi.json)、[官方价格](https://bfl.ai/pricing)
- [Microsoft MAI-Image-2.6 发布与多参考能力](https://microsoft.ai/news/pushing-the-quality-cost-frontier-with-mai-image-2-6/)、[Foundry编辑API五图限制](https://learn.microsoft.com/en-us/azure/foundry/foundry-models/how-to/use-foundry-models-mai-image#api-endpoints)
- [火山模型/API](https://docs.volcengine.com/docs/ark/image-generation-api?lang=zh)、[5.0 Pro 教程](https://docs.volcengine.com/docs/ark/seedream-5-0-pro?lang=zh)、[价格](https://docs.volcengine.com/docs/ark/model-pricing?lang=zh)
- [千问编辑指南](https://help.aliyun.com/zh/model-studio/qwen-image-edit-guide)、[价格](https://help.aliyun.com/zh/model-studio/model-pricing)、[2.1官方仓库](https://github.com/QwenLM/Qwen-Image-2.1)

## 专用试衣服务的具体限制

- **FASHN Try-On Max**：只有单个 `product_image`＋`model_image`，虽支持帽/首饰/包等，但 `num_images` 是生成输出数量，不是独立商品输入数。多件拼版的稳定性尚未核实。2K quality $0.30、4K quality $0.375；API最低$7.50/100credits；网页赠送credits不能当API免费额度。[API](https://docs.fashn.ai/api-reference/tryon-max)、[计费](https://help.fashn.ai/plans-and-pricing/api-pricing)
- **FLUX VTO v2**：多件需要先拼进一张商品画布；官方公开描述至多四件，本用例五件服饰小物超出该描述。可以作为四件以内对照。价格页数据仍含v1标识，不能把v1费用无条件当v2正式报价。[接口与免费Demo](https://docs.bfl.ai/flux_tools/flux_vto)、[四件说明](https://bfl.ai/blog/flux-vto-virtual-try-on-at-catalog-scale)
- **Pruna P-image-try-on**：最多11类别、每类一件；标准模式内部逐件处理，Turbo一次组合但4–5件以上可靠性下降。文档明确不支持 arm warmers，直接碰到手袖需求。不同官方页面存在价格差异，不作为首充候选。[模型限制](https://docs.pruna.ai/en/stable/docs_pruna_endpoints/performance_models/p-image-try-on.html)、[价格](https://www.pruna.ai/pricing)
- **Kling Kolors v1.5**：fal旧托管路线明确停用；Kling直连当前价格与可用性未完成官方文本核实，不根据旧榜单推荐充值。[fal状态](https://fal.ai/models/fal-ai/kling/v1-5/kolors-virtual-try-on/api)
- **Google virtual-try-on-001**：只收人物＋单商品；官方2026-09-14标记deprecated、2027-03-15计划退役，不宜作新接入。[生命周期](https://docs.cloud.google.com/gemini-enterprise-agent-platform/models/vto/virtual-try-on-001)
- **阿里Aidge**：人物＋单衣物图，1K¥0.40/2K¥0.50，不是六参考一次成套接口。[接口](https://help.aliyun.com/zh/aidge/virtual-try-on)
- **Oxygen-TryOn**：研究方向贴合多件any-item，但只核实到论文、官方仓库和Demo，未核实公开可充值API。[官方仓库](https://github.com/OxygenVision/Oxygen-TryOn)

## 充值与访问条件

OpenAI新API账户最低预充值$5，充值默认自动补充须自行检查；ChatGPT订阅不附带普通API余额。图像模型可能要求组织验证。支持地区以官方列表为准；不能承诺大陆账号/支付方式可开通。先确认账号具备模型权限，再小额测试。

Google公开账单文档当前写最低预存$5；中国大陆/香港不在Gemini Developer API支持地区。2026-03-02后新建账单账户的$300 Cloud赠金不适用于Gemini API/AI Studio。

火山、阿里统一最低充值额未从公开官方材料核实，不编造固定门槛。阿里2.1 Pro北京的合资格免费额度可作为国内低成本对照。

- [OpenAI API预充值](https://help.openai.com/en/articles/8264644-setting-up-and-managing-prepaid-api-billing)
- [OpenAI API支持地区](https://help.openai.com/en/articles/5347006-openai-api-supported-countries-and-territories)
- [Google账单](https://ai.google.dev/gemini-api/docs/billing)、[支持地区](https://ai.google.dev/gemini-api/docs/available-regions)
- [火山开通](https://docs.volcengine.com/docs/ark/activation-management?lang=zh)
- [阿里免费额度规则](https://help.aliyun.com/zh/model-studio/new-free-quota)

## 最小实测设计

同一固定成年模特，依次测试单裙、裙＋外搭、裙＋外搭＋发箍＋短手袖＋腰带；每种配置至少三张，控制构图和提示词。并排检查身份、五件全部出现、领口/拉链/盘扣、柄图/蕾丝层数、头饰比例、链条与袖口遮挡。记录真实请求型号、质量/尺寸、耗时、HTTP状态、usage与账单，不保存密钥。聊天内置imagegen未返回底层模型标识，已认可样片不能当作任何指定付费API的复现证据。

首轮资金建议：具备官方访问/支付条件时，先OpenAI最低$5测试Sunburst；国内用Qwen2.1Pro合资格免费额度对照，必要时加入Seedream5Pro。通过同图实测再确定应用默认服务，不购买长期套餐。
