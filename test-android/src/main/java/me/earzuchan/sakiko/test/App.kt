package me.earzuchan.sakiko.test

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.toClass
import me.earzuchan.sakiko.api.module.SakikoContext
import me.earzuchan.sakiko.core.initCore

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

private fun once(block: () -> Unit) {
    if (haveBeen) return

    haveBeen = true
    block()
}

class MainActivity : ComponentActivity() {

    override fun onStart() {
        super.onStart()

        once {
            initAndSetupCore()

            TestModuleEntry1().onHook()
            TestModuleEntry2().onHook()

            runCatching { main() }.onSuccess { success = true }
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
                        item { Text("Sakiko Android Test\nCheck your Logcat for results") }

                        item { Text("Success: $success") }
                    }
                }

            }
        }
    }

    // CHECK：权宜之计
    private fun initAndSetupCore() {
        initCore()

        "me.earzuchan.sakiko.api.module.ModuleKt".toClass().resolve()
            .firstField { name = "sakiCtxLocal" }.get<ThreadLocal<SakikoContext>>()!!.set(SakikoContext(classLoader))
    }
}