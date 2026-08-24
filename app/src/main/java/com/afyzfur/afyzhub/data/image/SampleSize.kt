package com.afyzfur.afyzhub.data.image

/**
 * 计算图片解码的降采样率。
 *
 * BitmapFactory 只接受 2 的幂作为 inSampleSize，因此逐次翻倍
 * 直到尺寸落到目标的两倍以内。留两倍余量是为了后续精确缩放时
 * 仍有足够像素——直接采样到目标尺寸会因整数取整损失清晰度。
 *
 * 单独成文件而非放在 [ImageStore] 内：那个文件依赖 Android 的
 * Bitmap 与 ContentResolver，在纯 JVM 测试环境下无法编译，
 * 而这段算术是最需要测试的部分（算错会导致大图 OOM）。
 *
 * 尺寸为非正数时返回 1：解码失败时 outWidth/outHeight 为 -1，
 * 不做保护会让循环条件永远成立。
 */
internal fun sampleSizeFor(width: Int, height: Int, maxSize: Int): Int {
    if (width <= 0 || height <= 0 || maxSize <= 0) return 1
    var sample = 1
    while (width / sample > maxSize * 2 || height / sample > maxSize * 2) {
        sample *= 2
    }
    return sample
}
