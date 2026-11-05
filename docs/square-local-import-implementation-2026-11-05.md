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

正式构建、覆盖升级和发布回执将在完成后补充。
