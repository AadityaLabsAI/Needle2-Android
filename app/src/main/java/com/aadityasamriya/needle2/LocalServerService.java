package com.aadityasamriya.needle2;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class LocalServerService extends Service {
    private LocalPlaygroundServer server;
    @Override public void onCreate() {
        super.onCreate();
        LocalPlaygroundServer.AssetStore.init(this);
        try { server = new LocalPlaygroundServer(this, 8765); server.start(); }
        catch (Exception ignored) { }
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override public void onDestroy() { if (server != null) server.stop(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
