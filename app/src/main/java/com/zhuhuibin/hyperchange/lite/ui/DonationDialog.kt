// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zhuhuibin.hyperchange.lite.R
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.window.WindowDialog

/** The About row and its local dialog never launch another app or a payment action. */
@Composable
internal fun DonationEntry() {
    var show by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val saved = uiText("已保存到相册")
    val failed = uiText("保存失败，请重试")
    ArrowPreference(uiText("微信赞赏"), insideMargin = compactPreferenceMargin, onClick = { show = true })
    if (show) DonationDialog(saving, onDismiss = { show = false }, onSave = {
        if (!saving) {
            saving = true
            scope.launch { try {
                saveDonationImage(context)
                Toast.makeText(context, saved, Toast.LENGTH_SHORT).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
            } finally { saving = false } }
        }
    })
}

@Composable
private fun DonationDialog(saving: Boolean, onDismiss: () -> Unit, onSave: () -> Unit) {
    val context = LocalContext.current
    var image by remember(context) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(context) { mutableStateOf(false) }
    LaunchedEffect(context) {
        val decoded = withContext(Dispatchers.IO) {
            runCatching {
                context.resources.openRawResource(R.raw.wechat_donation).use {
                    checkNotNull(BitmapFactory.decodeStream(it)) { "Unable to decode donation image" }.asImageBitmap()
                }
            }
        }
        decoded.fold({ image = it }, { failed = true })
    }
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val heightLimit = windowHeight * .55f
    WindowDialog(show = true, title = uiText("微信赞赏"), onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, heightLimit, 360.dp)
                val bitmap = image
                if (bitmap != null) Image(bitmap, uiText("微信赞赏码"), Modifier.size(side), contentScale = ContentScale.Fit)
                else Box(Modifier.size(side), contentAlignment = Alignment.Center) {
                    Text(uiText(if (failed) "图片加载失败" else "正在加载图片"))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(uiText("关闭"), onDismiss, Modifier.weight(1f), minHeight = 44.dp)
                TextButton(uiText(if (saving) "保存中…" else "保存到相册"), onSave,
                    Modifier.weight(1f), enabled = !saving, minHeight = 44.dp)
            }
        }
    }
}

/** Preserve the original PNG bytes; pending rows are removed on every failed write/publish. */
internal suspend fun saveDonationImage(context: Context): Uri = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "hc-lite-wechat-donation-${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/hc-lite")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
        ?: throw IOException("Unable to create donation image")
    try {
        context.resources.openRawResource(R.raw.wechat_donation).use { input ->
            val output = resolver.openOutputStream(uri, "w") ?: throw IOException("Unable to write donation image")
            output.use { if (input.copyTo(it) == 0L) throw IOException("Empty donation image") }
        }
        ensureActive()
        val publish = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
        if (resolver.update(uri, publish, null, null) != 1) throw IOException("Unable to publish donation image")
        uri
    } catch (failure: Throwable) {
        runCatching { resolver.delete(uri, null, null) }.onFailure { if (it !== failure) failure.addSuppressed(it) }
        throw failure
    }
}
