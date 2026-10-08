package com.kissedcode.pixel1keeper.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.kissedcode.pixel1keeper.R
import com.kissedcode.pixel1keeper.container
import com.kissedcode.pixel1keeper.device.DeviceHealth
import com.kissedcode.pixel1keeper.work.Scheduling
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(onSetup: () -> Unit, onSettings: () -> Unit, onLog: () -> Unit, onLogin: () -> Unit) {
    val ctx = LocalContext.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    val settings by c.settings.flow.collectAsState(initial = null)
    val expired by c.settings.sessionExpiredFlow.collectAsState(initial = false)
    val pausedUntil by c.settings.pausedUntilFlow.collectAsState(initial = 0L)
    val lastRun by c.settings.lastRunFlow.collectAsState(initial = null)
    val queued by c.db.itemDao().queuedFlow().collectAsState(initial = 0)
    val onDevice by c.db.itemDao().onDeviceFlow().collectAsState(initial = 0)
    val transferred by c.db.itemDao().transferredFlow().collectAsState(initial = 0)
    val running by remember {
        WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow(Scheduling.NIGHT)
            .map { l -> l.any { it.state == WorkInfo.State.RUNNING } }
    }.collectAsState(initial = false)
    val free = remember { mutableLongStateOf(c.health.freeBytes()) }
    LifecycleResumeEffect(Unit) { free.longValue = c.health.freeBytes(); onPauseOrDispose { } }

    // Первый запуск: сразу на экран подготовки, пока нет входа.
    var autoOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settings) {
        val s = settings ?: return@LaunchedEffect
        if (!s.setupGphotosDone && !autoOpened) { autoOpened = true; onSetup() }
    }

    val fmt = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(Scheduling.ZONE)
    val status = when {
        expired -> stringResource(R.string.status_relogin)
        pausedUntil > System.currentTimeMillis() -> stringResource(R.string.status_rate, fmt.format(Instant.ofEpochMilli(pausedUntil)))
        settings?.paused == true -> stringResource(R.string.status_paused)
        running -> stringResource(R.string.status_running)
        else -> stringResource(R.string.status_waiting)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        InfoCard(stringResource(R.string.home_status), status)
        if (expired) Button(onClick = onLogin, Modifier.fillMaxWidth()) { Text(stringResource(R.string.btn_login)) }
        InfoCard(
            stringResource(R.string.home_last_run),
            lastRun?.let { stringResource(R.string.home_last_run_value, fmt.format(Instant.ofEpochMilli(it.at)), it.downloaded, it.deleted) }
                ?: stringResource(R.string.home_never),
        )
        InfoCard(stringResource(R.string.home_queue), stringResource(R.string.home_queue_value, queued, onDevice, transferred))
        InfoCard(
            stringResource(R.string.home_memory),
            stringResource(R.string.home_memory_value, DeviceHealth.gb(free.longValue), (settings?.reserveGb ?: 2).toString()),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            Scheduling.startNight(ctx, onlyCharging = false, manual = true)
            Toast.makeText(ctx, R.string.run_now_queued, Toast.LENGTH_SHORT).show()
        }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.btn_run_now)) }
        OutlinedButton(onClick = { scope.launch { c.settings.setPaused(!(settings?.paused ?: false)) } }, Modifier.fillMaxWidth()) {
            Text(stringResource(if (settings?.paused == true) R.string.btn_resume else R.string.btn_pause))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onSettings) { Text(stringResource(R.string.btn_settings)) }
            TextButton(onClick = onLog) { Text(stringResource(R.string.btn_log)) }
            TextButton(onClick = onSetup) { Text(stringResource(R.string.btn_check_setup)) }
        }
    }
}
