# 背景动画粒子尺寸放大设计

## 目标

将所有5个皮肤的背景动画粒子尺寸翻倍（2x），使图标更醒目。

## 方案

直接在各粒子类中将尺寸参数乘以2。不改动粒子数量、速度、透明度、动画周期等参数。

## 修改范围

10个粒子文件，仅修改尺寸相关参数：

| 皮肤 | 粒子 | 当前尺寸 | 翻倍后 |
|------|------|---------|--------|
| Sweet | BubbleParticle radius | 4-12dp | 8-24dp |
| Sweet | PetalParticle size | 6-14dp | 12-28dp |
| Sweet | StarParticle size | 8-15dp | 16-30dp |
| Gothic | SmokeParticle radius | 60-120dp | 120-240dp |
| Gothic | EmberParticle size | 3-8dp | 6-16dp |
| Gothic | EmberParticle tail | 15-25dp | 30-50dp |
| Chinese | CloudParticle width | 80-140dp | 160-280dp |
| Chinese | CloudParticle height | 30-50dp | 60-100dp |
| Chinese | PlumBlossomParticle size | 10-18dp | 20-36dp |
| Classic | SparkleParticle radius | 10-40dp | 20-80dp |
| Classic | DiamondParticle size | 6-12dp | 12-24dp |
| Navy | AnchorParticle size | 8-14dp | 16-28dp |
| Navy | BubbleParticle radius | 4-12dp | 8-24dp |
| Navy | RopeKnotParticle size | 6-10dp | 12-20dp |
