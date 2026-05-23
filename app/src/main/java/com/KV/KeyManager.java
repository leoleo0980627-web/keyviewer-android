package com.KV;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KeyManager {
    private static final String TAG = "KV_KeyManager";
    
    private List<KeyData> keys = new ArrayList<>();
    private Map<String, Integer> keyCodeCount = new HashMap<>();
    private String selectedKeyId = null;
    
    private int totalPressCount = 0;
    private LongRingBuffer recentPressTimes = new LongRingBuffer();
    
    private static final String PREFS_NAME = "keyviewer_prefs";
    private static final String KEY_TOTAL = "total_press_count";
    private static final String KEY_SELECTED = "selected_key_id";
    private static final int COUNT_SAVE_BATCH_SIZE = 50;
    private static final long COUNT_SAVE_INTERVAL_MS = 1000;
    
    private Context context;
    private int unsavedCountUpdates = 0;
    private long lastCountSaveElapsedMs = 0;
    
    public KeyManager() {
        this(null);
    }
    
    public KeyManager(Context context) {
        this.context = context;
    }
    
    public void setContext(Context context) {
        this.context = context;
    }
    
    // ==================== 按键 CRUD ====================
    
    public KeyData createNewKey(float centerX, float centerY, float width, float height) {
        String id = UUID.randomUUID().toString();
        KeyData key = new KeyData(id, centerX, centerY, width, height);
        
        // 新按键放在最上层
        int maxZ = getMaxZIndex();
        key.zIndex = maxZ + 1;
        keys.add(key);
        
        saveToPreferences();
        Log.d(TAG, "createNewKey: " + id + " zIndex=" + key.zIndex);
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
    
    /**
     * 获取按 zIndex 排序的按键列表（降序：zIndex 大的在前，即上层在前）
     * 渲染时应按此列表顺序绘制，确保上层按键后绘制（覆盖下层）
     */
    public List<KeyData> getKeysSortedByZIndex() {
        List<KeyData> sorted = new ArrayList<>(keys);
        sorted.sort(ZINDEX_COMPARATOR);
        return sorted;
    }
    
    /**
     * 获取按 zIndex 排序的按键列表（升序：zIndex 小的在前，即下层在前）
     * 用于需要正序遍历的场景（如键雨粒子需要先绘制下层）
     */
    public List<KeyData> getKeysSortedByZIndexAsc() {
        List<KeyData> sorted = new ArrayList<>(keys);
        sorted.sort(ZINDEX_COMPARATOR_ASC);
        return sorted;
    }
    
    private static final Comparator<KeyData> ZINDEX_COMPARATOR = (a, b) -> {
        int zCompare = Integer.compare(b.zIndex, a.zIndex);
        if (zCompare != 0) return zCompare;
        // zIndex 相同时，按在列表中的原始顺序稳定排序
        return 0;
    };
    
    private static final Comparator<KeyData> ZINDEX_COMPARATOR_ASC = (a, b) -> {
        int zCompare = Integer.compare(a.zIndex, b.zIndex);
        if (zCompare != 0) return zCompare;
        return 0;
    };
    
    public void bringToFront(String id) {
        KeyData target = null;
        for (KeyData key : keys) {
            if (key.id.equals(id)) {
                target = key;
                break;
            }
        }
        if (target == null) return;
        
        // 获取当前最大 zIndex
        int maxZ = getMaxZIndex();
        
        // 如果目标已经是最大 zIndex，无需操作
        if (target.zIndex == maxZ) {
            Log.d(TAG, "bringToFront: " + id + " already at top");
            return;
        }
        
        // 将目标设为最大 zIndex + 1，确保它一定在最上层
        target.zIndex = maxZ + 1;
        
        saveToPreferences();
        Log.d(TAG, "bringToFront: " + id + " new zIndex=" + target.zIndex);
    }

    /**
     * 规范化 zIndex，使其成为连续的整数序列 [1, N]
     * zIndex 越大表示越在上层
     */
    public void normalizeZIndexes() {
        if (keys.isEmpty()) return;

        List<KeyData> normalized = getKeysSortedByZIndex();

        // 从大到小分配：最大的 zIndex 得到 N，最小的得到 1
        for (int i = 0; i < normalized.size(); i++) {
            normalized.get(i).zIndex = normalized.size() - i;
        }
        
        Log.d(TAG, "normalizeZIndexes: " + keys.size() + " keys");
    }

    private int getMaxZIndex() {
        int maxZIndex = 0;
        for (KeyData key : keys) {
            if (key.zIndex > maxZIndex) {
                maxZIndex = key.zIndex;
            }
        }
        return maxZIndex;
    }
    
    /**
     * 点击检测：按 zIndex 降序检测，确保点击到上层按键
     */
    public KeyData findKeyAt(float worldX, float worldY) {
        List<KeyData> sortedKeys = getKeysSortedByZIndex();
        
        for (KeyData key : sortedKeys) {
            if (key.rect.contains(worldX, worldY)) {
                return key;
            }
        }
        return null;
    }
    
    // ==================== 按键计数 ====================
    
    public void recordKeyPress(String keyCode) {
        if (keyCode == null || keyCode.equals("未绑定") || keyCode.isEmpty()) {
            return;
        }
        
        Integer count = keyCodeCount.get(keyCode);
        int newCount = (count == null ? 1 : count + 1);
        keyCodeCount.put(keyCode, newCount);
        totalPressCount++;
        
        long now = System.currentTimeMillis();
        recentPressTimes.add(now);
        recentPressTimes.removeOlderThan(now - 1000);
        
        markCountStateDirtyAndMaybeSave();
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
    
    public void setTotalPressCount(int total) {
        this.totalPressCount = total;
    }
    
    public int getKPS() {
        long now = System.currentTimeMillis();
        recentPressTimes.removeOlderThan(now - 1000);
        return recentPressTimes.size();
    }
    
    public Map<String, Integer> getKeyCodeCount() {
        return keyCodeCount;
    }
    
    public void resetAllCounts() {
        keyCodeCount.clear();
        totalPressCount = 0;
        recentPressTimes.clear();
        unsavedCountUpdates = 0;
        saveCountsToPreferences();
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
    
    // ==================== 存档保存 ====================
    
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
            
            editor.putInt(prefix + "fill_up", k.fillColorUp);
            editor.putInt(prefix + "border_up", k.borderColorUp);
            editor.putInt(prefix + "text_up", k.textColorUp);
            editor.putInt(prefix + "count_up", k.countColorUp);
            
            editor.putInt(prefix + "fill_down", k.fillColorDown);
            editor.putInt(prefix + "border_down", k.borderColorDown);
            editor.putInt(prefix + "text_down", k.textColorDown);
            editor.putInt(prefix + "count_down", k.countColorDown);
            
            editor.putFloat(prefix + "textSize", k.textSizeSp);
            editor.putBoolean(prefix + "showCount", k.showCount);
            
            editor.putBoolean(prefix + "mapping_en", k.mappingEnabled);
            editor.putString(prefix + "mapped", k.mappedKey);
            
            editor.putBoolean(prefix + "rain_en", k.rainEnabled);
            editor.putFloat(prefix + "rain_ox", k.rainOffsetXDp);
            editor.putFloat(prefix + "rain_oy", k.rainOffsetYDp);
            editor.putFloat(prefix + "rain_width", k.rainWidthPercent);
            editor.putInt(prefix + "rain_c", k.rainColor);
            editor.putFloat(prefix + "rain_sp", k.rainSpeedDp);
            
            editor.putBoolean(prefix + "no_wrap", k.kpsTotalNoWrap);
            editor.putFloat(prefix + "label_offset_x", k.kpsTotalLabelOffsetX);
            
            editor.putInt(prefix + "zIndex", k.zIndex);
        }
        
        if (selectedKeyId != null) {
            editor.putString(KEY_SELECTED, selectedKeyId);
        } else {
            editor.remove(KEY_SELECTED);
        }
        
        editor.apply();
        Log.d(TAG, "saveToPreferences: " + keys.size() + " keys");
    }
    
    private void saveCountsToPreferences() {
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
        unsavedCountUpdates = 0;
        lastCountSaveElapsedMs = SystemClock.elapsedRealtime();
    }

    private void markCountStateDirtyAndMaybeSave() {
        unsavedCountUpdates++;

        long nowElapsed = SystemClock.elapsedRealtime();
        boolean shouldSaveByBatch = unsavedCountUpdates >= COUNT_SAVE_BATCH_SIZE;
        boolean shouldSaveByTime = lastCountSaveElapsedMs == 0 ||
                nowElapsed - lastCountSaveElapsedMs >= COUNT_SAVE_INTERVAL_MS;

        if (shouldSaveByBatch || shouldSaveByTime) {
            saveCountsToPreferences();
        }
    }

    public void flushPendingCountSaves() {
        if (unsavedCountUpdates > 0) {
            saveCountsToPreferences();
        }
    }
    
    // ==================== 存档加载 ====================
    
    public void loadFromPreferences() {
        if (context == null) return;
        
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        int keyCount = prefs.getInt("key_count", 0);
        keys.clear();
        
        for (int i = 0; i < keyCount; i++) {
            String prefix = "key_" + i + "_";
            KeyData key = loadKeyFromPrefs(prefs, prefix);
            keys.add(key);
        }
        
        selectedKeyId = prefs.getString(KEY_SELECTED, null);
        
        loadCountsFromPrefs(prefs);
        
        Log.d(TAG, "loadFromPreferences: " + keys.size() + " keys, total=" + totalPressCount);
    }
    
    private KeyData loadKeyFromPrefs(SharedPreferences prefs, String prefix) {
        String id = prefs.getString(prefix + "id", UUID.randomUUID().toString());
        float cx = prefs.getFloat(prefix + "cx", 100);
        float cy = prefs.getFloat(prefix + "cy", 100);
        float w = prefs.getFloat(prefix + "w", 100);
        float h = prefs.getFloat(prefix + "h", 100);
        
        KeyData key = new KeyData(id, cx, cy, w, h);
        
        int oldFillColor = prefs.getInt(prefix + "fill", 0xFF999999);
        int oldBorderColor = prefs.getInt(prefix + "border", 0xFF666666);
        
        key.boundKey = prefs.getString(prefix + "bound", "未绑定");
        key.displayLabel = prefs.getString(prefix + "label", "");
        
        key.fillColorUp = prefs.getInt(prefix + "fill_up", oldFillColor);
        key.borderColorUp = prefs.getInt(prefix + "border_up", oldBorderColor);
        key.textColorUp = prefs.getInt(prefix + "text_up", 0xFFFFFFFF);
        key.countColorUp = prefs.getInt(prefix + "count_up", key.textColorUp);
        
        key.fillColorDown = prefs.getInt(prefix + "fill_down", 0xFFFFFFFF);
        key.borderColorDown = prefs.getInt(prefix + "border_down", key.borderColorUp);
        key.textColorDown = prefs.getInt(prefix + "text_down", key.textColorUp);
        key.countColorDown = prefs.getInt(prefix + "count_down", key.countColorUp);
        
        key.textSizeSp = prefs.getFloat(prefix + "textSize", 14f);
        key.showCount = prefs.getBoolean(prefix + "showCount", true);
        
        key.mappingEnabled = prefs.getBoolean(prefix + "mapping_en", false);
        key.mappedKey = prefs.getString(prefix + "mapped", "");
        
        key.rainEnabled = prefs.getBoolean(prefix + "rain_en", false);
        key.rainOffsetXDp = prefs.getFloat(prefix + "rain_ox", 0f);
        key.rainOffsetYDp = prefs.getFloat(prefix + "rain_oy", 0f);
        key.rainWidthPercent = prefs.getFloat(prefix + "rain_width", 100f);
        key.rainColor = prefs.getInt(prefix + "rain_c", 0xFFFFFFFF);
        key.rainSpeedDp = prefs.getFloat(prefix + "rain_sp", 5f);
        
        key.kpsTotalNoWrap = prefs.getBoolean(prefix + "no_wrap", false);
        key.kpsTotalLabelOffsetX = prefs.getFloat(prefix + "label_offset_x", 0f);
        
        key.zIndex = prefs.getInt(prefix + "zIndex", 0);
        
        return key;
    }
    
    private void loadCountsFromPrefs(SharedPreferences prefs) {
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
        recentPressTimes.clear();
        unsavedCountUpdates = 0;
        lastCountSaveElapsedMs = SystemClock.elapsedRealtime();
    }
    
    // ==================== JSON 导入导出 ====================
    
    public JSONObject exportToJson() throws Exception {
        JSONObject root = new JSONObject();
        JSONArray keysArray = new JSONArray();
        
        for (KeyData key : keys) {
            JSONObject keyJson = new JSONObject();
            keyJson.put("id", key.id);
            keyJson.put("centerX", key.centerX);
            keyJson.put("centerY", key.centerY);
            keyJson.put("width", key.width);
            keyJson.put("height", key.height);
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
        for (Map.Entry<String, Integer> entry : keyCodeCount.entrySet()) {
            countsJson.put(entry.getKey(), entry.getValue());
        }
        root.put("keyCounts", countsJson);
        root.put("totalPressCount", totalPressCount);
        
        return root;
    }
    
    public void updateKeyAndSave(KeyData key) {
        saveToPreferences();
    }

    private static class LongRingBuffer {
        private long[] values = new long[64];
        private int head = 0;
        private int size = 0;

        void add(long value) {
            ensureCapacity(size + 1);
            int tail = (head + size) % values.length;
            values[tail] = value;
            size++;
        }

        void removeOlderThan(long minInclusiveTimestamp) {
            while (size > 0 && values[head] < minInclusiveTimestamp) {
                head = (head + 1) % values.length;
                size--;
            }
        }

        int size() {
            return size;
        }

        void clear() {
            head = 0;
            size = 0;
        }

        private void ensureCapacity(int requiredCapacity) {
            if (requiredCapacity <= values.length) {
                return;
            }

            int newCapacity = values.length * 2;
            while (newCapacity < requiredCapacity) {
                newCapacity *= 2;
            }

            long[] newValues = new long[newCapacity];
            for (int i = 0; i < size; i++) {
                newValues[i] = values[(head + i) % values.length];
            }

            values = newValues;
            head = 0;
        }
    }
}
