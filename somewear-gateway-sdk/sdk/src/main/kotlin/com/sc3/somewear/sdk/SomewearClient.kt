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
    public fun observeDeviceStatus(): Flow<SomewearResult<DeviceStatus>>
    public suspend fun disconnect(): SomewearResult<Unit>
    public suspend fun shutdown(): SomewearResult<Unit>

    public suspend fun send(request: SendRequest): SomewearResult<SendReceipt>
    public suspend fun deliveryStatus(messageId: String): SomewearResult<DeliveryUpdate>
    public fun observeDeliveryStatus(messageId: String): Flow<DeliveryUpdate>

    public suspend fun pollIncomingMessages(
        afterSequence: Long,
        limit: Int = 50,
    ): SomewearResult<List<IncomingMessage>>

    public fun incomingMessages(afterSequence: Long = 0L): Flow<IncomingMessage>

    public suspend fun listWorkspaces(): SomewearResult<List<WorkspaceInfo>>
    public suspend fun workspaceStatus(workspaceId: Long): SomewearResult<WorkspaceStatus>
    public suspend fun meshKeyStatus(workspaceId: Long): SomewearResult<MeshKeyStatus>

    /** Releases SDK-side resources. This does not stop the gateway process. */
    override fun close()
}
