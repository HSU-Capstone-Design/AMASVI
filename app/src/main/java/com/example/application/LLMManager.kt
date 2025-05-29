package com.example.application

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.application.BuildConfig
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody


/**
 * LLMManager
 *
 * - fetchKeyword: 사용자 발화에서 단일 키워드만 뽑아냄
 * - fetchMatchOcr: OCR 결과 리스트+키워드를 받아 {index, text} JSON으로 응답받아 파싱
 *
 * 사용법:
 *   val llm = LLMManager()
 *   llm.fetchKeyword("카페 어디어디") { kw -> /* UI 쓰레드에서 kw 사용 */ }
 *   llm.fetchMatchOcr(listOf("Starbox","Exit"), "카페") { idx, txt -> /* idx, txt */ }
 */
class LLMManager {

    private val client = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val apiKey = BuildConfig.OPENAI_API_KEY

    /** 1) 사용자 발화 → 키워드 하나만 반환 */
    fun fetchKeyword(input: String, callback: (keyword: String) -> Unit) {
        val system   = "You are an assistant. Extract exactly one keyword from the user's input."
        val user     = "Extract a single keyword from:\n\"$input\""
        val messages = listOf(
            mapOf("role" to "system", "content" to system),
            mapOf("role" to "user",   "content" to user)
        )


        sendChatRequest(messages) { content ->
            // content 예시: "카페"
            val kw = content?.trim() ?: ""
            Log.d("Keyword", "keyword=${kw}")
            mainHandler.post { callback(kw) }
        }
    }

    /** 2) OCR 리스트 + 키워드 → { index: Int, text: String? } JSON 파싱 */
    fun fetchMatchOcr(
        ocrTexts: List<String>,
        keyword: String,
        callback: (index: Int, text: String?) -> Unit
    ) {
        val system = """
            You are an assistant. Given a list of OCR-detected texts and a keyword, 
            return a JSON object with:
              - "index": the 0-based index of the matching text (or -1 if none)
              - "text": the corrected text if it seems to be an OCR error (e.g., fix 'Starbox' to 'Starbucks') (or null)
        """.trimIndent()

        val user = """
            OCR Texts: ${ocrTexts.joinToString(", ", "[", "]")}
            Keyword: $keyword
            Respond **only** with valid JSON, e.g. {"index":0,"text":"Starbucks"} or {"index":-1,"text":null}
        """.trimIndent()

        val messages = listOf(
            mapOf("role" to "system",  "content" to system),
            mapOf("role" to "user",    "content" to user)
        )

        sendChatRequest(messages) { content ->
            if (content == null) {
                mainHandler.post { callback(-1, null) }
                return@sendChatRequest
            }
            try {
                val json = JSONObject(content)
                val idx  = json.getInt("index")
                val txt = if (json.isNull("text")) null else json.getString("text")
                mainHandler.post { callback(idx, txt) }
            } catch (e: Exception) {
                mainHandler.post { callback(-1, null) }
            }
        }
    }

    /** 내부: OpenAI ChatCompletion 호출 */
    private fun sendChatRequest(
        messages: List<Map<String, String>>,
        result: (content: String?) -> Unit
    ) {
        // request body
        val payload = JSONObject().apply {
            put("model", "gpt-3.5-turbo")
            put("temperature", 0.0)
            put("max_tokens", 100)
            put("messages", JSONArray().apply {
                for (msg in messages) put(JSONObject(msg))
            })
        }

        val body = RequestBody.create(
            "application/json".toMediaTypeOrNull(),
            payload.toString()
        )

        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                result(null)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {

                        result(null)
                        return
                    }
                    val bodyString = it.body?.string() ?: ""

                    try {
                        val root    = JSONObject(bodyString)
                        val choice  = root
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                        val content = choice.getString("content")

                        result(content)
                    } catch (e: Exception) {

                        result(null)
                    }
                }
            }
        })
    }
}
