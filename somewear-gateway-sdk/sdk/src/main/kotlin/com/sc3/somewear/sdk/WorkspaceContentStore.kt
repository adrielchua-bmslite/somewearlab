package com.sc3.somewear.sdk

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** App-private cache and catalogue owned by the SDK rather than SC3 UI code. */
internal class WorkspaceContentStore(context: Context) {
    private val root = File(context.filesDir, "somewear-content")

    @Synchronized
    fun attachCacheState(file: WorkspaceFile): WorkspaceFile =
        file.copy(cachedUri = cachedUri(file))

    @Synchronized
    fun cachedUri(file: WorkspaceFile): Uri? {
        val target = contentFile(file)
        return target
            .takeIf { it.isFile && it.length() == file.sizeBytes }
            ?.let(Uri::fromFile)
    }

    @Synchronized
    fun contentFile(file: WorkspaceFile): File {
        val directory = File(workspaceDirectory(file.workspaceId), "files")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(file.fileId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val extension = file.fileName
            .substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.length in 1..10 && it.all(Char::isLetterOrDigit) }
        val name = if (extension == null) digest else "$digest.$extension"
        return File(directory, name)
    }

    @Synchronized
    fun saveCatalogue(workspaceId: Long, files: List<WorkspaceFile>) {
        require(workspaceId > 0L) { "workspaceId must be positive" }
        require(files.all { it.workspaceId == workspaceId }) {
            "All catalogue entries must belong to workspaceId"
        }
        val directory = workspaceDirectory(workspaceId).apply { mkdirs() }
        val target = File(directory, CATALOGUE_FILE_NAME)
        val temporary = File(directory, "$CATALOGUE_FILE_NAME.part")
        val array = JSONArray()
        files.forEach { file -> array.put(file.toJson()) }
        temporary.writeText(
            JSONObject()
                .put("version", CATALOGUE_VERSION)
                .put("workspace_id", workspaceId)
                .put("files", array)
                .toString(),
            Charsets.UTF_8,
        )
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    @Synchronized
    fun readCatalogue(workspaceId: Long): List<WorkspaceFile> {
        require(workspaceId > 0L) { "workspaceId must be positive" }
        val target = File(workspaceDirectory(workspaceId), CATALOGUE_FILE_NAME)
        if (!target.isFile) return emptyList()
        return runCatching {
            val document = JSONObject(target.readText(Charsets.UTF_8))
            if (
                document.optInt("version") != CATALOGUE_VERSION ||
                document.optLong("workspace_id") != workspaceId
            ) {
                return@runCatching emptyList()
            }
            val array = document.optJSONArray("files") ?: return@runCatching emptyList()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val parsed = item.toWorkspaceFile(workspaceId) ?: continue
                    add(attachCacheState(parsed))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun workspaceDirectory(workspaceId: Long): File = File(root, workspaceId.toString())

    private fun WorkspaceFile.toJson(): JSONObject = JSONObject()
        .put("file_id", fileId)
        .put("file_name", fileName)
        .put("mime_type", mimeType ?: JSONObject.NULL)
        .put("size_bytes", sizeBytes)
        .put("owner_user_id", fileOwnerUserId ?: JSONObject.NULL)
        .put("created_at_ms", createdAtEpochMillis ?: JSONObject.NULL)
        .put("uploaded_at_ms", uploadedAtEpochMillis ?: JSONObject.NULL)
        .put("voice_recording", isVoiceRecording)
        .put("media_duration_ms", mediaDurationMillis ?: JSONObject.NULL)

    private fun JSONObject.toWorkspaceFile(workspaceId: Long): WorkspaceFile? = runCatching {
        WorkspaceFile(
            fileId = getString("file_id"),
            fileName = getString("file_name"),
            mimeType = nullableString("mime_type"),
            sizeBytes = getLong("size_bytes"),
            workspaceId = workspaceId,
            fileOwnerUserId = nullableString("owner_user_id"),
            createdAtEpochMillis = nullableLong("created_at_ms"),
            uploadedAtEpochMillis = nullableLong("uploaded_at_ms"),
            isVoiceRecording = optBoolean("voice_recording", false),
            mediaDurationMillis = nullableInt("media_duration_ms"),
        )
    }.getOrNull()

    private fun JSONObject.nullableString(key: String): String? =
        takeUnless { isNull(key) }?.optString(key)?.takeIf(String::isNotBlank)

    private fun JSONObject.nullableLong(key: String): Long? =
        takeUnless { isNull(key) }?.optLong(key)?.takeIf { it > 0L }

    private fun JSONObject.nullableInt(key: String): Int? =
        takeUnless { isNull(key) }?.optInt(key)?.takeIf { it >= 0 }

    private companion object {
        const val CATALOGUE_VERSION = 1
        const val CATALOGUE_FILE_NAME = "catalogue.json"
    }
}
