package com.example.application

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.io.File
import java.util.*

class TTsManager(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isSpeaking = false

    private val pendingUtterances = mutableSetOf<String>()
    private var pendingInstruction: (() -> Unit)? = null

    private val directionMap = mapOf(
        "좌측" to "left.mp3",
        "우측" to "right.mp3",
        "전방" to "forward"
    )

    private val distanceMap = (1..5).associate { m -> "${m}미터" to "${m}m.mp3" }

    private val locationMap = mutableMapOf(
        "버스정류장" to "bus_stop.mp3",
        "카페" to "cafe.mp3",
        "스타벅스" to "starbucks.mp3",
        "이디야" to "ediya.mp3",
        "투썸플레이스" to "twosome_place.mp3",
        "빽다방" to "paikdabang.mp3",
        "할리스커피" to "hollis_coffee.mp3",
        "커피빈" to "coffeebean.mp3",
        "폴바셋" to "paulbassett.mp3",
        "카페베네" to "cafebene.mp3",
        "탐앤탐스" to "tomntoms.mp3",
        "메가커피" to "mega_coffee.mp3"
    )

    private val obstacleMap = mapOf("장애물" to "obstacle.mp3")

    private val fixedEndFile = "fixed_end.mp3"            // "이 있습니다."
    private val cautionWithObjectFile = "obstacle_caution.mp3"  // "장애물에 유의하여주십시오"

    private var onInitListener: (() -> Unit)? = null

    fun setOnInitListener(listener: () -> Unit) {
        onInitListener = listener
    }

    fun init() {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        isInitialized = status == TextToSpeech.SUCCESS
        if (isInitialized) {
            tts?.language = Locale.KOREAN
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {}
                override fun onDone(utteranceId: String) {
                    pendingUtterances.remove(utteranceId)
                    if (pendingUtterances.isEmpty()) {
                        pendingInstruction?.invoke()
                        pendingInstruction = null
                    }
                }

                override fun onError(utteranceId: String) {
                    pendingUtterances.remove(utteranceId)
                }
            })
            cacheAllKeywords()
            onInitListener?.invoke()
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }

    private fun getCategoryDir(category: String): File {
        val dir = File(context.cacheDir, category)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun synthesizeToFile(text: String, fileName: String, category: String) {
        if (!isInitialized) return
        val outFile = File(getCategoryDir(category), fileName)
        if (!outFile.exists()) {
            val utteranceId = "$category-$fileName"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            tts?.synthesizeToFile(text, params, outFile, utteranceId)
            pendingUtterances.add(utteranceId)
        }
    }

    fun playInstruction(
        rawDirection: String,
        rawDistance: Float,
        item: String,
        speed: Float = 1.5f,
        onComplete: () -> Unit = {}
    ) {
        if (!isInitialized || isSpeaking) return
        isSpeaking = true

        val dirKey = when (rawDirection) {
            "L" -> "좌측"
            "R" -> "우측"
            "F" -> "전방"
            else -> null
        }
        val distKey = "${rawDistance.coerceIn(1f, 5f).toInt()}미터"

        val files = mutableListOf<File>().apply {
            dirKey?.let {
                directionMap[it]?.let { fn ->
                    add(File(getCategoryDir("direction"), fn))
                }
            }

            distanceMap[distKey]?.let {
                add(File(getCategoryDir("distance"), it))
            }

            val isObs = obstacleMap.containsKey(item)
            val fn = if (isObs) obstacleMap[item] else {
                locationMap.getOrPut(item) {
                    val newFile = "$item.mp3"
                    synthesizeToFile(item, newFile, "location")
                    newFile
                }
            }

            fn?.let {
                add(File(getCategoryDir(if (isObs) "obstacle" else "location"), it))
            }

            add(File(getCategoryDir("fixed"), fixedEndFile))

            if (isObs) {
                add(File(getCategoryDir("fixed"), cautionWithObjectFile)) // 장애물에 유의하여주십시오
            }
        }

        val missing = files.filterNot { it.exists() }
        if (missing.isNotEmpty()) {
            pendingInstruction = {
                playSequence(files.filter { it.exists() }, speed) {
                    isSpeaking = false
                    onComplete()
                }
            }
            isSpeaking = false
            onComplete()
            return
        }

        playSequence(files, speed) {
            isSpeaking = false
            onComplete()
        }
    }

    private fun playSequence(
        files: List<File>,
        speed: Float,
        onAllDone: () -> Unit
    ) {
        if (files.isEmpty()) {
            onAllDone()
            return
        }

        val file = files.first()
        Log.d("TTS", "재생 시작: ${file.name}")

        val player = MediaPlayer()
        val handler = Handler(Looper.getMainLooper())

        val timeoutRunnable = Runnable {
            Log.w("TTS", "타임아웃 → 다음으로 넘어감: ${file.name}")
            player.release()
            playSequence(files.drop(1), speed, onAllDone)
        }

        try {
            player.setDataSource(file.absolutePath)

            player.setOnCompletionListener {
                Log.d("TTS", "재생 완료: ${file.name}")
                handler.removeCallbacks(timeoutRunnable)
                it.release()
                playSequence(files.drop(1), speed, onAllDone)
            }

            player.setOnPreparedListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val params = PlaybackParams().setSpeed(speed)
                    it.playbackParams = params
                }
                it.start()

                // 2.5초 타임아웃 보조
                handler.postDelayed(timeoutRunnable, 2500)
            }

            player.prepareAsync()
        } catch (e: Exception) {
            Log.e("TTS", "재생 중 오류 발생: ${file.name}", e)
            handler.removeCallbacks(timeoutRunnable)
            player.release()
            playSequence(files.drop(1), speed, onAllDone)
        }
    }

    private fun cacheAllKeywords() {
        synthesizeToFile("이 있습니다.", fixedEndFile, "fixed")
        synthesizeToFile("장애물에 유의하여주십시오.", cautionWithObjectFile, "fixed") // 합성 추가

        directionMap.forEach { (text, file) ->
            synthesizeToFile(text, file, "direction")
        }

        distanceMap.forEach { (text, file) ->
            synthesizeToFile("${text}에", file, "distance")  // 예: "1미터에"
        }

        locationMap.forEach { (text, file) ->
            synthesizeToFile(text, file, "location")
        }

        obstacleMap.forEach { (text, file) ->
            synthesizeToFile(text, file, "obstacle")
        }
    }
}
