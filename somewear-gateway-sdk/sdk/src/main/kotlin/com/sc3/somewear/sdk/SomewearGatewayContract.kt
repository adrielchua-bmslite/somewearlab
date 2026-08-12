package com.sc3.somewear.sdk

import android.net.Uri

/** Low-level IPC contract implemented by the separately installed gateway APK. */
public object SomewearGatewayContract {
    public const val DEFAULT_PACKAGE: String = "com.somewearlabs.swtak.plugin"
    public const val DEFAULT_AUTHORITY: String =
        "com.somewearlabs.swtak.plugin.somewear.gateway"
    public const val WORKSPACE_QR_SCANNER_ACTIVITY: String =
        "com.somewearlabs.gateway.WorkspaceQrScannerActivity"
    public const val GATEWAY_SERVICE: String =
        "com.somewearlabs.gateway.SomewearGatewayService"
    public const val PERMISSION: String =
        "com.somewearlabs.swtak.plugin.permission.SOMEWEAR_GATEWAY"

    @JvmField
    public val DEFAULT_URI: Uri = Uri.parse("content://$DEFAULT_AUTHORITY")

    public object Method {
        public const val INFO: String = "info"
        public const val INITIALIZE: String = "activate"
        public const val CONNECT_BLUETOOTH: String = "connectBle"
        public const val CONNECT_USB: String = "connectUsb"
        public const val GET_DEVICE_STATUS: String = "getDeviceStatus"
        public const val CANCEL_CONNECTION: String = "cancelConnection"
        public const val SET_CONNECTION_MODE: String = "setConnectionMode"
        public const val DISCONNECT: String = "disconnect"
        public const val SHUTDOWN: String = "shutdown"
        public const val SEND_MESSAGE_V2: String = "sendMessageV2"
        public const val GET_DELIVERY_STATUS: String = "getDeliveryStatus"
        public const val POLL_INCOMING_MESSAGES: String = "pollIncomingMessages"
        public const val GET_RECEIVE_HEALTH: String = "getReceiveHealth"
        internal const val START_RECEIVING: String = "startReceiving"
        public const val JOIN_WORKSPACE: String = "joinWorkspace"
        public const val SYNC_WORKSPACES: String = "syncWorkspaces"
        public const val GET_WORKSPACE_PROVISIONING_STATUS: String =
            "getWorkspaceProvisioningStatus"
        public const val LIST_WORKSPACES: String = "listWorkspaces"
        public const val GET_ACTIVE_WORKSPACE: String = "getActiveWorkspace"
        public const val ACTIVATE_WORKSPACE: String = "activateWorkspace"
        public const val GET_WORKSPACE_STATUS: String = "getWorkspaceStatus"
        public const val GET_MESH_KEY_STATUS: String = "getMeshKeyStatus"
    }

    public object Key {
        public const val OK: String = "ok"
        public const val MESSAGE: String = "message"
        public const val ERROR_CODE: String = "error_code"
        public const val API_VERSION: String = "api_version"
        public const val CAPABILITIES: String = "capabilities"

        public const val ADDRESS: String = "address"
        public const val CONNECTION_STATE: String = "connection_state"
        public const val OPERATION_STATE: String = "operation_state"
        public const val OPERATION_RESULT: String = "operation_result"
        public const val LOCAL_TRANSPORT: String = "local_transport"
        public const val CONNECTION_MODE: String = "connection_mode"

        public const val MESSAGE_ID: String = "message_id"
        public const val CONTENT: String = "message"
        public const val WORKSPACE_ID: String = "workspace_id"
        public const val TARGET_USER_ID: String = "target_user_id"
        public const val ROUTE_POLICY: String = "route_policy"
        public const val RADIO_TIMEOUT_MS: String = "radio_timeout_ms"
        public const val PARCEL_ID: String = "parcel_id"
        public const val ACCEPTED_AT_MS: String = "accepted_at_ms"

        public const val DELIVERY_STATUS: String = "delivery_status"
        public const val DELIVERED_CHANNEL: String = "delivered_channel"
        public const val ERROR_REASON: String = "error_reason"
        public const val UPDATED_AT_MS: String = "updated_at_ms"

        public const val AFTER_SEQUENCE: String = "after_sequence"
        public const val LIMIT: String = "limit"
        public const val ITEMS: String = "items"
        public const val SEQUENCE: String = "sequence"
        public const val SENDER_ID: String = "sender_id"
        public const val RECEIVED_AT_MS: String = "received_at_ms"
        public const val RECEIVE_SUBSCRIPTION_ACTIVE: String = "receive_subscription_active"
        public const val ROUTER_CALLBACK_COUNT: String = "router_callback_count"
        public const val INBOUND_MESSAGE_COUNT: String = "inbound_message_count"
        public const val IGNORED_INBOUND_COUNT: String = "ignored_inbound_count"
        public const val RECEIVE_ERROR_COUNT: String = "receive_error_count"
        public const val LAST_ROUTER_CALLBACK_AT_MS: String = "last_router_callback_at_ms"
        public const val LAST_INBOUND_MESSAGE_AT_MS: String = "last_inbound_message_at_ms"
        public const val LAST_RECEIVE_ERROR_AT_MS: String = "last_receive_error_at_ms"
        public const val LAST_PAYLOAD_TYPE: String = "last_payload_type"
        public const val LAST_RECEIVE_ERROR: String = "last_receive_error"
        public const val QUEUED_INCOMING_COUNT: String = "queued_incoming_count"
        public const val LATEST_SEQUENCE: String = "latest_sequence"

        public const val INVITE_CODE: String = "invite_code"
        public const val WORKSPACE_TIMEOUT_MS: String = "workspace_timeout_ms"
        public const val WORKSPACE_SYNC_COMPLETED: String = "workspace_sync_completed"
        public const val WORKSPACE_NAME: String = "workspace_name"
        public const val WORKSPACE_READY: String = "workspace_ready"
        public const val WORKSPACE_ACTIVE: String = "workspace_active"
        public const val WORKSPACE_MEMBER: String = "workspace_member"
        public const val HAS_ACTIVE_WORKSPACE: String = "has_active_workspace"
        public const val WORKSPACES: String = "workspaces"
        public const val MESH_KEY_INSTALLED: String = "mesh_key_installed"
        public const val MESH_KEY_ID: String = "mesh_key_id"
        public const val AUTHENTICATED: String = "authenticated"
        public const val AUTH_STATE: String = "auth_state"
        public const val WORKSPACE_COUNT: String = "workspace_count"
    }
}
