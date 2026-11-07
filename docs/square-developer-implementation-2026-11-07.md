# 广场内置地址与开发者工具

采用[前置设计](plans/2026-11-07-square-developer.md)：内置 `http://192.168.0.104:18765`，已有覆盖配置（包括空地址停用）保留。“使用内置地址”只填写，保存才查询。失败继续静默，衣橱、心愿及其他页面不依赖目录服务。

清空入口：广场齿轮 → 开发者工具 → 清空本机广场资料 → 确认。操作仅处理 `files/square_catalog` 下的公开资料、原图、缩略、暂存及游标；不会删除Room、私人UUID图片、服务地址或服务端数据。整个目录先移动到独立回收目录，再删除；下次启动重试清理崩溃遗留回收目录。登记/收藏与清空采用一致的锁顺序，等待在途图片完成后切换，旧目录响应通过世代失效。清空后取消页面临时筛选和选择，持久登记草稿保留，下次刷新从零同步。

Room34、45实体、备份16保持；没有新的素材、照片上传、采集任务、花生壳或付费调用。不会替用户直接清掉手机数据，清空由用户在App中主动确认。

艺术主图和七皮肤浅暗图先于UI代码归档，复用现有皮肤字体、Material弹窗与按钮。真实Compose、320dp两倍字与键盘对照见本次 `docs/visuals/2026-11-07-square-developer/native/`；图稿较疏的弹窗留白由原生内容自适应收紧，确认后显示既有空态，无额外成功弹窗。

原生七皮肤浅暗：[设置](visuals/2026-11-07-square-developer/native/default-normal.jpg)、[开发者工具](visuals/2026-11-07-square-developer/native/tools-normal.jpg)、[清空确认](visuals/2026-11-07-square-developer/native/clear-normal.jpg)；320dp两倍字：[设置](visuals/2026-11-07-square-developer/native/default-small.jpg)、[工具](visuals/2026-11-07-square-developer/native/tools-small.jpg)、[确认](visuals/2026-11-07-square-developer/native/clear-small.jpg)。[小屏真实IME](visuals/2026-11-07-square-developer/native/small-service-real-ime.png)、[清空后空态](visuals/2026-11-07-square-developer/native/normal-cleared-empty.png)和[90张最终原始截图SHA](visuals/2026-11-07-square-developer/native/screenshots.json)单列。

小屏真实IME检查发现新增入口使原服务弹窗过高，已按既有ApiKeyDialog模式增加IME避让，内容滚动而保存/取消始终留在键盘上方。设备用例核对弹窗实际屏幕边界，并注入真实触摸保存；未仅凭Compose语义可见性认定通过。

27项定向JVM、7项仓库设备方法和3项UI方法覆盖配置兼容、已导入ZIP清空、心愿/草稿/图片保全、在途目录/图片、断网静默、七皮肤浅暗、小屏两倍字与键盘。

验证及正式发布结果记录于 `docs/square-developer-release-2.77.0-verification.json`。完整截图和私人资料逐表/图片核验中间产物仅留本机，不发布用户备份或私人数据。
