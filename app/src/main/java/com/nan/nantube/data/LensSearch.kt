@file:OptIn(UnstableApi::class)

package com.nan.nantube.data

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.TextureView
import androidx.core.content.FileProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object LensSearch {
    private const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
    private val entryPoints = listOf(
        "com.google.android.apps.search.lens.LensShareEntryPointActivity",
        "com.google.android.apps.lens.MainActivity"
    )

    fun grab(view: PlayerView?): Bitmap? {
        val texture = view?.videoSurfaceView as? TextureView ?: return null
        return if (texture.isAvailable) texture.bitmap else null
    }

    suspend fun open(context: Context, frame: Bitmap): Boolean {
        val uri = withContext(Dispatchers.IO) { save(context, frame) } ?: return false
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/jpeg")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .apply { clipData = ClipData.newRawUri(null, uri) }
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        for (name in entryPoints) {
            try {
                context.startActivity(Intent(send).setClassName(GOOGLE_APP, name))
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return try {
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun save(context: Context, frame: Bitmap): Uri? = try {
        val file = File(File(context.cacheDir, "lens").apply { mkdirs() }, "frame.jpg")
        file.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    } catch (_: Exception) {
        null
    }
}
