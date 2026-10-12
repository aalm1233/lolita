# Gitee APK 分发与启动自动检查

## 下载渠道

面向本人和朋友，以覆盖安装为主。已创建公开仓库 [aalm1233/lolita](https://gitee.com/aalm1233/lolita) 作为国内 APK 分发渠道，应用源码继续位于 GitHub。Gitee 标签对应分发说明，不代表完整应用源码。客户端使用匿名 Gitee API，无需令牌；发布令牌只用于本机上传，不进入源码、APK 或仓库。

| 渠道 | 适用性 | 已核实限制 |
| --- | --- | --- |
| Gitee Releases | 最贴合现有 Git Release 流程，适合下一步试建国内镜像 | 普通仓库单附件 100M、仓库与发行附件合计 1G；必须实际验证目标 APK 的匿名下载与国内网络访问 |
| 蒲公英固定下载页 | 适合朋友之间分享，免自建存储与域名 | 网页下载免费；内测额度以账号后台为准，分发模式需审核。更新 API 要账号密钥，不放进 APK |
| 国内 COS / OSS 加自有域名 | 稳定直链和版本信息更可控 | 新桶默认公网域名存在 APK 下载限制，需自有域名；涉及域名、备案和流量配置 |

已将正式 2.41.0 / 91 APK 原样上传到 Gitee，匿名完整下载得到 47,911,281 字节，SHA256 与 GitHub Release 相同：`b48e412d2e348490a4373fae3d04948229e1bd0c071ad81e173eda229dc7ce6b`。原正式签名 SHA256 为 `d26185c57f5d2eee158127ebd2eab921bfb8457d9f2cced94ec3f6e40937530c`。

稳定附件路径为 `/aalm1233/lolita/releases/download/v<version>/lolita-<version>.apk`。实际重定向至 `foruda.gitee.com`，APK 类型为 `application/zip`，探测兼容此真实响应。浏览器打开原稳定地址，不保存 CDN 临时 token 地址。Gitee latest 可能是预发布版，客户端读取列表筛选正式数字版本后取最高。代码镜像不会同步发行附件。

蒲公英适合先手动分享下载页；仅把 APK 改为国内下载地址但仍通过 GitHub API 获取版本，不会改善 GitHub API 本身不可达时的更新发现。若后续要完整改善检查与下载，应一起接入公开的国内版本信息源。

没有大陆三网下载速度实测，不承诺实际速度。本次已创建 Gitee 仓库与发行附件，没有配置收费服务。

官方资料（2026-10-12 核对）：

- [Gitee 发行版与附件限额](https://help.gitee.com/repository/release/create)
- [Gitee 稳定附件链接格式](https://blog.gitee.com/2022/08/18/update/)
- [蒲公英分发与国内 CDN](https://www.pgyer.com/app/distribution)
- [蒲公英计费](https://www.pgyer.com/doc/view/download_fee)
- [蒲公英模式与短链接](https://www.pgyer.com/doc/view/faq_dev)
- [蒲公英更新 API](https://www.pgyer.com/doc/view/api_version)
- [COS 默认域名 APK 限制](https://intl.cloud.tencent.com/document/product/436/57456)
- [COS 自有域名](https://cloud.tencent.com/document/product/436/36638)
- [OSS APK 限制](https://help.aliyun.com/zh/oss/what-to-do-with-apkdownloadforbidden-reporting-an-error)

## 自动检查行为

- 新启动应用后异步检查一次，查询公开 Gitee 正式版本。
- API 最多 5 页 × 20 条、总 JSON 1MiB、整体 15 秒；超过边界明确失败，不以部分列表猜最新版。正式版本筛选不依赖 GitHub 独有字段或 Gitee 缺失的附件 size。
- 仅远端版本更高、存在明确 APK，并通过下载地址探测后弹出「发现新版本」。点击交给浏览器下载，覆盖安装继续由系统完成。
- 最新版本、本机版本更高、发布信息缺失、API 错误、超时、缺少 APK、下载地址错误、HTML 错误页均静默。
- MainActivity 持有同一个 AppUpdateViewModel，个人页手动检查与根层 AppUpdateHost 共用；手动检查仍能看到加载、最新版和错误反馈。
- 自动请求未完成时点击手动入口，复用请求并切到手动反馈；已取得 Release 时立即显示结果并停止链接探测。关闭后迟到结果不能重新打开。
- 不在 onResume 或 Application 初始化时检查。ViewModel 阻止重组与旋转重复请求；新进程恢复后新 ViewModel 仍检查。
- 下载探测使用 HEAD，不读取文件正文；连接/读取各 3 秒、整体 8 秒，最多 3 次 HTTPS 重定向。允许指定 Gitee/GitHub 与资产 CDN；最终必须为 HTTP 200、APK/二进制/ZIP 类型且 Content-Length 大于 0。
- 自动检查整体限制 15 秒。阻塞网络同时设置 socket 超时并 disconnect；协程超时防止迟到提示，不能宣称能立即中断所有底层 I/O。
- 探测只能确认当时可达，不能证明整个文件可下载、内容完整或用户稍后点击仍可用。下载仍使用原稳定资产地址。

新功能版本 2.42.0 / 92，不变更数据库、备份、安装权限或引入下载器。

## 发布步骤

1. `clean assembleRelease` 后核对包名、递增 versionCode、原签名和 SHA256。
2. 用本机令牌创建暂标预发布的 Gitee Release，标签 `v<major>.<minor>.<patch>`。
3. 通过 `POST /api/v5/repos/aalm1233/lolita/releases/{id}/attach_files` 上传 APK 与 SHA256SUMS。
4. 匿名完整下载并核对 SHA256，再改为正式；客户端筛选正式版，上传期间不提示不完整发布。

## 验证

先跑基线 JVM 测试，再增加自动状态与链接协议测试，确认缺失实现时失败；完成实现后验证全量 JVM 测试和 assembleDebug。

回归覆盖 Gitee 正式版本选择、自动提示前隐藏、较新且链接可用、相等/较旧、缺 APK、网络/解析/探测失败、重复启动、自动转手动、关闭后迟到结果、HEAD/CDN/ZIP、HTML/空文件、不可信地址与重定向循环。真实 Compose 的 AppUpdateHostTest 覆盖提示与静默、同 VM 重挂载去重、新 VM 首次检查；Host 重挂载测试不等价于真实进程死亡。测试不修改线上发布资料。

112 项 JVM 测试、assembleDebug、assembleDebugAndroidTest 通过；独立 AVD 的 8 项真实 Compose 测试通过。`clean assembleRelease` 通过，正式包为 2.42.0 / 92，保持既有签名；47,927,665 字节，SHA256 为 `f31c947503a51723bbdb02ae9224a75926c2cd5c3e68055f9bf00e7f113034ac`，Gitee 匿名完整下载一致。

独立 Release AVD 从 2.40.2 / 87 同签名覆盖安装，564 条原业务记录、6086 个字段、4 张原图、336 个图片引用及原设置已核对，新增房间及四品牌补图符合预期。旧数据订单/付款等为空，此次快照不能证明这些非空关系的升级保全。数据库完整性及外键检查通过。验证记录见 `docs/gitee-release-2.42.0-verification.json`。
