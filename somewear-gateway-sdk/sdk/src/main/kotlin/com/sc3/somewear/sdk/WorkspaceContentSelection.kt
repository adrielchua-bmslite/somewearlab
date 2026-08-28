package com.sc3.somewear.sdk

internal data class WorkspaceContentSelection(
    val selectedFiles: List<WorkspaceFile>,
    val missingFileIds: Set<String>,
)

internal fun selectWorkspaceContent(
    catalogue: List<WorkspaceFile>,
    requestedFileIds: Set<String>,
): WorkspaceContentSelection {
    if (requestedFileIds.isEmpty()) {
        return WorkspaceContentSelection(catalogue, emptySet())
    }
    val selected = catalogue.filter { it.fileId in requestedFileIds }
    return WorkspaceContentSelection(
        selectedFiles = selected,
        missingFileIds = requestedFileIds - selected.mapTo(mutableSetOf()) { it.fileId },
    )
}
