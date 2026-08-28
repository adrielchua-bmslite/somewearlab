package com.sc3.somewear.sdk

import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
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
