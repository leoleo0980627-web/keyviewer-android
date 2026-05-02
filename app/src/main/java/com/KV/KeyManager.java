package com.KV;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KeyManager {
    private static final String TAG = "KV_KeyManager";
    private List<KeyData> keys = new ArrayList<>();
    private Map<String, Integer> keyCodeCount = new HashMap<>();
    private String selectedKeyId = null;
    
    private int totalPressCount = 0;
    private LinkedList<Long> recentPressTimes = new LinkedList<>();
    private static final int MAX_RECENT_SIZE = 300;
    
    private static final String PREFS_NAME = "keyviewer_prefs";
    private static final String KEY_TOTAL = "total_press_count";
    private static final String KEY_SELECTED = "selected_key_id";
    
    private Context context;
    
    private List<KeyData> clipboard = new ArrayList<>();
    
    public KeyManager() {
        this(null);
    }
    
    public KeyManager(Context context) {
        this.context = context;
    }
    
    public void setContext(Context context) {
        this.context = context;
    }
    
    public List<KeyData> getClipboard() {
        return clipboard;
    }
    
    public void copyToClipboard(List<KeyData> source) {
        clipboard.clear();
        for (KeyData key : source) {
            clipboard.add(key.clone());
        }
        Log.d(TAG, "copyToClipboard: " + clipboard.size() + " keys");
    }
    
    public void pasteFromClipboard() {
        for (KeyData key : clipboard) {
            KeyData newKey = key.clone();
            keys.add(newKey);
        }
        saveToPreferences();
        Log.d(TAG, "pasteFromClipboard: " + clipboard.size() + " keys");
    }
    
    public KeyData createNewKey(float centerX, float centerY, float width, float height) {
        String id = UUID.randomUUID().toString();
        KeyData key = new KeyData(id, centerX, centerY, width, height);
        key.zIndex = 0;
        keys.add(key);
        saveToPreferences();
        Log.d(TAG, "createNewKey: " + id);
        return key;
    }
    
    public void deleteKey(String id) {
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).id.equals(id)) {
                keys.remove(i);
                break;
            }
        }
        if (id.equals(selectedKeyId)) {
            selectedKeyId = null;
        }
        saveToPreferences();
        Log.d(TAG, "deleteKey: " + id);
    }
    
    public KeyData getSelectedKey() {
        if (selectedKeyId == null) return null;
        for (KeyData key : keys) {
            if (key.id.equals(selectedKeyId)) return key;
        }
        return null;
    }
    
    public void setSelectedKey(String id) {
        this.selectedKeyId = id;
        Log.d(TAG, "setSelectedKey: " + id);
    }
    
    public void clearSelection() {
        this.selectedKeyId = null;
        Log.d(TAG, "clearSelection");
    }
    
    public List<KeyData> getKeys() {
        return keys;
    }
    
    public String getSelectedKeyIdForInternal() {
        return selectedKeyId;
    }
    
    public List<KeyData> getSortedKeys() {
        List<KeyData> sorted = new ArrayList<>(keys);
        Collections.sort(sorted, new Comparator<KeyData>() {
            @Override
            public int compare(KeyData a, KeyData b) {
                return Integer.compare(a.zIndex, b.zIndex);
            }
        });
        return sorted;
    }
    
    public KeyData findKeyAt(float worldX, float worldY) {
        List<KeyData> sorted = getSortedKeys();
        for (int i = sorted.size() - 1; i >= 0; i--) {
            KeyData key = sorted.get(i);
            if (key.rect.contains(worldX, worldY)) {
                return key;
            }
        }
        return null;
    }
    
    public void bringToFront(String id) {
        Log.d(TAG, "bringToFront: " + id + " (layer order unchanged)");
    }
    
    public void recordKeyPress(String keyCode) {
        if (keyCode == null || keyCode.equals("未绑定") || keyCode.isEmpty()) {
            return;
        }
        
        Integer count = keyCodeCount.get(keyCode);
        int newCount = (count == null ? 1 : count + 1);
        keyCodeCount.put(keyCode, newCount);
        totalPressCount++;
        
        long now = System.currentTimeMillis();
        recentPressTimes.addLast(now);
        
        long oneSecondAgo = now - 1000;
        while (!recentPressTimes.isEmpty() && recentPressTimes.getFirst() < oneSecondAgo) {
            recentPressTimes.removeFirst();
        }
        
        while (recentPressTimes.size() > MAX_RECENT_SIZE) {
            recentPressTimes.removeFirst();
        }
        
        saveCountsToPreferences();
    }
    
    public int getKeyCount(String keyCode) {
        if (keyCode == null || keyCode.equals("未绑定") || keyCode.isEmpty()) {
            return 0;
        }
        Integer count = keyCodeCount.get(keyCode);
        return count == null ? 0 : count;
    }
    
    public int getTotalPressCount() {
        return totalPressCount;
    }
    
    public int getKPS() {
        long now = System.currentTimeMillis();
        long oneSecondAgo = now - 1000;
        
        Iterator<Long> iterator = recentPressTimes.iterator();
        while (iterator.hasNext()) {
            if (iterator.next() < oneSecondAgo) {
                iterator.remove();
            } else {
                break;
            }
        }
        
        return recentPressTimes.size();
    }
    
    public Map<String, Integer> getKeyCodeCount() {
        return keyCodeCount;
    }
    
    public void resetAllCounts() {
        keyCodeCount.clear();
        totalPressCount = 0;
        recentPressTimes.clear();
        saveCountsToPreferences();
    }
    
    public void setKeyCodeCounts(Map<String, Integer> counts) {
        this.keyCodeCount.clear();
        this.keyCodeCount.putAll(counts);
    }
    
    public void setTotalPressCount(int total) {
        this.totalPressCount = total;
    }
    
    public float[] getKeysBounds() {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;
        
        for (KeyData key : keys) {
            float halfW = key.width / 2f;
            float halfH = key.height / 2f;
            minX = Math.min(minX, key.centerX - halfW);
            minY = Math.min(minY, key.centerY - halfH);
            maxX = Math.max(maxX, key.centerX + halfW);
            maxY = Math.max(maxY, key.centerY + halfH);
        }
        
        return new float[]{minX, minY, maxX, maxY};
    }
    
    public void saveToPreferences() {
        if (context == null) return;
        
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        editor.putInt("key_count", keys.size());
        
        for (int i = 0; i < keys.size(); i++) {
            KeyData k = keys.get(i);
            String prefix = "key_" + i + "_";
            editor.putString(prefix + "id", k.id);
            editor.putFloat(prefix + "cx", k.centerX);
            editor.putFloat(prefix + "cy", k.centerY);
            editor.putFloat(prefix + "w", k.width);
            editor.putFloat(prefix + "h", k.height);
            editor.putString(prefix + "bound", k.boundKey);
            editor.putString(prefix + "label", k.displayLabel);
            editor.putInt(prefix + "fill", k.fillColor);
            editor.putInt(prefix + "border", k.borderColor);
            editor.putFloat(prefix + "radius", k.cornerRadiusDp);
            editor.putFloat(prefix + "borderWidth", k.borderWidthDp);
            editor.putFloat(prefix + "textSize", k.textSizeSp);
            editor.putInt(prefix + "textColor", k.textColor);
            editor.putBoolean(prefix + "showCount", k.showCount);
            editor.putBoolean(prefix + "mapping_en", k.mappingEnabled);
            editor.putString(prefix + "mapped", k.mappedKey);
            editor.putBoolean(prefix + "rain_en", k.rainEnabled);
            editor.putFloat(prefix + "rain_ox", k.rainOffsetXDp);
            editor.putFloat(prefix + "rain_oy", k.rainOffsetYDp);
            editor.putFloat(prefix + "rain_width", k.rainWidthPercent);
            editor.putInt(prefix + "rain_c", k.rainColor);
            editor.putFloat(prefix + "rain_sp", k.rainSpeedDp);
            editor.putInt(prefix + "zIndex", k.zIndex);
        }
        
        if (selectedKeyId != null) {
            editor.putString(KEY_SELECTED, selectedKeyId);
        } else {
            editor.remove(KEY_SELECTED);
        }
        
        editor.apply();
    }
    
    public void saveCountsToPreferences() {
        if (context == null) return;
        
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        editor.putInt("count_size", keyCodeCount.size());
        int idx = 0;
        for (Map.Entry<String, Integer> entry : keyCodeCount.entrySet()) {
            editor.putString("count_" + idx + "_k", entry.getKey());
            editor.putInt("count_" + idx + "_v", entry.getValue());
            idx++;
        }
        editor.putInt(KEY_TOTAL, totalPressCount);
        
        editor.apply();
    }
    
    public void loadFromPreferences() {
        if (context == null) return;
        
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        int keyCount = prefs.getInt("key_count", 0);
        keys.clear();
        for (int i = 0; i < keyCount; i++) {
            String prefix = "key_" + i + "_";
            String id = prefs.getString(prefix + "id", UUID.randomUUID().toString());
            float cx = prefs.getFloat(prefix + "cx", 100);
            float cy = prefs.getFloat(prefix + "cy", 100);
            float w = prefs.getFloat(prefix + "w", 100);
            float h = prefs.getFloat(prefix + "h", 100);
            
            KeyData k = new KeyData(id, cx, cy, w, h);
            k.boundKey = prefs.getString(prefix + "bound", "未绑定");
            k.displayLabel = prefs.getString(prefix + "label", "");
            k.fillColor = prefs.getInt(prefix + "fill", 0xFF999999);
            k.borderColor = prefs.getInt(prefix + "border", 0xFF666666);
            k.cornerRadiusDp = prefs.getFloat(prefix + "radius", 8f);
            k.borderWidthDp = prefs.getFloat(prefix + "borderWidth", 5f);
            k.textSizeSp = prefs.getFloat(prefix + "textSize", 14f);
            k.textColor = prefs.getInt(prefix + "textColor", 0xFFFFFFFF);
            k.showCount = prefs.getBoolean(prefix + "showCount", true);
            k.mappingEnabled = prefs.getBoolean(prefix + "mapping_en", false);
            k.mappedKey = prefs.getString(prefix + "mapped", "");
            k.rainEnabled = prefs.getBoolean(prefix + "rain_en", false);
            k.rainOffsetXDp = prefs.getFloat(prefix + "rain_ox", 0f);
            k.rainOffsetYDp = prefs.getFloat(prefix + "rain_oy", 0f);
            k.rainWidthPercent = prefs.getFloat(prefix + "rain_width", 100f);
            k.rainColor = prefs.getInt(prefix + "rain_c", 0xFFFFFFFF);
            k.rainSpeedDp = prefs.getFloat(prefix + "rain_sp", 5f);
            k.zIndex = prefs.getInt(prefix + "zIndex", 0);
            
            keys.add(k);
        }
        
        selectedKeyId = prefs.getString(KEY_SELECTED, null);
        
        int countSize = prefs.getInt("count_size", 0);
        keyCodeCount.clear();
        for (int i = 0; i < countSize; i++) {
            String k = prefs.getString("count_" + i + "_k", "");
            int v = prefs.getInt("count_" + i + "_v", 0);
            if (!k.isEmpty()) {
                keyCodeCount.put(k, v);
            }
        }
        totalPressCount = prefs.getInt(KEY_TOTAL, 0);
    }
    
    public void updateKeyAndSave(KeyData key) {
        saveToPreferences();
    }
}