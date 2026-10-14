# 表格订单导入交互实施记录

目标：默认逐件填写，支持切换批量编辑；快速修正全款/定金/尾款，明确配对和导入范围。

架构：沿用 TaobaoImportViewModel.importItems 作为唯一资料来源；Compose 保存模式、当前位置和筛选。切换视图不重建导入资料，不改数据库与账目逻辑。

- [x] 完善页提供逐件/批量切换、上一件/下一件与进度。
- [x] 批量按付款类型和待配对筛选，采用原始索引调用 ViewModel。
- [x] 展示原商品名、直接修改付款类型、显示配对对象及文字操作。
- [x] 精简说明并增加最终导入范围确认。
- [x] Debug 编译与现有 JVM 回归：`./gradlew assembleDebug testDebugUnitTest -Pkotlin.incremental=false` 通过。

普通增量编译触发 Compose 编译器 Unknown file 内部错误；关闭 Kotlin 增量编译后完整编译成功。后续已在新建独立 AVD `Lolita_Order_Import_Test` / `emulator-5560` 完成 3 项真实 Compose 交互测试：默认逐件与导航边界、切换保留修改、筛选修正原始索引、配对显示及解除。使用独立内存 Room，没有清理已有设备资料。

正式构建：2.46.1 / 100；`clean assembleRelease assembleDebug testDebugUnitTest -Pkotlin.incremental=false` 通过，170 项 JVM 测试无失败。APK 签名核验通过，SHA256 `1df3a82ee56576cff9a81a9285c3ab7fa402bdab360ac886fdafc3d004f7e3be`。安装包位于 `artifacts/releases/2.46.1/lolita-2.46.1.apk`，尚未上传发布。

核对重点：模式切换保留修改；过滤不改变商品索引；末项禁止下一件；更换配对解除旧关联；不把配对资料条数描述为服饰件数。


## 21:37 后续复查

详见 `docs/order-import-interaction-review-2026-10-14.md`。修复草稿丢失、逐件订单汇总、配对核对状态及小屏大字体底部挤压。8 项设备交互与170项JVM测试通过；重新 `clean assembleRelease -Pkotlin.incremental=false` 构建通过，并核对签名。修正版仍为尚未发布的2.46.1 / 100；此前本地APK已替换，当前SHA256：`f78229c175124d2b58c3c26d1361e0765af06cd23c4a92ccf2099574502fe36c`。
