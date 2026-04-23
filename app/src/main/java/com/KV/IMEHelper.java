package com.KV;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.List;

public class IMEHelper {
    
    private static final String TAG = "IMEHelper";
    private static String originalIME = null;
    
    public static void switchToKeyViewerIME(Context context) {
        String myIME = context.getPackageName() + "/" + KeyMappingIME.class.getName();
        originalIME = getCurrentIME(context);
        Log.d(TAG, "Original IME: " + originalIME);
        setIMEInternal(myIME);
        Log.d(TAG, "Switched to: " + myIME);
    }
    
    public static void switchBackToOriginalIME() {
        if (originalIME != null && !originalIME.isEmpty()) {
            setIMEInternal(originalIME);
            Log.d(TAG, "Switched back to: " + originalIME);
        }
    }
    
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
    
    public static void setIME(String ime) {
        setIMEInternal(ime);
    }
    
    private static void setIMEInternal(String ime) {
        try {
            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            Method putSettingsMethod = shizukuClass.getDeclaredMethod(
                "putSettings", String.class, String.class, String.class);
            putSettingsMethod.setAccessible(true);
            putSettingsMethod.invoke(null, "secure", Settings.Secure.DEFAULT_INPUT_METHOD, ime);
            Log.d(TAG, "setIME via Shizuku putSettings: " + ime);
        } catch (Exception e1) {
            Log.e(TAG, "putSettings failed, trying shell", e1);
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
    }
    
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
    
    public static void openIMESettings(android.app.Activity activity) {
        try {
            android.content.Intent intent = new android.content.Intent(
                android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS);
            activity.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "openIMESettings failed", e);
        }
    }
}