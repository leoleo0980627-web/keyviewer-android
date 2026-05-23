package com.KV;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import rikka.shizuku.Shizuku;

public class GlobalSettingsPanel {

    private Activity activity;
    private View panelView;
    private KeyView keyView;
    private GlobalSettings settings;
    private RishInputListener inputListener;
    
    private FrameLayout darkModeContainer;
    private View darkModeSlider;
    private ImageView sunIcon;
    private ImageView moonIcon;
    private boolean darkModeAnimating = false;
    private float sliderStartX = 0;
    private float sliderEndX = 0;
    
    private TextView floatScaleText;
    private Button floatScaleUpBtn;
    private Button floatScaleDownBtn;
    private EditText rainHeightInput;
    private EditText rainReserveHeightInput;
    private Button resetCountsBtn;
    private Button resetFloatPositionBtn;
    private Button exportConfigBtn;
    private Button importConfigBtn;
    private Button calibrateKeyboardBtn;
    private Button closeBtn;
    
    private OnSettingsChangedListener listener;
    
    private static final int REQUEST_EXPORT = 1001;
    private static final int REQUEST_IMPORT = 1002;
    private static final String TAG = "KV_GlobalSettings";
    
    public interface OnSettingsChangedListener {
        void onFloatScaleChanged(float scale);
        void onCountsReset();
        void onFloatPositionReset();
        void onConfigImported();
        void onDarkModeChanged(boolean isDarkMode);
    }
    
    public GlobalSettingsPanel(Activity activity, View panelView, KeyView keyView) {
        this.activity = activity;
        this.panelView = panelView;
        this.keyView = keyView;
        this.settings = GlobalSettings.getInstance(activity);
        
        initViews();
        setupListeners();
        updateScaleText();
        updateDarkModeUI(settings.darkModeEnabled);
    }
    
    public void setInputListener(RishInputListener listener) {
        this.inputListener = listener;
    }
    
    public void setOnSettingsChangedListener(OnSettingsChangedListener listener) {
        this.listener = listener;
    }
    
    private void initViews() {
        darkModeContainer = panelView.findViewById(R.id.darkModeContainer);
        darkModeSlider = panelView.findViewById(R.id.darkModeSlider);
        sunIcon = panelView.findViewById(R.id.sunIcon);
        moonIcon = panelView.findViewById(R.id.moonIcon);
        closeBtn = panelView.findViewById(R.id.closeGlobalSettingsBtn);
        
        floatScaleText = panelView.findViewById(R.id.floatScaleText);
        floatScaleUpBtn = panelView.findViewById(R.id.floatScaleUpBtn);
        floatScaleDownBtn = panelView.findViewById(R.id.floatScaleDownBtn);
        rainHeightInput = panelView.findViewById(R.id.rainHeightInput);
        rainReserveHeightInput = panelView.findViewById(R.id.rainReserveHeightInput);
        resetCountsBtn = panelView.findViewById(R.id.resetCountsBtn);
        resetFloatPositionBtn = panelView.findViewById(R.id.resetFloatPositionBtn);
        exportConfigBtn = panelView.findViewById(R.id.exportConfigBtn);
        importConfigBtn = panelView.findViewById(R.id.importConfigBtn);
        calibrateKeyboardBtn = panelView.findViewById(R.id.calibrateKeyboardBtn);
        
        rainHeightInput.setText(String.valueOf(settings.globalRainHeightDp));
        rainReserveHeightInput.setText(String.valueOf(settings.globalRainReserveHeightDp));
        
        panelView.post(() -> calculateSliderRange());
    }
    
    private void calculateSliderRange() {
        if (darkModeContainer == null || darkModeSlider == null) return;
        int containerWidth = darkModeContainer.getWidth();
        int sliderWidth = darkModeSlider.getWidth();
        sliderStartX = 0;
        sliderEndX = containerWidth - sliderWidth;
    }
    
    private void setupListeners() {
        darkModeContainer.setOnClickListener(v -> {
            if (darkModeAnimating) return;
            toggleDarkMode();
        });
        
        closeBtn.setOnClickListener(v -> {
            panelView.setVisibility(View.GONE);
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
        
        rainReserveHeightInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                try {
                    int reserve = Integer.parseInt(rainReserveHeightInput.getText().toString());
                    settings.globalRainReserveHeightDp = reserve;
                    settings.save();
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
    
    private void toggleDarkMode() {
        boolean newMode = !settings.darkModeEnabled;
        animateSliderToTarget(newMode);
        applyDarkMode(newMode);
    }
    
    private void animateSliderToTarget(boolean toDark) {
        if (darkModeAnimating) return;
        darkModeAnimating = true;
        
        float targetX = toDark ? sliderEndX : sliderStartX;
        ObjectAnimator animator = ObjectAnimator.ofFloat(darkModeSlider, "translationX", targetX);
        animator.setDuration(300);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                darkModeAnimating = false;
            }
        });
        animator.start();
    }
    
    private void applyDarkMode(boolean isDarkMode) {
        settings.darkModeEnabled = isDarkMode;
        settings.save();
        
        int iconColor = isDarkMode ? 0xFFFFFFFF : 0xFF757575;
        sunIcon.setColorFilter(iconColor);
        moonIcon.setColorFilter(iconColor);
        
        animateThemeTransition(isDarkMode);
        
        if (listener != null) {
            listener.onDarkModeChanged(isDarkMode);
        }
    }
    
    private void animateThemeTransition(boolean isDarkMode) {
        int panelBgEnd = isDarkMode ? 0xFF2C2C2C : 0xFFF5F5F5;
        int textColorEnd = isDarkMode ? 0xFFFFFFFF : 0xFF333333;
        int inputBgEnd = isDarkMode ? 0xFF3C3C3C : 0xFFFFFFFF;
        int inputTextEnd = isDarkMode ? 0xFFFFFFFF : 0xFF333333;
        
        animateBackgroundColor(panelView, panelBgEnd);
        
        TextView title = panelView.findViewById(R.id.closeGlobalSettingsBtn);
        if (title != null) {
            animateTextColor(title, textColorEnd);
        }
        
        updateAllChildViews(panelView, isDarkMode);
    }
    
    private void updateAllChildViews(View view, boolean isDarkMode) {
        if (view instanceof EditText) {
            EditText et = (EditText) view;
            int bgColor = isDarkMode ? 0xFF3C3C3C : 0xFFFFFFFF;
            int textColor = isDarkMode ? 0xFFFFFFFF : 0xFF333333;
            animateBackgroundColor(et, bgColor);
            animateTextColor(et, textColor);
        } else if (view instanceof TextView) {
            TextView tv = (TextView) view;
            int textColor = isDarkMode ? 0xFFFFFFFF : 0xFF333333;
            animateTextColor(tv, textColor);
        } else if (view instanceof Button) {
            Button btn = (Button) view;
            int textColor = isDarkMode ? 0xFFFFFFFF : 0xFF333333;
            animateTextColor(btn, textColor);
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                updateAllChildViews(vg.getChildAt(i), isDarkMode);
            }
        }
    }
    
    private void animateBackgroundColor(View target, int toColor) {
        ValueAnimator animator = ValueAnimator.ofArgb(target.getSolidColor(), toColor);
        animator.setDuration(300);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            target.setBackgroundColor((int) animation.getAnimatedValue());
        });
        animator.start();
    }
    
    private void animateTextColor(TextView target, int toColor) {
        ValueAnimator animator = ValueAnimator.ofArgb(target.getCurrentTextColor(), toColor);
        animator.setDuration(300);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            target.setTextColor((int) animation.getAnimatedValue());
        });
        animator.start();
    }
    
    private void updateDarkModeUI(boolean isDarkMode) {
        int iconColor = isDarkMode ? 0xFFFFFFFF : 0xFF757575;
        sunIcon.setColorFilter(iconColor);
        moonIcon.setColorFilter(iconColor);
        
        float targetX = isDarkMode ? sliderEndX : sliderStartX;
        darkModeSlider.setTranslationX(targetX);
        
        if (isDarkMode) {
            panelView.setBackgroundColor(0xFF2C2C2C);
            closeBtn.setTextColor(0xFFFFFFFF);
            floatScaleText.setTextColor(0xFFFFFFFF);
            rainHeightInput.setBackgroundColor(0xFF3C3C3C);
            rainHeightInput.setTextColor(0xFFFFFFFF);
            rainReserveHeightInput.setBackgroundColor(0xFF3C3C3C);
            rainReserveHeightInput.setTextColor(0xFFFFFFFF);
        } else {
            panelView.setBackgroundColor(0xFFF5F5F5);
            closeBtn.setTextColor(0xFF666666);
            floatScaleText.setTextColor(0xFF333333);
            rainHeightInput.setBackgroundColor(0xFFFFFFFF);
            rainHeightInput.setTextColor(0xFF333333);
            rainReserveHeightInput.setBackgroundColor(0xFFFFFFFF);
            rainReserveHeightInput.setTextColor(0xFF333333);
        }
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
        if (resultCode != Activity.RESULT_OK || data == null) return;
        
        android.net.Uri uri = data.getData();
        if (uri == null) return;
        
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
            globalJson.put("globalRainReserveHeightDp", settings.globalRainReserveHeightDp);
            globalJson.put("darkModeEnabled", settings.darkModeEnabled);
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
                
                keyJson.put("fillColorUp", key.fillColorUp);
                keyJson.put("borderColorUp", key.borderColorUp);
                keyJson.put("textColorUp", key.textColorUp);
                keyJson.put("countColorUp", key.countColorUp);
                keyJson.put("fillColorDown", key.fillColorDown);
                keyJson.put("borderColorDown", key.borderColorDown);
                keyJson.put("textColorDown", key.textColorDown);
                keyJson.put("countColorDown", key.countColorDown);
                
                keyJson.put("textSizeSp", key.textSizeSp);
                keyJson.put("showCount", key.showCount);
                keyJson.put("mappingEnabled", key.mappingEnabled);
                keyJson.put("mappedKey", key.mappedKey);
                keyJson.put("rainEnabled", key.rainEnabled);
                keyJson.put("rainOffsetXDp", key.rainOffsetXDp);
                keyJson.put("rainOffsetYDp", key.rainOffsetYDp);
                keyJson.put("rainWidthPercent", key.rainWidthPercent);
                keyJson.put("rainColor", key.rainColor);
                keyJson.put("rainSpeedDp", key.rainSpeedDp);
                keyJson.put("kpsTotalNoWrap", key.kpsTotalNoWrap);
                keyJson.put("kpsTotalLabelOffsetX", key.kpsTotalLabelOffsetX);
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
            
            return root.toString(2);
            
        } catch (Exception e) {
            Log.e(TAG, "buildConfigJson failed", e);
            return "{}";
        }
    }
    
    private void shareConfig() {
        String json = buildConfigJson();
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, json);
        activity.startActivity(Intent.createChooser(shareIntent, "导出配置"));
    }
    
    private void writeConfigToUri(android.net.Uri uri) {
        try {
            String json = buildConfigJson();
            if (json.equals("{}")) {
                Toast.makeText(activity, "配置数据为空", Toast.LENGTH_SHORT).show();
                return;
            }
            
            try {
                activity.getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (SecurityException e) {
                Log.e(TAG, "takePersistableUriPermission failed: " + e.getMessage());
            }
            
            OutputStream os = openExportOutputStream(uri);
            if (os == null) {
                Toast.makeText(activity, "无法写入文件", Toast.LENGTH_LONG).show();
                return;
            }
            
            try (OutputStream outputStream = os) {
                outputStream.write(json.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            }
            Toast.makeText(activity, "配置导出成功", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Log.e(TAG, "writeConfigToUri failed", e);
            Toast.makeText(activity, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private OutputStream openExportOutputStream(android.net.Uri uri) {
        try {
            return activity.getContentResolver().openOutputStream(uri, "wt");
        } catch (Exception e) {
            Log.w(TAG, "openOutputStream wt failed, falling back: " + e.getMessage());
        }

        try {
            return activity.getContentResolver().openOutputStream(uri);
        } catch (Exception e) {
            Log.e(TAG, "openOutputStream default failed", e);
            return null;
        }
    }
    
    private void importConfigFromUri(android.net.Uri uri) {
        try {
            try {
                activity.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException e) {
                Log.e(TAG, "takePersistableUriPermission failed: " + e.getMessage());
            }
            
            InputStream is = activity.getContentResolver().openInputStream(uri);
            if (is == null) {
                Toast.makeText(activity, "无法读取文件", Toast.LENGTH_SHORT).show();
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
            if (json.isEmpty()) {
                Toast.makeText(activity, "文件内容为空", Toast.LENGTH_SHORT).show();
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
                settings.globalRainReserveHeightDp = globalJson.optInt("globalRainReserveHeightDp", 10);
                settings.darkModeEnabled = globalJson.optBoolean("darkModeEnabled", false);
                settings.save();
                
                rainHeightInput.setText(String.valueOf(settings.globalRainHeightDp));
                rainReserveHeightInput.setText(String.valueOf(settings.globalRainReserveHeightDp));
                updateScaleText();
                updateDarkModeUI(settings.darkModeEnabled);
                if (listener != null) listener.onDarkModeChanged(settings.darkModeEnabled);
            }
            
            KeyManager km = keyView.getKeyManager();
            km.getKeys().clear();
            
            if (root.has("keys")) {
                JSONArray keysArray = root.getJSONArray("keys");
                float density = activity.getResources().getDisplayMetrics().density;
                
                for (int i = 0; i < keysArray.length(); i++) {
                    JSONObject keyJson = keysArray.getJSONObject(i);
                    KeyData key = importKeyFromJson(keyJson, density);
                    km.getKeys().add(key);
                }
            }
            
            km.resetAllCounts();
            if (root.has("keyCounts")) {
                JSONObject countsJson = root.getJSONObject("keyCounts");
                java.util.Iterator<String> keys = countsJson.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    int count = countsJson.optInt(key, 0);
                    for (int i = 0; i < count; i++) {
                        km.recordKeyPress(key);
                    }
                }
            }
            
            km.saveToPreferences();
            keyView.clearSelection();
            keyView.invalidate();
            
            if (listener != null) listener.onConfigImported();
            Toast.makeText(activity, "配置导入成功", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Log.e(TAG, "importConfigFromUri failed", e);
            Toast.makeText(activity, "导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private KeyData importKeyFromJson(JSONObject keyJson, float density) throws Exception {
        String id = keyJson.optString("id", java.util.UUID.randomUUID().toString());
        float cx = (float) keyJson.optDouble("centerX", 100) * density;
        float cy = (float) keyJson.optDouble("centerY", 100) * density;
        float w = (float) keyJson.optDouble("width", 50) * density;
        float h = (float) keyJson.optDouble("height", 50) * density;
        
        KeyData key = new KeyData(id, cx, cy, w, h);
        
        key.boundKey = keyJson.optString("boundKey", "未绑定");
        key.displayLabel = keyJson.optString("displayLabel", "");
        
        int oldFillColor = keyJson.optInt("fillColor", 0xFF999999);
        int oldBorderColor = keyJson.optInt("borderColor", 0xFF666666);
        
        key.fillColorUp = keyJson.optInt("fillColorUp", oldFillColor);
        key.borderColorUp = keyJson.optInt("borderColorUp", oldBorderColor);
        key.textColorUp = keyJson.optInt("textColorUp", 0xFFFFFFFF);
        key.countColorUp = keyJson.optInt("countColorUp", key.textColorUp);
        key.fillColorDown = keyJson.optInt("fillColorDown", 0xFFFFFFFF);
        key.borderColorDown = keyJson.optInt("borderColorDown", key.borderColorUp);
        key.textColorDown = keyJson.optInt("textColorDown", key.textColorUp);
        key.countColorDown = keyJson.optInt("countColorDown", key.countColorUp);
        
        key.textSizeSp = (float) keyJson.optDouble("textSizeSp", 14);
        key.showCount = keyJson.optBoolean("showCount", true);
        key.mappingEnabled = keyJson.optBoolean("mappingEnabled", false);
        key.mappedKey = keyJson.optString("mappedKey", "");
        key.rainEnabled = keyJson.optBoolean("rainEnabled", false);
        key.rainOffsetXDp = (float) keyJson.optDouble("rainOffsetXDp", 0);
        key.rainOffsetYDp = (float) keyJson.optDouble("rainOffsetYDp", 0);
        key.rainWidthPercent = (float) keyJson.optDouble("rainWidthPercent", 100);
        key.rainColor = keyJson.optInt("rainColor", 0xFFFFFFFF);
        key.rainSpeedDp = (float) keyJson.optDouble("rainSpeedDp", 5);
        key.kpsTotalNoWrap = keyJson.optBoolean("kpsTotalNoWrap", false);
        key.kpsTotalLabelOffsetX = (float) keyJson.optDouble("kpsTotalLabelOffsetX", 0);
        key.zIndex = keyJson.optInt("zIndex", 0);
        
        return key;
    }
}
