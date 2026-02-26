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
                            "导入前，你需要先从淘宝导出订单的 Excel 文件。",
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
                            "如何从淘宝导出订单",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        GuideStep(
                            number = "1",
                            text = "在电脑浏览器中打开淘宝网（taobao.com），登录你的账号。"
                        )
                        ScreenshotPlaceholder("截图：淘宝登录页面")
                        Spacer(modifier = Modifier.height(12.dp))

                        GuideStep(
                            number = "2",
                            text = "点击页面顶部「我的淘宝」，进入「已买到的宝贝」页面。"
                        )
                        ScreenshotPlaceholder("截图：已买到的宝贝入口")
                        Spacer(modifier = Modifier.height(12.dp))

                        GuideStep(
                            number = "3",
                            text = "在订单列表页面，找到「订单回收站」旁边的「导出订单」按钮（或在页面底部）。"
                        )
                        ScreenshotPlaceholder("截图：导出订单按钮位置")
                        Spacer(modifier = Modifier.height(12.dp))

                        GuideStep(
                            number = "4",
                            text = "在弹出的导出窗口中，选择需要导出的时间范围，确认导出格式为 Excel（.xlsx）。"
                        )
                        ScreenshotPlaceholder("截图：导出设置窗口")
                        Spacer(modifier = Modifier.height(12.dp))

                        GuideStep(
                            number = "5",
                            text = "点击「导出」，等待文件生成完毕后下载到电脑。"
                        )
                        ScreenshotPlaceholder("截图：下载完成")
                        Spacer(modifier = Modifier.height(12.dp))

                        GuideStep(
                            number = "6",
                            text = "将下载的 .xlsx 文件传输到手机上（可通过微信、QQ、云盘等方式）。"
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
                        NoteItem("建议按需选择时间范围，避免一次导出过多订单导致文件过大。")
                        NoteItem("导出的文件需要传输到手机上才能在 App 中导入。推荐使用微信「文件传输助手」或云盘。")
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
