# 非导航图标个性化扩展（2026-10-12）

用户要求五个导航之外也全部个性化。本轮保留既有 Home/Wishlist/Outfit/Stats/Settings 绘制，扩展 16 个操作、14 个内容、9 个方向和 7 个状态图标，七套皮肤共 322 个绘制组合。

## 设计与实现

沿用统一24×24网格、调用方颜色和尺寸。操作绘制拆至 `PolishedActionDrawings.kt`，方向/状态拆至 `PolishedDirectionStatusDrawings.kt`；`PolishedControlIcons.kt` 保持原入口，只做组分派。内容继续使用 `PolishedContentIcons.kt`。全部真实 provider 与51键的公开接口保持兼容。

同色矩阵用于检查主体轮廓和构件差异，尤其关注加号、保存、分享、九宫格、下拉和显隐。甜美使用饱满布艺笔形，哥特使用切面工具与矛头，中华使用笔势/卷轴/元宝，古典使用衬线文具和台式器物，海军使用机械接头/舷窗/硬件，田园使用木作与叶片，维多利亚使用收腰笔形/羽毛笔/珠宝轮廓。相机、调色、月份、金额、位置等图标采用不同的实际器物构造。

## 验证

- 新增真实绘制检查：每个非导航键的七套位图不得相同；这是防止共用默认造型的最低检查，不能代替艺术审阅。
- 既有测试保留：留白、18/24/26/32dp、矩形槽居中、tint、完全透明，以及图片/添加图片半透明下半部一致性。
- 分组截图增加18/32dp导出；概览重点改为非导航键。
- `assembleDebug assembleDebugAndroidTest testDebugUnitTest` 通过，86项单元测试、0失败；修正箭头尾部后再次编译与设备复测。
- 将新仪器化测试安装在原2.40.4 Debug APK上，实际失败：`Add uses identical drawings for DEFAULT and NAVY`。新 APK 的完整回归返回 `OK (1 test)`。
- 51×7真实 provider 在浅暗色、18/24/26/32dp、40×24矩形槽、半透明与全透明输入下通过。46个非导航键的七套绘制逐键不同；相较原矩阵，322个非导航组合中306个像素改变，已具备对应造型的轮廓继续复用。
- 五个导航代码的SHA256保持一致；新旧真实24dp矩阵中的35个导航单元亦逐像素完全一致。
- 实际矩阵审阅发现哥特箭头尾部构成相反箭头，已改为横向金属卡口，左右/交换/外部打开保持单一明确方向；定向复核通过。
- 操作/内容/箭头/状态的18/24/32dp及浅暗色均已实际查看。独立艺术审阅查看全部四组浅暗色及18dp的12张实际矩阵，结合源码/API/回归记录，最终通过，未发现需修复项。

工作使用独立 `Lolita_Icon_Details_Test` AVD / emulator-5560。已有模拟器和主工作区其他改动须保全。图标实现阶段保持已发布2.40.4版本；用户随后授权提交发布，本次发布升为2.40.5 / 90。正式包验证结果见下节。

## 实际截图归档

`docs/visuals/2026-10-12-icon-details-` 前缀下：

- `actions-light/dark`：16操作×7皮肤；`content-light/dark`：14内容×7。
- `arrows-light/dark`：9方向×7；`status-light/dark`：7状态×7。
- `overview-light/dark`：重点非导航图标；`18dp`与`32dp`：小/大尺寸概览。
- `all-light/dark`：全部51键，包含本轮保持原样的5导航。

所有图像均来自真实 Android Canvas 绘制，未用设计稿替代运行结果。

## 2.40.5 正式包验证

- 主工作区运行 `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew clean assembleRelease assembleDebug testDebugUnitTest`，构建通过；86项单元测试，0失败/错误/跳过。正式包版本为2.40.5 / 90。
- 在独立 `Lolita_Icon_Details_Release_Test` AVD / emulator-5560 上安装原2.40.4 / 89正式包，确认旧包与新包签名一致后覆盖升级；原田园皮肤设置保留，七套皮肤选择与首页均由真实UI树和截图确认，本次无应用崩溃。
- 完整 `SkinIconVisualTest` 也直接验证正式包中的真实七套provider。验证用测试APK仅临时改用已发布的MainActivity作宿主，并将矩阵导出至外部专用文件目录；编译后原测试源码逐字节恢复，测试APK使用同一签名。产品APK与产品代码没有为验证另行改写。
- 原2.40.4正式包在逐键差异检查中失败：`Add uses identical drawings for DEFAULT and NAVY`；2.40.5正式包完整检查返回 `OK (1 test)`，覆盖51×7、浅暗色、18/24/26/32dp、矩形槽、半透明及完全透明输入。
- 正式包导出的43张实际矩阵与已审阅的Debug结果逐像素一致；新旧正式包的35个导航单元逐像素一致。额外查看正式包18dp概览与暗色箭头矩阵，方向与辨认度正常。已有截图归档准确反映正式包造型。
- 发布文件为 `lolita-2.40.5.apk`，SHA256：`39c182b25fa41d1ec9cf551191a02ffc1f21e98c8bc09e6fceb16038bc2eb09f`；随包上传 `SHA256SUMS.txt`。
