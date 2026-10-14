# 2.46.0 / 99 正式发布

七套皮肤增加独立透明边角插图；桌面图标采用用户选定的衣橱挂裙，支持自适应和单色主题；15 个功能图标精修小尺寸笔势，保留全部 51×7 图标身份和已认可的五导航。

源码提交 `31899045a8619539e0455429b14bf5aafccbb3da`，`v2.46.0` 标签指向该提交，基于最新 2.45.2 并保留淘宝详情和组合导入修复。Gitee 标签仅对应分发说明，完整源码以 GitHub 为准。

- [GitHub 正式发布](https://github.com/aalm1233/lolita/releases/tag/v2.46.0)
- [Gitee 正式发布](https://gitee.com/aalm1233/lolita/releases/tag/v2.46.0)
- [国内 APK](https://gitee.com/aalm1233/lolita/releases/download/v2.46.0/lolita-2.46.0.apk)

## 包与验证

`lolita-2.46.0.apk` 为从已提交源码执行 `clean assembleRelease` 得到的正式包，54,697,625 字节，非调试、包名 `com.lolita.app`、2.46.0 / 99；SHA256：

```
3e45a490ce73b2d6a2f082c2204d20c4e77678969d2d6b91fb01a9e31c5498de
```

正式证书与 2.45.2 相同：`d26185c57f5d2eee158127ebd2eab921bfb8457d9f2cced94ec3f6e40937530c`。集成后 clean Debug、170 JVM 测试和 13 Android 回归全部通过；正式包的七背景 alpha 和全部可见 RGB 像素与原始生成图完全一致。

独立 AVD 正式 2.45.2 覆盖升级至 2.46.0 成功：全部 29 表逐字段、198 服饰、182 现存图 SHA256 和皮肤/视图/隐私设置 DataStore 字节零差异。182 图包含只读夹具的 172 图及此前独立空库初始化遗留的 10 图，全部纳入基准。用户设备数据未操作。

正式包完成 34 场真实页面浅暗/七皮肤导航证据、实际服饰详情及滚动前后四张截图和系统桌面显示审阅。归档 `visuals/2026-10-14-art-seven-skins-release.png`、`visuals/2026-10-14-art-actual-launcher.png`、`visuals/2026-10-14-art-detail-*.png`；单独 native 背景、18dp 功能图标和 mask 矩阵也已保留。

GitHub 与 Gitee 使用同一个 APK 和 SHA256SUMS。Gitee 预发布附件匿名完整下载、SHA256 与稳定链接 HEAD 核验后才正式化，正式化前重新下载，正式化后再核验。GitHub 匿名完整 APK/校验下载、HEAD、最高正式 Release 与标签解析至源码提交均通过。证据不保存令牌或 CDN 临时链接。

已安装的正式 2.45.2 通过真实 AppUpdateRepository 自动检查获得 2.46.0，并显示当前/最新版本及下载按钮，实际截图 `visuals/2026-10-14-art-update-dialog.png`。公开 Gitee API 选择 2.46.0 为最高正式数字版本。

完整机器证据见 `skin-art-release-2.46.0-verification.json`；美术实现、测试边界和来源见 `skin-art-polish-2026-10-14.md`、`skin-art-assets-sources-2026-10-14.json`。本地 APK 保存至主仓库 `artifacts/release-2.46.0/lolita-2.46.0.apk`。
