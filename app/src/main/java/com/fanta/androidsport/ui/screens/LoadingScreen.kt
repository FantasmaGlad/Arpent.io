package com.fanta.androidsport.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoadingScreen() {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    val assetLoader = WebViewAssetLoader.Builder()
                        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                        .build()

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url
                            android.util.Log.d("ArpentWebView", "shouldInterceptRequest (new API): $url")
                            val response = url?.let { assetLoader.shouldInterceptRequest(it) }
                            android.util.Log.d("ArpentWebView", "Response for $url: ${if (response != null) "FOUND" else "NULL"}")
                            return response
                        }

                        @Deprecated("Deprecated in Java")
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            url: String?
                        ): WebResourceResponse? {
                            android.util.Log.d("ArpentWebView", "shouldInterceptRequest (old API): $url")
                            val response = url?.let { assetLoader.shouldInterceptRequest(android.net.Uri.parse(it)) }
                            android.util.Log.d("ArpentWebView", "Response for $url: ${if (response != null) "FOUND" else "NULL"}")
                            return response
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            android.util.Log.d("ArpentWebView", "onPageStarted: $url")
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            android.util.Log.d("ArpentWebView", "onPageFinished: $url")
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            android.util.Log.e("ArpentWebView", "onReceivedError for ${request?.url}: description=${error?.description}, errorCode=${error?.errorCode}")
                        }
                    }

                    setBackgroundColor(android.graphics.Color.TRANSPARENT)

                    settings.apply {
                        javaScriptEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        domStorageEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                    }

                    loadUrl("https://appassets.androidplatform.net/assets/loading.html")
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

