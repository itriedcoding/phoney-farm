package com.phonefarm.app.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import com.phonefarm.app.data.FarmNode
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class ScreenShot(
    val nodeId: String,
    val path: String,
    val at: Long,
    val width: Int,
    val height: Int,
)

/**
 * Renders a node's real page off-screen with a software-layer WebView and writes the pixels to a
 * PNG. The WebView is destroyed immediately, so a capture costs memory only for the duration of
 * one frame.
 */
object ScreenCapture {

    private const val WIDTH = 420
    private const val HEIGHT = 720
    private const val SETTLE_MS = 2_500L
    private const val TIMEOUT_MS = 20_000L

    fun directory(context: Context): File = File(context.cacheDir, "screens").apply { mkdirs() }

    fun fileFor(context: Context, nodeId: String): File = File(directory(context), "$nodeId.png")

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun capture(context: Context, node: FarmNode): ScreenShot? =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { cont ->
                val handler = Handler(Looper.getMainLooper())
                var finished = false
                var web: WebView? = null

                fun finish(shot: ScreenShot?) {
                    if (finished) return
                    finished = true
                    handler.removeCallbacksAndMessages(null)
                    runCatching {
                        web?.stopLoading()
                        web?.destroy()
                    }
                    web = null
                    if (cont.isActive) cont.resume(shot)
                }

                try {
                    val view = WebView(context)
                    web = view
                    view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    view.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        loadsImagesAutomatically = true
                        blockNetworkImage = false
                        mediaPlaybackRequiresUserGesture = true
                        userAgentString = if (node.desktopUa) HttpRunner.UA_DESKTOP else HttpRunner.UA_MOBILE
                    }
                    view.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(v: WebView?, url: String?) {
                            handler.postDelayed({
                                val shot = runCatching { render(context, node, view) }.getOrNull()
                                finish(shot)
                            }, SETTLE_MS)
                        }
                    }
                    view.loadUrl(node.url)
                    handler.postDelayed({ finish(null) }, TIMEOUT_MS)
                } catch (t: Throwable) {
                    finish(null)
                }

                cont.invokeOnCancellation {
                    handler.post {
                        runCatching { web?.destroy() }
                        web = null
                    }
                }
            }
        }

    private fun render(context: Context, node: FarmNode, view: WebView): ScreenShot? {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, WIDTH, HEIGHT)

        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.BLACK)
            view.draw(canvas)
            val file = fileFor(context, node.id)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 92, out)
            }
            if (!file.exists() || file.length() < 512L) return null
            return ScreenShot(node.id, file.absolutePath, System.currentTimeMillis(), WIDTH, HEIGHT)
        } finally {
            bitmap.recycle()
        }
    }
}
