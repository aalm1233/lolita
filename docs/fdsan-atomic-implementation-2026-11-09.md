# 2.78.5 手机fdsan退出兼容修复

手机最新日志由2.78.4／160导出，退出于2026-11-09 12:00:50.800，约35秒后导出。37帧定位到以下检测/终止链路，原始TXT不入库：

```text
SquareRepository.writeNetworkState
android.util.AtomicFile.finishWrite
F2fsUtils.nativeCommitAtomicWrite
close / android_fdsan_close_with_tag / fdsan_error
```

这是后台刷新保存广场网络状态时的原生SIGABRT。`MainActivity.onStart`会启动刷新，SquareViewModel初始化、下拉和保存地址也会启动；它可与齿轮或JSON操作同时运行。此栈未进入Room迁移、BackupManager或上次Haze实验链路，因此不能再把那一实验缺陷当作此手机现场的匹配解释。

[Bionic fdsan说明](https://android.googlesource.com/platform/bionic/+/master/docs/fdsan.md)解释其检查FD关闭归属，且检测栈也可能是后续被影响的关闭点。本日志不含abort_message或OEM实现，故可确认检测链路，尚不能断言最初误关FD的代码、标签或时序；也不能用这一退出证明所有历史闪退均同因。

## 已采用的修复

将应用五处直接使用的系统AtomicFile改为既有`androidx.core.util.AtomicFile`：广场目录/游标/地址、心愿复制日志、识图配置、图像服务配置和头模偏好。精确core1.15.0 AAR与正式构建调用者字节码均验证为独立实现，不委派系统AtomicFile/F2fsUtils；仍写临时文件、同步、关闭和原子替换，保留原有互斥/回滚、文件名、格式、密文及Keystore别名。没有关闭fdsan、取消原子性、停用同步、清私人数据或改Room34/45实体/备份16。Haze已验证补丁保留。

本轮无界面/视觉变化或新权限，无新图稿适用项。长期原子文件约定同步维护到AGENTS.md与产品规范。

## 验收与边界

- Debug/AndroidTest构建及22项定向设备方法通过：旧base/bak/new夹具恢复、连续20次实际内网Repository刷新及游标/目录SHA绑定、失败静默保留缓存、心愿提交/回滚/孤儿日志、服务独立加密配置、头模偏好、JSON/ZIP/CSV导出及可恢复格式预览，以及既有七皮肤浅暗弹窗。未改纯Kotlin业务，不重复无关JVM或全迁移矩阵。
- 提交源码归档的clean原签名Release含lintVital通过，37项项目素材和24项原图片资源字节保持；正式字节码的五处调用者均指向AndroidX。2.78.4原包SHA核验后覆盖两次冷启动，45表全行、160件物品、629张图片SHA及6份原持久设置保持；正式App齿轮、保存地址、真实IME取消及160件JSON导出/解析通过。
- 测试临时保存过LAN地址，已恢复原先“无显式地址文件”的状态，只删除测试新建的配置文件；原6份设置未改。原备份未回写，没有卸载/清理私人数据。
- 正式包主缓存走查的公网请求在本机模拟器超时；临时切换LAN触发历史全量重放时又返回IllegalArgumentException，均静默保留缓存。此主缓存检查**未计为刷新成功**，未清缓存制造通过；独立目录/内网20次刷新另有通过证据。旧地址/配置存在状态已恢复，该重放问题未在本轮更改数据语义来处理。
- AOSP模拟器没有手机固件的F2FS原子提交扩展，本轮没有冒充该OEM路径重现。补丁依据手机栈绕开现场接口，物理手机实际效果仍待更新后回验。

源码/标签：`8ddf28a5690901e119bb21ef4d4df9407d6b401f` / `v2.78.5`。原签名APK SHA256：`ca6e4cb856a6762d2ab969d6ed5e96ff1cf71b00d4210674811ffcf444a5fc52`。

最终分发、更新入口与清理结果见[发布回执](fdsan-atomic-release-2.78.5-verification.json)，手机证据见[脱敏帧](fdsan-phone-evidence-2026-11-09.json)，前置范围见[修复计划](plans/2026-11-09-fdsan-atomic-file-fix.md)。

[GitHub正式发布](https://github.com/aalm1233/lolita/releases/tag/v2.78.5) · [Gitee正式发布](https://gitee.com/aalm1233/lolita/releases/tag/v2.78.5)。原用户数据、图片、TXT、寄存器及内存均未发布，付费请求0。

## 历史缓存重放后续澄清（2026-11-09）

上面的正式包失败观察保留，但不能继续将它泛化为用户历史缓存兼容故障。只读提取原测试AVD公开目录后发现，15条事件的9个描述字段全部为null，符合2.78.1为重现旧空指针而注入并保留的[专用测试夹具](square-historical-null-implementation-2026-11-07.md)，不是反馈者提供的缓存。相同事件revision在服务端仍有原始描述，因此合并按既有规则拒绝同版本内容冲突。

离线编译当前实际`SquarePackage.kt`与`SquareBrowse.kt`：原缓存在seq1复现同版本冲突；仅在内存恢复上述9个事件描述字段，保留其他数据和revision，15条服务端更新全部合并通过，保留31商品；从空目录回放也通过。不能据此放宽同版本冲突、售卖状态或图片校验，更不需要为这个测试残留发布App修复。若确需重置测试环境，可使用既有开发者工具清空公开缓存后重新同步；衣橱、心愿和私人图片不在此清理范围。

本次没有回写AVD缓存、改App/服务、升版本或重发APK，模拟器已关闭且保留数据。此前把它列为一般性待修功能问题的表述予以纠正。原公网超时及手机回验不在本次处理范围。离线控制不冒充正式App重新联网通过；[脱敏核查回执](square-cache-replay-investigation-2026-11-09.json)与[可复查Kotlin探针](diagnostics/square-cache-replay-2026-11-09.kt)保留范围与限制。
