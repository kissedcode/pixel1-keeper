package com.kissedcode.pixel1keeper.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kissedcode.pixel1keeper.R
import com.kissedcode.pixel1keeper.container
import com.kissedcode.pixel1keeper.work.Scheduling
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
fun LogScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val events by ctx.container.db.eventDao().recent().collectAsState(initial = emptyList())
    val fmt = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss").withZone(Scheduling.ZONE)
    fun line(e: com.kissedcode.pixel1keeper.data.EventEntity) = "${fmt.format(Instant.ofEpochMilli(e.ts))} ${e.level} ${e.type}: ${e.message}"
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            TextButton(onClick = onBack) { Text(stringResource(R.string.btn_back)) }
            TextButton(onClick = {
                val cm = ctx.getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("log", events.joinToString("\n", transform = ::line)))
                Toast.makeText(ctx, R.string.log_copied, Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.btn_copy_log)) }
        }
        Text(stringResource(R.string.log_title), style = MaterialTheme.typography.headlineSmall)
        LazyColumn { items(events, key = { it.id }) { Text(line(it), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) } }
    }
}
