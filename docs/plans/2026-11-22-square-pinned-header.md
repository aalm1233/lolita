# 广场顶部栏固定（2.78.14 / 170）

用户反馈广场顶部栏没有冻结。现有 `SquareBrowseFeed` 把搜索、四分类、筛选和已选标签放在 `LazyVerticalGrid` 首项，滚动商品时整块离开视口，后续搜索和切换分类需要先回到顶部。

本次只修正滚动归属：外层纵向布局先放原操作栏，再放占据剩余高度的商品网格。操作栏与已选筛选标签固定在已消费的状态栏安全区下面，商品在其下方独立滚动，无覆盖层，不需要为背景新增色块或毛玻璃。首页同高的字号、Tab最小34dp、图标32dp、上下2dp及皮肤指示器保持。原先首项后的12dp间距改由网格顶部内边距承接。搜索展开时仍保留筛选与取消，详情返回、分类/搜索/筛选上下文及下拉刷新沿用原流程。

实施前沿用已认可构图绘制[滚动前后主稿](../visuals/2026-11-22-square-pinned-header/artist/story.svg)、[七皮肤浅色](../visuals/2026-11-22-square-pinned-header/artist/light.svg)、[七皮肤暗色](../visuals/2026-11-22-square-pinned-header/artist/dark.svg)，图稿仅说明构图与滚动归属，实际皮肤图标仍以原生provider为准。大字分类仍横向滚动；小屏和真实IME覆盖固定栏及列表的可用高度；筛选标签自然换行，不裁文字。

只改变列表排布，不新增业务功能。不涉及Room34/45实体、备份16、持久设置、服务配置、公开目录、网络协议、图片缓存或引用/清理、私人资料、账本、AI输入和费用，无迁移或新增权限。商品缩略图继续完整Fit，详情/放大不改。本轮无淘宝访问或付费调用，不重复无关全库/图片SHA/备份审计。

验收：以真实商品滚动验证操作栏坐标恒定及首批商品离开视口；滚动后操作搜索/分类/筛选，已选标签可清除，空态可恢复；验证七皮肤浅暗、320dp两倍字、真实IME及详情返回。先用旧实现复现失败，再验证修复；Debug、隔离提交源码clean原签名Release、2.78.13覆盖两次启动、素材与双平台正式附件、实际更新入口分别记录。使用独立AVD，结束关闭并保留数据。

旧正式2.78.13的实际MainActivity已先行复现：商品向上滚动后，原搜索/筛选的UI语义节点均离开视口；滚动前后实拍与UI树留在本机发布工件。修复已将操作栏移到网格之外。定向滚动夹具仅在独立内存库/公开缓存中复制8条公开动态，提供足够长列表，经原合同校验，不修改服务端或私人记录；普通搜索/筛选回归仍使用原公开资料。原生固定Tab不再具有滚动祖先，用例仅对大字可滚动Tab执行横向滚动，保留文字完整性和高度断言。

实施与定向验收已完成：Debug与AndroidTest构建通过，普通1080×2400@420dpi五项、320dp（840×1800@420dpi）两倍字三项，共5个去重方法通过。七皮肤浅暗的商品首项离开视口后，操作栏顶部和高度保持在1px容差内；滚动后的搜索与真实IME、筛选、详情返回、空态已选店铺清除通过。完整NavHost保留真实Haze，首页与广场分类高度/顶部一致、四类点按及单行文字完整性均通过。两种尺寸各归档30个滚动/IME/空态和14个完整导航状态，共88个原生状态。

真实对照：[普通滚动前](../visuals/2026-11-22-square-pinned-header/normal-before-seven-skins.jpg)、[普通滚动后](../visuals/2026-11-22-square-pinned-header/normal-scrolled-seven-skins.jpg)、[大字滚动前](../visuals/2026-11-22-square-pinned-header/small-before-seven-skins.jpg)、[大字滚动后](../visuals/2026-11-22-square-pinned-header/small-scrolled-seven-skins.jpg)、[普通真实IME](../visuals/2026-11-22-square-pinned-header/normal-search-ime.png)、[大字真实IME](../visuals/2026-11-22-square-pinned-header/small-search-ime.png)。原图在各皮肤下继续Fit；固定栏与网格纵向分离，滚动内容不覆盖操作文字。

首轮因本机依赖缓存缺失补下载，阶段日志保留；最终源码构建通过，无生产代码编译失败。正式提交归档clean Release与覆盖发布验证已完成。

正式源码/标签`b876d0e0`已普通推送至master/v2.78.14。提交归档clean原签名Release含lintVital通过，正式包84,054,421字节，SHA256 `1a4b3194f1079d9fcc08c7d24f383723c0ee4ef461d18164313612007e25e86b`；37项目素材、24旧图片、依赖与生成profile核对通过。2.78.13覆盖两次冷启动通过；3份设置与原服务地址缺席状态在正式实页和实际检查更新后保持。无数据库/备份/图片管线变化，不重复无关全库/图片/备份审计。

正式MainActivity的[滚动前](../visuals/2026-11-22-square-pinned-header/release/official-before.jpg)、[连续滚动后](../visuals/2026-11-22-square-pinned-header/release/official-scrolled.jpg)、[筛选](../visuals/2026-11-22-square-pinned-header/release/official-filters.jpg)与[搜索/IME](../visuals/2026-11-22-square-pinned-header/release/official-search-ime.jpg)已核对，搜索与筛选实际触摸坐标滚动前后完全相同；向下手势可操作，不把此项当作公网刷新或耗时测量通过。

GitHub/Gitee正式同APK和校验文件匿名完整GET/SHA/HEAD、源标签及Gitee公开最高版本通过，实际App当前/最新均为2.78.14，完整更新说明正确。上传遇网络失败后，仅调整本机发布工具传输/上传时间预算并接续现有草稿，没有修改App网络代码或已发布标签。Gitee实际1GB配额400后，仅按持续授权核验GitHub全量下载和三份本机完整副本，再清理最旧2.78.1 APK，保留旧页/校验/GitHub与本机副本，见[清理回执](../gitee-apk-cleanup-2.78.1-2026-11-22.json)。最终结果见[发布核验](../square-pinned-header-release-2.78.14-verification.json)。

两台任务AVD均经正常请求退出并保存快照，宿主返回0，数据保留；临时签名引用已移除。原工作区分支与未跟踪文件保留，正常纳入并保留同期已发布的采集规范文档提交。
