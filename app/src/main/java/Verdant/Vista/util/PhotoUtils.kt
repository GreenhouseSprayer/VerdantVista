package Verdant.Vista.util

import android.content.Context
import android.graphics.*
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import java.io.ByteArrayOutputStream

object PhotoUtils {
    private const val TAG = "PhotoUtils"
    private const val SIZE_ORIGINAL = "original"
    private const val SIZE_LARGE = "large"
    private const val SIZE_MEDIUM = "medium"
    private val PHOTO_SIZES = listOf("square", "small", "medium", "large", "thumb", "original")

    /**
     * Helper to convert bitmap to byte array for DB storage
     */
    fun bitmapToByteArray(bitmap: Bitmap?): ByteArray? {
        if (bitmap == null) return null
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return stream.toByteArray()
    }

    /**
     * Helper to convert byte array back to bitmap
     */
    fun byteArrayToBitmap(data: ByteArray?): Bitmap? {
        if (data == null) return null
        return BitmapFactory.decodeByteArray(data, 0, data.size)
    }

    /**
     * Ensures the URL uses HTTPS to prevent cleartext blocks on mobile networks.
     */
    fun ensureHttps(url: String?): String? {
        if (url == null) return null
        return if (url.startsWith("http://")) url.replace("http://", "https://") else url
    }

    /**
     * Transforms an iNaturalist photo URL to its absolute highest resolution version (Original).
     */
    fun getHighResUrl(url: String?): String? {
        if (url == null) return null
        val httpsUrl = ensureHttps(url)!!
        for (size in PHOTO_SIZES) {
            val pattern = "/$size."
            if (httpsUrl.contains(pattern)) {
                return httpsUrl.replace(pattern, "/$SIZE_ORIGINAL.")
            }
        }
        return httpsUrl
    }

    /**
     * Returns a large-resolution URL (1024px) which is not cropped.
     */
    fun getLargeResUrl(url: String?): String? {
        if (url == null) return null
        val httpsUrl = ensureHttps(url)!!
        for (size in PHOTO_SIZES) {
            val pattern = "/$size."
            if (httpsUrl.contains(pattern)) {
                return httpsUrl.replace(pattern, "/$SIZE_LARGE.")
            }
        }
        return httpsUrl
    }

    /**
     * Returns a high-resolution URL suitable for widgets. 
     * Uses SIZE_LARGE (1024px) as a base to ensure maximum sharpness after the crop.
     */
    fun getWidgetResUrl(url: String?): String? {
        if (url == null) return null
        val httpsUrl = ensureHttps(url)!!

        for (size in PHOTO_SIZES) {
            val pattern = "/$size."
            if (httpsUrl.contains(pattern)) {
                return httpsUrl.replace(pattern, "/$SIZE_LARGE.")
            }
        }
        return httpsUrl
    }

    /**
     * Returns a tiny 100px thumbnail URL for 'Blur-Up' loading.
     */
    fun getThumbUrl(url: String?): String? {
        if (url == null) return null
        val httpsUrl = ensureHttps(url)!!
        // "square" is a tiny 75x75 variant, perfect for instant blur-ups
        for (size in PHOTO_SIZES) {
            val pattern = "/$size."
            if (httpsUrl.contains(pattern)) {
                return httpsUrl.replace(pattern, "/square.")
            }
        }
        return httpsUrl
    }

    /**
     * Helper to clean up the iNaturalist attribution string.
     * Example: "(c) jane_doe, some rights reserved" -> "jane_doe"
     */
    fun formatAttribution(raw: String?): String {
        if (raw == null) return "Unknown Photographer"
        return raw.replace("(c)", "")
            .replace("©", "")
            .split(",")
            .firstOrNull()?.trim() ?: "Nature Scout"
    }

    /**
     * Shared logic to fetch a bitmap with fallback for high/medium res URLs.
     */
    suspend fun fetchBitmap(context: Context, url: String, size: Int = 600): Bitmap? {
        val widgetUrl = getWidgetResUrl(url) ?: return null
        
        return try {
            val loader = context.imageLoader
            val request = ImageRequest.Builder(context)
                .data(widgetUrl)
                .size(size, size)
                .precision(Precision.AUTOMATIC)
                .allowHardware(false)
                .build()
            
            val result = loader.execute(request)
            if (result is SuccessResult) {
                result.drawable.toBitmap().copy(Bitmap.Config.RGB_565, false)
            } else {
                Log.w(TAG, "fetchBitmap: Initial fetch failed, trying fallback for $url")
                // Fallback: The original URL provided
                val fallbackUrl = ensureHttps(url) ?: url
                val fallbackRequest = ImageRequest.Builder(context)
                    .data(fallbackUrl)
                    .size(size, size)
                    .precision(Precision.AUTOMATIC)
                    .allowHardware(false)
                    .build()
                val fallbackResult = loader.execute(fallbackRequest)
                if (fallbackResult is SuccessResult) {
                    fallbackResult.drawable.toBitmap().copy(Bitmap.Config.RGB_565, false)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchBitmap: Exception for $url", e)
            null
        }
    }

    /**
     * Master method to create a high-impact, gap-free widget bitmap.
     * Uses a 'Smart Fit' strategy: scales the image to fill the width (600px)
     * and lets the height be natural, avoiding the harsh top/bottom crops
     * of standard center-cropping.
     */
    fun createCinematicWidgetBitmap(source: Bitmap?): Bitmap? {
        if (source == null) return null

        val targetWidth = 600
        val sourceW = source.width.toFloat()
        val sourceH = source.height.toFloat()
        
        // Scale to fit width, let height be natural
        val scale = targetWidth / sourceW
        val fgW = targetWidth
        val fgH = (sourceH * scale).toInt()
        
        return Bitmap.createScaledBitmap(source, fgW, fgH, true)
    }
}
