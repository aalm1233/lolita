# 2.47.0 / 104 正式发布

尺码录入改为下拉选择，内置 XS/S/M/L/XL/XXL/均码，可选“未设置”清空；旁侧“＋”快速添加并立即选用自定义项，下次继续可选。服饰编辑和淘宝订单逐件/批量导入共用，保留旧尺码，去首尾空格并忽略大小写去重。七套皮肤沿用各自图标与配色，颜色、尺码通栏适应小屏大字。

自定义候选为本机设置；已使用尺码继续随原 Item.size 备份恢复，恢复后自动读取为选项。数据库版本仍为 26，备份格式不变。实现与回归见 `size-selector-2026-10-15.md`。

源码 `3e1a444f1e04f03770e52ccdc6b211dd8926558f` 已推送 GitHub，标签 v2.47.0 指向该提交。Gitee 标签对应分发说明，完整源码以 GitHub 为准。

- [GitHub 发布](https://github.com/aalm1233/lolita/releases/tag/v2.47.0)
- [Gitee 发布](https://gitee.com/aalm1233/lolita/releases/tag/v2.47.0)
- [国内 APK](https://gitee.com/aalm1233/lolita/releases/download/v2.47.0/lolita-2.47.0.apk)

从已提交源码执行 `clean assembleRelease`，包名 com.lolita.app，2.47.0 / 104，非调试，大小 54,730,393 字节。SHA256：

```
a1245701d8a4ec9de5dc23c64125b2d2298e392f2fa22a230a3ea55fd8debdfb
```

原正式证书 SHA256 一致：`d26185c57f5d2eee158127ebd2eab921bfb8457d9f2cced94ec3f6e40937530c`。Debug 构建、177 项 JVM、22 项设备回归通过（ItemSizeSelectorTest 5 项、OrderImportInteractionTest 17 项，0 失败/跳过），独立代码复核未发现问题。真实七皮肤浅暗、320dp/两倍字体实拍见 `visuals/2026-10-15-size-selector-skins.png`。

独立 AVD Lolita_Release_2392_Test / emulator-5558 从正式 2.46.4 / 103 同签名覆盖升级，冷启动后 29 张表逐字段、198 件服饰、172 张原图片 SHA256 和设置 DataStore 字节全部保持一致，无遗漏图片引用。正式包实际打开尺码下拉，核对内置选项并打开“＋”自定义弹窗，随后取消退出，不保存资料；实拍见 `visuals/2026-10-15-size-selector-release.png`。设备回归仅使用独立 AVD，未清理用户设备资料。

双平台使用同一个 APK 和 SHA256SUMS。Gitee 先预发布，匿名完整下载/摘要与 HEAD 验证后正式化，并再次核验正式附件及公开最高数字版本更新入口；GitHub 正式附件匿名完整下载、摘要、HEAD 及源码标签核对一致。机器证据见 `release-2.47.0-verification.json`，本地正式 APK、SHA256SUMS 和构建/测试日志位于 `artifacts/releases/2.47.0/`。
