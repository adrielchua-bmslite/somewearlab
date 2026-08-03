package com.sc3.somewear.sdk

import android.content.Context

public object SomewearGateway {
    @JvmStatic
    @JvmOverloads
    public fun create(
        context: Context,
        config: SomewearSdkConfig = SomewearSdkConfig(),
    ): SomewearClient = ContentProviderSomewearClient(context.applicationContext, config)
}
