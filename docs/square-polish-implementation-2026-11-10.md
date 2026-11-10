# 广场六项修整实施记录

版本 2.78.6 / 162，用户指定顺序 **5 → 6 → 1 → 4 → 2 → 3**。前置构图、七皮肤与数据边界见[设计](plans/2026-11-10-square-polish.md)。本轮不测小屏、大字体；没有扩大到动态排序、采集或服务部署。

## 实现与原生对照

| 顺序 | 最终实现 | 原生证据 |
| --- | --- | --- |
| 5 卡片 | 两行标题、单行店铺、简短日期；真实SKU数值区间及阶段，未知不补0，含组合价注明。采集证据不冒充售卖日期。完整说明保留详情 | [列表](visuals/2026-11-10-square-polish/native/feed-seven-skins.jpg) |
| 6 详情 | 系列为副标题、单品为标题；次级淘宝操作移到规格后；尺码表提前，完整参数可展开，重要售卖限制保留；仅组合不显示为单件报价 | [详情](visuals/2026-11-10-square-polish/native/detail-seven-skins.jpg)、[参数](visuals/2026-11-10-square-polish/native/ozz-parameters-expanded.png) |
| 1 图片 | 精确照片缺失时直接用可放大的系列参考图，明确标记、提示不作为心愿封面；原始精确映射与私人UUID复制不变 | [系列图](visuals/2026-11-10-square-polish/native/reference-seven-skins.jpg) |
| 4 单品/颜色 | 同链接、明确颜色后缀、类别/销售维度一致才作展示分组；颜色选择落到原始成员。长系列名前缀只从展示移除。全部单品可搜索；多件按组展开、可搜索，隐藏选择保留 | [选择器](visuals/2026-11-10-square-polish/native/parts-seven-skins.jpg)、[多件](visuals/2026-11-10-square-polish/native/multi-seven-skins.jpg) |
| 2 筛选衔接 | 列表与ViewModel共用SquareBrowseFilter，匹配同一实际SKU，详情默认进入匹配部件/颜色；无精确图片时注明系列参考；选中标签滚入视区 | [实际小物](visuals/2026-11-10-square-polish/native/filtered-accessory.png) |
| 3 筛选 | 候选依店铺、动态、搜索及其他属性收敛；内容前置；长颜色/风格可搜索、按需展开；固定完成与清除属性，已选芯片逐项移除；空态分开清除属性和搜索 | [筛选](visuals/2026-11-10-square-polish/native/filters-seven-skins.jpg)、[键盘](visuals/2026-11-10-square-polish/native/filter-real-ime.png)、[空态](visuals/2026-11-10-square-polish/native/empty-filter-actions.png) |

仅新增只读展示投影 SquarePresentation / SquareBrowseFilter 和皮肤选择器 SquarePartPicker；SquareBrowse、SquareWishPlans、来源身份、原始SKU与资料、持久Repository没有改写。Room34、45实体、备份16不变；无数据迁移、无新权限、付费0、主助手淘宝访问0。金额开关沿原报价显示范围；原始公开资料始终留存。不同链接、SP与经典、长短款、商家套装均不因展示分组自动合并；未知尺码可直接收藏。

图稿与原生都使用真实皮肤令牌/既有图标，不新增图标或应用图片资源。原生按实际组件尺寸收敛：淘宝入口位于规格下方；系列参考提示单列；选择器使用当前Material表面色，避免弹层暗色白底浅字。图片保持Fit和全屏入口，不覆盖原图。

## 验证与修正

- Debug构建通过。定向JVM共19项：SquareBrowseTest 12、SquarePresentationTest 7。后者使用已落地公开目录的4商品/3动态/192图片元数据夹具，验证保守颜色分组、原始键全集、独立心愿身份、组合维度、实际筛选匹配、上下文候选、系列图不进入心愿、未知与价格阶段。
- 去重设备方法13项：新增SquarePolishTest 7、既有SquareBrowseTest 5、真实LolitaNavHost导航1。覆盖直接/多件保存、独立图、未知尺码、原账本不增加、SavedState恢复、已有拥有状态、淘宝Intent拦截/取消/复制回退、心愿详情及返回。没有实际外跳淘宝采集。
- 真实七皮肤浅暗的列表、详情、系列参考、筛选、全部单品、多件共84状态；最终截图清单在[manifest](visuals/2026-11-10-square-polish/native/screenshot-manifest.json)，6张对照板及关键独立截图归档。真实IME下筛选与单品搜索的完成按钮可达。
- 走查修正：空态及单品卡片内容需明确Column布局，修复重叠点击与图文叠放；选择器暗色底色显式消费当前Material surfaceVariant，修复弹层配置下浅底浅字；筛选匹配配件标签自动滚入视区；仅组合报价不冒充单件全款。
- 早期测试问题单列：临时ZIP清单误命名catalog.json导致准入拒绝，改回协议manifest.json，未放宽生产校验；两个测试选择器歧义/子串断言修正；一次ADB UiAutomation断开不计通过。最终全批13方法中12通过，NavHost测试补等待真实详情加载并定位未合并语义，最终三项定向复测（84状态、Bunny收藏、完整NavHost）全部通过，生产导航未绕过。详细原始日志仅保存在本机Downloads本轮目录。

正式提交源码的隔离clean签名构建、2.78.5覆盖和两端发布结果以 `square-polish-release-2.78.6-verification.json` 为最终回执，不将Debug截图当正式APK验收。

## 正式交付

源码与标签 `cc2ec9d9633573c327c90e72f6e5ea21ff37fc9b` 已普通推送至 GitHub master / v2.78.6；本地原 master 两条未合入远端的旧提交保留，未带入本次正式源码。提交归档 clean 原签名 Release（含 lintVital）通过，APK 84,038,041字节，SHA256 `f1bb9b3b41c050938e1db7bbef457868e528fde800db6180a9576a4533527931`。37份项目素材与源码相同，24份旧图片资源保持。

正式2.78.5同签名覆盖并两次冷启动，45表全行、160物品、629私有图SHA及6份原设置保全；临时内网地址用于新目录图片/筛选/配件详情的真实MainActivity核验。[实际列表](visuals/2026-11-10-square-polish/release/official-lan-feed.png)、[筛选卡片](visuals/2026-11-10-square-polish/release/official-filtered-card.png)、[参考图与匹配配件](visuals/2026-11-10-square-polish/release/official-reference-accessory.png)归档。最后快照识别到测试恢复脚本把adb的不存在提示当作配置文件，未认定恢复通过；依据升级前清单恢复原先“未保存地址”状态，再次核对45表/629图/6设置完全相同。原公网缓存图片最初未加载，仅内网图片验证通过，不将此算公网刷新修复。

GitHub/Gitee正式附件同APK与SHA256SUMS，匿名完整下载/SHA/HEAD及GitHub源标签通过；Gitee公开接口选择2.78.6，实际正式App显示当前/最新均2.78.6及完整更新说明。配额不足后依持续授权，仅清理Gitee最旧2.73.1 APK；旧发布、校验、GitHub及3份本机完整副本保留，见[清理回执](gitee-apk-cleanup-2.73.1-2026-11-10.json)。

两台任务AVD关闭且数据保留，无自动重启；临时签名配置副本清除，本机原签名和Gitee令牌保留。[最终回执](square-polish-release-2.78.6-verification.json)为发布验证依据。
