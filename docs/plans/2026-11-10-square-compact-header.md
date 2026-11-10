# 广场去标题与紧凑分类（2.78.10 / 166）

用户反馈“广场那一行去掉，下面上新再贩等字体调小一点，现在挤到一起了”。列表、加载和空态删除整条广场顶栏，内容只保留状态栏安全区；商品详情、只读尾款和规格登记仍使用原标准标题与返回。搜索收起/展开、店铺/属性筛选和下拉刷新保持。

现有分类文字15sp/22sp且Tab文字有默认左右留白。分类改为13sp/20sp、原Medium字重与阅读字体，单行不折叠；自定义Tab内容只留每侧6dp，保留至少48dp高点按区。普通字号五类在搜索图标右侧等分，大字体沿现有横向滚动。仅调整本页面，不改全局Typography或其他页面文字。

实施前归档[主稿](../visuals/2026-11-10-square-compact/artist/story.svg)、[七皮肤浅色](../visuals/2026-11-10-square-compact/artist/light.svg)、[七皮肤暗色](../visuals/2026-11-10-square-compact/artist/dark.svg)。采用既有SearchModeBar、皮肤图标/指标和商品卡，不新增位图。新增“列表不设标题栏”的明确用户例外，保证状态栏不遮挡分类与搜索。

纯页面布局/文字调整，无Room34/45实体/备份16、配置、缓存、图片、查询分类、来源协议、权限或付费变化。无需迁移、旧备份处理或无关全库/图片SHA回归。原图缩略与完整查看管线保留；不访问淘宝，不改服务端或采集技能。

任务开始时原工作区有三份采集技能未提交修改，本轮使用独立worktree，源码/正式包均不带入这些修改。按影响验收：Debug构建、真实完整NavHost的标题缺席/状态栏/七皮肤浅暗、五分类完整文本和点按、详情返回、搜索IME与取消、小屏/大字。正式包从提交源码隔离clean构建并核对签名、素材，2.78.9覆盖两次启动、相关设置及实际MainActivity，按默认流程发布GitHub/Gitee并分别记录下载与更新接口结果。

实现、真实截图和正式发布结果见下方定向验收及最终交付。

首次真实文本检查捕获13sp在特定密度下的分数像素裁边；固定分栏让文字使用完整宽度居中，大字滚动分栏按固有文字宽度测量，保留完整字形。定向检查仍要求真实TextLayoutResult单行且无宽/高溢出，不放宽断言。常规与320dp两倍字的70次分类点按/文本检查分别归档；实际搜索/取消/两次返回、详情标题保留及空态无标题按本轮影响复用既有方法。

Debug/AndroidTest构建及5个去重定向设备方法按影响分批通过；最后字形宽度修正后仅重测受影响分类布局，常规与320dp两倍字各14皮肤模式共140次分类点按/无文字溢出检查通过。搜索、真实IME/取消/两次返回、详情标准标题、已有心愿与完整导航、离线空态通过。原生[常规分类](../visuals/2026-11-10-square-compact/normal-feed-seven-skins.jpg)、[常规搜索](../visuals/2026-11-10-square-compact/normal-search-seven-skins.jpg)、[大字分类](../visuals/2026-11-10-square-compact/small-feed-seven-skins.jpg)、[大字搜索](../visuals/2026-11-10-square-compact/small-search-seven-skins.jpg)共56状态归档；原图和阶段失败日志留本机artifacts/releases/2.78.10。

正式源码/标签`0fabdae5`已普通推送至远端master/v2.78.10。提交源码隔离clean Release含lintVital通过；原正式签名、37项目素材、24旧图片、依赖素材及生成profile核验。正式2.78.9覆盖两次冷启动、2份设置SHA及原服务配置缺席状态保持。正式MainActivity无列表标题行，五分类完整显示、搜索展开和取消收起通过；本轮未改私人数据结构，不重复无关全库或图片审计。

Gitee上传触及1GB配额，按持续授权仅清理当时最旧2.75.1 APK；GitHub摘要/匿名HEAD及三份本机完整副本SHA核验，旧页、校验、GitHub及本机副本保留，见[清理回执](../gitee-apk-cleanup-2.75.1-2026-11-11.json)。新版APK与校验已正式发布，正式前后下载和更新入口结果见最终交付。

## 最终正式交付

2.78.10/166源码/标签`0fabdae53f0fe281142de20b790691bd673ff41c`已普通推送；正式APK 84,038,041 字节，SHA256 `6c9ffcb09ef06eed14e2e4d9a4921c543e57f6dd146bfde7db9c0844a73aec65`。GitHub/Gitee正式同APK及SHA256SUMS匿名完整下载、摘要、HEAD与GitHub源标签核验通过。GitHub HEAD按45秒可用性预算，Gitee按原8秒预算，不改变App网络策略；真实App更新检查另列。Gitee公开版本API及延时复核403，实际App检查拒绝/频控，稳定APK可直下，不计检查更新通过。

正式App[无标题分类行](../visuals/2026-11-10-square-compact/release/official-collapsed.png)、[搜索](../visuals/2026-11-10-square-compact/release/official-search.png)、[详情标题与返回](../visuals/2026-11-10-square-compact/release/official-detail.png)、[实际更新结果](../visuals/2026-11-10-square-compact/release/official-update-check.png)归档。2.78.9覆盖两次冷启动、最终视觉后原设置及原无显式地址配置状态保持。无关全库/迁移/备份/私人原图审计未重复。

本轮在独立worktree完成，原工作区分支和三份采集技能修改SHA保持；其他任务随后提交的3314fe3a与远端合并b2f8c722已正常同步保留；只提交本次界面与验收文件。两台任务AVD关闭留数据，临时签名配置引用清除。[最终回执](../square-compact-header-release-2.78.10-verification.json)保留阶段失败、最终有效方案及分发/更新入口边界。
