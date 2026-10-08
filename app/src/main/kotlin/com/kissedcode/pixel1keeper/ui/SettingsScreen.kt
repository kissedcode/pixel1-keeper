package com.kissedcode.pixel1keeper.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kissedcode.pixel1keeper.R
import com.kissedcode.pixel1keeper.container
import com.kissedcode.pixel1keeper.data.CleanupMode
import com.kissedcode.pixel1keeper.data.Settings
import com.kissedcode.pixel1keeper.work.Scheduling
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    var s by remember { mutableStateOf<Settings?>(null) }
    LaunchedEffect(Unit) { s = c.settings.get() }
    val cur = s ?: return
    fun upd(f: (Settings) -> Settings) { s = f(cur) }
    val usageAccess = remember { c.health.hasUsageAccess() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.btn_back)) }
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)

        NumberField(stringResource(R.string.settings_window_from), cur.windowFromHour) { v -> upd { it.copy(windowFromHour = v) } }
        NumberField(stringResource(R.string.settings_window_to), cur.windowToHour) { v -> upd { it.copy(windowToHour = v) } }
        SwitchRow(stringResource(R.string.settings_charging), cur.onlyCharging) { v -> upd { it.copy(onlyCharging = v) } }
        NumberField(stringResource(R.string.settings_reserve), cur.reserveGb) { v -> upd { it.copy(reserveGb = v) } }
        Text(stringResource(R.string.settings_cutoff), style = MaterialTheme.typography.bodyMedium)
        SwitchRow(stringResource(R.string.settings_skip_screens), cur.skipScreenshots) { v -> upd { it.copy(skipScreenshots = v) } }

        HorizontalDivider()
        Text(stringResource(R.string.settings_cleanup), style = MaterialTheme.typography.titleMedium)
        RadioRow(stringResource(R.string.cleanup_careful), cur.cleanupMode == CleanupMode.CAREFUL, enabled = usageAccess) { upd { it.copy(cleanupMode = CleanupMode.CAREFUL) } }
        RadioRow(stringResource(R.string.cleanup_timer), cur.cleanupMode == CleanupMode.TIMER || !usageAccess, enabled = true) { upd { it.copy(cleanupMode = CleanupMode.TIMER) } }
        if (cur.cleanupMode == CleanupMode.TIMER || !usageAccess) {
            NumberField(stringResource(R.string.settings_delete_after), cur.deleteAfterHours) { v -> upd { it.copy(deleteAfterHours = v) } }
        }

        HorizontalDivider()
        Text(stringResource(R.string.settings_telegram), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(cur.telegramToken, { v -> upd { it.copy(telegramToken = v) } }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_tg_token)) }, singleLine = true,
            visualTransformation = PasswordVisualTransformation())
        OutlinedTextField(cur.telegramChatId, { v -> upd { it.copy(telegramChatId = v) } }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_tg_chat)) }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                scope.launch {
                    val id = c.telegram.findChatId(cur.telegramToken.trim())
                    if (id != null) { s = cur.copy(telegramChatId = id); toast(ctx, R.string.tg_chat_found) }
                    else toast(ctx, R.string.tg_chat_not_found)
                }
            }) { Text(stringResource(R.string.btn_find_chat)) }
            OutlinedButton(onClick = {
                scope.launch {
                    val r = c.telegram.send(ctx.getString(R.string.tg_test_message), cur.telegramToken.trim(), cur.telegramChatId.trim())
                    if (r.isSuccess) toast(ctx, R.string.tg_test_ok)
                    else Toast.makeText(ctx, ctx.getString(R.string.tg_test_fail, r.exceptionOrNull()?.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }) { Text(stringResource(R.string.btn_tg_test)) }
        }
        OutlinedTextField(cur.reportTime, { v -> upd { it.copy(reportTime = v) } }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_report_time)) }, singleLine = true)

        Button(onClick = {
            scope.launch {
                c.settings.save(cur)
                Scheduling.scheduleReport(ctx, cur.reportTime, replace = true)
                toast(ctx, R.string.saved)
                onBack()
            }
        }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.btn_save)) }
    }
}

private fun toast(ctx: android.content.Context, res: Int) = Toast.makeText(ctx, res, Toast.LENGTH_SHORT).show()

@Composable
private fun NumberField(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        text, { t -> text = t.filter(Char::isDigit).take(3); text.toIntOrNull()?.let(onChange) },
        Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(value, onChange)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick, enabled = enabled)
        Text(label)
    }
}
