package com.sc3.somewear.sdk

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

public enum class WorkspaceInviteKind {
    SERVICE_TOKEN,
    MESH_KEY,
}

/** Non-secret metadata extracted from a Somewear workspace invite. */
public data class WorkspaceInviteInfo(
    val kind: WorkspaceInviteKind,
    val workspaceName: String?,
    val host: String?,
    val port: Int?,
)

/**
 * Validates the decoded contents of a Somewear workspace QR code without
 * exposing or logging the invite token or mesh key.
 */
public object WorkspaceInviteCode {
    @JvmStatic
    public fun inspect(rawValue: String): WorkspaceInviteInfo? {
        if (rawValue.isBlank() || rawValue.length > MAX_INVITE_LENGTH) return null
        return runCatching {
            val uri = URI(rawValue.trim())
            val query = uri.rawQuery.orEmpty()
                .split('&')
                .asSequence()
                .filter(String::isNotBlank)
                .map { item ->
                    val separator = item.indexOf('=')
                    val key = if (separator >= 0) item.substring(0, separator) else item
                    val value = if (separator >= 0) item.substring(separator + 1) else ""
                    decode(key) to decode(value)
                }
                .toMap()
            val token = query["token"].nonBlankOrNull()
            val meshKey = query["meshKey"].nonBlankOrNull()
            val workspaceId = query["workspaceId"].nonBlankOrNull()
            if ((token == null) == (meshKey == null)) return null
            if (meshKey != null && workspaceId == null) return null
            WorkspaceInviteInfo(
                kind = if (token != null) {
                    WorkspaceInviteKind.SERVICE_TOKEN
                } else {
                    WorkspaceInviteKind.MESH_KEY
                },
                workspaceName = query["name"].nonBlankOrNull(),
                host = uri.host.nonBlankOrNull(),
                port = uri.port.takeIf { it >= 0 },
            )
        }.getOrNull()
    }

    private const val MAX_INVITE_LENGTH: Int = 8_192

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}

private fun String?.nonBlankOrNull(): String? = this?.trim()?.takeIf(String::isNotEmpty)
