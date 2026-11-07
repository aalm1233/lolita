# 2.78.2 本机诊断与完整升级路径核查

小米MIX Flip2用户确认广场服务入口和JSON导出都会直接退出。2.78.1修复的公开目录null不是已确认现场根因。本轮没有改写私人数据库、清理缓存、改Compose依赖或重排地址弹窗。

## 实现

- Application早期安装尽力记录的处理器；记录失败仍将同一个线程和原异常交给原系统处理器。不会吞异常或尝试继续损坏状态。保留64KiB应急空间，OOM记录仍只能尽力；不承诺原生/系统杀进程一定有Java栈。
- 文件只含允许的版本/型号/API/时间、Java堆状态、异常类型与源代码位置。不读取异常消息、toString、线程名、SQL、地址、请求或系统trace；原因/抑制异常有深度/节点/帧数和32KiB限制。最新fatal与backup-export分开保留于noBackup目录，不进入云备份/衣橱备份。
- 个人页使用既有SettingsMenuItem及七套FileOpen图标。系统ACTION_CREATE_DOCUMENT选择TXT保存位置；取消不导出；文件失败保留原诊断。ViewModel持有忙碌和结果状态，IO在后台执行。
- API30+仅读取本包进程最近退出的数值原因/状态/时间/PSS/RSS/importance，不读描述、进程状态字节和trace。普通备份导出失败也保留脱敏栈，原有用户提示及校验保留。

Room34/45实体/备份16、图片和公开服务均不变，无新增权限或费用，无自动上传。前置图见[设计](plans/2026-11-07-crash-diagnostics.md)；真实七皮肤对照见[原生矩阵](visuals/2026-11-07-crash-diagnostics/native/contact-sheet.png)，小屏大字与真实系统选择器取消均通过。

## 验证

7项JVM通过：异常消息不读取、路径/内容不泄漏、循环/大栈/分叉原因图上限、独立覆盖/超限保全、写入失败仍委派、原处理器异常不吞。

13项去重正常设备方法通过（正常11项、小屏3项，诊断导出方法重复一次）：6项升级链方法包含34个直升起点+5个其他子场景；真实个人页七皮肤浅暗、320dp两倍字系统保存位置打开/取消、实际ContentResolver保存及失败保全、备份ZIP/JSON/CSV与普通历史错误、广场地址真实IME/输入/取消。六项升级方法和完整逐标签分析见[每版本记录](square-all-upgrade-paths-2026-11-07.md)。

另在显式启用的androidTest探针中让独立AVD应用进程真实未捕获异常退出；原系统处理器终止进程、重启后662字节脱敏记录仍在，保留原类型/代码位置，合成私密消息不在导出中。探针只在androidTest APK，正式应用无故意崩溃入口。

源码编译与定向结果保留于本机`~/Downloads/lolita-mixflip2-diagnostics-20261107/`。设备仅为Android15/API35 AVD；尚未取得MIX Flip2现场异常，当前版本为诊断补丁，不宣称修复。

## 正式发布

源码与标签`6733ce45`已普通推送至GitHub master／v2.78.2；仅提交源码的git archive完成clean Release及lintVital，原正式签名不变。APK 84,021,653字节，SHA256 `86522f25529d0c49a74571aedbe277d7b112c521a2f85e62a7094dd44407d590`。37项目assets和24张既有图片资源字节保持，androidTest故意崩溃探针未进入正式DEX。

独立AVD从已发布2.78.1原包同签名覆盖，首启／冷启、完整App实际JSON导出160物品（669,351字节）、真实系统保存位置导出诊断TXT均通过；45表全部行指纹、629本机原图SHA及持久设置与升级前一致。当前测试基准指针和原ZIP SHA已重新核对，原数据与图片不复制到公开附件。

[GitHub](https://github.com/aalm1233/lolita/releases/tag/v2.78.2)与[Gitee](https://gitee.com/aalm1233/lolita/releases/tag/v2.78.2)正式发布同APK／SUMS，两个平台匿名完整GET/SHA/HEAD及GitHub源标签校验通过；Gitee配额下仅清理最旧2.71.0 APK，先核验GitHub新鲜匿名完整下载与三份本机原副本，旧发布页／SUMS保留，见[清理回执](gitee-apk-cleanup-2.71.0-2026-11-07.json)。完整[发布回执](crash-diagnostics-release-2.78.2-verification.json)。

**限制仍保留**：MIX Flip2现场根因未确认。本轮附加窗口探索发现AOSP 840×840像素窗口叠加IME时旧地址弹窗内容会被遮挡；进程仍存活、无新异常，恢复窗口后原输入仍在，已取消未保存。该既有布局问题单列待修，不能与两处直接退出混同。本版不改该弹窗。

Gitee公开更新API仍403，原签名App手动检查实际显示「Gitee 暂时拒绝访问或请求过于频繁，请稍后重试」；不计作检查更新通过。稳定APK匿名下载正常，需通过发布页手动覆盖安装。安装后先复现一次，再重开个人页「导出诊断记录」，提供TXT后继续定位，无需卸载或清除资料。
