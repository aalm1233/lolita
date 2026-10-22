# 实验室入口对齐与独立图标

用户反馈个人页实验室的说明没有与下方入口对齐，也没有自己的独立图标。原实现将说明放在整个图标行下方，FEATURED 内边距与同级入口不同，且使用 Apps 九宫格；此前只核对标题字号，没有覆盖实际文字轴线。

依据[实现前图稿与七皮肤设计](plans/2026-10-22-laboratory-entry-alignment.md)，本轮发布2.66.4／141。

## 最终实现

LaboratoryEntry 复用 SettingsMenuItem。标题和说明在同一 Column，和属性管理、数据备份等使用相同的 cardInnerPadding、40dp 图标底座、22dp 图标、12dp 间距、18dp 箭头。FEATURED 的既有边框与阴影保留，标题仍为16/24sp、500，说明与其他入口相同为13/20sp；大字自然换行和增高，整卡保持唯一导航操作。

独立 IconKey.Laboratory 追加在枚举末尾，保留原51键及顺序。ContentIcons、Base、SkinIcon 入口和共用 Polished provider 完整接线，七套实际 provider 分别绘制不同主体的烧瓶，原有图标不改。绘制来源为本轮原创24单位路径，遵守原线宽、尺寸和 tint；不新增位图、网络或图片文件。

## 验证与真实对照

- clean Debug 与 androidTest 构建通过；定向设备方法3项最终通过，无跳过。使用独立AVD、内存Room／UUID设置和受控生成夹具，新增真实付费请求0次。
- SkinIconVisualTest 真实渲染52×7图标，验证18／24／26／32dp、40×24dp矩形、着色、半透明画框及完全透明输入，新增实验室图标在七套中均为不同主体。原51键在5个尺寸／浅暗矩阵的1,785个单元与2.66.3原图逐像素一致，详见[图标验证](visuals/2026-10-22-laboratory-entry-native/icon-verification.json)及[浅色尺寸对照](visuals/2026-10-22-laboratory-entry-native/laboratory-icons-light.png)、[暗色](visuals/2026-10-22-laboratory-entry-native/laboratory-icons-dark.png)。
- LaboratoryScreenTest 在1440×3200／600dpi下核对七皮肤浅暗：实验室标题和说明左边缘一致，并与属性管理标题、说明及图标左右边界一致；文字角色相同，点击仍进入实验室和原批量页面。
- 原生320dp／两倍系统字体下核对入口轴线、换行和图标，后续实验室假发搜索仍能打开真实IME，主要操作可达。测试后恢复原1080×2400／420dpi／字体1.0；没有卸载或清空旧数据库。
- [默认皮肤实际个人页](visuals/2026-10-22-laboratory-entry-native/compact-entry-default-light.png)、[维多利亚暗色](visuals/2026-10-22-laboratory-entry-native/compact-entry-victorian-dark.png)、[小屏两倍字](visuals/2026-10-22-laboratory-entry-native/small-aligned-entry.png)及[完整验收清单](visuals/2026-10-22-laboratory-entry-native/verification.json)。截图为实际Compose，前置图稿另行归档，不混作实现证据。

Room32、36实体、备份13、原图片与引用、隐私、生成请求和费用协议不变。未修改实验室内部功能顺序或其他页面，不重跑无关账本、备份及全库图片回归。

正式包从提交源码归档 clean 构建，按原签名由2.66.3覆盖并重复启动，原设置与内置素材字节、双渠道APK与校验文件及更新入口结果另见[发布回执](laboratory-entry-release-2.66.4-verification.json)。


发布完成：原签名覆盖及3份设置SHA保全通过，正式个人页实验室与属性管理标题／说明左边缘均为234px；[正式包实拍](visuals/2026-10-22-laboratory-entry-native/release/release-personal.png)。GitHub／Gitee同一APK的匿名完整下载、SHA及HEAD通过，公开最高版本为2.66.4。应用内单次实际检查结果为「已是最新版本（当前及最新均为2.66.4）」，[实际检查截图](visuals/2026-10-22-laboratory-entry-native/release/release-manual-update-check.png)，不以公开API代替。
