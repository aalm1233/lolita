# 原生崩溃代码路径审计

本轮响应用户“基于原生崩溃定位”，仅做代码追踪、隔离实验和文档归档，应用仍为2.78.3 / 159；没有改生产代码、数据库、图片引用或发布新APK。无新增界面或视觉设计适用项。原用户TXT仍只留本机img，原生内存/寄存器等实验日志仅留本机；此处只归档模块基名和代码符号。

## 已确认与尚未确认

**已定位到当前依赖Haze 1.6.9中一个可复现的原生崩溃点：RenderScript输入缓冲回调与资源释放竞态。尚未确认它就是MIX Flip 2的现场根因。**

用户提供的文件仍是2.78.2的v1诊断：最近退出reason5、status6（SIGABRT），无Java异常或原生调用帧；用户不能确定这次对应齿轮还是JSON。此次有效实验退出为reason5、status11（SIGSEGV）。相同原生退出类别不意味着相同原因，不能将这两组记录等同。具体证据见[机器回执](native-path-audit-2026-11-08.json)。

## 两个入口实际执行什么

| 入口 | 本地源码路径 | 能确定的边界 |
| --- | --- | --- |
| 广场齿轮 | `SquareScreen.kt`的onClick只复制serviceUrl、清错误、打开AlertDialog | 点击时不保存地址、不调用配置/刷新、不发新请求、不执行数据库迁移；既有异步订阅和图片请求可能仍在运行 |
| JSON导出 | `BackupRestoreViewModel → BackupManager.serializedResult → snapshot → validateStoredValues / validateWardrobeImages → Gson → Downloads` | 在IO协程，不只是JSON字符串操作；草稿、识图、收纳及生成任务的部分历史图片引用仍要验证和解码，不是逐张解码全部普通衣橱相册 |

广场弹窗本身没有Haze modifier；页面顶栏和导航栏共用Haze。两处问题尚未证明同因。普通Room结构校验/JSON损坏一般会成为Java异常，本次不能据此断言所有历史数据或SQLite native路径已排除。

## 可复现的RenderScript竞态

核对实际1.6.9 AAR字节码及[对应标签源码](https://github.com/chrisbanes/haze/blob/1.6.9/haze/src/androidMain/kotlin/dev/chrisbanes/haze/RenderScriptContext.kt)：输入缓冲监听器检查isDestroyed后调用Allocation.ioReceive；release设置该标志并销毁inputAlloc。两处没有共用锁，检查通过后仍可能被另一个线程销毁，之后native接收使用已释放对象。单独的Boolean检查无法保护这段资源使用。

同版本的后端选择判断同时依赖API与Canvas：API≥31且硬件Canvas用RenderEffect；软件Canvas仍可能进入RenderScript。因此API35不能直接排除该后端。尚未取得手机在故障时使用软件Canvas的证据；探针直接实例化库中的RenderScriptContext，不是对手机整个窗口流程的复现。

上游[修复PR #1294](https://github.com/chrisbanes/haze/pull/1294)确认过输入回调/释放竞态导致native crash，以共用锁保护liveness检查、ioReceive及release；该PR列出的验证设备API为30，不能把它作为小米API35同因证明。本轮进一步验证本项目旧AAR在API35上的同一资源问题。

隔离AOSP API35、host GPU，全部输入为自建纯色图片，无用户库存：

1. 完整准备512×512 Surface缓冲，经回调确认收到；串行计算五次，核对输出中心像素为蓝色。每个有效探针的串行基线均通过。
2. 正常绘制后续红色帧，使输入回调与计算/释放并发。原版两次独立进程均原生退出，分别在第0及第2轮之后；没有人为发送终止信号。
3. 对照只将原回调和release包在同一个实例monitor中，原库和输入流程不变。两次独立进程各60轮通过，合计120轮；进程存活，捕获的工作线程异常集合也为空。

两个有效原版失败的顶部函数均为：

```text
libRS_internal.so
android::renderscript::GrallocConsumer::lockNextBuffer
android::renderscript::rsi_AllocationIoReceive
android::renderscript::rsp_AllocationIoReceive
```

最初一次未准备输入缓冲的探针在libRSCpuRef的fetch_generic_asm发生SIGSEGV，已作为无效实验排除；另有一次反射getter名称错误的设置失败，已更正，也不计入有效复现。只让applyBlur与release并发、没有后续缓冲回调的30轮控制未失败。这里保留失败与控制，不将所有实验描述成通过。

[可复查探针](diagnostics/native-path-probe-2026-11-08.kt)不位于Android sourceSet。复查时仅在隔离debug AVD临时放入androidTest，显式使用raceProbe=true，guardedRelease=true/false做对照；普通测试不会触发竞态。原始系统崩溃日志不入库。

## JSON的硬件位图入口

`ImageFileHelper.isOwnedReadableImage`和`isOwnedReadableProductImage`调用ImageDecoder但未指定allocator。这两个校验结果不会用于界面绘制，解码之后直接recycle；此处仍可能分配硬件位图。[官方API说明](https://developer.android.com/reference/android/graphics/ImageDecoder#ALLOCATOR_DEFAULT)也将HARDWARE列为默认分配的通常结果。

API35实测同一张1600×1200合成PNG，复用当前采样/完整性参数：默认allocator=0得到HARDWARE；指定SOFTWARE=1得到ARGB_8888，尺寸一致，两种现有图片准入方法均接受原文件。**这确认了不必要的硬件分配入口，没有复现其导致手机SIGABRT。** 改用软件分配可以减少对GPU/Gralloc的依赖，但仍使用native图像解码器，不能称为绕过所有native风险。

## 依赖与其他native分支

- 完整releaseRuntimeClasspath解析显示UI、Foundation、Runtime、UI Graphics核心组件统一1.8.0，Material3为1.3.1；Haze仍为1.6.9。不能只根据项目BOM文字推断实际版本，当前未证实核心版本混用，也未找到可直接对应这次SIGABRT的Compose升级说明。
- 正式APK只打包graphics-path与datastore共享计数器两个native库家族。精确graphics-path 1.0.1字节码在API≥34使用平台PathIterator；本项目DataStore使用单进程coordinator，未见启用native共享计数器的路径。隔离图片探针中这两个so均未映射。此处是降低其优先级，不能用APK包含某so直接认定它参与了崩溃。
- 已映射libRS/libhwui/libart/libsqlite也不等于执行点或崩溃点。Android15/Xiaomi有[ART SignalCatcher报告](https://issuetracker.google.com/issues/472429844)，但当前手机没有GetNativeNiceness或SIGQUIT栈证据，不按品牌/Android版本套用该问题。

## 修复边界与后续比对

已经验证的库缺陷修复点是：在RenderScriptContext内部，让输入回调的状态检查/接收与release共享同步保护，避免释放后的native访问；还应核查计算线程的其他资源访问。对照是临时探针中的保护，并未作为生产修复交付。也可评估让软件Canvas只绘制透明材质回退、保留前景文字和操作，从而不启用该后端；不能直接隐藏整个顶栏/底栏内容，或未经核对就升级到不兼容的Haze2。

手机根因仍需与2.78.3报告的native-exit帧对应：若落到AllocationIoReceive/GrallocConsumer，当前竞态将成为直接匹配；若是libart、其他libhwui或libsqlite函数，应沿实际帧继续定位。现有reason/status两个数字无法确定哪个函数终止，也不应为了给出结论而更改数据库或批量清数据。

完成实验后已移除临时androidTest源、重建并还原不包含该探针的测试包，关闭本次AVD并保留数据。生产源码、正式2.78.3 APK、用户数据、既有广场API与内网穿透保持。

## 同日重试与修复范围纠正

用户要求再次尝试后，使用同一AVD冷启动和相同完整缓冲探针重新独立运行。原版两次在完成9轮及3轮后崩溃，均是新的SIGSEGV，顶部相对PC、BuildID和AllocationIoReceive/GrallocConsumer调用链与前轮一致。累计原版有效复现4/4。

仅锁回调/release的两次重试都完成60轮且进程未退出，但工作线程分别出现`NullPointerException + RSInvalidStateException`与`NullPointerException`；JUnit表面的OK不能覆盖探针捕获的异常，因此本轮两次均不算完整通过。此前两次零异常是当时实测结果，不构成该保护充分性的证明。

追加guardedCalculation=true，将applyBlur全部操作也放入同一个monitor后，两次各60轮完成、捕获异常为空。归档探针新增该显式参数，原有默认对照不变。该压力场景刻意并发计算和释放；不能据此断言手机一定走过同样调度。用户随后要求正式修复，采用的生产同步范围与验收见[修复方案](plans/2026-11-08-native-race-fix.md)。
