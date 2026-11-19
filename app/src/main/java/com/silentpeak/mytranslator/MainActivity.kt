package com.silentpeak.mytranslator

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silentpeak.mytranslator.translate.TranslateViewModel
import com.silentpeak.mytranslator.translate.TranslationUiState
import com.silentpeak.mytranslator.translate.languageDisplayName
import com.silentpeak.mytranslator.ui.LanguagePickerDialog
import com.silentpeak.mytranslator.ui.theme.MyTranslatorTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: TranslateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyTranslatorTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: TranslateViewModel) {
    val sourceText by viewModel.sourceText.collectAsStateWithLifecycle()
    val sourceOverride by viewModel.sourceLanguageOverride.collectAsStateWithLifecycle()
    val targetLanguage by viewModel.targetLanguage.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.copied_to_clipboard)
    var isPickingSourceLanguage by remember { mutableStateOf(false) }
    var isPickingTargetLanguage by remember { mutableStateOf(false) }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        val speech = TextToSpeech(context) { /* initialized */ }
        tts = speech
        onDispose {
            speech.stop()
            speech.shutdown()
        }
    }

    if (isPickingSourceLanguage) {
        LanguagePickerDialog(
            title = stringResource(R.string.source_language),
            selectedLanguage = sourceOverride,
            allowAutoDetect = true,
            onLanguageSelected = viewModel::setSourceLanguageOverride,
            onDismissRequest = { isPickingSourceLanguage = false },
        )
    }

    if (isPickingTargetLanguage) {
        LanguagePickerDialog(
            title = stringResource(R.string.target_language),
            selectedLanguage = targetLanguage,
            allowAutoDetect = false,
            onLanguageSelected = { selected ->
                if (selected != null) viewModel.setTargetLanguage(selected)
            },
            onDismissRequest = { isPickingTargetLanguage = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Language selection row
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val override = sourceOverride
                    val currentUiState = uiState
                    val sourceLabel = when {
                        override != null -> languageDisplayName(override)
                        currentUiState is TranslationUiState.Success -> languageDisplayName(currentUiState.sourceLanguage)
                        currentUiState is TranslationUiState.Detecting -> stringResource(R.string.detecting_language)
                        else -> stringResource(R.string.auto_detect)
                    }

                    TextButton(
                        onClick = { isPickingSourceLanguage = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = sourceLabel,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(R.string.source_language),
                        )
                    }

                    IconButton(
                        onClick = viewModel::swapLanguages,
                        enabled = uiState is TranslationUiState.Success || sourceOverride != null,
                    ) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = stringResource(R.string.swap_languages),
                        )
                    }

                    TextButton(
                        onClick = { isPickingTargetLanguage = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = languageDisplayName(targetLanguage),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(R.string.target_language),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Source text input card
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = sourceText.orEmpty(),
                        onValueChange = viewModel::setText,
                        placeholder = { Text(stringResource(R.string.input_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 200.dp),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        if (!sourceText.isNullOrEmpty()) {
                            IconButton(onClick = { viewModel.setText("") }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.clear),
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService<ClipboardManager>()
                                    val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                    if (!clip.isNullOrBlank()) {
                                        viewModel.setText(clip)
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Default.ContentPaste,
                                    contentDescription = stringResource(R.string.copy),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Translation result card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.translate_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp),
                    ) {
                        when (val state = uiState) {
                            TranslationUiState.Idle -> {
                                Text(
                                    text = stringResource(R.string.input_placeholder),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.CenterStart),
                                )
                            }
                            TranslationUiState.Detecting -> ProgressRow(stringResource(R.string.detecting_language))
                            TranslationUiState.Downloading -> ProgressRow(stringResource(R.string.downloading_model))
                            TranslationUiState.Translating -> ProgressRow(stringResource(R.string.translating))
                            is TranslationUiState.Error -> {
                                Column(modifier = Modifier.align(Alignment.CenterStart)) {
                                    Text(
                                        text = state.message.asString(),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(onClick = viewModel::retry) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.retry))
                                    }
                                }
                            }
                            is TranslationUiState.Success -> {
                                Column {
                                    SelectionContainer {
                                        Text(
                                            text = state.translatedText,
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        IconButton(
                                            onClick = {
                                                val locale = Locale.forLanguageTag(targetLanguage)
                                                tts?.language = locale
                                                tts?.speak(
                                                    state.translatedText,
                                                    TextToSpeech.QUEUE_FLUSH,
                                                    null,
                                                    "main_tts",
                                                )
                                            },
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.VolumeUp,
                                                contentDescription = stringResource(R.string.speak),
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService<ClipboardManager>()
                                                clipboard?.setPrimaryClip(
                                                    ClipData.newPlainText("translation", state.translatedText),
                                                )
                                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                                    Toast.makeText(
                                                        context,
                                                        copiedMessage,
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                            },
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = stringResource(R.string.copy),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Integration hint banner
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.main_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressRow(message: String) {
    Row(
        modifier = Modifier.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MyTranslatorTheme {
        // Preview placeholder
    }
}
