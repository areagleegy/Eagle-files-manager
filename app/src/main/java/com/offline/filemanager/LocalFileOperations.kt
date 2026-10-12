package com.offline.filemanager

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** All operations are limited to user-selected Storage Access Framework document URIs. */
internal object LocalFileOperations {
    private const val MAX_ARCHIVE_ENTRIES = 10_000
    private const val MAX_EXTRACTED_BYTES = 1L * 1024L * 1024L * 1024L
    private const val BUFFER_SIZE = 64 * 1024

    fun createFolder(parent: DocumentFile, requestedName: String): DocumentFile {
        val name = availableName(parent, LocalFileRules.safeFileName(requestedName))
        return parent.createDirectory(name) ?: throw IOException("تعذر إنشاء المجلد")
    }

    fun rename(file: DocumentFile, requestedName: String): Boolean {
        val name = LocalFileRules.safeFileName(requestedName)
        if (name == file.name) return true
        if (file.parentFile?.findFile(name) != null) throw IOException("يوجد عنصر بالاسم نفسه")
        return file.renameTo(name)
    }

    fun deleteRecursively(file: DocumentFile): Boolean {
        if (file.isDirectory) {
            file.listFiles().forEach { child ->
                if (!deleteRecursively(child)) return false
            }
        }
        return file.delete()
    }

    /** Copies a file or directory and avoids overwriting an existing item. */
    fun copyInto(context: Context, source: DocumentFile, destination: DocumentFile): DocumentFile {
        if (destination.uri == source.uri || isWithin(source, destination)) {
            throw IOException("لا يمكن نسخ المجلد إلى نفسه أو إلى مجلد فرعي منه")
        }
        return copyNamed(context, source, destination, availableName(destination, LocalFileRules.safeFileName(source.name ?: "ملف")))
    }

    private fun copyNamed(context: Context, source: DocumentFile, destination: DocumentFile, targetName: String): DocumentFile {
        if (source.isDirectory) {
            val target = destination.createDirectory(targetName) ?: throw IOException("تعذر إنشاء مجلد النسخة")
            source.listFiles().forEach { child ->
                copyNamed(context, child, target, availableName(target, LocalFileRules.safeFileName(child.name ?: "ملف")))
            }
            return target
        }
        val target = destination.createFile(source.type ?: mimeForName(source.name.orEmpty()), targetName)
            ?: throw IOException("تعذر إنشاء ملف النسخة")
        try {
            val input = context.contentResolver.openInputStream(source.uri) ?: throw IOException("تعذر قراءة الملف المصدر")
            val output = context.contentResolver.openOutputStream(target.uri, "w") ?: throw IOException("تعذر كتابة النسخة")
            input.use { src -> output.use { dst -> src.copyTo(dst, BUFFER_SIZE) } }
            return target
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    /** Creates a ZIP by streaming each source instead of loading it into RAM. */
    fun createZip(context: Context, sources: List<DocumentFile>, destination: DocumentFile, requestedName: String): DocumentFile {
        if (sources.isEmpty()) throw IOException("اختر ملفًا واحدًا على الأقل")
        var name = LocalFileRules.safeFileName(requestedName, "أرشيف")
        if (!name.lowercase().endsWith(".zip")) name += ".zip"
        name = availableName(destination, name)
        val archive = destination.createFile("application/zip", name) ?: throw IOException("تعذر إنشاء ملف ZIP")
        try {
            val output = context.contentResolver.openOutputStream(archive.uri, "w")
                ?: throw IOException("تعذر فتح ملف الأرشيف للكتابة")
            output.use { raw ->
                ZipOutputStream(BufferedOutputStream(raw, BUFFER_SIZE)).use { zip ->
                    val written = mutableSetOf<String>()
                    var count = 0
                    sources.forEach { source ->
                        var entryName = LocalFileRules.safeFileName(source.name ?: "ملف")
                        if (written.contains(entryName)) {
                            val dot = entryName.lastIndexOf('.')
                            val stem = if (dot > 0) entryName.substring(0, dot) else entryName
                            val ext = if (dot > 0) entryName.substring(dot) else ""
                            var n = 2
                            do {
                                entryName = "$stem ($n)$ext"
                                n++
                            } while (written.contains(entryName))
                        }
                        addToZip(context, zip, source, entryName, written, 0) {
                            count++
                            if (count > MAX_ARCHIVE_ENTRIES) throw IOException("عدد عناصر الأرشيف أكبر من الحد المسموح")
                        }
                    }
                }
            }
            return archive
        } catch (error: Exception) {
            archive.delete()
            throw error
        }
    }

    private fun addToZip(
        context: Context, zip: ZipOutputStream, file: DocumentFile, path: String,
        written: MutableSet<String>, depth: Int, onEntry: () -> Unit
    ) {
        if (depth > 100) throw IOException("عمق المجلدات أكبر من الحد المسموح")
        if (!LocalFileRules.isSafeArchivePath(path)) throw IOException("اسم ملف غير صالح داخل الأرشيف")
        if (file.isDirectory) {
            val directoryPath = path.trimEnd('/') + "/"
            if (written.add(directoryPath)) {
                zip.putNextEntry(ZipEntry(directoryPath))
                zip.closeEntry()
                onEntry()
            }
            file.listFiles().forEach { child ->
                addToZip(context, zip, child, "$directoryPath${LocalFileRules.safeFileName(child.name ?: "ملف")}", written, depth + 1, onEntry)
            }
        } else if (written.add(path)) {
            zip.putNextEntry(ZipEntry(path))
            val input = context.contentResolver.openInputStream(file.uri) ?: throw IOException("تعذر قراءة ${file.name}")
            input.use { it.copyTo(zip, BUFFER_SIZE) }
            zip.closeEntry()
            onEntry()
        }
    }

    /** Rejects traversal paths, limits the entry count and expanded size, and never overwrites files. */
    fun extractZip(context: Context, archive: DocumentFile, destination: DocumentFile): Int {
        val raw = context.contentResolver.openInputStream(archive.uri) ?: throw IOException("تعذر فتح الأرشيف")
        var entryCount = 0
        var extractedBytes = 0L
        raw.use { input ->
            ZipInputStream(BufferedInputStream(input, BUFFER_SIZE)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    if (entryCount > MAX_ARCHIVE_ENTRIES) throw IOException("الأرشيف يحتوي على عناصر أكثر من الحد المسموح")
                    val original = entry.name
                    if (!LocalFileRules.isSafeArchivePath(original)) throw IOException("أوقف الاستخراج: يحتوي الأرشيف على مسار غير آمن")
                    val segments = original.replace('\\', '/').split('/').filter { it.isNotEmpty() }
                    if (segments.isEmpty() || segments.size > 100) throw IOException("مسار عنصر غير صالح في الأرشيف")
                    var parent = destination
                    for (segment in segments.dropLast(1)) {
                        val safeSegment = LocalFileRules.safeFileName(segment)
                        val existing = parent.findFile(safeSegment)
                        parent = when {
                            existing?.isDirectory == true -> existing
                            existing != null -> parent.createDirectory(availableName(parent, safeSegment))
                                ?: throw IOException("تعذر إنشاء مجلد للاستخراج")
                            else -> parent.createDirectory(safeSegment) ?: throw IOException("تعذر إنشاء مجلد للاستخراج")
                        }
                    }
                    val leaf = LocalFileRules.safeFileName(segments.last())
                    if (entry.isDirectory || original.endsWith('/')) {
                        if (parent.findFile(leaf)?.isDirectory != true) {
                            parent.createDirectory(availableName(parent, leaf)) ?: throw IOException("تعذر إنشاء مجلد مستخرج")
                        }
                    } else {
                        val finalName = availableName(parent, leaf)
                        val outFile = parent.createFile(mimeForName(finalName), finalName)
                            ?: throw IOException("تعذر إنشاء ملف مستخرج")
                        try {
                            val output = context.contentResolver.openOutputStream(outFile.uri, "w")
                                ?: throw IOException("تعذر الكتابة إلى ملف مستخرج")
                            output.use { sink ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                while (true) {
                                    val read = zip.read(buffer)
                                    if (read < 0) break
                                    extractedBytes += read
                                    if (extractedBytes > MAX_EXTRACTED_BYTES) throw IOException("تجاوز حجم الملفات المستخرجة الحد المسموح")
                                    sink.write(buffer, 0, read)
                                }
                            }
                        } catch (error: Exception) {
                            outFile.delete()
                            throw error
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
        return entryCount
    }

    fun listZipEntries(context: Context, archive: DocumentFile, maxEntries: Int = 500): List<String> {
        val entries = mutableListOf<String>()
        val input = context.contentResolver.openInputStream(archive.uri) ?: throw IOException("تعذر فتح الأرشيف")
        input.use { raw ->
            ZipInputStream(BufferedInputStream(raw, BUFFER_SIZE)).use { zip ->
                while (entries.size < maxEntries) {
                    val entry = zip.nextEntry ?: break
                    entries += (if (entry.isDirectory) "📁 " else "📄 ") + entry.name
                    zip.closeEntry()
                }
            }
        }
        return entries
    }

    private fun isWithin(source: DocumentFile, possibleChild: DocumentFile): Boolean {
        var current: DocumentFile? = possibleChild
        repeat(100) {
            val item = current ?: return false
            if (item.uri == source.uri) return true
            current = item.parentFile
        }
        return false
    }

    private fun availableName(parent: DocumentFile, requested: String): String {
        val safe = LocalFileRules.safeFileName(requested)
        if (parent.findFile(safe) == null) return safe
        val dot = safe.lastIndexOf('.')
        val stem = if (dot > 0) safe.substring(0, dot) else safe
        val extension = if (dot > 0) safe.substring(dot) else ""
        var index = 2
        while (index < Int.MAX_VALUE) {
            val candidate = "$stem ($index)$extension"
            if (parent.findFile(candidate) == null) return candidate
            index++
        }
        throw IOException("تعذر إيجاد اسم متاح")
    }

    fun mimeForName(name: String): String = when (LocalFileRules.extensionOf(name)) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        "avif" -> "image/avif"
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "ogg", "opus" -> "audio/ogg"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "zip" -> "application/zip"
        "txt", "log", "md", "csv", "json", "xml", "html", "htm", "kt", "java", "js", "css", "yaml", "yml", "toml", "ini", "properties", "sh", "gradle" -> "text/plain"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "rtf" -> "application/rtf"
        "odt" -> "application/vnd.oasis.opendocument.text"
        "epub" -> "application/epub+zip"
        else -> "application/octet-stream"
    }
}
