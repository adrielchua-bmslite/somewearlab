package com.sc3.somewear.sdk

import android.net.Uri
import java.util.UUID

public sealed interface SomewearResult<out T> {
    public data class Success<T>(public val value: T) : SomewearResult<T>
    public data class Failure(public val error: SomewearError) : SomewearResult<Nothing>
}

public data class SomewearError(
    val code: SomewearErrorCode,
    val message: String,
    val method: String? = null,
    val cause: Throwable? = null,
)

public enum class SomewearErrorCode {
    GATEWAY_NOT_INSTALLED,
    PERMISSION_DENIED,
    UNSUPPORTED,
    INVALID_REQUEST,
    NOT_CONNECTED,
    CONNECTION_FAILED,
    TIMEOUT,
    NOT_FOUND,
    NOT_MEMBER,
    USB_NO_DEVICE,
    USB_NO_DRIVER,
    USB_PERMISSION_DENIED,
    USB_OPEN_DEVICE_FAILED,
    USB_OPEN_PORT_FAILED,
    USB_INCORRECT_DEVICE,
    GATEWAY_REJECTED,
    INVALID_INVITE,
    NETWORK_UNAVAILABLE,
    ENVIRONMENT_MISMATCH,
    JOIN_FAILED,
    RECEIVE_FAILED,
    SEND_FAILED,
    FILE_READ_FAILED,
    FILE_LIST_FAILED,
    FILE_UPLOAD_FAILED,
    FILE_DOWNLOAD_FAILED,
    FILE_INTEGRITY_FAILED,
    FILE_CACHE_FAILED,
    FRAGMENT_RECOVERY_FAILED,
    PAYLOAD_TOO_LARGE_FOR_RADIO,
    PAYLOAD_TOO_LARGE_FOR_SATELLITE,
    MALFORMED_RESPONSE,
    INTERNAL,
}

public data class GatewayInfo(
    val apiVersion: Int,
    val description: String,
    val capabilities: Set<String>,
)

public enum class ConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTED,
    CONNECTED_AND_SCANNING,
    UNKNOWN,
    ;

    internal val isConnected: Boolean
        get() = this == CONNECTED || this == CONNECTED_AND_SCANNING
}

public enum class OperationState {
    IDLE,
    IN_PROGRESS,
    COMPLETED,
    UNKNOWN,
}

public enum class LocalTransport {
    NONE,
    BLUETOOTH,
    USB,
    UNKNOWN,
}

public data class DeviceStatus(
    val connectionState: ConnectionState,
    val operationState: OperationState,
    val operationResult: String?,
    val localTransport: LocalTransport,
)

/** A stable snapshot of non-secret Node state exposed by the retained Somewear core. */
public data class NodeTelemetry(
    val batteryPercent: Int?,
    val chargeStatus: String?,
    val powerStatus: String?,
    val activityState: String?,
    /** Somewear satellite quality from 0 (none) to 5 (best). */
    val satelliteQuality: Int?,
    /** The retained core considers quality >= 2 sendable. */
    val satelliteSendable: Boolean?,
    val firmwareVersion: String?,
    val networkVersion: String?,
    val hardwareFlavor: String?,
    val serialNumber: String?,
    val imei: String?,
    val gpsInitialFix: Boolean?,
    val trackingState: String?,
    val trackingEnabled: Boolean?,
    val lowBandwidthMultiplier: Int?,
    val wakeAtEpochMillis: Long?,
    val sampledAtEpochMillis: Long,
)

/** The latest neighbour/topology report received from the Node's mesh radio. */
public data class MeshNetworkStatus(
    val available: Boolean,
    val peerUserId: Long?,
    val nextHopUserId: Long?,
    val hopsAway: Int?,
    val signalRssi: Int?,
    val canBackhaulData: Boolean?,
    val updatedAtEpochMillis: Long?,
    val sampledAtEpochMillis: Long,
) {
    /** Human-readable receive strength using the thresholds in the retained Somewear core. */
    val signalQuality: MeshSignalQuality
        get() = MeshSignalQuality.fromRssi(signalRssi)
}

/** Receive-side Node-to-Node mesh signal quality; this is not Bluetooth RSSI. */
public enum class MeshSignalQuality {
    UNKNOWN,
    FAR,
    SOMEWHAT_CLOSE,
    CLOSE,
    ;

    public companion object {
        /** Converts the Somewear mesh RSSI/link-score value to the vendor's quality bands. */
        @JvmStatic
        public fun fromRssi(rssi: Int?): MeshSignalQuality = when {
            rssi == null || rssi < 100 -> UNKNOWN
            rssi < 148 -> FAR
            rssi < 214 -> SOMEWHAT_CLOSE
            else -> CLOSE
        }
    }
}

public enum class NodeConnectionMode(public val wireValue: String) {
    BLUETOOTH("BLUETOOTH"),
    USB("USB"),
}

public data class TrackingInterval(
    val gpsSeconds: Int,
    val sendingSeconds: Int = gpsSeconds,
) {
    init {
        require(gpsSeconds > 0) { "gpsSeconds must be positive" }
        require(sendingSeconds > 0) { "sendingSeconds must be positive" }
    }
}

public data class RadioChannel(
    val lowSpeedFrequencyHz: Int,
    val highSpeedFrequencyHz: Int,
) {
    init {
        require(lowSpeedFrequencyHz > 0) { "lowSpeedFrequencyHz must be positive" }
        require(highSpeedFrequencyHz > 0) { "highSpeedFrequencyHz must be positive" }
    }
}

public enum class MeshTransmissionStrength(public val wireValue: String) {
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH"),
    UNKNOWN("UNKNOWN"),
}

public enum class DeviceButtonFunction(public val wireValue: String) {
    NONE("NONE"),
    SATELLITE("SATELLITE"),
    TRACKING("TRACKING"),
    SENSOR("SENSOR"),
    PUSH_TO_TALK("PUSH_TO_TALK"),
    UNKNOWN("UNKNOWN"),
}

/** Required explicit token for the destructive Node factory-reset operation. */
public enum class FactoryResetConfirmation(public val wireValue: String) {
    ERASE_NODE("ERASE_NODE"),
}

/**
 * Current settings reported by the connected Node. Null means the retained
 * Somewear core has not received that value from the Node yet.
 */
public data class HardwareSettings(
    val trackingEnabled: Boolean?,
    val trackingInterval: TrackingInterval?,
    val backhaulEnabled: Boolean?,
    val satelliteEnabled: Boolean?,
    val meshRadioEnabled: Boolean?,
    val radioChannel: RadioChannel?,
    val meshTransmissionStrength: MeshTransmissionStrength?,
    val ledLightEnabled: Boolean?,
    val vibrationFeedbackEnabled: Boolean?,
    val enduranceModeEnabled: Boolean?,
    val deviceButtonFunction: DeviceButtonFunction?,
    val connectionMode: NodeConnectionMode?,
)

public enum class RoutePolicy(public val wireValue: String) {
    /** Never use satellite or cellular. */
    RADIO_ONLY("RADIO_ONLY"),

    /** Try radio, wait for a terminal result/timeout, then allow one satellite attempt. */
    RADIO_THEN_SATELLITE("RADIO_THEN_SATELLITE"),

    /** Skip radio and use satellite only. */
    SATELLITE_ONLY("SATELLITE_ONLY"),
}

public data class SendRequest(
    val workspaceId: Long,
    val content: String,
    val routePolicy: RoutePolicy = RoutePolicy.RADIO_ONLY,
    val messageId: String = UUID.randomUUID().toString(),
    val targetUserId: Long? = null,
    val radioTimeoutMillis: Long = 30_000L,
    val satelliteTimeoutMillis: Long = 300_000L,
) {
    init {
        require(workspaceId > 0L) { "workspaceId must be positive" }
        require(content.isNotBlank()) { "content must not be blank" }
        require(messageId.isNotBlank()) { "messageId must not be blank" }
        require(messageId.toByteArray(Charsets.UTF_8).size <= 4 * 1_024) {
            "messageId must not exceed 4096 UTF-8 bytes"
        }
        require(radioTimeoutMillis > 0L) { "radioTimeoutMillis must be positive" }
        require(satelliteTimeoutMillis > 0L) { "satelliteTimeoutMillis must be positive" }
    }
}

public data class SendReceipt(
    val messageId: String,
    val parcelId: Int?,
    val acceptedAtEpochMillis: Long,
    val fragmentCount: Int = 1,
    val radioFragmented: Boolean = false,
    val satelliteFallbackArmed: Boolean = false,
    val transportFragmented: Boolean = false,
    val satelliteFragmented: Boolean = false,
    /** Estimated low-speed transmissions for the original parent payload. */
    val estimatedTransmissionCount: Int = 1,
    /** True when Somewear Core, rather than SC3 framing, owns Satellite splitting/reassembly. */
    val satelliteNativeComposite: Boolean = false,
    /** True when the Node must receive server/backhaul confirmation for this route. */
    val backhaulAckRequired: Boolean = false,
    /** Present for SC3-fragmented Radio sends retained for selective retransmission. */
    val transferId: String? = null,
    /** True when the peer gateway can acknowledge successful SC3 reassembly. */
    val receiverAcknowledgementAvailable: Boolean = false,
)

public enum class DeliveryStatus {
    NONE,
    QUEUED,
    PENDING_TRANSFER,
    TRANSFERRING,
    DELIVERED,
    ERROR,
    CANCELED,
    COLLAPSED,
    UNKNOWN,
    ;

    public val isTerminal: Boolean
        get() = this == DELIVERED || this == ERROR || this == CANCELED || this == COLLAPSED
}

public enum class DeviceChannel {
    NONE,
    RADIO,
    SATELLITE,
    CELLULAR,
    UNKNOWN,
}

public data class DeliveryUpdate(
    val messageId: String,
    val status: DeliveryStatus,
    val deliveredChannel: DeviceChannel,
    val errorReason: String?,
    val updatedAtEpochMillis: Long,
    /** Stronger than Node delivery: the peer gateway rebuilt and accepted every fragment. */
    val receiverConfirmed: Boolean = false,
    val receiverConfirmedAtEpochMillis: Long? = null,
)

/** A Radio message whose SC3 transport fragments have not all arrived yet. */
public data class IncompleteMessageTransfer(
    val transferId: String,
    val workspaceId: Long,
    val senderId: String?,
    val channel: DeviceChannel,
    val expectedFragmentCount: Int,
    val receivedFragmentCount: Int,
    val missingFragmentIndexes: List<Int>,
    val firstReceivedAtEpochMillis: Long,
    val lastReceivedAtEpochMillis: Long,
    val recoveryRequestCount: Int,
    val lastRecoveryRequestAtEpochMillis: Long?,
)

public data class FragmentRecoveryReceipt(
    val transferId: String,
    val requestedFragmentCount: Int,
    val recoveryRequestCount: Int,
)

public data class FragmentRetryReceipt(
    val messageId: String,
    val transferId: String,
    val fragmentCount: Int,
)

public data class IncomingMessage(
    val sequence: Long,
    val messageId: String,
    val content: String,
    val workspaceId: Long,
    val senderId: String?,
    val receivedAtEpochMillis: Long,
    val channel: DeviceChannel,
)

/** A local URI selected by SC3 and the route used for its small metadata announcement. */
public data class FileSendRequest(
    val workspaceId: Long,
    val sourceUri: Uri,
    val fileName: String? = null,
    val mimeType: String? = null,
    val routePolicy: RoutePolicy = RoutePolicy.RADIO_ONLY,
    val messageId: String = UUID.randomUUID().toString(),
    val radioTimeoutMillis: Long = 30_000L,
    val satelliteTimeoutMillis: Long = 300_000L,
) {
    init {
        require(workspaceId > 0L) { "workspaceId must be positive" }
        require(sourceUri.toString().isNotBlank()) { "sourceUri must not be empty" }
        require(fileName == null || fileName.isNotBlank()) { "fileName must not be blank" }
        require(mimeType == null || mimeType.isNotBlank()) { "mimeType must not be blank" }
        require(messageId.isNotBlank()) { "messageId must not be blank" }
        require(radioTimeoutMillis > 0L) { "radioTimeoutMillis must be positive" }
        require(satelliteTimeoutMillis > 0L) { "satelliteTimeoutMillis must be positive" }
    }
}

public data class FileSendReceipt(
    val fileId: String,
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val sha256: String,
    /** Delivery applies to the FileMetadata announcement, not the already-uploaded bytes. */
    val metadataDelivery: SendReceipt,
)

public object FileBatchFormat {
    public const val MIME_TYPE: String = "application/vnd.sc3.file-batch+json"
    public const val VERSION: Int = 1
}

public data class FileBatchItemRequest(
    val sourceUri: Uri,
    val fileName: String? = null,
    val mimeType: String? = null,
) {
    init {
        require(sourceUri.toString().isNotBlank()) { "sourceUri must not be empty" }
        require(fileName == null || fileName.isNotBlank()) { "fileName must not be blank" }
        require(mimeType == null || mimeType.isNotBlank()) { "mimeType must not be blank" }
    }
}

/** A closed, variable-size set of workspace files announced by one manifest uploaded last. */
public data class FileBatchSendRequest(
    val workspaceId: Long,
    val files: List<FileBatchItemRequest>,
    val batchId: String = UUID.randomUUID().toString(),
    val revision: Int = 1,
    val finalRevision: Boolean = true,
    val routePolicy: RoutePolicy = RoutePolicy.RADIO_ONLY,
    val radioTimeoutMillis: Long = 30_000L,
    val satelliteTimeoutMillis: Long = 300_000L,
) {
    init {
        require(workspaceId > 0L) { "workspaceId must be positive" }
        require(files.isNotEmpty()) { "files must not be empty" }
        require(batchId.matches(Regex("[A-Za-z0-9_-]{8,64}"))) {
            "batchId must be 8..64 URL-safe characters"
        }
        require(revision > 0) { "revision must be positive" }
        require(radioTimeoutMillis > 0L) { "radioTimeoutMillis must be positive" }
        require(satelliteTimeoutMillis > 0L) { "satelliteTimeoutMillis must be positive" }
    }
}

public data class FileBatchEntry(
    val index: Int,
    val fileId: String,
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val sha256: String,
) {
    init {
        require(index >= 0) { "index must be non-negative" }
        require(fileId.isNotBlank()) { "fileId must not be blank" }
        require(fileName.isNotBlank()) { "fileName must not be blank" }
        require(sizeBytes >= 0L) { "sizeBytes must be non-negative" }
        require(sha256.matches(Regex("[0-9a-f]{64}"))) {
            "sha256 must be lowercase hexadecimal"
        }
    }
}

public data class FileBatchManifest(
    val batchId: String,
    val workspaceId: Long,
    val revision: Int,
    val finalRevision: Boolean,
    val expectedFileCount: Int,
    val createdAtEpochMillis: Long,
    val files: List<FileBatchEntry>,
) {
    init {
        require(batchId.matches(Regex("[A-Za-z0-9_-]{8,64}"))) { "Invalid batchId" }
        require(workspaceId > 0L) { "workspaceId must be positive" }
        require(revision > 0) { "revision must be positive" }
        require(expectedFileCount >= 0) { "expectedFileCount must be non-negative" }
        require(files.size == expectedFileCount) {
            "A complete manifest must contain exactly expectedFileCount entries"
        }
        require(files.map { it.index } == files.indices.toList()) {
            "Manifest file indexes must be contiguous and ordered"
        }
        require(files.map { it.fileId }.distinct().size == files.size) {
            "Manifest file IDs must be unique"
        }
    }
}

public data class FileBatchSendReceipt(
    val manifest: FileBatchManifest,
    val files: List<FileSendReceipt>,
    val manifestFile: FileSendReceipt,
)

public data class FileBatchReconciliation(
    val manifest: FileBatchManifest,
    val availableFiles: List<WorkspaceFile>,
    val missingFileIds: Set<String>,
    val cachedFileIds: Set<String>,
)

public data class IncomingFile(
    val sequence: Long,
    val messageId: String,
    val fileId: String,
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val workspaceId: Long,
    val senderId: String?,
    val fileOwnerUserId: String?,
    val createdAtEpochMillis: Long?,
    val uploadedAtEpochMillis: Long?,
    val receivedAtEpochMillis: Long,
    val channel: DeviceChannel,
) {
    public val isFileBatchManifest: Boolean
        get() = mimeType.equals(FileBatchFormat.MIME_TYPE, ignoreCase = true)
}

public data class FileDownloadReceipt(
    val fileId: String,
    val destinationUri: Uri,
    val bytesWritten: Long,
)

/** Cloud-backed file metadata returned by the authenticated workspace catalogue. */
public data class WorkspaceFile(
    val fileId: String,
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val workspaceId: Long,
    val fileOwnerUserId: String?,
    val createdAtEpochMillis: Long?,
    val uploadedAtEpochMillis: Long?,
    val isVoiceRecording: Boolean = false,
    val mediaDurationMillis: Int? = null,
    /** App-private SDK-managed copy, when one has been downloaded and size-verified. */
    val cachedUri: Uri? = null,
) {
    init {
        require(fileId.isNotBlank()) { "fileId must not be blank" }
        require(fileName.isNotBlank()) { "fileName must not be blank" }
        require(sizeBytes >= 0L) { "sizeBytes must be non-negative" }
        require(workspaceId > 0L) { "workspaceId must be positive" }
        require(mediaDurationMillis == null || mediaDurationMillis >= 0) {
            "mediaDurationMillis must be non-negative"
        }
    }

    public val isFileBatchManifest: Boolean
        get() = mimeType.equals(FileBatchFormat.MIME_TYPE, ignoreCase = true)
}

/** One bounded page; [nextOffset] is null after the final page. */
public data class WorkspaceFilePage(
    val files: List<WorkspaceFile>,
    val totalCount: Int,
    val offset: Int,
    val nextOffset: Int?,
)

/** Controls the SDK-owned catalogue comparison and app-private download cache. */
public data class WorkspaceContentSyncRequest(
    val workspaceId: Long,
    /** Empty means every catalogue entry; otherwise only these exact file IDs are requested. */
    val fileIds: Set<String> = emptySet(),
    val pageSize: Int = 100,
    val maxDownloadAttempts: Int = 3,
    val replaceCachedFiles: Boolean = false,
    /** Optional end-to-end hashes, normally supplied by a [FileBatchManifest]. */
    val expectedSha256ByFileId: Map<String, String> = emptyMap(),
) {
    init {
        require(workspaceId > 0L) { "workspaceId must be positive" }
        require(fileIds.none(String::isBlank)) { "fileIds must not contain blank values" }
        require(pageSize in 1..500) { "pageSize must be between 1 and 500" }
        require(maxDownloadAttempts in 1..10) {
            "maxDownloadAttempts must be between 1 and 10"
        }
        require(expectedSha256ByFileId.keys.none(String::isBlank)) {
            "expectedSha256ByFileId must not contain blank file IDs"
        }
        require(expectedSha256ByFileId.values.all { it.matches(Regex("[0-9a-f]{64}")) }) {
            "expectedSha256ByFileId values must be lowercase SHA-256 hexadecimal"
        }
    }
}

public data class WorkspaceContentSyncSummary(
    val workspaceId: Long,
    val discoveredCount: Int,
    val requestedCount: Int,
    val downloadedCount: Int,
    val alreadyCachedCount: Int,
    val failedCount: Int,
    val notFoundCount: Int,
)

/** Progress from [SomewearClient.syncWorkspaceContent]. */
public sealed interface WorkspaceContentSyncEvent {
    public data class Started(public val workspaceId: Long) : WorkspaceContentSyncEvent

    public data class CatalogueLoaded(
        public val workspaceId: Long,
        public val files: List<WorkspaceFile>,
    ) : WorkspaceContentSyncEvent

    public data class Downloading(
        public val file: WorkspaceFile,
        public val attempt: Int,
        public val maxAttempts: Int,
    ) : WorkspaceContentSyncEvent

    public data class Downloaded(public val file: WorkspaceFile) : WorkspaceContentSyncEvent
    public data class AlreadyCached(public val file: WorkspaceFile) : WorkspaceContentSyncEvent

    public data class FileFailed(
        public val file: WorkspaceFile,
        public val error: SomewearError,
    ) : WorkspaceContentSyncEvent

    public data class NotFound(public val fileIds: Set<String>) : WorkspaceContentSyncEvent

    public data class Failed(public val error: SomewearError) : WorkspaceContentSyncEvent
    public data class Completed(public val summary: WorkspaceContentSyncSummary) :
        WorkspaceContentSyncEvent
}

/** Safe receive-pipeline telemetry. It never contains payload or workspace secrets. */
public data class ReceiveHealth(
    val subscriptionActive: Boolean,
    val routerCallbackCount: Long,
    val inboundMessageCount: Long,
    val ignoredInboundCount: Long,
    val errorCount: Long,
    val lastRouterCallbackAtEpochMillis: Long?,
    val lastInboundMessageAtEpochMillis: Long?,
    val lastErrorAtEpochMillis: Long?,
    val lastPayloadType: String?,
    val lastError: String?,
    val queuedIncomingCount: Int,
    val latestSequence: Long,
    val inboundTransportFragmentCount: Long = 0L,
    val completedTransportMessageCount: Long = 0L,
    val invalidTransportFragmentCount: Long = 0L,
    val activeTransportReassemblies: Int = 0,
    val lastDeliveredChannel: DeviceChannel = DeviceChannel.UNKNOWN,
    val lastPayloadStatus: String? = null,
    val lastPayloadOutbound: Boolean? = null,
    val lastPayloadParcelId: Int? = null,
    val fragmentRecoveryRequestCount: Long = 0L,
    val retransmittedFragmentCount: Long = 0L,
    val receiverCompletionAckCount: Long = 0L,
    val fragmentRecoveryErrorCount: Long = 0L,
)

public data class WorkspaceInfo(
    val id: Long,
    val name: String?,
    val ready: Boolean,
    val active: Boolean = false,
    val member: Boolean = false,
    val meshKeyInstalled: Boolean = false,
)

public data class WorkspaceJoinResult(
    val workspace: WorkspaceStatus,
    /** False means the invite was accepted but the post-join refresh timed out or was offline. */
    val syncCompleted: Boolean,
)

public data class WorkspaceProvisioningStatus(
    val authenticated: Boolean,
    val authState: String,
    val workspaceCount: Int,
    val hasActiveWorkspace: Boolean,
)

public data class WorkspaceStatus(
    val workspaceId: Long,
    val name: String?,
    val ready: Boolean,
    val active: Boolean = false,
    val member: Boolean = false,
    val meshKeyInstalled: Boolean = false,
)

public data class MeshKeyStatus(
    val workspaceId: Long,
    val installed: Boolean,
    /** Identifier only. The SDK never exposes mesh-key material. */
    val keyId: String?,
)

public data class SomewearSdkConfig(
    val authority: String = SomewearGatewayContract.DEFAULT_AUTHORITY,
    val pollIntervalMillis: Long = 500L,
    val operationTimeoutMillis: Long = 30_000L,
) {
    init {
        require(authority.isNotBlank()) { "authority must not be blank" }
        require(pollIntervalMillis > 0L) { "pollIntervalMillis must be positive" }
        require(operationTimeoutMillis > 0L) { "operationTimeoutMillis must be positive" }
    }
}

public class SomewearSdkException(public val error: SomewearError) :
    RuntimeException(error.message, error.cause)
