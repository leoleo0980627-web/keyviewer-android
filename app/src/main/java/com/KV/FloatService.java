package com.KV;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class FloatService extends Service {

    private static final String TAG = "FloatService";
    private FloatView floatView;
    private static FloatService instance;
    private static KeyView sharedKeyView;

    public static FloatService getInstance() {
        return instance;
    }

    public static void setSharedKeyView(KeyView keyView) {
        sharedKeyView = keyView;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        
        // 切换到 KeyViewer IME
        IMEHelper.switchToKeyViewerIME(this);
        
        floatView = new FloatView(this, sharedKeyView);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (floatView != null) {
            try {
                floatView.attachToWindow();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        // 切回原来的输入法
        IMEHelper.switchBackToOriginalIME();
        
        if (floatView != null) {
            floatView.detachFromWindow();
        }
        instance = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public FloatView getFloatView() {
        return floatView;
    }

    public void updateScale() {
        if (floatView != null) {
            floatView.updateScale();
        }
    }
}