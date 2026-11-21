package net.bitplane.android.microphone.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import net.bitplane.android.microphone.DeviceOption
import net.bitplane.android.microphone.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MicrophoneScreen(
    active: Boolean,
    onToggle: () -> Unit,
    inputs: List<DeviceOption>,
    selectedInput: String?,
    onSelectInput: (String?) -> Unit,
    outputs: List<DeviceOption>,
    selectedOutput: String?,
    onSelectOutput: (String?) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
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

            Spacer(Modifier.height(48.dp))

            DeviceDropdown(
                label = stringResource(R.string.input_label),
                options = inputs,
                selected = selectedInput,
                onSelect = onSelectInput,
            )

            Spacer(Modifier.height(16.dp))

            DeviceDropdown(
                label = stringResource(R.string.output_label),
                options = outputs,
                selected = selectedOutput,
                onSelect = onSelectOutput,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceDropdown(
    label: String,
    options: List<DeviceOption>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // A remembered device that isn't connected right now falls back to automatic.
    val current = options.firstOrNull { it.key == selected } ?: options.firstOrNull()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.widthIn(max = 360.dp),
    ) {
        OutlinedTextField(
            value = current?.label.orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option.key)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
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

private val previewInputs = listOf(
    DeviceOption(null, "Automatic"),
    DeviceOption("phone", "Phone microphone"),
    DeviceOption("bt", "Jabra Elite 85t"),
)

private val previewOutputs = listOf(
    DeviceOption(null, "Automatic"),
    DeviceOption("speaker", "Phone speaker"),
    DeviceOption("wired", "Headphones"),
)

@Preview(name = "Mic inactive")
@Composable
private fun MicrophoneScreenPreviewInactive() {
    MicrophoneTheme {
        MicrophoneScreen(
            active = false,
            onToggle = {},
            inputs = previewInputs,
            selectedInput = null,
            onSelectInput = {},
            outputs = previewOutputs,
            selectedOutput = null,
            onSelectOutput = {},
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
            inputs = previewInputs,
            selectedInput = "bt",
            onSelectInput = {},
            outputs = previewOutputs,
            selectedOutput = "speaker",
            onSelectOutput = {},
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
