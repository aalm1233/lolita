# GitHub Release 版本检查与外部下载设计

日期：2026-10-12。状态：按用户明确的简化范围实施；取代此前应用内下载安装设计。

## 范围与交互

给本人和朋友提供两项能力：个人页手动检查是否有更新；有更新时打开 GitHub Release 页面或 APK 下载链接，下载和覆盖安装由浏览器及系统负责。

在现有个人页增加“检查更新”，副文案显示当前版本。点击后显示对话框：检查中、当前已是最新版、当前版本较新、发现新版、检查失败。发现新版显示当前/最新版本，有合法 APK 资产时提供“下载 APK”，同时提供“查看发布页”；没有唯一可识别 APK 时只提供发布页。失败提供重试和固定 GitHub 最新发布页入口。检查期间阻止重复请求，关闭对话框不因迟到结果重新弹出。无自动检查、无新增页面，复用现有皮肤卡片和对话框。

## 数据与版本规则

固定匿名 GET https://api.github.com/repos/aalm1233/lolita/releases/latest，读取 tag_name、html_url、draft/prerelease 和 assets[].browser_download_url。不增加 update.json 或发布脚本，当前 Release 直接兼容。

稳定标签为 v<major>.<minor>.<patch>（允许省略 v），与 versionName 对应；按三个整数顺序比较，不能按字符串排序。远端更高才提示更新，相等为最新版，本机更高显示当前版本较新，不引导降级。未知版本格式或不正确 JSON 明确检查失败，保留发布页入口。versionCode 仍按原流程递增，标签比较只用于提示，不宣称替代系统覆盖安装校验。

APK 优先选已上传且命名为 lolita-<version>.apk 的资产，否则选择唯一已上传 APK；多个候选时提供发布页，不随意下载。外部链接只接受 HTTPS、github.com、本仓库当前标签的 Release 页面/下载路径，无凭据或异常端口；无合法 html_url 时用固定仓库与已校验 tag 构造页面地址。使用 API 返回的下载链接，不猜附件名或拼接不存在的最新版 APK 链接。

## 实现与失败

GitHubReleaseApi 使用现有 HttpURLConnection/Gson，连接/读取超时和响应大小上限；网络在 Dispatchers.IO。AppUpdateRepository 为薄封装，版本规则放 domain/usecase；AppModule 提供仓库。独立 AppUpdateViewModel 用 StateFlow 管理检查与对话框，不耦合个人统计的数据订阅。

超时、断网、HTTP 404、403/429、损坏响应用中文提示；取消异常继续抛出。外部浏览器打开失败提示无法打开，可重试。现有 INTERNET 权限足够，不修改 Manifest、Room、备份、FileProvider，不申请安装权限。

新增功能版拟为 versionCode 85 / versionName 2.40.0，实施前核对当前版本。同步 AGENTS.md；现有大量未提交改动需保留，只做局部修改。本次不创建/上传线上 Release。

## 验证

JUnit 覆盖 2.9/2.10 数字排序、相等/本机较新、未知格式、草稿/预发布拒绝、APK 唯一选择与多附件降级、不可信链接及异常 JSON。针对新增功能跑 testDebugUnitTest，并按项目要求 assembleDebug。可用独立设备时验证个人页入口及对话框；未执行浏览器下载/覆盖安装要明确说明，不代下载或安装。

首次仍须手动覆盖安装带此功能的一版，以后的版本可点击检查和外部下载。

参考：https://docs.github.com/en/rest/releases/releases#get-the-latest-release
