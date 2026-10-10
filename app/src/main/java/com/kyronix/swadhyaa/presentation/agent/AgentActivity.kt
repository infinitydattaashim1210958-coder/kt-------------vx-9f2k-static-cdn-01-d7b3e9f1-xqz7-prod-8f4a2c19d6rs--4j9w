package com.kyronix.swadhyaa.presentation.agent

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.ViewGroup
import android.view.Window
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import com.kyronix.swadhyaa.data.agent.AgentBridge
import com.kyronix.swadhyaa.data.agent.GroundedRetriever
import com.kyronix.swadhyaa.ui.theme.AppColors

/**
 * শাস্ত্র-সহায়ক — Puter.js-powered, database-grounded scripture agent.
 *
 *  - The chat UI + agent pipeline are web assets (assets/agent/*) served over https://appassets.androidplatform.net
 *    through WebViewAssetLoader. Puter identifies an app by its web origin, and file:// has none, so a real
 *    https origin is required for sign-in to work.
 *  - AI calls (puter.ai.chat) run in that page and are billed to the signed-in USER's Puter account
 *    (Puter "User-Pays" model) — the developer pays nothing and ships no API key.
 *  - Evidence comes from the on-device databases through [AgentBridge]. Nothing is downloaded automatically.
 *  - puter.auth.signIn() opens a popup window; WebView needs onCreateWindow to host it (below).
 */
class AgentActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var popupDialog: Dialog? = null
    private var popupView: WebView? = null

    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .setDomain(AGENT_HOST)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply { setBackgroundColor(AppColors.bg) }
        webView = WebView(this).apply {
            setBackgroundColor(AppColors.bg)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        root.addView(webView)
        setContentView(root)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true                 // Puter keeps its session token in localStorage
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)          // sign-in popup
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        webView.addJavascriptInterface(AgentBridge(GroundedRetriever(applicationContext)), "SwadhyayAgent")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)

            // Keep the agent page on its own origin; links elsewhere open in the system browser.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.host == AGENT_HOST) return false
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, u)) }
                return true
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean =
                openPopup(resultMsg)
            override fun onCloseWindow(window: WebView) { closePopup() }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (popupDialog != null) closePopup() else finish()
            }
        })

        if (savedInstanceState == null) webView.loadUrl(pageUrl()) else webView.restoreState(savedInstanceState)
    }

    /** Hosts Puter's sign-in popup in a dialog; the popup talks back to the opener via postMessage. */
    @SuppressLint("SetJavaScriptEnabled")
    private fun openPopup(resultMsg: Message): Boolean {
        closePopup()
        val popup = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onCloseWindow(window: WebView) { closePopup() }
            }
        }
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(popup)
            setOnCancelListener { closePopup() }
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        popupView = popup
        popupDialog = dialog
        val transport = resultMsg.obj as WebView.WebViewTransport
        transport.webView = popup
        resultMsg.sendToTarget()
        dialog.show()
        return true
    }

    private fun closePopup() {
        popupDialog?.let { runCatching { it.dismiss() } }
        popupView?.let { runCatching { it.stopLoading(); it.destroy() } }
        popupDialog = null
        popupView = null
    }

    private fun pageUrl(): String {
        fun hex(c: Int) = String.format("#%06X", 0xFFFFFF and c)
        return Uri.Builder().scheme("https").authority(AGENT_HOST).path("/assets/agent/index.html")
            .appendQueryParameter("gold", hex(AppColors.gold))
            .appendQueryParameter("gold2", hex(AppColors.goldBright))
            .build().toString()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        closePopup()
        webView.removeJavascriptInterface("SwadhyayAgent")
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        /**
         * Puter treats the page's origin as "your app". The default AssetLoader domain is shared by every
         * app that uses it; change this to a hostname you own (it is never fetched — requests are
         * intercepted locally) if you want Puter to see this app under its own identity.
         */
        private const val AGENT_HOST = "appassets.androidplatform.net"
    }
}
