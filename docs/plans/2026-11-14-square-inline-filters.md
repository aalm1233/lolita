# 广场分类与筛选同列（2.78.12 / 168）

用户要求广场Tab高度与首页一致，去掉“全部”页，将筛选提到Tab同高度，并将店铺过滤放入筛选。采用搜索图标、上新/再贩/现货/尾款四分类、筛选图标的单行布局。默认进入上新，旧页面空分类恢复为上新，已选其他分类仍保留。搜索展开沿用首页SearchModeBar与取消行为，右侧保留筛选，支持查询与筛选组合。

实施前以艺术家角色绘制[主稿](../visuals/2026-11-14-square-tabs/artist/story.svg)、[七皮肤浅色](../visuals/2026-11-14-square-tabs/artist/light.svg)、[七皮肤暗色](../visuals/2026-11-14-square-tabs/artist/dark.svg)。沿用首页labelMedium、最小34dp Tab、32dp图标与上下2dp行边距、皮肤指示器，无底部分隔线；移除列表顶部额外12dp空白，使分类纵向位置也与首页一致；大字号分类可横向滚动。对照真实页面检查常规、小屏大字和键盘。

店铺入口位于筛选面板内，沿用可搜索选择框，选择后回到筛选面板。筛选入口计数包含店铺、内容、颜色、风格。已选店铺与属性在列表以可移除标签反馈；清除属性保留店铺、动态与搜索，店铺可独立切回全部店铺。结果为空时仍可清除店铺筛选。筛选完成区保持键盘上方可达。

仅改页面排布与页面分类默认值，不改服务端、采集、SQL、Room34/45实体/备份16、图片下载/裁切/Fit/放大/引用、私人资料、持久服务配置、账本或付费流程，无数据迁移或新增权限。无真实生图调用或淘宝访问。不重新执行无关全库/图片/备份审计。原工作区只有既有未跟踪文件，本轮在独立worktree实施和打包，原分支与文件保留。

验证范围：Debug及定向分类/筛选/搜索设备回归、七皮肤浅暗常规及320dp两倍字、真实IME、完整NavHost首页高度对照与详情返回；按默认发布流程提交源码、隔离clean原签名Release、覆盖启动、素材核对、双平台附件与检查更新分别记录。

实施与定向验收：Debug和AndroidTest构建通过。6个去重设备方法覆盖店铺选择/真实IME/筛选计数/清除上下文/旧空分类恢复、搜索取消与详情返回、SKU筛选一致性，以及完整NavHost七皮肤浅暗分类高度/顶部位置/筛选纵向中心实测。常规1080×2400@420dpi与320dp（840×1800@420dpi）两倍字号均通过；两组各56次四分类点按及单行无溢出检查。

真实页面按常规/小屏各归档70状态，含分类、搜索键盘、已选店铺、筛选面板、店铺选择框，共140状态。[常规分类](../visuals/2026-11-14-square-tabs/normal-feed-seven-skins.jpg)、[常规筛选](../visuals/2026-11-14-square-tabs/normal-filters-seven-skins.jpg)、[大字分类](../visuals/2026-11-14-square-tabs/small-feed-seven-skins.jpg)、[大字筛选](../visuals/2026-11-14-square-tabs/small-filters-seven-skins.jpg)与同目录搜索/店铺对照板可查看。初次回归发现详情返回搜索态丢筛选入口，最终保留搜索栏右侧筛选并复测通过；首次默认内存构建失败，后续构建通过，阶段日志留本机。未放宽断言，未重复全量测试。

无数据库/备份/图片管线变化，相应迁移与全库/原图审计不适用。源码/标签`e5c5b788`已普通推送至远端master/v2.78.12。提交源码归档隔离`clean assembleRelease`及lintVital通过；正式包84,054,421字节，SHA256 `d1fa11443f7c61bdde3205183957993fcc3c1af15b3749479d6f68c01a6903d2`，原签名、37项目assets、24旧图片、两项依赖素材与两份生成profile核验。正式2.78.11覆盖安装及两次冷启动通过；2份设置与原服务配置缺席状态在实页及检查更新后保持。不重复本轮未改的全库/图片/备份审计。

正式MainActivity[分类](../visuals/2026-11-14-square-tabs/release/official-feed.png)、[筛选](../visuals/2026-11-14-square-tabs/release/official-filters.png)、[店铺选择](../visuals/2026-11-14-square-tabs/release/official-shops.png)、[详情](../visuals/2026-11-14-square-tabs/release/official-detail.png)、[搜索](../visuals/2026-11-14-square-tabs/release/official-search.png)、[实际更新](../visuals/2026-11-14-square-tabs/release/official-update-check.png)核对完成；首页与广场文字纵向范围实测均为162–210px，搜索展开保留同列筛选。本轮不将服务端缓存变化或联网刷新计为专项验收。

Gitee触及1GB配额，按持续授权核验GitHub摘要/匿名HEAD及三份本机完整副本后，仅删除最旧2.77.0 APK，旧发布页/校验/GitHub/本机副本保留，见[清理回执](../gitee-apk-cleanup-2.77.0-2026-11-14.json)。Gitee正式同APK匿名完整GET/SHA/HEAD与公开最高版本查询通过；实际App当前与最新均为2.78.12，完整更新说明通过。GitHub初次上传停滞，保留草稿后经本机系统代理HTTP1上传完成；正式同APK与SHA匿名完整GET/SHA/HEAD和源标签核验通过。双平台一致，见[最终回执](../square-inline-filters-release-2.78.12-verification.json)。

两台本轮启动的AVD已关闭并保留数据。正式AVD在新APK安装前出现宿主退出134，保留数据恢复后完成原包核验、覆盖启动与实页验收；没有观察到新版App崩溃。所有临时签名引用已清除，原工作区分支及未跟踪文件保留，并已快进同步本次源码。
