package com.lolita.app.ui.screen.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lolita.app.BuildConfig

@Composable
internal fun AppUpdateHost(viewModel: AppUpdateViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.checkAutomaticallyOnce()
    }
    AppUpdateDialog(
        state = state,
        currentVersion = BuildConfig.VERSION_NAME,
        onDismiss = viewModel::dismissDialog,
        onRetry = viewModel::checkForUpdate,
        onOpenUrl = { url ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                viewModel.dismissDialog()
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "无法打开链接，请安装或启用浏览器", Toast.LENGTH_SHORT).show()
            } catch (_: SecurityException) {
                Toast.makeText(context, "系统阻止打开链接，请在浏览器中访问 Gitee", Toast.LENGTH_SHORT).show()
            }
        }
    )
}
