# 广场商品外跳淘宝实施

按[前置设计](plans/2026-11-06-square-taobao.md)实现商品摘要下的“淘宝查看”。普通单品入口使用当前部件、颜色和尺码筛选后的来源；多链接主动选择，标题与价格阶段原文保留。尾款页同样提供查看，仍保持只读。外跳不自动收藏、录价、建单或付款。

`SquareTaobaoButton`共用七皮肤形状、颜色及既有OpenInNew图标。官方淘宝包优先，无法处理再交给系统HTTPS处理；双重失败使用可复制的中文提示。URI只接受taobao.com/tmall.com及其子域的标准HTTPS来源，不拼接用户私人字段。无有效来源入口禁用；来源文字继续保留。未新增路由、权限、后台网络或网页采集。

实际商品标题较长，原生两倍字检查发现默认TextButton胶囊会裁切文字，已改为皮肤cardShape和onSurface正文；失败提示操作独占下一行。测试对尾款LazyColumn显式滚动到实际按钮，未放宽可见性或点击断言。原图片Fit、相册放大、心愿保存与底栏保持。

数据影响：Room34、45实体、备份16及公开包v1不变；无新字段、设置、图像复制或清理，不改变历史Item、来源、账本或备份。纯页面与Intent改动，不重复不相关的全库/图片/备份往返审计。

验证按2.74.0至本次差异选择SquareBrowseTest的五个方法：外部Intent优先/回退/拒绝和不可信链接；实际来源选择/取消、颜色尺码上下文、尾款失败复制；七皮肤浅暗；缺失来源禁用；既有直接心愿/颜色/参数流程。测试使用独立内存Room与隔离文件目录，实际ACTION_VIEW由Context拦截，不启动淘宝或浏览器。受控接收不代表安装淘宝的真机验证。真实淘宝客户端和TalkBack人工朗读未执行。

原生对照：普通411dp/1倍字体及320dp/2倍字体各覆盖七套皮肤浅暗的商品、选择框、尾款三场景，另验证失败复制。最终截图、定向测试结果及发布回执在本轮目录归档；IME不涉及本入口，原搜索输入未改。

实施状态：2.75.0/152已正式交付；Debug与提交归档clean Release通过，源码和标签已推送，双平台附件匿名完整下载/摘要/HEAD通过。实际检查更新受Gitee限流影响，直接APK可用。

最终定向[测试回执](square-taobao-test-verification-2026-11-06.json)：5个去重设备方法通过，涉及最终视觉修订的两个方法在普通及小屏重新通过。84张最终原生矩阵图有SHA清单，另存失败恢复实拍：[商品普通](visuals/2026-11-06-square-taobao/native/product-normal.jpg)、[选择框普通](visuals/2026-11-06-square-taobao/native/chooser-normal.jpg)、[尾款普通](visuals/2026-11-06-square-taobao/native/tail-normal.jpg)、[商品小屏](visuals/2026-11-06-square-taobao/native/product-small.jpg)、[选择框小屏](visuals/2026-11-06-square-taobao/native/chooser-small.jpg)、[尾款小屏](visuals/2026-11-06-square-taobao/native/tail-small.jpg)、[失败复制小屏](visuals/2026-11-06-square-taobao/native/failure-small.png)。


正式源码`2943ca0b`及`v2.75.0`已普通推送，测试App目录与正式源码目录树一致。仅提交源码归档clean Release完成原签名2.74.0覆盖、两次启动及2份既有设置SHA保全，正式App商品页按钮已核对；无清用户数据或无关全库审计。37项目素材、24旧图片资源与2个依赖素材逐字节一致，生成profile单列。

[GitHub正式发布](https://github.com/aalm1233/lolita/releases/tag/v2.75.0)与[Gitee正式发布](https://gitee.com/aalm1233/lolita/releases/tag/v2.75.0)为同一个APK及SHA256SUMS；匿名完整下载、SHA、HEAD与源码标签通过。Gitee附件在预发布时完整下载核验后改正式，随后正式元数据核对通过。公开更新接口HTTP403，正式App一次完整手动检查显示“暂时拒绝访问或请求过于频繁”；未冒充更新入口通过，使用[国内直接APK](https://gitee.com/aalm1233/lolita/releases/download/v2.75.0/lolita-2.75.0.apk)可下载覆盖安装。

首次Gitee上传触及1GB配额后，依持续授权仅清理最旧2.66.3 APK；新鲜匿名GitHub完整下载、三份原本机副本及SHA先核验，旧发布页、校验文件与GitHub副本保留。[清理回执](gitee-apk-cleanup-2.66.3-2026-11-06.json)。正式详情实拍见[按钮页面](visuals/2026-11-06-square-taobao/native/release-product.png)。

[最终发布回执](square-taobao-release-2.75.0-verification.json)区分所有验证阶段。两台本次AVD已关闭、数据保留；临时签名引用移除，付费0次、主助手淘宝访问0次，真实淘宝客户端未实测。
