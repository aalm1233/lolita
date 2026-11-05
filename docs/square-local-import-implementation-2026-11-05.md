# 广场本地资料包实施记录

版本2.72.0 / 148，2026-11-05。采用范围见[本轮设计](plans/2026-11-05-square-local-import.md)，[格式文档](square-package-format.md)。首包仅使用既有本地dsh资料，无淘宝浏览或请求。

已实现五项底栏中的广场、本地ZIP读取与全图校验、预览/取消/导入、原子目录发布及幂等合并。按上新/再贩/现货/尾款、店铺、搜索、颜色、风格与购买内容筛选，属性须同时命中同一实际SKU。详情可切换商品链接和主图/规格/详情/促销角色，完整Fit、全部缩略与全屏缩放；超50张不截断。

登记明确选择实际SKU，复用服饰编辑器核对后保存为心愿。整组或只登记一个明确部件；分件未知颜色尺码留空。全款与定金的已核实同款规格共用variantId查重，先前草稿继续编辑、已保存物品直接打开。来源原规格、条款、报价阶段和商品资料保留为可读说明。图片独立复制到UUID私有文件。无自动账本、实付、收纳、衣橱套装或主件小物关联。原子多件登记与云端同步留待后续，不表示原广场设计全部能力都已上线。

首包：三店、10动态、14商品链接、542个完整SKU、732个图片引用/421张去重原图，270,998,822字节（约258.4MiB）。SHA及三份规范数据来源摘要见[first-package-receipt.json](visuals/2026-11-05-square-import/first-package-receipt.json)。包不含跳转截图、原始SSR、账号或私人资料。三组已核实的定金链接将明确购买条款与XS/S/M/L等尺码分离，原规格原文不变。

前置图稿 [design.png](visuals/2026-11-05-square-import/design.png) 先于实现。真实Compose浅暗对照：[列表浅](visuals/2026-11-05-square-import/native-feed-light.jpg) / [列表暗](visuals/2026-11-05-square-import/native-feed-dark.jpg)、[详情浅](visuals/2026-11-05-square-import/native-detail-light.jpg) / [详情暗](visuals/2026-11-05-square-import/native-detail-dark.jpg)、[规格浅](visuals/2026-11-05-square-import/native-selection-light.jpg) / [规格暗](visuals/2026-11-05-square-import/native-selection-dark.jpg)。[320dp两倍字真实IME](visuals/2026-11-05-square-import/native-small-double-font-real-ime.jpg)和[新路德维希第二链接](visuals/2026-11-05-square-import/native-neo-second-link-small.jpg)均可操作。

采用既有GradientTopAppBar/LolitaCard/LolitaSection/SectionHeader/ImageFrame/EditorSaveBar/全屏图片组件。新增Square店面图标补Base及七套实际provider，原52键保持，53键在Android中通过边界、调用尺寸、tint/透明度及不同皮肤主体差异检查；哥特尖顶按实际小尺寸收束，消除miter越界。

数据：公开目录独立文件，不升级Room34和备份15。保存的ItemDraft、Item.importToken、来源说明和UUID图片复用既有JSON/ZIP及恢复校验，公开目录由原包重新导入。已验证新登记完整54张详情图的ZIP导出/预览/恢复及图片保全，目录更新不修改私人物品。无AI或付费调用，无新增权限或上传。

定向验证：9项JVM包格式/哈希/路径/引用/版本/合并/SKU筛选；SquareImportTest真实首包的重复/坏包保全、心愿草稿及备份、全款/定金查重、第二链接、七皮肤浅暗和320dp两倍字真实IME；SquareNavigationTest真实五底栏与系统文件选择器；SkinIconVisualTest真实53键×七provider。Debug构建及上述定向方法通过。独立AVD `Lolita_Square_20261105`，正常1080×2400/density420，窄屏density540/320dp/fontScale2。

既有CreativeNavigationScreenTest的旧订单指南文案断言尝试时失败（未找到其期待的「如何获取订单文件」），未修改该无关页面；本轮改用新增的真实广场导航/系统选择器定向用例并通过。早期测试的坏ZIP异常类别、未组成的Lazy项定位及图标尖顶问题均已纠正并复测。

正式交付：源码`4a4bf3fa`和标签`v2.72.0`已普通推送。提交归档clean Release、原正式签名及37项目素材字节核验通过，七背景编译PNG与2.71.1逐字节一致（aapt对源码PNG有原有透明RGB量化，未冒充源码RGBA完全相等）。APK83,841,429字节，SHA256 `67989240e668227c6d3807fa25f3fb256c4b980bbed5a39d92ffbee0a1da581e`。

独立正式AVD从2.71.1原签名覆盖，使用current.json指向的160物品/619图片基准，通过原备份页实际恢复。两次冷启动保全45张表全部行、629个实际私有图片文件SHA（含恢复前内置图片）和全部SharedPreferences。正式包系统选择器导入本次ZIP并保存真实M码心愿，物品160→161，54张完整详情图独立保留，原图、原Price和Payment均不变。重启仍可浏览目录。[正式广场](visuals/2026-11-05-square-import/native-release-square.jpg)、[正式导入预览](visuals/2026-11-05-square-import/native-release-import-preview.jpg)、[原编辑器核对](visuals/2026-11-05-square-import/native-release-editor.jpg)。

GitHub/Gitee正式APK与SHA256SUMS匿名完整下载、摘要及HEAD一致，GitHub源码标签和Gitee公开最高版本2.72.0通过；正式App一次手动检查显示当前/最新均为2.72.0。首次Gitee上传触及1GB配额后，按持续授权仅清理最旧2.64.0 APK，核验GitHub及三份本机完整副本和SHA后删除，保留旧发布页与校验；回执见[清理记录](gitee-apk-cleanup-2.64.0-2026-11-05.json)。首包按本地ZIP交付，不包含在APK内，未提供云端同步；GitHub额外资料ZIP上传很慢，已停止并撤去未完成的附件/链接，不将其当作线上交付。

汇总见[发布回执](square-local-import-release-2.72.0-verification.json)。所有本轮启动模拟器在验证后关闭并保留数据，无用户设备清理。真实付费调用0次。
