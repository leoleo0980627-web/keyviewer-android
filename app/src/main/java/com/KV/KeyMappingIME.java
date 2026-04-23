package com.KV;

import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

public class KeyMappingIME extends InputMethodService {
    
    private static final String TAG = "KeyMappingIME";
    private static KeyMappingIME instance;
    
    // 用于发送映射按键
    public interface KeySender {
        void sendKeyEvent(String keyName);
    }
    
    private static KeySender keySender;
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.d(TAG, "IME created");
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }
    
    public static KeyMappingIME getInstance() {
        return instance;
    }
    
    public static void setKeySender(KeySender sender) {
        keySender = sender;
    }
    
    /**
     * 发送按键事件（供外部调用）
     */
    public void injectKeyEvent(String keyName) {
        int keyCode = keyNameToKeyCode(keyName);
        if (keyCode == -1) {
            Log.e(TAG, "Unknown key: " + keyName);
            return;
        }
        
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            long now = System.currentTimeMillis();
            ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
            ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
            Log.d(TAG, "Injected key: " + keyName + " (" + keyCode + ")");
        } else {
            Log.e(TAG, "No input connection available");
        }
    }
    
    @Override
    public View onCreateInputView() {
        // 返回 null，使用系统默认输入法界面（用户原来的输入法会接管）
        return null;
    }
    
    @Override
    public boolean onEvaluateFullscreenMode() {
        // 禁止全屏模式
        return false;
    }
    
    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        Log.d(TAG, "onStartInput");
    }
    
    @Override
    public void onFinishInput() {
        super.onFinishInput();
        Log.d(TAG, "onFinishInput");
    }
    
    private int keyNameToKeyCode(String name) {
        if (name == null) return -1;
        switch (name.toLowerCase()) {
            case "space": return KeyEvent.KEYCODE_SPACE;
            case "enter": return KeyEvent.KEYCODE_ENTER;
            case "tab": return KeyEvent.KEYCODE_TAB;
            case "esc":
            case "escape": return KeyEvent.KEYCODE_ESCAPE;
            case "backspace": return KeyEvent.KEYCODE_DEL;
            case "del": return KeyEvent.KEYCODE_FORWARD_DEL;
            case "lshift": return KeyEvent.KEYCODE_SHIFT_LEFT;
            case "rshift": return KeyEvent.KEYCODE_SHIFT_RIGHT;
            case "lctrl": return KeyEvent.KEYCODE_CTRL_LEFT;
            case "rctrl": return KeyEvent.KEYCODE_CTRL_RIGHT;
            case "lalt": return KeyEvent.KEYCODE_ALT_LEFT;
            case "ralt": return KeyEvent.KEYCODE_ALT_RIGHT;
            case "up": return KeyEvent.KEYCODE_DPAD_UP;
            case "down": return KeyEvent.KEYCODE_DPAD_DOWN;
            case "left": return KeyEvent.KEYCODE_DPAD_LEFT;
            case "right": return KeyEvent.KEYCODE_DPAD_RIGHT;
            case "pageup": return KeyEvent.KEYCODE_PAGE_UP;
            case "pagedown": return KeyEvent.KEYCODE_PAGE_DOWN;
            case "home": return KeyEvent.KEYCODE_MOVE_HOME;
            case "end": return KeyEvent.KEYCODE_MOVE_END;
            case "capslock": return KeyEvent.KEYCODE_CAPS_LOCK;
            case "comma": return KeyEvent.KEYCODE_COMMA;
            case "dot": return KeyEvent.KEYCODE_PERIOD;
            case "semicolon": return KeyEvent.KEYCODE_SEMICOLON;
            case "slash": return KeyEvent.KEYCODE_SLASH;
            case "backslash": return KeyEvent.KEYCODE_BACKSLASH;
            case "quote": return KeyEvent.KEYCODE_APOSTROPHE;
            case "backquote": return KeyEvent.KEYCODE_GRAVE;
            case "bracketleft": return KeyEvent.KEYCODE_LEFT_BRACKET;
            case "bracketright": return KeyEvent.KEYCODE_RIGHT_BRACKET;
            case "minus": return KeyEvent.KEYCODE_MINUS;
            case "equal": return KeyEvent.KEYCODE_EQUALS;
            case "a": return KeyEvent.KEYCODE_A;
            case "b": return KeyEvent.KEYCODE_B;
            case "c": return KeyEvent.KEYCODE_C;
            case "d": return KeyEvent.KEYCODE_D;
            case "e": return KeyEvent.KEYCODE_E;
            case "f": return KeyEvent.KEYCODE_F;
            case "g": return KeyEvent.KEYCODE_G;
            case "h": return KeyEvent.KEYCODE_H;
            case "i": return KeyEvent.KEYCODE_I;
            case "j": return KeyEvent.KEYCODE_J;
            case "k": return KeyEvent.KEYCODE_K;
            case "l": return KeyEvent.KEYCODE_L;
            case "m": return KeyEvent.KEYCODE_M;
            case "n": return KeyEvent.KEYCODE_N;
            case "o": return KeyEvent.KEYCODE_O;
            case "p": return KeyEvent.KEYCODE_P;
            case "q": return KeyEvent.KEYCODE_Q;
            case "r": return KeyEvent.KEYCODE_R;
            case "s": return KeyEvent.KEYCODE_S;
            case "t": return KeyEvent.KEYCODE_T;
            case "u": return KeyEvent.KEYCODE_U;
            case "v": return KeyEvent.KEYCODE_V;
            case "w": return KeyEvent.KEYCODE_W;
            case "x": return KeyEvent.KEYCODE_X;
            case "y": return KeyEvent.KEYCODE_Y;
            case "z": return KeyEvent.KEYCODE_Z;
            case "1": return KeyEvent.KEYCODE_1;
            case "2": return KeyEvent.KEYCODE_2;
            case "3": return KeyEvent.KEYCODE_3;
            case "4": return KeyEvent.KEYCODE_4;
            case "5": return KeyEvent.KEYCODE_5;
            case "6": return KeyEvent.KEYCODE_6;
            case "7": return KeyEvent.KEYCODE_7;
            case "8": return KeyEvent.KEYCODE_8;
            case "9": return KeyEvent.KEYCODE_9;
            case "0": return KeyEvent.KEYCODE_0;
            default: return -1;
        }
    }
}