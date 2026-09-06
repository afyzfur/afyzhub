package com.afyzfur.afyzhub.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.domain.model.ApiProfile
import com.afyzfur.afyzhub.ui.components.ModelIcon

@Composable
fun ModelSelectionSection(
    profile: ApiProfile,
    onChange: (ApiProfile) -> Unit
) {
    val selected = profile.effectiveSelectedModels.filter { it in profile.cachedModels }
    val available = profile.cachedModels.filterNot { it in selected }

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "已选择模型（${selected.size}）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
        )
        ModelSelectionRows(
            models = selected,
            selectedModel = profile.model,
            onClick = { model ->
                if (selected.size > 1) {
                    val next = selected.filterNot { it == model }
                    onChange(profile.copy(
                        model = if (profile.model == model) next.first() else profile.model,
                        selectedModels = next
                    ))
                }
            }
        )
        if (available.isNotEmpty()) {
            Text(
                text = "可用模型（${available.size}）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
            )
            ModelSelectionRows(
                models = available,
                selectedModel = "",
                onClick = { model ->
                    onChange(profile.copy(selectedModels = selected + model))
                }
            )
        }
    }
}

@Composable
private fun ModelSelectionRows(
    models: List<String>,
    selectedModel: String,
    onClick: (String) -> Unit
) {
    models.forEachIndexed { index, model ->
        if (index > 0) SettingsItemDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick(model) }
                .background(
                    if (model == selectedModel) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    }
                )
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            ModelIcon(modelName = model, size = 20.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = model,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (model == selectedModel) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "当前模型",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
