package com.lolita.app.ui.theme.skin.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Preserve the common provider entry point; each group owns its themed drawing. */
internal fun DrawScope.drawPolishedControlIcon(key: IconKey, style: SkinIconStyle, tint: Color) {
    when (key) {
        IconKey.Add, IconKey.Delete, IconKey.Edit, IconKey.Search, IconKey.Sort,
        IconKey.Save, IconKey.Close, IconKey.Share, IconKey.FilterList, IconKey.MoreVert,
        IconKey.ContentCopy, IconKey.Refresh, IconKey.ViewAgenda, IconKey.GridView,
        IconKey.Apps, IconKey.Gallery -> drawPolishedActionIcon(key, style, tint)
        IconKey.ArrowBack, IconKey.ArrowForward, IconKey.KeyboardArrowLeft,
        IconKey.KeyboardArrowRight, IconKey.ExpandMore, IconKey.ExpandLess,
        IconKey.ArrowDropDown, IconKey.SwapVert, IconKey.OpenInNew,
        IconKey.CheckCircle, IconKey.Warning, IconKey.Error, IconKey.Info,
        IconKey.Visibility, IconKey.VisibilityOff, IconKey.Help ->
            drawPolishedDirectionStatusIcon(key, style, tint)
        else -> error("Not a control icon: $key")
    }
}
