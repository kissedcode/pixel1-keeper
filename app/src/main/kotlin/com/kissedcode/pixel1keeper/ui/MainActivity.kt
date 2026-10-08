package com.kissedcode.pixel1keeper.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KeeperTheme { KeeperNav() } }
    }
}

private val Primary = Color(0xFF1F3A5F)
private val Accent = Color(0xFFFFCC00)

@Composable
fun KeeperTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme())
        darkColorScheme(primary = Color(0xFF9DB8DE), secondary = Accent, onSecondary = Color.Black)
    else lightColorScheme(primary = Primary, secondary = Accent, onSecondary = Color.Black)
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun KeeperNav() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onSetup = { nav.navigate("setup") },
                onSettings = { nav.navigate("settings") },
                onLog = { nav.navigate("log") },
                onLogin = { nav.navigate("login") },
            )
        }
        composable("setup") {
            SetupScreen(
                onLogin = { nav.navigate("login") },
                onTelegram = { nav.navigate("settings") },
                onDone = { nav.popBackStack("home", inclusive = false) },
            )
        }
        composable("login") { LoginScreen(onDone = { nav.popBackStack() }) }
        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
        composable("log") { LogScreen(onBack = { nav.popBackStack() }) }
    }
}
