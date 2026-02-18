package com.lolita.app.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lolita.app.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.*

class OutfitWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val outfitLogRepo = com.lolita.app.di.AppModule.outfitLogRepository()
        val todayLog = outfitLogRepo.getTodayOutfitLog()
        val hasOutfit = todayLog != null
        val itemCount = todayLog?.items?.size ?: 0

        provideContent {
            WidgetContent(hasOutfit = hasOutfit, itemCount = itemCount)
        }
    }
}

@Composable
private fun WidgetContent(hasOutfit: Boolean, itemCount: Int) {
    val dateFormat = SimpleDateFormat("M月d日 EEEE", Locale.CHINESE)
    val today = dateFormat.format(Date())
    val pink = ColorProvider(android.graphics.Color.parseColor("#FF69B4"))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(16.dp)
            .background(GlanceTheme.colors.surface)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = today,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
        )
        Spacer(modifier = GlanceModifier.height(8.dp))

        if (hasOutfit) {
            Text(
                text = "今日已记录 $itemCount 件穿搭",
                style = TextStyle(fontSize = 13.sp, color = pink)
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = "点击查看/编辑",
                style = TextStyle(fontSize = 12.sp)
            )
        } else {
            Text(
                text = "今天穿了什么？",
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = pink)
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = "点击记录今日穿搭",
                style = TextStyle(fontSize = 12.sp)
            )
        }
    }
}
