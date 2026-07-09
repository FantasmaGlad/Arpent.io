package com.fanta.androidsport.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoadingScreen() {
    val context = LocalContext.current

    // Read the SVG content once from assets and cache it in memory.
    // Inlining it directly into the HTML avoids the secondary embed request
    // that fails on cold-start and prevents the CSS animation from playing.
    val svgContent = remember {
        try {
            context.assets.open("Chargement.svg").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            android.util.Log.e("Arpent", "Failed to read Chargement.svg from assets", e)
            null
        }
    }

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
                    webViewClient = WebViewClient()
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)

                    settings.apply {
                        javaScriptEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        domStorageEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                    }

                    val inlineSvg = svgContent ?: ""
                    val htmlContent = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                            <style>
                                html, body {
                                    margin: 0;
                                    padding: 0;
                                    width: 100%;
                                    height: 100%;
                                    overflow: hidden;
                                    background-color: white;
                                    display: flex;
                                    justify-content: center;
                                    align-items: center;
                                }
                                svg {
                                    width: 100%;
                                    height: 100%;
                                    object-fit: contain;
                                }
                            </style>
                        </head>
                        <body>
                            $inlineSvg
                        </body>
                        </html>
                    """.trimIndent()

                    loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

