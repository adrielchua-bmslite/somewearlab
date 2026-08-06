.class public final Lcom/somewearlabs/gateway/SomewearGatewayProvider;
.super Landroid/content/ContentProvider;
.source "SomewearGatewayProvider.java"


# static fields
.field private static volatile started:Z

.field private static volatile operationState:Ljava/lang/String; = "idle"

.field private static volatile operationResult:Ljava/lang/String; = ""


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Landroid/content/ContentProvider;-><init>()V

    return-void
.end method

.method private static ensureStarted()V
    .locals 1

    sget-boolean v0, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->started:Z

    if-nez v0, :started

    sget-object v0, Lcom/somewearlabs/ataklibs/config/SomewearConfig;->INSTANCE:Lcom/somewearlabs/ataklibs/config/SomewearConfig;

    invoke-virtual {v0}, Lcom/somewearlabs/ataklibs/config/SomewearConfig;->start()V

    const/4 v0, 0x1

    sput-boolean v0, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->started:Z

    :started
    return-void
.end method

.method public static completeConnection(Ljava/lang/Object;)V
    .locals 1

    if-nez p0, :has_result

    const-string v0, "null"

    goto :store_result

    :has_result
    invoke-virtual {p0}, Ljava/lang/Object;->toString()Ljava/lang/String;

    move-result-object v0

    :store_result
    sput-object v0, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->operationResult:Ljava/lang/String;

    const-string v0, "completed"

    sput-object v0, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->operationState:Ljava/lang/String;

    return-void
.end method

.method private static result(ZLjava/lang/String;)Landroid/os/Bundle;
    .locals 2

    new-instance v0, Landroid/os/Bundle;

    invoke-direct {v0}, Landroid/os/Bundle;-><init>()V

    const-string v1, "ok"

    invoke-virtual {v0, v1, p0}, Landroid/os/Bundle;->putBoolean(Ljava/lang/String;Z)V

    const-string v1, "message"

    invoke-virtual {v0, v1, p1}, Landroid/os/Bundle;->putString(Ljava/lang/String;Ljava/lang/String;)V

    return-object v0
.end method

.method private static send(Lcom/somewearlabs/somewearcore/api/DevicePayload;)V
    .locals 1

    sget-object v0, Lcom/somewearlabs/somewearcore/api/SomewearRouter;->Companion:Lcom/somewearlabs/somewearcore/api/SomewearRouter$Companion;

    invoke-virtual {v0}, Lcom/somewearlabs/somewearshared/util/Singleton;->getInstance()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lcom/somewearlabs/somewearcore/api/SomewearRouter;

    invoke-interface {v0, p0}, Lcom/somewearlabs/somewearcore/api/SomewearRouter;->send(Lcom/somewearlabs/somewearcore/api/DevicePayload;)V

    return-void
.end method


# virtual methods
.method public call(Ljava/lang/String;Ljava/lang/String;Landroid/os/Bundle;)Landroid/os/Bundle;
    .locals 7

    :try_start
    invoke-static {p1, p3}, Lcom/somewearlabs/gateway/GatewayV2;->call(Ljava/lang/String;Landroid/os/Bundle;)Landroid/os/Bundle;

    move-result-object v0

    if-eqz v0, :legacy_dispatch

    return-object v0

    :legacy_dispatch
    const-string v0, "info"

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :check_activate

    const/4 v0, 0x1

    const-string v1, "SomewearCoreConfig + SomewearDevice + MessagePayload/DataPayload + SomewearRouter; methods=activate,connectBle,getDeviceStatus,cancelConnection,disconnect,sendMessage,sendRaw,sendRawToWorkspace,sendRawWithParcel"

    invoke-static {v0, v1}, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->result(ZLjava/lang/String;)Landroid/os/Bundle;

    move-result-object v0

    return-object v0

    :check_activate
    const-string v0, "activate"

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :check_device_status

    invoke-static {}, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->ensureStarted()V

    const/4 v0, 0x1

    const-string v1, "Somewear core active"

    invoke-static {v0, v1}, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->result(ZLjava/lang/String;)Landroid/os/Bundle;

    move-result-object v0

    return-object v0

    :check_device_status
    const-string v0, "getDeviceStatus"

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :check_cancel_connection

    invoke-static {}, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->ensureStarted()V

    sget-object v0, Lcom/somewearlabs/somewearcore/api/SomewearDevice;->Companion:Lcom/somewearlabs/somewearcore/api/SomewearDevice$Companion;

    invoke-virtual {v0}, Lcom/somewearlabs/somewearcore/api/SomewearDevice$Companion;->getInstance()Lcom/somewearlabs/somewearcore/api/SomewearDevice;

    move-result-object v0

    invoke-virtual {v0}, Lcom/somewearlabs/somewearcore/api/SomewearDevice;->getConnectionState()Lcom/somewearlabs/somewearshared/util/CommonStateFlow;

    move-result-object v0

    invoke-virtual {v0}, Lcom/somewearlabs/somewearshared/util/CommonStateFlow;->getValue()Ljava/lang/Object;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/Object;->toString()Ljava/lang/String;

    move-result-object v1

    new-instance v0, Landroid/os/Bundle;

    invoke-direct {v0}, Landroid/os/Bundle;-><init>()V

    const-string v2, "ok"

    const/4 v3, 0x1

    invoke-virtual {v0, v2, v3}, Landroid/os/Bundle;->putBoolean(Ljava/lang/String;Z)V

    const-string v2, "connection_state"

    invoke-virtual {v0, v2, v1}, Landroid/os/Bundle;->putString(Ljava/lang/String;Ljava/lang/String;)V

    const-string v1, "operation_state"

    sget-object v2, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->operationState:Ljava/lang/String;

    invoke-virtual {v0, v1, v2}, Landroid/os/Bundle;->putString(Ljava/lang/String;Ljava/lang/String;)V

    const-string v1, "operation_result"

    sget-object v2, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->operationResult:Ljava/lang/String;

    invoke-virtual {v0, v1, v2}, Landroid/os/Bundle;->putString(Ljava/lang/String;Ljava/lang/String;)V

    return-object v0

    :check_cancel_connection
    const-string v0, "cancelConnection"

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :check_disconnect

    invoke-static {}, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->ensureStarted()V

    sget-object v0, Lcom/somewearlabs/somewearcore/api/SomewearDevice;->Companion:Lcom/somewearlabs/somewearcore/api/SomewearDevice$Companion;

    invoke-virtual {v0}, Lcom/somewearlabs/somewearcore/api/SomewearDevice$Companion;->getInstance()Lcom/somewearlabs/somewearcore/api/SomewearDevice;

    move-result-object v0

    invoke-virtual {v0}, Lcom/somewearlabs/somewearcore/api/SomewearDevice;->cancelConnection()V

    const-string v0, "cancelled"

    sput-object v0, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->operationState:Ljava/lang/String;

    const/4 v0, 0x1

    const-string v1, "Connection attempt cancelled"

    invoke-static {v0, v1}, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->result(ZLjava/lang/String;)Landroid/os/Bundle;

    move-result-object v0

    return-object v0

    :check_disconnect
    const-string v0, "disconnect"

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :check_payload

    invoke-static {}, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->ensureStarted()V

    const-string v0, "pending"

    sput-object v0, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->operationState:Ljava/lang/String;

    const-string v0, ""

    sput-object v0, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->operationResult:Ljava/lang/String;

    sget-object v0, Lcom/somewearlabs/somewearcore/api/SomewearDevice;->Companion:Lcom/somewearlabs/somewearcore/api/SomewearDevice$Companion;

    invoke-virtual {v0}, Lcom/somewearlabs/somewearcore/api/SomewearDevice$Companion;->getInstance()Lcom/somewearlabs/somewearcore/api/SomewearDevice;

    move-result-object v0

    new-instance v1, Lcom/somewearlabs/gateway/ConnectionContinuation;

    invoke-direct {v1}, Lcom/somewearlabs/gateway/ConnectionContinuation;-><init>()V

    invoke-virtual {v0, v1}, Lcom/somewearlabs/somewearcore/api/SomewearDevice;->disconnect(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;

    move-result-object v0

    invoke-static {}, Lkotlin/coroutines/intrinsics/IntrinsicsKt;->getCOROUTINE_SUSPENDED()Ljava/lang/Object;

    move-result-object v1

    if-eq v0, v1, :disconnect_started

    invoke-static {v0}, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->completeConnection(Ljava/lang/Object;)V

    :disconnect_started
    const/4 v0, 0x1

    const-string v1, "Disconnect operation accepted"

    invoke-static {v0, v1}, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->result(ZLjava/lang/String;)Landroid/os/Bundle;

    move-result-object v0

    return-object v0

    :check_payload
    if-nez p3, :has_extras

    const/4 v0, 0x0

    const-string v1, "Missing extras Bundle"

    invoke-static {v0, v1}, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->result(ZLjava/lang/String;)Landroid/os/Bundle;

    move-result-object v0

    return-object v0

    :has_extras
    const-string v0, "connectBle"

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :check_message

    const-string v0, "address"

    invoke-virtual {p3, v0}, Landroid/os/BaseBundle;->getString(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    if-nez v1, :has_address

    const/4 v0, 0x0

    const-string v1, "Missing String extra: address"

    invoke-static {v0, v1}, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->result(ZLjava/lang/String;)Landroid/os/Bundle;

    move-result-object v0

    return-object v0

    :has_address
    invoke-static {v1}, Lcom/somewearlabs/gateway/GatewayV2;->prepareBluetoothAddress(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    invoke-static {}, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->ensureStarted()V

    const-string v0, "pending"

    sput-object v0, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->operationState:Ljava/lang/String;

    const-string v0, ""

    sput-object v0, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->operationResult:Ljava/lang/String;

    sget-object v0, Lcom/somewearlabs/somewearcore/api/SomewearDevice;->Companion:Lcom/somewearlabs/somewearcore/api/SomewearDevice$Companion;

    invoke-virtual {v0}, Lcom/somewearlabs/somewearcore/api/SomewearDevice$Companion;->getInstance()Lcom/somewearlabs/somewearcore/api/SomewearDevice;

    move-result-object v0

    new-instance v2, Lcom/somewearlabs/gateway/ConnectionContinuation;

    invoke-direct {v2}, Lcom/somewearlabs/gateway/ConnectionContinuation;-><init>()V

    invoke-virtual {v0, v1, v2}, Lcom/somewearlabs/somewearcore/api/SomewearDevice;->toggleScan(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;

    move-result-object v0

    invoke-static {}, Lkotlin/coroutines/intrinsics/IntrinsicsKt;->getCOROUTINE_SUSPENDED()Ljava/lang/Object;

    move-result-object v1

    if-eq v0, v1, :connection_started

    invoke-static {v0}, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->completeConnection(Ljava/lang/Object;)V

    :connection_started
    const/4 v0, 0x1

    const-string v1, "BLE connection operation accepted"

    invoke-static {v0, v1}, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->result(ZLjava/lang/String;)Landroid/os/Bundle;

    move-result-object v0

    return-object v0

    :check_message
    const-string v0, "sendMessage"

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :get_raw_payload

    const-string v0, "message"

    invoke-virtual {p3, v0}, Landroid/os/BaseBundle;->getString(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    if-nez v1, :has_message

    const/4 v0, 0x0

    const-string v1, "Missing String extra: message"

    invoke-static {v0, v1}, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->result(ZLjava/lang/String;)Landroid/os/Bundle;

    move-result-object v0

    return-object v0

    :has_message
    invoke-static {}, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->ensureStarted()V

    const-string v0, "workspace_id"

    invoke-virtual {p3, v0}, Landroid/os/BaseBundle;->getLong(Ljava/lang/String;)J

    move-result-wide v3

    invoke-static {v1, v3, v4}, Lcom/somewearlabs/somewearcore/api/MessagePayload;->build(Ljava/lang/String;J)Lcom/somewearlabs/somewearcore/api/MessagePayload;

    move-result-object v2

    goto :route

    :get_raw_payload
    const-string v0, "sendRaw"

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :read_raw_payload

    const-string v0, "sendRawToWorkspace"

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :read_raw_payload

    const-string v0, "sendRawWithParcel"

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :unknown_method

    :read_raw_payload
    const-string v0, "payload"

    invoke-virtual {p3, v0}, Landroid/os/Bundle;->getByteArray(Ljava/lang/String;)[B

    move-result-object v1

    if-nez v1, :has_payload

    const/4 v0, 0x0

    const-string v1, "Missing byte[] extra: payload"

    invoke-static {v0, v1}, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->result(ZLjava/lang/String;)Landroid/os/Bundle;

    move-result-object v0

    return-object v0

    :has_payload
    invoke-static {}, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->ensureStarted()V

    const-string v0, "sendRaw"

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :check_target

    invoke-static {v1}, Lcom/somewearlabs/somewearcore/api/DataPayload;->build([B)Lcom/somewearlabs/somewearcore/api/DataPayload;

    move-result-object v2

    goto :route

    :check_target
    const-string v0, "sendRawToWorkspace"

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :check_parcel

    const-string v0, "workspace_id"

    invoke-virtual {p3, v0}, Landroid/os/BaseBundle;->getLong(Ljava/lang/String;)J

    move-result-wide v3

    invoke-static {v1, v3, v4}, Lcom/somewearlabs/somewearcore/api/DataPayload;->build([BJ)Lcom/somewearlabs/somewearcore/api/DataPayload;

    move-result-object v2

    goto :route

    :check_parcel
    const-string v0, "sendRawWithParcel"

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :unknown_method

    const-string v0, "parcel_id"

    invoke-virtual {p3, v0}, Landroid/os/BaseBundle;->getInt(Ljava/lang/String;)I

    move-result v2

    const-string v0, "workspace_id"

    invoke-virtual {p3, v0}, Landroid/os/BaseBundle;->getLong(Ljava/lang/String;)J

    move-result-wide v3

    invoke-static {v1, v2, v3, v4}, Lcom/somewearlabs/somewearcore/api/DataPayload;->build([BIJ)Lcom/somewearlabs/somewearcore/api/DataPayload;

    move-result-object v2

    :route
    invoke-static {v2}, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->send(Lcom/somewearlabs/somewearcore/api/DevicePayload;)V

    const/4 v0, 0x1

    const-string v1, "Payload accepted by SomewearRouter"

    invoke-static {v0, v1}, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->result(ZLjava/lang/String;)Landroid/os/Bundle;

    move-result-object v0

    return-object v0

    :unknown_method
    const/4 v0, 0x0

    const-string v1, "Unknown gateway method"

    invoke-static {v0, v1}, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->result(ZLjava/lang/String;)Landroid/os/Bundle;

    move-result-object v0

    return-object v0
    :try_end
    .catch Ljava/lang/Throwable; {:try_start .. :try_end} :catch_error

    :catch_error
    move-exception v0

    invoke-virtual {v0}, Ljava/lang/Throwable;->toString()Ljava/lang/String;

    move-result-object v1

    const/4 v0, 0x0

    invoke-static {v0, v1}, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->result(ZLjava/lang/String;)Landroid/os/Bundle;

    move-result-object v0

    return-object v0
.end method

.method public delete(Landroid/net/Uri;Ljava/lang/String;[Ljava/lang/String;)I
    .locals 1

    const/4 v0, 0x0

    return v0
.end method

.method public getType(Landroid/net/Uri;)Ljava/lang/String;
    .locals 1

    const/4 v0, 0x0

    return-object v0
.end method

.method public insert(Landroid/net/Uri;Landroid/content/ContentValues;)Landroid/net/Uri;
    .locals 1

    const/4 v0, 0x0

    return-object v0
.end method

.method public onCreate()Z
    .locals 2

    invoke-virtual {p0}, Landroid/content/ContentProvider;->getContext()Landroid/content/Context;

    move-result-object v0

    if-eqz v0, :initialized

    invoke-static {v0}, Lio/realm/Realm;->init(Landroid/content/Context;)V

    sget-object v1, Lcom/somewearlabs/swtak/plugin/PluginConfig;->Companion:Lcom/somewearlabs/swtak/plugin/PluginConfig$Companion;

    invoke-virtual {v1, v0}, Lcom/somewearlabs/swtak/plugin/PluginConfig$Companion;->setup(Landroid/content/Context;)V

    invoke-static {v0}, Lcom/somewearlabs/gateway/GatewayV2;->initialize(Landroid/content/Context;)V

    :initialized
    const/4 v0, 0x1

    return v0
.end method

.method public query(Landroid/net/Uri;[Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;)Landroid/database/Cursor;
    .locals 1

    const/4 v0, 0x0

    return-object v0
.end method

.method public update(Landroid/net/Uri;Landroid/content/ContentValues;Ljava/lang/String;[Ljava/lang/String;)I
    .locals 1

    const/4 v0, 0x0

    return v0
.end method
