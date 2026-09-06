package com.kyronix.swadhyaa.data.repository

import android.content.Context
import com.kyronix.swadhyaa.data.remote.AiAgentService

/**
 * Bridges local search (SearchRepository) with the Gemini AI agent.
 *
 * Online mode:  local DB search → context → Gemini → Bengali answer
 * Offline mode: local DB search → formatted results (no AI)
 */
class AiAgentRepository(
    private val context: Context,
    private val searchRepo: SearchRepository
) {

    /**
     * Main entry point for the chat agent.
     *
     * @param question User's question (Bengali/Sanskrit/English — any)
     * @return [AgentAnswer] with the response text, source list, and mode used
     */
    suspend fun ask(question: String): AgentAnswer {
        // 1. Always search local DB first (works offline too)
        val hits = searchRepo.search(question, limit = 8)

        // 2. Format hits as readable context for the AI
        val contextText = formatContext(hits)

        return if (AiAgentService.isOnline(context)) {
            // ── Online: send to Gemini ────────────────────────────────
            val result = AiAgentService.ask(
                userQuestion = question,
                searchContext = contextText
            )
            result.fold(
                onSuccess = { answer ->
                    AgentAnswer(
                        text = answer,
                        sources = hits.map { it.label },
                        mode = AnswerMode.ONLINE_AI
                    )
                },
                onFailure = { error ->
                    // Gemini failed — fall back to offline display
                    AgentAnswer(
                        text = buildOfflineAnswer(hits, question),
                        sources = hits.map { it.label },
                        mode = AnswerMode.OFFLINE_FALLBACK,
                        errorMessage = "AI সংযোগ ব্যর্থ: ${error.message}"
                    )
                }
            )
        } else {
            // ── Offline: show search results directly ─────────────────
            AgentAnswer(
                text = buildOfflineAnswer(hits, question),
                sources = hits.map { it.label },
                mode = AnswerMode.OFFLINE_SEARCH
            )
        }
    }

    // ── Private helpers ───────────────────────────────────────────────

    /**
     * Converts SearchHit list into a readable string for Gemini context.
     * Each hit shows its label + Sanskrit/Bengali snippet.
     */
    private fun formatContext(hits: List<SearchHit>): String {
        if (hits.isEmpty()) return ""
        return hits.joinToString("\n\n") { hit ->
            val source = when (hit.kind) {
                "veda" -> "বেদ (Corpus ${hit.corpusId})"
                "ramayana" -> "রামায়ণ"
                else -> hit.kind
            }
            "[$source — ${hit.label}]\n${hit.snippet}"
        }
    }

    /**
     * Offline answer: plain formatted search results, no AI.
     * Shows top results or a "not found" message.
     */
    private fun buildOfflineAnswer(hits: List<SearchHit>, question: String): String {
        if (hits.isEmpty()) {
            return """
                «$question» বিষয়ে ডাউনলোড করা শাস্ত্রে সরাসরি কোনো ফলাফল পাওয়া যায়নি।
                
                • ইন্টারনেট সংযোগ চালু করলে AI সম্পূর্ণ উত্তর দিতে পারবে।
                • আরও শাস্ত্র ডাউনলোড করতে Library → Downloads-এ যান।
            """.trimIndent()
        }

        val sb = StringBuilder()
        sb.appendLine("প্রাসঙ্গিক অংশগুলো (ডাউনলোড করা শাস্ত্র থেকে):\n")
        hits.take(4).forEachIndexed { i, hit ->
            val source = when (hit.kind) {
                "veda" -> "বেদ"
                "ramayana" -> "রামায়ণ"
                else -> hit.kind
            }
            sb.appendLine("${i + 1}. $source — ${hit.label}")
            sb.appendLine("   ${hit.snippet.take(120)}…")
            sb.appendLine()
        }
        sb.append("(AI বিশ্লেষণের জন্য ইন্টারনেট সংযোগ চালু করুন।)")
        return sb.toString()
    }
}

// ── Data models ───────────────────────────────────────────────────────

enum class AnswerMode {
    ONLINE_AI,          // Gemini answered with DB context
    OFFLINE_SEARCH,     // No internet — showing DB results directly
    OFFLINE_FALLBACK    // Internet existed but Gemini call failed
}

data class AgentAnswer(
    val text: String,
    val sources: List<String> = emptyList(),
    val mode: AnswerMode,
    val errorMessage: String? = null   // non-null only for OFFLINE_FALLBACK
)
