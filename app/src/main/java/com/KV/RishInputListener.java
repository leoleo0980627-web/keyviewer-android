package com.KV;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import rikka.shizuku.Shizuku;

public class RishInputListener {

    private static final String TAG = "KV_Rish";
    private KeyView keyView;
    private Context context;
    private boolean isListening = false;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private List<Process> currentProcesses = new CopyOnWriteArrayList<>();

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
        if (!BinaryKeyInjector.getInstance().isRunning()) {
            Log.d(TAG, "BinaryKeyInjector not running");
            return;
        }

        new Thread(() -> {
            isListening = true;
            Log.d(TAG, "Listener thread started");

            Process catProcess = startMergedMonitoring();
            if (catProcess == null) {
                Log.e(TAG, "Failed to start merged monitoring");
                isListening = false;
                return;
            }

            synchronized (currentProcesses) {
                currentProcesses.add(catProcess);
            }

            Log.d(TAG, "Reading from merged stream...");

            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(catProcess.getInputStream()));
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

                                if (!keyView.isKeyBound(originalKey)) continue;

                                Boolean currentlyPressed = keyPressedState.get(code);
                                if (currentlyPressed == null) currentlyPressed = false;

                                if (currentlyPressed == isDown) continue;

                                keyPressedState.put(code, isDown);

                                final boolean finalIsDown = isDown;

                                mainHandler.post(() -> {
                                    if (keyView != null) {
                                        keyView.notifyKeyPressed(originalKey);
                                        keyView.setKeyPressed(originalKey, finalIsDown);

                                        if (finalIsDown) {
                                            keyView.getKeyManager().recordKeyPress(originalKey);
                                            keyView.invalidate();

                                            String mappedKey = keyView.getMappedKey(originalKey);
                                            if (mappedKey != null && !mappedKey.equals(originalKey)) {
                                                BinaryKeyInjector binInjector = BinaryKeyInjector.getInstance();
                                                int linuxCode = BinaryKeyInjector.keyNameToLinuxCode(mappedKey);
                                                if (linuxCode != -1 && binInjector.isRunning()) {
                                                    binInjector.injectKey(linuxCode, true);
                                                    binInjector.injectKey(linuxCode, false);
                                                }
                                            }
                                        }
                                    }
                                });
                            }
                        } catch (NumberFormatException e) {
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading from merged stream", e);
            }
        }).start();
    }

    private Process startMergedMonitoring() {
        try {
            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            Method method = shizukuClass.getDeclaredMethod(
                    "newProcess", String[].class, String[].class, String.class);
            method.setAccessible(true);

            String[] cmd = {"sh", "-c",
                    "for f in /dev/input/event*; do " +
                            "  getevent $f 2>/dev/null & " +
                            "done | cat"
            };

            Process p = (Process) method.invoke(null, cmd, null, null);
            Log.d(TAG, "Merged monitoring started (anonymous pipe)");
            return p;
        } catch (Exception e) {
            Log.e(TAG, "startMergedMonitoring failed", e);
            return null;
        }
    }

    public void pause() {
        paused = true;
        Log.d(TAG, "Input listener paused");
    }

    public void resume() {
        paused = false;
        Log.d(TAG, "Input listener resumed");
    }

    public void stopListening() {
        isListening = false;
        keyPressedState.clear();

        try {
            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            Method method = shizukuClass.getDeclaredMethod(
                    "newProcess", String[].class, String[].class, String.class);
            method.setAccessible(true);

            String[] killCmd = {"sh", "-c", "killall getevent 2>/dev/null"};
            Process killP = (Process) method.invoke(null, killCmd, null, null);
            killP.waitFor();
            killP.destroy();
            Log.d(TAG, "All getevent processes killed");
        } catch (Exception e) {
            Log.e(TAG, "cleanup failed", e);
        }

        synchronized (currentProcesses) {
            for (Process p : currentProcesses) {
                if (p != null) p.destroy();
            }
            currentProcesses.clear();
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
}