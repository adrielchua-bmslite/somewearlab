package com.sc3.somewear.sdk

import java.util.UUID

/**
 * A compact, versioned application envelope suitable for a Somewear MessagePayload.
 * [bodyJson] must contain valid JSON. It is inserted without additional quoting.
 */
public data class Sc3MessageEnvelope(
    val type: String,
    val sender: String,
    val bodyJson: String,
    val target: String = "workspace",
    val id: String = UUID.randomUUID().toString(),
    val sentAtEpochMillis: Long = System.currentTimeMillis(),
    val version: Int = 1,
) {
    init {
        require(type.isNotBlank()) { "type must not be blank" }
        require(sender.isNotBlank()) { "sender must not be blank" }
        require(bodyJson.isNotBlank()) { "bodyJson must not be blank" }
        require(target.isNotBlank()) { "target must not be blank" }
        require(id.isNotBlank()) { "id must not be blank" }
        require(version > 0) { "version must be positive" }
    }

    public fun encode(): String = buildString {
        append('{')
        append("\"v\":").append(version)
        append(",\"id\":").append(id.asJsonString())
        append(",\"type\":").append(type.asJsonString())
        append(",\"sentAt\":").append(sentAtEpochMillis)
        append(",\"sender\":").append(sender.asJsonString())
        append(",\"target\":").append(target.asJsonString())
        append(",\"body\":").append(bodyJson.trim())
        append('}')
    }
}

private fun String.asJsonString(): String = buildString(length + 2) {
    append('"')
    for (character in this@asJsonString) {
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character.code < 0x20) {
                append("\\u").append(character.code.toString(16).padStart(4, '0'))
            } else {
                append(character)
            }
        }
    }
    append('"')
}
