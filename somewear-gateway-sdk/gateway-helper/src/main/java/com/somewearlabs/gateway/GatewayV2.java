package com.somewearlabs.gateway;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * API-v2 adapter injected into the standalone gateway APK.
 *
 * All Somewear references are reflective so this helper can be compiled without
 * redistributing or linking a proprietary Somewear SDK at build time.
 */
public final class GatewayV2 {
    private static final String LOG_TAG = "SC3-Somewear-Gateway";
    private static final String STATE_PREFERENCES = "sc3_somewear_gateway_state";
    private static final String LAST_RADIO_FRAGMENT_SECOND = "last_radio_fragment_second";
    private static final Object LOCK = new Object();
    private static final int MAX_INCOMING_MESSAGES = 2_000;
    private static final int RADIO_MTU_BYTES = 340;
    private static final long DEFAULT_WORKSPACE_TIMEOUT_MS = 45_000L;
    private static final long MAX_WORKSPACE_TIMEOUT_MS = 120_000L;
    private static final long DEFAULT_SETTINGS_TIMEOUT_MS = 30_000L;
    private static final int SETTING_TRACKING_ENABLED = 0;
    private static final int SETTING_TRACKING_FREQUENCY = 1;
    private static final int SETTING_NO_SLEEP = 4;
    private static final int SETTING_CONNECTION_MODE = 8;
    private static final int SETTING_LED_DISABLED = 12;
    private static final int SETTING_SATELLITE_DISABLED = 13;
    private static final int SETTING_BUTTON_FUNCTION = 15;
    private static final int SETTING_LOW_SPEED_FREQUENCY = 16;
    private static final int SETTING_HIGH_SPEED_FREQUENCY = 17;
    private static final int SETTING_RADIO_POWER_MODE = 18;
    private static final int SETTING_BACKHAUL_ENABLED = 20;
    private static final int SETTING_HAPTICS_DISABLED = 21;
    private static final int SETTING_RADIO_MODE = 25;
    private static final int HARDWARE_SETTING_COUNT = 32;
    private static final List<Bundle> INCOMING = new ArrayList<>();
    private static final Map<String, Bundle> DELIVERY = new LinkedHashMap<>();
    private static final RadioMessageReassembler RADIO_REASSEMBLER =
            new RadioMessageReassembler();
    private static final FragmentDeliveryTracker DELIVERY_TRACKER =
            new FragmentDeliveryTracker();

    private static Context appContext;
    private static Object payloadSubscription;
    private static long nextSequence = 1L;
    private static long routerCallbackCount;
    private static long inboundMessageCount;
    private static long ignoredInboundCount;
    private static long receiveErrorCount;
    private static long lastRouterCallbackAt;
    private static long lastInboundMessageAt;
    private static long lastReceiveErrorAt;
    private static long inboundRadioFragmentCount;
    private static long completedRadioMessageCount;
    private static long invalidRadioFragmentCount;
    private static String lastPayloadType;
    private static String lastReceiveError;
    private static Object compositePackager;

    private GatewayV2() {}

    public static void initialize(Context context) {
        appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
    }

    /** Returns null when the legacy provider should handle the method. */
    public static Bundle call(String method, Bundle extras) {
        try {
            if ("info".equals(method)) return info();
            if ("sendMessageV2".equals(method)) return sendMessage(extras);
            if ("getDeliveryStatus".equals(method)) return deliveryStatus(extras);
            if ("pollIncomingMessages".equals(method)) return pollIncoming(extras);
            if ("getReceiveHealth".equals(method)) return receiveHealth();
            if ("startReceiving".equals(method)) return startReceivingResult();
            if ("testInjectIncomingMessage".equals(method)) return injectIncoming(extras);
            if ("testDispatchRouterMessage".equals(method)) return dispatchTestRouterMessage(extras);
            if ("testDispatchFramedRouterMessage".equals(method)) {
                return dispatchTestFramedRouterMessage(extras);
            }
            if ("testInspectHardwareSettingsPatch".equals(method)) {
                return inspectTestHardwareSettingsPatch();
            }
            if ("connectUsb".equals(method)
                    || "connectUSB".equals(method)
                    || "connect_usb".equals(method)) return connectUsb();
            if ("setConnectionMode".equals(method)) return setConnectionMode(extras);
            if ("getHardwareSettings".equals(method)) return hardwareSettings();
            if ("setTrackingEnabled".equals(method)) {
                return updateBooleanSetting(extras, SETTING_TRACKING_ENABLED, false);
            }
            if ("setTrackingInterval".equals(method)) return setTrackingInterval(extras);
            if ("setBackhaulEnabled".equals(method)) {
                return updateBooleanSetting(extras, SETTING_BACKHAUL_ENABLED, false);
            }
            if ("setSatelliteEnabled".equals(method)) {
                return updateBooleanSetting(extras, SETTING_SATELLITE_DISABLED, true);
            }
            if ("setMeshRadioEnabled".equals(method)) return setMeshRadioEnabled(extras);
            if ("setRadioChannel".equals(method)) return setRadioChannel(extras);
            if ("setMeshTransmissionStrength".equals(method)) {
                return setMeshTransmissionStrength(extras);
            }
            if ("setLedLightEnabled".equals(method)) {
                return updateBooleanSetting(extras, SETTING_LED_DISABLED, true);
            }
            if ("setVibrationFeedbackEnabled".equals(method)) {
                return updateBooleanSetting(extras, SETTING_HAPTICS_DISABLED, true);
            }
            if ("setEnduranceModeEnabled".equals(method)) {
                return updateBooleanSetting(extras, SETTING_NO_SLEEP, true);
            }
            if ("setDeviceButtonFunction".equals(method)) {
                return setDeviceButtonFunction(extras);
            }
            if ("factoryReset".equals(method)) return factoryReset(extras);
            if ("shutdown".equals(method)) return shutdown();
            if ("listWorkspaces".equals(method)) return listWorkspaces();
            if ("syncWorkspaces".equals(method)) return syncWorkspaces(extras);
            if ("joinWorkspace".equals(method)) {
                try {
                    return joinWorkspace(extras);
                } catch (Throwable throwable) {
                    // Never reflect an exception message that could contain invite material.
                    return error(
                            "JOIN_FAILED",
                            "Somewear workspace join failed ("
                                    + throwable.getClass().getSimpleName() + ")"
                    );
                }
            }
            if ("getWorkspaceProvisioningStatus".equals(method)) {
                return workspaceProvisioningStatus();
            }
            if ("getActiveWorkspace".equals(method)) return getActiveWorkspace();
            if ("activateWorkspace".equals(method)) return activateWorkspace(extras);
            if ("getWorkspaceStatus".equals(method)) return workspaceStatus(extras);
            if ("getMeshKeyStatus".equals(method)) return meshKeyStatus(extras);
            // The vendor provider's legacy raw branch contains a method reference that is
            // unsafe on some Android runtimes. API v2 never exposes these methods, so stop
            // them here before the hand-written provider dispatcher can reach that branch.
            if ("sendRaw".equals(method)
                    || "sendRawToWorkspace".equals(method)
                    || "sendRawWithParcel".equals(method)) {
                return error(
                        "UNSUPPORTED",
                        "Legacy raw payload methods are not exposed by the SC3 gateway"
                );
            }
            if (isLegacyProviderMethod(method)) return null;
            return error("UNSUPPORTED", "Unknown gateway method: " + method);
        } catch (Throwable throwable) {
            return error("INTERNAL", rootMessage(throwable));
        }
    }

    private static boolean isLegacyProviderMethod(String method) {
        return "activate".equals(method)
                || "getDeviceStatus".equals(method)
                || "cancelConnection".equals(method)
                || "disconnect".equals(method)
                || "connectBle".equals(method)
                || "sendMessage".equals(method);
    }

    private static Bundle info() {
        Bundle result = ok("Somewear standalone gateway API v2");
        result.putInt("api_version", 2);
        ArrayList<String> capabilities = new ArrayList<>();
        Collections.addAll(
                capabilities,
                "initialize",
                "bluetooth",
                "usb_connect",
                "radio_only",
                "radio_fragmentation",
                "radio_fragment_dedup",
                "satellite_only",
                "delivery_status",
                "incoming_messages",
                "receive_health",
                "hardware_settings",
                "tracking_settings",
                "network_settings",
                "device_settings",
                "factory_reset",
                "workspace_join",
                "workspace_sync",
                "workspace_qr_invite",
                "workspace_qr_scanner",
                "workspace_provisioning_status",
                "workspace_list",
                "workspace_selection",
                "workspace_status",
                "mesh_key_status",
                "test_injection"
        );
        result.putStringArrayList("capabilities", capabilities);
        return result;
    }

    /**
     * Forces the retained Somewear workspace repository to synchronize with the
     * service. Unlike listWorkspaces(), this performs network/authentication work.
     */
    private static Bundle syncWorkspaces(Bundle extras) throws Exception {
        ensureCoreStarted();
        final long timeout = workspaceTimeout(extras);
        final Object refreshed;
        try {
            refreshed = invokeSuspend(genericUserSource(), "refreshWorkspaces", timeout);
        } catch (SuspendTimeoutException timeoutException) {
            return error("TIMEOUT", "Timed out while synchronizing Somewear workspaces");
        }
        if (Boolean.FALSE.equals(refreshed)) {
            return error(
                    "NETWORK_UNAVAILABLE",
                    "Somewear could not synchronize workspaces; check internet access and authentication"
            );
        }
        Bundle result = listWorkspaces();
        result.putString("message", "Somewear workspaces synchronized");
        result.putBoolean("workspace_sync_completed", true);
        return result;
    }

    /**
     * Joins the workspace encoded by a Somewear QR/deep-link invite. The invite
     * token is consumed in memory and is never returned, persisted, or logged.
     */
    private static Bundle joinWorkspace(Bundle extras) throws Exception {
        if (extras == null) return error("INVALID_REQUEST", "Missing extras Bundle");
        String rawInvite = extras.getString("invite_code");
        if (rawInvite == null || rawInvite.trim().isEmpty()) {
            return error("INVALID_REQUEST", "invite_code must not be blank");
        }

        final InviteParts invite;
        try {
            invite = InviteParts.parse(rawInvite);
        } catch (IllegalArgumentException exception) {
            return error("INVALID_INVITE", exception.getMessage());
        }

        ensureCoreStarted();
        if (invite.token != null) {
            Bundle mismatch = environmentMismatch(invite);
            if (mismatch != null) return mismatch;
        }

        long timeout = workspaceTimeout(extras);
        Object repository = null;
        try {
            repository = workspaceRepository();
            final Object joinResult;
            try {
                if (invite.token != null) {
                    joinResult = invokeSuspend(
                            repository,
                            "joinWorkspaceByInviteToken",
                            timeout,
                            invite.token
                    );
                } else {
                    final byte[] meshKey;
                    try {
                        meshKey = Base64.getDecoder().decode(invite.meshKey);
                    } catch (IllegalArgumentException exception) {
                        return error("INVALID_INVITE", "The workspace mesh key is not valid Base64");
                    }
                    joinResult = invokeSuspend(
                            repository,
                            "createWorkspaceFromMeshKey",
                            timeout,
                            invite.workspaceId,
                            invite.workspaceName == null ? "Somewear Workspace" : invite.workspaceName,
                            meshKey
                    );
                }
            } catch (SuspendTimeoutException timeoutException) {
                return error("TIMEOUT", "Timed out while joining the Somewear workspace");
            }

            String resultName = joinResult == null
                    ? ""
                    : joinResult.getClass().getSimpleName();
            if ("NoConnection".equals(resultName)) {
                return error(
                        "NETWORK_UNAVAILABLE",
                        "Somewear could not reach the workspace service"
                );
            }
            if ("InvalidInput".equals(resultName)) {
                return error(
                        "INVALID_INVITE",
                        "The workspace invite is invalid, expired, or no longer grants access"
                );
            }
            if (!"Success".equals(resultName)) {
                return error("JOIN_FAILED", "Somewear did not accept the workspace invite");
            }

            Object response = invokeNoArgs(joinResult, "getResponse");
            String workspaceId = stringOrNull(invokeNoArgs(response, "getId"));
            if (workspaceId == null) {
                return error("MALFORMED_RESPONSE", "Somewear returned a workspace without an ID");
            }
            final long numericWorkspaceId;
            try {
                numericWorkspaceId = Long.parseLong(workspaceId);
            } catch (NumberFormatException exception) {
                return error(
                        "UNSUPPORTED",
                        "Joined workspace ID is not numeric and cannot be used by this gateway"
                );
            }
            if (numericWorkspaceId <= 0L) {
                return error("MALFORMED_RESPONSE", "Somewear returned an invalid workspace ID");
            }

            // Match the retained ATAK post-join behavior without opening ATAK UI:
            // make the response active, then refresh the shared workspace cache.
            invoke(userContextUtil(), "activateWorkspace", response);
            boolean syncCompleted = false;
            try {
                Object refreshed = invokeSuspend(genericUserSource(), "refreshWorkspaces", timeout);
                syncCompleted = !Boolean.FALSE.equals(refreshed);
            } catch (SuspendTimeoutException ignored) {
                // Joining already succeeded and the repository inserted the response locally.
            }

            Object workspace = findWorkspace(workspaceId);
            Bundle result = workspace == null
                    ? workspaceBundleFromResponse(response, numericWorkspaceId)
                    : workspaceBundle(workspace, activeWorkspaceId());
            if (result == null) {
                return error("MALFORMED_RESPONSE", "Joined workspace could not be read from cache");
            }
            result.putBoolean("ok", true);
            result.putString("message", "Workspace joined and activated");
            result.putBoolean("workspace_sync_completed", syncCompleted);
            return result;
        } finally {
            if (repository != null) {
                try {
                    invokeNoArgs(repository, "close");
                } catch (Throwable ignored) {
                    // Never replace the real join result with a cleanup failure.
                }
            }
        }
    }

    private static Bundle workspaceProvisioningStatus() throws Exception {
        ensureCoreStarted();
        Object userContext = invokeNoArgs(invokeNoArgs(userContextUtil(), "getUserContext"), "getValue");
        String uid = stringOrNull(invokeNoArgs(userContext, "getUid"));
        Object authState = invokeNoArgs(userContext, "getAuthState");
        String activeId = activeWorkspaceId();
        Bundle result = ok("Somewear workspace provisioning status");
        result.putBoolean("authenticated", uid != null);
        result.putString(
                "auth_state",
                authState == null ? "Unknown" : authState.getClass().getSimpleName()
        );
        result.putInt("workspace_count", workspaceList().size());
        result.putBoolean("has_active_workspace", activeId != null);
        if (activeId != null) result.putString("active_workspace_id_text", activeId);
        return result;
    }

    private static Bundle environmentMismatch(InviteParts invite) throws Exception {
        Object environment = invokeNoArgs(environmentUtil(), "getEnvironmentInfo");
        String currentHost = String.valueOf(invokeNoArgs(environment, "getGrpcHost"));
        int currentPort = ((Number) invokeNoArgs(environment, "getGrpcPort")).intValue();
        boolean currentPlaintext = Boolean.TRUE.equals(invokeNoArgs(environment, "getPlaintext"));

        String targetHost = invite.host == null ? currentHost : invite.host;
        int targetPort = invite.port < 0 ? currentPort : invite.port;
        boolean targetPlaintext = invite.plaintext;
        if (currentHost.equals(targetHost)
                && currentPort == targetPort
                && currentPlaintext == targetPlaintext) {
            return null;
        }
        return error(
                "ENVIRONMENT_MISMATCH",
                "Invite targets " + targetHost + ":" + targetPort
                        + " but the gateway is configured for " + currentHost + ":" + currentPort
        );
    }

    private static Bundle workspaceBundleFromResponse(Object response, long workspaceId)
            throws Exception {
        Bundle result = new Bundle();
        result.putLong("workspace_id", workspaceId);
        result.putString("workspace_name", stringOrNull(invokeNoArgs(response, "getName")));
        result.putBoolean("workspace_member", true);
        result.putBoolean("workspace_active", true);
        result.putBoolean("mesh_key_installed", false);
        result.putBoolean("workspace_ready", false);
        return result;
    }

    private static Bundle listWorkspaces() throws Exception {
        ensureCoreStarted();
        String activeId = activeWorkspaceId();
        ArrayList<Bundle> items = new ArrayList<>();
        int skippedNonNumeric = 0;
        for (Object workspace : workspaceList()) {
            Bundle item = workspaceBundle(workspace, activeId);
            if (item == null) {
                skippedNonNumeric++;
            } else {
                items.add(item);
            }
        }
        Bundle result = ok("Somewear workspace cache");
        result.putParcelableArrayList("workspaces", items);
        result.putInt("skipped_non_numeric_workspaces", skippedNonNumeric);
        return result;
    }

    private static Bundle getActiveWorkspace() throws Exception {
        ensureCoreStarted();
        String activeId = activeWorkspaceId();
        Bundle result = ok(activeId == null ? "No active workspace" : "Active workspace");
        if (activeId == null) {
            result.putBoolean("has_active_workspace", false);
            return result;
        }
        Object workspace = findWorkspace(activeId);
        if (workspace == null) {
            return error("NOT_FOUND", "Active workspace is not present in the synchronized cache");
        }
        Bundle workspaceResult = workspaceBundle(workspace, activeId);
        if (workspaceResult == null) {
            return error("UNSUPPORTED", "Active workspace ID is not numeric: " + activeId);
        }
        workspaceResult.putBoolean("ok", true);
        workspaceResult.putBoolean("has_active_workspace", true);
        workspaceResult.putString("message", "Active workspace");
        return workspaceResult;
    }

    private static Bundle activateWorkspace(Bundle extras) throws Exception {
        Long workspaceId = requestedWorkspaceId(extras);
        if (workspaceId == null) {
            return error("INVALID_REQUEST", "workspace_id must be a positive Long");
        }
        ensureCoreStarted();
        Object workspace = findWorkspace(String.valueOf(workspaceId));
        if (workspace == null) {
            return error("NOT_FOUND", "Workspace is not present in the synchronized cache: " + workspaceId);
        }
        if (!workspaceMember(workspace)) {
            return error("NOT_MEMBER", "The signed-in Somewear user is not a member of workspace " + workspaceId);
        }

        Object userSource = genericUserSource();
        invoke(userSource, "activateWorkspace", workspace);
        String activeId = activeWorkspaceId();
        if (!String.valueOf(workspaceId).equals(activeId)) {
            return error("INTERNAL", "Somewear Core did not activate workspace " + workspaceId);
        }
        Bundle result = workspaceBundle(workspace, activeId);
        if (result == null) {
            return error("UNSUPPORTED", "Workspace ID is not numeric: " + workspaceId);
        }
        result.putBoolean("ok", true);
        result.putString("message", "Workspace activated");
        return result;
    }

    private static Bundle workspaceStatus(Bundle extras) throws Exception {
        Long workspaceId = requestedWorkspaceId(extras);
        if (workspaceId == null) {
            return error("INVALID_REQUEST", "workspace_id must be a positive Long");
        }
        ensureCoreStarted();
        Object workspace = findWorkspace(String.valueOf(workspaceId));
        if (workspace == null) {
            return error("NOT_FOUND", "Workspace is not present in the synchronized cache: " + workspaceId);
        }
        Bundle result = workspaceBundle(workspace, activeWorkspaceId());
        if (result == null) {
            return error("UNSUPPORTED", "Workspace ID is not numeric: " + workspaceId);
        }
        result.putBoolean("ok", true);
        result.putString("message", "Workspace status");
        return result;
    }

    private static Bundle meshKeyStatus(Bundle extras) throws Exception {
        Long workspaceId = requestedWorkspaceId(extras);
        if (workspaceId == null) {
            return error("INVALID_REQUEST", "workspace_id must be a positive Long");
        }
        ensureCoreStarted();
        Object workspace = findWorkspace(String.valueOf(workspaceId));
        if (workspace == null) {
            return error("NOT_FOUND", "Workspace is not present in the synchronized cache: " + workspaceId);
        }
        byte[] meshKey = workspaceMeshKey(workspace);
        Bundle result = ok("Workspace mesh-key status");
        result.putLong("workspace_id", workspaceId);
        result.putBoolean("mesh_key_installed", meshKey != null && meshKey.length > 0);
        if (meshKey != null && meshKey.length > 0) {
            result.putString("mesh_key_id", meshKeyFingerprint(meshKey));
        }
        return result;
    }

    private static Bundle workspaceBundle(Object workspace, String activeId) throws Exception {
        String id = String.valueOf(invokeNoArgs(workspace, "getWorkspaceId"));
        final long numericId;
        try {
            numericId = Long.parseLong(id);
        } catch (NumberFormatException ignored) {
            return null;
        }
        if (numericId <= 0L) return null;
        boolean member = workspaceMember(workspace);
        byte[] meshKey = workspaceMeshKey(workspace);
        boolean keyInstalled = meshKey != null && meshKey.length > 0;
        Bundle result = new Bundle();
        result.putLong("workspace_id", numericId);
        Object name = invokeNoArgs(workspace, "getName");
        result.putString("workspace_name", name == null ? null : String.valueOf(name));
        result.putBoolean("workspace_member", member);
        result.putBoolean("workspace_active", id.equals(activeId));
        result.putBoolean("mesh_key_installed", keyInstalled);
        result.putBoolean("workspace_ready", member && keyInstalled);
        return result;
    }

    private static Long requestedWorkspaceId(Bundle extras) {
        if (extras == null || !extras.containsKey("workspace_id")) return null;
        long workspaceId = extras.getLong("workspace_id", 0L);
        return workspaceId > 0L ? workspaceId : null;
    }

    private static List<?> workspaceList() throws Exception {
        Object list = invokeNoArgs(workspaceCache(), "getWorkspaceList");
        return list instanceof List ? (List<?>) list : Collections.emptyList();
    }

    private static Object findWorkspace(String workspaceId) throws Exception {
        return invoke(workspaceCache(), "findByWorkspaceId", workspaceId);
    }

    private static Object workspaceCache() throws Exception {
        Class<?> type = Class.forName(
                "com.somewearlabs.somewearshared.workspace.SharedWorkspaceCache"
        );
        Object singleton = type.getField("INSTANCE").get(null);
        return invokeNoArgs(singleton, "getInstance");
    }

    private static Object genericUserSource() throws Exception {
        Class<?> type = Class.forName("com.somewearlabs.somewearshared.user.GenericUserSource");
        Object companion = type.getField("Companion").get(null);
        return invokeNoArgs(companion, "getInstance");
    }

    private static Object userContextUtil() throws Exception {
        Class<?> type = Class.forName("com.somewearlabs.somewearshared.auth.UserContextUtil");
        Object companion = type.getField("Companion").get(null);
        return invokeNoArgs(companion, "getInstance");
    }

    private static Object environmentUtil() throws Exception {
        Class<?> type = Class.forName("com.somewearlabs.somewearshared.core.util.EnvironmentUtil");
        Object companion = type.getField("Companion").get(null);
        return invokeNoArgs(companion, "getInstance");
    }

    private static Object workspaceRepository() throws Exception {
        Object factory = invokeNoArgs(genericUserSource(), "getWorkspaceRepositoryFactory");
        Object contextFlow = invokeNoArgs(userContextUtil(), "getUserContext");
        Object userContext = invokeNoArgs(contextFlow, "getValue");
        Object authState = invokeNoArgs(userContext, "getAuthState");
        return invoke(factory, "build", authState);
    }

    private static String activeWorkspaceId() throws Exception {
        Object workspace = invokeNoArgs(userContextUtil(), "getActiveWorkspaceOrNull");
        if (workspace == null) return null;
        Object id = invokeNoArgs(workspace, "getId");
        if (id == null || String.valueOf(id).trim().isEmpty()) return null;
        return String.valueOf(id);
    }

    private static boolean workspaceMember(Object workspace) throws Exception {
        return Boolean.TRUE.equals(invokeNoArgs(workspace, "isMemberOf"));
    }

    private static byte[] workspaceMeshKey(Object workspace) throws Exception {
        Object meshKey = invokeNoArgs(workspace, "getMeshKey");
        return meshKey instanceof byte[] ? (byte[]) meshKey : null;
    }

    private static String meshKeyFingerprint(byte[] meshKey) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(meshKey);
        char[] hex = "0123456789abcdef".toCharArray();
        StringBuilder result = new StringBuilder(16);
        for (int index = 0; index < 8; index++) {
            int value = digest[index] & 0xff;
            result.append(hex[value >>> 4]).append(hex[value & 0x0f]);
        }
        return result.toString();
    }

    private static Bundle sendMessage(Bundle extras) throws Exception {
        if (extras == null) return error("INVALID_REQUEST", "Missing extras Bundle");
        String messageId = requiredString(extras, "message_id");
        String content = requiredString(extras, "message");
        long workspaceId = extras.getLong("workspace_id", 0L);
        if (workspaceId <= 0L) return error("INVALID_REQUEST", "workspace_id must be positive");
        if (extras.containsKey("target_user_id")) {
            return error("UNSUPPORTED", "target_user_id is not implemented in this gateway build");
        }

        String policy = extras.getString("route_policy", "RADIO_ONLY");
        final String channel;
        if ("RADIO_ONLY".equalsIgnoreCase(policy)) {
            channel = "Radio";
        } else if ("SATELLITE_ONLY".equalsIgnoreCase(policy)) {
            channel = "Satellite";
        } else if ("RADIO_THEN_SATELLITE".equalsIgnoreCase(policy)) {
            return error(
                    "UNSUPPORTED",
                    "RADIO_THEN_SATELLITE is disabled until terminal delivery timeout fallback is implemented"
            );
        } else {
            return error("INVALID_REQUEST", "Unknown route_policy: " + policy);
        }

        ensureCoreStarted();
        ensurePayloadSubscription();

        List<Object> payloads = new ArrayList<>();
        Object originalPayload = buildMessagePayload(content, workspaceId);
        int originalTransmissionCount = approximateTransmissionCount(originalPayload);
        if ("Radio".equals(channel)) {
            Log.i(
                    LOG_TAG,
                    "Radio payload preflight; contentUtf8Bytes="
                            + content.getBytes(StandardCharsets.UTF_8).length
                            + "; transmissions=" + originalTransmissionCount
            );
        }
        if ("Radio".equals(channel) && originalTransmissionCount > 1) {
            try {
                payloads.addAll(buildRadioFragmentPayloads(messageId, content, workspaceId));
            } catch (IllegalArgumentException exception) {
                return error("PAYLOAD_TOO_LARGE_FOR_RADIO", exception.getMessage());
            }
        } else {
            payloads.add(originalPayload);
        }

        List<Integer> parcelIds = new ArrayList<>(payloads.size());
        for (Object payload : payloads) {
            parcelIds.add(((Number) invokeNoArgs(payload, "getParcelId")).intValue());
        }
        long acceptedAt = System.currentTimeMillis();

        synchronized (LOCK) {
            DELIVERY_TRACKER.register(messageId, channel, parcelIds);
            DELIVERY.put(
                    messageId,
                    deliveryBundle(messageId, "QUEUED", channel.toUpperCase(), null, acceptedAt)
            );
        }

        long timeout = extras.getLong("radio_timeout_ms", 30_000L);
        Object router = router();
        Method send = findMethod(router.getClass(), "send", 2);
        try {
            for (Object payload : payloads) {
                send.invoke(router, payload, sendOptions(channel, timeout));
            }
        } catch (Throwable throwable) {
            FragmentDeliveryTracker.Update failed = DELIVERY_TRACKER.fail(
                    messageId,
                    channel.toUpperCase(Locale.US),
                    "Gateway could not queue every radio fragment",
                    System.currentTimeMillis()
            );
            if (failed != null) storeDeliveryUpdate(failed);
            throwAsException(throwable);
        }

        Bundle result = ok(
                payloads.size() == 1
                        ? "Payload accepted by SomewearRouter with channel " + channel
                        : "Payload accepted as " + payloads.size() + " radio-safe fragments"
        );
        result.putString("message_id", messageId);
        result.putInt("parcel_id", parcelIds.get(0));
        result.putInt("fragment_count", payloads.size());
        result.putBoolean("radio_fragmented", payloads.size() > 1);
        result.putLong("accepted_at_ms", acceptedAt);
        return result;
    }

    private static Object buildMessagePayload(String content, long workspaceId) throws Exception {
        return Class.forName("com.somewearlabs.somewearcore.api.MessagePayload")
                .getMethod("build", String.class, long.class)
                .invoke(null, content, workspaceId);
    }

    /**
     * Builds a MessagePayload with a caller-controlled timestamp.
     *
     * <p>The retained core identifies inbound Message payload duplicates using
     * only package type, source user and whole-second timestamp. SC3 radio
     * fragments are different messages queued within one second, so the normal
     * two-argument builder makes the receiver discard fragment three onward.
     * Supplying a distinct timestamp for each fragment preserves the proven
     * mesh-supported Message transport without patching the vendor core.</p>
     */
    private static Object buildMessagePayload(
            String content,
            long workspaceId,
            long timestampMillis
    ) throws Exception {
        Class<?> messageClass = Class.forName(
                "com.somewearlabs.somewearcore.api.MessagePayload"
        );
        Object companion = messageClass.getField("Companion").get(null);
        Method datedBuilder = findMethod(companion.getClass(), "build", 9);
        return datedBuilder.invoke(
                companion,
                content,
                new Date(timestampMillis),
                Long.toString(workspaceId),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static RadioFragmentTimestamps.Reservation reserveRadioFragmentTimestamps(
            int fragmentCount
    ) {
        Context context = appContext;
        if (context == null) {
            throw new IllegalStateException("GatewayV2.initialize(Context) was not called");
        }
        synchronized (LOCK) {
            SharedPreferences preferences = context.getSharedPreferences(
                    STATE_PREFERENCES,
                    Context.MODE_PRIVATE
            );
            long persistedLastSecond = preferences.getLong(
                    LAST_RADIO_FRAGMENT_SECOND,
                    0L
            );
            RadioFragmentTimestamps.Reservation reservation =
                    RadioFragmentTimestamps.reserve(
                            System.currentTimeMillis(),
                            persistedLastSecond,
                            fragmentCount
                    );
            if (!preferences.edit()
                    .putLong(LAST_RADIO_FRAGMENT_SECOND, reservation.lastEpochSecond)
                    .commit()) {
                throw new IllegalStateException(
                        "Could not persist the radio fragment timestamp reservation"
                );
            }
            return reservation;
        }
    }

    private static List<Object> buildRadioFragmentPayloads(
            String messageId,
            String content,
            long workspaceId
    ) throws Exception {
        String transferId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        for (int chunkBytes = RadioMessageFraming.DEFAULT_CHUNK_BYTES;
                chunkBytes >= RadioMessageFraming.MIN_CHUNK_BYTES;
                chunkBytes -= 16) {
            List<String> frames = RadioMessageFraming.split(
                    messageId,
                    content,
                    transferId,
                    chunkBytes
            );
            List<Object> payloads = new ArrayList<>(frames.size());
            boolean everyFrameFitsRadio = true;
            for (String frame : frames) {
                Object payload = buildMessagePayload(frame, workspaceId);
                if (approximateTransmissionCount(payload) != 1) {
                    everyFrameFitsRadio = false;
                    break;
                }
                payloads.add(payload);
            }
            if (everyFrameFitsRadio) {
                RadioFragmentTimestamps.Reservation timestamps =
                        reserveRadioFragmentTimestamps(frames.size());
                payloads.clear();
                for (int index = 0; index < frames.size(); index++) {
                    payloads.add(buildMessagePayload(
                            frames.get(index),
                            workspaceId,
                            timestamps.timestampMillis(index)
                    ));
                }
                Log.i(
                        LOG_TAG,
                        "Reserved duplicate-safe radio fragment timestamps; count="
                                + frames.size()
                                + "; firstEpochSecond=" + timestamps.firstEpochSecond
                                + "; lastEpochSecond=" + timestamps.lastEpochSecond
                );
                return payloads;
            }
        }
        throw new IllegalArgumentException(
                "The Somewear build cannot fit the SC3 radio-fragment header in one transmission"
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object sendOptions(String channel, long timeout) throws Exception {
        Object options = Class.forName("com.somewearlabs.somewearshared.core.api.SendOptions")
                .getConstructor()
                .newInstance();
        Class<?> channelClass = Class.forName(
                "com.somewearlabs.somewearshared.core.api.DevicePayloadChannel"
        );
        Object channelValue = Enum.valueOf((Class<? extends Enum>) channelClass, channel);
        Set<?> channelIntent = Collections.singleton(channelValue);
        invoke(options, "setChannelIntent", channelIntent);
        invoke(options, "setAllowBackhaul", false);
        invoke(options, "setRequiresBackhaulAck", false);
        invoke(options, "setTimeout", (int) Math.max(1L, Math.min(Integer.MAX_VALUE, timeout)));
        return options;
    }

    private static int approximateTransmissionCount(Object payload) throws Exception {
        Class<?> devicePayloadClass = Class.forName(
                "com.somewearlabs.somewearcore.api.DevicePayload"
        );
        Class<?> sendOptionsClass = Class.forName(
                "com.somewearlabs.somewearshared.core.api.SendOptions"
        );
        Object outbound = Class.forName(
                "com.somewearlabs.somewearcore.internal.multiplatform.SharedPayloadMappersKt"
        ).getMethod("toOutboundPayload", devicePayloadClass, sendOptionsClass)
                .invoke(null, payload, null);
        Object payloadContent = invokeNoArgs(outbound, "getContent");
        Class<?> payloadContentClass = Class.forName(
                "com.somewearlabs.somewearshared.core.api.database.PayloadContent"
        );
        Object hotspotContent = Class.forName(
                "com.somewearlabs.somewearshared.core.internal.service.SharedRouterImplKt"
        ).getMethod("toHotspotMailContent", payloadContentClass)
                .invoke(null, payloadContent);
        return ((Number) invoke(compositePackager(), "approximateTransmissionCount", hotspotContent))
                .intValue();
    }

    private static Object compositePackager() throws Exception {
        synchronized (LOCK) {
            if (compositePackager != null) return compositePackager;
            Class<?> idProviderClass = Class.forName(
                    "com.somewearlabs.somewearshared.core.util.IdProvider"
            );
            Object idProvider = Proxy.newProxyInstance(
                    idProviderClass.getClassLoader(),
                    new Class<?>[] { idProviderClass },
                    (proxy, method, args) -> "nextId".equals(method.getName()) ? 1 : null
            );
            Class<?> function0Class = Class.forName("kotlin.jvm.functions.Function0");
            Object mtuProvider = Proxy.newProxyInstance(
                    function0Class.getClassLoader(),
                    new Class<?>[] { function0Class },
                    (proxy, method, args) -> "invoke".equals(method.getName())
                            ? RADIO_MTU_BYTES
                            : null
            );
            Class<?> packagerClass = Class.forName(
                    "com.somewearlabs.somewearshared.core.internal.util.CompositePackager"
            );
            compositePackager = packagerClass
                    .getConstructor(idProviderClass, function0Class)
                    .newInstance(idProvider, mtuProvider);
            return compositePackager;
        }
    }

    private static Bundle deliveryStatus(Bundle extras) {
        if (extras == null) return error("INVALID_REQUEST", "Missing extras Bundle");
        String messageId = extras.getString("message_id");
        if (messageId == null || messageId.trim().isEmpty()) {
            return error("INVALID_REQUEST", "message_id must not be blank");
        }
        synchronized (LOCK) {
            Bundle update = DELIVERY.get(messageId);
            if (update == null) return error("NOT_FOUND", "Unknown message_id: " + messageId);
            Bundle result = new Bundle(update);
            result.putBoolean("ok", true);
            return result;
        }
    }

    private static Bundle pollIncoming(Bundle extras) throws Exception {
        if (extras == null) return error("INVALID_REQUEST", "Missing extras Bundle");
        ensureCoreStarted();
        ensurePayloadSubscription();
        long after = extras.getLong("after_sequence", 0L);
        int limit = extras.getInt("limit", 100);
        if (after < 0L || limit < 1 || limit > 500) {
            return error("INVALID_REQUEST", "Invalid after_sequence or limit");
        }

        ArrayList<Bundle> items = new ArrayList<>();
        synchronized (LOCK) {
            for (Bundle item : INCOMING) {
                if (item.getLong("sequence") > after) {
                    items.add(new Bundle(item));
                    if (items.size() >= limit) break;
                }
            }
        }
        Bundle result = ok("Incoming messages");
        result.putParcelableArrayList("items", items);
        return result;
    }

    private static Bundle receiveHealth() {
        Bundle result = ok("Gateway receive health");
        synchronized (LOCK) {
            result.putBoolean("receive_subscription_active", payloadSubscription != null);
            result.putLong("router_callback_count", routerCallbackCount);
            result.putLong("inbound_message_count", inboundMessageCount);
            result.putLong("ignored_inbound_count", ignoredInboundCount);
            result.putLong("receive_error_count", receiveErrorCount);
            result.putLong("last_router_callback_at_ms", lastRouterCallbackAt);
            result.putLong("last_inbound_message_at_ms", lastInboundMessageAt);
            result.putLong("last_receive_error_at_ms", lastReceiveErrorAt);
            result.putLong("inbound_radio_fragment_count", inboundRadioFragmentCount);
            result.putLong("completed_radio_message_count", completedRadioMessageCount);
            result.putLong("invalid_radio_fragment_count", invalidRadioFragmentCount);
            result.putInt("active_radio_reassemblies", RADIO_REASSEMBLER.activeCount());
            result.putString("last_payload_type", lastPayloadType);
            result.putString("last_receive_error", lastReceiveError);
            result.putInt("queued_incoming_count", INCOMING.size());
            result.putLong("latest_sequence", nextSequence - 1L);
        }
        return result;
    }

    /** Signature-protected test hook used only to validate the Android IPC pipeline. */
    private static Bundle injectIncoming(Bundle extras) {
        if (extras == null) return error("INVALID_REQUEST", "Missing extras Bundle");
        String messageId = requiredString(extras, "message_id");
        String content = requiredString(extras, "message");
        long workspaceId = extras.getLong("workspace_id", 0L);
        if (workspaceId <= 0L) return error("INVALID_REQUEST", "workspace_id must be positive");
        String senderId = extras.getString("sender_id", "emulator-peer");
        String channel = extras.getString("delivered_channel", "RADIO");
        enqueueIncoming(messageId, content, workspaceId, senderId, channel, System.currentTimeMillis());
        return ok("Test message injected");
    }

    /** Exercises the same reflective RouterPayload parser used by physical receive callbacks. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Bundle dispatchTestRouterMessage(Bundle extras) throws Exception {
        if (extras == null) return error("INVALID_REQUEST", "Missing extras Bundle");
        String content = requiredString(extras, "message");
        long workspaceId = extras.getLong("workspace_id", 0L);
        if (workspaceId <= 0L) return error("INVALID_REQUEST", "workspace_id must be positive");
        long sourceUserId = extras.getLong("sender_id_long", 42L);
        dispatchInboundTestContent(content, workspaceId, sourceUserId);
        return ok("Test RouterPayload dispatched");
    }

    /** Exercises framing plus the real retained MessagePayload/RouterPayload receive parser. */
    private static Bundle dispatchTestFramedRouterMessage(Bundle extras) throws Exception {
        if (extras == null) return error("INVALID_REQUEST", "Missing extras Bundle");
        String messageId = requiredString(extras, "message_id");
        String content = requiredString(extras, "message");
        long workspaceId = extras.getLong("workspace_id", 0L);
        if (workspaceId <= 0L) return error("INVALID_REQUEST", "workspace_id must be positive");
        long sourceUserId = extras.getLong("sender_id_long", 42L);
        List<String> frames = RadioMessageFraming.split(
                messageId,
                content,
                "0123456789abcdef",
                RadioMessageFraming.DEFAULT_CHUNK_BYTES
        );
        if (extras.getBoolean("reverse_order", false)) Collections.reverse(frames);
        for (String frame : frames) {
            dispatchInboundTestContent(frame, workspaceId, sourceUserId);
        }
        Bundle result = ok("Test framed RouterPayloads dispatched");
        result.putInt("fragment_count", frames.size());
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void dispatchInboundTestContent(
            String content,
            long workspaceId,
            long sourceUserId
    ) throws Exception {
        Class<?> messageClass = Class.forName(
                "com.somewearlabs.somewearcore.api.MessagePayload"
        );
        Object payload = messageClass
                .getMethod("build", String.class, long.class)
                .invoke(null, content, workspaceId);
        Object oldInfo = invokeNoArgs(payload, "getRoutingInfo");
        Class<?> channelClass = Class.forName(
                "com.somewearlabs.somewearshared.core.api.DevicePayloadChannel"
        );
        Class<?> statusClass = Class.forName(
                "com.somewearlabs.somewearshared.core.api.DevicePayloadStatus"
        );
        Object channel = Enum.valueOf((Class<? extends Enum>) channelClass, "Radio");
        Object status = Enum.valueOf((Class<? extends Enum>) statusClass, "Delivered");
        Object inboundInfo = findMethod(oldInfo.getClass(), "copy", 12).invoke(
                oldInfo,
                ((Number) invokeNoArgs(oldInfo, "getParcelId")).intValue(),
                false,
                sourceUserId,
                false,
                0L,
                workspaceId,
                channel,
                status,
                invokeNoArgs(oldInfo, "getHotspotId"),
                System.currentTimeMillis(),
                false,
                null
        );
        invoke(payload, "setRoutingInfo", inboundInfo);

        Class<?> routerPayloadClass = Class.forName(
                "com.somewearlabs.somewearcore.api.RouterPayload"
        );
        Object routerPayload = routerPayloadClass
                .getConstructor(
                        Class.forName("com.somewearlabs.somewearcore.api.DevicePayload"),
                        statusClass,
                        channelClass
                )
                .newInstance(payload, status, channel);
        handleRouterPayload(routerPayload);
    }

    private static Bundle connectUsb() throws Exception {
        ensureCoreStarted();
        Object device = somewearDevice();
        Object continuation = Class.forName("com.somewearlabs.gateway.ConnectionContinuation")
                .getConstructor()
                .newInstance();
        findMethod(device.getClass(), "toggleUsbConnect", 2)
                .invoke(device, false, continuation);
        return ok("USB connection operation accepted");
    }

    /**
     * Normalizes a caller-supplied MAC and makes the corresponding Android
     * BluetoothDevice visible to the vendor core before toggleScan().
     *
     * Somewear Core only searches the system bonded-device list and its private
     * scan cache. Seeding that cache prevents a valid explicit MAC from being
     * rejected as NoKnownDeviceFound before a connection is attempted.
     */
    public static String prepareBluetoothAddress(String address) throws Exception {
        if (address == null) throw new IllegalArgumentException("Bluetooth address is missing");
        String normalized = address.trim().toUpperCase(Locale.US);
        if (!BluetoothAdapter.checkBluetoothAddress(normalized)) {
            throw new IllegalArgumentException(
                    "Invalid Bluetooth address; expected AA:BB:CC:DD:EE:FF"
            );
        }
        if (appContext == null) {
            throw new IllegalStateException("GatewayV2.initialize(Context) was not called");
        }

        BluetoothManager manager = (BluetoothManager) appContext.getSystemService(
                Context.BLUETOOTH_SERVICE
        );
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) throw new IllegalStateException("Bluetooth is unavailable on this device");
        if (!adapter.isEnabled()) throw new IllegalStateException("Bluetooth is disabled");

        final BluetoothDevice device;
        try {
            device = adapter.getRemoteDevice(normalized);
        } catch (SecurityException exception) {
            throw new SecurityException(
                    "Grant Nearby devices/Bluetooth permission to the Somewear gateway",
                    exception
            );
        }

        Class<?> cacheClass = Class.forName(
                "com.somewearlabs.somewearcore.internal.util.BleUtility$Companion$DeviceCache"
        );
        Object cache = cacheClass.getField("INSTANCE").get(null);
        cacheClass.getMethod("putDevice", BluetoothDevice.class).invoke(cache, device);
        return normalized;
    }

    private static Bundle setConnectionMode(Bundle extras) throws Exception {
        if (extras == null) return error("INVALID_REQUEST", "Missing extras Bundle");
        String mode = extras.getString("connection_mode");
        ensureCoreStarted();
        final String vendorMode;
        if ("USB".equalsIgnoreCase(mode) || "LineIn".equalsIgnoreCase(mode)) {
            vendorMode = "LineIn";
        } else if ("BLUETOOTH".equalsIgnoreCase(mode)) {
            vendorMode = "Bluetooth";
        } else {
            return error("INVALID_REQUEST", "connection_mode must be BLUETOOTH or USB");
        }
        if (!deviceConnected()) return notConnectedForSettings();
        Object result = invokeSuspend(
                deviceUtil(),
                "updateConnectionMode",
                DEFAULT_SETTINGS_TIMEOUT_MS,
                enumField("com.somewear.wire.SettingsCommand$ConnectionMode", vendorMode)
        );
        if (!Boolean.TRUE.equals(result)) {
            return error("GATEWAY_REJECTED", "The Node did not acknowledge connection mode");
        }
        return ok("Node connection mode accepted");
    }

    private static Bundle hardwareSettings() throws Exception {
        ensureCoreStarted();
        Object settingsFlow = invokeNoArgs(deviceUtil(), "getSettings");
        Object settings = invokeNoArgs(settingsFlow, "getValue");
        return hardwareSettingsBundle(settings, "Current Somewear Node hardware settings");
    }

    private static Bundle hardwareSettingsBundle(Object settings, String message) throws Exception {
        Bundle result = ok(message);
        if (settings == null) return result;

        putOptionalBoolean(result, "tracking_enabled", invokeNoArgs(settings, "getTrackingEnabled"), false);
        Object trackingFrequency = invokeNoArgs(settings, "getTrackingFrequency");
        if (trackingFrequency != null) {
            putPositiveInt(
                    result,
                    "tracking_gps_seconds",
                    invokeNoArgs(trackingFrequency, "getGpsSeconds")
            );
            putPositiveInt(
                    result,
                    "tracking_sending_seconds",
                    invokeNoArgs(trackingFrequency, "getSendingSeconds")
            );
        }
        putOptionalBoolean(result, "backhaul_enabled", invokeNoArgs(settings, "getBackhaulEnabled"), false);
        putOptionalBoolean(result, "satellite_enabled", invokeNoArgs(settings, "getSatelliteDisabled"), true);

        Object radioMode = invokeNoArgs(settings, "getRadioMode");
        if (radioMode != null) {
            String value = String.valueOf(radioMode);
            result.putBoolean(
                    "mesh_radio_enabled",
                    "RadioModeEnabled".equals(value) || "RadioModeNone".equals(value)
            );
        }
        putPositiveInt(
                result,
                "low_speed_frequency_hz",
                invokeNoArgs(settings, "getLowSpeedFrequencyHz")
        );
        putPositiveInt(
                result,
                "high_speed_frequency_hz",
                invokeNoArgs(settings, "getHighSpeedFrequencyHz")
        );

        Object powerMode = invokeNoArgs(settings, "getRadioPowerMode");
        if (powerMode != null) {
            result.putString(
                    "mesh_transmission_strength",
                    externalPowerMode(String.valueOf(powerMode))
            );
        }
        putOptionalBoolean(result, "led_light_enabled", invokeNoArgs(settings, "getLedDisabled"), true);
        putOptionalBoolean(
                result,
                "vibration_feedback_enabled",
                invokeNoArgs(settings, "getHapticsDisabled"),
                true
        );
        putOptionalBoolean(result, "endurance_mode_enabled", invokeNoArgs(settings, "getNoSleep"), true);

        Object button = invokeNoArgs(settings, "getButton1");
        if (button != null) {
            result.putString("device_button_function", externalButtonFunction(String.valueOf(button)));
        }
        Object connectionMode = invokeNoArgs(settings, "getConnectionMode");
        if (connectionMode != null) {
            result.putString(
                    "connection_mode",
                    "LineIn".equals(String.valueOf(connectionMode)) ? "USB" : "BLUETOOTH"
            );
        }
        return result;
    }

    /** Signature-protected test hook; constructs no command and changes no Node state. */
    private static Bundle inspectTestHardwareSettingsPatch() throws Exception {
        Object[] fields = emptyHardwareSettingFields();
        fields[SETTING_TRACKING_ENABLED] = true;
        fields[SETTING_TRACKING_FREQUENCY] = Class.forName(
                "com.somewearlabs.somewearshared.core.api.TrackingFrequency"
        ).getConstructor(int.class, int.class).newInstance(15, 30);
        fields[SETTING_NO_SLEEP] = false;
        fields[SETTING_CONNECTION_MODE] = enumField(
                "com.somewear.wire.SettingsCommand$ConnectionMode",
                "Bluetooth"
        );
        fields[SETTING_LED_DISABLED] = false;
        fields[SETTING_SATELLITE_DISABLED] = false;
        fields[SETTING_BUTTON_FUNCTION] = enumField(
                "com.somewear.wire.ButtonFunction",
                "ButtonFunctionPTT"
        );
        fields[SETTING_LOW_SPEED_FREQUENCY] = 915_000_000;
        fields[SETTING_HIGH_SPEED_FREQUENCY] = 916_000_000;
        fields[SETTING_RADIO_POWER_MODE] = enumField(
                "com.somewear.wire.SettingsCommand$PowerMode",
                "PowerModeHigh"
        );
        fields[SETTING_BACKHAUL_ENABLED] = true;
        fields[SETTING_HAPTICS_DISABLED] = false;
        fields[SETTING_RADIO_MODE] = enumField(
                "com.somewear.wire.RadioMode",
                "RadioModeEnabled"
        );
        Bundle result = hardwareSettingsBundle(
                newHardwareSettings(fields),
                "Hardware-settings patch reflection test"
        );
        Class<?> deviceUtilClass = Class.forName(
                "com.somewearlabs.somewearshared.core.util.DeviceUtil"
        );
        Object repository = Class.forName(
                "com.somewearlabs.uicomponent.device.DeviceManagementRepositoryImpl"
        ).getConstructor(Context.class, deviceUtilClass).newInstance(appContext, deviceUtil());
        findMethod(repository.getClass(), "factoryReset-gIAlu-s", 2);
        result.putBoolean("factory_reset_bridge_available", true);
        return result;
    }

    private static Bundle updateBooleanSetting(
            Bundle extras,
            int settingIndex,
            boolean invert
    ) throws Exception {
        if (extras == null || !extras.containsKey("enabled")) {
            return error("INVALID_REQUEST", "Missing enabled Boolean");
        }
        boolean value = extras.getBoolean("enabled");
        return updateHardwareSetting(settingIndex, invert ? !value : value);
    }

    private static Bundle setTrackingInterval(Bundle extras) throws Exception {
        if (extras == null
                || !extras.containsKey("tracking_gps_seconds")
                || !extras.containsKey("tracking_sending_seconds")) {
            return error("INVALID_REQUEST", "Missing tracking interval values");
        }
        int gpsSeconds = extras.getInt("tracking_gps_seconds");
        int sendingSeconds = extras.getInt("tracking_sending_seconds");
        if (gpsSeconds <= 0 || sendingSeconds <= 0) {
            return error("INVALID_REQUEST", "Tracking intervals must be positive seconds");
        }
        Object frequency = Class.forName(
                "com.somewearlabs.somewearshared.core.api.TrackingFrequency"
        ).getConstructor(int.class, int.class).newInstance(gpsSeconds, sendingSeconds);
        return updateHardwareSetting(SETTING_TRACKING_FREQUENCY, frequency);
    }

    private static Bundle setMeshRadioEnabled(Bundle extras) throws Exception {
        if (extras == null || !extras.containsKey("enabled")) {
            return error("INVALID_REQUEST", "Missing enabled Boolean");
        }
        Object mode = enumField(
                "com.somewear.wire.RadioMode",
                extras.getBoolean("enabled") ? "RadioModeEnabled" : "RadioModeDisabled"
        );
        return updateHardwareSetting(SETTING_RADIO_MODE, mode);
    }

    private static Bundle setRadioChannel(Bundle extras) throws Exception {
        if (extras == null
                || !extras.containsKey("low_speed_frequency_hz")
                || !extras.containsKey("high_speed_frequency_hz")) {
            return error("INVALID_REQUEST", "Missing radio-channel frequencies");
        }
        int low = extras.getInt("low_speed_frequency_hz");
        int high = extras.getInt("high_speed_frequency_hz");
        if (low <= 0 || high <= 0) {
            return error("INVALID_REQUEST", "Radio-channel frequencies must be positive Hz");
        }
        Object[] fields = emptyHardwareSettingFields();
        fields[SETTING_LOW_SPEED_FREQUENCY] = low;
        fields[SETTING_HIGH_SPEED_FREQUENCY] = high;
        return updateHardwareSettings(fields);
    }

    private static Bundle setMeshTransmissionStrength(Bundle extras) throws Exception {
        if (extras == null) return error("INVALID_REQUEST", "Missing extras Bundle");
        String requested = requiredString(extras, "mesh_transmission_strength").toUpperCase(Locale.US);
        final String vendorValue;
        if ("LOW".equals(requested)) {
            vendorValue = "PowerModeLow";
        } else if ("MEDIUM".equals(requested)) {
            vendorValue = "PowerModeMedium";
        } else if ("HIGH".equals(requested)) {
            vendorValue = "PowerModeHigh";
        } else {
            return error("INVALID_REQUEST", "mesh_transmission_strength must be LOW, MEDIUM, or HIGH");
        }
        return updateHardwareSetting(
                SETTING_RADIO_POWER_MODE,
                enumField("com.somewear.wire.SettingsCommand$PowerMode", vendorValue)
        );
    }

    private static Bundle setDeviceButtonFunction(Bundle extras) throws Exception {
        if (extras == null) return error("INVALID_REQUEST", "Missing extras Bundle");
        String requested = requiredString(extras, "device_button_function").toUpperCase(Locale.US);
        final String vendorValue;
        if ("NONE".equals(requested)) {
            vendorValue = "ButtonFunctionNone";
        } else if ("SATELLITE".equals(requested)) {
            vendorValue = "ButtonFunctionSat";
        } else if ("TRACKING".equals(requested)) {
            vendorValue = "ButtonFunctionTracking";
        } else if ("SENSOR".equals(requested)) {
            vendorValue = "ButtonFunctionSensor";
        } else if ("PUSH_TO_TALK".equals(requested)) {
            vendorValue = "ButtonFunctionPTT";
        } else {
            return error("INVALID_REQUEST", "Unknown device-button function");
        }
        return updateHardwareSetting(
                SETTING_BUTTON_FUNCTION,
                enumField("com.somewear.wire.ButtonFunction", vendorValue)
        );
    }

    private static Bundle factoryReset(Bundle extras) throws Exception {
        if (extras == null
                || !"ERASE_NODE".equals(extras.getString("factory_reset_confirmation"))) {
            return error("INVALID_REQUEST", "Factory reset requires ERASE_NODE confirmation");
        }
        ensureCoreStarted();
        if (!deviceConnected()) return notConnectedForSettings();
        Object util = deviceUtil();
        Object address = invokeNoArgs(util, "getConnectedBleDeviceAddress");
        Class<?> deviceUtilClass = Class.forName(
                "com.somewearlabs.somewearshared.core.util.DeviceUtil"
        );
        Object repository = Class.forName(
                "com.somewearlabs.uicomponent.device.DeviceManagementRepositoryImpl"
        ).getConstructor(Context.class, deviceUtilClass).newInstance(appContext, util);
        try {
            // This is the retained Somewear reset workflow: clear the most recent
            // bond, reset firmware, and forget the device from the local cache.
            invokeSuspend(
                    repository,
                    "factoryReset-gIAlu-s",
                    DEFAULT_SETTINGS_TIMEOUT_MS,
                    address
            );
        } catch (SuspendTimeoutException timeout) {
            return error("TIMEOUT", "Timed out waiting for the Node factory reset");
        }
        return ok("Node factory reset completed; the connection and stored bond were cleared");
    }

    private static Bundle updateHardwareSetting(int index, Object value) throws Exception {
        Object[] fields = emptyHardwareSettingFields();
        fields[index] = value;
        return updateHardwareSettings(fields);
    }

    private static Bundle updateHardwareSettings(Object[] fields) throws Exception {
        ensureCoreStarted();
        if (!deviceConnected()) return notConnectedForSettings();
        Object patch = newHardwareSettings(fields);
        final Object acknowledged;
        try {
            acknowledged = invokeSuspend(
                    deviceUtil(),
                    "updateSettings",
                    DEFAULT_SETTINGS_TIMEOUT_MS,
                    patch
            );
        } catch (SuspendTimeoutException timeout) {
            return error("TIMEOUT", "Timed out waiting for the Node settings acknowledgement");
        }
        if (acknowledged == null) {
            return error("GATEWAY_REJECTED", "The Node did not acknowledge the settings update");
        }
        return ok("Node hardware setting acknowledged");
    }

    private static Object[] emptyHardwareSettingFields() {
        return new Object[HARDWARE_SETTING_COUNT];
    }

    private static Object newHardwareSettings(Object[] fields) throws Exception {
        if (fields.length != HARDWARE_SETTING_COUNT) {
            throw new IllegalArgumentException("Unexpected hardware setting field count");
        }
        Class<?> type = Class.forName(
                "com.somewearlabs.somewearshared.device.SharedDevice$Settings"
        );
        for (Constructor<?> constructor : type.getConstructors()) {
            if (constructor.getParameterTypes().length == HARDWARE_SETTING_COUNT) {
                return constructor.newInstance(fields);
            }
        }
        throw new NoSuchMethodException(type.getName() + "/" + HARDWARE_SETTING_COUNT);
    }

    private static boolean deviceConnected() throws Exception {
        Object state = invokeNoArgs(invokeNoArgs(deviceUtil(), "getConnectionState"), "getValue");
        String value = String.valueOf(state);
        return "Connected".equals(value) || "ConnectedAndScanning".equals(value);
    }

    private static Bundle notConnectedForSettings() {
        return error("NOT_CONNECTED", "Connect the Somewear Node before changing hardware settings");
    }

    private static Object enumField(String className, String fieldName) throws Exception {
        return Class.forName(className).getField(fieldName).get(null);
    }

    private static void putOptionalBoolean(
            Bundle result,
            String key,
            Object rawValue,
            boolean invert
    ) {
        if (!(rawValue instanceof Boolean)) return;
        boolean value = (Boolean) rawValue;
        result.putBoolean(key, invert ? !value : value);
    }

    private static void putPositiveInt(Bundle result, String key, Object rawValue) {
        if (!(rawValue instanceof Number)) return;
        int value = ((Number) rawValue).intValue();
        if (value > 0) result.putInt(key, value);
    }

    private static String externalPowerMode(String value) {
        if (value.endsWith("High")) return "HIGH";
        if (value.endsWith("Medium")) return "MEDIUM";
        if (value.endsWith("Low")) return "LOW";
        return "UNKNOWN";
    }

    private static String externalButtonFunction(String value) {
        if (value.endsWith("Sat")) return "SATELLITE";
        if (value.endsWith("Tracking")) return "TRACKING";
        if (value.endsWith("Sensor")) return "SENSOR";
        if (value.endsWith("PTT")) return "PUSH_TO_TALK";
        if (value.endsWith("None")) return "NONE";
        return "UNKNOWN";
    }

    private static Bundle shutdown() throws Exception {
        Object subscription;
        synchronized (LOCK) {
            subscription = payloadSubscription;
            payloadSubscription = null;
            DELIVERY_TRACKER.clear();
            RADIO_REASSEMBLER.clear();
            compositePackager = null;
        }
        if (subscription != null) {
            try {
                invokeNoArgs(subscription, "dispose");
            } catch (Throwable ignored) {
                // Core teardown below remains authoritative.
            }
        }
        Class<?> configClass = Class.forName("com.somewearlabs.ataklibs.config.SomewearConfig");
        Object config = configClass.getField("INSTANCE").get(null);
        findMethod(configClass, "destroy", 1).invoke(config, new Object[] { null });
        return ok("Somewear core shutdown requested");
    }

    private static void ensureCoreStarted() throws Exception {
        Class<?> provider = Class.forName("com.somewearlabs.gateway.SomewearGatewayProvider");
        Method method = provider.getDeclaredMethod("ensureStarted");
        method.setAccessible(true);
        method.invoke(null);
    }

    /** Starts the core and inbound subscription without exposing vendor objects. */
    public static void startReceiving() {
        startReceivingResult();
    }

    private static Bundle startReceivingResult() {
        try {
            ensureCoreStarted();
            ensurePayloadSubscription();
            return ok("Gateway receive subscription active");
        } catch (Throwable error) {
            recordReceiveError("SUBSCRIBE", error);
            return error(
                    "RECEIVE_FAILED",
                    "Could not start the gateway receive subscription ("
                            + rootClassName(error) + ")"
            );
        }
    }

    private static String rootClassName(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root.getClass().getSimpleName();
    }

    private static void ensurePayloadSubscription() throws Exception {
        synchronized (LOCK) {
            if (payloadSubscription != null) return;
            final Object router = router();
            final Object flow = invokeNoArgs(router, "getPayload");
            final Class<?> function1 = Class.forName("kotlin.jvm.functions.Function1");
            InvocationHandler handler = (proxy, method, args) -> {
                if ("invoke".equals(method.getName()) && args != null && args.length == 1) {
                    handleRouterPayload(args[0]);
                    return kotlinUnit();
                }
                if ("toString".equals(method.getName())) return "SC3GatewayPayloadListener";
                return null;
            };
            Object callback = Proxy.newProxyInstance(
                    function1.getClassLoader(),
                    new Class<?>[] { function1 },
                    handler
            );
            Method onEach = flow.getClass().getMethod("onEach", function1);
            payloadSubscription = onEach.invoke(flow, callback);
        }
    }

    private static void handleRouterPayload(Object routerPayload) {
        synchronized (LOCK) {
            routerCallbackCount++;
            lastRouterCallbackAt = System.currentTimeMillis();
        }
        try {
            Object payload = invokeNoArgs(routerPayload, "getPayload");
            synchronized (LOCK) {
                lastPayloadType = payload == null ? null : payload.getClass().getName();
            }
            boolean outbound = (Boolean) invokeNoArgs(payload, "isOutbound");
            String channel = String.valueOf(invokeNoArgs(routerPayload, "getDeliveredDeviceChannel"));
            String status = String.valueOf(invokeNoArgs(routerPayload, "getSummaryStatus"));
            String traceId = String.valueOf(invokeNoArgs(payload, "getTraceId"));
            int parcelId = ((Number) invokeNoArgs(payload, "getParcelId")).intValue();
            long now = System.currentTimeMillis();

            if (outbound) {
                String errorReason = null;
                Object routingInfo = invokeNoArgs(payload, "getRoutingInfo");
                try {
                    Object reason = invokeNoArgs(routingInfo, "getErrorReason");
                    errorReason = reason == null ? null : String.valueOf(reason);
                } catch (Throwable ignored) {
                    // Optional field varies across core releases.
                }
                FragmentDeliveryTracker.Update aggregate = DELIVERY_TRACKER.update(
                        parcelId,
                        status,
                        channel,
                        errorReason,
                        now
                );
                if (aggregate != null) {
                    storeDeliveryUpdate(aggregate);
                }
                return;
            }

            if (!payload.getClass().getName().endsWith(".MessagePayload")) {
                synchronized (LOCK) {
                    ignoredInboundCount++;
                }
                return;
            }
            String content = String.valueOf(invokeNoArgs(payload, "getContent"));
            long workspaceId = ((Number) invokeNoArgs(payload, "getWorkspaceId")).longValue();
            long sourceUserId = ((Number) invokeNoArgs(payload, "getSourceUserId")).longValue();
            Date timestamp = (Date) invokeNoArgs(payload, "getTimestamp");
            long receivedAt = timestamp == null ? now : timestamp.getTime();
            String senderId = String.valueOf(sourceUserId);
            String deliveredChannel = channel.toUpperCase(Locale.US);
            RadioMessageReassembler.Result reassembly = RADIO_REASSEMBLER.accept(
                    content,
                    workspaceId,
                    senderId,
                    deliveredChannel,
                    receivedAt
            );
            switch (reassembly.kind) {
                case NOT_FRAME:
                    enqueueIncoming(
                            traceId,
                            content,
                            workspaceId,
                            senderId,
                            deliveredChannel,
                            receivedAt
                    );
                    break;
                case PENDING:
                    synchronized (LOCK) {
                        inboundRadioFragmentCount++;
                    }
                    break;
                case COMPLETE:
                    synchronized (LOCK) {
                        inboundRadioFragmentCount++;
                        completedRadioMessageCount++;
                    }
                    enqueueIncoming(
                            reassembly.messageId,
                            reassembly.content,
                            workspaceId,
                            senderId,
                            deliveredChannel,
                            reassembly.completedAt
                    );
                    break;
                case INVALID:
                    synchronized (LOCK) {
                        inboundRadioFragmentCount++;
                        invalidRadioFragmentCount++;
                    }
                    recordReceiveError("REASSEMBLY", new IllegalArgumentException());
                    break;
            }
        } catch (Throwable error) {
            // Never include payload content or exception messages in diagnostics.
            recordReceiveError("PARSE", error);
        }
    }

    private static void recordReceiveError(String stage, Throwable error) {
        String safeError = stage + ":" + rootClassName(error);
        synchronized (LOCK) {
            receiveErrorCount++;
            lastReceiveErrorAt = System.currentTimeMillis();
            lastReceiveError = safeError;
        }
        Log.w(LOG_TAG, "Receive pipeline failure " + safeError);
    }

    private static void enqueueIncoming(
            String messageId,
            String content,
            long workspaceId,
            String senderId,
            String channel,
            long receivedAt
    ) {
        synchronized (LOCK) {
            Bundle item = new Bundle();
            item.putLong("sequence", nextSequence++);
            item.putString("message_id", messageId);
            item.putString("message", content);
            item.putLong("workspace_id", workspaceId);
            item.putString("sender_id", senderId);
            item.putLong("received_at_ms", receivedAt);
            item.putString("delivered_channel", channel);
            INCOMING.add(item);
            inboundMessageCount++;
            lastInboundMessageAt = receivedAt;
            while (INCOMING.size() > MAX_INCOMING_MESSAGES) INCOMING.remove(0);
        }
    }

    private static Bundle deliveryBundle(
            String messageId,
            String status,
            String channel,
            String errorReason,
            long updatedAt
    ) {
        Bundle result = new Bundle();
        result.putString("message_id", messageId);
        result.putString("delivery_status", status);
        result.putString("delivered_channel", channel);
        result.putString("error_reason", errorReason);
        result.putLong("updated_at_ms", updatedAt);
        return result;
    }

    private static void storeDeliveryUpdate(FragmentDeliveryTracker.Update update) {
        synchronized (LOCK) {
            Bundle bundle = deliveryBundle(
                    update.messageId,
                    update.status,
                    update.channel,
                    update.errorReason,
                    update.updatedAt
            );
            bundle.putInt("fragment_count", update.fragmentCount);
            DELIVERY.put(update.messageId, bundle);
        }
    }

    private static Object router() throws Exception {
        Class<?> type = Class.forName("com.somewearlabs.somewearcore.api.SomewearRouter");
        Object companion = type.getField("Companion").get(null);
        return invokeNoArgs(companion, "getInstance");
    }

    private static Object somewearDevice() throws Exception {
        Class<?> type = Class.forName("com.somewearlabs.somewearcore.api.SomewearDevice");
        Object companion = type.getField("Companion").get(null);
        return invokeNoArgs(companion, "getInstance");
    }

    private static Object deviceUtil() throws Exception {
        Class<?> type = Class.forName(
                "com.somewearlabs.somewearshared.core.util.DeviceUtil"
        );
        Object companion = type.getField("Companion").get(null);
        return invokeNoArgs(companion, "getInstance");
    }

    private static Object kotlinUnit() {
        try {
            return Class.forName("kotlin.Unit").getField("INSTANCE").get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invokeNoArgs(Object target, String name) throws Exception {
        return findMethod(target.getClass(), name, 0).invoke(target);
    }

    private static Object invoke(Object target, String name, Object argument) throws Exception {
        return findMethod(target.getClass(), name, 1).invoke(target, argument);
    }

    /**
     * Calls a Kotlin suspend function reflectively and waits for its Continuation.
     * Provider calls arrive on a Binder worker and the SC3 SDK also dispatches them
     * from Dispatchers.IO, so this never blocks either application's UI thread.
     */
    private static Object invokeSuspend(
            Object target,
            String name,
            long timeoutMillis,
            Object... arguments
    ) throws Exception {
        Class<?> continuationClass = Class.forName("kotlin.coroutines.Continuation");
        Class<?> emptyContextClass = Class.forName("kotlin.coroutines.EmptyCoroutineContext");
        Object emptyContext = emptyContextClass.getField("INSTANCE").get(null);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Object> resumedResult = new AtomicReference<>();

        InvocationHandler handler = (proxy, method, args) -> {
            if ("getContext".equals(method.getName())) return emptyContext;
            if ("resumeWith".equals(method.getName())) {
                resumedResult.set(args == null || args.length == 0 ? null : args[0]);
                completed.countDown();
                return null;
            }
            if ("toString".equals(method.getName())) return "SC3GatewayContinuation";
            if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
            if ("equals".equals(method.getName())) {
                return args != null && args.length == 1 && proxy == args[0];
            }
            return null;
        };
        Object continuation = Proxy.newProxyInstance(
                continuationClass.getClassLoader(),
                new Class<?>[] { continuationClass },
                handler
        );

        Object[] callArguments = new Object[arguments.length + 1];
        System.arraycopy(arguments, 0, callArguments, 0, arguments.length);
        callArguments[arguments.length] = continuation;

        final Object immediate;
        try {
            immediate = findCompatibleMethod(target.getClass(), name, callArguments)
                    .invoke(target, callArguments);
        } catch (InvocationTargetException exception) {
            throwAsException(exception.getCause());
            return null;
        }

        Object suspended = Class.forName("kotlin.coroutines.intrinsics.IntrinsicsKt")
                .getMethod("getCOROUTINE_SUSPENDED")
                .invoke(null);
        if (immediate != suspended) return unwrapKotlinResult(immediate);
        if (!completed.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
            throw new SuspendTimeoutException();
        }
        return unwrapKotlinResult(resumedResult.get());
    }

    private static Object unwrapKotlinResult(Object value) throws Exception {
        try {
            Class.forName("kotlin.ResultKt")
                    .getMethod("throwOnFailure", Object.class)
                    .invoke(null, value);
            return value;
        } catch (InvocationTargetException exception) {
            throwAsException(exception.getCause());
            return null;
        }
    }

    private static void throwAsException(Throwable throwable) throws Exception {
        if (throwable instanceof Exception) throw (Exception) throwable;
        if (throwable instanceof Error) throw (Error) throwable;
        throw new RuntimeException(throwable);
    }

    private static long workspaceTimeout(Bundle extras) {
        long requested = extras == null
                ? DEFAULT_WORKSPACE_TIMEOUT_MS
                : extras.getLong("workspace_timeout_ms", DEFAULT_WORKSPACE_TIMEOUT_MS);
        return Math.max(1_000L, Math.min(MAX_WORKSPACE_TIMEOUT_MS, requested));
    }

    private static String stringOrNull(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount)
            throws NoSuchMethodException {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            for (Method method : cursor.getDeclaredMethods()) {
                if (name.equals(method.getName()) && method.getParameterTypes().length == parameterCount) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        for (Method method : type.getMethods()) {
            if (name.equals(method.getName()) && method.getParameterTypes().length == parameterCount) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name + "/" + parameterCount);
    }

    private static Method findCompatibleMethod(Class<?> type, String name, Object[] arguments)
            throws NoSuchMethodException {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            for (Method method : cursor.getDeclaredMethods()) {
                if (compatible(method, name, arguments)) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        for (Method method : type.getMethods()) {
            if (compatible(method, name, arguments)) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name + "/" + arguments.length);
    }

    private static boolean compatible(Method method, String name, Object[] arguments) {
        if (!name.equals(method.getName())) return false;
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != arguments.length) return false;
        for (int index = 0; index < parameterTypes.length; index++) {
            Object argument = arguments[index];
            if (argument == null) {
                if (parameterTypes[index].isPrimitive()) return false;
                continue;
            }
            if (!boxedType(parameterTypes[index]).isInstance(argument)) return false;
        }
        return true;
    }

    private static Class<?> boxedType(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static String requiredString(Bundle extras, String key) {
        String value = extras.getString(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(key + " must not be blank");
        }
        return value;
    }

    private static Bundle ok(String message) {
        Bundle result = new Bundle();
        result.putBoolean("ok", true);
        result.putString("message", message);
        return result;
    }

    private static Bundle error(String code, String message) {
        Bundle result = new Bundle();
        result.putBoolean("ok", false);
        result.putString("error_code", code);
        result.putString("message", message);
        return result;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) cursor = cursor.getCause();
        String message = cursor.getMessage();
        return cursor.getClass().getName() + (message == null ? "" : ": " + message);
    }

    private static final class SuspendTimeoutException extends Exception {}

    private static final class InviteParts {
        final String host;
        final int port;
        final String token;
        final String meshKey;
        final String workspaceId;
        final String workspaceName;
        final boolean plaintext;

        private InviteParts(
                String host,
                int port,
                String token,
                String meshKey,
                String workspaceId,
                String workspaceName,
                boolean plaintext
        ) {
            this.host = host;
            this.port = port;
            this.token = token;
            this.meshKey = meshKey;
            this.workspaceId = workspaceId;
            this.workspaceName = workspaceName;
            this.plaintext = plaintext;
        }

        static InviteParts parse(String rawInvite) {
            final Uri uri;
            try {
                uri = Uri.parse(rawInvite.trim());
            } catch (Throwable throwable) {
                throw new IllegalArgumentException("The QR code is not a valid Somewear invite");
            }
            String token = stringOrNull(uri.getQueryParameter("token"));
            String meshKey = stringOrNull(uri.getQueryParameter("meshKey"));
            String workspaceId = stringOrNull(uri.getQueryParameter("workspaceId"));
            if ((token == null) == (meshKey == null)) {
                throw new IllegalArgumentException(
                        "A Somewear invite must contain exactly one token or meshKey"
                );
            }
            if (meshKey != null && workspaceId == null) {
                throw new IllegalArgumentException(
                        "A mesh-key invite must contain workspaceId"
                );
            }
            return new InviteParts(
                    stringOrNull(uri.getHost()),
                    uri.getPort(),
                    token,
                    meshKey,
                    workspaceId,
                    stringOrNull(uri.getQueryParameter("name")),
                    "true".equals(uri.getQueryParameter("plaintext"))
            );
        }
    }
}
