package com.kissedcode.pixel1keeper.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.kissedcode.pixel1keeper.R
import com.kissedcode.pixel1keeper.container
import com.kissedcode.pixel1keeper.device.DeviceHealth
import kotlinx.coroutines.launch

@SuppressLint("BatteryLife")
@Composable
fun SetupScreen(onLogin: () -> Unit, onTelegram: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    val settings by c.settings.flow.collectAsState(initial = null)
    var loggedIn by remember { mutableStateOf(false) }
    var usage by remember { mutableStateOf(false) }
    var battery by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        usage = c.health.hasUsageAccess()
        battery = c.health.ignoringBatteryOptimizations()
        val job = scope.launch { c.session.reset(); loggedIn = c.session.validate() }
        onPauseOrDispose { job.cancel() }
    }
    val telegram = settings?.let { it.telegramToken.isNotBlank() && it.telegramChatId.isNotBlank() } ?: false
    val gphotos = settings?.setupGphotosDone ?: false

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.setup_subtitle))

        Step(1, stringResource(R.string.setup_step_login), loggedIn) {
            Button(onClick = onLogin) { Text(stringResource(R.string.btn_login)) }
        }
        Step(2, stringResource(R.string.setup_step_usage), usage) {
            Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text(stringResource(R.string.btn_open_settings)) }
        }
        Step(3, stringResource(R.string.setup_step_battery), battery) {
            Button(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
            }) { Text(stringResource(R.string.btn_open_settings)) }
        }
        Step(4, stringResource(R.string.setup_step_telegram), telegram) {
            Button(onClick = onTelegram) { Text(stringResource(R.string.btn_configure)) }
        }
        Step(5, stringResource(R.string.setup_step_gphotos), gphotos) {
            Row {
                OutlinedButton(onClick = { help = true }) { Text(stringResource(R.string.btn_how)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { scope.launch { c.settings.setGphotosDone(true) } }) { Text(stringResource(R.string.setup_gphotos_done)) }
            }
        }
        Button(onClick = onDone, enabled = loggedIn, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.btn_start)) }
    }

    if (help) {
        AlertDialog(
            onDismissRequest = { help = false },
            text = { Text(stringResource(R.string.setup_gphotos_help)) },
            confirmButton = {
                TextButton(onClick = {
                    help = false
                    ctx.packageManager.getLaunchIntentForPackage(DeviceHealth.GOOGLE_PHOTOS)?.let(ctx::startActivity)
                }) { Text(stringResource(R.string.btn_open_settings)) }
            },
            dismissButton = { TextButton(onClick = { help = false }) { Text(stringResource(R.string.btn_close)) } },
        )
    }
}

@Composable
private fun Step(n: Int, title: String, done: Boolean, action: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (done) "✓" else "$n", style = MaterialTheme.typography.titleLarge,
                color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (!done) action()
            }
        }
    }
}
