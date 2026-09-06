package com.kyronix.swadhyaa.presentation.agent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kyronix.swadhyaa.data.local.CoreDatabase
import com.kyronix.swadhyaa.data.local.RamayanaCoreDatabase
import com.kyronix.swadhyaa.data.repository.AgentAnswer
import com.kyronix.swadhyaa.data.repository.AiAgentRepository
import com.kyronix.swadhyaa.data.repository.AnswerMode
import com.kyronix.swadhyaa.data.repository.SearchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A single message bubble in the chat. */
data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val isUser: Boolean,
    val sources: List<String> = emptyList(),
    val mode: AnswerMode? = null,
    val errorMessage: String? = null
)

data class AgentUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isOnline: Boolean = true
)

class AgentViewModel(app: Application) : AndroidViewModel(app) {

    private val searchRepo = SearchRepository(
        CoreDatabase.getInstance(app),
        RamayanaCoreDatabase.getInstance(app)
    )
    private val agentRepo = AiAgentRepository(app, searchRepo)

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    init {
        // Greeting message shown on first open
        addBotMessage(
            "নমস্কার 🙏 আমি স্বাধ্যায় শাস্ত্র-সহায়ক।\n\n" +
            "বেদ, রামায়ণ বা মহাভারত সম্পর্কে যেকোনো প্রশ্ন করুন।\n" +
            "বাংলা, সংস্কৃত বা ইংরেজিতে প্রশ্ন করতে পারেন।"
        )
    }

    fun sendMessage(question: String) {
        val trimmed = question.trim()
        if (trimmed.isEmpty() || _uiState.value.isLoading) return

        // Add user message immediately
        val userMsg = ChatMessage(text = trimmed, isUser = true)
        updateMessages(userMsg)

        // Show loading
        _uiState.value = _uiState.value.copy(isLoading = true)

        viewModelScope.launch {
            val answer: AgentAnswer = agentRepo.ask(trimmed)

            val botMsg = ChatMessage(
                text = answer.text,
                isUser = false,
                sources = answer.sources,
                mode = answer.mode,
                errorMessage = answer.errorMessage
            )
            updateMessages(botMsg)
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun clearHistory() {
        _uiState.value = AgentUiState()
        addBotMessage("কথোপকথন মুছে ফেলা হয়েছে। নতুন প্রশ্ন করুন।")
    }

    // ── Private ───────────────────────────────────────────────────────

    private fun addBotMessage(text: String) {
        updateMessages(ChatMessage(text = text, isUser = false))
    }

    private fun updateMessages(msg: ChatMessage) {
        val current = _uiState.value.messages.toMutableList()
        current.add(msg)
        _uiState.value = _uiState.value.copy(messages = current)
    }
}
