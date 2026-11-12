# 私有商品提取／三视图评估工具

这是独立研究工具，不是Android新增功能。结果不能自动追加用户相册或作为真实商品规格。使用前必须具备本轮明确的付费和图片上传授权；代码内500上限只对应2026-11-12这轮授权，不代表未来任务自动获准。

`three_view.py inventory` 只读固定备份入口，核对ZIP/SHA/图片清单，保存私有副本和EXIF清理后的参考图。Pillow不能解码的历史HEIC在macOS通过ImageIO转换工作副本；不改原字节。清单区分明确AI生成图和没有AI标记的参考；没有标记本身不证明照片真实性。

`three_view.py run` 接收经过逐图选择的JSON作业：`id/itemId/category/name/strategy/inputs`，可选`notes/size/prompt`。每张输出预留一次调用；500次硬上限、线程间加锁原子记账、每批最多三路、跨批互斥、同ID拒绝重发，额度/鉴权/限流后停止派发新请求。失败和未知结果仍计数。无重定向、自动重试或额外识图服务；只保存服务错误代码，不保存消息或响应正文。只应使用小批次，并逐图检查前一批后再继续。

`production_probe.py build/run` 离线编译当前正式Seedream传输和单品提示源码，记录Debug模型类与探针类摘要。人台模式还检查实际提示与当前 `MannequinRequest.kt` 字面值相等。运行前复核源码和类文件摘要；先完成Debug构建，再编译探针，不能与Gradle重建类文件同时进行。它仍使用主机归一化输入，不代替Android编码、任务或页面验证。

`evidence_board.py prepare` 只接受当前物品自己的、无AI生成标记且逐图确认视角的图片与归一化裁切；缺失角度不创建付费作业。发送后不能重写该证据输入。`compose` 通过本地代码填未知格，默认保留原裁切；只有明确提供结果SHA且与成功回执一致才换入AI提取。结果仍需逐图检查细节、方向和裁切，三格Fit排版不表示真实等物理比例。

`contact_sheet.py` 生成逐物品对照；`report.py` 复核原包、全部原图、每次输入/提示词/结果SHA，生成只含本机相对图片链接的私有HTML和汇总。逐图结论从私有 `reviews.json` 读取，服务返回成功不会自动成为视觉通过。

`label_hypotheses.py` 在推测研究图下方追加本地生成来源说明；原始返回PNG保持，额外调用0次。图册优先展示带标记副本，不能依赖模型自行生成的“AI推测”文字作为唯一标识。

在仓库外创建700权限目录和Python环境（Pillow、pillow-heif），把私有图片、作业、清单、结果和报告都放该目录。密钥默认为本机受保护的 `~/.config/lolita/recognition-evaluation.json`，必须600权限，不通过命令行传Key。不要提交真实作业JSON、库存名称、照片或派生图。

离线安全回归：

```sh
python -m unittest discover -s scripts/image_eval -p 'test_*.py' -v
```

本轮范围、方法与结果分别见 `docs/plans/2026-11-12-three-view-evaluation.md` 和最终评估记录。重新执行已完成作业不会恢复或重新发送；需另建明确的试验ID，并仍受同一账本上限约束。
