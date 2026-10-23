# 价格体验前两批实施与验收

2026-10-23，2.69.0／144。用户批准实施价格专项报告前两批，并明确允许使用模拟器。先行四场景图稿与七套浅暗设计见[审查报告](plans/2026-10-23-price-experience-review.md)，本次采用范围见[实施设计](plans/2026-10-23-price-experience-implementation.md)。

## 最终行为

- 价格和实付编辑按导航条目保存紧凑输入草稿；旋转不重复加载覆盖输入，系统恢复先重读账本、核对指纹，再开放编辑。陈旧账目、丢失订单和读取失败禁止保存；保存由ViewModel持有，完成后清理草稿并防重复，退出保留放弃确认。
- 金额、阶段合计差值和实际日期就地反馈。付款先填真实实付，再看可主动填入的参考额、日期和保存后的累计／剩余／状态。阶段与购买参考按需展开，底部只有一个文字保存动作；键盘出现后金额输入保持可见。
- 当前订单首先展示物品、下一笔金额与应付日期，直接进入付款；进度区区分计划与实际付款日期，未指定阶段的实付只列一次并说明顺序抵扣。原账本、归档、阶段编辑、历史购买核对全部保留。
- 物品详情直接记付款。保存后回到原入口，显示不含金额的保存反馈，详情／订单／年历按原Flow刷新。没有强制把所有入口改为同一个返回页。
- 新建入口明确为“建立购买订单”，常显心愿转为待付款及实付另行记录的后果。全款默认只展开应付日期；其他阶段设置可展开。手动计划切换类型先确认保留范围，明确重拆才替换原草稿。
- 隐私实时覆盖详情、订单、原账本、年历年度／月份／明细及语义文本。主动编辑或核对前提示将临时显示金额，返回不改变全局设置。
- 年历只导航到付款编辑并预选阶段，不直接写实付。年度明确有日期口径；日期待定入口连接预算，预算按待付物品／已付订单身份直达正确页面，包含归档实付。

## 图稿到真实页面

复用原有皮肤组件、字体、手绘图标和艺术背景。未引入新的栅格插画或替换用户图片。首轮实际截图发现年历暗色玻璃偏亮、月份大字金额裁切，以及预算下一月被挤成竖排，均在最终验收前修正：年历使用当前surface作为玻璃底色，四列月份和正常48dp布局保留，随系统字号增高；微型金额显式行高；预算使用皮肤左右箭头与居中可换行月份。

[完整截图与逐文件摘要](visuals/2026-10-23-price-implementation/verification.json)包含9个受影响场景×7皮肤×浅暗×正常／320dp两倍字号，共252张真实Compose截图。PNG原始图在本机验收目录保存，仓库使用逐像素验证一致的无损WebP。拼图只供浏览，单张可查看完整分辨率。详情矩阵是实际ItemOrderSummary组件，完整MainActivity详情另有走查证据。

| 场景 | 正常尺寸 | 320dp／两倍字 |
|---|---|---|
| 详情摘要 | [浅暗七皮肤](visuals/2026-10-23-price-implementation/board-detail-normal.jpg) | [浅暗七皮肤](visuals/2026-10-23-price-implementation/board-detail-320-large.jpg) |
| 订单总览 | [浅暗七皮肤](visuals/2026-10-23-price-implementation/board-overview-normal.jpg) | [浅暗七皮肤](visuals/2026-10-23-price-implementation/board-overview-320-large.jpg) |
| 记付款 | [浅暗七皮肤](visuals/2026-10-23-price-implementation/board-payment-normal.jpg) | [浅暗七皮肤](visuals/2026-10-23-price-implementation/board-payment-320-large.jpg) |
| 建立订单 | [浅暗七皮肤](visuals/2026-10-23-price-implementation/board-price-normal.jpg) | [浅暗七皮肤](visuals/2026-10-23-price-implementation/board-price-320-large.jpg) |
| 历史核对 | [浅暗七皮肤](visuals/2026-10-23-price-implementation/board-history-normal.jpg) | [浅暗七皮肤](visuals/2026-10-23-price-implementation/board-history-320-large.jpg) |
| 历史补录 | [浅暗七皮肤](visuals/2026-10-23-price-implementation/board-supplement-normal.jpg) | [浅暗七皮肤](visuals/2026-10-23-price-implementation/board-supplement-320-large.jpg) |
| 原账本 | [浅暗七皮肤](visuals/2026-10-23-price-implementation/board-ledger-normal.jpg) | [浅暗七皮肤](visuals/2026-10-23-price-implementation/board-ledger-320-large.jpg) |
| 年历 | [浅暗七皮肤](visuals/2026-10-23-price-implementation/board-calendar-normal.jpg) | [浅暗七皮肤](visuals/2026-10-23-price-implementation/board-calendar-320-large.jpg) |
| 预算 | [浅暗七皮肤](visuals/2026-10-23-price-implementation/board-budget-normal.jpg) | [浅暗七皮肤](visuals/2026-10-23-price-implementation/board-budget-320-large.jpg) |

## 原生与业务验证

独立AVD `Lolita_Creative_Test_35`／Android15，测试使用独立内存Room和UUID设置。小屏由系统实际改为960×2100／480dpi，即320dp，并设fontScale=2；不是仅缩放浏览器或截图。真实键盘已用WindowInsets.ime与系统截图双重确认。

- 22项定向JVM：价格补算12、预算口径3、阶段抵扣7。
- 33项去重定向设备方法最终通过。新PricingExperienceTest覆盖输入幂等、SavedState重建、并发账目变化拒绝、原付款保留、重复保存、历史未知日期、金额隐私语义、导航不写账、部分付款／撤销与IME。
- 旧回归按受影响范围选取价格管理、实付管理、引用报价、价格补算、历史购买、阶段异常、共价小物、预算与年历时钟。最终方法、运行日志摘要、失败修复过程见verification.json，不把未找到测试类的初始化错误计为功能通过。
- 完整MainActivity使用追加到独立AVD副本的虚构订单：在隐私关闭的详情进入付款，输入125，横竖屏切换后保留；回到桌面后am kill确认进程消失，再恢复仍为125及原日期。返回出现放弃确认，继续编辑保留输入，日期选择取消不改日期。保存回到详情，SQLite核对原200加125共325、余额367，只新增一笔。测试结束恢复原库／WAL并逐字节核对，原图片未动。
- 完整应用证据：[详情隐私](visuals/2026-10-23-price-implementation/app-detail.webp)、[旋转恢复](visuals/2026-10-23-price-implementation/app-payment-rotated-back.webp)、[进程恢复](visuals/2026-10-23-price-implementation/app-payment-process-restored.webp)、[真实键盘](visuals/2026-10-23-price-implementation/payment-system-ime.webp)。

## 数据、边界与发布

Room33、42实体、备份14不变；BudgetEntry身份只用于查询投影。未增加持久实体、图片或备份字段，没有修改原账本事务或历史金额，没有重新跑无关备份与图片迁移。正式覆盖升级比较受影响的物品／价格／绑定／阶段／实付／预算原字段和本机设置；包内37份项目assets、7幅背景及依赖、生成profile独立校验。

退款、退差价、取消订单及独立参考价模型保留在第三批，本次未实现。模拟器原生测试经用户确认；未冒充USB真机、TalkBack人工朗读、真实交易或付费服务测试，应用付费调用0次。

提交源码后从git archive执行clean正式签名构建，再验证2.68.0覆盖升级、双渠道同一附件、匿名完整下载／SHA256／HEAD及更新入口。最终源码与发布回执将在发布完成后追加。
