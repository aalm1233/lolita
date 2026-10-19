# 购买资料提醒跟随价格显示

用户关闭价格显示时，不希望再看到购买资料未完善的提醒。已检查DataStore的`showTotalPrice`、首页和统计的StateFlow与共用`PurchaseReviewShortcut`；两处现有调用均没有消费该开关。

先归档[开启/关闭界面方案](../visuals/2026-10-19-review-price-visibility/design-flow.png)和[七皮肤浅暗方案](../visuals/2026-10-19-review-price-visibility/design-seven-skins.png)，再实现。首页四Tab和统计总览的整张核对提醒只在价格显示开启且待办大于零时出现，关闭后不保留空白占位；重开读取原待办，零待办仍隐藏。保留既有皮肤卡片和间距，小屏大字沿用原内容流，不增加输入或键盘场景。显示设置的说明同步覆盖这项联动。

只改变提醒显示，购买资料、核对状态、价格、实付、状态筛选、主动进入价格/核对页的操作均不变；不改DAO、Repository、ViewModel或持久设置协议。Room30/备份10及图片文件、引用、导出均无变化，不需迁移、历史刷新、备份往返或全库/全图片升级审计。不产生AI请求，无照片或额外隐私出口。

按影响范围选择共用卡片零/正数与开关切换、真实首页和统计的独立Room/DataStore实时联动回归；只归档受影响页面的七皮肤浅暗和小屏大字。执行Debug、clean正式构建、签名核对、正常覆盖启动和双平台附件/更新入口验证，不重跑无关全量JVM、设备或USB用例。版本2.57.1/123，交付结果如下。

## 实现与验收

共用提醒卡片新增显式价格可见性参数，两处调用直接使用原ViewModel的`showTotalPrice`，默认未读取设置时也隐藏。设置说明已更新。Debug及测试APK构建通过；6项定向仪器化用例通过，覆盖零/正数、关闭期间数量变化、首页四Tab、真实DataStore实时切换、统计恢复及隔离资料未改写。不执行无关JVM/仪器化/USB全量回归。

[真实开启/关闭对照](../visuals/2026-10-19-review-price-visibility/actual-before-after.jpg)、[首页七皮肤浅暗](../visuals/2026-10-19-review-price-visibility/actual-home-seven-skins.jpg)、[统计七皮肤浅暗](../visuals/2026-10-19-review-price-visibility/actual-stats-seven-skins.jpg)已核对。两页均为320dp与两倍字号，关闭后整张提醒及占位消失；开启后仍有原核对入口。截图只含本轮独立Room/DataStore夹具。首次截图发现测试宿主未同步系统浅暗参数且统计对照字号不同，已只修正宿主并重跑受影响的两项；最终应用代码未变化，不重复其他已通过用例。

## 正式交付

2.57.1/123已完成clean正式构建，源码`4be5d43d54b0802ed62ffa30b9753de66dca4d2a`和`v2.57.1`已推送；2.57.0/122同签名正常覆盖安装并冷启动通过，没有卸载或清数据。仅执行APK身份、签名、覆盖与启动检查，没有重复无关数据/图片/密钥全量升级审计。

GitHub与Gitee正式附件匿名完整下载、SHA256与HEAD通过，Gitee先核验再正式发布，未清理旧附件。电脑匿名公开查询HTTP200并选择2.57.1。正式应用一次手动检查仍显示“检查更新失败，请检查网络或前往 Gitee 查看”，具体原因未确认，应用内最新版本及正文未验证通过；不能以电脑公开查询成功替代。可[直接覆盖安装](https://gitee.com/aalm1233/lolita/releases/download/v2.57.1/lolita-2.57.1.apk)。完整记录见[发布回执](../review-price-visibility-release-2.57.1-verification.json)。
