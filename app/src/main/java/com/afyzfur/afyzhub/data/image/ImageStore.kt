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
 * 1. 通过选择器拿到的 URI 只有临时读权限，可能被系统回收，
 *    或源文件被用户删除，届时头像与背景会消失；
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

    /** 保存结果。失败时带上原因，供界面提示——静默失败会让用户无从判断 */
    sealed interface Result {
        data class Success(val path: String) : Result
        data class Failure(val reason: String) : Result
    }

    /**
     * 把 [source] 指向的图片缩放后存入私有目录。
     *
     * 失败原因逐项区分而非统一返回 null：图片读不出、格式不支持、
     * 磁盘写入失败的处理方式不同，笼统的"失败"对排查没有帮助。
     */
    suspend fun save(source: Uri, purpose: Purpose): Result = withContext(Dispatchers.IO) {
        val bitmap = try {
            decodeScaled(source, purpose.maxSize)
        } catch (e: OutOfMemoryError) {
            // OOM 是 Error 而非 Exception，catch (e: Exception) 抓不到
            return@withContext Result.Failure("图片过大，内存不足")
        } catch (e: SecurityException) {
            return@withContext Result.Failure("没有读取该图片的权限")
        } catch (e: Exception) {
            return@withContext Result.Failure("无法读取图片：${e.message ?: e.javaClass.simpleName}")
        } ?: return@withContext Result.Failure("图片格式无法解析")

        try {
            val dir = imageDir
            if (!dir.exists() && !dir.mkdirs()) {
                return@withContext Result.Failure("无法创建图片目录")
            }
            val target = File(dir, purpose.fileName)
            FileOutputStream(target).use { out ->
                // JPEG 而非 PNG：头像与背景都是照片类内容，
                // PNG 无损压缩在这类图上体积可达数倍
                val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                if (!ok) return@withContext Result.Failure("图片压缩失败")
            }
            Result.Success(target.absolutePath)
        } catch (e: Exception) {
            Result.Failure("保存失败：${e.message ?: e.javaClass.simpleName}")
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun delete(purpose: Purpose) = withContext(Dispatchers.IO) {
        File(imageDir, purpose.fileName).delete()
    }

    /**
     * 按归一化矩形裁剪已保存的图片，结果覆盖原文件。
     *
     * 参数用 0..1 的相对坐标而非像素：界面上的裁剪框是按预览尺寸
     * 拖动的，而预览与实际文件的分辨率不同，传像素需要调用方自己
     * 换算，换算错了就裁偏。
     *
     * 覆盖原文件而非另存：这个应用里每种用途只有一张图（背景、
     * 用户头像、助手头像各一），保留旧文件除了占空间没有别的作用。
     * 代价是裁剪不可逆——所以界面上让用户先看到结果再确认。
     */
    suspend fun crop(
        purpose: Purpose,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ): Result = withContext(Dispatchers.IO) {
        val file = File(imageDir, purpose.fileName)
        if (!file.exists()) return@withContext Result.Failure("图片不存在")

        val source = try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: OutOfMemoryError) {
            return@withContext Result.Failure("图片过大，内存不足")
        } ?: return@withContext Result.Failure("图片格式无法解析")

        try {
            // 起点也要留出至少 1 像素的余地。left 传 1.0 时 x 会等于
            // width，此时无论宽度取多少 createBitmap 都会越界——界面上
            // 的最小边长约束挡住了这种输入，但数据层不该依赖界面的约束
            val x = (left.coerceIn(0f, 1f) * source.width).toInt()
                .coerceIn(0, source.width - 1)
            val y = (top.coerceIn(0f, 1f) * source.height).toInt()
                .coerceIn(0, source.height - 1)
            // 至少留 1 像素：滑到极限时宽或高可能算成 0，
            // createBitmap 会直接抛异常
            val w = ((right - left).coerceIn(0f, 1f) * source.width).toInt()
                .coerceAtLeast(1)
                .coerceAtMost(source.width - x)
            val h = ((bottom - top).coerceIn(0f, 1f) * source.height).toInt()
                .coerceAtLeast(1)
                .coerceAtMost(source.height - y)

            val cropped = Bitmap.createBitmap(source, x, y, w, h)
            // 先写临时文件再替换，而不是直接覆盖原文件。
            // FileOutputStream 一打开就会把原文件清空，若随后压缩失败，
            // 用户的图片就永久没了——而裁剪本身是不可逆操作，
            // 至少不该在失败时连原图一起丢掉
            val temp = File(file.parentFile, "${purpose.fileName}.tmp")
            FileOutputStream(temp).use { out ->
                val ok = cropped.compress(Bitmap.CompressFormat.JPEG, 90, out)
                if (!ok) {
                    temp.delete()
                    return@withContext Result.Failure("图片压缩失败")
                }
            }
            if (!temp.renameTo(file)) {
                temp.delete()
                return@withContext Result.Failure("无法写入图片文件")
            }
            // 用引用比较：createBitmap 在裁剪范围等于整图时会直接返回
            // 入参，此时 cropped 就是 source，回收它会让 finally 里的
            // recycle 二次释放
            if (cropped !== source) cropped.recycle()
            Result.Success(file.absolutePath)
        } catch (e: Exception) {
            Result.Failure("裁剪失败：${e.message ?: e.javaClass.simpleName}")
        } finally {
            source.recycle()
        }
    }

    /**
     * 按需降采样解码。
     *
     * 先读尺寸信息算出采样率，再按该采样率解码，避免把整张原图
     * 读进内存后才缩小——大照片会直接 OOM。
     *
     * 关键点：把字节一次性读入内存后再解码两次，而不是两次
     * openInputStream。部分 content provider 返回的流不支持重复打开，
     * 或临时读权限在首次关闭后即失效，导致第二次读取拿到 null。
     */
    private fun decodeScaled(source: Uri, maxSize: Int): Bitmap? {
        val bytes = context.contentResolver.openInputStream(source)?.use {
            it.readBytes()
        } ?: return null

        if (bytes.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxSize)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            ?: return null

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
