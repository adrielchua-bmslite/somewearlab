package com.sc3.somewear.sdk

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal data class LocalFileDescription(
    val name: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val sha256: String,
)

internal fun inspectLocalFile(
    resolver: ContentResolver,
    uri: Uri,
    requestedName: String?,
    requestedMimeType: String?,
): LocalFileDescription {
    var providerName: String? = null
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0) providerName = cursor.getString(column)
            }
        }
    }

    val digest = MessageDigest.getInstance("SHA-256")
    var count = 0L
    resolver.openInputStream(uri)?.use { raw ->
        BufferedInputStream(raw).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                digest.update(buffer, 0, read)
                count += read
            }
        }
    } ?: throw IllegalArgumentException("The source URI cannot be opened")

    val fallbackName = uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.takeIf(String::isNotBlank)
        ?: "sc3-file"
    return LocalFileDescription(
        name = requestedName ?: providerName?.takeIf(String::isNotBlank) ?: fallbackName,
        mimeType = requestedMimeType ?: resolver.getType(uri),
        sizeBytes = count,
        sha256 = digest.digest().joinToString("") { "%02x".format(it) },
    )
}

internal fun uploadLocalFile(
    resolver: ContentResolver,
    sourceUri: Uri,
    signedUploadUrl: String,
    mimeType: String?,
    sizeBytes: Long,
) {
    val connection = (URL(signedUploadUrl).openConnection() as HttpURLConnection).apply {
        requestMethod = "PUT"
        doOutput = true
        connectTimeout = 30_000
        readTimeout = 120_000
        setRequestProperty("Content-Type", mimeType ?: "application/octet-stream")
        if (sizeBytes >= 0L) setFixedLengthStreamingMode(sizeBytes)
    }
    try {
        resolver.openInputStream(sourceUri)?.use { raw ->
            BufferedInputStream(raw).use { input ->
                BufferedOutputStream(connection.outputStream).use { output ->
                    input.copyTo(output)
                }
            }
        } ?: throw IllegalArgumentException("The source URI cannot be reopened")
        val code = connection.responseCode
        if (code !in 200..299) {
            throw IllegalStateException("Somewear file upload returned HTTP $code")
        }
    } finally {
        connection.disconnect()
    }
}

internal fun downloadToLocalFile(
    resolver: ContentResolver,
    signedDownloadUrl: String,
    destinationUri: Uri,
): Long {
    val connection = (URL(signedDownloadUrl).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 30_000
        readTimeout = 120_000
    }
    try {
        val code = connection.responseCode
        if (code !in 200..299) {
            throw IllegalStateException("Somewear file download returned HTTP $code")
        }
        var count = 0L
        BufferedInputStream(connection.inputStream).use { input ->
            resolver.openOutputStream(destinationUri, "w")?.use { raw ->
                BufferedOutputStream(raw).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        count += read
                    }
                }
            } ?: throw IllegalArgumentException("The destination URI cannot be opened")
        }
        return count
    } finally {
        connection.disconnect()
    }
}
