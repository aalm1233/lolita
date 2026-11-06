# 原生验收：广场浏览与直接心愿

这些PNG来自独立AVD上的真实Compose与完整NavHost，不是HTML。普通1080×2400、320dp小屏960×2400／fontScale2。feed/product/multi覆盖七套皮肤浅暗，-small为小屏。

- app-*：生产导航的广场→商品→直接心愿→物品资料→原图→个人组合→现有心愿→返回；为受控Compose空闲判断关闭Haze与背景动画，未改变生产配置。
- feed/product/multi-*：受控页面矩阵；单件保存反馈最终以after-black-*和app-*为准。
- kc-parameters、lilizi-*：独立小物及第二店长短款/袖套资料。
- missing-component：没有独立图时不借用系列图作为封面。tail：尾款没有保存入口。info：可按需打开与返回关闭。small-real-ime-search：真实系统键盘。

历史原型另在../artist。MainActivity/Release实拍与覆盖保全记录见实施报告。

田园暗色普通商品图采用同轮较早的已加载原生帧（本机settled归档），避免后续归档里图片异步加载中的空画框覆盖已验收截图；没有编辑或补绘截图。商品布局与最终相同，单件成功反馈由最终app/after-black帧核对。
