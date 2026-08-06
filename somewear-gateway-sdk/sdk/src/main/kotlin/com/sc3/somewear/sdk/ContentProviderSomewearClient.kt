package com.sc3.somewear.sdk

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale

internal class ContentProviderSomewearClient(
    context: Context,
    private val config: SomewearSdkConfig,
) : SomewearClient {
    private val resolver = context.contentResolver
    private val gatewayUri = Uri.parse("content://${config.authority}")

    override suspend fun info(): SomewearResult<GatewayInfo> =
        call(SomewearGatewayContract.Method.INFO).map { bundle ->
            GatewayInfo(
                apiVersion = bundle.getInt(SomewearGatewayContract.Key.API_VERSION, 1),
                description = bundle.getString(SomewearGatewayContract.Key.MESSAGE).orEmpty(),
                capabilities = bundle
                    .getStringArrayList(SomewearGatewayContract.Key.CAPABILITIES)
                    ?.toSet()
                    .orEmpty(),
            )
        }

    override suspend fun initialize(): SomewearResult<Unit> =
        call(SomewearGatewayContract.Method.INITIALIZE).unit()

    override suspend fun connectBluetooth(
        macAddress: String,
        timeoutMillis: Long?,
    ): SomewearResult<DeviceStatus> {
        val normalizedAddress = normalizeBluetoothMacAddress(macAddress)
        if (normalizedAddress == null) {
            return invalid(
                SomewearGatewayContract.Method.CONNECT_BLUETOOTH,
                "macAddress must use the form AA:BB:CC:DD:EE:FF",
            )
        }
        val accepted = call(
            SomewearGatewayContract.Method.CONNECT_BLUETOOTH,
            Bundle().apply { putString(SomewearGatewayContract.Key.ADDRESS, normalizedAddress) },
        )
        if (accepted is SomewearResult.Failure) return accepted
        return awaitConnected(timeoutMillis ?: config.operationTimeoutMillis)
    }

    override suspend fun connectUsb(timeoutMillis: Long?): SomewearResult<DeviceStatus> {
        // A non-null Bundle keeps connectUsb compatible with gateways whose
        // legacy dispatcher rejects every null-extras provider call.
        val accepted = call(SomewearGatewayContract.Method.CONNECT_USB, Bundle())
        if (accepted is SomewearResult.Failure) return accepted
        return awaitConnected(timeoutMillis ?: config.operationTimeoutMillis)
    }

    override suspend fun cancelConnection(): SomewearResult<Unit> =
        call(SomewearGatewayContract.Method.CANCEL_CONNECTION).unit()

    override suspend fun setNodeConnectionMode(mode: NodeConnectionMode): SomewearResult<Unit> =
        call(
            SomewearGatewayContract.Method.SET_CONNECTION_MODE,
            Bundle().apply {
                putString(SomewearGatewayContract.Key.CONNECTION_MODE, mode.wireValue)
            },
        ).unit()

    override suspend fun deviceStatus(): SomewearResult<DeviceStatus> =
        call(SomewearGatewayContract.Method.GET_DEVICE_STATUS).map(::parseDeviceStatus)

    override fun observeDeviceStatus(): Flow<SomewearResult<DeviceStatus>> = flow {
        while (currentCoroutineContext().isActive) {
            emit(deviceStatus())
            delay(config.pollIntervalMillis)
        }
    }

    override suspend fun disconnect(): SomewearResult<Unit> =
        call(SomewearGatewayContract.Method.DISCONNECT).unit()

    override suspend fun shutdown(): SomewearResult<Unit> =
        call(SomewearGatewayContract.Method.SHUTDOWN).unit()

    override suspend fun send(request: SendRequest): SomewearResult<SendReceipt> {
        // Deliberately no fallback to legacy sendMessage: it enables all default channels.
        return call(
            SomewearGatewayContract.Method.SEND_MESSAGE_V2,
            Bundle().apply {
                putString(SomewearGatewayContract.Key.MESSAGE_ID, request.messageId)
                putString(SomewearGatewayContract.Key.CONTENT, request.content)
                putLong(SomewearGatewayContract.Key.WORKSPACE_ID, request.workspaceId)
                request.targetUserId?.let {
                    putLong(SomewearGatewayContract.Key.TARGET_USER_ID, it)
                }
                putString(SomewearGatewayContract.Key.ROUTE_POLICY, request.routePolicy.wireValue)
                putLong(SomewearGatewayContract.Key.RADIO_TIMEOUT_MS, request.radioTimeoutMillis)
            },
        ).map { bundle ->
            SendReceipt(
                messageId = bundle.getString(SomewearGatewayContract.Key.MESSAGE_ID)
                    ?: request.messageId,
                parcelId = bundle.optionalInt(SomewearGatewayContract.Key.PARCEL_ID),
                acceptedAtEpochMillis = bundle.getLong(
                    SomewearGatewayContract.Key.ACCEPTED_AT_MS,
                    System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun deliveryStatus(messageId: String): SomewearResult<DeliveryUpdate> {
        if (messageId.isBlank()) {
            return invalid(SomewearGatewayContract.Method.GET_DELIVERY_STATUS, "messageId must not be blank")
        }
        return call(
            SomewearGatewayContract.Method.GET_DELIVERY_STATUS,
            Bundle().apply { putString(SomewearGatewayContract.Key.MESSAGE_ID, messageId) },
        ).map { parseDeliveryUpdate(it, messageId) }
    }

    override fun observeDeliveryStatus(messageId: String): Flow<DeliveryUpdate> = flow {
        while (currentCoroutineContext().isActive) {
            when (val result = deliveryStatus(messageId)) {
                is SomewearResult.Success -> {
                    emit(result.value)
                    if (result.value.status.isTerminal) return@flow
                }
                is SomewearResult.Failure -> throw SomewearSdkException(result.error)
            }
            delay(config.pollIntervalMillis)
        }
    }

    override suspend fun pollIncomingMessages(
        afterSequence: Long,
        limit: Int,
    ): SomewearResult<List<IncomingMessage>> {
        if (afterSequence < 0L || limit !in 1..500) {
            return invalid(
                SomewearGatewayContract.Method.POLL_INCOMING_MESSAGES,
                "afterSequence must be non-negative and limit must be between 1 and 500",
            )
        }
        return call(
            SomewearGatewayContract.Method.POLL_INCOMING_MESSAGES,
            Bundle().apply {
                putLong(SomewearGatewayContract.Key.AFTER_SEQUENCE, afterSequence)
                putInt(SomewearGatewayContract.Key.LIMIT, limit)
            },
        ).map { bundle ->
            bundle.bundleList(SomewearGatewayContract.Key.ITEMS).map(::parseIncomingMessage)
        }
    }

    override fun incomingMessages(afterSequence: Long): Flow<IncomingMessage> = flow {
        var cursor = afterSequence
        while (currentCoroutineContext().isActive) {
            when (val result = pollIncomingMessages(cursor)) {
                is SomewearResult.Success -> result.value.forEach { message ->
                    emit(message)
                    cursor = maxOf(cursor, message.sequence)
                }
                is SomewearResult.Failure -> throw SomewearSdkException(result.error)
            }
            delay(config.pollIntervalMillis)
        }
    }

    override suspend fun listWorkspaces(): SomewearResult<List<WorkspaceInfo>> =
        call(SomewearGatewayContract.Method.LIST_WORKSPACES).map { bundle ->
            bundle.bundleList(SomewearGatewayContract.Key.WORKSPACES).map(::parseWorkspaceInfo)
        }

    override suspend fun activeWorkspace(): SomewearResult<WorkspaceInfo?> =
        call(SomewearGatewayContract.Method.GET_ACTIVE_WORKSPACE).map { bundle ->
            if (bundle.getBoolean(SomewearGatewayContract.Key.HAS_ACTIVE_WORKSPACE, false)) {
                parseWorkspaceInfo(bundle)
            } else {
                null
            }
        }

    override suspend fun activateWorkspace(workspaceId: Long): SomewearResult<WorkspaceStatus> {
        if (workspaceId <= 0L) {
            return invalid(SomewearGatewayContract.Method.ACTIVATE_WORKSPACE, "workspaceId must be positive")
        }
        return call(
            SomewearGatewayContract.Method.ACTIVATE_WORKSPACE,
            Bundle().apply { putLong(SomewearGatewayContract.Key.WORKSPACE_ID, workspaceId) },
        ).map { bundle -> parseWorkspaceStatus(bundle, workspaceId) }
    }

    override suspend fun workspaceStatus(workspaceId: Long): SomewearResult<WorkspaceStatus> {
        if (workspaceId <= 0L) {
            return invalid(SomewearGatewayContract.Method.GET_WORKSPACE_STATUS, "workspaceId must be positive")
        }
        return call(
            SomewearGatewayContract.Method.GET_WORKSPACE_STATUS,
            Bundle().apply { putLong(SomewearGatewayContract.Key.WORKSPACE_ID, workspaceId) },
        ).map { bundle -> parseWorkspaceStatus(bundle, workspaceId) }
    }

    override suspend fun meshKeyStatus(workspaceId: Long): SomewearResult<MeshKeyStatus> {
        if (workspaceId <= 0L) {
            return invalid(SomewearGatewayContract.Method.GET_MESH_KEY_STATUS, "workspaceId must be positive")
        }
        return call(
            SomewearGatewayContract.Method.GET_MESH_KEY_STATUS,
            Bundle().apply { putLong(SomewearGatewayContract.Key.WORKSPACE_ID, workspaceId) },
        ).map { bundle ->
            MeshKeyStatus(
                workspaceId = bundle.getLong(SomewearGatewayContract.Key.WORKSPACE_ID, workspaceId),
                installed = bundle.getBoolean(SomewearGatewayContract.Key.MESH_KEY_INSTALLED, false),
                keyId = bundle.getString(SomewearGatewayContract.Key.MESH_KEY_ID),
            )
        }
    }

    override fun close(): Unit = Unit

    private suspend fun awaitConnected(timeoutMillis: Long): SomewearResult<DeviceStatus> {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        var latestStatus: DeviceStatus? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            when (val status = deviceStatus()) {
                is SomewearResult.Success -> {
                    latestStatus = status.value
                    if (status.value.connectionState.isConnected) return status
                    if (
                        status.value.operationState == OperationState.COMPLETED &&
                        !status.value.operationResult.isNullOrBlank() &&
                        !status.value.operationResult.equals("Success", ignoreCase = true)
                    ) {
                        return SomewearResult.Failure(
                            SomewearError(
                                SomewearErrorCode.CONNECTION_FAILED,
                                status.value.operationResult,
                                SomewearGatewayContract.Method.GET_DEVICE_STATUS,
                            ),
                        )
                    }
                }
                is SomewearResult.Failure -> return status
            }
            delay(config.pollIntervalMillis)
        }
        return SomewearResult.Failure(
            SomewearError(
                SomewearErrorCode.TIMEOUT,
                "Timed out waiting for the Node connection. Last status: $latestStatus",
                SomewearGatewayContract.Method.GET_DEVICE_STATUS,
            ),
        )
    }

    private suspend fun call(method: String, extras: Bundle? = null): SomewearResult<Bundle> =
        withContext(Dispatchers.IO) {
            try {
                val response = resolver.call(gatewayUri, method, null, extras)
                    ?: return@withContext SomewearResult.Failure(
                        SomewearError(
                            SomewearErrorCode.GATEWAY_NOT_INSTALLED,
                            "Gateway returned no response for $method",
                            method,
                        ),
                    )
                if (response.getBoolean(SomewearGatewayContract.Key.OK, false)) {
                    SomewearResult.Success(response)
                } else {
                    val message = response.getString(SomewearGatewayContract.Key.MESSAGE)
                        ?: "Gateway rejected $method"
                    SomewearResult.Failure(
                        SomewearError(mapErrorCode(response, message), message, method),
                    )
                }
            } catch (exception: SecurityException) {
                SomewearResult.Failure(
                    SomewearError(
                        SomewearErrorCode.PERMISSION_DENIED,
                        "SC3 is not authorized to call the Somewear gateway. Sign both apps with the same certificate.",
                        method,
                        exception,
                    ),
                )
            } catch (exception: IllegalArgumentException) {
                SomewearResult.Failure(
                    SomewearError(
                        SomewearErrorCode.GATEWAY_NOT_INSTALLED,
                        "Somewear gateway provider is not installed or visible",
                        method,
                        exception,
                    ),
                )
            } catch (exception: Exception) {
                SomewearResult.Failure(
                    SomewearError(SomewearErrorCode.INTERNAL, exception.message ?: "Gateway call failed", method, exception),
                )
            }
        }

    private fun parseDeviceStatus(bundle: Bundle): DeviceStatus = DeviceStatus(
        connectionState = enumValueOrUnknown(
            bundle.getString(SomewearGatewayContract.Key.CONNECTION_STATE),
            ConnectionState.UNKNOWN,
        ),
        operationState = enumValueOrUnknown(
            bundle.getString(SomewearGatewayContract.Key.OPERATION_STATE),
            OperationState.UNKNOWN,
        ),
        operationResult = bundle.getString(SomewearGatewayContract.Key.OPERATION_RESULT),
        localTransport = enumValueOrUnknown(
            bundle.getString(SomewearGatewayContract.Key.LOCAL_TRANSPORT),
            LocalTransport.UNKNOWN,
        ),
    )

    private fun parseDeliveryUpdate(bundle: Bundle, fallbackMessageId: String): DeliveryUpdate =
        DeliveryUpdate(
            messageId = bundle.getString(SomewearGatewayContract.Key.MESSAGE_ID) ?: fallbackMessageId,
            status = enumValueOrUnknown(
                bundle.getString(SomewearGatewayContract.Key.DELIVERY_STATUS),
                DeliveryStatus.UNKNOWN,
            ),
            deliveredChannel = enumValueOrUnknown(
                bundle.getString(SomewearGatewayContract.Key.DELIVERED_CHANNEL),
                DeviceChannel.UNKNOWN,
            ),
            errorReason = bundle.getString(SomewearGatewayContract.Key.ERROR_REASON),
            updatedAtEpochMillis = bundle.getLong(
                SomewearGatewayContract.Key.UPDATED_AT_MS,
                System.currentTimeMillis(),
            ),
        )

    private fun parseIncomingMessage(bundle: Bundle): IncomingMessage = IncomingMessage(
        sequence = bundle.getLong(SomewearGatewayContract.Key.SEQUENCE),
        messageId = bundle.getString(SomewearGatewayContract.Key.MESSAGE_ID).orEmpty(),
        content = bundle.getString(SomewearGatewayContract.Key.CONTENT).orEmpty(),
        workspaceId = bundle.getLong(SomewearGatewayContract.Key.WORKSPACE_ID),
        senderId = bundle.getString(SomewearGatewayContract.Key.SENDER_ID),
        receivedAtEpochMillis = bundle.getLong(SomewearGatewayContract.Key.RECEIVED_AT_MS),
        channel = enumValueOrUnknown(
            bundle.getString(SomewearGatewayContract.Key.DELIVERED_CHANNEL),
            DeviceChannel.UNKNOWN,
        ),
    )

    private fun parseWorkspaceInfo(bundle: Bundle): WorkspaceInfo = WorkspaceInfo(
        id = bundle.getLong(SomewearGatewayContract.Key.WORKSPACE_ID),
        name = bundle.getString(SomewearGatewayContract.Key.WORKSPACE_NAME),
        ready = bundle.getBoolean(SomewearGatewayContract.Key.WORKSPACE_READY, false),
        active = bundle.getBoolean(SomewearGatewayContract.Key.WORKSPACE_ACTIVE, false),
        member = bundle.getBoolean(SomewearGatewayContract.Key.WORKSPACE_MEMBER, false),
        meshKeyInstalled = bundle.getBoolean(SomewearGatewayContract.Key.MESH_KEY_INSTALLED, false),
    )

    private fun parseWorkspaceStatus(bundle: Bundle, fallbackId: Long): WorkspaceStatus =
        WorkspaceStatus(
            workspaceId = bundle.getLong(SomewearGatewayContract.Key.WORKSPACE_ID, fallbackId),
            name = bundle.getString(SomewearGatewayContract.Key.WORKSPACE_NAME),
            ready = bundle.getBoolean(SomewearGatewayContract.Key.WORKSPACE_READY, false),
            active = bundle.getBoolean(SomewearGatewayContract.Key.WORKSPACE_ACTIVE, false),
            member = bundle.getBoolean(SomewearGatewayContract.Key.WORKSPACE_MEMBER, false),
            meshKeyInstalled = bundle.getBoolean(
                SomewearGatewayContract.Key.MESH_KEY_INSTALLED,
                false,
            ),
        )

    private fun mapErrorCode(bundle: Bundle, message: String): SomewearErrorCode {
        val wireCode = bundle.getString(SomewearGatewayContract.Key.ERROR_CODE)
        return when (wireCode?.uppercase()) {
            "UNSUPPORTED" -> SomewearErrorCode.UNSUPPORTED
            "INVALID_REQUEST" -> SomewearErrorCode.INVALID_REQUEST
            "NOT_CONNECTED" -> SomewearErrorCode.NOT_CONNECTED
            "TIMEOUT" -> SomewearErrorCode.TIMEOUT
            "NOT_FOUND" -> SomewearErrorCode.NOT_FOUND
            "NOT_MEMBER" -> SomewearErrorCode.NOT_MEMBER
            "NO_DEVICE_FOUND" -> SomewearErrorCode.USB_NO_DEVICE
            "NO_DEVICE_DRIVER_FOUND" -> SomewearErrorCode.USB_NO_DRIVER
            "USB_PERMISSION_DENIED" -> SomewearErrorCode.USB_PERMISSION_DENIED
            "OPEN_USB_DEVICE_FAILED" -> SomewearErrorCode.USB_OPEN_DEVICE_FAILED
            "OPEN_USB_PORT_FAILED" -> SomewearErrorCode.USB_OPEN_PORT_FAILED
            "INCORRECT_DEVICE_DETECTED" -> SomewearErrorCode.USB_INCORRECT_DEVICE
            else -> if (message.contains("unknown method", ignoreCase = true)) {
                SomewearErrorCode.UNSUPPORTED
            } else {
                SomewearErrorCode.GATEWAY_REJECTED
            }
        }
    }

    private fun invalid(method: String, message: String): SomewearResult.Failure =
        SomewearResult.Failure(SomewearError(SomewearErrorCode.INVALID_REQUEST, message, method))
}

internal fun normalizeBluetoothMacAddress(value: String): String? {
    val normalized = value.trim().uppercase(Locale.US)
    return normalized.takeIf { BLUETOOTH_MAC_ADDRESS.matches(it) }
}

private val BLUETOOTH_MAC_ADDRESS = Regex("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}")

private inline fun <T, R> SomewearResult<T>.map(transform: (T) -> R): SomewearResult<R> = when (this) {
    is SomewearResult.Success -> SomewearResult.Success(transform(value))
    is SomewearResult.Failure -> this
}

private fun SomewearResult<Bundle>.unit(): SomewearResult<Unit> = map { Unit }

private inline fun <reified T : Enum<T>> enumValueOrUnknown(value: String?, unknown: T): T {
    if (value.isNullOrBlank()) return unknown
    val normalized = value
        .replace(Regex("([a-z])([A-Z])"), "$1_$2")
        .replace('-', '_')
        .uppercase()
    return enumValues<T>().firstOrNull { it.name == normalized } ?: unknown
}

private fun Bundle.optionalInt(key: String): Int? = if (containsKey(key)) getInt(key) else null

@Suppress("DEPRECATION")
private fun Bundle.bundleList(key: String): List<Bundle> =
    getParcelableArrayList<Bundle>(key)?.toList().orEmpty()
