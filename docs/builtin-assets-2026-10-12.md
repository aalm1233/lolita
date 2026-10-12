# 内置店铺图标与默认房间（2026-10-12）

从本机测试备份中实际关联服饰的 17 家店铺及淘宝测试表取店名，核验官方页面后保存原图片字节；不按预置品牌总表猜图。当前收集 14 家，另 3 家保留缺省显示。

## 店铺图片来源

| 测试资料店名 | 官方来源 | 素材 | 说明 |
|---|---|---|---|
| ZJstory原创设计 | [ZJstory原创设计](https://www.sina.cn/media/5650275998) | `builtin/brands/zjstory.jpg` | 官方公开头像 |
| 羊羊羊兰兰兰 | [羊羊羊兰兰兰](https://www.sina.cn/media/5625280379) | `builtin/brands/sheep.jpg` | 官方公开头像 |
| 花与珍珠匣 | [花与珍珠匣](https://www.sina.cn/media/6574740774) | `builtin/brands/flower_pearl.jpg` | 官方公开头像 |
| FelinaeCookieLolita「猫科小甜饼」 | [FelinaeCookie猫科小甜饼](https://www.sina.cn/media/6058825266) | `builtin/brands/felinae_cookie.jpg` | 官方公开头像 |
| 新路德维希Neo Ludwig洋装 | [Neoludwig新路德维希](https://www.sina.cn/media/3104885167) | `builtin/brands/neo_ludwig.jpg` | 官方公开头像 |
| 荆棘玫瑰 | [Bramble_Rose荆棘玫瑰原创设计](https://www.sina.cn/media/3823128577) | `builtin/brands/bramble_rose.jpg` | 官方公开头像 |
| OZZONJAPAN | [OZZON JAPAN](https://ozzon-japan.jp/) | `builtin/brands/ozzon_japan.png` | 官方页头横向透明商标 |
| 十tonight十 | [十Tonight十](https://www.sina.cn/media/6750101743) | `builtin/brands/tonight.jpg` | 官方公开头像 |
| 莉莉子lolita店 | [莉莉子lolita店](https://www.sina.cn/media/1956142282) | `builtin/brands/lilizi.jpg` | 官方公开头像 |
| With PUJI 原创自制轻洋装 | [-Withpuji-](https://www.sina.cn/media/6750679404) | `builtin/brands/with_puji.jpg` | 官方公开头像 |
| Elpress L 原创Lolita洋装 | [Elpress](https://www.sina.cn/media/1234587767) | `builtin/brands/elpress_l.jpg` | 官方公开头像 |
| 樱桃爆弹 | [CherriBomb樱桃爆弹Lolita原创店](https://www.sina.cn/media/1680101651) | `builtin/brands/cherry_bomb.jpg` | 官方公开头像（源图本身偏模糊） |
| 时之砂Lolita洋装工作室 | [时之砂Lolita洋装工作室](https://www.sina.cn/media/1624163643) | `builtin/brands/time_sand.jpg` | 官方公开头像 |
| 古典玩偶Puppets and Doll官方店 | [古典玩偶ClassicalPuppets](https://www.sina.cn/media/5360834362) | `builtin/brands/classical_puppets.jpg` | 官方公开头像（当前周年版） |

未找到可核验图片：JND Lolita、古雾行原创手作Lolita设计、怪味诊所。公开商品链接登录跳转及第三方报道不作为店铺商标来源；未采用近似店名图案。

## 默认房间

六个房间均为普通可编辑位置，配图采用统一的温暖木色、织物质感与等距微缩房间风格。用户原位置不改名、不合并，有照片的同名房间保留原图。

| 房间 | 原始生成图片 |
|---|---|
| 卧室 | [图片](../app/src/main/assets/builtin/rooms/bedroom.png) |
| 客厅 | [图片](../app/src/main/assets/builtin/rooms/living_room.png) |
| 衣帽间 | [图片](../app/src/main/assets/builtin/rooms/dressing_room.png) |
| 书房 | [图片](../app/src/main/assets/builtin/rooms/study.png) |
| 玄关 | [图片](../app/src/main/assets/builtin/rooms/entryway.png) |
| 储物间 | [图片](../app/src/main/assets/builtin/rooms/storage_room.png) |

六张图片均通过内置 imagegen 工具分别新生成，原图直接保存至项目；没有改绘真实店铺商标。完整提示词、官方链接、图片尺寸及 SHA256 位于 [来源清单](builtin-assets-sources-2026-10-12.json)。

## 接入与数据保全

首次升级/启动在 Room 事务内补充默认资料，图片复制到 UUID 本地文件，随后保存本机完成标记。默认房间可删除、改名或去图；重启不重新添加。新增匹配店铺可自动带商标，自选图片优先，编辑不强制补图。

备份操作先等待初始化；恢复完成后保持源数据，不在后台重新塞入默认资料。默认图片与普通用户图片使用相同 ZIP 校验、导入及清理机制。

## 验证

- `assembleDebug assembleDebugAndroidTest testDebugUnitTest` 构建成功；86 项 JVM 测试、0 失败。
- 专属 API35 ARM64 AVD `Lolita_Builtin_Assets_Test`，仅定向 `emulator-5560`。安装前确认 AVD 名称、应用包及旧/新/测试 APK 签名相同。
- `BuiltInLibraryTest`：9 项真实 Android 回归通过；屏障失败路径先捕获 IOException 的实际 RED，再修正并 GREEN。`DataIntegrityTest`：8 项备份/迁移回归通过。
- 从原 v2.40.5 Debug 包导入真实测试 ZIP 后升级新版：140 件服饰、215 个品牌、4 个原位置和 3 张原位置照片完整保留；仅品牌空图片字段变化、追加 6 个房间。其他 24 张表逐字段不变，20 种素材对应的 21 个新增本地副本（含同店铺的既有别名记录）和 3 个原位置图片逐项 SHA256 验证。
- 所有 190 个原资料引用的本地图片文件，另与独立源 ZIP 的原始图片 SHA256 核对；对应实体的图片路径逐字段不变。
- 回归输出：[内置资料测试](builtin-assets-android-tests-2026-10-12.txt)、[数据完整性](builtin-assets-data-integrity-2026-10-12.txt)、[测试备份升级核对](builtin-assets-upgrade-verification-2026-10-12.json)。
- 实际页面已逐项查看：17 家测试店铺的管理行（14 张官方图与 3 个缺省）、六房间首页缩略图、卧室详情，以及暗色下的房间和透明横向 OZZON 字标；图片可识别、字标完整、用户原衣柜照片保留。 [逐店截图清单](builtin-assets-visual-verification-2026-10-12.json)。
- [房间列表](visuals/2026-10-12-builtin-room-list.png)、[原位置与新增房间](visuals/2026-10-12-builtin-room-list-top.png)、[卧室详情](visuals/2026-10-12-builtin-bedroom-detail.png)、[暗色房间](visuals/2026-10-12-builtin-rooms-dark.png)、[暗色透明商标](visuals/2026-10-12-builtin-ozzon-dark.png)。
- 实现任务审查与全改动独立审查均通过，无待修问题。主工作区再次 `assembleDebug testDebugUnitTest` 成功（11 秒、86 项单测），仅同步本轮文件；其他会话原始字节保留。
- 正式发布版本 `2.41.0 / 91`：`clean assembleRelease assembleDebug testDebugUnitTest` 通过（1分24秒）；正式 APK 内的清单及20张图片与源文件字节一致。独立 AVD 从已发布 2.40.5 同签名覆盖升级，原140件服饰、215个品牌、4个位置、190张原图片和其他24张表完整保留；新增21个本地图片副本逐一核对素材 SHA256，重启不重复添加。大快照流式传输曾截断，改用完整远端文件拉取核对重启数据库，涉及原图片另行拉取确认原字节不变。 [发布验证](builtin-assets-release-2.41.0-verification.json)、[正式包房间](visuals/2026-10-12-builtin-release-rooms.png)、[正式包原资料](visuals/2026-10-12-builtin-release-existing-data.png)。

临时验证 AVD、5560 和对应 launchctl 服务已清理；原有三个模拟器保留。[清理记录](builtin-assets-avd-cleanup-2026-10-12.json)。
