# Skin Background and Icon Art Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax.

**Goal:** 将用户选择的衣橱桌面标识、七套边角背景和更清晰的功能图标落实到真实应用。

**Architecture:** drawable背景 + LolitaSkinConfig令牌 + 单一SkinArtBackground层；页面开放底色、卡片保留表面；launcher原生adaptive Vector；功能图标保留现有Canvas分派。

**Tech Stack:** Kotlin 2.1 / Compose / Android VectorDrawable / 内置imagegen，无新依赖。

**Spec:** docs/superpowers/specs/2026-10-14-skin-art-polish-design.md

## Global Constraints

中文；七套全覆盖；108画布中央66安全区；24逻辑图标、1.7主线宽、约2单位边距；原tint.alpha相乘；五导航大轮廓保持；无业务/数据库/备份改动；首页布局、月份、Insets、Haze、滚动冻结保留；仅独立AVD；保持原密钥；用户授权默认双平台完整发布。

## Review Focus

背景被内层Scaffold遮挡；暗色背景出现白矩形或影响正文；静态图片加载/滚动重复动画；小图标平行线挤合与箭头不清；adaptive裁切与通知小图标误用。

## Task 1: 桌面衣橱标识

**Files:** res/drawable/ic_launcher_foreground.xml、新ic_launcher_monochrome.xml、mipmap-anydpi-v26及v33两份adaptive XML、values/colors.xml、DailyOutfitReminderReceiver.kt。
**Interfaces:** foreground/background/monochrome使用固定resource；通知使用R.drawable.ic_notification。

- [ ] 绘制莓紫底象牙衣橱挂裙，单色版保留同一主要轮廓和负空间；图形完全在108画布中央66区域。
- [ ] 前景无位图/微小文字；独立Android13单色层，旧API保持前景背景兼容。
- [ ] 核对通知smallIcon独立于桌面资源；Android真渲染24/48尺寸与裁切交由控制器验证。

## Task 2: 公共背景与页面接入

**Files:** LolitaSkinConfig.kt、SkinConfigs.kt、新skin/component/SkinArtBackground.kt、LolitaShimmerImage.kt、LolitaNavHost.kt、普通页面Scaffold底色；新androidTest SkinArtBackgroundTest.kt。
**Interfaces:** artBackgroundRes:Int? / artBackgroundAlpha:Float / artBackgroundAlphaDark:Float / artBackgroundTintDark:Color?；SkinArtBackground(modifier:Modifier=Modifier)；LolitaShimmerImage尾部colorFilter:ColorFilter?=null和showFailurePlaceholder:Boolean=true。
**Consumes:** 控制器提供drawable-nodpi/bg_art_{sweet,gothic,chinese,classic,navy,country,victorian}.png。

- [ ] 先给出真实绘制回归：七套浅/暗图层正确、透明中心保留底色、装饰不占无障碍，初始缺组件状态由控制器观察RED；避免实现镜像断言。
- [ ] 添加令牌和组件、图片可选色彩过滤/静默失败；在公共NavHost单实例放在原粒子前，中央留白无需改变卡片色。
- [ ] 逐一处理普通页面内层Scaffold的不透明遮挡；保留网页、对话框与全屏照片等内容表面，禁止删改Insets或滚动机制。
- [ ] 根据真实页面验证确保首页、穿搭、统计、个人和详情可见；在失败/加载时无问号或shimmer，保留默认图片行为。

## Task 3: 功能图标光学精修

**Files:** PolishedContentIcons.kt、PolishedActionDrawings.kt、PolishedDirectionStatusDrawings.kt；必要时PolishedNavigationIcons.kt仅光学微调；复用SkinIconVisualTest。
**Interfaces:** 现有drawPolished*分派和SkinIconStyle不变。

- [ ] 按设计修¥间距、Camera内部疏密、田园短箭头、SwapVert和OpenInNew独立斜箭头；保持七套实际构造。
- [ ] 修Search连接、Add/Close视觉重量、重叠纸片/照片与Link空白，确保tint/alpha及所有镜像/星标轮廓关系保留。
- [ ] 检查所有51键，保持24网格/1.7主线和语义，不用新纹理/徽章替代工具本体。现有完整真实provider矩阵由控制器统一构建执行。

## Task 4: 素材、验收和交付

**Files:** 七张独立生成PNG、来源提示词JSON、AGENTS.md、版本文件、docs/visuals真实页面/矩阵与发布报告。
**Interfaces:** 固定7资源名称交给Task2；全部改动统一构建与提交由控制器负责。

- [ ] 内置imagegen每张分别生成透明边角插画，目测并检查alpha、尺寸、磁盘/运行时开销；保存原始提示词与SHA256。
- [ ] 基线和最终构建、单测、真实背景与图标回归；审查任务和最终全改动，无遗留阻塞项。
- [ ] 独立AVD核对签名并从2.45.2覆盖升级，归档实际浅暗页面与桌面图标，保全原设置/记录。
- [ ] 升2.46.0/99并更新约定；源码合入master、普通推送，clean正式包签名与版本校验，GitHub/Gitee完整双平台发布与匿名附件摘要核对。

## Execution

用户已确认一起优化、选定左侧衣橱方案，连续执行已授权范围，不再重复征求同一授权。独立代码范围并行，控制器独占Gradle、git index/commit、设备及发布；各实现者不得改他人文件、版本/AGENTS或启动编译。视觉微调复用有效的真实绘制回归，不新增模拟绘制测试。
