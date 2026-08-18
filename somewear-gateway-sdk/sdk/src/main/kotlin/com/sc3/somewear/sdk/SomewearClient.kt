package com.sc3.somewear.sdk

import kotlinx.coroutines.flow.Flow

public interface SomewearClient : AutoCloseable {
    public suspend fun info(): SomewearResult<GatewayInfo>
    public suspend fun initialize(): SomewearResult<Unit>

    public suspend fun connectBluetooth(
        macAddress: String,
        timeoutMillis: Long? = null,
    ): SomewearResult<DeviceStatus>

    public suspend fun connectUsb(timeoutMillis: Long? = null): SomewearResult<DeviceStatus>
    public suspend fun cancelConnection(): SomewearResult<Unit>
    public suspend fun setNodeConnectionMode(mode: NodeConnectionMode): SomewearResult<Unit>
    public suspend fun deviceStatus(): SomewearResult<DeviceStatus>

    /** Emits immediately, then only when the status value actually changes. */
    public fun observeDeviceConnection(): Flow<SomewearResult<DeviceStatus>>

    /** Compatibility alias for [observeDeviceConnection]. */
    public fun observeDeviceStatus(): Flow<SomewearResult<DeviceStatus>>
    public suspend fun disconnect(): SomewearResult<Unit>
    public suspend fun shutdown(): SomewearResult<Unit>

    public suspend fun hardwareSettings(): SomewearResult<HardwareSettings>
    public suspend fun setTrackingEnabled(enabled: Boolean): SomewearResult<Unit>
    public suspend fun setTrackingInterval(interval: TrackingInterval): SomewearResult<Unit>
    public suspend fun setBackhaulEnabled(enabled: Boolean): SomewearResult<Unit>
    public suspend fun setSatelliteEnabled(enabled: Boolean): SomewearResult<Unit>
    public suspend fun setMeshRadioEnabled(enabled: Boolean): SomewearResult<Unit>
    public suspend fun setRadioChannel(channel: RadioChannel): SomewearResult<Unit>
    public suspend fun setMeshTransmissionStrength(
        strength: MeshTransmissionStrength,
    ): SomewearResult<Unit>
    public suspend fun setLedLightEnabled(enabled: Boolean): SomewearResult<Unit>
    public suspend fun setVibrationFeedbackEnabled(enabled: Boolean): SomewearResult<Unit>
    public suspend fun setEnduranceModeEnabled(enabled: Boolean): SomewearResult<Unit>
    public suspend fun setDeviceButtonFunction(
        function: DeviceButtonFunction,
    ): SomewearResult<Unit>

    /** Erases the Node and normally terminates the local connection. */
    public suspend fun factoryReset(
        confirmation: FactoryResetConfirmation,
    ): SomewearResult<Unit>

    public suspend fun send(request: SendRequest): SomewearResult<SendReceipt>
    public suspend fun deliveryStatus(messageId: String): SomewearResult<DeliveryUpdate>
    public fun observeDeliveryStatus(messageId: String): Flow<DeliveryUpdate>

    public suspend fun pollIncomingMessages(
        afterSequence: Long,
        limit: Int = 50,
    ): SomewearResult<List<IncomingMessage>>

    public fun incomingMessages(afterSequence: Long = 0L): Flow<IncomingMessage>

    /** Non-secret counters showing whether the gateway is subscribed and seeing router traffic. */
    public suspend fun receiveHealth(): SomewearResult<ReceiveHealth>

    /**
     * Join and activate a workspace from the decoded contents of a Somewear QR invite.
     * The invite is consumed by the gateway and is not retained by this SDK.
     */
    public suspend fun joinWorkspace(
        inviteCode: String,
        timeoutMillis: Long? = null,
    ): SomewearResult<WorkspaceJoinResult>

    /** Forces a network/authentication refresh before returning the workspace cache. */
    public suspend fun syncWorkspaces(
        timeoutMillis: Long? = null,
    ): SomewearResult<List<WorkspaceInfo>>

    public suspend fun workspaceProvisioningStatus(): SomewearResult<WorkspaceProvisioningStatus>
    public suspend fun listWorkspaces(): SomewearResult<List<WorkspaceInfo>>
    public suspend fun activeWorkspace(): SomewearResult<WorkspaceInfo?>
    public suspend fun activateWorkspace(workspaceId: Long): SomewearResult<WorkspaceStatus>
    public suspend fun workspaceStatus(workspaceId: Long): SomewearResult<WorkspaceStatus>
    public suspend fun meshKeyStatus(workspaceId: Long): SomewearResult<MeshKeyStatus>

    /** Releases SDK resources and the bound gateway receive-lifetime anchor. */
    override fun close()
}
