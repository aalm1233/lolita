# 实验室导航字体修正（2.66.2）

用户指出2.66.1中“实验室”仍过大，随后明确要求“改 发”。本次只修改两个文字角色：个人页入口改用与属性管理一致的titleSmall16/24sp、Medium500和阅读字体；公共appBarTitle改为18/26sp、Medium500，七皮肤标题字体保留。主功能标题22sp、正文、卡片、图标及原功能保持。

先绘制[前后对照及七皮肤方案](plans/2026-10-21-compact-laboratory-titles.md)，再修改源码。实际个人页入口、同级属性管理和实验室顶栏直接测量并截图，避免只检验字体示例。入口与同级在字号、行高、字重、字体四项完全一致；实际顶栏为18/26sp、500字重，七皮肤浅暗都成立，入口和批量跳转继续可用。

Debug及测试APK构建通过，最终4项去重定向设备方法通过：TypographyScreenTest的2K七皮肤与大字真实IME；LaboratoryScreenTest的实际入口／同级／顶栏对比与小屏假发搜索真实IME。初次两项IME超时对应独立模拟器的System UI ANR弹窗，保存系统焦点和弹窗证据后，只重启该AVD、保留数据和已安装包，原应用代码不变，仅重跑这两项均通过；没有放宽键盘断言。

测试使用1440×3200，正常density600、约384dp；小屏density720、约320dp，系统字体2.0。50张本轮相关原生截图与实际样式测量归档于[真实页面目录](visuals/2026-10-21-compact-lab-titles-native/verification.json)，含[入口浅色](visuals/2026-10-21-compact-lab-titles-native/entry-light-overview.jpg)与[顶栏暗色](visuals/2026-10-21-compact-lab-titles-native/page-dark-overview.jpg)。原创服饰夹具只证明布局和操作，没有真实生图质量结论。

本次无数据、备份、图片存储／裁切／引用、网络和费用协议变化，Room32／36实体／备份13不变，新增真实生图0次。沿实际影响选择上述4个方法，未跑无关全量JVM、数据库、原图哈希及备份审计。

正式交付沿用提交源码归档clean、原签名2.66.1覆盖及重复启动、既有中华皮肤设置保全、主包素材与源码核验，以及GitHub／Gitee同一正式附件匿名下载和公开版本查询。源码`8b5f383f`与标签已推送，2.66.1覆盖及重复启动保留原中华皮肤与3份既有设置SHA。GitHub／Gitee正式同一APK和SHA文件匿名完整下载、SHA／HEAD及公开最高正式版本查询通过，详见[回执](compact-laboratory-titles-release-2.66.2-verification.json)；正式包[个人页](visuals/2026-10-21-compact-lab-titles-native/release/release-personal.png)与同目录实验室已核对。正式AVD初次截图同样遇到System UI ANR，保存证据后只重启该AVD，原APK／设置不变，再次启动、设置核验与实拍均通过。不重跑用户要求忽略的App手动网络检查，不删旧附件。
