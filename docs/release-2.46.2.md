# 2.46.2 / 101 正式发布

逐件订单导入采用叠层浮卡，主卡突出当前商品，卡缘与进度说明预览下一件。上一件、下一件和导入按钮采用独立悬浮卡片，键盘打开时操作保持可达，切换商品保留金额草稿。金额与付款优先展示，服饰资料可折叠，缺失身份自动展开；批量编辑及定金尾款配对继续复用原业务逻辑。

源码提交 `95d44ab9807f751c6465e1a3ee549d191b34328a` 已推送 GitHub，`v2.46.2` 指向该提交。Gitee 标签对应分发说明，完整应用源码以 GitHub 为准。

- [GitHub 正式发布](https://github.com/aalm1233/lolita/releases/tag/v2.46.2)
- [Gitee 正式发布](https://gitee.com/aalm1233/lolita/releases/tag/v2.46.2)
- [国内 APK](https://gitee.com/aalm1233/lolita/releases/download/v2.46.2/lolita-2.46.2.apk)

从已提交源码执行 `clean assembleRelease`；包名 com.lolita.app，2.46.2 / 101，非调试，大小 54,714,009 字节。SHA256：

```
4ba572b2b829303116f77f9c0ab961f9801bf50dc57077b1c948e8215a3ed187
```

正式证书与旧包一致：`d26185c57f5d2eee158127ebd2eab921bfb8457d9f2cced94ec3f6e40937530c`。Debug 编译、170 项 JVM 和 13 项独立 AVD Compose 回归通过，含七皮肤浅暗实拍、320dp/两倍字体和真实数字键盘。暗色进度标题问题通过真实文字像素回归先复现再修复。独立代码复核无运行问题；实现及实拍见 `floating-order-cards-2026-10-15.md`。

独立 AVD `Lolita_Release_2392_Test` / emulator-5558 从正式 2.46.1 / 100 同签名覆盖升级到 2.46.2 / 101。冷启动后全部29张表逐字段、198件服饰、172张原图片SHA256与设置DataStore字节均保持一致，无遗漏图片引用。正式包的个人页及订单导入入口实际打开；默认 AndroidViewModel 构造正常。实拍为 `visuals/2026-10-15-floating-order-release-import.png`。验证在独立模拟器完成，没有清理用户设备数据。

本地正式 APK、SHA256SUMS 与构建/回归日志归档于 `artifacts/releases/2.46.2/`。

两个平台发布同一个 APK 与 SHA256SUMS。Gitee 预发布、正式化前及正式化后均匿名完整下载核验；GitHub/Gitee 正式附件匿名完整下载、SHA256、稳定链接 HEAD 和 GitHub 源码标签均通过。公开 Gitee 更新 API 将 v2.46.2 选为最高正式数字版本。机器证据见 `release-2.46.2-verification.json`。
