package com.sc3.somewear.sdk

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Optional SDK-owned content page. SC3 only launches it with a workspace ID. */
public class WorkspaceContentActivity : ComponentActivity() {
    private lateinit var client: SomewearClient
    private lateinit var statusView: TextView
    private lateinit var contentList: LinearLayout
    private lateinit var refreshButton: Button
    private lateinit var syncButton: Button
    private lateinit var progress: ProgressBar
    private var workspaceId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        workspaceId = intent.getLongExtra(EXTRA_WORKSPACE_ID, 0L)
        client = SomewearGateway.create(applicationContext)
        setContentView(buildView())
        lifecycleScope.launch { initializeAndLoad() }
    }

    override fun onDestroy() {
        client.close()
        super.onDestroy()
    }

    private fun buildView(): View {
        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        root.addView(TextView(this).apply {
            text = "Somewear workspace content"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
        })
        statusView = TextView(this).apply {
            text = "Starting…"
            setPadding(0, padding / 2, 0, padding / 2)
        }
        root.addView(statusView)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        refreshButton = Button(this).apply {
            text = "Refresh"
            isEnabled = false
            setOnClickListener { lifecycleScope.launch { loadCatalogue() } }
        }
        syncButton = Button(this).apply {
            text = "Download missing"
            isEnabled = false
            setOnClickListener { syncContent(emptySet()) }
        }
        progress = ProgressBar(this).apply { visibility = View.GONE }
        controls.addView(refreshButton)
        controls.addView(syncButton)
        controls.addView(progress)
        root.addView(controls)

        contentList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(
            ScrollView(this).apply { addView(contentList) },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        return root
    }

    private suspend fun initializeAndLoad() {
        setBusy(true, "Initializing Somewear gateway…")
        when (val initialized = client.initialize()) {
            is SomewearResult.Failure -> {
                showFailure(initialized.error)
                return
            }
            is SomewearResult.Success -> Unit
        }
        if (workspaceId <= 0L) {
            when (val active = client.activeWorkspace()) {
                is SomewearResult.Failure -> {
                    showFailure(active.error)
                    return
                }
                is SomewearResult.Success -> workspaceId = active.value?.id ?: 0L
            }
        }
        if (workspaceId <= 0L) {
            setBusy(false, "No active workspace. Join or activate a workspace first.")
            return
        }
        loadCatalogue()
    }

    private suspend fun loadCatalogue() {
        setBusy(true, "Refreshing workspace $workspaceId content…")
        val files = mutableListOf<WorkspaceFile>()
        var offset = 0
        do {
            when (val page = client.listWorkspaceFiles(workspaceId, offset, PAGE_SIZE)) {
                is SomewearResult.Failure -> {
                    val cached = client.cachedWorkspaceFiles(workspaceId)
                    if (cached is SomewearResult.Success && cached.value.isNotEmpty()) {
                        renderFiles(cached.value)
                        setBusy(
                            false,
                            "Remote refresh failed; showing ${cached.value.size} cached catalogue entries. " +
                                page.error.message,
                        )
                    } else {
                        showFailure(page.error)
                    }
                    return
                }
                is SomewearResult.Success -> {
                    files += page.value.files
                    offset = page.value.nextOffset ?: -1
                }
            }
        } while (offset >= 0)
        renderFiles(files)
        setBusy(false, "${files.size} file(s) in workspace $workspaceId")
    }

    private fun renderFiles(files: List<WorkspaceFile>) {
        contentList.removeAllViews()
        if (files.isEmpty()) {
            contentList.addView(TextView(this).apply { text = "No workspace files found." })
            return
        }
        files.forEach { file ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 12, 0, 12)
            }
            val description = TextView(this).apply {
                val state = if (file.cachedUri == null) "Missing locally" else "Downloaded"
                text = buildString {
                    append(file.fileName)
                    append('\n')
                    append(Formatter.formatFileSize(this@WorkspaceContentActivity, file.sizeBytes))
                    append(" · ")
                    append(state)
                }
            }
            row.addView(
                description,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            if (file.cachedUri == null) {
                row.addView(Button(this).apply {
                    text = "Download"
                    setOnClickListener { syncContent(setOf(file.fileId)) }
                })
            }
            contentList.addView(row)
        }
    }

    private fun syncContent(fileIds: Set<String>) {
        lifecycleScope.launch {
            client.syncWorkspaceContent(
                WorkspaceContentSyncRequest(
                    workspaceId = workspaceId,
                    fileIds = fileIds,
                ),
            ).collect { event ->
                when (event) {
                    is WorkspaceContentSyncEvent.Started ->
                        setBusy(true, "Reading the workspace catalogue…")
                    is WorkspaceContentSyncEvent.CatalogueLoaded ->
                        statusView.text = "Found ${event.files.size} workspace file(s)"
                    is WorkspaceContentSyncEvent.Downloading ->
                        statusView.text = "Downloading ${event.file.fileName} " +
                            "(${event.attempt}/${event.maxAttempts})"
                    is WorkspaceContentSyncEvent.Downloaded ->
                        statusView.text = "Downloaded ${event.file.fileName}"
                    is WorkspaceContentSyncEvent.AlreadyCached ->
                        statusView.text = "Already downloaded: ${event.file.fileName}"
                    is WorkspaceContentSyncEvent.FileFailed ->
                        statusView.text = "Failed ${event.file.fileName}: ${event.error.message}"
                    is WorkspaceContentSyncEvent.NotFound ->
                        statusView.text = "Not found: ${event.fileIds.joinToString()}"
                    is WorkspaceContentSyncEvent.Failed -> showFailure(event.error)
                    is WorkspaceContentSyncEvent.Completed -> {
                        val summary = event.summary
                        statusView.text = "Downloaded ${summary.downloadedCount}, " +
                            "already present ${summary.alreadyCachedCount}, " +
                            "failed ${summary.failedCount + summary.notFoundCount}"
                        loadCatalogue()
                    }
                }
            }
        }
    }

    private fun setBusy(busy: Boolean, message: String) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        refreshButton.isEnabled = !busy && workspaceId > 0L
        syncButton.isEnabled = !busy && workspaceId > 0L
        statusView.text = message
    }

    private fun showFailure(error: SomewearError) {
        setBusy(false, "${error.code}: ${error.message}")
    }

    public companion object {
        private const val EXTRA_WORKSPACE_ID = "com.sc3.somewear.sdk.WORKSPACE_ID"
        private const val PAGE_SIZE = 100

        @JvmStatic
        public fun createIntent(context: Context, workspaceId: Long): Intent {
            require(workspaceId > 0L) { "workspaceId must be positive" }
            return Intent(context, WorkspaceContentActivity::class.java)
                .putExtra(EXTRA_WORKSPACE_ID, workspaceId)
        }
    }
}
