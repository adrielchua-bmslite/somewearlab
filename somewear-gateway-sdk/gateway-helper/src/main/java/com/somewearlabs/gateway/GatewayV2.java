package com.somewearlabs.gateway;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Bundle;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * API-v2 adapter injected into the standalone gateway APK.
 *
 * All Somewear references are reflective so this helper can be compiled without
 * redistributing or linking a proprietary Somewear SDK at build time.
 */
public final class GatewayV2 {
    private static final Object LOCK = new Object();
    private static final int MAX_INCOMING_MESSAGES = 2_000;
    private static final List<Bundle> INCOMING = new ArrayList<>();
    private static final Map<String, Bundle> DELIVERY = new LinkedHashMap<>();
    private static final Map<String, String> TRACE_TO_MESSAGE = new LinkedHashMap<>();

    private static Context appContext;
    private static Object payloadSubscription;
    private static long nextSequence = 1L;

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
            if ("testInjectIncomingMessage".equals(method)) return injectIncoming(extras);
            if ("connectUsb".equals(method)
                    || "connectUSB".equals(method)
                    || "connect_usb".equals(method)) return connectUsb();
            if ("setConnectionMode".equals(method)) return setConnectionMode(extras);
            if ("shutdown".equals(method)) return shutdown();
            if ("listWorkspaces".equals(method)) return listWorkspaces();
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
                "satellite_only",
                "delivery_status",
                "incoming_messages",
                "workspace_list",
                "workspace_selection",
                "workspace_status",
                "mesh_key_status",
                "test_injection"
        );
        result.putStringArrayList("capabilities", capabilities);
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

    private static String activeWorkspaceId() throws Exception {
        Class<?> type = Class.forName("com.somewearlabs.somewearshared.auth.UserContextUtil");
        Object companion = type.getField("Companion").get(null);
        Object userContext = invokeNoArgs(companion, "getInstance");
        Object workspace = invokeNoArgs(userContext, "getActiveWorkspaceOrNull");
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

        Class<?> messagePayloadClass = Class.forName(
                "com.somewearlabs.somewearcore.api.MessagePayload"
        );
        Object payload = messagePayloadClass
                .getMethod("build", String.class, long.class)
                .invoke(null, content, workspaceId);

        String traceId = String.valueOf(invokeNoArgs(payload, "getTraceId"));
        int parcelId = ((Number) invokeNoArgs(payload, "getParcelId")).intValue();
        long acceptedAt = System.currentTimeMillis();

        synchronized (LOCK) {
            TRACE_TO_MESSAGE.put(traceId, messageId);
            DELIVERY.put(
                    messageId,
                    deliveryBundle(messageId, "QUEUED", channel.toUpperCase(), null, acceptedAt)
            );
        }

        Object options = Class.forName("com.somewearlabs.somewearshared.core.api.SendOptions")
                .getConstructor()
                .newInstance();
        Class<?> channelClass = Class.forName(
                "com.somewearlabs.somewearshared.core.api.DevicePayloadChannel"
        );
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object channelValue = Enum.valueOf((Class<? extends Enum>) channelClass, channel);
        Set<?> channelIntent = Collections.singleton(channelValue);
        invoke(options, "setChannelIntent", channelIntent);
        invoke(options, "setAllowBackhaul", false);
        invoke(options, "setRequiresBackhaulAck", false);
        long timeout = extras.getLong("radio_timeout_ms", 30_000L);
        invoke(options, "setTimeout", (int) Math.max(1L, Math.min(Integer.MAX_VALUE, timeout)));

        Object router = router();
        Method send = findMethod(router.getClass(), "send", 2);
        send.invoke(router, payload, options);

        Bundle result = ok("Payload accepted by SomewearRouter with channel " + channel);
        result.putString("message_id", messageId);
        result.putInt("parcel_id", parcelId);
        result.putLong("accepted_at_ms", acceptedAt);
        return result;
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
        if (!"USB".equalsIgnoreCase(mode) && !"LineIn".equalsIgnoreCase(mode)) {
            return error("UNSUPPORTED", "Only USB/LineIn mode is implemented in this gateway build");
        }
        ensureCoreStarted();
        Class<?> useCaseClass = Class.forName(
                "com.somewearlabs.ataklibs.usb.UsbConnectionUseCaseImpl"
        );
        Object companion = useCaseClass.getField("Companion").get(null);
        Object useCase = invokeNoArgs(companion, "getInstance");
        invokeNoArgs(useCase, "requestLineInConnectionMode");
        return ok("USB/LineIn mode request accepted");
    }

    private static Bundle shutdown() throws Exception {
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
        try {
            Object payload = invokeNoArgs(routerPayload, "getPayload");
            boolean outbound = (Boolean) invokeNoArgs(payload, "isOutbound");
            String channel = String.valueOf(invokeNoArgs(routerPayload, "getDeliveredDeviceChannel"));
            String status = String.valueOf(invokeNoArgs(routerPayload, "getSummaryStatus"));
            String traceId = String.valueOf(invokeNoArgs(payload, "getTraceId"));
            long now = System.currentTimeMillis();

            if (outbound) {
                synchronized (LOCK) {
                    String messageId = TRACE_TO_MESSAGE.get(traceId);
                    if (messageId != null) {
                        String errorReason = null;
                        Object routingInfo = invokeNoArgs(payload, "getRoutingInfo");
                        try {
                            Object reason = invokeNoArgs(routingInfo, "getErrorReason");
                            errorReason = reason == null ? null : String.valueOf(reason);
                        } catch (Throwable ignored) {
                            // Optional field varies across core releases.
                        }
                        DELIVERY.put(
                                messageId,
                                deliveryBundle(
                                        messageId,
                                        status.toUpperCase(),
                                        channel.toUpperCase(),
                                        errorReason,
                                        now
                                )
                        );
                    }
                }
                return;
            }

            if (!payload.getClass().getName().endsWith(".MessagePayload")) return;
            String content = String.valueOf(invokeNoArgs(payload, "getContent"));
            long workspaceId = ((Number) invokeNoArgs(payload, "getWorkspaceId")).longValue();
            long sourceUserId = ((Number) invokeNoArgs(payload, "getSourceUserId")).longValue();
            Date timestamp = (Date) invokeNoArgs(payload, "getTimestamp");
            enqueueIncoming(
                    traceId,
                    content,
                    workspaceId,
                    String.valueOf(sourceUserId),
                    channel.toUpperCase(),
                    timestamp == null ? now : timestamp.getTime()
            );
        } catch (Throwable ignored) {
            // Router callbacks must never crash the gateway process.
        }
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
}
