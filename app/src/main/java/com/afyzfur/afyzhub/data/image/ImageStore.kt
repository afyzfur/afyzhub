package com.afyzfur.afyzhub.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 用户选择的图片的本地副本管理。
 *
 * 为什么复制而不直接存 SAF 的 URI：
 * 1. 通过 ACTION_OPEN_DOCUMENT 拿到的 URI 需要显式持久化权限，
 *    而权限可能被系统回收，或源文件被用户删除，届时头像与背景会消失；
 * 2. 原图可能是数 MB 的相机照片，每次渲染都解码既慢又占内存。
 *
 * 复制时按用途缩放：头像只需几十 dp，背景不超过屏幕尺寸。
 */
class ImageStore(private val context: Context) {

    /** 图片用途，决定缩放上限与文件名 */
    enum class Purpose(val fileName: String, val maxSize: Int) {
        /** 头像显示尺寸约 40dp，512 足够覆盖高密度屏 */
        USER_AVATAR("user_avatar.jpg", 512),
        ASSISTANT_AVATAR("assistant_avatar.jpg", 512),

        /** 背景铺满屏幕，取长边 1920 兼顾清晰度与内存 */
        CHAT_BACKGROUND("chat_background.jpg", 1920)
    }

    private val imageDir: File
        get() = File(context.filesDir, "images").apply { if (!exists()) mkdirs() }

    /**
     * 把 [source] 指向的图片缩放后存入私有目录。
     *
     * @return 保存后的文件路径；读取或解码失败返回 null
     */
    suspend fun save(source: Uri, purpose: Purpose): String? = withContext(Dispatchers.IO) {
        try {
            val bitmap = decodeScaled(source, purpose.maxSize) ?: return@withContext null
            val target = File(imageDir, purpose.fileName)
            FileOutputStream(target).use { out ->
                // JPEG 而非 PNG：头像与背景都是照片类内容，
                // PNG 无损压缩在这类图上体积可达数倍
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            bitmap.recycle()
            target.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    suspend fun delete(purpose: Purpose) = withContext(Dispatchers.IO) {
        File(imageDir, purpose.fileName).delete()
    }

    /**
     * 按需降采样解码。
     *
     * 先只读尺寸信息（inJustDecodeBounds），算出采样率后再真正解码，
     * 避免把整张原图读进内存后再缩小——大照片会直接 OOM。
     */
    private fun decodeScaled(source: Uri, maxSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (
            bounds.outWidth / sample > maxSize * 2 ||
            bounds.outHeight / sample > maxSize * 2
        ) {
            sample *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: return null

        // 采样率是 2 的幂，降采样后仍可能超限，再做一次精确缩放
        val longest = maxOf(decoded.width, decoded.height)
        if (longest <= maxSize) return decoded

        val scale = maxSize.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }
}
