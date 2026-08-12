package com.somewearlabs.gateway;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

/**
 * Process-lifetime anchor for the standalone gateway.
 *
 * SC3 binds for as long as its SomewearClient is open. This prevents Android
 * from treating the gateway's router subscription like an idle provider
 * process between polling calls.
 */
public final class SomewearGatewayService extends Service {
    private final IBinder binder = new Binder();

    @Override
    public void onCreate() {
        super.onCreate();
        GatewayV2.initialize(this);
        GatewayV2.startReceiving();
    }

    @Override
    public IBinder onBind(Intent intent) {
        GatewayV2.startReceiving();
        return binder;
    }
}
