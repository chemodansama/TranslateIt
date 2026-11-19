package com.silentpeak.mytranslator.translate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.nl.translate.TranslateLanguage
import com.silentpeak.mytranslator.R
import com.silentpeak.mytranslator.data.MlKitTranslationRepository
import com.silentpeak.mytranslator.data.TranslationRepository
import com.silentpeak.mytranslator.data.TranslationStep
import com.silentpeak.mytranslator.util.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

sealed interface TranslationUiState {
    data object Idle : TranslationUiState
    data object Detecting : TranslationUiState
    data object Downloading : TranslationUiState
    data object Translating : TranslationUiState
    data class Success(
        val sourceLanguage: String,
        val targetLanguage: String,
        val translatedText: String,
    ) : TranslationUiState
    data class Error(val message: UiText) : TranslationUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class TranslateViewModel(
    application: Application,
    private val repository: TranslationRepository,
) : AndroidViewModel(application) {

    // Primary constructor for Android ViewModel provider
    constructor(application: Application) : this(
        application,
        MlKitTranslationRepository(application),
    )

    private val _sourceText = MutableStateFlow<String?>(null)
    val sourceText: StateFlow<String?> = _sourceText.asStateFlow()

    private val _sourceLanguageOverride = MutableStateFlow<String?>(null)
    val sourceLanguageOverride: StateFlow<String?> = _sourceLanguageOverride.asStateFlow()

    private val _retryTrigger = MutableStateFlow(0)

    val targetLanguage: StateFlow<String> = repository.targetLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.defaultTargetLanguage())

    val uiState: StateFlow<TranslationUiState> = combine(
        _sourceText,
        targetLanguage,
        _sourceLanguageOverride,
        _retryTrigger,
    ) { text, target, override, _ ->
        TranslateParams(text, target, override)
    }.transformLatest { params ->
        val text = params.text
        if (text == null) {
            emit(TranslationUiState.Idle)
        } else if (text.isBlank()) {
            emit(TranslationUiState.Error(UiText.StringResource(R.string.no_text)))
        } else {
            emitAll(performTranslation(text, params.target, params.sourceOverride))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TranslationUiState.Idle)

    fun setText(text: String?) {
        _sourceText.value = text.orEmpty()
    }

    fun setTargetLanguage(language: String) {
        viewModelScope.launch {
            repository.setTargetLanguage(language)
        }
    }

    fun setSourceLanguageOverride(language: String?) {
        _sourceLanguageOverride.value = language
    }

    fun swapLanguages() {
        val currentState = uiState.value
        val currentTarget = targetLanguage.value
        val currentSource = if (currentState is TranslationUiState.Success) {
            currentState.sourceLanguage
        } else {
            _sourceLanguageOverride.value
        }

        if (currentSource != null && currentSource != currentTarget) {
            _sourceLanguageOverride.value = currentTarget
            setTargetLanguage(currentSource)
            if (currentState is TranslationUiState.Success) {
                _sourceText.value = currentState.translatedText
            }
        }
    }

    fun retry() {
        _retryTrigger.update { it + 1 }
    }

    private fun performTranslation(
        text: String,
        target: String,
        sourceOverride: String?,
    ): Flow<TranslationUiState> = flow {
        val source = if (sourceOverride != null) {
            sourceOverride
        } else {
            emit(TranslationUiState.Detecting)
            val languageTag = repository.identifyLanguage(text)
            if (languageTag == null) {
                emit(TranslationUiState.Error(UiText.StringResource(R.string.unknown_language)))
                return@flow
            }
            val validated = TranslateLanguage.fromLanguageTag(languageTag)
            if (validated == null) {
                emit(
                    TranslationUiState.Error(
                        UiText.StringResource(
                            R.string.unsupported_language,
                            languageDisplayName(languageTag),
                        ),
                    ),
                )
                return@flow
            }
            validated
        }

        repository.translate(text, source, target).collect { step ->
            when (step) {
                TranslationStep.Downloading -> emit(TranslationUiState.Downloading)
                TranslationStep.Translating -> emit(TranslationUiState.Translating)
                is TranslationStep.Success -> emit(
                    TranslationUiState.Success(
                        sourceLanguage = step.sourceLanguage,
                        targetLanguage = target,
                        translatedText = step.translatedText,
                    ),
                )
            }
        }
    }.catch { e ->
        emit(
            TranslationUiState.Error(
                UiText.StringResource(
                    R.string.translation_failed,
                    e.localizedMessage ?: e.toString(),
                ),
            ),
        )
    }

    private data class TranslateParams(
        val text: String?,
        val target: String,
        val sourceOverride: String?,
    )
}

fun languageDisplayName(languageTag: String): String =
    Locale.forLanguageTag(languageTag).displayLanguage
        .ifEmpty { languageTag }
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
