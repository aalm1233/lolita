# GitHub Release Check Implementation Plan

> **For agentic workers:** Use executing-plans for the small implementation; a fresh reviewer checks the final feature. Steps use checkbox syntax.

**Goal:** 个人页检查最新版，并由浏览器打开 GitHub 页面或 APK 下载链接。

**Architecture:** 固定 GitHub API → 薄 Repository → 独立 StateFlow ViewModel → 现有个人页菜单和对话框。domain/usecase 负责数字版本比较。

**Tech Stack:** Kotlin、Compose、HttpURLConnection、Gson、JUnit4，无新增依赖。

**Spec:** docs/superpowers/specs/2026-10-11-github-release-update-design.md

## Global Constraints

中文文案；只手动检查和外部下载；保留未提交改动；不改数据库与安装权限；更新 AGENTS.md。小范围修改在当前工作区执行，避免丢失工作区最新UI。

## Review Focus

数字版本排序；错误不误报最新版；多个APK不乱选；外部链接限定本仓库；关闭对话框后网络结果不重新弹出。

## Task 1: Release 解析与版本判断

Files: domain/model/AppRelease.kt、domain/usecase/ReleaseVersion.kt、data/remote/GitHubReleaseApi.kt、data/repository/AppUpdateRepository.kt；JUnit ReleaseVersionTest / GitHubReleaseApiTest。

Interfaces: ReleaseVersion.compare(remote: String, local: String): Int；GitHubReleaseApi.fetchLatest(): AppRelease；AppUpdateRepository.checkForUpdate(currentVersion: String): ReleaseUpdate。

- [x] 写版本/真实Release格式解析/链接与多资产行为测试，先观察失败，再实现最小逻辑。
- [x] 通过定向JUnit，确认异常不被解释为无更新。

## Task 2: 个人页入口与验证

Files: di/AppModule.kt、ui/screen/settings/AppUpdateViewModel.kt、AppUpdateDialog.kt、SettingsScreen.kt、app/build.gradle.kts、AGENTS.md。

- [x] 接入独立仓库/ViewModel，复用 SettingsMenuItem 和 AlertDialog，ACTION_VIEW 打开外部链接；重复请求与关闭状态明确。
- [x] 版本更新为85/2.40.0，同步项目约定；不提交任何既有用户改动。
- [x] 跑 testDebugUnitTest assembleDebug，检查真实线上响应与定向UI（设备可用时）。
- [x] 独立审阅新增文件和局部集成，修复实质问题，交付验证结果。

## Execution rulings

用户已明确缩小范围并要求先设计后实现，按该授权连续执行，不增加审批轮次。实现与现有改动耦合，在当前文件局部修改，不创建基于旧HEAD的工作树、不自动提交混合改动。仅业务规则与解析有必要增加单测，不为可逆的菜单文案写实现镜像测试。

## 验证记录

- 使用 Android Studio JBR21，目标字节码仍为 JVM17。
- TDD：版本/Release解析9例先失败，完成实现后通过；状态2例先失败，完成实现后通过。
- `./gradlew testDebugUnitTest assembleDebug`：BUILD SUCCESSFUL，86 tests、0 failures/errors/skipped。
- 专用 `Lolita_Business_Fix_Test`（emulator-5556）核对旧/新完整 APK 签名一致，保全旧 APK 与 run-as 数据包后用 install -r 升级84→85；未卸载或清数据。
- 通过真实UI树点击个人页/检查更新，真实 GitHub 响应显示当前2.40.0、最新发布2.39.5，正确进入“当前版本较新”，截图与UI树在 `/tmp/lolita-release-check-20261012/device/`。
- 下载按钮的数据选择由单测覆盖；尚未实际点击浏览器下载或覆盖安装 GitHub APK（线上版本低于当前测试版）。
- 独立只读审阅无 Critical/Important 问题。工作保留在 `codex/release-version-check`，未自动提交既有混合改动、未创建线上 Release。
