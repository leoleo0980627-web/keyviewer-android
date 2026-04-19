package com.KV;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rikka.shizuku.Shizuku;

public class RishInputListener {

    private static final String TAG = "KV_Rish";
    private KeyView keyView;
    private Context context;
    private boolean isListening = false;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private List<Process> currentProcesses = new ArrayList<>();
    
    private Map<Integer, Boolean> keyPressedState = new HashMap<>();
    private volatile boolean paused = false;
    
    public RishInputListener(Context context, KeyView keyView) {
        this.context = context;
        this.keyView = keyView;
        Log.d(TAG, "RishInputListener created");
    }
    
    public boolean isShizukuAvailable() {
        try {
            boolean available = Shizuku.pingBinder() && Shizuku.checkSelfPermission() == 0;
            Log.d(TAG, "isShizukuAvailable: " + available);
            return available;
        } catch (Exception e) {
            Log.e(TAG, "isShizukuAvailable error", e);
            return false;
        }
    }
    
    public void startListening() {
        Log.d(TAG, "startListening called, isListening=" + isListening);
        if (isListening) return;
        if (!isShizukuAvailable()) return;
        
        new Thread(() -> {
            isListening = true;
            Log.d(TAG, "Listener thread started");
            
            GlobalSettings settings = GlobalSettings.getInstance(context);
            String calibratedDevice = settings.keyboardDevicePath;
            
            if (calibratedDevice != null && !calibratedDevice.isEmpty()) {
                Log.d(TAG, "Using calibrated device: " + calibratedDevice);
                startMonitoring(calibratedDevice);
            } else {
                List<String> devices = findAllKeyboardDevices();
                Log.d(TAG, "Found " + devices.size() + " keyboard devices");
                for (String device : devices) {
                    startMonitoring(device);
                }
            }
        }).start();
    }
    
    public void pause() {
        paused = true;
        Log.d(TAG, "Input listener paused");
    }
    
    public void resume() {
        paused = false;
        Log.d(TAG, "Input listener resumed");
    }
    
    private List<String> findAllKeyboardDevices() {
        List<String> devices = new ArrayList<>();
        
        try {
            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            Method method = shizukuClass.getDeclaredMethod(
                "newProcess", String[].class, String[].class, String.class);
            method.setAccessible(true);
            
            for (int i = 0; i < 32; i++) {
                String devicePath = "/dev/input/event" + i;
                
                if (!deviceExists(devicePath, method)) {
                    continue;
                }
                
                String deviceInfo = getDeviceInfo(devicePath, method);
                if (deviceInfo == null || deviceInfo.isEmpty()) {
                    continue;
                }
                
                if (isKeyboardDevice(deviceInfo)) {
                    devices.add(devicePath);
                    Log.d(TAG, "✅ Keyboard: " + devicePath + " | " + extractDeviceName(deviceInfo));
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "findAllKeyboardDevices error", e);
        }
        
        return devices;
    }
    
    private boolean deviceExists(String devicePath, Method method) {
        try {
            String[] cmd = {"sh", "-c", "test -e " + devicePath + " && echo ok"};
            Process p = (Process) method.invoke(null, cmd, null, null);
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String result = reader.readLine();
            p.destroy();
            return "ok".equals(result);
        } catch (Exception e) {
            return false;
        }
    }
    
    private String getDeviceInfo(String devicePath, Method method) {
        try {
            String[] cmd = {"sh", "-c", "getevent -p " + devicePath + " 2>/dev/null"};
            Process p = (Process) method.invoke(null, cmd, null, null);
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            p.destroy();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
    
    private boolean isKeyboardDevice(String deviceInfo) {
        boolean hasKey = deviceInfo.contains("KEY (0001):");
        boolean hasRel = deviceInfo.contains("REL (0002):") || deviceInfo.contains("EV_REL");
        
        if (!hasKey) {
            return false;
        }
        
        int keyCount = countKeyCodes(deviceInfo);
        String name = extractDeviceName(deviceInfo).toLowerCase();
        
        Log.d(TAG, "Device: " + name + " | hasKey=" + hasKey + " | hasRel=" + hasRel + " | keys=" + keyCount);
        
        String[] excludePatterns = {"touch", "proximity", "hall", "grip", "accdet", "meta", "sensor", "headset", "button jack", "pon", "nav", "folio"};
        for (String pattern : excludePatterns) {
            if (name.contains(pattern)) {
                Log.d(TAG, "Excluded by name: " + name);
                return false;
            }
        }
        
        boolean hasEnoughKeys = (keyCount > 50);
        
        if (hasRel && keyCount < 10) {
            Log.d(TAG, "Mouse with few keys: " + name);
            return false;
        }
        
        if (name.contains("2.4g") || name.contains("mouse")) {
            return hasEnoughKeys;
        }
        
        return hasEnoughKeys;
    }
    
    private int countKeyCodes(String deviceInfo) {
        int count = 0;
        boolean inKeySection = false;
        
        for (String line : deviceInfo.split("\n")) {
            if (line.contains("KEY") && line.contains("(0001)")) {
                inKeySection = true;
                count += countHexNumbers(line);
            } else if (inKeySection) {
                if (line.trim().isEmpty() || (line.contains("(") && !line.contains("KEY"))) {
                    inKeySection = false;
                } else {
                    count += countHexNumbers(line);
                }
            }
        }
        return count;
    }
    
    private int countHexNumbers(String line) {
        int count = 0;
        String[] parts = line.split("\\s+");
        for (String part : parts) {
            if (part.matches("[0-9a-fA-F]{4}")) {
                count++;
            }
        }
        return count;
    }
    
    private String extractDeviceName(String deviceInfo) {
        for (String line : deviceInfo.split("\n")) {
            if (line.contains("name:")) {
                int start = line.indexOf('"');
                int end = line.lastIndexOf('"');
                if (start != -1 && end != -1 && end > start) {
                    return line.substring(start + 1, end);
                }
            }
        }
        return "unknown";
    }
    
    private void startMonitoring(String devicePath) {
        try {
            Log.d(TAG, "startMonitoring: " + devicePath);
            
            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            Method method = shizukuClass.getDeclaredMethod(
                "newProcess", String[].class, String[].class, String.class);
            method.setAccessible(true);
            
            String[] cmd = {"sh", "-c", "getevent " + devicePath};
            Process p = (Process) method.invoke(null, cmd, null, null);
            
            synchronized (currentProcesses) {
                currentProcesses.add(p);
            }
            
            Log.d(TAG, "Process started for " + devicePath);
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(p.getInputStream()));
            String line;
            
            while (isListening && (line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 3) {
                    try {
                        int type = Integer.parseInt(parts[0], 16);
                        int code = Integer.parseInt(parts[1], 16);
                        int value = Integer.parseInt(parts[2], 16);
                        
                        if (type == 1 && (value == 1 || value == 0)) {
                            boolean isDown = (value == 1);
                            
                            String originalKey = keyCodeToName(code);
                            if (originalKey == null) continue;
                            
                            if (paused) continue;
                            
                            // 检查是否绑定了这个按键
                            if (!keyView.isKeyBound(originalKey)) continue;
                            
                            Boolean currentlyPressed = keyPressedState.get(code);
                            if (currentlyPressed == null) currentlyPressed = false;
                            
                            if (currentlyPressed == isDown) continue;
                            
                            keyPressedState.put(code, isDown);
                            
                            // 获取映射后的按键名
                            String mappedKey = keyView.getMappedKey(originalKey);
                            String finalKeyName = mappedKey != null ? mappedKey : originalKey;
                            
                            Log.d(TAG, "Key " + originalKey + " -> mapped to " + finalKeyName + " " + (isDown ? "DOWN" : "UP") + " from " + devicePath);
                            
                            final String finalKey = finalKeyName;
                            final boolean finalIsDown = isDown;
                            
                            mainHandler.post(() -> {
                                if (keyView != null) {
                                    keyView.notifyKeyPressed(finalKey);
                                    keyView.setKeyPressed(finalKey, finalIsDown);
                                    
                                    if (finalIsDown) {
                                        keyView.getKeyManager().recordKeyPress(finalKey);
                                        keyView.invalidate();
                                    }
                                }
                            });
                        }
                    } catch (NumberFormatException e) {}
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "startMonitoring error for " + devicePath, e);
        }
    }
    
    private String keyCodeToName(int code) {
        switch (code) {
            case 57: return "space";
            case 28: return "enter";
            case 15: return "tab";
            case 1: return "esc";
            case 14: return "backspace";
            case 111: return "del";
            case 42: return "lshift";
            case 54: return "rshift";
            case 29: return "lctrl";
            case 97: return "rctrl";
            case 56: return "lalt";
            case 100: return "ralt";
            case 103: return "up";
            case 108: return "down";
            case 105: return "left";
            case 106: return "right";
            case 51: return "comma";
            case 52: return "dot";
            case 39: return "semicolon";
            case 53: return "slash";
            case 43: return "backslash";
            case 40: return "quote";
            case 41: return "backquote";
            case 26: return "bracketleft";
            case 27: return "bracketright";
            case 12: return "minus";
            case 13: return "equal";
            case 104: return "pageup";
            case 109: return "pagedown";
            case 102: return "home";
            case 107: return "end";
            case 110: return "insert";
            case 58: return "capslock";
            case 69: return "numlock";
            case 78: return "plus";
            case 74: return "minus";
            case 98: return "slash";
            case 55: return "asterisk";
            case 83: return "dot";
            case 96: return "enter";
            case 30: return "a"; case 48: return "b"; case 46: return "c";
            case 32: return "d"; case 18: return "e"; case 33: return "f";
            case 34: return "g"; case 35: return "h"; case 23: return "i";
            case 36: return "j"; case 37: return "k"; case 38: return "l";
            case 50: return "m"; case 49: return "n"; case 24: return "o";
            case 25: return "p"; case 16: return "q"; case 19: return "r";
            case 31: return "s"; case 20: return "t"; case 22: return "u";
            case 47: return "v"; case 17: return "w"; case 45: return "x";
            case 21: return "y"; case 44: return "z";
            case 2: return "1"; case 3: return "2"; case 4: return "3";
            case 5: return "4"; case 6: return "5"; case 7: return "6";
            case 8: return "7"; case 9: return "8"; case 10: return "9";
            case 11: return "0";
            default: return null;
        }
    }
    
    public void stopListening() {
        isListening = false;
        keyPressedState.clear();
        synchronized (currentProcesses) {
            for (Process p : currentProcesses) {
                if (p != null) p.destroy();
            }
            currentProcesses.clear();
        }
    }
}