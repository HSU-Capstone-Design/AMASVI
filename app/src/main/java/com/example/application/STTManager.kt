//STTManager.kt
package com.example.application

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class STTManager(
    private val context: Context,
    private val onTextResult: (String) -> Unit,
    private val onError: (Int) -> Unit = {}
) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null

    /** 기본 세팅 */
    fun initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(this)
    }

    /** 녹음(음성인식) 시작 */
    fun startSpeechRecognition(
        language: String = "ko-KR",
        partialResults: Boolean = true
    ) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, partialResults)
        }
        speechRecognizer?.startListening(intent)
    }

    /** 녹음(음성인식) 종료 */
    fun stopSpeechRecognition() {
        speechRecognizer?.stopListening()
    }

    /** 리소스 해제 */
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    // ─── RecognitionListener 콜백 ─────────────────────────────────

    override fun onResults(results: Bundle) {
        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.let { onTextResult(it) }
    }

    override fun onError(error: Int) {
        onError(error)
    }

    // 빈 구현
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
