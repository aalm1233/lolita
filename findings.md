# Findings: 代码审计问题修复

## 审计报告概览
- 来源: code_audit_report.md（5人交叉审计）
- 总计 58 个问题: 12 高 / 26 中 / 20 低
- 涉及文件: ~30+ Kotlin 源文件

## 关键发现

### 数据丢失风险（最高优先级）
- BackupData 缺少 Style/Season 表 → 备份恢复后数据永久丢失
- 编辑页面删除图片立即删磁盘文件 → 不保存返回则图片不可恢复
- BrandSelector disabled TextField 阻止点击 → 品牌选择功能完全失效

### 协程泄漏模式（多处重复）
- updateTotalPrice() 每次筛选新增永不终止的 Flow.collect
- PaymentCalendar loadData() 切换月份累积协程
- CoordinateDetailViewModel.loadCoordinate() 同样模式
- 修复模式统一: 保存 Job 引用，调用前 cancel

### 备份系统问题集中
- BackupManager.kt 涉及 7 个问题（H-01, M-05, M-06, M-08, M-10, M-16, 加上 Style/Season 缺失）
- 建议集中修复，避免多次改动同一文件

### 管理页面重复模式
- Brand/Category/Style/Season 四个管理页面共享相同问题模式:
  - M-12 重复 loadXxx()
  - M-13 缺少编辑功能
  - M-14 缺少 trim()
  - M-15 添加失败关闭对话框
  - L-10 错误消息吞掉
- 修复一个后可批量复制到其他三个

### Android 权限问题
- POST_NOTIFICATIONS (Android 13+) 未请求 → 通知完全失效
- SCHEDULE_EXACT_ALARM (Android 14+) 无 UI 引导
- BOOT_COMPLETED 未注册 → 重启后提醒丢失

## 文件修改热点
| 文件 | 涉及问题数 |
|------|-----------|
| BackupManager.kt | 7 (H-01, M-05, M-06, M-08, M-10, M-16, + Style/Season) |
| ItemEditScreen.kt | 4 (H-10, H-11, M-23, L-20) |
| PriceViewModel.kt | 4 (M-18, M-19, M-25, L-11) |
| PaymentRepository.kt | 2 (M-01, M-02) |
| CoordinateViewModel.kt | 3 (M-07, M-21, L-12) |
| 4x ManageScreen/ViewModel | 5 patterns × 4 = 20 fixes |
