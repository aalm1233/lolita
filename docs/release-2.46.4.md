# 2.46.4 / 103 正式发布

服饰顶部筛选入口由“待补尾款”统一为“待付款”，纳入PENDING_PAYMENT与PENDING_BALANCE，修复普通待付款漏筛。主件和小物均可筛出，属性、搜索、排序及视图模式继续组合；真实账本付清/撤销时清单和共价小物实时刷新。不修改实体细分状态、账本、备份或数据库版本。

源码 `63ebf65da15f7538d280a04fd469d5de1fda1940` 已推送GitHub，v2.46.4指向该提交。Gitee标签对应分发说明，完整源码以GitHub为准。

- [GitHub发布](https://github.com/aalm1233/lolita/releases/tag/v2.46.4)
- [Gitee发布](https://gitee.com/aalm1233/lolita/releases/tag/v2.46.4)
- [国内APK](https://gitee.com/aalm1233/lolita/releases/download/v2.46.4/lolita-2.46.4.apk)

从已提交源码执行 `clean assembleRelease`，包名com.lolita.app，2.46.4 / 103，非调试，大小54,714,009字节。SHA256：

```
ea8b2e00db9f3eb5e680fd613e5f3497d23178fc0ce49b54120d104c1c39a90b
```

原正式证书SHA256一致：`d26185c57f5d2eee158127ebd2eab921bfb8457d9f2cced94ec3f6e40937530c`。Debug构建、177项JVM和18项设备回归通过（新PendingPaymentFilterTest 4项、OrderBusinessTest 12项、PurchaseReviewConsistencyTest 2项），独立代码复核未发现问题。新增回归先复现期望5件仅显示2件，再验证修复；实现与真实页面见 `pending-payment-filter-2026-10-15.md`。

独立AVD Lolita_Release_2392_Test / emulator-5558 从正式2.46.3 / 102同签名覆盖升级到2.46.4 / 103，冷启动后29张表逐字段、198件服饰、172张原图片SHA256及设置DataStore字节全部保持一致，无遗漏图片引用。正式包实际打开服饰页并观察到新“待付款”入口（`visuals/2026-10-15-pending-payment-filter-release.png`），个人页/订单导入入口也正常打开。未清理用户设备资料。

本地正式APK、SHA256SUMS及构建/回归日志位于 `artifacts/releases/2.46.4/`。

双平台使用同一APK和SHA256SUMS。Gitee预发布在正式化前匿名完整下载/HEAD核验，正式化后再次核验；GitHub正式附件匿名完整下载、SHA256、稳定地址HEAD及源码标签均通过。公开Gitee更新API将v2.46.4选为最高正式数字版本。机器证据见 `release-2.46.4-verification.json`。
