package com.afyzfur.afyzhub.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Check
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
                text = "已选择模型（${selected.size}）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)
            )
            
            val selectedPages = selected.chunked(PAGE_SIZE)
            val currentSelectedPage = selectedPage.coerceIn(0, (selectedPages.size - 1).coerceAtLeast(0))
            
            selectedPages.getOrNull(currentSelectedPage)?.forEach { model ->
                ModelRow(
                    model = model,
                    isSelected = true,
                    isCurrent = model == profile.model,
                    onClick = {
                        if (selected.size > 1) {
                            val newSelected = selected.filterNot { it == model }
                            val newModel = if (profile.model == model) newSelected.first() else profile.model
                            onChange(profile.copy(
                                model = newModel,
                                selectedModels = newSelected
                            ))
                        }
                    }
                )
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
                text = "可用模型（${available.size}）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
            )
            
            val availablePages = available.chunked(PAGE_SIZE)
            val currentAvailablePage = availablePage.coerceIn(0, (availablePages.size - 1).coerceAtLeast(0))
            
            availablePages.getOrNull(currentAvailablePage)?.forEach { model ->
                ModelRow(
                    model = model,
                    isSelected = false,
                    isCurrent = false,
                    onClick = {
                        onChange(profile.copy(selectedModels = selected + model))
                    }
                )
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

@Composable
private fun ModelRow(
    model: String,
    isSelected: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModelIcon(modelName = model, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = model,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "已选择",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
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
            Icon(Icons.Default.KeyboardArrowLeft, "上一页")
        }
        Text(
            text = "${currentPage + 1} / $totalPages",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        IconButton(onClick = onNext, enabled = currentPage < totalPages - 1) {
            Icon(Icons.Default.KeyboardArrowRight, "下一页")
        }
    }
}
