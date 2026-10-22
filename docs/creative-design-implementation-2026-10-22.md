# 两轮体验设计实施与原生验收

日期：2026-10-22。版本：2.67.0／142。用户明确要求基于前两轮图稿实施；依据[实施计划](plans/2026-10-22-creative-design-implementation.md)、[第一轮提案](plans/2026-10-22-creative-experience-review.md)和[第二轮提案](plans/2026-10-22-creative-experience-review-part2.md)。本次已完成下面的应用改造与定向设备验收；正式包覆盖升级及分发结果另存发布回执，不以Debug成功替代正式发布。

## 最终采用范围

| 设计目标 | 实现与保留规则 | 原生对照 |
| --- | --- | --- |
| 金额隐私与精度 | 个人页ProfileSection接收实时显示金额设置；关闭时整行及语义消失，开启显示两位小数，长金额自然换行 | [七皮肤浅暗](visuals/2026-10-22-creative-native/profile-seven-skins.jpg) |
| 空态就地行动 | 衣橱真空态提供新增、明确选择淘宝商品链接／订单文件、恢复备份；筛选空态清除搜索及属性，保留页面拥有状态范围。穿搭空态直接记录，已有列表仍保留FAB；对应空态不重复FAB | [空态对照](visuals/2026-10-22-creative-native/empty-seven-skins.jpg) |
| 安静表面与插画 | 中华静态底图、云朵和花瓣降低透明度；普通卡边框降低强调，主动作及精选层级保留。新衣橱插画完整Fit，普通180dp、大字96dp，装饰不代表实际库存 | [空态完整图](visuals/2026-10-22-creative-native/empty-chinese-light.png) |
| 实验室按目的呈现 | 展示图／虚拟试衣为同级可整卡点击入口；分别用现有七皮肤符号和内置人物说明用途，标记「用途示意」。本机图像配置置于紧凑摘要，原全部工具和API配置可达 | [浅暗对照](visuals/2026-10-22-creative-native/laboratory-seven-skins.jpg)、[完整首屏](visuals/2026-10-22-creative-native/laboratory-chinese-light.png) |
| 试衣先选择搭配 | 人物76×114dp完整Fit且可放大；物品入口及已选卡先于服务设置，后者按需展开。主按钮「核对输入与费用」仍先打开原确认，不绕过实际输入预览、费用及陈旧确认校验 | [试衣对照](visuals/2026-10-22-creative-native/tryon-seven-skins.jpg) |
| 唯一文字保存 | 价格与付款共用EditorSaveBar；消费外层Insets，栏内处理IME。价格可展开编辑阶段，摘要保留金额／未知日期，重新拆分使用次级文字操作；付款先填实际金额／日期，再看购买参考 | [价格](visuals/2026-10-22-creative-native/price-seven-skins.jpg)、[阶段](visuals/2026-10-22-creative-native/plan-seven-skins.jpg)、[付款](visuals/2026-10-22-creative-native/payment-seven-skins.jpg) |
| 备份层级及结果 | ZIP唯一主要导出；JSON／CSV明确限制并提供次级文字导出。恢复后果常显。导出结果保留真实文件名／格式／URI，主动只读打开；无查看器时说明已导出和查找方式，成功结果不消失 | [备份对照](visuals/2026-10-22-creative-native/backup-seven-skins.jpg) |
| 任务结果优先 | 单任务卡显示真实可用结果缩略图，待确认／完成／失败分别核对、查看结果、查看原因。缺图回退并引导核对，输入图不冒充结果；顶部新建收为次级入口，普通帮助下移 | [任务卡对照](visuals/2026-10-22-creative-native/task-seven-skins.jpg) |
| 收纳与导入 | 房间封面88dp且可完整放大，位置说明及低频管理可展开；原搜索、移入、创建、模板及所有节点动作保留。订单文件选择旁直达原七步指南 | [收纳对照](visuals/2026-10-22-creative-native/storage-seven-skins.jpg) |
| 底栏大字安全区 | 内容高度按图标及当前文字行高计算，在navigationBars Insets上方显示；取消标签负偏移，系统字体不缩小 | [真实320dp两倍字浅暗](visuals/2026-10-22-creative-native/navigation-small-seven-skins.jpg) |

主稿的艺术方向映射为现有皮肤令牌、Type.kt、LolitaCard、LolitaSection、ImageFrame和真实52键provider，没有把生成稿的通用图标搬入应用。实验室展示图采用流程符号、试衣采用既有内置人物，避免展示未经真实生成验证的前后效果；这与概念稿的照片构图有所不同，但两个目的入口保持同等层级与完整路线。任务批次仍使用既有摘要、逐件状态及查看入口，本轮新缩略图用于单任务卡；全局活动任务栏、停止排队和批量舍弃不变。

## 原生验证

使用新建独立 `Lolita_Creative_Test_35`（emulator-5602，API35），没有卸载或清理既有用户／发布AVD。常规1080×2400／420dpi；实际小屏切至960×2100／480dpi、系统fontScale2.0，结束恢复原值。独立内存Room、UUID目录和受控记录验证业务，不调用真实AI服务。

最终去重28项设备方法通过，包含下列范围。最初实验室卡片标题点击未导航，已修为整卡点击并复测；收纳列表变化后一个用例需要先滚动定位虚拟列表项，已保留原拥有状态断言并复测。编译期间测试夹具缺少status参数已补齐；这些失败没有被忽略或记为通过。

| 改动影响 | 所选定向验证 |
| --- | --- |
| 隐私／空态／结果界面 | CreativeExperienceScreenTest：金额实时隐去且保留两位小数、导出结果／格式警告／忙碌禁用、空态和任务动作／缺图、五组七皮肤浅暗截图 |
| 原导航与实际Insets | CreativeNavigationScreenTest：空衣橱导入方式→原订单→原指南／备份路线；真实320dp两倍字下七皮肤浅暗四底栏选中与文字边界高于系统手势区 |
| 备份URI及操作互斥 | BackupExportResultTest：真实ZIP／JSON／CSV返回URI可读、文件名来自实际文件、ZIP可预览且取消不写库；导出期间拒绝第二导出与恢复、查看失败保留成功结果 |
| 价格与实付 | PriceAutofillIntegrationTest：双向补算／手动优先、原ID／日期／实付保全、历史只读；价格七皮肤及唯一保存；付款七皮肤、真实IME下唯一保存可达，点击前无实付，点击后仅一笔125.50 |
| 收纳与隐藏选择 | StorageRoomScreenTest：完整原六项，用真实Room核对父子记录／计数／位置、筛选保留选择、移入前确认、未付物品排除及失去拥有状态清选择；七皮肤320dp一倍／两倍字 |
| 试衣与费用边界 | TryOnScreenTest中选择保留／统一配置、七皮肤控制可达、穿戴说明／键盘／费用确认三项；没有实际提交付费任务 |
| 实验室 | LaboratoryNavigationTest验证全部原路由及无提交；LaboratoryScreenTest的真实小屏大字、假发搜索与IME方法验证调整后入口可滚动到达 |
| 背景 | SkinArtBackgroundTest中真实七皮肤浅暗与静态绘制，SkinBackgroundAnimationTest检查滚动冻结与恢复；未更改粒子生命周期 |

新增插画后补跑受影响空态矩阵、真实空态路线及小屏大字路线；其他已通过且未受后续改动影响的方法未重复执行。最终Debug构建通过。[验证汇总](visuals/2026-10-22-creative-native/verification.json)记录去重方法与阶段结果；[截图清单](visuals/2026-10-22-creative-native/manifest.json)列出154份选入对照板的原生来源摘要，完整原图保存在本机发布工件目录。

真实键盘证据：[价格](visuals/2026-10-22-creative-native/large-keyboard-system.png)、[付款](visuals/2026-10-22-creative-native/payment-real-ime.png)、[实验室假发搜索](visuals/2026-10-22-creative-native/small-wig-ime.png)。测试宿主中的部分顶栏没有提供Haze source，使用原组件既定渐变回退；正式应用保留Haze路径。独立组件截图不冒充完整应用界面，发布包实拍另列。未宣称TalkBack人工朗读或真实AI视觉质量已经验证。

## 数据、素材与隐私

Room32、36实体、备份13、CSV业务列、原任务请求与业务图片均无格式变更。新导出结果仅在ViewModel内存保存，未新增Room记录或本机长期备份时间；使用原BackupManager及其校验、锁和恢复屏障。没有重做无关全库迁移或恢复往返，真实URI导出与预览不等于完整恢复验收。

新插画位于 `app/src/main/assets/illustrations/empty-wardrobe.png`，内置image_gen从文字生成1次；原始1254×1254 RGBA完整复制，四角透明、alpha范围0–255，缩略／暗色仍保留原色和完整轮廓，不染色、不改原图。完整提示词、来源与SHA256见[素材记录](creative-illustration-sources-2026-10-22.json)。这属于应用静态装饰，不是用户业务图片，不进入其备份清单；正式APK仍核对所有37份项目assets与提交源码逐字节一致。应用内付费AI请求为0，不沿用历史接口测试额度。

现有订单与付款计算、未知日期、拥有状态和位置规则未改；保存仍通过原ViewModel与OrderRepository。试衣选择／费用确认／待确认结果不自动建实付、改封面或登记穿搭。分享和导出范围没有扩大；文件打开由用户主动触发，授予只读URI访问，不创建公开链接。既有工作区未跟踪资料全部保留，正式包从提交归档构建。

## 完成检查

- [x] 前两轮图稿与七皮肤效果先于应用改动归档，用户明确要求实施。
- [x] 主要入口、空／失败／待确认／保存／导出结果与取消场景有明确动作。
- [x] 七皮肤浅暗、小屏大字、真实键盘和手势区经原生检查，截图已对照。
- [x] 图片完整预览、原图和引用保护、金额隐私、账本、旧备份与费用边界已核对。
- [x] Debug及28项去重定向设备方法通过，未跑无关全量测试。
- [x] AGENTS.md维护本轮组件、交互与版本；设计规范索引同步。
- 正式源码提交、隔离clean Release、原签名覆盖及双平台分发的最终结果，以 `docs/creative-design-release-2.67.0-verification.json` 为准。

## 正式交付结果

源码 `94b13c23` 与标签 `v2.67.0` 已推送。从该提交归档执行clean Release，正式APK为83,399,061字节，原签名保持；37份项目assets、7张背景RGBA、依赖素材与生成profile均核验通过。独立发布AVD从2.66.4／141覆盖并两次启动，3份原有设置摘要保全；本轮未重复无关全库／原图审计。正式包的金额隐藏、空态入口、试衣首屏及唯一文字保存已实拍核对，没有保存业务记录或付费调用。

[正式包三页实拍](visuals/2026-10-22-creative-native/release-overview.jpg)、[价格真实IME](visuals/2026-10-22-creative-native/release-price-ime.png)、[金额隐私](visuals/2026-10-22-creative-native/release-private-personal.png)、[备份](visuals/2026-10-22-creative-native/release-backup.png)与[任务中心](visuals/2026-10-22-creative-native/release-tasks.png)可直接对照。正式包实拍有Haze source，与前述独立组件截图的渐变回退分别记录。

GitHub与Gitee均已正式发布，同一APK及SHA256SUMS匿名完整下载、大小／摘要和HEAD跳转链核验通过，未删除旧附件。APK SHA256为 `ea52ce47204e48aca1fe00680554d2b7828e48a13bdd7c47fe54535f73f49f19`，GitHub标签准确指向上述源码；Gitee仍只承担分发说明与附件。

Gitee公开版本API本次返回HTTP403，未确认公开最高版本查询；正式应用仅实际检查一次，显示「Gitee 暂时拒绝访问或请求过于频繁，请稍后重试」。[实拍](visuals/2026-10-22-creative-native/release-manual-update-check.png)和[完整回执](creative-design-release-2.67.0-verification.json)保留这一限制。附件直链可用，不以下载成功代替应用检查更新成功，本轮没有更改网络／更新代码或重试修复网络。
