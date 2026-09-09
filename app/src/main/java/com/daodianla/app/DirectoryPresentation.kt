package com.daodianla.app

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * 将持久化的目录值转换为适合展示的文本。
 * 原始值不会在这里被改写，实际读写仍使用原始路径或 content URI。
 */
internal fun displayableDirectoryPath(context: Context, rawValue: String?): String? {
    if (rawValue.isNullOrBlank()) return null

    val uri = runCatching { Uri.parse(rawValue) }.getOrNull()
    if (uri != null && uri.scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true)) {
        return displayableDocumentUri(context, uri)
    }

    val filePath = when {
        uri != null && uri.scheme.equals(ContentResolver.SCHEME_FILE, ignoreCase = true) -> uri.path
        uri?.scheme.isNullOrEmpty() -> rawValue
        else -> null
    }

    return if (filePath != null) {
        displayableFilePath(filePath)
    } else {
        "已选目录（暂不可解析）"
    }
}

private fun displayableDocumentUri(context: Context, uri: Uri): String {
    val documentId = runCatching {
        if (DocumentsContract.isTreeUri(uri)) {
            DocumentsContract.getTreeDocumentId(uri)
        } else {
            DocumentsContract.getDocumentId(uri)
        }
    }.getOrNull()

    val documentFile = runCatching {
        if (DocumentsContract.isTreeUri(uri)) {
            DocumentFile.fromTreeUri(context, uri)
        } else {
            DocumentFile.fromSingleUri(context, uri)
        }
    }.getOrNull()

    val parts = documentId?.split(':', limit = 2).orEmpty()
    val volumeId = parts.firstOrNull().orEmpty()
    val relativePath = parts.getOrNull(1).orEmpty()
    val volumeLabel = when (volumeId.lowercase(Locale.ROOT)) {
        "primary" -> "内部存储"
        "home" -> "主目录"
        else -> Uri.decode(volumeId).ifBlank { "存储" }
    }

    val decodedSegments = relativePath
        .split('/')
        .map(Uri::decode)
        .filter(String::isNotBlank)

    val documentName = runCatching {
        documentFile?.name?.takeIf(String::isNotBlank)
    }.getOrNull()
    if (volumeId.isBlank() && decodedSegments.isEmpty()) {
        return documentName?.let { "已选目录 / $it" } ?: "已选目录（URI 暂不可用）"
    }

    val canAccessDocument = runCatching { documentFile?.exists() == true }.getOrDefault(false)
    if (canAccessDocument || decodedSegments.isNotEmpty() || volumeId.isNotBlank()) {
        return (listOf(volumeLabel) + decodedSegments).joinToString(" / ")
    }

    return documentName?.let { "已选目录 / $it" } ?: "已选目录（URI 暂不可用）"
}

private fun displayableFilePath(path: String): String {
    val normalizedPath = path.replace('\\', '/')
    val storageRoot = Environment.getExternalStorageDirectory().absolutePath
        .replace('\\', '/')
        .trimEnd('/')

    return when {
        normalizedPath == storageRoot -> "内部存储"
        normalizedPath.startsWith("$storageRoot/") -> {
            val relativePath = normalizedPath.removePrefix("$storageRoot/")
            (listOf("内部存储") + relativePath.split('/').filter(String::isNotBlank))
                .joinToString(" / ")
        }
        normalizedPath.isBlank() -> "已选目录（路径为空）"
        else -> normalizedPath
    }
}

internal fun writeTextToDirectory(
    context: Context,
    rawDirectory: String,
    fileName: String,
    contents: String
): String {
    val uri = runCatching { Uri.parse(rawDirectory) }.getOrNull()
    if (uri != null && uri.scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true)) {
        val directory = DocumentFile.fromTreeUri(context, uri)
            ?: throw IOException("无法解析导出目录")
        if (!directory.isDirectory) throw IOException("导出目录不可用")

        directory.findFile(fileName)?.delete()
        val target = directory.createFile("application/json", fileName)
            ?: throw IOException("无法创建导出文件")
        context.contentResolver.openOutputStream(target.uri)?.use { output ->
            output.write(contents.toByteArray(Charsets.UTF_8))
        } ?: throw IOException("无法写入导出文件")
        return target.name ?: fileName
    }

    val directoryPath = when {
        uri != null && uri.scheme.equals(ContentResolver.SCHEME_FILE, ignoreCase = true) -> uri.path
        uri?.scheme.isNullOrEmpty() -> rawDirectory
        else -> null
    } ?: throw IOException("导出目录格式无效")

    val directory = File(directoryPath)
    if (!directory.isDirectory) throw IOException("导出目录不可用")
    File(directory, fileName).writeText(contents, Charsets.UTF_8)
    return fileName
}
