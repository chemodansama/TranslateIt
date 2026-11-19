package com.silentpeak.mytranslator

import android.app.Application
import com.silentpeak.mytranslator.data.TranslationRepository
import com.silentpeak.mytranslator.data.TranslationStep
import com.silentpeak.mytranslator.translate.TranslateViewModel
import com.silentpeak.mytranslator.translate.TranslationUiState
import com.silentpeak.mytranslator.translate.languageDisplayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class TranslateViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeTranslationRepository
    private lateinit var viewModel: TranslateViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeTranslationRepository()
        viewModel = TranslateViewModel(Application(), repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isIdle() = runTest {
        assertEquals(TranslationUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun emptyOrBlankText_emitsError() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.setText("")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is TranslationUiState.Error)

        viewModel.setText("   ")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is TranslationUiState.Error)
        collectJob.cancel()
    }

    @Test
    fun validText_emitsSuccess() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.setText("Hola mundo")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is TranslationUiState.Success)
        val success = state as TranslationUiState.Success
        assertEquals("es", success.sourceLanguage)
        assertEquals("en", success.targetLanguage)
        assertEquals("Hello world", success.translatedText)

        collectJob.cancel()
    }

    @Test
    fun translationFailure_andRetry() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        repository.shouldFail = true
        viewModel.setText("Bonjour")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is TranslationUiState.Error)

        repository.shouldFail = false
        repository.translationResult = "Hello"
        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is TranslationUiState.Success)
        assertEquals("Hello", (state as TranslationUiState.Success).translatedText)

        collectJob.cancel()
    }

    @Test
    fun swapLanguages_swapsSourceAndTarget() = runTest {
        val collectJob = launch(testDispatcher) { viewModel.uiState.collect {} }

        viewModel.setText("Hola mundo")
        advanceUntilIdle()

        assertEquals("es", (viewModel.uiState.value as TranslationUiState.Success).sourceLanguage)
        assertEquals("en", viewModel.targetLanguage.value)

        viewModel.swapLanguages()
        advanceUntilIdle()

        assertEquals("en", viewModel.sourceLanguageOverride.value)
        assertEquals("es", viewModel.targetLanguage.value)
        assertEquals("Hello world", viewModel.sourceText.value)

        collectJob.cancel()
    }

    @Test
    fun languageDisplayName_returnsReadableNames() {
        assertEquals("English", languageDisplayName("en"))
        assertEquals("Spanish", languageDisplayName("es"))
        assertEquals("French", languageDisplayName("fr"))
    }
}

private class FakeTranslationRepository(
    initialTargetLanguage: String = "en",
    var detectedLanguage: String? = "es",
    var translationResult: String = "Hello world",
    var shouldFail: Boolean = false,
) : TranslationRepository {
    private val _targetLanguage = MutableStateFlow(initialTargetLanguage)
    override val targetLanguage: Flow<String> = _targetLanguage.asStateFlow()

    override suspend fun setTargetLanguage(language: String) {
        _targetLanguage.value = language
    }

    override suspend fun identifyLanguage(text: String): String? = detectedLanguage

    override fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): Flow<TranslationStep> = flow {
        if (shouldFail) {
            throw IOException("Network error")
        }
        emit(TranslationStep.Downloading)
        emit(TranslationStep.Translating)
        emit(TranslationStep.Success(sourceLanguage, translationResult))
    }

    override suspend fun isModelDownloaded(language: String): Boolean = true
    override suspend fun getDownloadedModels(): Set<String> = setOf("en", "es")
    override suspend fun downloadModel(language: String) {}
    override suspend fun deleteModel(language: String) {}
    override fun defaultTargetLanguage(): String = "en"
}
