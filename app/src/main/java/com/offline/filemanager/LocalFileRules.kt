package com.offline.filemanager

/** Deterministic filename and archive-path rules, independent of Android storage permissions. */
internal object LocalFileRules {
    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif")
    private val videoExtensions = setOf("mp4", "m4v", "3gp", "webm", "mkv", "mov", "ts", "avi")
    private val audioExtensions = setOf("mp3", "m4a", "aac", "ogg", "wav", "flac", "opus", "3ga", "amr", "mid", "midi")
    private val archiveExtensions = setOf("zip")
    private val textExtensions = setOf("txt", "md", "markdown", "json", "csv", "xml", "html", "htm", "log", "ini", "yaml", "yml", "properties", "kt", "java", "js", "css", "sh", "gradle", "toml")
    private val documentExtensions = setOf("pdf", "doc", "docx", "odt", "rtf", "ppt", "pptx", "xls", "xlsx", "epub")

    fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()
    fun isImage(name: String): Boolean = extensionOf(name) in imageExtensions
    fun isVideo(name: String): Boolean = extensionOf(name) in videoExtensions
    fun isAudio(name: String): Boolean = extensionOf(name) in audioExtensions
    fun isZipArchive(name: String): Boolean = extensionOf(name) in archiveExtensions
    fun isPdf(name: String): Boolean = extensionOf(name) == "pdf"
    fun isTextDocument(name: String): Boolean = extensionOf(name) in textExtensions
    fun isDocument(name: String): Boolean = isPdf(name) || isTextDocument(name) || extensionOf(name) in documentExtensions

    /** Reject absolute paths, drive paths, traversal segments, and ambiguous repeated separators. */
    fun isSafeArchivePath(path: String): Boolean {
        if (path.isBlank() || path.startsWith('/') || path.startsWith('\\')) return false
        if (Regex("^[A-Za-z]:").containsMatchIn(path)) return false
        val normalized = path.replace('\\', '/')
        // Permit one trailing slash for directory entries, but never empty interior segments
        // or multiple trailing separators.
        if (normalized.contains("//")) return false
        val segments = normalized.split('/')
        if (segments.any { it == "." || it == ".." }) return false
        if (segments.dropLastWhile { it.isEmpty() }.any { it.isEmpty() }) return false
        return segments.any { it.isNotEmpty() }
    }

    fun safeFileName(value: String, fallback: String = "ملف"): String {
        val cleaned = value.trim()
            .replace('/', '_')
            .replace('\\', '_')
            .replace(Regex("[\\u0000-\\u001F]"), "_")
            .trim('.', ' ')
            .take(120)
        return cleaned.ifBlank { fallback }.let { if (it == "." || it == "..") fallback else it }
    }
}
