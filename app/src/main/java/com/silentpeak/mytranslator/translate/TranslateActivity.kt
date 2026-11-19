package com.silentpeak.mytranslator.translate

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.silentpeak.mytranslator.ui.theme.MyTranslatorTheme

/**
 * Receives text from the system share sheet (or the text selection menu) and shows its
 * translation in a popup over the calling app. The window is translucent, so the calling
 * app stays visible and is back in front as soon as the popup is dismissed.
 */
class TranslateActivity : ComponentActivity() {

    private val viewModel: TranslateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            viewModel.setText(intent.sharedText())
        }
        val isReadOnly = intent.isProcessTextReadOnly()
        setContent {
            MyTranslatorTheme {
                TranslatePopup(
                    viewModel = viewModel,
                    isReadOnly = isReadOnly,
                    onReplace = { replacementText ->
                        val resultIntent = Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, replacementText)
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    },
                    onDismiss = ::finish,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.setText(intent.sharedText())
    }
}

private fun Intent.sharedText(): String? = when (action) {
    Intent.ACTION_SEND -> getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
    Intent.ACTION_PROCESS_TEXT -> getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
    else -> null
}

private fun Intent.isProcessTextReadOnly(): Boolean =
    action != Intent.ACTION_PROCESS_TEXT ||
        getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
