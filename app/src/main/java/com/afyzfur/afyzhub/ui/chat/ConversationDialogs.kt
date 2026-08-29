package com.afyzfur.afyzhub.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * 单个文本输入的对话框，用于重命名与改简介。
 *
 * 内部持有 [TextFieldValue] 而非 String：这里的值不经过 DataStore，
 * 但用同一种写法保持一致，也避免将来接上异步存储时重蹈
 * "光标被回填重置到 0" 的覆辙。
 */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    placeholder: String,
    /** 允许提交空串。简介可以清空，名称不行 */
    allowEmpty: Boolean = false,
    singleLine: Boolean = true,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // 光标放末尾：进来就是要接着改，停在开头得先自己移过去
    var field by remember {
        mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
    }
    val trimmed = field.text.trim()
    val canConfirm = allowEmpty || trimmed.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it },
                placeholder = { Text(placeholder) },
                singleLine = singleLine,
                maxLines = if (singleLine) 1 else 4,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = canConfirm
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 分组选择对话框。
 *
 * 已有分组用单选列出，同时留一个输入框新建——把"选已有"和"建新的"
 * 放在一起，省掉先问"新建还是选择"的一步。
 *
 * 「未分组」是一个显式选项而不是留空输入：让人看得见当前不在任何
 * 分组里，也提供把会话移出分组的入口。
 */
@Composable
fun GroupPickerDialog(
    current: String,
    existingGroups: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(current) }
    var newGroup by remember { mutableStateOf(TextFieldValue("")) }

    // 输入了新分组名就以它为准，否则用选中的
    val typed = newGroup.text.trim()
    val result = typed.ifEmpty { selected }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动到分组") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                GroupOption(
                    label = "未分组",
                    selected = selected.isEmpty() && typed.isEmpty(),
                    onClick = {
                        selected = ""
                        newGroup = TextFieldValue("")
                    }
                )
                existingGroups.forEach { group ->
                    GroupOption(
                        label = group,
                        selected = selected == group && typed.isEmpty(),
                        onClick = {
                            selected = group
                            newGroup = TextFieldValue("")
                        }
                    )
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newGroup,
                    onValueChange = { newGroup = it },
                    label = { Text("新建分组") },
                    placeholder = { Text("输入新的分组名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(result) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun GroupOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
