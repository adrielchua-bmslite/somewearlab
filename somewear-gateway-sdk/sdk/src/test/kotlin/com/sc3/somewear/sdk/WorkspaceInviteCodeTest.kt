package com.sc3.somewear.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceInviteCodeTest {
    @Test
    fun inspectsServiceTokenWithoutExposingSecret() {
        val info = WorkspaceInviteCode.inspect(
            "somewear://api.somewearlabs.com:443/workspace?name=Ops%20Team&token=secret-value",
        )

        requireNotNull(info)
        assertEquals(WorkspaceInviteKind.SERVICE_TOKEN, info.kind)
        assertEquals("Ops Team", info.workspaceName)
        assertEquals("api.somewearlabs.com", info.host)
        assertEquals(443, info.port)
        assert(!info.toString().contains("secret-value"))
    }

    @Test
    fun inspectsMeshKeyInvite() {
        val info = WorkspaceInviteCode.inspect(
            "somewear://join?workspaceId=123&meshKey=YWJjZA%3D%3D&name=Field",
        )

        requireNotNull(info)
        assertEquals(WorkspaceInviteKind.MESH_KEY, info.kind)
        assertEquals("Field", info.workspaceName)
    }

    @Test
    fun rejectsMissingAndAmbiguousCredential() {
        assertNull(WorkspaceInviteCode.inspect("somewear://join?name=NoCredential"))
        assertNull(
            WorkspaceInviteCode.inspect(
                "somewear://join?token=one&meshKey=two&workspaceId=123",
            ),
        )
    }

    @Test
    fun rejectsMeshKeyWithoutWorkspaceId() {
        assertNull(WorkspaceInviteCode.inspect("somewear://join?meshKey=YWJjZA%3D%3D"))
    }
}
