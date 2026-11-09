# 本机SQLite入库与API验收

主助手在关键图文语义复核、`workflow.py package`成功后执行。本步骤写的是独立公开目录库，不是Android Room、衣橱或采集基线；dsh只采集，不操作数据库。API只读，不承担采集或发布。公开数据入库后会由现有广场服务对App提供，不发送原始页面、OCR、日志、账号或私人资料。

## 选择本轮包和现有服务

- 从本轮`request.json`的`appPackage.project`确定工程，使用本轮`app-package/`的正式ZIP及同名`.quality.json`，不选择`.candidate`或其他轮次旧包。核对质量报告`passed=true`、实际Android合同验收成功及ZIP的SHA256等于报告`sha256`；结构通过后仍须完成主助手的关键图文语义复核。
- 现有服务默认数据目录是`~/Library/Application Support/LolitaSquare/`，库文件`square.sqlite3`，本机API为`http://127.0.0.1:18765`。先查看工程`service_control.py status`及现有LaunchAgent的启动参数；若显式指定`--data`或端口，以实际服务配置为准。入库与API必须指向同一数据目录，不能另建一个空库冒充发布。
- 默认复用已运行API，不为每轮采集重新安装/重启LaunchAgent或修改花生壳。服务未运行时仍可向已确认的数据目录入库，但必须报告API验收未完成；服务配置不明时停止写入，报告需要确认的数据目录。用户明确只要离线ZIP时不入库。

## 本地入库

在已确认工程根目录执行，参数使用本轮实际绝对路径；命令行全局`--data`必须放在`ingest`前：

```bash
uv run --offline --with pillow python scripts/square/lan_service.py \
  --data "$HOME/Library/Application Support/LolitaSquare" \
  ingest --package "/本轮目录/app-package/本轮正式资料包.zip"
```

`ingest`会再次检查实际Kotlin合同、ZIP路径/SHA和图片解码，写入SQLite/WAL、原图及缩略图。不得跳过合同校验、手工拼SQL、删库重建、改revision或放宽冲突校验来通过。沿用本机Java/Pillow环境，缺依赖时报告实际阻塞，不改全局权限或凭据。

- 将命令退出码和成功返回的`changed/cursor/products/assets`保存为本轮`ingest.json`，附包SHA和实际数据目录；失败也留明确结果，不能留下看似成功的空文件。原始stderr只保留本地，不放进公开目录。
- 相同revision与内容重复入库不新增变更，旧revision不覆盖新revision，未出现的旧记录保留。同版本冲突或陈旧listing证据必须停下核对，不能自动清空服务端数据。
- 入库按动态及关联商品短事务提交，**不是整包原子事务**。后续动态失败时，前面已提交项可能保留；报告“部分可能已入库”，核查已提交内容后再处理失败项，不承诺整库回滚。校验全部源图失败发生在发布业务引用前，但不要据此承诺磁盘完全没有新增缓存文件。
- `package`已成功推进采集基线、但入库失败时，保留本轮ZIP与质量报告用于补交，不回退`current.json`，也不为修复入库重新采集。已核对为暂时性失败时可重试同一正式包一次；同样错误再次出现就停止，报告原因与待补交包。内容冲突先修正来源并走正常打包revision，不盲目重试。
- 即使`businessChanged=false`也要完成本轮入库和验收：服务端可能尚未收录，或上轮入库失败。`changed=0`只代表此次没有新增变更，不代表错误；不据此声称本轮有新品。

## 只读API验收

只请求已确认的本机广场API，禁止请求淘宝。记录到本轮`api-verification.json`：`passed`、检查时间、`serverId`、本轮包SHA、入库cursor、已核对的动态/商品ID与revision、原图摘要结果，以及失败阶段；无需保存完整API响应或原始采集证据。

1. 读取`/v1/health`，检查`format=lolita.square.api`、`schemaVersion=1`及稳定`serverId`。读取健康检查不等于目录更新成功。
2. 按`/v1/events?limit=100`的`next`继续分页，核对包中每个动态存在、revision不低于包中值；相同revision内容应一致，服务端更高版本标为“已存在更新版本”，不要求回退。
3. 对包中商品逐一读取`/v1/products/{id}`，核对商品身份、revision及相同revision完整内容（按解析后的JSON比较，忽略对象键顺序）。检查SKU、图片归属和listing仍在，不能只看返回200。
4. 读取`/v1/square/changes?after=<ingest返回cursor>&limit=10`，验证API接受该游标、`serverId`与健康检查相同，返回cursor不低于入库cursor；并发写入可能使游标更高。它不替代上述当前商品/动态检查。
5. 对本轮新增/变化商品涉及的原图至少取一张；无变化时取包中一张。GET `/v1/images/{sha}.{ext}?variant=original`，核对实际字节数、SHA256与清单一致，不能用缩略图代替原图。记录抽查数量，不宣称逐图API下载全检。

API暂时不可达时允许一次只读复查，仍失败就停止并报告“入库成功，API验收失败/待验”；不反复入库、不重启穿透、不回滚数据。入库失败时不能用API仍能查询旧资料证明本轮成功。公网穿透和手机App实测不在每轮入库的默认验收中，不能将本机API成功写成手机已更新。

最终报告分别给出“采集/打包、SQLite入库、API验收”的状态，附资料包、质量报告和两份回执路径。采集时间变化不是新品；未采到、页面失败或部分缺货不推断下架。此流程不创建或修改定时任务。
