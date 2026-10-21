# 实验室与统一展示图实施记录

## 最终行为

个人 → 实验室提供全部 AI 功能，首张卡片直接开启批量生成展示图；识图录入仍在物品编辑器主动加图/框选，不自动发送。假发头模选择器按稳定全头假发类型筛选，支持搜索；发片保留单品逻辑。原业务页面的上下文快捷入口保留。

单品仅保留一个普通“生成展示图”，预选当前物品进入1～30件批量流程。已确认图片预览进入管理页，提供设封面和试衣；重新生成回到批量流程，管理页不重复展示逐件生成表单。普通旧人台/小物入口退出详情，旧任务确认/历史/重试保留原协议。假发戴头模继续独立，不与透明素材整理混用。

确认图片在管理页和批次“已确认”卡片均可设封面，明确操作后复用同一图片路径进入相册首位并标记AI来源。原图相对顺序、备注、状态、价格和位置保留。重复点击幂等；复核素材ID、类型/组合、透明性与SHA256。移除或替换素材、删除任务时相册引用继续保护封面；ZIP去重恢复共享路径及来源，不复制多份或自动改封面。Room32/36实体、备份13及旧格式兼容逻辑不变。

## 设计与实际页面

先行图稿见[设计文档](plans/2026-10-21-laboratory-display-flow.md)。已用实际 Compose 对照个人入口、实验室、单品展示分区、确认图管理与已保存批次，覆盖七皮肤浅暗、320dp两倍字体和真实键盘。

- [个人页](visuals/2026-10-21-laboratory-native/overview-personal.jpg)
- [实验室](visuals/2026-10-21-laboratory-native/overview-lab.jpg)
- [单品展示分区](visuals/2026-10-21-laboratory-native/overview-detail.jpg)
- [确认图管理](visuals/2026-10-21-laboratory-native/overview-workspace.jpg)
- [批次封面操作](visuals/2026-10-21-laboratory-native/overview-batch.jpg)
- [小屏真实键盘](visuals/2026-10-21-laboratory-native/small-wig-ime.png)

图片采用既有原创矢量测试夹具和受控接口，只证明布局、输入/保存和导航，不声称新的生图保真。所有主图完整Fit，检查底不写入透明字节，支持全屏预览。管理页顶栏缩为“展示图”，减少小屏大字拥挤；操作自然换行并随页面滚动。

## 定向验证

范围为上一正式源码94721aa5至本次应用改动：入口导航、普通/假发分流、确认图片封面引用及相应UI。没有改网络协议、数据库结构、账本、服务执行或旧备份解析，不运行全量JVM/USB套件。

最终18项去重定向设备用例通过：MaterialCoverWorkflowTest 4、MannequinDetailEntryTest 2、LaboratoryNavigationTest 1、LaboratoryScreenTest 2（正常七皮肤与真实小屏各自运行）、MaterialCategoriesWorkflowTest 4、MaterialBatchViewModelTest 3、ItemCoverTest 1、MaterialWorkspaceScreenTest的realPagesAcrossSevenSkinsLightDarkAndExplicitFeeReview 1。普通尺寸下的小屏条件用例跳过，随后在真实320dp/两倍字体专门运行通过。管理页收敛后重跑相应视觉及原待确认/费用流程，未重复全套。

覆盖最新相册保全、并发/重复设置、AI来源、替换/移除/删任务后的文件保护、共享素材与相册ZIP往返、陈旧ID/类型/组合/字节变化拒绝；实验室所有入口、默认零选择和假发头模独立路由；真实搜索IME下选假发、小屏封面与批次按钮可达。

Debug构建通过。无新真实生图请求，不消耗现有授权额度；累计额度与超时预留沿用上一轮记录。模型本身可能改写细节的限制保持。

正式源码9b9f7a7f及v2.65.0已推送，提交源码归档clean原签名Release通过。2.64.0覆盖升级及重复启动保全36张旧表原列、原v5批次和14张原图片SHA；可选设置前基准清单未捕获，不声称本轮逐字节验证设置（本次没有新增或修改持久设置字段）。验证脚本缺可选字段的报告错误已修复，基于原2.64.0基准重新生成报告，没有清数据或重装规避。

正式App入口[实验室](visuals/2026-10-21-laboratory-native/release/release-laboratory.png)与[单品展示图](visuals/2026-10-21-laboratory-native/release/release-item-display-entry.png)核对通过；36份主包assets、7张背景RGBA、依赖素材和生成profile与提交源码一致，测试夹具及未跟踪图片未入包。

GitHub/Gitee同一正式APK与SHA文件的匿名完整下载、SHA256及HEAD通过，公开更新API选中2.65.0（HTTP200，3页）。Gitee首次发布请求超时后，经owner元数据确认不存在该tag，再安全恢复发布；没有删除旧附件，也未重跑App内手动网络检查。回执见[2.65.0发布验证](laboratory-display-release-2.65.0-verification.json)。
