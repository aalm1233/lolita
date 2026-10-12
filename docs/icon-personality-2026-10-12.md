# 七套皮肤图标个性恢复

用户反馈七套图标都失去特色。本轮保留统一的绘制网格和光学重量，重新绘制主体轮廓，使导航、内容和常用工具通过造型表达皮肤身份。

## 实现范围

三个 `Polished*Icons.kt` 绘制组保持原入口和 51 个 `IconKey`，七套真实 provider 不变，共 357 个皮肤/图标组合。

| 皮肤 | 主体语言 | 代表性物件 |
|---|---|---|
| 甜美 | 饱满软曲线、紧凑比例 | 娃娃屋、蓬裙、花瓣齿轮、软圆画框 |
| 哥特 | 尖顶、切角、深负形 | 尖拱建筑、切面裙摆、棱形镜头、尖顶柱 |
| 中华 | 飞檐、交领、折角笔触 | 飞檐屋、交领衣、方币、毛笔 |
| 古典 | 对称、直立、收束 | 三角山墙、直线 A 字裙、立柱框、规整工具 |
| 海军 | 横向结构、舷窗、简洁海事物件 | 灯塔、水手领、饼图、舵轮、横向相机 |
| 田园 | 宽檐、自然曲线、木构比例 | 小屋、围裙、自然曲线图、宽木框 |
| 维多利亚 | 拱形、细长、层叠 | 曲线主楼、层叠裙、拱顶空心柱、拱门框、羽毛笔 |

删除、搜索、编辑、复制、刷新和视图图标在工具本体上表达主题。基本加号、关闭、箭头和状态继续使用直接功能符号，以端部和笔触保持系列联系。无 Material 回退、位图、固定颜色或缩小主符号的装饰背板。

24×24 逻辑坐标、1.7 主线宽、调用方尺寸优先、矩形等比居中和 tint 契约保持。实心与空心星标共用一个皮肤轮廓。

## 实际渲染与回归

使用独立新建的 API 35 / ARM64 / Pixel 7 AVD `Lolita_Icon_Personality_API35`，序列号 `emulator-5560`。安装前核对 AVD 名称与 Debug/测试 APK 签名，使用 `install -r`。其他已连接设备未作为安装或测试目标。

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug testDebugUnitTest assembleDebugAndroidTest
adb -s emulator-5560 shell am instrument -w -r \
  -e class com.lolita.app.ui.theme.skin.icon.SkinIconVisualTest \
  com.lolita.app.test/androidx.test.runner.AndroidJUnitRunner
```

- Debug 与测试 APK 构建成功，86 项单元测试通过，失败/错误/跳过均为 0。
- Android 真实渲染测试结果 `OK (1 test)`；每个渲染矩阵包含全部 357 个组合。
- 检查 18/24/26/32dp 及 40×24dp 矩形输入的边距、缩放、居中；矩形渲染像素与居中的 24dp 图像一致。
- 检查浅暗色的调用 tint、完全透明输入；导出 24/26dp 五组中文对照和 18/26/32dp 中文概览。
- 新增半透明实测：添加照片的右上加号不应改变图片下半部原边框/山形的 tint 与 alpha。审查发现第一稿两个重叠裁切重复绘制边框，修复为一次 Difference 裁切绘制。新测试在已安装第一稿上失败，修复后七套全部通过，确认回归能够捕获问题。
- 按真实图片检查七套全部 51 个 key 的浅暗色，以及小尺寸的分类、日历、星标、编辑工具和空心统计柱。没有边框截断或内部纹样挤小主功能符号。

三组任务审查及独立整套代码/实图审查通过，无 Critical 或 Important。控制组个别复杂路径使用单行表示，拆行整理作为后续维护建议，不影响绘制和本轮验收。

图标实现阶段只改绘制与图像验证，不修改账本、数据库、页面布局和图标调用接口；用户随后明确要求提交发布，发布版本为 2.40.4 / 89。美观差异由真实对照图审查，几何/tint 测试不替代视觉判断。

## 对照归档

- 26dp 概览：[浅色](visuals/2026-10-12-icon-personality-overview-light.png) / [暗色](visuals/2026-10-12-icon-personality-overview-dark.png)
- 导航：[浅色](visuals/2026-10-12-icon-personality-navigation-light.png) / [暗色](visuals/2026-10-12-icon-personality-navigation-dark.png)
- 内容：[浅色](visuals/2026-10-12-icon-personality-content-light.png) / [暗色](visuals/2026-10-12-icon-personality-content-dark.png)
- 操作：[浅色](visuals/2026-10-12-icon-personality-actions-light.png) / [暗色](visuals/2026-10-12-icon-personality-actions-dark.png)
- 全部 51 个 key：[浅色](visuals/2026-10-12-icon-personality-all-light.png) / [暗色](visuals/2026-10-12-icon-personality-all-dark.png)
- 尺寸概览：[18dp](visuals/2026-10-12-icon-personality-18dp.png) / [32dp](visuals/2026-10-12-icon-personality-32dp.png)


## 2.40.4 发布验证

- `clean assembleRelease assembleDebug testDebugUnitTest` 完成；全部 97 个 Gradle task 执行，构建成功，86 项单测零失败/错误/跳过。构建保留既有弃用 API 警告，未改动图标组之外的调用。
- 正式包 `com.lolita.app`，versionCode=89、versionName=2.40.4，非 debuggable；签名证书 SHA256 与已发布 2.40.3 一致。
- 独立新建 `Lolita_Icon_Release_API35` / `emulator-5560`，先安装校验过 SHA256 的已发布 2.40.3 / 88，再用实际 Release APK 覆盖升级至 2.40.4 / 89；原 `COUNTRY` 皮肤设置保留，无卸载或清数据。
- 在实际正式包上逐项选择七套皮肤，核对卡片“当前使用”和首页选中语义，保存 UI 树和截图；七套导航及预览中的首页/搜索/设置图标均呈现新主体，没有默认图标回退，本次运行没有应用崩溃记录。
- Release 实拍：[七套导航](visuals/2026-10-12-icon-personality-release-navigation.png) / [皮肤预览](visuals/2026-10-12-icon-personality-release-preview.png)。
- APK SHA256：`1bcbd447b74f860dd6f5e0703dfda6f9c3d462c0dd135d33859b60685b1a86b3`。

Release 运行验证覆盖安装升级、设置保留和七套图标加载；它不替代原有业务与备份迁移回归。
