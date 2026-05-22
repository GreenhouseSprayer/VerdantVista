package Verdant.Vista.util

import android.util.Log

object WikiUtils {
    /**
     * Cleans up Wikipedia extracts by removing HTML tags and CSS junk.
     * Preserves section headers (== Section ==) for the parser.
     */
    fun cleanExtract(text: String?): String {
        if (text.isNullOrBlank()) {
            Log.d("WikiUtils", "Input text is null or blank")
            return ""
        }

        Log.d("WikiUtils", "Original text length: ${text.length}")

        // 1. Remove Wikipedia CSS junk
        val cssRegex = Regex("""\.mw-parser-output[\s\S]*?\{[\s\S]*?\}""", RegexOption.MULTILINE)
        var cleaned = text.replace(cssRegex, "")

        // 2. Remove HTML tags
        cleaned = cleaned.replace(Regex("<[^>]*>"), "")

        // 3. Normalize section headers to ensure they are on their own lines.
        val headerRegex = Regex("""(==+[^=]+==+)""")
        cleaned = cleaned.replace(headerRegex) { match ->
            "\n\n${match.value.trim()}\n\n"
        }

        // 4. Final cleanup of excessive whitespace
        val result = cleaned.replace(Regex("""\n{3,}"""), "\n\n").trim()
        
        Log.d("WikiUtils", "Cleaned text length: ${result.length}")
        return result
    }
}
