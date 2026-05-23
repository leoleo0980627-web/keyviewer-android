package com.KV;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.List;

public class IMEHelper {
    
    private static final String TAG = "IMEHelper";
    private static String originalIME = null;
    
    /**
     * 切换到 KeyViewer 输入法
     */
    public static void switchToKeyViewerIME(Context context) {
        String myIME = context.getPackageName() + "/" + KeyMappingIME.class.getName();
        originalIME = getCurrentIME(context);
        Log.d(TAG, "Original IME: " + originalIME);
        setIMEInternal(myIME);
        Log.d(TAG, "Switched to: " + myIME);
    }
    
    /**
     * 切换回原来的输入法
     */
    public static void switchBackToOriginalIME() {
        if (originalIME != null && !originalIME.isEmpty()) {
            setIMEInternal(originalIME);
            Log.d(TAG, "Switched back to: " + originalIME);
        }
    }
    
    /**
     * 手动设置输入法
     */
    public static void setIME(String ime) {
        setIMEInternal(ime);
    }
    
    /**
     * 获取当前输入法
     */
    private static String getCurrentIME(Context context) {
        try {
            return Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD);
        } catch (Exception e) {
            Log.e(TAG, "getCurrentIME failed", e);
            return null;
        }
    }
    
    /**
     * 通过 Shizuku 或 shell 设置输入法
     */
    private static void setIMEInternal(String ime) {
        // 方法1: 通过 Shizuku putSettings
        try {
            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            Method putSettingsMethod = shizukuClass.getDeclaredMethod(
                "putSettings", String.class, String.class, String.class);
            putSettingsMethod.setAccessible(true);
            putSettingsMethod.invoke(null, "secure", Settings.Secure.DEFAULT_INPUT_METHOD, ime);
            Log.d(TAG, "setIME via Shizuku putSettings: " + ime);
            return;
        } catch (Exception e1) {
            Log.e(TAG, "putSettings failed", e1);
        }
        
        // 方法2: 通过 Shizuku shell
        try {
            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            Method method = shizukuClass.getDeclaredMethod(
                "newProcess", String[].class, String[].class, String.class);
            method.setAccessible(true);
            
            String[] cmd = {"sh", "-c", "ime set " + ime};
            Process p = (Process) method.invoke(null, cmd, null, null);
            p.waitFor();
            p.destroy();
            Log.d(TAG, "setIME via shell: " + ime);
        } catch (Exception e2) {
            Log.e(TAG, "All setIME methods failed", e2);
        }
    }
    
    /**
     * 检查 KeyViewer 输入法是否已启用
     */
    public static boolean isKeyViewerIMEEnabled(Context context) {
        android.view.inputmethod.InputMethodManager imm = 
            (android.view.inputmethod.InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        List<android.view.inputmethod.InputMethodInfo> enabledIMEs = imm.getEnabledInputMethodList();
        
        for (android.view.inputmethod.InputMethodInfo ime : enabledIMEs) {
            if (ime.getComponent().getClassName().equals(KeyMappingIME.class.getName())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 打开输入法设置页面
     */
    public static void openIMESettings(Activity activity) {
        try {
            Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
            activity.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "openIMESettings failed", e);
        }
    }
    
    /**
     * 打开输入法选择器
     */
    public static void showInputMethodPicker(Context context) {
        android.view.inputmethod.InputMethodManager imm = 
            (android.view.inputmethod.InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showInputMethodPicker();
    }
}