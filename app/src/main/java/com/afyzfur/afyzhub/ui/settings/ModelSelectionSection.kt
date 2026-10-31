package com.afyzfur.afyzhub.ui.settings

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.domain.model.ApiProfile
import com.afyzfur.afyzhub.ui.components.ModelIcon

private const val PAGE_SIZE = 10

@Composable
fun ModelSelectionSection(
    profile: ApiProfile,
    onChange: (ApiProfile) -> Unit
) {
    val selected = profile.selectedModels.filter { it in profile.cachedModels }
    val available = profile.cachedModels.filterNot { it in selected }
    
    var selectedPage by remember { mutableIntStateOf(0) }
    var availablePage by remember { mutableIntStateOf(0) }
    
    Column(Modifier.fillMaxWidth()) {
        // 已选择模型区域
        if (selected.isNotEmpty()) {
            Text(
                text = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.models_selected, selected.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)
            )
            
            val selectedPages = selected.chunked(PAGE_SIZE)
            val currentSelectedPage = selectedPage.coerceIn(0, (selectedPages.size - 1).coerceAtLeast(0))
            
            selectedPages.getOrNull(currentSelectedPage)?.forEach { model ->
                val isCurrent = model == profile.model
                ModelRow(
                    model = model,
                    isCurrent = isCurrent,
                    onClick = { onChange(profile.copy(model = model)) },
                    onRemove = if (selected.size > 1) {
                        { 
                            val newSelected = selected.filterNot { it == model }
                            // 删的是当前模型时切到剩下的第一个
                            onChange(profile.copy(
                                model = if (isCurrent) newSelected.first() else profile.model,
                                selectedModels = newSelected
                            ))
                        }
                    } else null
                )
            }
            
            // 最后一页不满时补齐高度，翻页时分页控件不会上跳
            val selRemainder = selected.size % PAGE_SIZE
            if (selectedPages.size > 1 && selRemainder != 0 && currentSelectedPage == selectedPages.size - 1) {
                Spacer(Modifier.height(((PAGE_SIZE - selRemainder) * 48).dp))
            }
            if (selectedPages.size > 1) {
                PaginationControls(
                    currentPage = currentSelectedPage,
                    totalPages = selectedPages.size,
                    onPrevious = { if (currentSelectedPage > 0) selectedPage = currentSelectedPage - 1 },
                    onNext = { if (currentSelectedPage < selectedPages.size - 1) selectedPage = currentSelectedPage + 1 }
                )
            }
        }
        
        // 可用模型区域
        if (available.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.models_available, available.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
            )
            
            val availablePages = available.chunked(PAGE_SIZE)
            val currentAvailablePage = availablePage.coerceIn(0, (availablePages.size - 1).coerceAtLeast(0))
            
            availablePages.getOrNull(currentAvailablePage)?.forEach { model ->
                ModelRow(
                    model = model,
                    isCurrent = false,
                    onClick = {
                        // 加入已选列表的同时切为当前使用：从可用区点一个
                        // 模型，意图几乎必然是"用它"，只入列不切换会让人
                        // 以为换模型没生效
                        onChange(profile.copy(
                            selectedModels = selected + model,
                            model = model
                        ))
                    }
                )
            }
            
            // 同上：最后一页补齐，避免翻页时按钮乱动
            val availRemainder = available.size % PAGE_SIZE
            if (availablePages.size > 1 && availRemainder != 0 && currentAvailablePage == availablePages.size - 1) {
                Spacer(Modifier.height(((PAGE_SIZE - availRemainder) * 48).dp))
            }
            if (availablePages.size > 1) {
                PaginationControls(
                    currentPage = currentAvailablePage,
                    totalPages = availablePages.size,
                    onPrevious = { if (currentAvailablePage > 0) availablePage = currentAvailablePage - 1 },
                    onNext = { if (currentAvailablePage < availablePages.size - 1) availablePage = currentAvailablePage + 1 }
                )
            }
        }
    }
}

/**
 * 模型行。
 *
 * 当前使用的模型用加深背景加粗标记——不放对勾，勾选符号在
 * 多个已选模型并存时反而分不清"已选"与"在用"。整行点击切换
 * 当前模型，尾部 ✕ 一步移出已选列表。
 */
@Composable
private fun ModelRow(
    model: String,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(AppShapeTokens.SettingsGroup)
            .clickable(onClick = onClick)
            .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModelIcon(modelName = model, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = model,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onRemove != null) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_2f752c005ec5),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onRemove)
            )
        }
    }
}

@Composable
private fun PaginationControls(
    currentPage: Int,
    totalPages: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious, enabled = currentPage > 0) {
            Icon(Icons.Default.KeyboardArrowLeft, com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_b41561d80765))
        }
        Text(
            text = "${currentPage + 1} / $totalPages",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        IconButton(onClick = onNext, enabled = currentPage < totalPages - 1) {
            Icon(Icons.Default.KeyboardArrowRight, com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_67a246a344ae))
        }
    }
}
