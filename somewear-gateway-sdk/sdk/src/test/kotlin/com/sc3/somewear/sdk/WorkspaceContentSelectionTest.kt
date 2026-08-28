package com.sc3.somewear.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceContentSelectionTest {
    private val catalogue = listOf(
        workspaceFile("alpha", 10L),
        workspaceFile("bravo", 20L),
        workspaceFile("charlie", 30L),
    )

    @Test
    fun emptyRequestSelectsTheWholeCatalogue() {
        val selection = selectWorkspaceContent(catalogue, emptySet())

        assertEquals(catalogue, selection.selectedFiles)
        assertTrue(selection.missingFileIds.isEmpty())
    }

    @Test
    fun exactIdsSelectOnlyRequestedFilesAndReportUnknownIds() {
        val selection = selectWorkspaceContent(catalogue, setOf("bravo", "missing"))

        assertEquals(listOf("bravo"), selection.selectedFiles.map { it.fileId })
        assertEquals(setOf("missing"), selection.missingFileIds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun syncRequestRejectsBlankFileIds() {
        WorkspaceContentSyncRequest(workspaceId = 7L, fileIds = setOf(""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun syncRequestRejectsUnboundedRetryCount() {
        WorkspaceContentSyncRequest(workspaceId = 7L, maxDownloadAttempts = 11)
    }

    private fun workspaceFile(id: String, size: Long): WorkspaceFile = WorkspaceFile(
        fileId = id,
        fileName = "$id.json",
        mimeType = "application/json",
        sizeBytes = size,
        workspaceId = 7L,
        fileOwnerUserId = null,
        createdAtEpochMillis = null,
        uploadedAtEpochMillis = null,
    )
}
