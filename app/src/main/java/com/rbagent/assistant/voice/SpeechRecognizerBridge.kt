package com.rbagent.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

internal class SpeechRecognizerBridge(private val context: Context) {

    companion object { private const val TAG = "SpeechRecognizerBridge" }

    private var recognizer: SpeechRecognizer? = null
    private var busy = false

    fun feedUtterance(onResult: (String) -> Unit) {
        if (busy) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) { Log.w(TAG, "unavailable"); return }
        busy = true
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr
        val listener = object : RecognitionListener {
            private val collected = StringBuilder()
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { Log.d(TAG, "onError code=$error"); finish() }
            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.forEach { collected.append(it).append(" ") }
                val text = collected.toString().trim(); if (text.isNotEmpty()) onResult(text); finish()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.forEach { collected.append(it).append(" ") }
                val text = collected.toString().trim(); if (text.isNotEmpty()) onResult(text)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
            private fun finish() { runCatching { sr.destroy() }; if (recognizer === sr) recognizer = null; busy = false }
        }
        sr.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("en", "IN").toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 600L)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        try { sr.startListening(intent) }
        catch (e: Exception) { Log.e(TAG, "startListening failed", e); busy = false; runCatching { sr.destroy() } }
    }

    fun destroy() { runCatching { recognizer?.destroy() }; recognizer = null; busy = false }
}
