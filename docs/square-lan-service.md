# 广场内网服务

服务只读提供商品资料，不上传衣橱、订单、账号、采集证据或备份。花生壳尚未接入；目前用同一局域网地址访问。

## 启动与入库

在项目根执行（复用本机 uv/Pillow 环境）：

```bash
uv run --offline --with pillow python scripts/square/lan_service.py ingest --package /绝对路径/已核验的广场资料.zip
python3 scripts/square/service_control.py start
python3 scripts/square/service_control.py status
```

`start` 安装并启动本机用户 LaunchAgent，登录后运行，持续提供已有资料；不创建 Codex 定时采集任务。停止并移除启动项：`python3 scripts/square/service_control.py stop`。前台临时运行：`uv run --offline --with pillow python scripts/square/lan_service.py serve --host 0.0.0.0`。

默认端口18765；数据库、私有原图和缩略存放于 `~/Library/Application Support/LolitaSquare/`，日志在 `~/Library/Logs/LolitaSquare/`。数据库启用WAL；API连接为只读，每次请求独立事务，不通过HTTP接收SQL、发布、文件路径或采集命令。LaunchAgent与数据不提交Git。

入库入口沿用已核验 `lolita.square` v1：先通过实际 Kotlin 合同检查、ZIP路径/SHA/图像解码，再串行逐动态及关联商品事务入库。不是整批版本发布；写完一项即可查询，其他已完成项保留。相同revision相同内容无新增变更，相同revision冲突拒绝，旧版本不覆盖新版本；未出现记录保留，已有下架证据不允许被缺失/陈旧状态复活。每项变更保存提交时的关联快照，游标顺序与事务一致。

后续Codex定时任务链路：调用既有 `taobao-shop-updates` 技能，prepare→dsh→主助手只读核验→package；对本轮通过核验的公开包调用上述 `ingest`。任务失败不修改旧数据；不将原始页面/OCR/账号/日志写入公开字段。本轮没有新增定时调度。

## App连接

2.77.0起内置 `http://192.168.0.104:18765`。无已存配置时自动采用，已有自定义地址或明确保存的空地址继续保留。广场顶栏齿轮「广场服务」可修改；“使用内置地址”只填入默认值，保存才生效。每次App回到前台及广场下拉刷新再次查询；已有内容先展示，网络不阻塞衣橱和导航。地址清空则停止联网，缓存保留。地址仅存本机noBackup目录，不进入衣橱备份；后续可以直接换成HTTPS根地址。电脑IP变化时应在该入口修改地址。

同一配置弹窗内「开发者工具」→「清空本机广场资料」提供二次确认，仅删除本机广场目录、图片缓存、导入暂存及同步游标；保留服务地址、衣橱、心愿、登记草稿和服务端数据。已保存物品及草稿使用独立UUID原图。清空后显示空态，下次下拉或进入应用从零拉取；正在进行的旧目录请求不会回填，图片请求完成后才切换缓存目录。清空不等同于删除服务端商品。

HTTP只接受私有IPv4、回环地址与localhost；其他地址使用HTTPS。地址不能带用户名、密码、路径、查询或fragment。App不跟随接口重定向。服务地址变化或数据库身份变化会重置游标、重新合并；不会清空旧资料或个人心愿。

连接/超时/响应校验/图片下载失败只写类别日志，不弹Snackbar、Toast、弹窗或通知，不清空缓存。首次无缓存时保持正常空态和本地导入入口。显式保存心愿所需原图未就绪时不写入半成品，结束忙碌并保留选择；缓存完整时可离线保存。

列表按需获取600×800以内WebP缩略；详情/放大按需下载原图并核对SHA、大小及尺寸。原图与缩略持久缓存分别存放，不把缩略复制为私人物品图片；心愿仍复制独立UUID原图进入原备份16。公开网络缓存允许缺少未浏览图片，原ZIP仍严格全图校验。网络累计缓存有独立的数量/64MiB清单保护，不沿用ZIP的600MiB原图总量上限。

## API v1

| GET路径 | 结果 |
|---|---|
| `/v1/health` | 协议和数据库稳定serverId |
| `/v1/square/changes?after=0&limit=10` | 递增seq、提交时商品关联快照、cursor与hasMore |
| `/v1/shops?after=&limit=30` | 店铺分页；after为上一页最后ID |
| `/v1/events?after=&limit=30` | 动态分页 |
| `/v1/products/{id}` | 完整规格/部件/参数/资料与售卖状态 |
| `/v1/images/{sha}.{ext}?variant=thumbnail` | 缩略图，X-Content-SHA256 |
| `/v1/images/{sha}.{ext}?variant=original` | 原字节图片，摘要与资料清单一致 |

其他路径404；写方法405；错误只含固定业务错误码，不返回路径、SQL或异常链。API无私人数据写入口。App在本地应用每页更新成功后才保存游标，目录SHA不匹配则从0重放；不会把部分失败当作同步成功。

数据库目前针对单机一个写入工具、多只读请求；花生壳接入、互联网访问容量及外部连接实测属于下一阶段。
