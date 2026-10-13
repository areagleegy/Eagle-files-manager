package com.offline.filemanager

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

private enum class FileFilter(val label: String) {
    ALL("الكل"), IMAGES("الصور"), VIDEO("الفيديو"), AUDIO("الصوت"), DOCUMENTS("المستندات"), ARCHIVES("الأرشيف");
    fun matches(file: DocumentFile): Boolean {
        if (file.isDirectory || this == ALL) return true
        val name = file.name.orEmpty()
        return when (this) {
            ALL -> true
            IMAGES -> LocalFileRules.isImage(name)
            VIDEO -> LocalFileRules.isVideo(name)
            AUDIO -> LocalFileRules.isAudio(name)
            DOCUMENTS -> LocalFileRules.isDocument(name)
            ARCHIVES -> LocalFileRules.isZipArchive(name)
        }
    }
}
private enum class DialogType { CREATE_FOLDER, RENAME, DELETE, CREATE_ZIP }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = androidx.compose.ui.graphics.Color(0xFF176B87),
                    onPrimary = androidx.compose.ui.graphics.Color.White,
                    secondary = androidx.compose.ui.graphics.Color(0xFF23A995),
                    background = androidx.compose.ui.graphics.Color(0xFFF4F7FA),
                    onBackground = androidx.compose.ui.graphics.Color(0xFF172B3A),
                    surface = androidx.compose.ui.graphics.Color.White,
                    onSurface = androidx.compose.ui.graphics.Color(0xFF172B3A),
                    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFEAF0F5),
                    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF5D6E7E)
                )
            ) { FileManagerScreen() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileManagerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { context.getSharedPreferences("local_settings", Context.MODE_PRIVATE) }
    var savedTreeUri by remember { mutableStateOf(preferences.getString("tree_uri", null)) }
    var directoryStack by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var searchText by remember { mutableStateOf("") }
    var refreshVersion by remember { mutableIntStateOf(0) }
    var activeFile by remember { mutableStateOf<DocumentFile?>(null) }
    var activeFilter by remember { mutableStateOf(FileFilter.ALL) }
    var selectionMode by remember { mutableStateOf(false) }
    val selectedUris = remember { mutableStateListOf<String>() }
    var dialogType by remember { mutableStateOf<DialogType?>(null) }
    var dialogName by remember { mutableStateOf("") }
    var operationTarget by remember { mutableStateOf<DocumentFile?>(null) }
    var transferFile by remember { mutableStateOf<DocumentFile?>(null) }
    var transferIsCut by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) { }
            preferences.edit().putString("tree_uri", uri.toString()).apply()
            savedTreeUri = uri.toString()
            val root = DocumentFile.fromTreeUri(context, uri)
            directoryStack = if (root != null) listOf(root) else emptyList()
            searchText = ""
            activeFile = null
            selectedUris.clear()
            selectionMode = false
            refreshVersion++
        }
    }

    LaunchedEffect(savedTreeUri) {
        if (directoryStack.isEmpty() && !savedTreeUri.isNullOrBlank()) {
            val root = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(savedTreeUri)) }.getOrNull()
            if (root != null) directoryStack = listOf(root)
        }
    }

    val currentDirectory = directoryStack.lastOrNull()
    val visibleFiles = remember(currentDirectory, searchText, refreshVersion, activeFilter) {
        runCatching { currentDirectory?.listFiles()?.toList().orEmpty() }.getOrDefault(emptyList())
            .filter { file ->
                val name = file.name.orEmpty()
                (searchText.isBlank() || name.contains(searchText.trim(), ignoreCase = true)) && activeFilter.matches(file)
            }
            .sortedWith(compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name.orEmpty().lowercase(Locale.ROOT) })
    }

    fun performOperation(action: () -> String, onSuccess: () -> Unit = {}) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                val message = withContext(Dispatchers.IO) { action() }
                refreshVersion++
                feedback = message
                onSuccess()
            } catch (error: Exception) {
                feedback = "تعذر تنفيذ العملية: " + (error.message ?: "خطأ غير معروف")
            } finally { busy = false }
        }
    }

    fun openSelectedFile(file: DocumentFile) {
        if (file.isDirectory) {
            directoryStack = directoryStack + file
            searchText = ""
        } else {
            val name = file.name.orEmpty()
            if (LocalFileRules.isImage(name) || LocalFileRules.isVideo(name) || LocalFileRules.isAudio(name) ||
                LocalFileRules.isZipArchive(name) || LocalFileRules.isPdf(name) || LocalFileRules.isTextDocument(name)
            ) activeFile = file else openWithExternalApp(context, file)
        }
    }

    BackHandler(enabled = activeFile != null || directoryStack.size > 1 || selectionMode) {
        when {
            activeFile != null -> activeFile = null
            selectionMode -> { selectionMode = false; selectedUris.clear() }
            directoryStack.size > 1 -> directoryStack = directoryStack.dropLast(1)
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color(0xFFF4F7FA),
        topBar = {
            TopAppBar(
                title = {
                    if (activeFile != null) {
                        Text(activeFile?.name ?: "عارض الملفات", maxLines = 1)
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                Modifier.size(38.dp).background(
                                    androidx.compose.ui.graphics.Color(0xFF12364B),
                                    RoundedCornerShape(12.dp)
                                ),
                                contentAlignment = Alignment.Center
                            ) { Text("📂") }
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text("Eagle Files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    currentDirectory?.name ?: "مدير الملفات المحلي",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    when {
                        activeFile != null -> TextButton(onClick = { activeFile = null }) { Text("رجوع") }
                        directoryStack.size > 1 -> TextButton(onClick = { directoryStack = directoryStack.dropLast(1) }) { Text("أعلى") }
                    }
                }
            )
        }
    ) { padding ->
        if (activeFile != null) {
            val file = activeFile!!
            when {
                LocalFileRules.isImage(file.name.orEmpty()) -> ImageViewer(file.uri, Modifier.fillMaxSize().padding(padding))
                LocalFileRules.isVideo(file.name.orEmpty()) -> LocalVideoPlayer(file.uri, Modifier.fillMaxSize().padding(padding))
                LocalFileRules.isAudio(file.name.orEmpty()) -> LocalAudioPlayer(file.uri, file.name.orEmpty(), Modifier.fillMaxSize().padding(padding))
                LocalFileRules.isZipArchive(file.name.orEmpty()) -> ZipViewer(
                    archive = file, modifier = Modifier.fillMaxSize().padding(padding),
                    onExtract = {
                        val targetDirectory = currentDirectory
                        if (targetDirectory != null) performOperation({
                            val count = LocalFileOperations.extractZip(context, file, targetDirectory)
                            "اكتمل الاستخراج. عدد عناصر الأرشيف: " + count
                        })
                    }
                )
                LocalFileRules.isPdf(file.name.orEmpty()) -> PdfViewer(file.uri, Modifier.fillMaxSize().padding(padding))
                LocalFileRules.isTextDocument(file.name.orEmpty()) -> TextViewer(file.uri, Modifier.fillMaxSize().padding(padding))
                else -> Text("لا يوجد عارض داخلي لهذا النوع.", modifier = Modifier.padding(padding).padding(16.dp))
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (currentDirectory != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("ملفاتي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = { folderPicker.launch(null) }) { Text("تغيير المجلد") }
                    }
                }
                if (currentDirectory == null) {
                    Card(
                        shape = RoundedCornerShape(26.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFF12364B)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                Text(
                                    "كل ملفاتك في مكان واحد",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color.White
                                )
                                Text(
                                    "تصفّح، نظّم، افتح وانقل ملفاتك مع الحفاظ عليها داخل جهازك.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = androidx.compose.ui.graphics.Color(0xFFD7E8F0)
                                )
                                Button(
                                    onClick = { activeFilter = FileFilter.ALL; folderPicker.launch(null) },
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = androidx.compose.ui.graphics.Color(0xFF65D8C5),
                                        contentColor = androidx.compose.ui.graphics.Color(0xFF12364B)
                                    )
                                ) { Text("ابدأ تصفح الملفات", fontWeight = FontWeight.Bold) }
                            }
                            Box(
                                Modifier.size(68.dp).background(
                                    androidx.compose.ui.graphics.Color(0xFF24566D),
                                    RoundedCornerShape(20.dp)
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🗂️", style = MaterialTheme.typography.displaySmall)
                            }
                        }
                    }
                    Text("تصفّح حسب النوع", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        HomeCategoryCard(
                            title = "كل الملفات", subtitle = "المجلدات والملفات",
                            icon = "📁", tint = androidx.compose.ui.graphics.Color(0xFF1C8190),
                            modifier = Modifier.weight(1f)
                        ) { activeFilter = FileFilter.ALL; folderPicker.launch(null) }
                        HomeCategoryCard(
                            title = "الصور", subtitle = "صورك المحلية",
                            icon = "🖼️", tint = androidx.compose.ui.graphics.Color(0xFF6B66C8),
                            modifier = Modifier.weight(1f)
                        ) { activeFilter = FileFilter.IMAGES; folderPicker.launch(null) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        HomeCategoryCard(
                            title = "الفيديو", subtitle = "مقاطع الفيديو",
                            icon = "🎬", tint = androidx.compose.ui.graphics.Color(0xFFE08A3E),
                            modifier = Modifier.weight(1f)
                        ) { activeFilter = FileFilter.VIDEO; folderPicker.launch(null) }
                        HomeCategoryCard(
                            title = "المستندات", subtitle = "PDF والنصوص",
                            icon = "📄", tint = androidx.compose.ui.graphics.Color(0xFF3A8B63),
                            modifier = Modifier.weight(1f)
                        ) { activeFilter = FileFilter.DOCUMENTS; folderPicker.launch(null) }
                    }
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFFE7F4F2)
                        )
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("🔒", style = MaterialTheme.typography.titleLarge)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("خصوصيتك أولًا", fontWeight = FontWeight.Bold)
                                Text(
                                    "يعمل محليًا دون إنترنت. الوصول يقتصر على المجلد الذي تختاره.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                } else {
                    Text(directoryStack.joinToString("  ›  ") { it.name ?: "المجلد" },
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    OutlinedTextField(value = searchText, onValueChange = { searchText = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("بحث في المجلد الحالي") })
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FileFilter.entries.forEach { filter ->
                            FilterChip(selected = activeFilter == filter, onClick = { activeFilter = filter }, label = { Text(filter.label) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        Text(visibleFiles.size.toString() + " عنصر", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = { refreshVersion++ }) { Text("تحديث") }
                        TextButton(onClick = { selectionMode = !selectionMode; selectedUris.clear() }) {
                            Text(if (selectionMode) "إلغاء التحديد" else "تحديد")
                        }
                        TextButton(onClick = { dialogName = ""; dialogType = DialogType.CREATE_FOLDER }) { Text("مجلد +") }
                    }

                    if (selectionMode) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("تم تحديد " + selectedUris.size, modifier = Modifier.weight(1f))
                        Button(onClick = { dialogName = "أرشيف"; dialogType = DialogType.CREATE_ZIP },
                            enabled = selectedUris.isNotEmpty() && !busy) { Text("إنشاء ZIP") }
                    }
                    if (transferFile != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (transferIsCut) "عنصر جاهز للنقل" else "عنصر جاهز للنسخ", modifier = Modifier.weight(1f))
                        Button(enabled = !busy, onClick = {
                            val source = transferFile
                            val destination = currentDirectory
                            if (source != null && destination != null) performOperation({
                                val copied = LocalFileOperations.copyInto(context, source, destination)
                                if (transferIsCut) {
                                    if (!LocalFileOperations.deleteRecursively(source)) throw IllegalStateException("تم النسخ، لكن تعذر حذف المصدر؛ تحقق من المجلدين")
                                    "تم نقل " + copied.name
                                } else "تم نسخ " + copied.name
                            }, onSuccess = { transferFile = null })
                        }) { Text("لصق هنا") }
                        TextButton(onClick = { transferFile = null }) { Text("إلغاء") }
                    }

                    Divider()
                    if (busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text("جارٍ تنفيذ العملية محليًا…")
                    }
                    feedback?.let { message ->
                        Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.clickable { feedback = null })
                    }
                    if (visibleFiles.isEmpty()) Text("لا توجد ملفات مطابقة.", modifier = Modifier.padding(12.dp))
                    else LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(visibleFiles, key = { it.uri.toString() }) { file ->
                            FileRow(file, selectionMode, selectedUris.contains(file.uri.toString()),
                                onToggle = {
                                    val id = file.uri.toString()
                                    if (selectedUris.contains(id)) selectedUris.remove(id) else selectedUris.add(id)
                                },
                                onClick = {
                                    if (selectionMode) {
                                        val id = file.uri.toString()
                                        if (selectedUris.contains(id)) selectedUris.remove(id) else selectedUris.add(id)
                                    } else openSelectedFile(file)
                                },
                                onAction = { action ->
                                    when (action) {
                                        "rename" -> { operationTarget = file; dialogName = file.name.orEmpty(); dialogType = DialogType.RENAME }
                                        "delete" -> { operationTarget = file; dialogType = DialogType.DELETE }
                                        "copy" -> { transferFile = file; transferIsCut = false; feedback = "انتقل إلى المجلد المطلوب ثم اضغط «لصق هنا»." }
                                        "cut" -> { transferFile = file; transferIsCut = true; feedback = "انتقل إلى المجلد المطلوب ثم اضغط «لصق هنا»." }
                                    }
                                })
                        }
                    }
                }
                Text("وضع محلي • لا يلزم اتصال إنترنت", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    val currentDialog = dialogType
    if (currentDialog != null) {
        val dialogTitle = when (currentDialog) {
            DialogType.CREATE_FOLDER -> "إنشاء مجلد جديد"
            DialogType.RENAME -> "إعادة تسمية العنصر"
            DialogType.DELETE -> "حذف العنصر؟"
            DialogType.CREATE_ZIP -> "إنشاء أرشيف ZIP"
        }
        AlertDialog(
            onDismissRequest = { dialogType = null },
            title = { Text(dialogTitle) },
            text = {
                if (currentDialog == DialogType.DELETE) Text("سيتم حذف العنصر المحدد ومحتوياته إن كان مجلدًا. لا يمكن التراجع عن الحذف.")
                else OutlinedTextField(value = dialogName, onValueChange = { dialogName = it }, singleLine = true,
                    label = { Text(if (currentDialog == DialogType.CREATE_ZIP) "اسم الأرشيف" else "الاسم") })
            },
            confirmButton = {
                Button(enabled = !busy && (currentDialog == DialogType.DELETE || dialogName.isNotBlank()), onClick = {
                    val kind = currentDialog
                    val target = operationTarget
                    val parent = currentDirectory
                    val name = dialogName
                    dialogType = null
                    when (kind) {
                        DialogType.CREATE_FOLDER -> if (parent != null) performOperation({
                            "تم إنشاء المجلد " + LocalFileOperations.createFolder(parent, name).name
                        })
                        DialogType.RENAME -> if (target != null) performOperation({
                            if (!LocalFileOperations.rename(target, name)) throw IllegalStateException("تعذر إعادة التسمية")
                            "تمت إعادة التسمية إلى " + LocalFileRules.safeFileName(name)
                        })
                        DialogType.DELETE -> if (target != null) performOperation({
                            if (!LocalFileOperations.deleteRecursively(target)) throw IllegalStateException("تعذر حذف العنصر")
                            "تم حذف العنصر"
                        })
                        DialogType.CREATE_ZIP -> if (parent != null) {
                            val sources = parent.listFiles().filter { selectedUris.contains(it.uri.toString()) }
                            performOperation({
                                "تم إنشاء الأرشيف " + LocalFileOperations.createZip(context, sources, parent, name).name
                            }, onSuccess = { selectedUris.clear(); selectionMode = false })
                        }
                        null -> Unit
                    }
                }) { Text(if (currentDialog == DialogType.DELETE) "حذف" else "تنفيذ") }
            },
            dismissButton = { TextButton(onClick = { dialogType = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun FileRow(
    file: DocumentFile, selectionMode: Boolean, selected: Boolean,
    onToggle: () -> Unit, onClick: () -> Unit, onAction: (String) -> Unit
) {
    val name = file.name ?: "(بدون اسم)"
    val icon = when {
        file.isDirectory -> "📁"
        LocalFileRules.isImage(name) -> "🖼️"
        LocalFileRules.isVideo(name) -> "🎬"
        LocalFileRules.isAudio(name) -> "🎵"
        LocalFileRules.isZipArchive(name) -> "🗜️"
        LocalFileRules.isPdf(name) -> "📕"
        LocalFileRules.isDocument(name) -> "📄"
        else -> "📃"
    }
    var menuExpanded by remember(file.uri) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onToggle() })
            } else {
                val iconTint = when {
                    file.isDirectory -> androidx.compose.ui.graphics.Color(0xFFFFF0C2)
                    LocalFileRules.isImage(name) -> androidx.compose.ui.graphics.Color(0xFFE6E7FF)
                    LocalFileRules.isVideo(name) -> androidx.compose.ui.graphics.Color(0xFFFFE8D5)
                    LocalFileRules.isAudio(name) -> androidx.compose.ui.graphics.Color(0xFFE4F5ED)
                    LocalFileRules.isZipArchive(name) -> androidx.compose.ui.graphics.Color(0xFFFFEBC2)
                    LocalFileRules.isPdf(name) -> androidx.compose.ui.graphics.Color(0xFFFFE2E2)
                    else -> androidx.compose.ui.graphics.Color(0xFFE3EFF7)
                }
                Box(
                    Modifier.size(42.dp).background(iconTint, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) { Text(icon, style = MaterialTheme.typography.titleMedium) }
            }
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Medium, maxLines = 2)
                Text(if (file.isDirectory) "مجلد" else formatSize(file.length()), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!selectionMode) {
                Box {
                    TextButton(onClick = { menuExpanded = true }) { Text("⋮") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("نسخ") }, onClick = { menuExpanded = false; onAction("copy") })
                        DropdownMenuItem(text = { Text("قص / نقل") }, onClick = { menuExpanded = false; onAction("cut") })
                        DropdownMenuItem(text = { Text("إعادة تسمية") }, onClick = { menuExpanded = false; onAction("rename") })
                        DropdownMenuItem(text = { Text("حذف") }, onClick = { menuExpanded = false; onAction("delete") })
                    }
                }
                Text(if (file.isDirectory) "فتح ›" else "عرض", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ImageViewer(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var loadingError by remember(uri) { mutableStateOf(false) }
    var zoom by remember(uri) { mutableFloatStateOf(1f) }
    var pan by remember(uri) { mutableStateOf(Offset.Zero) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) { decodeScaledImage(context, uri) }
        loadingError = bitmap == null
    }
    Box(modifier.background(androidx.compose.ui.graphics.Color.Black), contentAlignment = Alignment.Center) {
        when {
            bitmap != null -> Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = "عارض الصور",
                modifier = Modifier.fillMaxSize().pointerInput(uri) {
                    detectTransformGestures { _, gesturePan, gestureZoom, _ ->
                        zoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                        pan = if (zoom <= 1f) Offset.Zero else pan + gesturePan
                    }
                }.graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = pan.x, translationY = pan.y),
                contentScale = ContentScale.Fit)
            loadingError -> Text("تعذر فتح الصورة أو أن تنسيقها غير مدعوم.", color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(20.dp))
            else -> CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White)
        }
    }
}

private fun decodeScaledImage(context: Context, uri: Uri): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 2560 || bounds.outHeight / sample > 2560) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    } catch (_: Exception) { null }
}

@Composable
private fun LocalAudioPlayer(uri: Uri, name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var player by remember(uri) { mutableStateOf<MediaPlayer?>(null) }
    var prepared by remember(uri) { mutableStateOf(false) }
    var playing by remember(uri) { mutableStateOf(false) }
    var failed by remember(uri) { mutableStateOf(false) }
    DisposableEffect(uri) {
        val media = MediaPlayer()
        player = media
        media.setOnPreparedListener { prepared = true; it.start(); playing = true }
        media.setOnCompletionListener { playing = false }
        media.setOnErrorListener { _, _, _ -> failed = true; playing = false; true }
        try { media.setDataSource(context, uri); media.prepareAsync() } catch (_: Exception) { failed = true }
        onDispose { player = null; try { media.release() } catch (_: Exception) { } }
    }
    Column(modifier.padding(22.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🎵", style = MaterialTheme.typography.displayLarge)
        Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("مشغل صوت محلي", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        when {
            failed -> Text("تعذر تشغيل الملف. قد يكون الترميز غير مدعوم.")
            !prepared -> CircularProgressIndicator()
            else -> Button(onClick = {
                val media = player ?: return@Button
                try {
                    if (media.isPlaying) { media.pause(); playing = false } else { media.start(); playing = true }
                } catch (_: IllegalStateException) { failed = true }
            }) { Text(if (playing) "إيقاف مؤقت ⏸" else "تشغيل ▶") }
        }
    }
}

@Composable
private fun LocalVideoPlayer(uri: Uri, modifier: Modifier = Modifier) {
    var playerError by remember(uri) { mutableStateOf(false) }
    Column(modifier.background(androidx.compose.ui.graphics.Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize().weight(1f),
            factory = { viewContext ->
                VideoView(viewContext).apply {
                    tag = uri.toString()
                    val controller = MediaController(viewContext)
                    controller.setAnchorView(this)
                    setMediaController(controller)
                    setVideoURI(uri)
                    setOnPreparedListener { it.isLooping = false; start() }
                    setOnErrorListener { _, _, _ -> playerError = true; true }
                }
            },
            update = { view -> if (view.tag != uri.toString()) { view.tag = uri.toString(); view.setVideoURI(uri); view.start() } }
        )
        if (playerError) Text("قد لا يكون ترميز الفيديو مدعومًا على هذا الجهاز.", color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun TextViewer(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var body by remember(uri) { mutableStateOf<String?>(null) }
    var readError by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(uri) {
        body = withContext(Dispatchers.IO) { readTextLimited(context, uri) }
        readError = body == null
    }
    when {
        body != null -> Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(body!!, style = MaterialTheme.typography.bodyMedium)
            if (body!!.endsWith("\n… تم اقتطاع الملف للعرض الآمن …")) {
                Text("تم اقتطاع الملف للعرض الآمن (حد العرض 1 ميبيبايت).", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        readError -> Text("تعذر قراءة الملف النصي.", modifier = modifier.padding(18.dp))
        else -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

private fun readTextLimited(context: Context, uri: Uri): String? {
    return try {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                val builder = StringBuilder()
                val buffer = CharArray(8192)
                var total = 0
                while (true) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    val available = 1_048_576 - total
                    if (available <= 0) return builder.append("\n… تم اقتطاع الملف للعرض الآمن …").toString()
                    val accepted = minOf(read, available)
                    builder.append(buffer, 0, accepted)
                    total += accepted
                    if (accepted < read) return builder.append("\n… تم اقتطاع الملف للعرض الآمن …").toString()
                }
                builder.toString()
            }
        }
    } catch (_: Exception) { null }
}

private data class PdfRenderResult(val pageCount: Int, val bitmap: Bitmap?)

@Composable
private fun PdfViewer(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var pageIndex by remember(uri) { mutableIntStateOf(0) }
    var pageCount by remember(uri) { mutableIntStateOf(0) }
    var pageBitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(uri) { mutableStateOf(true) }
    var failed by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(uri, pageIndex) {
        loading = true
        val rendered = withContext(Dispatchers.IO) { renderPdfPage(context, uri, pageIndex) }
        pageCount = rendered?.pageCount ?: 0
        pageBitmap = rendered?.bitmap
        failed = rendered == null
        loading = false
        if (pageCount > 0 && pageIndex >= pageCount) pageIndex = pageCount - 1
    }
    Column(modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(enabled = pageIndex > 0 && !loading, onClick = { pageIndex-- }) { Text("السابق") }
            Text(if (pageCount > 0) (pageIndex + 1).toString() + " / " + pageCount else "PDF")
            TextButton(enabled = pageCount > 0 && pageIndex + 1 < pageCount && !loading, onClick = { pageIndex++ }) { Text("التالي") }
        }
        when {
            loading -> Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            failed -> Text("تعذر فتح ملف PDF. قد يكون الملف محميًا أو تالفًا.", modifier = Modifier.padding(16.dp))
            pageBitmap != null -> Image(bitmap = pageBitmap!!.asImageBitmap(), contentDescription = "صفحة PDF " + (pageIndex + 1),
                modifier = Modifier.fillMaxSize().weight(1f), contentScale = ContentScale.Fit)
        }
    }
}

private fun renderPdfPage(context: Context, uri: Uri, requestedPage: Int): PdfRenderResult? {
    var descriptor: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    var page: PdfRenderer.Page? = null
    return try {
        val openedDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        descriptor = openedDescriptor
        val openedRenderer = PdfRenderer(openedDescriptor)
        renderer = openedRenderer
        if (openedRenderer.pageCount <= 0) return null
        val openedPage = openedRenderer.openPage(requestedPage.coerceIn(0, openedRenderer.pageCount - 1))
        page = openedPage
        val scale = minOf(1f, 1800f / maxOf(openedPage.width, openedPage.height).coerceAtLeast(1))
        val bitmap = Bitmap.createBitmap((openedPage.width * scale).toInt().coerceAtLeast(1),
            (openedPage.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        openedPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        PdfRenderResult(openedRenderer.pageCount, bitmap)
    } catch (_: Exception) {
        null
    } finally {
        try { page?.close() } catch (_: Exception) { }
        try { renderer?.close() } catch (_: Exception) { }
        if (renderer == null) try { descriptor?.close() } catch (_: Exception) { }
    }
}

@Composable
private fun ZipViewer(archive: DocumentFile, modifier: Modifier = Modifier, onExtract: () -> Unit) {
    val context = LocalContext.current
    var entries by remember(archive.uri) { mutableStateOf<List<String>>(emptyList()) }
    var loadFailed by remember(archive.uri) { mutableStateOf(false) }
    var askingToExtract by remember { mutableStateOf(false) }
    var loading by remember(archive.uri) { mutableStateOf(true) }
    LaunchedEffect(archive.uri) {
        loading = true
        val result = runCatching { withContext(Dispatchers.IO) { LocalFileOperations.listZipEntries(context, archive) } }
        entries = result.getOrDefault(emptyList())
        loadFailed = result.isFailure
        loading = false
    }
    Column(modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("محرك أرشيف ZIP", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text((archive.name ?: "ZIP") + " • معاينة أول " + entries.size + " عنصر", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { askingToExtract = true }) { Text("استخراج إلى المجلد الحالي") }
        when {
            loading -> CircularProgressIndicator()
            loadFailed -> Text("تعذر قراءة الأرشيف؛ قد يكون تالفًا أو غير صالح.")
            entries.isEmpty() -> Text("الأرشيف فارغ.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(entries) { name -> Text(name, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) }
            }
        }
    }
    if (askingToExtract) AlertDialog(
        onDismissRequest = { askingToExtract = false },
        title = { Text("استخراج الأرشيف؟") },
        text = { Text("سيتم الاستخراج داخل المجلد الحالي. يفحص التطبيق المسارات ويمنع الكتابة فوق الملفات الموجودة.") },
        confirmButton = { Button(onClick = { askingToExtract = false; onExtract() }) { Text("استخراج") } },
        dismissButton = { TextButton(onClick = { askingToExtract = false }) { Text("إلغاء") } }
    )
}

private fun openWithExternalApp(context: Context, file: DocumentFile) {
    val mime = file.type?.takeIf { it.isNotBlank() } ?: LocalFileOperations.mimeForName(file.name.orEmpty())
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(file.uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "فتح الملف بواسطة"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "لا يوجد تطبيق محلي لفتح هذا النوع من الملفات.", Toast.LENGTH_LONG).show()
    }
}


@Composable
private fun HomeCategoryCard(
    title: String,
    subtitle: String,
    icon: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color.White
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(42.dp).background(tint.copy(alpha = 0.14f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) { Text(icon, style = MaterialTheme.typography.titleMedium) }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}
