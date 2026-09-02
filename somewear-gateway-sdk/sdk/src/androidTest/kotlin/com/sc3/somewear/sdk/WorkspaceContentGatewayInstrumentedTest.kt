package com.sc3.somewear.sdk

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceContentGatewayInstrumentedTest {
    @Test
    fun signedProviderExposesCatalogueWithoutBootstrapOrReflectionCrash() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val client = SomewearGateway.create(context)
            try {
                val info = client.info()
                assertTrue("Gateway info failed: $info", info is SomewearResult.Success)
                info as SomewearResult.Success
                assertTrue("workspace_file_catalog capability missing", "workspace_file_catalog" in info.value.capabilities)
                assertTrue("radio_fragment_recovery capability missing", "radio_fragment_recovery" in info.value.capabilities)
                assertTrue("radio_fragment_persistence capability missing", "radio_fragment_persistence" in info.value.capabilities)
                assertTrue("radio_receiver_ack capability missing", "radio_receiver_ack" in info.value.capabilities)
                Log.i(LOG_TAG, "info capability workspace_file_catalog=true")

                val initialized = client.initialize()
                assertNoBootstrapCrash(initialized)
                Log.i(LOG_TAG, "initialize result=${safeResult(initialized)}")
                val catalogue = client.listWorkspaceFiles(workspaceId = 1L, offset = 0, limit = 10)
                when (catalogue) {
                    is SomewearResult.Success -> {
                        assertTrue(catalogue.value.offset >= 0)
                        assertTrue(catalogue.value.files.size <= 10)
                    }
                    is SomewearResult.Failure -> {
                        assertFalse(catalogue.error.code == SomewearErrorCode.UNSUPPORTED)
                        assertNoRuntimeLinkageFailure(catalogue.error.message)
                    }
                }
                Log.i(LOG_TAG, "catalogue result=${safeResult(catalogue)}")

                context.startActivity(
                    WorkspaceContentActivity.createIntent(context, 1L)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                Thread.sleep(1_000L)
                Log.i(LOG_TAG, "content activity launch=OK")
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun variableSizeFileBatchManifestRoundTrips() {
        val manifest = FileBatchManifest(
            batchId = "batch_2026_test",
            workspaceId = 42L,
            revision = 3,
            finalRevision = true,
            expectedFileCount = 17,
            createdAtEpochMillis = 1234L,
            files = (0 until 17).map { index ->
                FileBatchEntry(
                    index = index,
                    fileId = "file-$index",
                    fileName = "photo-$index.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 1_000L + index,
                    sha256 = "%064x".format(index + 1),
                )
            },
        )

        val decoded = decodeFileBatchManifest(manifest.encodeFileBatchManifest())

        assertEquals(manifest, decoded)
        assertEquals(17, decoded.expectedFileCount)
    }

    @Test
    fun completedIncomingMessageRemainsUntilExplicitlyAcknowledged() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val client = SomewearGateway.create(context)
            val messageId = "durable-inbox-${System.currentTimeMillis()}"
            try {
                val initialized = client.initialize()
                assertNoBootstrapCrash(initialized)
                val injected = context.contentResolver.call(
                    SomewearGatewayContract.DEFAULT_URI,
                    "testInjectIncomingMessage",
                    null,
                    Bundle().apply {
                        putString(SomewearGatewayContract.Key.MESSAGE_ID, messageId)
                        putString(SomewearGatewayContract.Key.CONTENT, "{\"type\":\"RFT\"}")
                        putLong(SomewearGatewayContract.Key.WORKSPACE_ID, 42L)
                        putString(SomewearGatewayContract.Key.DELIVERED_CHANNEL, "SATELLITE")
                    },
                ) ?: error("Gateway returned no injection result")
                assertTrue(injected.getBoolean(SomewearGatewayContract.Key.OK))

                val firstPoll = client.pollIncomingMessages(0L, 500)
                assertTrue(firstPoll is SomewearResult.Success)
                firstPoll as SomewearResult.Success
                val received = firstPoll.value.single { it.messageId == messageId }

                val replayBeforeAck = client.pollIncomingMessages(0L, 500)
                assertTrue(replayBeforeAck is SomewearResult.Success)
                replayBeforeAck as SomewearResult.Success
                assertEquals(1, replayBeforeAck.value.count { it.messageId == messageId })

                val ack = client.acknowledgeIncomingMessagesThrough(received.sequence)
                assertTrue("Incoming acknowledgement failed: $ack", ack is SomewearResult.Success)

                val afterAck = client.pollIncomingMessages(0L, 500)
                assertTrue(afterAck is SomewearResult.Success)
                afterAck as SomewearResult.Success
                assertTrue(afterAck.value.none { it.messageId == messageId })
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun missingRadioFragmentIsRetainedAndCompletedLater() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val client = SomewearGateway.create(context)
            val messageId = "recovery-test-${System.currentTimeMillis()}"
            val transferId = "transfer_${System.currentTimeMillis()}"
            val payload = "{\"type\":\"CAS\",\"data\":\"${"x".repeat(2_800)}\"}"
            try {
                val first = context.contentResolver.call(
                    SomewearGatewayContract.DEFAULT_URI,
                    "testDispatchFramedRouterMessage",
                    null,
                    Bundle().apply {
                        putString(SomewearGatewayContract.Key.MESSAGE_ID, messageId)
                        putString(SomewearGatewayContract.Key.CONTENT, payload)
                        putLong(SomewearGatewayContract.Key.WORKSPACE_ID, 42L)
                        putString(SomewearGatewayContract.Key.TRANSFER_ID, transferId)
                        putInt("omit_fragment_index", 1)
                    },
                ) ?: error("Gateway returned no partial-dispatch result")
                assertTrue(first.getBoolean(SomewearGatewayContract.Key.OK))
                assertTrue(first.getInt(SomewearGatewayContract.Key.FRAGMENT_COUNT) > 1)
                assertEquals(
                    transferId,
                    first.getString(SomewearGatewayContract.Key.TRANSFER_ID),
                )

                val incomplete = client.listIncompleteMessageTransfers()
                assertTrue("Incomplete transfer query failed: $incomplete", incomplete is SomewearResult.Success)
                incomplete as SomewearResult.Success
                val transfer = incomplete.value.single { it.transferId == transferId }
                assertEquals(listOf(1), transfer.missingFragmentIndexes)

                val volatileClear = context.contentResolver.call(
                    SomewearGatewayContract.DEFAULT_URI,
                    "testClearVolatileFragmentState",
                    null,
                    null,
                ) ?: error("Gateway returned no volatile-state result")
                assertTrue(volatileClear.getBoolean(SomewearGatewayContract.Key.OK))

                val last = context.contentResolver.call(
                    SomewearGatewayContract.DEFAULT_URI,
                    "testDispatchFramedRouterMessage",
                    null,
                    Bundle().apply {
                        putString(SomewearGatewayContract.Key.MESSAGE_ID, messageId)
                        putString(SomewearGatewayContract.Key.CONTENT, payload)
                        putLong(SomewearGatewayContract.Key.WORKSPACE_ID, 42L)
                        putString(SomewearGatewayContract.Key.TRANSFER_ID, transferId)
                        putInt("only_fragment_index", 1)
                    },
                ) ?: error("Gateway returned no completion-dispatch result")
                assertTrue(last.getBoolean(SomewearGatewayContract.Key.OK))

                val delivered = client.pollIncomingMessages(0L, 500)
                assertTrue("Incoming poll failed: $delivered", delivered is SomewearResult.Success)
                delivered as SomewearResult.Success
                assertEquals(payload, delivered.value.single { it.messageId == messageId }.content)

                context.contentResolver.call(
                    SomewearGatewayContract.DEFAULT_URI,
                    "testClearVolatileFragmentState",
                    null,
                    null,
                )
                context.contentResolver.call(
                    SomewearGatewayContract.DEFAULT_URI,
                    "testDispatchFramedRouterMessage",
                    null,
                    Bundle().apply {
                        putString(SomewearGatewayContract.Key.MESSAGE_ID, messageId)
                        putString(SomewearGatewayContract.Key.CONTENT, payload)
                        putLong(SomewearGatewayContract.Key.WORKSPACE_ID, 42L)
                        putString(SomewearGatewayContract.Key.TRANSFER_ID, transferId)
                        putInt("only_fragment_index", 1)
                    },
                )
                val afterDuplicate = client.pollIncomingMessages(0L, 500)
                assertTrue(afterDuplicate is SomewearResult.Success)
                afterDuplicate as SomewearResult.Success
                assertEquals(1, afterDuplicate.value.count { it.messageId == messageId })

                val after = client.listIncompleteMessageTransfers()
                assertTrue(after is SomewearResult.Success)
                after as SomewearResult.Success
                assertTrue(after.value.none { it.transferId == transferId })
            } finally {
                client.close()
            }
        }
    }

    private fun assertNoBootstrapCrash(result: SomewearResult<*>) {
        if (result is SomewearResult.Failure) {
            assertNoRuntimeLinkageFailure(result.error.message)
        }
    }

    private fun assertNoRuntimeLinkageFailure(message: String) {
        val forbidden = listOf(
            "instanceProvider has not been initialized",
            "Realm.init",
            "NoClassDefFoundError",
            "ClassNotFoundException",
            "NoSuchMethodException",
            "IllegalAccessException",
            "getByteArray",
        )
        forbidden.forEach { value ->
            assertFalse("Unexpected runtime/linkage failure: $message", message.contains(value))
        }
    }

    private fun safeResult(result: SomewearResult<*>): String = when (result) {
        is SomewearResult.Success -> "SUCCESS"
        is SomewearResult.Failure -> "FAILURE(${result.error.code})"
    }

    private companion object {
        const val LOG_TAG = "SC3-Content-Test"
    }
}
