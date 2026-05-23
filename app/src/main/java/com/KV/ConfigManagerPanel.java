package com.KV;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ConfigManagerPanel {

    private static final String TAG = "KV_ConfigManager";
    private static final String CONFIG_DIR = "kv_configs";
    private static final String CONFIG_EXTENSION = ".json";
    private static final String CURRENT_CONFIG_KEY = "current_config_name";
    private static final int REQUEST_IMPORT_CONFIG = 2001;
    
    private Activity activity;
    private View panelView;
    private KeyView keyView;
    private GlobalSettings globalSettings;
    
    private RecyclerView recyclerView;
    private ConfigAdapter adapter;
    private List<ConfigItem> configList = new ArrayList<>();
    private Button closeBtn;
    private Button newConfigBtn;
    
    private File configDir;
    private String currentConfigName = "default";
    private String pendingNewConfigName = null;
    
    private OnConfigChangedListener listener;
    
    public interface OnConfigChangedListener {
        void onConfigLoaded(String configName);
        void onConfigDeleted(String configName);
        void onConfigRenamed(String oldName, String newName);
    }
    
    public ConfigManagerPanel(Activity activity, View panelView, KeyView keyView) {
        this.activity = activity;
        this.panelView = panelView;
        this.keyView = keyView;
        this.globalSettings = GlobalSettings.getInstance(activity);
        
        configDir = new File(activity.getFilesDir(), CONFIG_DIR);
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        
        initViews();
        setupListeners();
        loadCurrentConfigName();
        refreshConfigList();
        applyDarkMode();
    }
    
    private void applyDarkMode() {
        boolean isDarkMode = globalSettings.darkModeEnabled;
        int bgColor = isDarkMode ? 0xFF2C2C2C : 0xFFF5F5F5;
        panelView.setBackgroundColor(bgColor);
        
        TextView closeBtnText = panelView.findViewById(R.id.closeConfigPanelBtn);
        if (closeBtnText != null) {
            closeBtnText.setTextColor(isDarkMode ? 0xFFFFFFFF : 0xFF333333);
        }
        
        if (newConfigBtn != null) {
            newConfigBtn.setTextColor(isDarkMode ? 0xFFFFFFFF : 0xFF333333);
        }
        
        if (recyclerView != null) {
            int recyclerBg = isDarkMode ? 0xFF3C3C3C : 0xFFFFFFFF;
            recyclerView.setBackgroundColor(recyclerBg);
        }
    }
    
    public void onDarkModeChanged() {
        applyDarkMode();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }
    
    public void setOnConfigChangedListener(OnConfigChangedListener listener) {
        this.listener = listener;
    }
    
    private void initViews() {
        recyclerView = panelView.findViewById(R.id.configRecyclerView);
        closeBtn = panelView.findViewById(R.id.closeConfigPanelBtn);
        newConfigBtn = panelView.findViewById(R.id.newConfigBtn);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        adapter = new ConfigAdapter();
        recyclerView.setAdapter(adapter);
    }
    
    private void setupListeners() {
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> hidePanel());
        }
        if (newConfigBtn != null) {
            newConfigBtn.setOnClickListener(v -> showCreateConfigDialog());
        }
    }
    
    private void loadCurrentConfigName() {
        SharedPreferences prefs = activity.getSharedPreferences("kv_config_prefs", Context.MODE_PRIVATE);
        currentConfigName = prefs.getString(CURRENT_CONFIG_KEY, "default");
    }
    
    private void saveCurrentConfigName(String name) {
        SharedPreferences prefs = activity.getSharedPreferences("kv_config_prefs", Context.MODE_PRIVATE);
        prefs.edit().putString(CURRENT_CONFIG_KEY, name).apply();
        currentConfigName = name;
    }
    
    private void refreshConfigList() {
        configList.clear();
        
        File[] files = configDir.listFiles((dir, name) -> name.endsWith(CONFIG_EXTENSION));
        if (files != null) {
            for (File file : files) {
                String name = file.getName().replace(CONFIG_EXTENSION, "");
                ConfigItem item = new ConfigItem();
                item.name = name;
                item.isCurrent = name.equals(currentConfigName);
                configList.add(item);
            }
        }
        
        boolean hasDefault = false;
        for (ConfigItem item : configList) {
            if (item.name.equals("default")) {
                hasDefault = true;
                break;
            }
        }
        if (!hasDefault) {
            ConfigItem defaultItem = new ConfigItem();
            defaultItem.name = "default";
            defaultItem.isCurrent = currentConfigName.equals("default");
            configList.add(0, defaultItem);
            createEmptyConfigFile("default");
        }
        
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }
    
    private void createEmptyConfigFile(String configName) {
        try {
            File file = new File(configDir, configName + CONFIG_EXTENSION);
            if (file.exists()) {
                return;
            }
            
            JSONObject root = new JSONObject();
            
            JSONObject globalJson = new JSONObject();
            globalJson.put("floatWindowEnabled", false);
            globalJson.put("floatWindowScale", 1.0);
            globalJson.put("floatWindowX", 100);
            globalJson.put("floatWindowY", 200);
            globalJson.put("globalRainHeightDp", 200);
            globalJson.put("globalRainReserveHeightDp", 10);
            globalJson.put("darkModeEnabled", false);
            root.put("globalSettings", globalJson);
            
            JSONArray keysArray = new JSONArray();
            root.put("keys", keysArray);
            
            JSONObject countsJson = new JSONObject();
            root.put("keyCounts", countsJson);
            root.put("totalPressCount", 0);
            
            try (FileOutputStream fos = new FileOutputStream(file);
                 OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                osw.write(root.toString(2));
            }
            
            Log.d(TAG, "Created empty config: " + configName);
            
        } catch (Exception e) {
            Log.e(TAG, "createEmptyConfigFile failed", e);
        }
    }
    
    private void saveCurrentConfigToFile(String configName) {
        new Thread(() -> {
            try {
                JSONObject root = new JSONObject();
                
                JSONObject globalJson = new JSONObject();
                globalJson.put("floatWindowEnabled", globalSettings.floatWindowEnabled);
                globalJson.put("floatWindowScale", globalSettings.floatWindowScale);
                globalJson.put("floatWindowX", globalSettings.floatWindowX);
                globalJson.put("floatWindowY", globalSettings.floatWindowY);
                globalJson.put("globalRainHeightDp", globalSettings.globalRainHeightDp);
                globalJson.put("globalRainReserveHeightDp", globalSettings.globalRainReserveHeightDp);
                globalJson.put("darkModeEnabled", globalSettings.darkModeEnabled);
                root.put("globalSettings", globalJson);
                
                KeyManager km = keyView.getKeyManager();
                JSONArray keysArray = new JSONArray();
                float density = activity.getResources().getDisplayMetrics().density;
                
                List<KeyData> keys = km.getKeys();
                if (keys != null) {
                    for (KeyData key : keys) {
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
                }
                root.put("keys", keysArray);
                
                JSONObject countsJson = new JSONObject();
                if (km.getKeyCodeCount() != null) {
                    for (Map.Entry<String, Integer> entry : km.getKeyCodeCount().entrySet()) {
                        countsJson.put(entry.getKey(), entry.getValue());
                    }
                }
                root.put("keyCounts", countsJson);
                root.put("totalPressCount", km.getTotalPressCount());
                
                File file = new File(configDir, configName + CONFIG_EXTENSION);
                try (FileOutputStream fos = new FileOutputStream(file);
                     OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                    osw.write(root.toString(2));
                }
                
                Log.d(TAG, "Saved config: " + configName);
                
            } catch (Exception e) {
                Log.e(TAG, "saveCurrentConfigToFile failed", e);
            }
        }).start();
    }
    
    private void loadConfigFromFile(String configName) {
        Toast.makeText(activity, "加载中...", Toast.LENGTH_SHORT).show();
        
        new Thread(() -> {
            try {
                File file = new File(configDir, configName + CONFIG_EXTENSION);
                if (!file.exists()) {
                    activity.runOnUiThread(() -> Toast.makeText(activity, "配置文件不存在", Toast.LENGTH_SHORT).show());
                    return;
                }
                
                StringBuilder sb = new StringBuilder();
                try (FileInputStream fis = new FileInputStream(file);
                     InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
                    char[] buffer = new char[1024];
                    int len;
                    while ((len = isr.read(buffer)) != -1) {
                        sb.append(buffer, 0, len);
                    }
                }
                
                JSONObject root = new JSONObject(sb.toString());
                
                applyConfigToUI(root, configName);
                
            } catch (Exception e) {
                Log.e(TAG, "loadConfigFromFile failed", e);
                activity.runOnUiThread(() -> Toast.makeText(activity, "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
    
    private void applyConfigToUI(JSONObject root, String configName) {
        try {
            if (root.has("globalSettings")) {
                JSONObject globalJson = root.getJSONObject("globalSettings");
                globalSettings.floatWindowEnabled = globalJson.optBoolean("floatWindowEnabled", false);
                globalSettings.floatWindowScale = (float) globalJson.optDouble("floatWindowScale", 1.0);
                globalSettings.floatWindowX = globalJson.optInt("floatWindowX", 100);
                globalSettings.floatWindowY = globalJson.optInt("floatWindowY", 200);
                globalSettings.globalRainHeightDp = globalJson.optInt("globalRainHeightDp", 200);
                globalSettings.globalRainReserveHeightDp = globalJson.optInt("globalRainReserveHeightDp", 10);
                globalSettings.darkModeEnabled = globalJson.optBoolean("darkModeEnabled", false);
                globalSettings.save();
            }
            
            KeyManager km = keyView.getKeyManager();
            List<KeyData> newKeys = new ArrayList<>();
            
            if (root.has("keys")) {
                JSONArray keysArray = root.getJSONArray("keys");
                float density = activity.getResources().getDisplayMetrics().density;
                
                for (int i = 0; i < keysArray.length(); i++) {
                    JSONObject keyJson = keysArray.getJSONObject(i);
                    KeyData key = importKeyFromJson(keyJson, density);
                    newKeys.add(key);
                }
            }
            
            km.getKeys().clear();
            km.getKeys().addAll(newKeys);
            
            // 一次性设置计数，不循环
            if (root.has("keyCounts")) {
                JSONObject countsJson = root.getJSONObject("keyCounts");
                Map<String, Integer> newCounts = new HashMap<>();
                Iterator<String> keys = countsJson.keys();
                int totalCount = 0;
                while (keys.hasNext()) {
                    String key = keys.next();
                    int count = countsJson.optInt(key, 0);
                    newCounts.put(key, count);
                    totalCount += count;
                }
                
                // 直接替换整个 Map
                km.getKeyCodeCount().clear();
                km.getKeyCodeCount().putAll(newCounts);
                km.setTotalPressCount(totalCount);
            } else {
                km.resetAllCounts();
            }
            
            // 一次性保存
            km.saveToPreferences();
            
            activity.runOnUiThread(() -> {
                keyView.clearSelection();
                keyView.invalidate();
                
                saveCurrentConfigName(configName);
                refreshConfigList();
                
                if (listener != null) {
                    listener.onConfigLoaded(configName);
                }
                
                Toast.makeText(activity, "已加载配置: " + configName, Toast.LENGTH_SHORT).show();
            });
            
        } catch (Exception e) {
            Log.e(TAG, "applyConfigToUI failed", e);
            activity.runOnUiThread(() -> Toast.makeText(activity, "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }
    
    private void deleteConfigFile(String configName) {
        if (configName.equals("default")) {
            Toast.makeText(activity, "默认配置不能删除", Toast.LENGTH_SHORT).show();
            return;
        }
        
        File file = new File(configDir, configName + CONFIG_EXTENSION);
        if (file.exists()) {
            file.delete();
        }
        
        if (currentConfigName.equals(configName)) {
            loadConfigFromFile("default");
        }
        
        refreshConfigList();
        
        if (listener != null) {
            listener.onConfigDeleted(configName);
        }
        
        Toast.makeText(activity, "已删除配置: " + configName, Toast.LENGTH_SHORT).show();
    }
    
    private void renameConfigFile(String oldName, String newName) {
        if (oldName.equals("default")) {
            Toast.makeText(activity, "默认配置不能重命名", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (newName == null || newName.trim().isEmpty()) {
            Toast.makeText(activity, "配置名称不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        
        newName = newName.trim();
        
        File oldFile = new File(configDir, oldName + CONFIG_EXTENSION);
        File newFile = new File(configDir, newName + CONFIG_EXTENSION);
        
        if (newFile.exists()) {
            Toast.makeText(activity, "配置名称已存在", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (oldFile.exists()) {
            oldFile.renameTo(newFile);
        }
        
        if (currentConfigName.equals(oldName)) {
            saveCurrentConfigName(newName);
        }
        
        refreshConfigList();
        
        if (listener != null) {
            listener.onConfigRenamed(oldName, newName);
        }
        
        Toast.makeText(activity, "已重命名: " + oldName + " → " + newName, Toast.LENGTH_SHORT).show();
    }
    
    private void showCreateConfigDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("新建配置");
        
        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("请输入配置名称");
        builder.setView(input);
        
        builder.setPositiveButton("创建", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(activity, "名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            
            File file = new File(configDir, name + CONFIG_EXTENSION);
            if (file.exists()) {
                Toast.makeText(activity, "配置已存在", Toast.LENGTH_SHORT).show();
                return;
            }
            
            createEmptyConfigFile(name);
            pendingNewConfigName = name;
            
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivityForResult(intent, REQUEST_IMPORT_CONFIG);
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    private void importAndOverwriteConfig(android.net.Uri uri, String configName) {
        Toast.makeText(activity, "导入中...", Toast.LENGTH_SHORT).show();
        
        new Thread(() -> {
            try {
                InputStream is = activity.getContentResolver().openInputStream(uri);
                if (is == null) {
                    activity.runOnUiThread(() -> Toast.makeText(activity, "无法读取文件", Toast.LENGTH_SHORT).show());
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
                    activity.runOnUiThread(() -> Toast.makeText(activity, "文件内容为空", Toast.LENGTH_SHORT).show());
                    return;
                }
                
                JSONObject root = new JSONObject(json);
                
                applyConfigToUI(root, configName);
                
                saveCurrentConfigToFile(configName);
                
                activity.runOnUiThread(() -> {
                    refreshConfigList();
                    Toast.makeText(activity, "已创建并导入配置: " + configName, Toast.LENGTH_SHORT).show();
                });
                
            } catch (Exception e) {
                Log.e(TAG, "importAndOverwriteConfig failed", e);
                activity.runOnUiThread(() -> Toast.makeText(activity, "导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
    
    private void importConfigToCurrent(android.net.Uri uri) {
        Toast.makeText(activity, "导入中...", Toast.LENGTH_SHORT).show();
        
        new Thread(() -> {
            try {
                InputStream is = activity.getContentResolver().openInputStream(uri);
                if (is == null) {
                    activity.runOnUiThread(() -> Toast.makeText(activity, "无法读取文件", Toast.LENGTH_SHORT).show());
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
                    activity.runOnUiThread(() -> Toast.makeText(activity, "文件内容为空", Toast.LENGTH_SHORT).show());
                    return;
                }
                
                JSONObject root = new JSONObject(json);
                
                applyConfigToUI(root, currentConfigName);
                
                saveCurrentConfigToFile(currentConfigName);
                
                activity.runOnUiThread(() -> {
                    refreshConfigList();
                    Toast.makeText(activity, "已导入到当前配置: " + currentConfigName, Toast.LENGTH_SHORT).show();
                });
                
            } catch (Exception e) {
                Log.e(TAG, "importConfigToCurrent failed", e);
                activity.runOnUiThread(() -> Toast.makeText(activity, "导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
    
    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) return;
        
        android.net.Uri uri = data.getData();
        if (uri == null) return;
        
        if (requestCode == REQUEST_IMPORT_CONFIG) {
            if (pendingNewConfigName != null) {
                importAndOverwriteConfig(uri, pendingNewConfigName);
                pendingNewConfigName = null;
            } else {
                importConfigToCurrent(uri);
            }
        }
    }
    
    public void importConfigFromUri(android.net.Uri uri) {
        importConfigToCurrent(uri);
    }
    
    private void showRenameDialog(String oldName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("重命名配置");
        
        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(oldName);
        input.selectAll();
        builder.setView(input);
        
        builder.setPositiveButton("重命名", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            renameConfigFile(oldName, newName);
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    private void showDeleteConfirmDialog(String configName) {
        new AlertDialog.Builder(activity)
            .setTitle("删除配置")
            .setMessage("确定要删除配置 \"" + configName + "\" 吗？")
            .setPositiveButton("删除", (dialog, which) -> deleteConfigFile(configName))
            .setNegativeButton("取消", null)
            .show();
    }
    
    public void showPanel() {
        panelView.setVisibility(View.VISIBLE);
        panelView.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.slide_in_right));
        refreshConfigList();
        applyDarkMode();
    }
    
    private void hidePanel() {
        panelView.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.slide_out_right));
        panelView.setVisibility(View.GONE);
    }
    
    public void togglePanel() {
        if (panelView.getVisibility() == View.VISIBLE) {
            hidePanel();
        } else {
            showPanel();
        }
    }
    
    public void refresh() {
        refreshConfigList();
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
    
    // ==================== Adapter ====================
    
    private class ConfigAdapter extends RecyclerView.Adapter<ConfigAdapter.ViewHolder> {
        
        private int editingPosition = -1;
        private EditText editingEditText;
        
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(activity).inflate(R.layout.config_list_item, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            ConfigItem item = configList.get(position);
            boolean isDarkMode = globalSettings.darkModeEnabled;
            
            if (editingPosition == position) {
                holder.nameText.setVisibility(View.GONE);
                holder.nameEdit.setVisibility(View.VISIBLE);
                holder.nameEdit.setText(item.name);
                holder.nameEdit.requestFocus();
                holder.nameEdit.selectAll();
                editingEditText = holder.nameEdit;
            } else {
                holder.nameText.setVisibility(View.VISIBLE);
                holder.nameEdit.setVisibility(View.GONE);
                holder.nameText.setText(item.name);
                
                if (item.isCurrent) {
                    holder.nameText.setTextColor(0xFF2196F3);
                    holder.currentIndicator.setVisibility(View.VISIBLE);
                } else {
                    int textColor = isDarkMode ? 0xFFFFFFFF : 0xFF333333;
                    holder.nameText.setTextColor(textColor);
                    holder.currentIndicator.setVisibility(View.GONE);
                }
            }
            
            int moreBtnTint = isDarkMode ? 0xFFFFFFFF : 0xFF666666;
            holder.moreBtn.setColorFilter(moreBtnTint);
            
            holder.itemView.setOnClickListener(v -> {
                if (editingPosition != -1) {
                    finishEditing();
                } else {
                    loadConfigFromFile(item.name);
                }
            });
            
            holder.moreBtn.setOnClickListener(v -> showMoreOptionsDialog(position, item.name));
        }
        
        @Override
        public int getItemCount() {
            return configList.size();
        }
        
        public void startEditing(int position) {
            if (editingPosition != -1) {
                finishEditing();
            }
            editingPosition = position;
            notifyItemChanged(position);
        }
        
        public void finishEditing() {
            if (editingPosition != -1 && editingEditText != null) {
                ConfigItem item = configList.get(editingPosition);
                String newName = editingEditText.getText().toString().trim();
                if (!newName.isEmpty() && !newName.equals(item.name)) {
                    renameConfigFile(item.name, newName);
                }
                editingPosition = -1;
                editingEditText = null;
                refreshConfigList();
            }
        }
        
        private void showMoreOptionsDialog(int position, String configName) {
            String[] options = {"重命名", "删除"};
            new AlertDialog.Builder(activity)
                .setTitle(configName)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        startEditing(position);
                    } else if (which == 1) {
                        showDeleteConfirmDialog(configName);
                    }
                })
                .show();
        }
        
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameText;
            EditText nameEdit;
            View currentIndicator;
            ImageButton moreBtn;
            
            ViewHolder(View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.configNameText);
                nameEdit = itemView.findViewById(R.id.configNameEdit);
                currentIndicator = itemView.findViewById(R.id.currentIndicator);
                moreBtn = itemView.findViewById(R.id.configMoreBtn);
                
                nameEdit.setOnEditorActionListener((v, actionId, event) -> {
                    finishEditing();
                    return true;
                });
                
                nameEdit.setOnFocusChangeListener((v, hasFocus) -> {
                    if (!hasFocus) {
                        finishEditing();
                    }
                });
            }
        }
    }
    
    private static class ConfigItem {
        String name;
        boolean isCurrent;
    }
}