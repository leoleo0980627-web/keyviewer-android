package com.KV;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import rikka.shizuku.Shizuku;

public class GlobalSettingsPanel {

    private Activity activity;
    private View panelView;
    private KeyView keyView;
    private GlobalSettings settings;
    private RishInputListener inputListener;
    
    private TextView floatScaleText;
    private Button floatScaleUpBtn;
    private Button floatScaleDownBtn;
    private EditText rainHeightInput;
    private Button resetCountsBtn;
    private Button resetFloatPositionBtn;
    private Button exportConfigBtn;
    private Button importConfigBtn;
    private Button calibrateKeyboardBtn;
    private Button closeGlobalSettingsBtn;
    
    private OnSettingsChangedListener listener;
    private OnCloseRequestedListener closeListener;
    
    private static final int REQUEST_EXPORT = 1001;
    private static final int REQUEST_IMPORT = 1002;
    private static final String TAG = "KV_GlobalSettings";
    
    public interface OnSettingsChangedListener {
        void onFloatScaleChanged(float scale);
        void onCountsReset();
        void onFloatPositionReset();
        void onConfigImported();
    }
    
    public interface OnCloseRequestedListener {
        void onCloseRequested();
    }
    
    public GlobalSettingsPanel(Activity activity, View panelView, KeyView keyView) {
        this.activity = activity;
        this.panelView = panelView;
        this.keyView = keyView;
        this.settings = GlobalSettings.getInstance(activity);
        
        initViews();
        setupListeners();
        updateScaleText();
    }
    
    public void setInputListener(RishInputListener listener) {
        this.inputListener = listener;
    }
    
    public void setOnSettingsChangedListener(OnSettingsChangedListener listener) {
        this.listener = listener;
    }
    
    public void setOnCloseRequestedListener(OnCloseRequestedListener listener) {
        this.closeListener = listener;
    }
    
    private void initViews() {
        floatScaleText = panelView.findViewById(R.id.floatScaleText);
        floatScaleUpBtn = panelView.findViewById(R.id.floatScaleUpBtn);
        floatScaleDownBtn = panelView.findViewById(R.id.floatScaleDownBtn);
        rainHeightInput = panelView.findViewById(R.id.rainHeightInput);
        resetCountsBtn = panelView.findViewById(R.id.resetCountsBtn);
        resetFloatPositionBtn = panelView.findViewById(R.id.resetFloatPositionBtn);
        exportConfigBtn = panelView.findViewById(R.id.exportConfigBtn);
        importConfigBtn = panelView.findViewById(R.id.importConfigBtn);
        calibrateKeyboardBtn = panelView.findViewById(R.id.calibrateKeyboardBtn);
        closeGlobalSettingsBtn = panelView.findViewById(R.id.closeGlobalSettingsBtn);
        
        rainHeightInput.setText(String.valueOf(settings.globalRainHeightDp));
    }
    
    private void setupListeners() {
        closeGlobalSettingsBtn.setOnClickListener(v -> {
            if (closeListener != null) {
                closeListener.onCloseRequested();
            }
        });
        
        floatScaleUpBtn.setOnClickListener(v -> {
            settings.increaseScale();
            updateScaleText();
            if (listener != null) listener.onFloatScaleChanged(settings.floatWindowScale);
            FloatService service = FloatService.getInstance();
            if (service != null) service.updateScale();
        });
        
        floatScaleDownBtn.setOnClickListener(v -> {
            settings.decreaseScale();
            updateScaleText();
            if (listener != null) listener.onFloatScaleChanged(settings.floatWindowScale);
            FloatService service = FloatService.getInstance();
            if (service != null) service.updateScale();
        });
        
        rainHeightInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                try {
                    int height = Integer.parseInt(rainHeightInput.getText().toString());
                    settings.globalRainHeightDp = height;
                    settings.save();
                    FloatService service = FloatService.getInstance();
                    if (service != null && service.getFloatView() != null) {
                        service.getFloatView().updateScale();
                    }
                } catch (NumberFormatException ignored) {}
                return true;
            }
            return false;
        });
        
        resetCountsBtn.setOnClickListener(v -> {
            keyView.getKeyManager().resetAllCounts();
            keyView.invalidate();
            if (listener != null) listener.onCountsReset();
        });
        
        resetFloatPositionBtn.setOnClickListener(v -> {
            settings.resetFloatPosition();
            if (listener != null) listener.onFloatPositionReset();
            FloatService service = FloatService.getInstance();
            if (service != null && service.getFloatView() != null) {
                service.getFloatView().updatePositionFromSettings();
            }
        });
        
        calibrateKeyboardBtn.setOnClickListener(v -> startCalibration());
        
        exportConfigBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "kv_config_" + System.currentTimeMillis() + ".json");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            activity.startActivityForResult(intent, REQUEST_EXPORT);
        });
        
        exportConfigBtn.setOnLongClickListener(v -> {
            shareConfig();
            return true;
        });
        
        importConfigBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivityForResult(intent, REQUEST_IMPORT);
        });
    }
    
    private void startCalibration() {
        Toast.makeText(activity, "请按键盘上的任意键（如空格）", Toast.LENGTH_LONG).show();
        
        new Thread(() -> {
            String device = findKeyboardDeviceByUserInput();
            if (device != null) {
                settings.keyboardDevicePath = device;
                settings.keyboardCalibrated = true;
                settings.save();
                activity.runOnUiThread(() -> {
                    Toast.makeText(activity, "已校准: " + device, Toast.LENGTH_SHORT).show();
                    if (inputListener != null) {
                        inputListener.stopListening();
                        inputListener.startListening();
                    }
                });
            } else {
                activity.runOnUiThread(() -> 
                    Toast.makeText(activity, "未检测到按键，请重试", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
    
    private String findKeyboardDeviceByUserInput() {
        try {
            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            Method method = shizukuClass.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            method.setAccessible(true);
            
            for (int i = 0; i < 32; i++) {
                String devicePath = "/dev/input/event" + i;
                
                String[] testCmd = {"sh", "-c", "test -e " + devicePath + " && echo ok"};
                Process testP = (Process) method.invoke(null, testCmd, null, null);
                BufferedReader testReader = new BufferedReader(new InputStreamReader(testP.getInputStream()));
                String result = testReader.readLine();
                testP.destroy();
                if (!"ok".equals(result)) continue;
                
                String[] cmd = {"sh", "-c", "timeout 3 getevent " + devicePath};
                Process p = (Process) method.invoke(null, cmd, null, null);
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 3) {
                        try {
                            int type = Integer.parseInt(parts[0], 16);
                            int value = Integer.parseInt(parts[2], 16);
                            if (type == 1 && (value == 1 || value == 0)) {
                                p.destroy();
                                return devicePath;
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
                p.destroy();
            }
        } catch (Exception e) {
            Log.e(TAG, "findKeyboardDeviceByUserInput error", e);
        }
        return null;
    }
    
    private void updateScaleText() {
        floatScaleText.setText(settings.getScalePercent());
    }
    
    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.d(TAG, "handleActivityResult: resultCode=" + resultCode + ", data=" + data);
            return;
        }
        
        android.net.Uri uri = data.getData();
        if (uri == null) {
            Log.d(TAG, "handleActivityResult: uri is null");
            return;
        }
        
        if (requestCode == REQUEST_EXPORT) {
            writeConfigToUri(uri);
        } else if (requestCode == REQUEST_IMPORT) {
            importConfigFromUri(uri);
        }
    }
    
    private String buildConfigJson() {
        try {
            JSONObject root = new JSONObject();
            
            JSONObject globalJson = new JSONObject();
            globalJson.put("floatWindowEnabled", settings.floatWindowEnabled);
            globalJson.put("floatWindowScale", settings.floatWindowScale);
            globalJson.put("floatWindowX", settings.floatWindowX);
            globalJson.put("floatWindowY", settings.floatWindowY);
            globalJson.put("globalRainHeightDp", settings.globalRainHeightDp);
            root.put("globalSettings", globalJson);
            
            KeyManager km = keyView.getKeyManager();
            JSONArray keysArray = new JSONArray();
            float density = activity.getResources().getDisplayMetrics().density;
            
            for (KeyData key : km.getKeys()) {
                JSONObject keyJson = new JSONObject();
                keyJson.put("id", key.id);
                keyJson.put("centerX", key.centerX / density);
                keyJson.put("centerY", key.centerY / density);
                keyJson.put("width", key.width / density);
                keyJson.put("height", key.height / density);
                keyJson.put("boundKey", key.boundKey);
                keyJson.put("displayLabel", key.displayLabel);
                keyJson.put("fillColor", key.fillColor);
                keyJson.put("borderColor", key.borderColor);
                keyJson.put("cornerRadiusDp", key.cornerRadiusDp);
                keyJson.put("borderWidthDp", key.borderWidthDp);
                keyJson.put("textSizeSp", key.textSizeSp);
                keyJson.put("textColor", key.textColor);
                keyJson.put("showCount", key.showCount);
                keyJson.put("mappingEnabled", key.mappingEnabled);
                keyJson.put("mappedKey", key.mappedKey);
                keyJson.put("rainEnabled", key.rainEnabled);
                keyJson.put("rainOffsetXDp", key.rainOffsetXDp);
                keyJson.put("rainOffsetYDp", key.rainOffsetYDp);
                keyJson.put("rainWidthPercent", key.rainWidthPercent);
                keyJson.put("rainColor", key.rainColor);
                keyJson.put("rainSpeedDp", key.rainSpeedDp);
                keyJson.put("zIndex", key.zIndex);
                keysArray.put(keyJson);
            }
            root.put("keys", keysArray);
            
            JSONObject countsJson = new JSONObject();
            for (Map.Entry<String, Integer> entry : km.getKeyCodeCount().entrySet()) {
                countsJson.put(entry.getKey(), entry.getValue());
            }
            root.put("keyCounts", countsJson);
            root.put("totalPressCount", km.getTotalPressCount());
            
            String json = root.toString(2);
            Log.d(TAG, "buildConfigJson: success, length=" + json.length());
            return json;
            
        } catch (Exception e) {
            Log.e(TAG, "buildConfigJson failed", e);
            return "{}";
        }
    }
    
    private void shareConfig() {
        new Thread(() -> {
            String json = buildConfigJson();
            activity.runOnUiThread(() -> {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, json);
                activity.startActivity(Intent.createChooser(shareIntent, "导出配置"));
                Log.d(TAG, "Config shared via intent, length: " + json.length());
            });
        }).start();
    }
    
    private void writeConfigToUri(android.net.Uri uri) {
        activity.runOnUiThread(() -> {
            Toast.makeText(activity, "正在导出配置...", Toast.LENGTH_SHORT).show();
        });
        
        new Thread(() -> {
            OutputStream os = null;
            java.io.BufferedOutputStream bos = null;
            try {
                Log.d(TAG, "writeConfigToUri: " + uri);
                
                String json = buildConfigJson();
                if (json.equals("{}")) {
                    activity.runOnUiThread(() -> 
                        Toast.makeText(activity, "配置数据为空", Toast.LENGTH_SHORT).show());
                    return;
                }
                
                try {
                    activity.getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    );
                } catch (SecurityException e) {
                    Log.e(TAG, "takePersistableUriPermission failed: " + e.getMessage());
                }
                
                try {
                    os = activity.getContentResolver().openOutputStream(uri, "wt");
                } catch (Exception e1) {
                    try {
                        os = activity.getContentResolver().openOutputStream(uri, "w");
                    } catch (Exception e2) {
                        try {
                            os = activity.getContentResolver().openOutputStream(uri, "rwt");
                        } catch (Exception e3) {
                            os = activity.getContentResolver().openOutputStream(uri);
                        }
                    }
                }
                
                if (os == null) {
                    Log.e(TAG, "OutputStream is null");
                    activity.runOnUiThread(() -> 
                        Toast.makeText(activity, "无法写入文件，请尝试保存到其他位置", Toast.LENGTH_LONG).show());
                    return;
                }
                
                bos = new java.io.BufferedOutputStream(os);
                byte[] data = json.getBytes("UTF-8");
                bos.write(data);
                bos.flush();
                
                Log.d(TAG, "writeConfigToUri: success, wrote " + data.length + " bytes");
                activity.runOnUiThread(() -> 
                    Toast.makeText(activity, "配置导出成功 (" + data.length + " bytes)", Toast.LENGTH_SHORT).show());
                
            } catch (Exception e) {
                Log.e(TAG, "writeConfigToUri failed", e);
                activity.runOnUiThread(() -> 
                    Toast.makeText(activity, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                if (bos != null) {
                    try { bos.close(); } catch (Exception e) { Log.e(TAG, "bos close failed", e); }
                }
                if (os != null) {
                    try { os.close(); } catch (Exception e) { Log.e(TAG, "os close failed", e); }
                }
            }
        }).start();
    }
    
    private void importConfigFromUri(android.net.Uri uri) {
        activity.runOnUiThread(() -> {
            Toast.makeText(activity, "正在导入配置...", Toast.LENGTH_SHORT).show();
        });
        
        new Thread(() -> {
            try {
                Log.d(TAG, "importConfigFromUri: " + uri);
                
                try {
                    activity.getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                } catch (SecurityException e) {
                    Log.e(TAG, "takePersistableUriPermission failed: " + e.getMessage());
                }
                
                InputStream is = activity.getContentResolver().openInputStream(uri);
                if (is == null) {
                    activity.runOnUiThread(() -> 
                        Toast.makeText(activity, "无法读取文件", Toast.LENGTH_SHORT).show());
                    return;
                }
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                
                String json = sb.toString();
                Log.d(TAG, "importConfigFromUri: read " + json.length() + " bytes");
                
                if (json.isEmpty()) {
                    activity.runOnUiThread(() -> 
                        Toast.makeText(activity, "文件内容为空", Toast.LENGTH_SHORT).show());
                    return;
                }
                
                JSONObject root = new JSONObject(json);
                
                if (root.has("globalSettings")) {
                    JSONObject globalJson = root.getJSONObject("globalSettings");
                    settings.floatWindowEnabled = globalJson.optBoolean("floatWindowEnabled", false);
                    settings.floatWindowScale = (float) globalJson.optDouble("floatWindowScale", 1.0);
                    settings.floatWindowX = globalJson.optInt("floatWindowX", 100);
                    settings.floatWindowY = globalJson.optInt("floatWindowY", 200);
                    settings.globalRainHeightDp = globalJson.optInt("globalRainHeightDp", 200);
                    settings.save();
                    
                    activity.runOnUiThread(() -> {
                        rainHeightInput.setText(String.valueOf(settings.globalRainHeightDp));
                        updateScaleText();
                    });
                }
                
                KeyManager km = keyView.getKeyManager();
                
                activity.runOnUiThread(() -> km.getKeys().clear());
                
                if (root.has("keys")) {
                    JSONArray keysArray = root.getJSONArray("keys");
                    float density = activity.getResources().getDisplayMetrics().density;
                    
                    List<KeyData> importedKeys = new ArrayList<>();
                    
                    for (int i = 0; i < keysArray.length(); i++) {
                        JSONObject keyJson = keysArray.getJSONObject(i);
                        
                        String id = keyJson.optString("id", java.util.UUID.randomUUID().toString());
                        float cx = (float) keyJson.optDouble("centerX", 100) * density;
                        float cy = (float) keyJson.optDouble("centerY", 100) * density;
                        float w = (float) keyJson.optDouble("width", 50) * density;
                        float h = (float) keyJson.optDouble("height", 50) * density;
                        
                        KeyData key = new KeyData(id, cx, cy, w, h);
                        key.boundKey = keyJson.optString("boundKey", "未绑定");
                        key.displayLabel = keyJson.optString("displayLabel", "");
                        key.fillColor = keyJson.optInt("fillColor", 0xFF999999);
                        key.borderColor = keyJson.optInt("borderColor", 0xFF666666);
                        key.cornerRadiusDp = (float) keyJson.optDouble("cornerRadiusDp", 8);
                        key.borderWidthDp = (float) keyJson.optDouble("borderWidthDp", 5);
                        key.textSizeSp = (float) keyJson.optDouble("textSizeSp", 14);
                        key.textColor = keyJson.optInt("textColor", 0xFFFFFFFF);
                        key.showCount = keyJson.optBoolean("showCount", true);
                        key.mappingEnabled = keyJson.optBoolean("mappingEnabled", false);
                        key.mappedKey = keyJson.optString("mappedKey", "");
                        key.rainEnabled = keyJson.optBoolean("rainEnabled", false);
                        key.rainOffsetXDp = (float) keyJson.optDouble("rainOffsetXDp", 0);
                        key.rainOffsetYDp = (float) keyJson.optDouble("rainOffsetYDp", 0);
                        key.rainWidthPercent = (float) keyJson.optDouble("rainWidthPercent", 100);
                        key.rainColor = keyJson.optInt("rainColor", 0xFFFFFFFF);
                        key.rainSpeedDp = (float) keyJson.optDouble("rainSpeedDp", 5);
                        key.zIndex = keyJson.optInt("zIndex", 0);
                        
                        importedKeys.add(key);
                    }
                    
                    activity.runOnUiThread(() -> {
                        km.getKeys().addAll(importedKeys);
                        // 导入后强制对齐网格
                        float density2 = activity.getResources().getDisplayMetrics().density;
                        int gridSizePx = (int) (15 * density2);
                        for (KeyData k : km.getKeys()) {
                            k.centerX = Math.round(k.centerX / gridSizePx) * gridSizePx;
                            k.centerY = Math.round(k.centerY / gridSizePx) * gridSizePx;
                        }
                    });
                }
                
                if (root.has("keyCounts")) {
                    JSONObject countsJson = root.getJSONObject("keyCounts");
                    Map<String, Integer> counts = new java.util.HashMap<>();
                    java.util.Iterator<String> keys = countsJson.keys();
                    int total = 0;
                    while (keys.hasNext()) {
                        String key = keys.next();
                        int count = countsJson.optInt(key, 0);
                        counts.put(key, count);
                        total += count;
                    }
                    km.setKeyCodeCounts(counts);
                    km.setTotalPressCount(total);
                } else {
                    km.resetAllCounts();
                }
                
                activity.runOnUiThread(() -> {
                    km.saveToPreferences();
                    km.saveCountsToPreferences();
                    keyView.clearSelection();
                    keyView.invalidate();
                    
                    if (listener != null) listener.onConfigImported();
                    
                    Toast.makeText(activity, "配置导入成功", Toast.LENGTH_SHORT).show();
                });
                
                Log.d(TAG, "importConfigFromUri: success");
                
            } catch (Exception e) {
                Log.e(TAG, "importConfigFromUri failed", e);
                activity.runOnUiThread(() -> 
                    Toast.makeText(activity, "导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}