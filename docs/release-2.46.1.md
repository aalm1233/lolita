# 2.46.1 / 100 正式发布

表格订单导入默认逐件填写，保留批量模式；全款、定金、尾款、待配对可快速筛选和修正。配对显示对应商品，支持更换及解除，改变配对后须重新核对受影响的资料。原始金额草稿在切换商品或模式时保留，源订单汇总包含全部选中资料，导入前确认本次处理与跳过的范围；小屏、大字体底部控件保持可读。

源码提交 `1c3690d98550572f267069379feb827d420d3533` 已推送 GitHub；`v2.46.1` 标签指向该提交。Gitee 标签对应分发说明，完整源码以 GitHub 为准。

- [GitHub 正式发布](https://github.com/aalm1233/lolita/releases/tag/v2.46.1)
- [Gitee 正式发布](https://gitee.com/aalm1233/lolita/releases/tag/v2.46.1)
- [国内 APK](https://gitee.com/aalm1233/lolita/releases/download/v2.46.1/lolita-2.46.1.apk)

正式包由已提交源码执行 `clean assembleRelease -Pkotlin.incremental=false` 得到，非调试、包名 `com.lolita.app`、2.46.1 / 100；大小 54,697,625 字节，SHA256：

```
6d09531b6f6de90dc524a3e532cf77a3fea99b1232dc0c1fd58084231e2276eb
```

正式证书与旧包一致：`d26185c57f5d2eee158127ebd2eab921bfb8457d9f2cced94ec3f6e40937530c`。发布前 Debug 构建、170 项 JVM 测试和 8 项独立 AVD Compose 交互回归全部通过。复查曾复现金额草稿丢失、订单汇总漏算、旧核对状态和极端字体挤压，修复与边界记录见 `order-import-interaction-review-2026-10-14.md`。

独立 AVD `Lolita_Release_2392_Test` / `emulator-5558` 从正式 2.45.2 / 98 同签名覆盖升级到 2.46.1 / 100；冷启动后全部29张表逐字段、198件服饰、172张原图片SHA256和设置DataStore字节保持一致，无遗漏图片引用。没有清理用户设备资料。正式包的个人页及订单导入入口实际打开，默认 AndroidViewModel 构造成功，截图为 `visuals/2026-10-14-order-import-release.png`。

两个平台使用同一个 APK 和 SHA256SUMS；Gitee 预发布先匿名完整下载核验，正式化前重新下载，正式化后再次核验。GitHub/Gitee 正式附件的匿名完整下载、SHA256、稳定链接 HEAD 和 GitHub 源码标签均通过；公开 Gitee 更新 API 将 v2.46.1 选为最高正式数字版本。

完整机器证据见 `release-2.46.1-verification.json`。本地正式包与校验文件位于 `artifacts/releases/2.46.1/`；该包替换先前未发布的本地构建。
