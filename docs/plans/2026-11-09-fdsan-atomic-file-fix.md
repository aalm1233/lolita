# 基于手机原生栈的AtomicFile兼容修复

新日志由2.78.4／160导出，退出时间2026-11-09 12:00:50.800，导出时间12:01:25.418（北京时间）。reason5/status6，37帧明确出现`SquareRepository.writeNetworkState → android.util.AtomicFile.finishWrite → F2fsUtils.nativeCommitAtomicWrite → close → android_fdsan_close_with_tag → fdsan_error`。导出版本不单独证明历史退出版本，但最新退出与导出相隔约35秒，且用户明确反馈最新版本。原始TXT只留本机。

这是广场后台刷新写网络状态的实际触发链路，没有经过Haze、Room迁移或BackupManager。只能定位此次退出；不能由一个栈断言每次齿轮/JSON退出均同因，也不能在没有OEM实现和abort_message时断言内部究竟是重复关闭还是其他FD标签错误。上次Haze补丁解决了另一个复现缺陷，未匹配到此手机现场。

## 修复边界

将应用直接使用的系统`android.util.AtomicFile`改为已依赖的`androidx.core.util.AtomicFile`，覆盖广场目录/游标/地址、心愿复制回收日志、识图/图像服务本机配置与头模偏好五处源码。精确core1.15.0 AAR字节码确认AndroidX独立实现：写`.new`、FileDescriptor.sync、关闭流、rename，不委派系统AtomicFile或F2fsUtils。保留原文件名/JSON/密文/Keystore别名、原锁、失败回滚和`.bak/.new`兼容，不清用户数据、不停用同步、不降低原子写保证。

无新增界面或视觉变化，无新权限、联网、费用或自动任务；Room34/45实体/备份16不变。既有Haze补丁保留。本轮无需新界面图，验收覆盖受影响的数据持久化与既有入口。

## 验收计划

1. 历史base、`.bak`及未完成`.new`、写入失败回滚和重启读取；广场实际重复刷新及游标/目录摘要匹配，旧地址保留，失败仍静默。
2. 心愿提交/回滚/孤儿日志保护，以及本机服务选择/加密凭据/头模偏好的现有定向用例；仅合成密钥、独立目录，不读写用户真实密钥，不调用付费服务。
3. Debug、提交源码归档clean原签名Release、2.78.4覆盖启动和必要数据/图片/配置保全，正式App齿轮/IME与JSON导出。
4. 依项目约定发布2.78.5／161至双平台，验证同包/完整下载/源码标签和正常更新入口。AOSP模拟器无手机OEM的F2FS扩展，不能用未崩溃冒充该固件复现；以手机栈、精确库实现及实际调用字节码证明故障入口已绕开，目标手机仍需回验。

参考：[AOSP Android15 AtomicFile](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/core/java/android/util/AtomicFile.java)、[AndroidX AtomicFile独立实现](https://github.com/androidx/androidx/blob/androidx-main/core/core/src/main/java/androidx/core/util/AtomicFile.java)。参考主分支不代替本次精确1.15.0 AAR字节码核验。

## 开发验收

Debug/AndroidTest构建及22项定向设备方法通过。新增历史原子文件夹具恢复后连续20次刷新，逐次核对目录SHA与游标状态绑定；离线失败仍保留缓存、不产生消息。心愿提交/回滚/孤儿日志、识图/图像服务独立加密配置、头模偏好及备份导出/预览通过，既有七皮肤浅暗服务弹窗操作通过。五处调用者的全部相关Debug类字节码均指向AndroidX，未调用系统AtomicFile；精确core1.15.0实现无F2fsUtils委派。

本轮只换持久化实现，不改纯Kotlin业务逻辑，因此选用实际Android文件/Keystore/Room/入口定向方法，没有无关JVM全量或全库迁移重放。结果见`docs/fdsan-atomic-debug-verification-2026-11-09.json`。正式包和目标手机回验尚不能由这些开发检查替代。

正式包采用范围、主缓存检查未通过的边界，以及最终分发结果见[实施记录](../fdsan-atomic-implementation-2026-11-09.md)与对应发布回执；目标手机回验仍待反馈。
