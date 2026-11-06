# 广场内网服务实施记录

本轮采用用户确定的架构：Codex定时任务调度dsh采集，Codex只读核验后调用本地工具增量写SQLite；API只提供资料查询和图片。App每次前台进入及下拉刷新查询；连接失败静默保留已有内容。没有操作淘宝、配置花生壳、新建定时任务或调用付费服务。

## 实现

- 服务：`lan_service.py`和本地用户常驻服务管理；独立SQLite/WAL、店铺/商品/动态/图片索引/变更快照；逐动态事务、不整批切换；版本去重、旧内容保全、下架状态证据约束。GET/HEAD明确业务接口，写方法405、其他路径404，不泄露本机路径或原始采集证据。
- App：`SquareNetworkClient`、增量目录与游标SHA、短超时、不跟随重定向、并发合并；网络目录允许未下载原图，离线ZIP仍严格验证。`SquareImageFetcher`仅处理广场URI，沿原Coil其余管线；列表缩略、详情/放大原图，三路下载并发、SHA/尺寸验证及持久缓存；收藏复制所需完整原图后沿原UUID与事务写入。
- UI：保持浏览优先布局，标准顶栏增加低频服务地址设置，支持清空停止联网；下拉刷新和首用空态均可用。服务地址仅本机noBackup；所有网络失败与不完整响应都不进入UI消息流、不影响衣橱。显式本地文件损坏仍维持原校验提示。
- 参数清单的JsonObject中14个商品存在显式null，原Gson默认落盘会丢空值。真实重启回归发现后改为仅缓存使用serializeNulls，验证重新读取与原对象完整一致；共享报价、JSON/ZIP备份协议未改。

Room34、45实体、备份16不变；私人来源/账本/已拥有状态及原图不自动改写。后续采集入口命令与接口契约见[操作说明](square-lan-service.md)。代码默认不包含内网主机地址，用户在广场设置填写，之后可直接更换HTTPS。

## 前置设计与原生对照

前置[设计](plans/2026-11-06-square-lan-service.md)、[关键状态SVG](visuals/2026-11-06-square-lan/artist/main.svg)、[七皮肤SVG](visuals/2026-11-06-square-lan/artist/skins.svg)先于应用实现归档。沿用原七套皮肤组件与图标，不新增换色图标系统。

真实原生完整窗口截图来自独立AVD `Lolita_Square_LAN_20261107`，由仪器化状态标记与外部adb截图逐张确认。Compose根节点截图不能完整覆盖Dialog/IME，因此最终改用完整窗口截图，不将早期裁切图当最终验收。

- [七皮肤浅暗动态页](visuals/2026-11-06-square-lan/native/feed-normal.jpg)、[服务设置](visuals/2026-11-06-square-lan/native/service-normal.jpg)
- [320dp两倍字动态页](visuals/2026-11-06-square-lan/native/feed-small.jpg)、[服务设置](visuals/2026-11-06-square-lan/native/service-small.jpg)
- [真实IME](visuals/2026-11-06-square-lan/native/normal-service-real-ime.png)、[小屏真实IME](visuals/2026-11-06-square-lan/native/small-service-real-ime.png)、[无缓存离线](visuals/2026-11-06-square-lan/native/normal-empty-offline.png)
- [56张矩阵源图SHA](visuals/2026-11-06-square-lan/native/screenshots.json)

## 已完成定向验证

- 真实内网IP连接（非adb reverse、非模拟器专用10.0.2.2），六店28商品/14动态/777SKU/986原图；实际Kotlin合同及全部原图解码/SHA入库，重复入库changed=0。
- 6项Python回归：幂等/更新/旧版、冲突回滚、坏图拒绝、缺席保全、WAL读写、只读SQL连接、业务路由/方法/参数、游标历史快照、缩略原图摘要、下架证据保全。
- 27项JVM：SquarePackage13、SquareBrowse12、NetworkConfig2；Debug及仪器化APK编译通过。
- 7项新设备方法：真实接口首拉/并发/重启、缩略与完整原图及独立心愿、增量更新/坏游标/坏图、坏JSON/离线缓存静默、无缓存离线下拉、服务设置验证/取消/真实IME、七皮肤浅暗；320dp两倍字3项UI方法通过。
- 原4项设备回归覆盖离线ZIP导入、并发心愿、事务回滚/中断清理、备份16往返、坏图与陈旧选择。原本地坏图回归发现预下载错误类别变化，已恢复原IllegalArgumentException校验语义并重跑通过。

## 发布验证

源码 `707cb8227066f5a2f9fb09aa3b9a884c5b9e06b8` 已集成并普通推送master，标签v2.76.0指向同一提交；隔离worktree以仅提交源码执行clean Release，2.76.0/154原签名、非调试、37项目assets/24旧图片字节一致。APK SHA256为 `1e8ddde8f6cfee20be1fee9595747a385afc65c88ef61b9bcf845b10cdef02cd`。

在独立正式AVD从2.75.0原签名覆盖，使用固定current.json指定真实备份建立基线；两次冷启动45表所有字段/关联、160原物品、629原图片及设置摘要完全保全。正式App真实LAN获取资料及缩略、保存漫游JSK心愿成功：仅Item/来源各新增1条，原160物品所有字段、原629图与账本不变。实际停止API后冷启动319ms、前台返回26ms，广场缓存及下拉、首页导航正常，无网络错误弹窗；测试后恢复API常驻。

正式AVD宿主在启用Vulkan时崩溃；保留同一AVD数据，改为host GLES冷启动并关闭崩溃报告弹窗后完成上述回归，没有清数据、重装绕过签名或将宿主故障记成App通过。真实已签名[在线](visuals/2026-11-06-square-lan/native/release-online.png)、[离线](visuals/2026-11-06-square-lan/native/release-offline.png)、[加入心愿](visuals/2026-11-06-square-lan/native/release-saved.png)截图已归档。内网服务已启动，公网穿透尚未接入；双渠道附件与更新入口以最终发布回执为准。


Gitee本轮配额不足，按2026-10-28既有授权，只清理最旧2.67.0 APK附件。清理前已重新匿名完整下载GitHub旧包并核对SHA，另核对三份本机完整副本；旧发布页、SHA256SUMS与GitHub附件保留。正式Gitee新APK与校验附件匿名全量下载、SHA及HEAD通过，实际App手动检查返回Gitee拒绝访问/限流，不能宣称自动更新检查通过；未重复重试或改动更新模块。

最终GitHub/Gitee正式同一APK与SHA256SUMS匿名完整下载、SHA与HEAD均通过；GitHub最高正式版本与源码标签指向核对通过。GitHub首次HEAD临时网络失败，仅重试HEAD后通过，没有重复无关测试。完整结果见[发布回执](square-lan-release-2.76.0-verification.json)，Gitee旧附件清理见[回执](gitee-apk-cleanup-2.67.0-2026-11-07.json)。两台本次测试AVD均已关闭并保留数据，临时签名引用已移除；API本机用户常驻服务已恢复运行。
