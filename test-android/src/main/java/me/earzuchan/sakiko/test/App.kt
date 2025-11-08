package me.earzuchan.sakiko.test

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import main
import me.earzuchan.sakiko.launcher.Launcher
import java.io.File

const val TAG = "Test"

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

private var haveBeen = false

private var success by mutableStateOf(false)

private var errText by mutableStateOf("")

private fun once(block: () -> Unit) {
    if (haveBeen) return

    haveBeen = true
    block()
}

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()

        once {
            val dexName = "module.dex"

            val dexPath = File(cacheDir, dexName).apply {
                writeBytes(assets.open(dexName).readBytes())
            }.absolutePath.also { Log.i(TAG, "MODULE DEX：$it") }

            Launcher.findAndLoadFromDexByPath(dexPath)

            runCatching { main() }.onSuccess { success = true }.onFailure {
                val err = it.stackTraceToString()
                Log.e(TAG, err)
                errText = err
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppTheme {
                Scaffold(Modifier.fillMaxSize()) { innerPadding ->
                    LazyColumn(
                        Modifier.padding(16.dp),
                        contentPadding = innerPadding,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { Text("Sakiko Android Test") }

                        item { Text("Success: $success\n$errText") }
                    }
                }

            }
        }
    }
}