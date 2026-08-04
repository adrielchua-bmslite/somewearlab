.class public final Lcom/somewearlabs/swtak/plugin/SomewearPlugin;
.super Landroid/app/Application;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/SourceDebugExtension;
    value = "SMAP\nSomewearPlugin.kt\nKotlin\n*S Kotlin\n*F\n+ 1 SomewearPlugin.kt\ncom/somewearlabs/swtak/plugin/SomewearPlugin\n+ 2 r8-map-id-be3796caf3ef53165d119ed2326396f79166a84d667830c41c14d11638a6f08c\ncom/somewearlabs/somewearcore/api/LogKt\n*L\n1#1,32:1\n93#2,2:33\n93#2,2:35\n*S KotlinDebug\n*F\n+ 1 SomewearPlugin.kt\ncom/somewearlabs/swtak/plugin/SomewearPlugin\n*L\n24#1:33,2\n28#1:35,2\n*E\n"
.end annotation

.annotation build Lkotlin/jvm/internal/SourceDebugExtension;
    value = {
        "SMAP\nSomewearPlugin.kt\nKotlin\n*S Kotlin\n*F\n+ 1 SomewearPlugin.kt\ncom/somewearlabs/swtak/plugin/SomewearPlugin\n+ 2 r8-map-id-be3796caf3ef53165d119ed2326396f79166a84d667830c41c14d11638a6f08c\ncom/somewearlabs/somewearcore/api/LogKt\n*L\n1#1,32:1\n93#2,2:33\n93#2,2:35\n*S KotlinDebug\n*F\n+ 1 SomewearPlugin.kt\ncom/somewearlabs/swtak/plugin/SomewearPlugin\n*L\n24#1:33,2\n28#1:35,2\n*E\n"
    }
.end annotation


# direct methods
.method public constructor <init>()V
    .locals 2

    invoke-direct {p0}, Landroid/app/Application;-><init>()V

    invoke-static {}, Ljava/lang/Thread;->getDefaultUncaughtExceptionHandler()Ljava/lang/Thread$UncaughtExceptionHandler;

    move-result-object v0

    new-instance v1, Lcom/somewearlabs/swtak/plugin/SomewearPlugin$1;

    invoke-direct {v1, p0, v0}, Lcom/somewearlabs/swtak/plugin/SomewearPlugin$1;-><init>(Lcom/somewearlabs/swtak/plugin/SomewearPlugin;Ljava/lang/Thread$UncaughtExceptionHandler;)V

    invoke-static {v1}, Ljava/lang/Thread;->setDefaultUncaughtExceptionHandler(Ljava/lang/Thread$UncaughtExceptionHandler;)V

    return-void
.end method


# virtual methods
.method public onCreate()V
    .locals 4

    invoke-super {p0}, Landroid/app/Application;->onCreate()V

    invoke-static {p0}, Lio/realm/Realm;->init(Landroid/content/Context;)V

    sget-object v0, Lcom/somewearlabs/somewearcore/api/LogModule;->INSTANCE:Lcom/somewearlabs/somewearcore/api/LogModule;

    const-class v1, Lcom/somewearlabs/swtak/plugin/SomewearPlugin;

    invoke-virtual {v1}, Ljava/lang/Class;->getSimpleName()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Lcom/somewearlabs/somewearcore/api/LogModule;->logger(Ljava/lang/String;)Lcom/somewearlabs/somewearshared/util/Logger;

    move-result-object v0

    invoke-static {}, Landroid/os/Process;->myPid()I

    move-result v1

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "onCreate: plugin application created with process id "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-interface {v0, v1}, Lcom/somewearlabs/somewearshared/util/Logger;->debug(Ljava/lang/String;)V

    return-void
.end method

.method public onTerminate()V
    .locals 2

    sget-object v0, Lcom/somewearlabs/somewearcore/api/LogModule;->INSTANCE:Lcom/somewearlabs/somewearcore/api/LogModule;

    const-class v1, Lcom/somewearlabs/swtak/plugin/SomewearPlugin;

    invoke-virtual {v1}, Ljava/lang/Class;->getSimpleName()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Lcom/somewearlabs/somewearcore/api/LogModule;->logger(Ljava/lang/String;)Lcom/somewearlabs/somewearshared/util/Logger;

    move-result-object v0

    const-string v1, "onTerminate: is terminating"

    invoke-interface {v0, v1}, Lcom/somewearlabs/somewearshared/util/Logger;->debug(Ljava/lang/String;)V

    invoke-super {p0}, Landroid/app/Application;->onTerminate()V

    return-void
.end method
