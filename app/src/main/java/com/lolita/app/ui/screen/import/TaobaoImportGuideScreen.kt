package com.lolita.app.ui.screen.`import`

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lolita.app.ui.screen.common.GradientTopAppBar
import com.lolita.app.ui.screen.common.LolitaCard
import com.lolita.app.ui.theme.skin.icon.IconKey
import com.lolita.app.ui.theme.skin.icon.SkinIcon

@Composable
fun TaobaoImportGuideScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            GradientTopAppBar(
                title = { Text("导入指南") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        SkinIcon(IconKey.ArrowBack)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // 概述
            item {
                LolitaCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "关于淘宝订单导入",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "本功能可将淘宝订单批量导入为服饰记录，自动解析品牌、类型、颜色、尺码等信息。" +
                            "导入前，你需要先从淘宝导出订单的 Excel 文件。\n\n" +
                            "无需电脑，直接在手机上用 Chrome 浏览器即可完成导出。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // 导出步骤
            item {
                LolitaCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "如何从手机导出淘宝订单",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        GuideStep(
                            number = "1",
                            text = "在手机上打开 Chrome 浏览器（Google 浏览器），访问 taobao.com 并登录你的淘宝账号。"
                        )
                        ScreenshotPlaceholder("截图：Chrome 打开淘宝登录页面")
                        Spacer(modifier = Modifier.height(12.dp))

                        GuideStep(
                            number = "2",
                            text = "点击 Chrome 右上角「⋮」菜单，勾选「桌面版网站」，页面会切换为电脑版显示。"
                        )
                        ScreenshotPlaceholder("截图：Chrome 菜单中的「桌面版网站」选项")
                        Spacer(modifier = Modifier.height(12.dp))

                        GuideStep(
                            number = "3",
                            text = "点击页面顶部「我的淘宝」，进入「已买到的宝贝」页面。"
                        )
                        ScreenshotPlaceholder("截图：已买到的宝贝入口")
                        Spacer(modifier = Modifier.height(12.dp))

                        GuideStep(
                            number = "4",
                            text = "在订单列表页面，找到「导出订单」按钮（通常在「订单回收站」旁边或页面底部）。"
                        )
                        ScreenshotPlaceholder("截图：导出订单按钮位置")
                        Spacer(modifier = Modifier.height(12.dp))

                        GuideStep(
                            number = "5",
                            text = "在弹出的导出窗口中，选择需要导出的时间范围，确认导出格式为 Excel（.xlsx），然后点击「导出」。"
                        )
                        ScreenshotPlaceholder("截图：导出设置窗口")
                        Spacer(modifier = Modifier.height(12.dp))

                        GuideStep(
                            number = "6",
                            text = "等待文件生成完毕，Chrome 会自动下载 .xlsx 文件到手机的「下载」文件夹中。"
                        )
                        ScreenshotPlaceholder("截图：下载完成提示")
                        Spacer(modifier = Modifier.height(12.dp))

                        GuideStep(
                            number = "7",
                            text = "回到本 App，在导入页面点击「选择文件」，从「下载」文件夹中选择刚才导出的文件即可。"
                        )
                    }
                }
            }

            // 注意事项
            item {
                LolitaCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "注意事项",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        NoteItem("仅支持 .xlsx 格式的文件，其他格式（如 .csv、.xls）无法识别。")
                        NoteItem("必须切换到「桌面版网站」模式，手机版淘宝网页没有导出功能。")
                        NoteItem("建议按需选择时间范围，避免一次导出过多订单导致文件过大。")
                        NoteItem("支持同时选择多个文件导入，系统会自动去重。")
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideStep(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    number,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ScreenshotPlaceholder(hint: String) {
    Spacer(modifier = Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                color = Color(0xFFF0F0F0),
                cornerRadius = CornerRadius(8.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFFCCCCCC),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(10.dp.toPx(), 6.dp.toPx())
                    )
                )
            )
        }
        Text(
            hint,
            color = Color(0xFF999999),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun NoteItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("•", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
