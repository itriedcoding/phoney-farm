package com.phonefarm.app.engine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.phonefarm.app.data.FarmNode
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Full-session node runner. Each cycle spins a WebView, loads the target, runs the node script
 * and tears the WebView down again so idle memory returns to the OS.
 */
object WebRunner {
    private const val PAGE_TIMEOUT_MS = 25_000L
    private const val DWELL_MS = 1_500L

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun render(context: Context, node: FarmNode): RunResult = suspendCancellableCoroutine { cont ->
        val handler = Handler(Looper.getMainLooper())
        var finished = false
        var status = 0
        var web: WebView? = null

        fun finish(result: RunResult) {
            if (finished) return
            finished = true
            handler.removeCallbacksAndMessages(null)
            runCatching {
                web?.stopLoading()
                web?.destroy()
            }
            web = null
            if (cont.isActive) cont.resume(result)
        }

        try {
            val view = WebView(context)
            web = view
            view.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadsImagesAutomatically = node.images
                blockNetworkImage = !node.images
                mediaPlaybackRequiresUserGesture = true
                cacheMode = WebSettings.LOAD_DEFAULT
                setSupportZoom(false)
                builtInZoomControls = false
                userAgentString = if (node.desktopUa) HttpRunner.UA_DESKTOP else HttpRunner.UA_MOBILE
            }
            view.isSoundEffectsEnabled = false
            view.webViewClient = object : WebViewClient() {
                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?,
                ) {
                    if (request?.isForMainFrame == true) {
                        status = errorResponse?.statusCode ?: status
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (node.script.isNotBlank()) {
                        runCatching { view?.evaluateJavascript(node.script, null) }
                    }
                    handler.postDelayed({ finish(RunResult(true, status, 0, null)) }, DWELL_MS)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    if (request?.isForMainFrame == true) {
                        finish(RunResult(false, status, 0, error?.description?.toString() ?: "page error"))
                    }
                }
            }
            view.loadUrl(node.url)
            handler.postDelayed({
                finish(RunResult(false, status, 0, "page timeout"))
            }, PAGE_TIMEOUT_MS)
        } catch (t: Throwable) {
            finish(RunResult(false, 0, 0, t.message ?: "webview unavailable"))
        }

        cont.invokeOnCancellation {
            handler.post {
                runCatching {
                    web?.stopLoading()
                    web?.destroy()
                }
                web = null
            }
        }
    }
}
