package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object LocalImageUtils {
    
    /**
     * Copies a Uri selected from the visual picker into the app's internal private storage,
     * ensuring access credentials never expire and are always loadable across app launches.
     */
    fun copyUriToInternalStorage(context: Context, uri: Uri, prefix: String): String? {
        return try {
            val contentResolver = context.contentResolver
            val inputStream: InputStream = contentResolver.openInputStream(uri) ?: return null
            val outputDir = File(context.filesDir, "project_images")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            val fileName = "${prefix}_${UUID.randomUUID()}.jpg"
            val outputFile = File(outputDir, fileName)
            val outputStream = FileOutputStream(outputFile)
            
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            outputFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Copies a Bitmap to internal storage and returns the absolute file path.
     */
    fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap, prefix: String): String? {
        return try {
            val outputDir = File(context.filesDir, "project_images")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            val fileName = "${prefix}_temp_${UUID.randomUUID()}.jpg"
            val outputFile = File(outputDir, fileName)
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            outputFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
