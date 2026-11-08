# MIX Flip 2 原生退出诊断

收到用户主动提供的本地 TXT：2.78.2 / 158、Xiaomi 2505APX7BC、API 35；最近主进程退出时间 2026-11-08 09:27:22.851 +08:00，reason=5、status=6，导出时间晚约 15 秒。Android 定义为原生崩溃及 SIGABRT。Java fatal 和备份普通失败记录均为空。这只确认退出类别，不证明两个入口同因，不证明迁移、内存不足或渲染故障。

## 实施范围

沿用个人页现有「导出诊断记录」及系统保存选择器，布局、文案、皮肤均不变；本轮仅后台诊断，无新增界面图适用项。报告升级为 v2，Java 历史 v1 文件继续原样包含。导出时查询本应用主进程最近十条退出记录，最新退出和最近原生退出分节并标时间，避免覆盖升级等退出掩盖原生证据。

API 31+ 且退出原因为 CRASH_NATIVE 时，读取官方 ApplicationExitInfo.traceInputStream 的 tombstone protobuf。最多读取 4 MiB + 1 字节，超过上限整段拒绝；仅解码信号数字、架构数字、崩溃线程的前 64 个调用帧。模块只保留合法代码文件基名，函数只接受代码符号字符，地址仅模块相对 PC / 函数偏移及十六进制 Build ID。屏蔽命令行、完整路径、线程名、abort_message、日志、寄存器、内存转储、文件描述符、CrashDetail 和未知字段；不保存原始 protobuf，也不自动上传。

原生记录可能已被系统环形缓冲回收，明确 unavailable / malformed / too-large / no-crash-thread，不伪造异常。无新崩溃时也可尝试读取升级前仍被系统保留的记录。导出运行在既有 IO 协程，不在启动或正常浏览时读取 trace，不加入 native 库或依赖，不清理或修复私人数据。Room34、45 实体、备份16保持。

## 定向验收与限制

JVM 用含私人标记的 tombstone 合成夹具验证字段过滤、字段乱序、错误 wire/长度/varint、输入和输出上限。独立 AVD 显式触发测试包 SIGABRT，重新拉起测试读取真实系统记录并经真实 resolver 导出；普通测试不触发进程退出。沿用诊断菜单取消/保存测试，正式签名覆盖 2.78.2、启动及诊断导出。未改业务持久化或 UI，不重复历史 39 迁移子场景和七皮肤截图。

正式发布仍属于诊断增强，不能称已修复 MIX Flip 2。实际手机需要用增强版导出原生调用位置；若系统已回收旧记录，需要重新复现后导出。

依据：[Android ApplicationExitInfo](https://developer.android.com/reference/android/app/ApplicationExitInfo#getTraceInputStream())、[AOSP tombstone.proto](https://android.googlesource.com/platform/system/core/+/refs/heads/main/debuggerd/proto/tombstone.proto)、[AOSP signal.h](https://android.googlesource.com/platform/bionic/+/e2aefc46e/libc/kernel/uapi/asm-generic/signal.h)。原用户日志仅保留本机 img，不加入源码或发布附件。
