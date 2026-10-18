# 衣橱局部识图服务核验（2026-10-18）

本轮识图提供豆包与 DeepSeek 两个选项。用户已持有两家的账号；账号是否已获模型权限、余额和实际识别质量尚未实测。新识图协议没有发布或真实调用过，因此拒绝此前开发中的 OpenAI 请求快照，不向 OpenAI 发送识图请求。生图设置仍由原有生图模块管理。

## 官方接口与模型

| 项目 | 火山方舟 | DeepSeek 官方 |
|---|---|---|
| 请求服务身份 | `VOLCENGINE_ARK` | `DEEPSEEK` |
| URL | `https://ark.cn-beijing.volces.com/api/v3/responses` | `https://api.deepseek.com/responses` |
| 请求 model | `doubao-seed-2-1-pro-260915` | `deepseek-flash` |
| 关闭思考 | `thinking.type=disabled` | `reasoning.effort=none` |
| 结构输出 | `text.format` 的 `type/name/schema/strict=true` | `text.format` 的 `type/name/schema` |
| 响应存储参数 | `store=false` | 不发送 `store`；官方说明该接口无状态，响应恒为 `store:false` |

方舟逐项核验依据：[Responses API](https://console.volcengine.com/ark/region:cn-beijing/docs/create-model-responses-api)、[模型列表](https://console.volcengine.com/ark/region:cn-beijing/docs/model-list)、[深度思考](https://ark.volcengine.com/region:cn-beijing/docs/deep-thinking.md)。方舟结构化输出仍标记 beta，本机始终再次严格验证，不将服务端约束当作可信结果。

DeepSeek 的[Responses 指南](https://api-docs.deepseek.com/zh-cn/guides/responses_api/)、[创建响应参考](https://api-docs.deepseek.com/zh-cn/api/create-response/)和[思考模式](https://api-docs.deepseek.com/zh-cn/guides/thinking_mode/)独立核对。其参考页列明 `json_schema` 的 `type/name/schema`，未列独立 `strict` 参数，因此不照搬方舟字段。该服务的响应常量 `parallel_tool_calls:true` 不代表发生工具调用；实际输出只允许一个完整 assistant message、一段 `output_text` JSON，工具或推理输出仍拒绝。

截至核验时，[DeepSeek 模型与价格](https://api-docs.deepseek.com/zh-cn/quick_start/pricing/)把 `deepseek-flash` 指向 `DeepSeek-V4.1-Flash-1017`，支持图像理解，`deepseek-v4-pro` 不支持。请求使用官方调用别名；别名会变化，任务分别保存请求 model 与上游实际返回的 model。若上游只返回别名，就原样记录别名，不伪造固定版本，也不承诺可回到 1017。

两家均发送 Bearer Key、单轮 user 输入、JPEG data URL、`detail=high`、`stream=false`、`max_output_tokens=6000`。不发送工具、会话、历史响应、用户身份或远程图片 URL。两家分别构建 payload、核验各自响应字段，不能用切换 URL 的方式假设协议相同。

## 开通与原价

方舟已有 Seedream 账号或密钥，不等于已开通豆包模型。需要在北京地域控制台确认 Seed 2.1 Pro 模型服务、项目/密钥访问权限和可用余额，再主动配置识图 Key。应用仅对[官方错误码](https://console.volcengine.com/ark/region:cn-beijing/docs/error-codes)允许列表中的模型未开通/模型无权限错误给出固定中文开通提示，不保存服务端错误正文。

方舟[常规在线推理价格](https://ark.volcengine.com/region:cn-beijing/docs/model-pricing.md)中，Seed 2.1 Pro 输入长度 0–1024k 的非音频输入原价为 **6 元/百万 token**，输出 **30 元/百万 token**。3/15 元属于低优推理档，本接入没有请求该档，不能作为本次默认原价。

DeepSeek Flash 官方原价按时段区分：未命中缓存输入空闲 **1 元/百万 token**、高峰 **2 元/百万 token**；输出分别 **4/8 元/百万 token**。高峰为北京时间工作日 9–12 时、14–18 时，周末及中国法定节假日为空闲。图片 token、实际输出和最终账单决定单次成本；这些公开单价不构成质量比较或单次费用承诺。

## 本机安全边界

- 每次请求保存 provider、对应固定调用 model、区域 ID/SHA256/尺寸等不可变信息。服务选择、Key 版本或草稿变化后，先前付费确认失效。执行从该请求的 provider 读取 Key，不从当前默认服务推断。
- `RecognitionSettings` 使用 `noBackupFilesDir`。方舟文件/别名为 `recognition-ark-credential-v1` / `lolita.recognition.ark.personal-key.v1`；DeepSeek 为 `recognition-deepseek-credential-v1` / `lolita.recognition.deepseek.personal-key.v1`。AES-256-GCM 使用新 IV。两家不互解，不读取旧 OpenAI 识图文件，也不读 `TryOnSettings` 或 Seedream 密钥。
- 用户可以主动在识图设置重新输入同一个方舟 Key，但密文文件和用途仍独立。`recognition-provider-v1` 只保存在本机；损坏时要求明确重新选择，不暗中换服务。密钥及服务选择不进入显式备份。
- 上传只接受用户最终确认的 1–5 张裁切 JPEG，每张最多 3 MiB、累计 12 MiB。校验 SHA256、像素尺寸和 JPEG 标记，拒绝 EXIF/XMP/IPTC/注释。请求不包含本地路径、草稿原字段、整图、购买或付款资料。
- 禁止跟随重定向和自动重试；固定长度流式 POST 避免 HTTP 库重放。连接/读取/总等待时间有界；取消会断开连接。超时后主动重试可能再次计费，应用不会自动补发。
- 响应上限 1 MiB；只接受 completed、当前 schema、已知字段与当前区域 ID。重复 JSON 键、拒绝、截断、额外内容、坏结构均返回固定中文错误。新结果每个候选必须显式携带 `measurement`（可以为 null）；尺寸对象、口径、单位不靠视觉补猜。原响应、服务端错误正文、Key 和异常 cause 不持久化。

设置接口为 `readProvider/selectProvider`、`readKey/saveKey/clearKey/hasKey(provider)` 和 `changes`。`recognizeWithMetadata` 返回 `RecognitionApiResult(result, provider, actualModel)`；兼容的 `recognize` 仅返回结构结果。任务保存实际模型由协调器事务完成。

## 验证与未测范围

受控 HTTP 测试将生产 URL 交给注入工厂断言后，只连接 `127.0.0.1`，使用虚构 Key 和手写响应。已覆盖两家精确 endpoint/payload、原始裁切字节、模型返回元数据、响应拒绝、未知字段、重定向拒绝、无重试、取消断开和错误净化。请求协议测试拒绝未知服务、旧 OpenAI model 与 provider/model 交叉组合。

本轮构建 `testDebugUnitTest assembleDebug assembleDebugAndroidTest` 成功；376 个 JVM 测试通过，其中传输 18、服务快照 4。独立 `Lolita_Wardrobe_Test_35`（emulator-5582）真实 Android Keystore 回归 9/9，通过同签名版本 113 的 `install -r` 保留数据验证。最终构建证据为 `.superpowers/sdd/2026-10-18-complete-wardrobe-phase1/recognition-provider-final-green.log`，设备证据为同目录 `recognition-dual-green-device*`。

ARK 替换阶段已取得实际断言 RED；追加双服务阶段先取得缺失新接口的编译 RED，再补齐实现通过上述回归。不能把接口编译错误写成业务断言失败。随后 fresh 响应版本类型及请求数字/标签强转的回归，均另外取得共享 Gradle 的真实断言 RED，再补齐严格校验通过完整 GREEN；日志为 `recognition-dual-fresh-gradle-red-final.log`、`recognition-request-types-red.log`。

**真实识图与生图调用均未执行。** 新授权 20 次的独立账本 `artifacts/wardrobe-evaluation-2026-10-18/call-ledger.json` 仍为空，剩余额度 20 次。未验证用户的模型开通、实际扣费、网络可达性、中文商品 OCR、假发/袜鞋识别质量或两家的效果排名。应在真实参考图和配置就绪后，按每次 HTTP 前写入账本、绝不自动重试的流程进行付费评估。
