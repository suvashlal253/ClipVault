package com.clipvault.android.domain.classifier

import com.clipvault.android.data.local.entity.ClipCategory
import java.util.regex.Pattern
import kotlin.math.log2

data class ClassificationResult(
    val category: ClipCategory,
    val tags: List<String>,
    val isAutoVaultCandidate: Boolean = false,
    val extractedMetadata: Map<String, String> = emptyMap()
)

object ClipClassifier {

    private val URL_PATTERN = Pattern.compile(
        "^(https?://)?([a-zA-Z0-9-]+\\\\.)+[a-zA-Z]{2,}(:\\\\d+)?(/[^\\\\s]*)?$",
        Pattern.CASE_INSENSITIVE
    )

    private val EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\\\.[a-zA-Z]{2,}$"
    )

    private val PHONE_PATTERN = Pattern.compile(
        "^(\\\\+?\\\\d{1,4}[-.\\\\s]?)?(\\\\(\\\\d{2,4}\\\\)[-.\\\\s]?)?\\\\d{3,4}[-.\\\\s]?\\\\d{3,4}$"
    )

    private val HEX_COLOR_PATTERN = Pattern.compile(
        "^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$"
    )

    private val OTP_PATTERN = Pattern.compile(
        "\\\\b(?:\\\\d{4,8}|[A-Z0-9]{6,8})\\\\b"
    )

    private val CODE_KEYWORDS = listOf(
        "fun ", "val ", "var ", "class ", "interface ", "import ", "package ",
        "function ", "const ", "let ", "def ", "public static void",
        "SELECT ", "INSERT INTO ", "UPDATE ", "DELETE FROM ", "{"
    )

    fun classify(text: String): ClassificationResult {
        val trimmed = text.trim()
        val tags = mutableListOf<String>()

        // 1. Color Hex Check (#FFF, #3B82F6)
        if (HEX_COLOR_PATTERN.matcher(trimmed).matches()) {
            tags.addAll(listOf("Color", "Hex"))
            return ClassificationResult(
                category = ClipCategory.COLOR,
                tags = tags,
                extractedMetadata = mapOf("hex" to trimmed)
            )
        }

        // 2. Email Address
        if (EMAIL_PATTERN.matcher(trimmed).matches()) {
            tags.addAll(listOf("Email", "Contact"))
            return ClassificationResult(
                category = ClipCategory.EMAIL,
                tags = tags,
                extractedMetadata = mapOf("email" to trimmed)
            )
        }

        // 3. Phone Number
        if (PHONE_PATTERN.matcher(trimmed).matches() && trimmed.filter { it.isDigit() }.length >= 7) {
            tags.addAll(listOf("Phone", "Call"))
            return ClassificationResult(
                category = ClipCategory.PHONE,
                tags = tags,
                extractedMetadata = mapOf("phone" to trimmed)
            )
        }

        // 4. Web URL
        if (URL_PATTERN.matcher(trimmed).matches() || trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            val url = if (trimmed.startsWith("http")) trimmed else "https://$trimmed"
            tags.addAll(listOf("Link", "Web"))
            return ClassificationResult(
                category = ClipCategory.URL,
                tags = tags,
                extractedMetadata = mapOf("url" to url)
            )
        }

        // 5. High-Entropy Secret / API Key / Password Detection
        val entropy = calculateShannonEntropy(trimmed)
        val isLikelyKey = (entropy > 4.2 && trimmed.length in 12..120) ||
                trimmed.startsWith("ghp_") ||
                trimmed.startsWith("sk-") ||
                trimmed.startsWith("AIzaSy") ||
                trimmed.startsWith("Bearer ")

        if (isLikelyKey) {
            tags.addAll(listOf("Sensitive", "Vault Candidate"))
            return ClassificationResult(
                category = ClipCategory.SENSITIVE,
                tags = tags,
                isAutoVaultCandidate = true,
                extractedMetadata = mapOf("entropy" to String.format("%.2f", entropy))
            )
        }

        // 6. One-Time Password / Verification Code
        if (trimmed.length in 4..8 && trimmed.all { it.isDigit() }) {
            tags.addAll(listOf("OTP", "Code"))
            return ClassificationResult(
                category = ClipCategory.OTP,
                tags = tags,
                extractedMetadata = mapOf("otp" to trimmed)
            )
        }

        val lower = trimmed.lowercase()
        if (lower.contains("code") || lower.contains("otp") || lower.contains("verification") || lower.contains("token")) {
            val matcher = OTP_PATTERN.matcher(trimmed)
            if (matcher.find()) {
                val otp = matcher.group()
                tags.addAll(listOf("OTP", "Verification"))
                return ClassificationResult(
                    category = ClipCategory.OTP,
                    tags = tags,
                    extractedMetadata = mapOf("otp" to otp)
                )
            }
        }

        // 7. Source Code / JSON / SQL
        if (trimmed.startsWith("{") && trimmed.endsWith("}") || trimmed.startsWith("[") && trimmed.endsWith("]")) {
            tags.addAll(listOf("Code", "JSON"))
            return ClassificationResult(category = ClipCategory.CODE, tags = tags)
        }

        if (CODE_KEYWORDS.any { trimmed.contains(it) } && (trimmed.contains("{") || trimmed.contains(";"))) {
            tags.addAll(listOf("Code", "Developer"))
            return ClassificationResult(category = ClipCategory.CODE, tags = tags)
        }

        // 8. Markdown
        if (trimmed.startsWith("# ") || trimmed.contains("```") || trimmed.startsWith("- [ ]")) {
            tags.addAll(listOf("Markdown", "Notes"))
            return ClassificationResult(category = ClipCategory.MARKDOWN, tags = tags)
        }




        // Default Plain Text
        tags.add("Text")
        return ClassificationResult(category = ClipCategory.TEXT, tags = tags)
    }

    /**
     * Shannon Entropy algorithm to measure randomness of strings.
     * High entropy indicates cryptographic keys, tokens, or strong passwords.
     */
    private fun calculateShannonEntropy(str: String): Double {
        if (str.length < 8) return 0.0
        val frequencies = HashMap<Char, Int>()
        for (ch in str) {
            frequencies[ch] = (frequencies[ch] ?: 0) + 1
        }
        var entropy = 0.0
        val len = str.length.toDouble()
        for (count in frequencies.values) {
            val p = count / len
            entropy -= p * (log2(p))
        }
        return entropy
    }
}
