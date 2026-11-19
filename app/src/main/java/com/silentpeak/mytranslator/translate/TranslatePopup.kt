package com.silentpeak.mytranslator.translate

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.nl.translate.TranslateLanguage
import com.silentpeak.mytranslator.R
import com.silentpeak.mytranslator.ui.LanguagePickerDialog
import com.silentpeak.mytranslator.ui.theme.MyTranslatorTheme
import java.util.Locale

@Composable
fun TranslatePopup(
    viewModel: TranslateViewModel,
    isReadOnly: Boolean = true,
    onReplace: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val sourceText by viewModel.sourceText.collectAsStateWithLifecycle()
    val sourceOverride by viewModel.sourceLanguageOverride.collectAsStateWithLifecycle()
    val targetLanguage by viewModel.targetLanguage.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TranslatePopupContent(
        sourceText = sourceText.orEmpty(),
        sourceLanguageOverride = sourceOverride,
        targetLanguage = targetLanguage,
        uiState = uiState,
        isReadOnly = isReadOnly,
        onTargetLanguageChange = viewModel::setTargetLanguage,
        onSourceLanguageChange = viewModel::setSourceLanguageOverride,
        onSwapLanguages = viewModel::swapLanguages,
        onRetry = viewModel::retry,
        onReplace = onReplace,
        onDismiss = onDismiss,
    )
}

@Composable
fun TranslatePopupContent(
    sourceText: String,
    sourceLanguageOverride: String?,
    targetLanguage: String,
    uiState: TranslationUiState,
    isReadOnly: Boolean,
    onTargetLanguageChange: (String) -> Unit,
    onSourceLanguageChange: (String?) -> Unit,
    onSwapLanguages: () -> Unit,
    onRetry: () -> Unit,
    onReplace: ((String) -> Unit)?,
    onDismiss: () -> Unit,
) {
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
            selectedLanguage = sourceLanguageOverride,
            allowAutoDetect = true,
            onLanguageSelected = onSourceLanguageChange,
            onDismissRequest = { isPickingSourceLanguage = false },
        )
    }

    if (isPickingTargetLanguage) {
        LanguagePickerDialog(
            title = stringResource(R.string.target_language),
            selectedLanguage = targetLanguage,
            allowAutoDetect = false,
            onLanguageSelected = { selected ->
                if (selected != null) onTargetLanguageChange(selected)
            },
            onDismissRequest = { isPickingTargetLanguage = false },
        )
    }

    Box(Modifier.fillMaxSize()) {
        // Scrim: tapping outside dismisses popup
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(interactionSource = null, indication = null, onClick = onDismiss),
        )
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(16.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
        ) {
            Column(Modifier.padding(start = 20.dp, top = 12.dp, end = 12.dp, bottom = 8.dp)) {
                // Language bar: Source [swap] Target
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val sourceLabel = when {
                        sourceLanguageOverride != null -> languageDisplayName(sourceLanguageOverride)
                        uiState is TranslationUiState.Success -> languageDisplayName(uiState.sourceLanguage)
                        uiState is TranslationUiState.Detecting -> stringResource(R.string.detecting_language)
                        else -> stringResource(R.string.auto_detect)
                    }

                    TextButton(onClick = { isPickingSourceLanguage = true }) {
                        Text(
                            text = sourceLabel,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(R.string.source_language),
                        )
                    }

                    IconButton(
                        onClick = onSwapLanguages,
                        enabled = uiState is TranslationUiState.Success || sourceLanguageOverride != null,
                    ) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = stringResource(R.string.swap_languages),
                        )
                    }

                    TextButton(onClick = { isPickingTargetLanguage = true }) {
                        Text(
                            text = languageDisplayName(targetLanguage),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(R.string.target_language),
                        )
                    }
                }

                Text(
                    text = sourceText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp),
                )

                HorizontalDivider(Modifier.padding(vertical = 12.dp))

                Box(
                    Modifier
                        .heightIn(min = 48.dp, max = 320.dp)
                        .padding(end = 8.dp),
                ) {
                    TranslationResult(
                        uiState = uiState,
                        onRetry = onRetry,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    val successState = uiState as? TranslationUiState.Success

                    if (successState != null) {
                        // Pronunciation / TTS button
                        IconButton(
                            onClick = {
                                val locale = Locale.forLanguageTag(targetLanguage)
                                tts?.language = locale
                                tts?.speak(
                                    successState.translatedText,
                                    TextToSpeech.QUEUE_FLUSH,
                                    null,
                                    "translation_tts",
                                )
                            },
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = stringResource(R.string.speak),
                            )
                        }

                        // Copy button
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService<ClipboardManager>()
                                clipboard?.setPrimaryClip(
                                    ClipData.newPlainText("translation", successState.translatedText),
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
                            Text(stringResource(R.string.copy))
                        }

                        // In-place replace button for ACTION_PROCESS_TEXT
                        if (!isReadOnly && onReplace != null) {
                            TextButton(onClick = { onReplace(successState.translatedText) }) {
                                Text(stringResource(R.string.replace))
                            }
                        }
                    }

                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslationResult(
    uiState: TranslationUiState,
    onRetry: () -> Unit,
) {
    when (uiState) {
        TranslationUiState.Idle -> {
            Text(
                text = stringResource(R.string.translating),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TranslationUiState.Detecting -> Progress(stringResource(R.string.detecting_language))
        TranslationUiState.Downloading -> Progress(stringResource(R.string.downloading_model))
        TranslationUiState.Translating -> Progress(stringResource(R.string.translating))
        is TranslationUiState.Error -> {
            Column {
                Text(
                    text = uiState.message.asString(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRetry) {
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
        is TranslationUiState.Success -> SelectionContainer {
            Text(
                text = uiState.translatedText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun Progress(message: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Preview
@Composable
private fun TranslatePopupPreview() {
    MyTranslatorTheme {
        TranslatePopupContent(
            sourceText = "Hello, how are you doing today?",
            sourceLanguageOverride = null,
            targetLanguage = TranslateLanguage.SPANISH,
            uiState = TranslationUiState.Success(
                sourceLanguage = TranslateLanguage.ENGLISH,
                targetLanguage = TranslateLanguage.SPANISH,
                translatedText = "Hola, ¿cómo estás hoy?",
            ),
            isReadOnly = false,
            onTargetLanguageChange = {},
            onSourceLanguageChange = {},
            onSwapLanguages = {},
            onRetry = {},
            onReplace = {},
            onDismiss = {},
        )
    }
}
