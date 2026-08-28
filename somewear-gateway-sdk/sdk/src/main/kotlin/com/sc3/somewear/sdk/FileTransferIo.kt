package com.sc3.somewear.sdk

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

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

/** Downloads to an app-private temporary file and publishes it only after size verification. */
internal fun downloadToManagedFile(
    signedDownloadUrl: String,
    destination: File,
    expectedSizeBytes: Long,
    expectedSha256: String? = null,
): Long {
    require(expectedSizeBytes >= 0L) { "expectedSizeBytes must be non-negative" }
    require(expectedSha256 == null || expectedSha256.matches(Regex("[0-9a-f]{64}"))) {
        "expectedSha256 must be lowercase hexadecimal"
    }
    destination.parentFile?.mkdirs()
    val temporary = File(
        destination.parentFile,
        ".${destination.name}.${UUID.randomUUID()}.part",
    )
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
        val digest = expectedSha256?.let { MessageDigest.getInstance("SHA-256") }
        BufferedInputStream(connection.inputStream).use { input ->
            BufferedOutputStream(temporary.outputStream()).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    output.write(buffer, 0, read)
                    digest?.update(buffer, 0, read)
                    count += read
                }
            }
        }
        if (count != expectedSizeBytes) {
            throw FileSizeMismatchException(expectedSizeBytes, count)
        }
        if (digest != null) {
            val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            if (actualSha256 != expectedSha256) {
                throw FileHashMismatchException(expectedSha256.orEmpty(), actualSha256)
            }
        }
        try {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
        return count
    } finally {
        connection.disconnect()
        temporary.delete()
    }
}

internal fun downloadToByteArray(
    signedDownloadUrl: String,
    maximumBytes: Int,
): ByteArray {
    require(maximumBytes > 0) { "maximumBytes must be positive" }
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
        val declared = connection.contentLengthLong
        if (declared > maximumBytes) {
            throw IllegalStateException("Somewear file exceeds the $maximumBytes byte limit")
        }
        val output = ByteArrayOutputStream(
            if (declared in 1..maximumBytes.toLong()) declared.toInt() else DEFAULT_BUFFER_SIZE,
        )
        BufferedInputStream(connection.inputStream).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var count = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                count += read
                if (count > maximumBytes) {
                    throw IllegalStateException("Somewear file exceeds the $maximumBytes byte limit")
                }
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    } finally {
        connection.disconnect()
    }
}

internal fun sha256OfFile(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal class FileSizeMismatchException(
    val expectedBytes: Long,
    val actualBytes: Long,
) : IllegalStateException(
    "Downloaded file size mismatch: expected $expectedBytes bytes but received $actualBytes",
)

internal class FileHashMismatchException(
    val expectedSha256: String,
    val actualSha256: String,
) : IllegalStateException(
    "Downloaded file SHA-256 mismatch: expected $expectedSha256 but received $actualSha256",
)
