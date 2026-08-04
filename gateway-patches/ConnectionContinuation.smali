.class public final Lcom/somewearlabs/gateway/ConnectionContinuation;
.super Ljava/lang/Object;
.source "ConnectionContinuation.java"

# interfaces
.implements Lkotlin/coroutines/Continuation;


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public getContext()Lkotlin/coroutines/CoroutineContext;
    .locals 1

    sget-object v0, Lkotlin/coroutines/EmptyCoroutineContext;->INSTANCE:Lkotlin/coroutines/EmptyCoroutineContext;

    return-object v0
.end method

.method public resumeWith(Ljava/lang/Object;)V
    .locals 0

    invoke-static {p1}, Lcom/somewearlabs/gateway/SomewearGatewayProvider;->completeConnection(Ljava/lang/Object;)V

    return-void
.end method
