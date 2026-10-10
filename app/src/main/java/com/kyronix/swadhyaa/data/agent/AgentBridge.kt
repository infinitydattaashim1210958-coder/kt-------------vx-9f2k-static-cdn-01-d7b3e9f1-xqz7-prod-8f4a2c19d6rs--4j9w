package com.kyronix.swadhyaa.data.agent

import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * The ONLY native surface exposed to the agent page (window.SwadhyayAgent).
 * Read-only access to public scripture text; no files, no network, no user data, no secrets.
 * JavascriptInterface methods run on a WebView background thread, so blocking DB work is fine here.
 */
class AgentBridge(private val retriever: GroundedRetriever) {

    @JavascriptInterface
    fun search(requestJson: String): String = try {
        retriever.searchJson(requestJson)
    } catch (e: Exception) {
        JSONObject().put("error", e.message ?: e.javaClass.simpleName).toString()
    }

    @JavascriptInterface
    fun coverage(): String = try {
        retriever.coverageJson()
    } catch (e: Exception) {
        JSONObject().put("error", e.message ?: e.javaClass.simpleName).toString()
    }
}
