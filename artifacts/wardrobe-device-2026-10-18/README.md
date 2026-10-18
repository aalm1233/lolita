# 首期衣橱独立设备验证

目标为全新 `Lolita_Wardrobe_Test_35` / `emulator-5582`，API35 Google APIs ARM64、Pixel7、1080×2400 / 420dpi。通过 launchctl 作业 `com.lolita.wardrobe-test.5582` 保留模拟器。未使用 Lolita_API_35、USB、卸载、清数据或付费接口。设备数据与进程保留，当前已释放设备/Gradle窗口。

## 首轮 storage/crop 结果（历史53项）

- `final-targeted-instrumentation.log`：14个精确指定类，53项全部通过，20.495秒；逐项结果见同名 `-summary.json`，命令见 `final-targeted-command.json`。
- `.superpowers/sdd/2026-10-18-complete-wardrobe-phase1/storage-crop-final-device-build-3.log`：357项JVM单测0失败/错误，Debug与androidTest APK构建成功。之后仅修改测试定位、测试宿主和截图导出，最后测试包构建为 `storage-crop-dialog-host-build.log`，成功。
- 最终APK与安装记录见 `round8-apks.json`、`round8-install-evidence.txt`。每轮安装前核对AVD、主/测试包、已装/新APK签名、版本，只用 install -r。主包113覆盖113；两包证书均为 `b7832feba195d280dfa51957f8f6a38946d82a7d633ef3e2a11b23c04f01a8d2`。
- 实现提交 `e5b173e`，设备发现修复和迁移回归提交 `f629251`。catalog的S1新增物品入口由其单独提交。
- 可审阅截图为 `verified-screenshots/`：七套皮肤浅/暗320dp、2倍字体共14张，以及系统窗口截图的实际整图/100×100绿色裁切预览2张。来源与SHA见 `verified-screenshots.json`。已肉眼检查默认浅色、维多利亚暗色和实际绿色裁切；截图来自隔离Room与合成测试图片，不代表真实付费识图质量。

覆盖：树/循环/同父同名/四层拒绝、事务移动/回滚、挂载小物、非破坏删除、四图明确采纳及不复活、路径搜索/选择保持/影响确认、位置选择器、所有原生裁切像素及EXIF/旋转/长图、裁切真实手势与取消清理、登记及鞋履入口、试衣/头模/图像任务所有权与识图独立设置。

`WardrobeMigrationTest` 实际使用v29 schema填满30张旧表、195个旧字段，逐列比较迁移前后值；保留六房间、主子/价格/订单/付款/穿搭关系和两张原图SHA。新收纳表为空，新字段为空/未确认；外键检查和完整性检查通过。旧请求JSON作为不透明历史证据原样核对，不将它冒称有效新协议请求。

## 失败与修复历史（全部保留）

| 证据 | 实际结果与边界 |
| --- | --- |
| `initial-targeted-instrumentation.log` | 启动40、完成39：32过/7失败。第40项旧MannequinViewModelTest因UNCONFIRMED品类准入阻断后无限entered.await，负责人结束新AVD该进程。不能称完整47项通过。旧类未纳入后续定向串，已报owner。 |
| `storage-room-red-repro.log` | 真实点击柜架被重叠操作行截获，1失败。LolitaCard为Box，生产柜架/房间卡改Column；后续真实点击、路径和移动通过。 |
| `round2-targeted-instrumentation.log` | 38项37过/1失败，大字体直接查询LazyColumn已回收节点；改performScrollToNode，仍验证项目和移动按钮可见。 |
| `round2-preinstall-aborted-*` | 测试APK空versionCode预检问题后误起旧APK，已中止，单独归档，不能作验证。 |
| `round3-targeted-instrumentation.log` | 53项51过/2失败。裁切预览Coil父子同无障碍描述，改明确选择外层并等真实绿色像素；新增登记fixture重复同名品类交catalog修。 |
| `round4-targeted-instrumentation.log` | 53项全部通过。 |
| `round5-visual-instrumentation.log` | 补主题Surface后收纳3过，crop1初始化超时/1过，共4过1失败。 |
| `round6-visual-instrumentation.log` | 5项全部通过。Compose根截图仍抓到Dialog后方空宿主，仅作中间诊断，不选入最终图。 |
| `round7-native-screenshot-instrumentation.log` | crop1初始化超时/1过；诊断抓到唯一测试宿主0×0，原图文件正常。诊断自身根捕图也因0尺寸报错，保留原始日志与语义。 |
| `round8-dialog-host-instrumentation.log` | 测试宿主补fillMaxSize Box，诊断改系统窗口抓图；crop2项全部通过，2.759秒。 |
| `final-targeted-instrumentation.log` | 包含上述最终测试修正，完整53项全部通过。 |

另外：无EXIF JPEG的Android中性orientation兼容值可为0，测试现在核对私密属性不存在且字节中无Exif段；取消清理测试推进Compose时钟后等待异步删除，同时核对原SHA；暗色透明页面测试宿主补主题Surface，最终14张截图有正确主题背景。初期空白Dialog宿主截图及未设Surface的暗色图保留为诊断，不能当作最终视觉验证。

`WardrobeEditorRecognitionTest` 在此轮代码中尚不存在，根代理后续识图协调器/编辑器与国内provider接线不在这53项的完成声明内。没有真实网络模型请求。

## 后续持久化、草稿与原生识图UI最终验证

后续真实Room18项先13过5失败，根修复后增加至24项，全绿；原始RED/中间fixture失败与最终GREEN均保留在backup-persistence目录，不能用早期53项替代。fresh reviewer继续发现收纳移动/删除未同步ItemDraft的房间/节点，以及收纳SQL不递增Item.updatedAt允许陈旧草稿覆盖，分别以实际Room RED3与RED1捕获后修复。

未发布schema持续修改使5582应用全局Nav初始化遇到历史schema hash；根另建全新专用 `Lolita_Wardrobe_Final_35` / `emulator-5586`，5582与旧正式升级5584均保留。最新固定同签version113包，Main SHA256 `2f77a10a7481138c632448e543c40a3757a3e23a13bbf6636c2334451d0405ad`，test `ba610ed03e43945208bea534196603587f37e08b4676833b9d3dadffe8e5829f`，386 JVM0失败/错误/跳过、双APK构建成功。

在5586同一已安装固定包上实际通过 **44 Room + 3 UI = 47/47**：StorageRepository8、StorageDraft4、Baseline6、NewDraftStorageCommit2、WardrobeBackup16、RecognitionPersistence8；实际Compose3含款式/尺码切换、分次采纳、实际保存重入查看证据、7皮肤浅暗原生320dp/2x。耗时分别3.374秒和21.267秒，精确类串/源SHA/逐项日志/APK证据与解析计数在 `final-5586/final47/`。这47项不声称覆盖尚由根补跑的旧Nav/provider/migration套件。

最终28张实际原生窗口截图与SHA在 `final-5586/final47/recognition-ui-native/`，含两个标注总览。真实配置由外层测试rule在启动Activity前设置，结束还原；已只读核对还原为physical420dpi、font1.0、night=no。仅LocalDensity的早期Dialog图/Compose根截图/paragraph宽度误报日志均保留为诊断，不代作原生大字体证据。所有模型传输为0，无真实凭据、付费请求、卸载或清数据。

## 最后旧回归与新增缺陷收口

完整旧127首次真实结果为112通过、14失败、1显式opt-in跳过，见 `final-5586/legacy-regression/`；不能称127全绿。旧Mannequin/TryOn UI fixture确认稳定品类、同步既有按钮文案后，完整受影响两类15/15通过（75.698秒），没有改生产页面或准入。socket项明确启用后仅host127.0.0.1随机端口受控服务，1/1通过（1.081秒），官方API0。

旧Backup5的实际失败由根修正：仅任务独占v4输入与识图region按SHA归并，业务/待确认输出保留身份，不松ownership。随后历史missing资源阻断所有ZIP又以单method1/1真实RED捕获；根修复仅豁免已标记且任务独占缺图，仍保留missing count与业务严格检查。Catalog迟到autosave复活已删物品草稿也以3项目标RED→GREEN捕获关闭。全部原RED、中间fixture诊断和新GREEN分别保留。

最终全量构建12秒成功，390 JVM0失败/错误/跳过，构建前后源码SHA稳定。最终同签Debug113/test install-r，main SHA `03dc649e7d7e7a2c9e394c87b0f60e843d850e32496bc6f46e8cb6a0b120aac1`，test `68dd0e222fc7937184e986666f0da977d9191212824249bca819221009ef628e`；**52/52设备回归通过，6.282秒，0skip**。具体为WardrobeBackup18、旧ImageTaskBackup9、Personal6、草稿删除3、Lifecycle9、Baseline6、Deletion1。证据 `final-5586/legacy-final/`，总索引 `final-5586/legacy-verification-rollup.json`。旧127/新47未在本包整串重复，结果按各自实际APK边界记录。Gradle/5586已还根处理独立版本114cleanRelease，未卸载清数据、未用旧正式设备、未发实际付费请求。

## 最新有效结果：114 / JDK17 / 53项

旧COMPLETED结果仅为informational时，不应让另一个已标记缺失历史输入重新成为ZIP必需文件。第19项先在114包实际1/1 RED（0.372秒），根修复仅将待确认输出计为必需，其余严格边界不变。最终使用本机真实Homebrew JDK17.0.19构建，390 JVM0失败/错误/跳过，双APK33秒成功，所有src和版本文件构建前后SHA稳定。此前AndroidStudio路径实际是JBR21.0.10/编译target17，两者分别记录。

**114设备53/53通过，6.505秒，0skip**，含WardrobeBackup19+oldBackup15+draft/lifecycle19；main SHA `17ba85539edd57085f3744db78b36864621ffd3682d7f63d30c9c341b8caa9a9`，test `15f174e11bcc509da2973e9dd164335c356cfc9ad6f0624efd426d77ad8372af`。最终证据 `final-5586/legacy-final114/`，RED为 `informational-history-red114/`，总索引 `legacy-verification-rollup.json`。同签install-r，保留模拟器数据，实际官方API0；Gradle/5586/index交回根正式发布。
