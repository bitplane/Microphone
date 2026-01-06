package net.bitplane.android.microphone.ui

import android.webkit.WebView
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsControllerCompat
import net.bitplane.android.microphone.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MicrophoneScreen(
    active: Boolean,
    onToggle: () -> Unit,
    onShowAbout: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) },
                actions = {
                    IconButton(onClick = onShowAbout) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = stringResource(id = R.string.about)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            val isDark = isSystemInDarkTheme()

            val background = when {
                active && isDark ->
                    MaterialTheme.colorScheme.primary

                active ->
                    MaterialTheme.colorScheme.primaryContainer

                else ->
                    MaterialTheme.colorScheme.surfaceVariant
            }

            Surface(
                modifier = Modifier
                    .size(140.dp)
                    .shadow(6.dp, CircleShape)
                    .clickable(onClick = onToggle),
                shape = CircleShape,
                color = background
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (active) Icons.Filled.Mic else Icons.Filled.MicOff,
                        contentDescription = if (active) {
                            stringResource(id = R.string.mic_active)
                        } else {
                            stringResource(id = R.string.cancel_mic)
                        },
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionScreen(
    showExplanation: Boolean,
    onRequestPermissions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (showExplanation) {
            Text(
                text = stringResource(id = R.string.app_grant_the_permissions),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        Button(onClick = onRequestPermissions) {
            Text(text = stringResource(id = R.string.grant_permissions))
        }
    }
}

@Composable
fun AboutDialog(
    aboutHtml: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
        title = { Text(text = stringResource(id = R.string.about)) },
        text = {
            AboutDialogWebView(aboutHtml)
        }
    )
}

@Composable
private fun AboutDialogWebView(aboutHtml: String) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                loadDataWithBaseURL(null, aboutHtml, "text/html", "UTF-8", null)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp)
    )
}

@Composable
fun MicrophoneTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    val activity = LocalActivity.current
    val darkTheme = isSystemInDarkTheme()

    SideEffect {
        val window = activity?.window ?: return@SideEffect
        WindowInsetsControllerCompat(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        content = content
    )
}

@Preview(name = "Mic inactive")
@Composable
private fun MicrophoneScreenPreviewInactive() {
    MicrophoneTheme {
        MicrophoneScreen(
            active = false,
            onToggle = {},
            onShowAbout = {}
        )
    }
}

@Preview(name = "Mic active")
@Composable
private fun MicrophoneScreenPreviewActive() {
    MicrophoneTheme {
        MicrophoneScreen(
            active = true,
            onToggle = {},
            onShowAbout = {}
        )
    }
}

@Preview(name = "Permissions")
@Composable
private fun PermissionScreenPreview() {
    MicrophoneTheme {
        PermissionScreen(
            showExplanation = true,
            onRequestPermissions = {}
        )
    }
}
