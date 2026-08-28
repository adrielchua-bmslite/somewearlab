package com.sc3.somewear.sdk

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.nio.file.Files
import kotlin.concurrent.thread

class ManagedFileDownloadTest {
    @Test
    fun downloadsLargeFileAndPublishesOnlyTheVerifiedResult() {
        val bytes = ByteArray(2 * 1_024 * 1_024 + 17) { index -> (index % 251).toByte() }
        withServer(bytes) { url ->
            val directory = Files.createTempDirectory("somewear-content-test").toFile()
            val destination = directory.resolve("large-image.bin")

            val written = downloadToManagedFile(url, destination, bytes.size.toLong())

            assertEquals(bytes.size.toLong(), written)
            assertArrayEquals(bytes, destination.readBytes())
            assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".part") })
        }
    }

    @Test
    fun sizeMismatchDoesNotPublishPartialFile() {
        val bytes = ByteArray(4_096) { 7 }
        withServer(bytes) { url ->
            val directory = Files.createTempDirectory("somewear-content-test").toFile()
            val destination = directory.resolve("truncated.bin")

            try {
                downloadToManagedFile(url, destination, bytes.size.toLong() + 1L)
                throw AssertionError("Expected FileSizeMismatchException")
            } catch (_: FileSizeMismatchException) {
                assertFalse(destination.exists())
                assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".part") })
            }
        }
    }

    @Test
    fun hashMismatchDoesNotPublishPartialFile() {
        val bytes = ByteArray(8_192) { index -> (index % 113).toByte() }
        withServer(bytes) { url ->
            val directory = Files.createTempDirectory("somewear-content-test").toFile()
            val destination = directory.resolve("corrupt.bin")

            try {
                downloadToManagedFile(
                    url,
                    destination,
                    bytes.size.toLong(),
                    "0".repeat(64),
                )
                throw AssertionError("Expected FileHashMismatchException")
            } catch (_: FileHashMismatchException) {
                assertFalse(destination.exists())
                assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".part") })
            }
        }
    }

    @Test
    fun matchingHashPublishesVerifiedFile() {
        val bytes = "verified batch content".toByteArray()
        val expectedHash = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        withServer(bytes) { url ->
            val destination = Files.createTempDirectory("somewear-content-test")
                .resolve("verified.bin")
                .toFile()

            downloadToManagedFile(url, destination, bytes.size.toLong(), expectedHash)

            assertArrayEquals(bytes, destination.readBytes())
        }
    }

    private fun withServer(bytes: ByteArray, block: (String) -> Unit) {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val worker = thread(name = "somewear-test-http") {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }
                socket.getOutputStream().buffered().use { output ->
                    output.write(
                        (
                            "HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\n" +
                                "Connection: close\r\n\r\n"
                        ).toByteArray(Charsets.US_ASCII),
                    )
                    output.write(bytes)
                }
            }
        }
        try {
            block("http://127.0.0.1:${server.localPort}/file")
        } finally {
            server.close()
            worker.join(5_000L)
        }
    }
}
