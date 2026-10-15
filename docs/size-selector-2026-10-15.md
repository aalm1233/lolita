# 尺码选择与快速添加（2.47.0）

服饰编辑和淘宝订单导入共用 `SizeSelector`，把手输尺码改为下拉选择。内置 XS、S、M、L、XL、XXL、均码；“未设置”清空选择，旁侧皮肤加号打开自定义尺码弹窗，添加成功立即选用。空白不可添加，取消保留原选择；保存失败保留输入并允许重试。

`SizeOptionsViewModel` 将 `ItemDao.getSizes()` 的 Room Flow、`AppPreferences.customSizes` 与内置值合并。候选去首尾空格并忽略大小写去重，内置顺序固定；DataStore.edit 原子追加自定义项，重复项复用已有名称。组合函数不读写数据库，选择仅更新原编辑草稿，确认保存服饰或导入后才更新业务数据。

历史 Item.size 不主动改写。自定义候选为本机设置；已使用的尺码仍随既有 JSON/ZIP/CSV Item.size 导出和恢复，恢复后的 Room Flow 自动补回选项。没有新增实体或字段，数据库仍为 26，备份格式不变。尚未用于服饰的自定义候选与其他本机设置一样不纳入业务备份。

淘宝订单逐件和批量共用组件，资料折叠与定金/尾款资料继承保持原流程。颜色、尺码各占一行，给特殊尺码文字和 48dp 加号操作留出空间。复用 SkinIcon 的 Add/ExpandMore/ExpandLess 和主题颜色，无新增图标或皮肤令牌。

## 回归

`ItemSizeSelectorTest` 使用独立内存 Room、UUID DataStore 和独立 ViewModelStore，覆盖旧尺码选择/清空、新增后立即选中、首尾空格、重复/空白/取消、DataStore 重新打开、保存时其他字段不变，以及七套皮肤浅暗、320dp、两倍字号下的选择与加号操作。七皮肤实拍来自真实组件，归档 `visuals/2026-10-15-size-selector-skins.png`。

`OrderImportInteractionTest` 的新增用例实际展开资料、从下拉选内置项、快速添加、切换下一件并复用自定义项，核对各原索引草稿独立保留。该套件使用独立设置，保留既有逐件/批量、配对、金额草稿、键盘、小屏和七皮肤回归。

定向设备为 `Lolita_Order_Import_Test / emulator-5560`，正式覆盖升级使用 `Lolita_Release_2392_Test / emulator-5558`，不操作用户 USB 设备。

验证通过：`assembleDebug`、177 项 JVM 测试及上述两套件的 22 项真实设备回归（5 项尺码、17 项导入，0 失败/跳过）。首次回归先在原手输界面确认下拉选择缺失；实现后验证通过。独立代码复核未发现问题。
