package com.sc3.somewear.sdk

import org.json.JSONArray
import org.json.JSONObject

internal fun FileBatchManifest.encodeFileBatchManifest(): String {
    val entries = JSONArray()
    files.forEach { file ->
        entries.put(
            JSONObject()
                .put("index", file.index)
                .put("file_id", file.fileId)
                .put("file_name", file.fileName)
                .put("mime_type", file.mimeType ?: JSONObject.NULL)
                .put("size_bytes", file.sizeBytes)
                .put("sha256", file.sha256),
        )
    }
    return JSONObject()
        .put("format", "SC3_FILE_BATCH")
        .put("version", FileBatchFormat.VERSION)
        .put("batch_id", batchId)
        .put("workspace_id", workspaceId)
        .put("revision", revision)
        .put("final", finalRevision)
        .put("expected_file_count", expectedFileCount)
        .put("created_at_ms", createdAtEpochMillis)
        .put("files", entries)
        .toString()
}

internal fun decodeFileBatchManifest(encoded: String): FileBatchManifest {
    require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_MANIFEST_BYTES) {
        "File batch manifest exceeds $MAX_MANIFEST_BYTES bytes"
    }
    val document = JSONObject(encoded)
    require(document.getString("format") == "SC3_FILE_BATCH") {
        "Not an SC3 file batch manifest"
    }
    require(document.getInt("version") == FileBatchFormat.VERSION) {
        "Unsupported SC3 file batch manifest version"
    }
    val rawFiles = document.getJSONArray("files")
    val files = buildList(rawFiles.length()) {
        for (index in 0 until rawFiles.length()) {
            val item = rawFiles.getJSONObject(index)
            add(
                FileBatchEntry(
                    index = item.getInt("index"),
                    fileId = item.getString("file_id"),
                    fileName = item.getString("file_name"),
                    mimeType = item.takeUnless { it.isNull("mime_type") }
                        ?.getString("mime_type"),
                    sizeBytes = item.getLong("size_bytes"),
                    sha256 = item.getString("sha256"),
                ),
            )
        }
    }
    return FileBatchManifest(
        batchId = document.getString("batch_id"),
        workspaceId = document.getLong("workspace_id"),
        revision = document.getInt("revision"),
        finalRevision = document.getBoolean("final"),
        expectedFileCount = document.getInt("expected_file_count"),
        createdAtEpochMillis = document.getLong("created_at_ms"),
        files = files,
    )
}

internal const val MAX_MANIFEST_BYTES: Int = 8 * 1_024 * 1_024
