package com.afyzfur.afyzhub.ui.settings

/** 模型列表每页条数。超过这个数量才出现翻页控件。 */
const val MODELS_PER_PAGE = 10

/**
 * 模型列表的分页视图。
 *
 * 中转服务常给出两三百个模型，一次全平铺会把设置页撑得极长，
 * 想找某个模型只能反复滚动。
 *
 * 抽成纯函数而非在 Composable 里就地切片：分页的边界条件
 * （末页不足整页、越界页码、空列表）值得用测试固定下来。
 */
data class ModelPage(
    /** 当前页的模型 */
    val models: List<String>,
    /** 从 0 开始的当前页序号，已夹到合法范围内 */
    val pageIndex: Int,
    val pageCount: Int
) {
    /** 只有一页时不显示翻页控件——十个以内没有翻页的必要 */
    val showPager: Boolean get() = pageCount > 1

    val hasPrevious: Boolean get() = pageIndex > 0
    val hasNext: Boolean get() = pageIndex < pageCount - 1
}

/**
 * 取 [models] 的第 [pageIndex] 页。
 *
 * [pageIndex] 会被夹到 `0..pageCount-1`：模型列表会因刷新而变短，
 * 此时界面上残留的页码可能越界，夹住比抛异常或返回空页更合理——
 * 用户看到的是末页而不是空白。
 */
fun pageOfModels(
    models: List<String>,
    pageIndex: Int,
    perPage: Int = MODELS_PER_PAGE
): ModelPage {
    if (models.isEmpty()) {
        return ModelPage(models = emptyList(), pageIndex = 0, pageCount = 0)
    }

    // 向上取整，末页允许不足整页
    val pageCount = (models.size + perPage - 1) / perPage
    val safeIndex = pageIndex.coerceIn(0, pageCount - 1)
    val from = safeIndex * perPage
    val to = minOf(from + perPage, models.size)

    return ModelPage(
        models = models.subList(from, to),
        pageIndex = safeIndex,
        pageCount = pageCount
    )
}
