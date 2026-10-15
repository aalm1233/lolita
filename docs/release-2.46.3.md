# 2.46.3 / 102 正式发布

已识别的有效定金尾款配对相邻展示，先定金、后对应尾款。逐件、批量和筛选共用排序；更换、解除或角色修改后及时重排，保留当前商品。金额草稿、图片、配对和导入回调继续使用原索引，避免展示排序造成错位。

源码提交 `1b73aa4f4317dc42028a122bd675299f4bc68cca` 已推送 GitHub，v2.46.3标签指向该提交。Gitee标签对应分发说明，完整应用源码以GitHub为准。

- [GitHub 发布](https://github.com/aalm1233/lolita/releases/tag/v2.46.3)
- [Gitee 发布](https://gitee.com/aalm1233/lolita/releases/tag/v2.46.3)
- [国内 APK](https://gitee.com/aalm1233/lolita/releases/download/v2.46.3/lolita-2.46.3.apk)

从已提交源码执行 `clean assembleRelease`，包名 com.lolita.app，2.46.3 / 102，非调试，大小 54,714,009字节，SHA256：

```
d5844bd56f6065219fce5a5b391ff01302629c8b1e5b6ee76143137136e7a3e1
```

正式证书与旧包一致：`d26185c57f5d2eee158127ebd2eab921bfb8457d9f2cced94ec3f6e40937530c`。Debug构建、177项JVM及16项独立AVD真实Compose回归通过；独立代码复核未发现问题。新增回归先复现原首件尾款错误，再验证定金优先、双向导航/草稿、批量筛选原索引纠错及动态重排。实现和实拍见 `order-pair-ordering-2026-10-15.md`。

独立AVD Lolita_Release_2392_Test / emulator-5558 从正式2.46.2 / 101同签名覆盖升级至2.46.3 / 102。冷启动后29张表逐字段、198件服饰、172张原图片SHA256及设置DataStore字节保持一致，无遗漏图片引用。正式包个人页及订单导入入口实际打开，默认AndroidViewModel构造成功；截图为 `visuals/2026-10-15-order-pair-ordering-release-import.png`。未清理用户设备资料。

正式APK、SHA256SUMS、构建和测试日志位于 `artifacts/releases/2.46.3/`。

两个平台使用同一APK和SHA256SUMS。Gitee预发布、正式化前及正式化后均匿名完整下载核验；GitHub/Gitee正式附件匿名下载、SHA256、稳定地址HEAD和GitHub源码标签核对通过。Gitee公开更新API选取v2.46.3为最高正式数字版本。正式化前的短时403已恢复，重新执行完整校验后完成发布；没有修改或重建已上传附件。机器证据见 `release-2.46.3-verification.json`。
