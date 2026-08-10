package com.sc3.somewear.sdk

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle

/**
 * Dependency-free proxy that opens the signature-protected scanner hosted by
 * the installed gateway. SC3 no longer packages CameraX or ML Kit.
 */
public class WorkspaceQrScannerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val scannerIntent = Intent().setComponent(
            ComponentName(
                SomewearGatewayContract.DEFAULT_PACKAGE,
                SomewearGatewayContract.WORKSPACE_QR_SCANNER_ACTIVITY,
            ),
        )
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(scannerIntent, SCANNER_REQUEST)
        } catch (_: ActivityNotFoundException) {
            finishWithError(
                "The installed Somewear gateway does not expose the workspace QR scanner. " +
                    "Install the current five-split gateway handover set.",
            )
        } catch (_: SecurityException) {
            finishWithError(
                "SC3 is not authorized to open the Somewear gateway scanner. " +
                    "Sign SC3 and all gateway splits with the same certificate.",
            )
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != SCANNER_REQUEST) return
        setResult(resultCode, data)
        finish()
    }

    private fun finishWithError(message: String) {
        setResult(
            RESULT_CANCELED,
            Intent().putExtra(WorkspaceQrScanContract.EXTRA_ERROR, message),
        )
        finish()
    }

    private companion object {
        const val SCANNER_REQUEST: Int = 9138
    }
}
