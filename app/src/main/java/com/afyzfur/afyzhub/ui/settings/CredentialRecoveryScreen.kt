package com.afyzfur.afyzhub.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.afyzhub.chat.R

/** A separate confirmation is mandatory because the encrypted profile JSON includes prompts. */
@Composable
internal fun CredentialRecoveryScreen(
    failed: Boolean,
    onNavigateBack: () -> Unit,
    onConfirmed: () -> Unit
) {
    var confirming by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding()) {
            SettingsPageHeader(stringResource(R.string.credential_recovery_title), onNavigateBack)
            Text(
                stringResource(R.string.credential_recovery_message),
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.error
            )
            if (failed) Text(stringResource(R.string.credential_recovery_failed), Modifier.padding(24.dp))
            Button(onClick = { confirming = true }, modifier = Modifier.padding(24.dp)) {
                Text(stringResource(R.string.credential_reset_action))
            }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.credential_reset_action)) },
            text = { Text(stringResource(R.string.credential_reset_confirmation)) },
            confirmButton = {
                TextButton(onClick = { confirming = false; onConfirmed() }) {
                    Text(stringResource(R.string.credential_reset_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.credential_keep_action)) }
            }
        )
    }
}
