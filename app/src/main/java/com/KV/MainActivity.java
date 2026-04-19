package com.KV;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {

    private static final String TAG = "KV_Main";
    
    private KeyView keyView;
    private View settingsPanel;
    private View globalSettingsPanel;
    private SettingsPanel settingsPanelController;
    private GlobalSettingsPanel globalSettingsPanelController;
    private boolean isPanelVisible = false;
    private boolean isGlobalPanelVisible = false;

    private ImageButton addKeyBtn;
    private ImageButton deleteKeyBtn;
    private ImageButton batchEditBtn;
    private ImageButton globalSettingsBtn;
    private View zoomInBtn;
    private View zoomOutBtn;
    private View zoomResetBtn;
    private Button floatWindowBtn;
    private Button shizukuBtn;
    
    private GlobalSettings globalSettings;
    private RishInputListener inputListener;
    private Handler handler = new Handler();
    
    private static final int REQUEST_OVERLAY_PERMISSION = 1001;

    private final Shizuku.OnBinderReceivedListener BINDER_RECEIVED_LISTENER = () -> {
        Log.d(TAG, "Binder received, checking permission...");
        int perm = Shizuku.checkSelfPermission();
        Log.d(TAG, "Permission: " + perm);
        if (perm == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permission granted, starting listener");
            inputListener.startListening();
            runOnUiThread(() -> {
                shizukuBtn.setText("监听中");
                shizukuBtn.setBackgroundColor(0xFF4CAF50);
            });
        } else {
            Log.d(TAG, "Permission not granted: " + perm);
        }
    };

    private final Shizuku.OnBinderDeadListener BINDER_DEAD_LISTENER = () -> {
        Log.d(TAG, "Binder dead");
        runOnUiThread(() -> {
            shizukuBtn.setText("Shizuku 断开");
            shizukuBtn.setBackgroundColor(0xFFF44336);
        });
    };

    private final Shizuku.OnRequestPermissionResultListener PERMISSION_LISTENER = 
        (requestCode, grantResult) -> {
            Log.d(TAG, "Permission result: " + grantResult);
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                inputListener.startListening();
                shizukuBtn.setText("监听中");
                shizukuBtn.setBackgroundColor(0xFF4CAF50);
                Toast.makeText(this, "授权成功，监听已启动", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "授权失败", Toast.LENGTH_SHORT).show();
            }
        };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        globalSettings = GlobalSettings.getInstance(this);
        
        keyView = findViewById(R.id.keyView);
        settingsPanel = findViewById(R.id.settingsPanel);
        globalSettingsPanel = findViewById(R.id.globalSettingsPanel);

        addKeyBtn = findViewById(R.id.addKeyBtn);
        deleteKeyBtn = findViewById(R.id.deleteKeyBtn);
        batchEditBtn = findViewById(R.id.batchEditBtn);
        globalSettingsBtn = findViewById(R.id.globalSettingsBtn);
        zoomInBtn = findViewById(R.id.zoomInBtn);
        zoomOutBtn = findViewById(R.id.zoomOutBtn);
        zoomResetBtn = findViewById(R.id.zoomResetBtn);
        floatWindowBtn = findViewById(R.id.floatWindowBtn);
        shizukuBtn = findViewById(R.id.shizukuBtn);

        settingsPanelController = new SettingsPanel(this, settingsPanel, keyView);
        globalSettingsPanelController = new GlobalSettingsPanel(this, globalSettingsPanel, keyView);
        
        inputListener = new RishInputListener(this, keyView);
        
        FloatService.setSharedKeyView(keyView);
        settingsPanelController.setInputListener(inputListener);
        
        Shizuku.addBinderReceivedListener(BINDER_RECEIVED_LISTENER);
        Shizuku.addBinderDeadListener(BINDER_DEAD_LISTENER);
        Shizuku.addRequestPermissionResultListener(PERMISSION_LISTENER);

        addKeyBtn.setOnClickListener(v -> {
            keyView.addNewKey();
            updateDeleteButtonState();
        });

        deleteKeyBtn.setOnClickListener(v -> {
            keyView.deleteSelectedKey();
            updateDeleteButtonState();
        });

        batchEditBtn.setOnClickListener(v -> {
            boolean newMode = !keyView.isBatchEditMode();
            keyView.setBatchEditMode(newMode);
            batchEditBtn.setAlpha(newMode ? 1.0f : 0.5f);
            if (newMode) {
                hideSettingsPanel();
                keyView.clearSelection();
            }
            updateDeleteButtonState();
        });

        zoomInBtn.setOnClickListener(v -> keyView.zoomIn());
        zoomOutBtn.setOnClickListener(v -> keyView.zoomOut());
        zoomResetBtn.setOnClickListener(v -> keyView.zoomReset());

        globalSettingsBtn.setOnClickListener(v -> {
            if (isPanelVisible) {
                hideSettingsPanel();
            }
            showGlobalSettingsPanel();
        });

        floatWindowBtn.setOnClickListener(v -> {
            if (checkOverlayPermission()) {
                toggleFloatWindow();
            }
        });
        
        shizukuBtn.setOnClickListener(v -> {
            Log.d(TAG, "Shizuku button clicked");
            requestShizukuPermission();
        });

        keyView.setOnSelectionChangedListener(hasSelection -> {
            updateDeleteButtonState();
            if (hasSelection) {
                if (isGlobalPanelVisible) {
                    hideGlobalSettingsPanel();
                }
                showSettingsPanel();
            } else {
                hideSettingsPanel();
            }
        });

        keyView.setOnKeyUpdatedListener(key -> {
            if (isPanelVisible) {
                settingsPanelController.syncFromKeyView();
            }
        });

        globalSettingsPanelController.setOnSettingsChangedListener(new GlobalSettingsPanel.OnSettingsChangedListener() {
            @Override
            public void onFloatScaleChanged(float scale) {
                FloatService service = FloatService.getInstance();
                if (service != null) {
                    service.updateScale();
                }
            }

            @Override
            public void onCountsReset() {
                keyView.invalidate();
            }

            @Override
            public void onFloatPositionReset() {
                FloatService service = FloatService.getInstance();
                if (service != null && service.getFloatView() != null) {
                    service.getFloatView().updatePositionFromSettings();
                }
            }

            @Override
            public void onConfigImported() {
                FloatService service = FloatService.getInstance();
                if (service != null && service.getFloatView() != null) {
                    service.getFloatView().updateScale();
                }
                if (globalSettings.floatWindowEnabled) {
                    stopService(new Intent(MainActivity.this, FloatService.class));
                    startService(new Intent(MainActivity.this, FloatService.class));
                }
                keyView.invalidate();
                Toast.makeText(MainActivity.this, "配置已导入", Toast.LENGTH_SHORT).show();
            }
        });

        updateDeleteButtonState();
        updateFloatWindowButton();
        batchEditBtn.setAlpha(0.5f);
        
        checkShizukuStatus();
    }
    
    private void checkShizukuStatus() {
        Log.d(TAG, "checkShizukuStatus");
        try {
            if (Shizuku.pingBinder()) {
                Log.d(TAG, "Shizuku binder is alive");
                int perm = Shizuku.checkSelfPermission();
                Log.d(TAG, "Permission: " + perm);
                if (perm == PackageManager.PERMISSION_GRANTED) {
                    shizukuBtn.setText("监听中");
                    shizukuBtn.setBackgroundColor(0xFF4CAF50);
                    inputListener.startListening();
                } else {
                    shizukuBtn.setText("授权 Shizuku");
                    shizukuBtn.setBackgroundColor(0xFFFF9800);
                }
            } else {
                Log.d(TAG, "Shizuku binder not alive");
                shizukuBtn.setText("请启动 Shizuku");
                shizukuBtn.setBackgroundColor(0xFFF44336);
            }
        } catch (Exception e) {
            Log.e(TAG, "checkShizukuStatus error", e);
            shizukuBtn.setText("Shizuku 错误");
            shizukuBtn.setBackgroundColor(0xFFF44336);
        }
    }
    
    private void requestShizukuPermission() {
        Log.d(TAG, "requestShizukuPermission");
        try {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(this, "请先启动 Shizuku", Toast.LENGTH_SHORT).show();
                return;
            }
            
            int perm = Shizuku.checkSelfPermission();
            if (perm == PackageManager.PERMISSION_GRANTED) {
                shizukuBtn.setText("监听中");
                shizukuBtn.setBackgroundColor(0xFF4CAF50);
                inputListener.startListening();
                Toast.makeText(this, "监听已启动", Toast.LENGTH_SHORT).show();
            } else {
                Shizuku.requestPermission(0);
                Toast.makeText(this, "请在弹出的窗口中授权", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "requestShizukuPermission error", e);
            Toast.makeText(this, "Shizuku 不可用", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    toggleFloatWindow();
                }
            }
        } else {
            globalSettingsPanelController.handleActivityResult(requestCode, resultCode, data);
        }
    }

    private void toggleFloatWindow() {
        globalSettings.floatWindowEnabled = !globalSettings.floatWindowEnabled;
        globalSettings.save();
        
        if (globalSettings.floatWindowEnabled) {
            startService(new Intent(this, FloatService.class));
        } else {
            stopService(new Intent(this, FloatService.class));
        }
        
        updateFloatWindowButton();
    }

    private void updateFloatWindowButton() {
        if (globalSettings.floatWindowEnabled) {
            floatWindowBtn.setText("关闭悬浮窗");
            floatWindowBtn.setBackgroundColor(0xFFD32F2F);
        } else {
            floatWindowBtn.setText("开启悬浮窗");
            floatWindowBtn.setBackgroundColor(0xFF4CAF50);
        }
    }

    private void updateDeleteButtonState() {
        if (keyView.isBatchEditMode()) {
            boolean hasSelection = !keyView.getSelectedKeys().isEmpty();
            deleteKeyBtn.setAlpha(hasSelection ? 1.0f : 0.5f);
            deleteKeyBtn.setEnabled(hasSelection);
        } else {
            boolean hasSelection = keyView.getSelectedKey() != null;
            deleteKeyBtn.setAlpha(hasSelection ? 1.0f : 0.5f);
            deleteKeyBtn.setEnabled(hasSelection);
        }
    }

    private void showSettingsPanel() {
        if (!isPanelVisible) {
            isPanelVisible = true;
            settingsPanelController.syncFromKeyView();
            settingsPanel.setVisibility(View.VISIBLE);
            settingsPanel.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_right));
        }
    }

    private void hideSettingsPanel() {
        if (isPanelVisible) {
            isPanelVisible = false;
            settingsPanelController.stopKeyListening();
            settingsPanel.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_out_right));
            settingsPanel.setVisibility(View.GONE);
        }
    }

    private void showGlobalSettingsPanel() {
        if (!isGlobalPanelVisible) {
            isGlobalPanelVisible = true;
            globalSettingsPanel.setVisibility(View.VISIBLE);
            globalSettingsPanel.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_right));
        }
    }

    private void hideGlobalSettingsPanel() {
        if (isGlobalPanelVisible) {
            isGlobalPanelVisible = false;
            globalSettingsPanel.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_out_right));
            globalSettingsPanel.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isPanelVisible && settingsPanelController != null && settingsPanelController.isListeningForKey()) {
            if (settingsPanelController.handleKeyEvent(event)) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (isPanelVisible) {
            keyView.clearSelection();
        } else if (isGlobalPanelVisible) {
            hideGlobalSettingsPanel();
        } else {
            super.onBackPressed();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Shizuku.removeBinderReceivedListener(BINDER_RECEIVED_LISTENER);
        Shizuku.removeBinderDeadListener(BINDER_DEAD_LISTENER);
        Shizuku.removeRequestPermissionResultListener(PERMISSION_LISTENER);
        if (inputListener != null) {
            inputListener.stopListening();
        }
        handler.removeCallbacksAndMessages(null);
    }
}