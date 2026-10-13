# 2.45.0 / 96 正式发布

淘宝商品支持勾选多个明确物品后逐件核对保存；每件保留完整描述、5张主图与本轮商品的全部28张详情原图。默认心愿，不产生价格或付款。取消后可继续同件，保存自动下一件，恢复及重试不重复建档；删除一件不影响另一件图片。

源码提交：`b9dafa67ddee68bd0f5a8cc65c9b6080410a2c3c`，GitHub正式标签`v2.45.0`指向该提交，master已推送。正式源码已移除临时测试宿主配置，执行`clean assembleRelease`成功。

APK：`lolita-2.45.0.apk`，48,058,737 bytes；SHA256：`b09adee79e7a549325059cd76d4c0b5d8ab9df0badc7ef0d57bffd1e4ba6578c`。包名`com.lolita.app`，versionCode96，非debuggable，沿用原正式签名。已覆盖安装并核对所有原表、196条服饰与86张图片完整；正式clean包实际网页登录与提取仍识别6个候选、5张主图及28张详情图。

154项单元测试和23项独立模拟器设备回归通过；25→26真实迁移保全升级前195条服饰旧字段和53张原图片，详情见`taobao-product-split-2026-10-13.md`。

- [GitHub正式发布](https://github.com/aalm1233/lolita/releases/tag/v2.45.0)
- [Gitee正式发布](https://gitee.com/aalm1233/lolita/releases/tag/v2.45.0)
- [国内APK下载](https://gitee.com/aalm1233/lolita/releases/download/v2.45.0/lolita-2.45.0.apk)

两平台匿名下载的全包均与本地SHA256一致，SHA256SUMS附件也一致。GitHub使用8段合法HTTP206匿名下载后完整重组核对；Gitee先预发布，匿名完整下载验证后转正式，公开API最高正式版为v2.45.0。稳定APK HEAD经两次302跳转至foruda.gitee.com，最终200、application/zip、48,058,737bytes；未保存临时CDN地址。正常“个人→检查更新”采用该Gitee正式版本和稳定附件地址。

Gitee标签对应分发说明，其正文注明上述GitHub完整源码提交。发布令牌仅保留本机仓库外600权限文件，未进入源码、APK或附件。来源与公开分发核验汇总见`release-2.45.0-verification.json`。
