# 2.47.1 / 105 正式发布

修复本轮确认的十项逻辑与联动问题：未完结订单源实付、日期框时区、编辑器草稿重载、现代备份颜色文本、连写规格解析、统计精确下钻、穿搭取消关联、年历逾期刷新、图片生命周期和桌面穿搭入口。独立复核补充修复保存时编辑和屏幕取消等待导致重复新建的问题。实现及先失败后通过的依据见 `audit-logic-fixes-2026-10-15.md`。

源码 `a0abcbac2b86d20248eaa0366c89712c1016ee6a` 已推送 GitHub master，源码标签 v2.47.1 指向该提交。Gitee 标签对应分发说明，完整应用源码以 GitHub 为准。

- [GitHub 发布](https://github.com/aalm1233/lolita/releases/tag/v2.47.1)
- [Gitee 发布](https://gitee.com/aalm1233/lolita/releases/tag/v2.47.1)
- [国内 APK](https://gitee.com/aalm1233/lolita/releases/download/v2.47.1/lolita-2.47.1.apk)

从已提交源码执行 `clean assembleRelease`，包名 com.lolita.app，2.47.1 / 105，非调试，大小 54,746,777 字节。SHA256：

```
3fc79e439468bb0696cc32f8c7bd57f44eafaf81b63525cae7f68a6d2320e2d7
```

正式签名证书 SHA256 与 2.47.0 相同：`d26185c57f5d2eee158127ebd2eab921bfb8457d9f2cced94ec3f6e40937530c`。Debug 与 AndroidTest 构建、185 项 JVM、112 项真实 Room/Compose 设备回归全部通过，零失败、零跳过。覆盖编辑器 18、订单源证据 9、标签 3、日期框 3、年历时钟 2、复制取消 1、订单交互 18、完整性 8、订单业务 12、历史购买 13、导入恢复 2、完整穿搭 4、快捷穿搭 2、消费统计 4、商品导入 8、七皮肤尺码 5 项。独立数据和编辑器复核、全改动终审及最后测试接线复核均通过。

全量回归发现历史迁移测试验证到 23 后直接打开 26 版 Room，缺少后续迁移注册；仅补测试 builder 的 23→26 迁移，保留原断言，再跑全部 112 项通过。生产迁移链完整，数据库版本保持 26、备份格式保持 6，不批量改写已有金额或日期。

独立 AVD Lolita_Release_2392_Test / emulator-5558 从正式 2.47.0 / 104 同签名覆盖升级：29 张表、198 件服饰逐字段及关系一致，172 张原图片 SHA256 和设置 DataStore 字节保全，无遗漏图片引用。正式包快捷穿搭显式 Intent 冷/热启动 UI 检查通过，未保存记录；原生 launcher 挂载组件点击未自动化覆盖。测试未清理用户设备资料。

两个平台正式附件均已匿名完整下载并验证 APK 与 SHA256SUMS，摘要和大小一致，稳定链接的 HEAD 跳转链通过。Gitee 经预发布校验后正式化，再核验正式附件及公开最高数字版本更新入口；GitHub 源码标签与构建提交一致。机器证据见 `release-2.47.1-verification.json`，本地 APK、摘要、完整测试与构建日志在 `artifacts/releases/2.47.1/`。
