package com.kyronix.swadhyaa.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Gemini 2.0 Flash — free tier (1 500 requests/day, no billing needed).
 *
 * API key: get yours free at https://aistudio.google.com → "Get API key"
 * Replace GEMINI_API_KEY below with your actual key.
 *
 * Endpoint used:
 *   POST https://generativelanguage.googleapis.com/v1beta/models/
 *        gemini-2.0-flash:generateContent?key=<KEY>
 */
object AiAgentService {

    // ── ⚠️  API key ──────────────
    private const val GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY
    // ──────────────────────────────────────────────────────────────────

    private const val MODEL = "gemini-2.0-flash"
    private const val BASE_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    /**
     * Check network availability before making API call.
     */
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Ask Gemini a scripture question with local DB search results as context.
     *
     * @param userQuestion  The user's question in Bengali/Sanskrit/English.
     * @param searchContext Relevant verses/mantras fetched from local DB (as formatted text).
     * @return Result<String> — the AI's Bengali answer, or an error.
     */
    suspend fun ask(
        userQuestion: String,
        searchContext: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemInstruction = buildSystemPrompt()
            val userPrompt = buildUserPrompt(userQuestion, searchContext)

            val bodyJson = JSONObject().apply {
                // System instruction
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", systemInstruction))
                    })
                })
                // User message
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", userPrompt))
                        })
                    })
                })
                // Generation config
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)          // শাস্ত্রীয় বিষয়ে accurate থাকার জন্য কম
                    put("maxOutputTokens", 800)
                    put("topP", 0.8)
                })
            }

            val request = Request.Builder()
                .url("$BASE_URL?key=$GEMINI_API_KEY")
                .post(bodyJson.toString().toRequestBody(JSON_MEDIA))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response from Gemini"))

                if (!response.isSuccessful) {
                    val errMsg = tryParseGeminiError(body)
                    return@withContext Result.failure(Exception("Gemini error (${response.code}): $errMsg"))
                }

                val text = parseGeminiResponse(body)
                    ?: return@withContext Result.failure(Exception("Could not parse Gemini response"))

                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────

    private fun buildSystemPrompt(): String = """
        তুমি স্বাধ্যায় অ্যাপের শাস্ত্র-সহায়ক (Scripture Assistant)।
        তোমার কাজ: সনাতন হিন্দু ধর্মের শাস্ত্র সম্পর্কিত প্রশ্নের উত্তর বাংলায় দেওয়া।
        
        তুমি যে শাস্ত্রগুলো জানো: চার বেদ (ঋগ্বেদ, যজুর্বেদ, সামবেদ, অথর্ববেদ), 
        বাল্মীকি রামায়ণ এবং মহাভারত।
        
        নিয়ম:
        1. সবসময় বাংলায় উত্তর দাও।
        2. যদি Context-এ প্রাসঙ্গিক মন্ত্র/শ্লোক থাকে, সেগুলো উল্লেখ করো।
        3. শাস্ত্রের reference দাও — কোন গ্রন্থ, কোন কাণ্ড/পর্ব/মণ্ডল।
        4. যদি Context-এ উত্তর না থাকে, তোমার জ্ঞান থেকে সৎভাবে উত্তর দাও।
        5. অনিশ্চিত হলে স্পষ্ট বলো।
        6. উত্তর সংক্ষিপ্ত ও সহজবোধ্য রাখো — সর্বোচ্চ ৫-৬ বাক্য।
    """.trimIndent()

    private fun buildUserPrompt(question: String, context: String): String {
        return if (context.isBlank()) {
            """
            প্রশ্ন: $question
            
            (এই প্রশ্নের জন্য স্থানীয় DB-তে কোনো সরাসরি ফলাফল পাওয়া যায়নি। 
            তোমার সাধারণ শাস্ত্রজ্ঞান থেকে উত্তর দাও।)
            """.trimIndent()
        } else {
            """
            প্রশ্ন: $question
            
            প্রাসঙ্গিক শাস্ত্রীয় তথ্য (স্থানীয় DB থেকে):
            $context
            
            উপরের তথ্যের ভিত্তিতে বাংলায় উত্তর দাও।
            """.trimIndent()
        }
    }

    private fun parseGeminiResponse(body: String): String? = try {
        val json = JSONObject(body)
        json
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()
    } catch (_: Exception) { null }

    private fun tryParseGeminiError(body: String): String = try {
        JSONObject(body)
            .getJSONObject("error")
            .getString("message")
    } catch (_: Exception) { body.take(200) }
}
