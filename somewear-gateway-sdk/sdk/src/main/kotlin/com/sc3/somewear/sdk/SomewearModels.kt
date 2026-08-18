package com.sc3.somewear.sdk

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
) {
    init {
        require(workspaceId > 0L) { "workspaceId must be positive" }
        require(content.isNotBlank()) { "content must not be blank" }
        require(messageId.isNotBlank()) { "messageId must not be blank" }
        require(radioTimeoutMillis > 0L) { "radioTimeoutMillis must be positive" }
    }
}

public data class SendReceipt(
    val messageId: String,
    val parcelId: Int?,
    val acceptedAtEpochMillis: Long,
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
