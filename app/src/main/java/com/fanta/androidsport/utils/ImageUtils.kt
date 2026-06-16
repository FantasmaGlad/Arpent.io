package com.fanta.androidsport.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

fun uriToBase64(context: android.content.Context, uri: android.net.Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        
        if (originalBitmap == null) return null
        
        // Resize to max 160x160 for database storage efficiency
        val size = 160
        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, size, size, true)
        
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        val bytes = outputStream.toByteArray()
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (e: Exception) {
        android.util.Log.e("Arpent", "Failed to convert image to Base64", e)
        null
    }
}

fun base64ToImageBitmap(base64Str: String?): ImageBitmap? {
    if (base64Str == null || base64Str.isEmpty()) return null
    return try {
        val cleanStr = if (base64Str.startsWith("data:image")) {
            val commaIdx = base64Str.indexOf(",")
            if (commaIdx != -1) base64Str.substring(commaIdx + 1) else base64Str
        } else base64Str
        val bytes = Base64.decode(cleanStr, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        bitmap?.let { it.asImageBitmap() }
    } catch (e: Exception) {
        null
    }
}
