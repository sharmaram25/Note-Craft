package com.notecraft.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Stores note images in app-private storage, downscaled to a sensible size, and serves
 * memory-cached thumbnails. Deliberately dependency-free (no image loading library).
 */
class AttachmentStore(private val context: Context) {

    val dir: File = File(context.filesDir, "attachments").apply { mkdirs() }

    data class Imported(val fileName: String, val width: Int, val height: Int)

    private val cache = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun file(name: String) = File(dir, name)

    suspend fun import(uri: Uri, maxDim: Int = 2048): Imported? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0) return@runCatching null
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDim) }
            var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return@runCatching null
            val rotation = resolver.openInputStream(uri)?.use { exifRotation(ExifInterface(it)) } ?: 0
            bmp = scaleAndRotate(bmp, maxDim, rotation)
            val name = "${UUID.randomUUID()}.jpg"
            File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            Imported(name, bmp.width, bmp.height).also { bmp.recycle() }
        }.getOrNull()
    }

    suspend fun importFile(src: File): Imported? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(src.path, bounds)
            if (bounds.outWidth <= 0) return@runCatching null
            val name = "${UUID.randomUUID()}.jpg"
            src.copyTo(File(dir, name), overwrite = true)
            Imported(name, bounds.outWidth, bounds.outHeight)
        }.getOrNull()
    }

    fun cached(name: String, reqSize: Int): ImageBitmap? = cache.get("$name@$reqSize")

    suspend fun load(name: String, reqSize: Int): ImageBitmap? {
        val key = "$name@$reqSize"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val f = File(dir, name)
                if (!f.exists()) return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, bounds)
                val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, reqSize) }
                BitmapFactory.decodeFile(f.path, opts)?.asImageBitmap()?.also { cache.put(key, it) }
            }.getOrNull()
        }
    }

    fun delete(name: String) {
        cache.snapshot().keys.filter { it.startsWith("$name@") }.forEach { cache.remove(it) }
        File(dir, name).delete()
    }

    private fun sampleSize(w: Int, h: Int, req: Int): Int {
        var s = 1
        while (w / (s * 2) >= req || h / (s * 2) >= req) s *= 2
        return s
    }

    private fun exifRotation(exif: ExifInterface): Int = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    private fun scaleAndRotate(src: Bitmap, maxDim: Int, rotation: Int): Bitmap {
        val scale = minOf(1f, maxDim.toFloat() / maxOf(src.width, src.height))
        if (scale >= 1f && rotation == 0) return src
        val m = Matrix().apply { postScale(scale, scale); postRotate(rotation.toFloat()) }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (out != src) src.recycle()
        return out
    }
}
