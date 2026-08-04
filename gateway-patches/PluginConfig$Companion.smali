.class public final Lcom/somewearlabs/swtak/plugin/PluginConfig$Companion;
.super Ljava/lang/Object;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/somewearlabs/swtak/plugin/PluginConfig;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = "Companion"
.end annotation


# direct methods
.method private constructor <init>()V
    .locals 0

    .line 2
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public synthetic constructor <init>(Lkotlin/jvm/internal/DefaultConstructorMarker;)V
    .locals 0

    .line 1
    invoke-direct {p0}, Lcom/somewearlabs/swtak/plugin/PluginConfig$Companion;-><init>()V

    return-void
.end method

.method public static synthetic a(Landroid/content/Context;Landroid/content/Context;)Lcom/somewearlabs/ataklibs/util/DialogPresenterSupport;
    .locals 0

    invoke-static {p0, p1}, Lcom/somewearlabs/swtak/plugin/PluginConfig$Companion;->setup$lambda$0(Landroid/content/Context;Landroid/content/Context;)Lcom/somewearlabs/ataklibs/util/DialogPresenterSupport;

    move-result-object p0

    return-object p0
.end method

.method private static final setup$lambda$0(Landroid/content/Context;Landroid/content/Context;)Lcom/somewearlabs/ataklibs/util/DialogPresenterSupport;
    .locals 1

    const-string v0, "presenterContext"

    invoke-static {p0, v0}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullParameter(Ljava/lang/Object;Ljava/lang/String;)V

    new-instance v0, Lcom/somewearlabs/ataklibs/util/DialogPresenterSupport;

    invoke-direct {v0, p0, p1}, Lcom/somewearlabs/ataklibs/util/DialogPresenterSupport;-><init>(Landroid/content/Context;Landroid/content/Context;)V

    return-object v0
.end method


# virtual methods
.method public final setup(Landroid/content/Context;)V
    .locals 11
    .param p1    # Landroid/content/Context;
        .annotation build Latak/core/aqp;
        .end annotation
    .end param

    const-string v0, "context"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullParameter(Ljava/lang/Object;Ljava/lang/String;)V

    sget-object v1, Lcom/somewearlabs/ataklibs/config/LogConfig;->INSTANCE:Lcom/somewearlabs/ataklibs/config/LogConfig;

    invoke-static {}, Lcom/somewearlabs/swtak/plugin/PluginConfig;->access$getAtakVersion$cp()Ljava/lang/String;

    move-result-object v4

    invoke-static {}, Lcom/somewearlabs/swtak/plugin/PluginConfig;->access$getPluginVersion$cp()Ljava/lang/String;

    move-result-object v5

    const-string v6, "somewear-standalone"

    const-string v3, "gateway"

    move-object v2, p1

    invoke-virtual/range {v1 .. v6}, Lcom/somewearlabs/ataklibs/config/LogConfig;->setup(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V

    new-instance v4, Lcom/somewearlabs/ataklibs/PluginStateProvider;

    invoke-direct {v4}, Lcom/somewearlabs/ataklibs/PluginStateProvider;-><init>()V

    sget-object v0, Lcom/somewearlabs/ataklibs/util/LocationUtil;->Companion:Lcom/somewearlabs/ataklibs/util/LocationUtil$Companion;

    new-instance v1, Lcom/somewearlabs/swtak/plugin/PluginConfig$Companion$setup$1;

    invoke-direct {v1}, Lcom/somewearlabs/swtak/plugin/PluginConfig$Companion$setup$1;-><init>()V

    invoke-virtual {v0, v1}, Lcom/somewearlabs/ataklibs/util/LocationUtil$Companion;->setup(Lcom/somewearlabs/ataklibs/view/LocationProvider;)V

    sget-object v1, Lcom/somewearlabs/ataklibs/config/SomewearConfig;->INSTANCE:Lcom/somewearlabs/ataklibs/config/SomewearConfig;

    sget-object v3, Lcom/somewearlabs/ataklibs/util/Scopes;->INSTANCE:Lcom/somewearlabs/ataklibs/util/Scopes;

    invoke-static {}, Lcom/somewearlabs/swtak/plugin/PluginConfig;->access$getPluginVersion$cp()Ljava/lang/String;

    move-result-object v5

    invoke-static {}, Lcom/somewearlabs/swtak/plugin/PluginConfig;->access$getAtakVersion$cp()Ljava/lang/String;

    move-result-object v6

    invoke-virtual {p1}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v0

    const-string v7, "android_id"

    invoke-static {v0, v7}, Landroid/provider/Settings$Secure;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    if-nez v0, :has_device_uid

    const-string v0, "somewear-standalone"

    :has_device_uid
    new-instance v8, Lcom/somewearlabs/ataklibs/config/AuthSource;

    const-string v10, "Standalone"

    invoke-direct {v8, v0, v10}, Lcom/somewearlabs/ataklibs/config/AuthSource;-><init>(Ljava/lang/String;Ljava/lang/String;)V

    const/16 v9, 0x20

    const/4 v10, 0x0

    const/4 v7, 0x0

    invoke-static/range {v1 .. v10}, Lcom/somewearlabs/ataklibs/config/SomewearConfig;->setup$default(Lcom/somewearlabs/ataklibs/config/SomewearConfig;Landroid/content/Context;Lcom/somewearlabs/somewearshared/core/api/ScopeProvider;Lcom/somewearlabs/somewearshared/core/api/AppStateProvider;Ljava/lang/String;Ljava/lang/String;Lcom/somewearlabs/somewearshared/user/SharedUserSource;Lcom/somewearlabs/ataklibs/config/AuthSource;ILjava/lang/Object;)V

    sget-object v0, Lcom/somewearlabs/ataklibs/db/RealmBuilderModule;->INSTANCE:Lcom/somewearlabs/ataklibs/db/RealmBuilderModule;

    new-instance v1, Lcom/somewearlabs/ataklibs/db/RealmFactoryImpl;

    invoke-direct {v1}, Lcom/somewearlabs/ataklibs/db/RealmFactoryImpl;-><init>()V

    invoke-virtual {v0, p1, v1}, Lcom/somewearlabs/ataklibs/db/RealmBuilderModule;->setup(Landroid/content/Context;Lcom/somewearlabs/ataklibs/db/RealmFactory;)V

    sget-object v0, Lcom/somewearlabs/uicomponent/config/DialogPresenterModule;->INSTANCE:Lcom/somewearlabs/uicomponent/config/DialogPresenterModule;

    new-instance v1, Latakplugin/somewear/os2;

    invoke-direct {v1}, Latakplugin/somewear/os2;-><init>()V

    invoke-virtual {v0, v1}, Lcom/somewearlabs/uicomponent/config/DialogPresenterModule;->setProvider(Lkotlin/jvm/functions/Function2;)V

    return-void
.end method
