package com.sc3.somewear.sdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

public sealed interface WorkspaceQrScanResult {
    public data class Success(val inviteCode: String) : WorkspaceQrScanResult
    public data class Failure(val message: String) : WorkspaceQrScanResult
    public data object Cancelled : WorkspaceQrScanResult
}

/** Activity Result contract used by SC3 to launch the SDK-owned QR scanner. */
public class WorkspaceQrScanContract : ActivityResultContract<Unit, WorkspaceQrScanResult>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(context, WorkspaceQrScannerActivity::class.java)

    override fun parseResult(resultCode: Int, intent: Intent?): WorkspaceQrScanResult {
        if (resultCode != Activity.RESULT_OK) {
            val error = intent?.getStringExtra(EXTRA_ERROR)
            return if (error.isNullOrBlank()) {
                WorkspaceQrScanResult.Cancelled
            } else {
                WorkspaceQrScanResult.Failure(error)
            }
        }
        val invite = intent?.getStringExtra(EXTRA_INVITE_CODE)
        return if (invite != null && WorkspaceInviteCode.inspect(invite) != null) {
            WorkspaceQrScanResult.Success(invite)
        } else {
            WorkspaceQrScanResult.Failure("Scanner returned an invalid Somewear workspace invite")
        }
    }

    internal companion object {
        const val EXTRA_INVITE_CODE: String = "com.sc3.somewear.sdk.INVITE_CODE"
        const val EXTRA_ERROR: String = "com.sc3.somewear.sdk.SCAN_ERROR"
    }
}
