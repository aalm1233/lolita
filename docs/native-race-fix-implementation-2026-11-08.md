# 2.78.4 原生资源竞态修复交付

已将复现的Haze RenderScript生命周期问题修进正式包，源码`f6213efcf7a386d72b9431b6face2685353ac97f`，标签`v2.78.4`。输入回调的检查/接收、模糊计算及资源释放共享实例monitor，release幂等；挂起等待不持锁。图片准入校验显式使用SOFTWARE位图，保留原路径、私有归属、采样和严格完整性规则。

当前1.6.9源码保存在`third_party/haze`，保留Apache许可证和原始SHA清单，唯一上游源码差异为RenderScriptContext。运行时依赖比对没有其他版本变化，仅由本地库替代两项Maven Haze坐标；没有升级Haze2。归一化Kotlin内部module后缀后，公开JVM签名差异仅两处新增synchronized。

## 验证结果

- 原版有效复现累计4/4，均在AllocationIoReceive/GrallocConsumer链路SIGSEGV。只锁回调和释放的追加120轮虽未退出，却有3次计算异常，不能算完整修复。把计算纳入锁的隔离对照120轮零异常；正式补丁不依靠测试侧锁，也独立完成120轮零异常。完整蓝色像素、重复释放及释放后计算通过。
- 13项JVM与26项去重设备方法通过，包含23项旧图片/个人模型/备份回执方法、两个生命周期方法，以及实际NavHost的七皮肤浅暗服务弹窗/导航。小屏为320dp宽、两倍字体；测试仅为Compose idling暂停背景装饰运动，实际blur保留。首次未暂停装饰运动时的idling失败保留在开发回执，未当作原生退出或通过。
- 仅已提交源码归档的clean Release含lintVital通过；原正式签名、min26/target35及非debuggable核对通过，37项项目assets及24项原图片资源字节保持。Release字节码确认锁，临时探针和回归测试类均不在正式APK。
- 原签名2.78.3覆盖两次冷启动，45张表全行、160件物品、629张图片SHA及6份持久设置文件保持；没有卸载、清数据或回写原备份。正式App实际打开广场齿轮、真实IME输入后取消、导出并解析160件JSON通过。正常App保留背景运动，截图只留本机。Room34/45实体/备份16不变。
- GitHub/Gitee正式同包同校验附件，匿名完整GET/SHA及HEAD、源码标签与Gitee最高正式版本查询通过。实际原签名App手动检查显示当前/最新2.78.4及完整说明。Gitee配额不足按既有授权只删除最旧2.72.0 APK；先重新完整下载核对GitHub与三份既有本机副本，旧页、校验和其他附件保留。

用户img仍为2.78.2 v1日志，只记录SIGABRT而没有帧；实验为SIGSEGV。这个已修复的库缺陷还没有与MIX Flip 2现场栈匹配，不能把模拟器通过称作目标手机回验通过。诊断v2保留供后续比对。没有发布原用户JSON、图片、TXT或原生内存/寄存器。

完整回执见[native-race-fix-release-2.78.4-verification.json](native-race-fix-release-2.78.4-verification.json)，前置范围见[修复方案](plans/2026-11-08-native-race-fix.md)。

[GitHub正式发布](https://github.com/aalm1233/lolita/releases/tag/v2.78.4) · [Gitee正式发布](https://gitee.com/aalm1233/lolita/releases/tag/v2.78.4)。APK SHA256：`3b53e2b803202a5208814b486c33142122d3935a41ad45be871919357391a604`。

本轮启动的两台AVD已关闭并保留数据，临时签名引用及构建归档已清理，既有广场API/内网穿透未改。
