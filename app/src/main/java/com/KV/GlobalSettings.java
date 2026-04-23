package com.KV;

import android.content.Context;
import android.content.SharedPreferences;

public class GlobalSettings {
    
    private static GlobalSettings instance;
    private Context context;
    private SharedPreferences prefs;
    
    public boolean floatWindowEnabled = false;
    public float floatWindowScale = 1.0f;
    public int floatWindowX = 100;
    public int floatWindowY = 200;
    
    public int globalRainHeightDp = 200;
    public float globalRainWidthDp = 100;
    public float globalRainCornerRadiusDp = 4f;
    
    // 键盘校准
    public String keyboardDevicePath = "";
    public boolean keyboardCalibrated = false;
    
    private static final String PREFS_NAME = "kv_global_settings";
    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 2.0f;
    
    private GlobalSettings(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        load();
    }
    
    public static GlobalSettings getInstance(Context context) {
        if (instance == null) {
            instance = new GlobalSettings(context);
        }
        return instance;
    }
    
    private void load() {
        floatWindowEnabled = prefs.getBoolean("float_enabled", false);
        floatWindowScale = prefs.getFloat("float_scale", 1.0f);
        floatWindowX = prefs.getInt("float_x", 100);
        floatWindowY = prefs.getInt("float_y", 200);
        globalRainHeightDp = prefs.getInt("rain_height", 200);
        globalRainWidthDp = prefs.getFloat("rain_width", 100);
        globalRainCornerRadiusDp = prefs.getFloat("rain_radius", 4f);
        keyboardDevicePath = prefs.getString("keyboard_device_path", "");
        keyboardCalibrated = prefs.getBoolean("keyboard_calibrated", false);
    }
    
    public void save() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("float_enabled", floatWindowEnabled);
        editor.putFloat("float_scale", floatWindowScale);
        editor.putInt("float_x", floatWindowX);
        editor.putInt("float_y", floatWindowY);
        editor.putInt("rain_height", globalRainHeightDp);
        editor.putFloat("rain_width", globalRainWidthDp);
        editor.putFloat("rain_radius", globalRainCornerRadiusDp);
        editor.putString("keyboard_device_path", keyboardDevicePath);
        editor.putBoolean("keyboard_calibrated", keyboardCalibrated);
        editor.apply();
    }
    
    public void increaseScale() {
        floatWindowScale = Math.min(MAX_SCALE, floatWindowScale + 0.1f);
        save();
    }
    
    public void decreaseScale() {
        floatWindowScale = Math.max(MIN_SCALE, floatWindowScale - 0.1f);
        save();
    }
    
    public String getScalePercent() {
        return Math.round(floatWindowScale * 100) + "%";
    }
    
    public void resetFloatPosition() {
        floatWindowX = 100;
        floatWindowY = 200;
        save();
    }
}