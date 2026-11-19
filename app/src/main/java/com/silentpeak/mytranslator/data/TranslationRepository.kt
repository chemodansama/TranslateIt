package com.silentpeak.mytranslator.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Locale

private val Context.translatorDataStore by preferencesDataStore(name = "translator")
private val TARGET_LANGUAGE_KEY = stringPreferencesKey("target_language")

private const val UNDETERMINED_LANGUAGE = "und"

sealed interface TranslationStep {
    data object Downloading : TranslationStep
    data object Translating : TranslationStep
    data class Success(val sourceLanguage: String, val translatedText: String) : TranslationStep
}

interface TranslationRepository {
    val targetLanguage: Flow<String>
    suspend fun setTargetLanguage(language: String)
    suspend fun identifyLanguage(text: String): String?
    fun translate(text: String, sourceLanguage: String, targetLanguage: String): Flow<TranslationStep>
    suspend fun isModelDownloaded(language: String): Boolean
    suspend fun getDownloadedModels(): Set<String>
    suspend fun downloadModel(language: String)
    suspend fun deleteModel(language: String)
    fun defaultTargetLanguage(): String
}

class MlKitTranslationRepository(context: Context) : TranslationRepository {

    private val dataStore = context.translatorDataStore
    private val modelManager = RemoteModelManager.getInstance()

    override val targetLanguage: Flow<String> = dataStore.data
        .map { it[TARGET_LANGUAGE_KEY] ?: defaultTargetLanguage() }
        .distinctUntilChanged()

    override suspend fun setTargetLanguage(language: String) {
        dataStore.edit { it[TARGET_LANGUAGE_KEY] = language }
    }

    override suspend fun identifyLanguage(text: String): String? {
        val identifier = LanguageIdentification.getClient()
        return identifier.use { client ->
            val tag = client.identifyLanguage(text).await()
            if (tag == UNDETERMINED_LANGUAGE) null else tag
        }
    }

    override fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): Flow<TranslationStep> = flow {
        val source = TranslateLanguage.fromLanguageTag(sourceLanguage)
            ?: throw IllegalArgumentException("Unsupported source language: $sourceLanguage")
        val target = TranslateLanguage.fromLanguageTag(targetLanguage)
            ?: throw IllegalArgumentException("Unsupported target language: $targetLanguage")

        if (source == target) {
            emit(TranslationStep.Success(source, text))
            return@flow
        }

        val needsDownload = listOf(source, target).any { lang ->
            !isModelDownloaded(lang)
        }

        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build()
        )
        try {
            if (needsDownload) {
                emit(TranslationStep.Downloading)
                translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            }
            emit(TranslationStep.Translating)
            val translated = translator.translate(text).await()
            emit(TranslationStep.Success(source, translated))
        } finally {
            translator.close()
        }
    }

    override suspend fun isModelDownloaded(language: String): Boolean {
        val model = TranslateRemoteModel.Builder(language).build()
        return modelManager.isModelDownloaded(model).await()
    }

    override suspend fun getDownloadedModels(): Set<String> {
        val models = modelManager.getDownloadedModels(TranslateRemoteModel::class.java).await()
        return models.map { it.language }.toSet()
    }

    override suspend fun downloadModel(language: String) {
        val model = TranslateRemoteModel.Builder(language).build()
        val conditions = DownloadConditions.Builder().build()
        modelManager.download(model, conditions).await()
    }

    override suspend fun deleteModel(language: String) {
        val model = TranslateRemoteModel.Builder(language).build()
        modelManager.deleteDownloadedModel(model).await()
    }

    override fun defaultTargetLanguage(): String =
        TranslateLanguage.fromLanguageTag(Locale.getDefault().toLanguageTag().substringBefore('-'))
            ?: TranslateLanguage.ENGLISH
}
