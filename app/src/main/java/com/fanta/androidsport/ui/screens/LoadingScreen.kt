package com.fanta.androidsport.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    webViewClient = WebViewClient()
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    
                    settings.apply {
                        javaScriptEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        domStorageEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                    }
                    
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
                                    background-color: transparent;
                                    display: flex;
                                    justify-content: center;
                                    align-items: center;
                                }
                                embed {
                                    width: 100%;
                                    height: 100%;
                                    border: none;
                                    object-fit: contain;
                                }
                            </style>
                        </head>
                        <body>
                            <embed src="file:///android_asset/Chargement.svg" type="image/svg+xml" />
                        </body>
                        </html>
                    """.trimIndent()
                    
                    loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "utf-8", null)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

