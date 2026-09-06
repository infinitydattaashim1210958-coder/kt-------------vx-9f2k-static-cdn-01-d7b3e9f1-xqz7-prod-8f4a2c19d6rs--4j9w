package com.kyronix.swadhyaa.presentation.agent

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kyronix.swadhyaa.data.repository.AnswerMode
import com.kyronix.swadhyaa.ui.theme.AppColors
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * AI scripture agent — chat UI.
 *
 * Launch from ShellActivity (or anywhere):
 *   startActivity(Intent(this, AgentActivity::class.java))
 *
 * Add to AndroidManifest.xml:
 *   <activity android:name=".presentation.agent.AgentActivity"
 *             android:exported="false"
 *             android:windowSoftInputMode="adjustResize"
 *             android:theme="@style/Theme.Swadhyay" />
 */
class AgentActivity : AppCompatActivity() {

    private val vm: AgentViewModel by viewModels()
    private val density by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * density).toInt()

    private lateinit var messagesLayout: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var inputField: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var loadingBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = buildUi()
        setContentView(root)

        lifecycleScope.launch {
            vm.uiState.collectLatest { state ->
                renderMessages(state.messages)
                loadingBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                sendButton.isEnabled = !state.isLoading
                sendButton.alpha = if (state.isLoading) 0.4f else 1f
            }
        }
    }

    // ── UI Builder ────────────────────────────────────────────────────

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppColors.bg)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Toolbar
        root.addView(buildToolbar())

        // Loading indicator (below toolbar, above messages)
        loadingBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(3)
            )
            progressDrawable = null
            indeterminateDrawable?.setTint(AppColors.saffron)
            visibility = View.GONE
        }
        root.addView(loadingBar)

        // Messages scroll area
        messagesLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            addView(messagesLayout)
            isSmoothScrollingEnabled = true
        }
        root.addView(scrollView)

        // Divider
        root.addView(View(this).apply {
            setBackgroundColor(AppColors.border)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            )
        })

        // Input bar
        root.addView(buildInputBar())

        return root
    }

    private fun buildToolbar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(AppColors.surface)
            setPadding(dp(4), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
            )
        }

        // Back button
        val back = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            setColorFilter(AppColors.ivory)
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            contentDescription = "ফিরে যান"
            setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }
        bar.addView(back)

        // Title
        val title = TextView(this).apply {
            text = "শাস্ত্র-সহায়ক ✦"
            textSize = 17f
            setTextColor(AppColors.gold)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(8), 0, 0, 0)
        }
        bar.addView(title)

        // Clear history button
        val clear = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            setColorFilter(AppColors.muted)
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            contentDescription = "ইতিহাস মুছুন"
            setOnClickListener { vm.clearHistory() }
        }
        bar.addView(clear)

        return bar
    }

    private fun buildInputBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setBackgroundColor(AppColors.surface)
            setPadding(dp(12), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        inputField = EditText(this).apply {
            hint = "প্রশ্ন করুন…"
            setHintTextColor(AppColors.muted)
            setTextColor(AppColors.ivory)
            textSize = 15f
            background = roundedBg(AppColors.elevated, AppColors.border, dp(20))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 4
            imeOptions = EditorInfo.IME_ACTION_SEND
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    doSend(); true
                } else false
            }
        }
        bar.addView(inputField)

        bar.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), 1)
        })

        sendButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_send)
            setColorFilter(AppColors.saffron)
            background = roundedBg(AppColors.elevated, AppColors.border, dp(22))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            contentDescription = "পাঠান"
            setOnClickListener { doSend() }
        }
        bar.addView(sendButton)

        return bar
    }

    // ── Rendering ─────────────────────────────────────────────────────

    private fun renderMessages(messages: List<ChatMessage>) {
        messagesLayout.removeAllViews()
        messages.forEach { msg ->
            messagesLayout.addView(buildBubble(msg))
            messagesLayout.addView(spacer(dp(8)))
        }
        // Auto-scroll to bottom
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun buildBubble(msg: ChatMessage): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (msg.isUser) Gravity.END else Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Bubble wrapper to align left/right
        val bubbleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (msg.isUser) Gravity.END else Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val bubbleBg = if (msg.isUser) AppColors.elevated else AppColors.surface
        val bubbleBorder = if (msg.isUser) AppColors.gold else AppColors.border

        val bubble = TextView(this).apply {
            text = msg.text
            textSize = 14.5f
            setTextColor(if (msg.isUser) AppColors.ivory else AppColors.ivory)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundedBg(bubbleBg, bubbleBorder, dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                // Max 85% of screen width
                val maxW = (resources.displayMetrics.widthPixels * 0.85).toInt()
                width = ViewGroup.LayoutParams.WRAP_CONTENT
                this.weight = 0f
            }
        }
        bubbleRow.addView(bubble)
        container.addView(bubbleRow)

        // Mode badge (for bot messages)
        if (!msg.isUser && msg.mode != null) {
            val badge = buildModeBadge(msg.mode, msg.errorMessage)
            container.addView(badge)
        }

        // Sources (for bot messages with search hits)
        if (!msg.isUser && msg.sources.isNotEmpty()) {
            container.addView(buildSourcesView(msg.sources))
        }

        return container
    }

    private fun buildModeBadge(mode: AnswerMode, error: String?): View {
        val (label, color) = when (mode) {
            AnswerMode.ONLINE_AI ->
                "✓ AI উত্তর (Gemini)" to AppColors.saffron
            AnswerMode.OFFLINE_SEARCH ->
                "📴 অফলাইন — স্থানীয় অনুসন্ধান" to AppColors.muted
            AnswerMode.OFFLINE_FALLBACK ->
                "⚠ AI ব্যর্থ — স্থানীয় ফলাফল" to AppColors.vermilion
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            addView(TextView(this@AgentActivity).apply {
                text = label
                textSize = 10.5f
                setTextColor(color)
                setPadding(dp(4), dp(2), 0, 0)
            })
            if (error != null) {
                addView(TextView(this@AgentActivity).apply {
                    text = error
                    textSize = 10f
                    setTextColor(AppColors.vermilion)
                    setPadding(dp(4), 0, 0, 0)
                })
            }
        }
    }

    private fun buildSourcesView(sources: List<String>): View {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), 0, 0)
        }
        val header = TextView(this).apply {
            text = "📚 সূত্র:"
            textSize = 10.5f
            setTextColor(AppColors.muted)
        }
        wrap.addView(header)
        sources.take(4).forEach { src ->
            wrap.addView(TextView(this).apply {
                text = "  • $src"
                textSize = 10.5f
                setTextColor(AppColors.muted)
            })
        }
        return wrap
    }

    // ── Actions ───────────────────────────────────────────────────────

    private fun doSend() {
        val text = inputField.text.toString().trim()
        if (text.isEmpty()) return
        inputField.setText("")
        hideKeyboard()
        vm.sendMessage(text)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(inputField.windowToken, 0)
    }

    // ── Drawing helpers ───────────────────────────────────────────────

    private fun roundedBg(fillColor: Int, strokeColor: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius.toFloat()
            setColor(fillColor)
            setStroke(dp(1), strokeColor)
        }

    private fun spacer(height: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, height
        )
    }
}
