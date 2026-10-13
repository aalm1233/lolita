# 2.44.1 正式发布验证

正式源码：cf50a05dfe67e85d0032d858e738d7d4e4a83a70，GitHub标签v2.44.1；版本95，沿用正式签名。从该源码clean assembleRelease成功。

GitHub与Gitee正式版本均为v2.44.1，附件lolita-2.44.1.apk与SHA256SUMS齐全。两个平台匿名完整下载均为48042353字节，SHA256一致：bbdb38fa10785e89ddcb3dbe84e5c6b699db9eb5c9cb95d20026d96215d2d807。

Gitee稳定APK地址HEAD为302→302→200，最终foruda.gitee.com返回非空application/zip；符合应用自动更新探测规则。GitHub latest和Gitee最高正式版本API均返回2.44.1。详细公开验证见release-2.44.1-verification.json。

发布页：
- https://github.com/aalm1233/lolita/releases/tag/v2.44.1
- https://gitee.com/aalm1233/lolita/releases/tag/v2.44.1

凭据继续保存在仓库外本机配置，没有进入源码、APK或公开附件。应用代码改动以后默认完成正式发布，用户通过“个人→检查更新”下载覆盖安装测试。
