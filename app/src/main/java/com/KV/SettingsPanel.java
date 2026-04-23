package com.KV;

import android.app.Activity;
import android.graphics.Color;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;

import java.util.List;

public class SettingsPanel {

    private static final String TAG = "KV_Settings";
    
    private Activity activity;
    private View panelView;
    private KeyView keyView;
    private RishInputListener inputListener;

    private Button keyBindBtn;
    private EditText labelInput;
    private EditText widthInput;
    private EditText heightInput;
    private EditText cornerRadiusInput;
    private EditText borderWidthInput;
    private EditText fillColorInput;
    private View fillColorPreview;
    private EditText borderColorInput;
    private View borderColorPreview;
    private EditText textColorInput;
    private View textColorPreview;
    private EditText textSizeInput;
    private Switch showCountSwitch;

    private Switch mappingEnabledSwitch;
    private LinearLayout mappingSettingsContainer;
    private Button mappingBindBtn;

    private Switch rainEnabledSwitch;
    private LinearLayout rainSettingsContainer;
    private EditText rainOffsetXInput;
    private EditText rainOffsetYInput;
    private EditText rainWidthInput;
    private EditText rainColorInput;
    private View rainColorPreview;
    private EditText rainSpeedInput;

    private boolean isListeningForKey = false;
    private boolean isListeningForMapping = false;

    public SettingsPanel(Activity activity, View panelView, KeyView keyView) {
        this.activity = activity;
        this.panelView = panelView;
        this.keyView = keyView;

        initViews();
        setupListeners();
        syncFromKeyView();
    }
    
    public void setInputListener(RishInputListener listener) {
        this.inputListener = listener;
    }
    
    public boolean isListeningForKey() {
        return isListeningForKey || isListeningForMapping;
    }

    private void initViews() {
        keyBindBtn = (Button) panelView.findViewById(R.id.keyBindBtn);
        labelInput = (EditText) panelView.findViewById(R.id.labelInput);
        widthInput = (EditText) panelView.findViewById(R.id.widthInput);
        heightInput = (EditText) panelView.findViewById(R.id.heightInput);
        cornerRadiusInput = (EditText) panelView.findViewById(R.id.cornerRadiusInput);
        borderWidthInput = (EditText) panelView.findViewById(R.id.borderWidthInput);
        fillColorInput = (EditText) panelView.findViewById(R.id.fillColorInput);
        fillColorPreview = panelView.findViewById(R.id.fillColorPreview);
        borderColorInput = (EditText) panelView.findViewById(R.id.borderColorInput);
        borderColorPreview = panelView.findViewById(R.id.borderColorPreview);
        textColorInput = (EditText) panelView.findViewById(R.id.textColorInput);
        textColorPreview = panelView.findViewById(R.id.textColorPreview);
        textSizeInput = (EditText) panelView.findViewById(R.id.textSizeInput);
        showCountSwitch = (Switch) panelView.findViewById(R.id.showCountSwitch);

        mappingEnabledSwitch = (Switch) panelView.findViewById(R.id.mappingEnabledSwitch);
        mappingSettingsContainer = (LinearLayout) panelView.findViewById(R.id.mappingSettingsContainer);
        mappingBindBtn = (Button) panelView.findViewById(R.id.mappingBindBtn);

        rainEnabledSwitch = (Switch) panelView.findViewById(R.id.rainEnabledSwitch);
        rainSettingsContainer = (LinearLayout) panelView.findViewById(R.id.rainSettingsContainer);
        rainOffsetXInput = (EditText) panelView.findViewById(R.id.rainOffsetXInput);
        rainOffsetYInput = (EditText) panelView.findViewById(R.id.rainOffsetYInput);
        rainWidthInput = (EditText) panelView.findViewById(R.id.rainWidthInput);
        rainColorInput = (EditText) panelView.findViewById(R.id.rainColorInput);
        rainColorPreview = panelView.findViewById(R.id.rainColorPreview);
        rainSpeedInput = (EditText) panelView.findViewById(R.id.rainSpeedInput);
    }

    private void setupListeners() {
        keyBindBtn.setOnClickListener(v -> {
            if (!isListeningForKey && !isListeningForMapping) {
                isListeningForKey = true;
                keyBindBtn.setText("按下按键...");
                if (inputListener != null) inputListener.pause();
            }
        });

        mappingBindBtn.setOnClickListener(v -> {
            if (!isListeningForKey && !isListeningForMapping) {
                isListeningForMapping = true;
                mappingBindBtn.setText("按下映射目标按键...");
                if (inputListener != null) inputListener.pause();
            }
        });

        mappingEnabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mappingSettingsContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            for (KeyData key : getTargetKeys()) {
                key.mappingEnabled = isChecked;
            }
            keyView.getKeyManager().saveToPreferences();
        });

        labelInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        widthInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        heightInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        cornerRadiusInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        borderWidthInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        fillColorInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        borderColorInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        textColorInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        textSizeInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        rainOffsetXInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        rainOffsetYInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        rainWidthInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        rainColorInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        rainSpeedInput.setImeOptions(EditorInfo.IME_ACTION_DONE);

        setupEditorAction(labelInput, this::updateLabel);
        setupEditorAction(widthInput, this::updateSize);
        setupEditorAction(heightInput, this::updateSize);
        setupEditorAction(cornerRadiusInput, this::updateCornerRadius);
        setupEditorAction(borderWidthInput, this::updateBorderWidth);
        setupEditorAction(fillColorInput, this::updateFillColor);
        setupEditorAction(borderColorInput, this::updateBorderColor);
        setupEditorAction(textColorInput, this::updateTextColor);
        setupEditorAction(textSizeInput, this::updateTextSize);
        setupEditorAction(rainOffsetXInput, this::updateRainOffsetX);
        setupEditorAction(rainOffsetYInput, this::updateRainOffsetY);
        setupEditorAction(rainWidthInput, this::updateRainWidth);
        setupEditorAction(rainColorInput, this::updateRainColor);
        setupEditorAction(rainSpeedInput, this::updateRainSpeed);

        showCountSwitch.setOnCheckedChangeListener((v, isChecked) -> {
            for (KeyData key : getTargetKeys()) {
                key.showCount = isChecked;
            }
            keyView.getKeyManager().saveToPreferences();
            keyView.invalidate();
        });

        rainEnabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            rainSettingsContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            for (KeyData key : getTargetKeys()) {
                key.rainEnabled = isChecked;
            }
            keyView.getKeyManager().saveToPreferences();
        });
    }

    private void setupEditorAction(EditText editText, Runnable action) {
        editText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                action.run();
                editText.clearFocus();
                return true;
            }
            return false;
        });
    }

    private List<KeyData> getTargetKeys() {
        return keyView.getSelectedKeys();
    }

    private void updateLabel() {
        String label = labelInput.getText().toString();
        for (KeyData key : getTargetKeys()) {
            key.displayLabel = label;
        }
        keyView.getKeyManager().saveToPreferences();
        keyView.invalidate();
    }

    private void updateSize() {
        try {
            float w = Float.parseFloat(widthInput.getText().toString());
            float h = Float.parseFloat(heightInput.getText().toString());
            float density = activity.getResources().getDisplayMetrics().density;
            for (KeyData key : getTargetKeys()) {
                key.width = w * density;
                key.height = h * density;
            }
            keyView.getKeyManager().saveToPreferences();
            keyView.invalidate();
        } catch (NumberFormatException ignored) {}
    }

    private void updateCornerRadius() {
        try {
            float r = Float.parseFloat(cornerRadiusInput.getText().toString());
            for (KeyData key : getTargetKeys()) {
                key.cornerRadiusDp = r;
            }
            keyView.getKeyManager().saveToPreferences();
            keyView.invalidate();
        } catch (NumberFormatException ignored) {}
    }

    private void updateBorderWidth() {
        try {
            float w = Float.parseFloat(borderWidthInput.getText().toString());
            for (KeyData key : getTargetKeys()) {
                key.borderWidthDp = w;
            }
            keyView.getKeyManager().saveToPreferences();
            keyView.invalidate();
        } catch (NumberFormatException ignored) {}
    }

    private int parseColor(String hex) {
        try {
            if (hex == null || hex.isEmpty()) {
                return 0xFF999999;
            }
            
            hex = hex.trim();
            
            if (hex.startsWith("#")) {
                hex = hex.substring(1);
            }
            
            if (hex.length() == 6) {
                hex = "FF" + hex;
            } else if (hex.length() == 3) {
                char r = hex.charAt(0);
                char g = hex.charAt(1);
                char b = hex.charAt(2);
                hex = "FF" + r + r + g + g + b + b;
            } else if (hex.length() == 4) {
                char a = hex.charAt(0);
                char r = hex.charAt(1);
                char g = hex.charAt(2);
                char b = hex.charAt(3);
                hex = "" + a + a + r + r + g + g + b + b;
            } else if (hex.length() != 8) {
                return 0xFF999999;
            }
            
            long color = Long.parseLong(hex, 16);
            return (int) color;
            
        } catch (Exception e) {
            Log.e(TAG, "parseColor failed: " + hex, e);
            return 0xFF999999;
        }
    }

    private String colorToHex(int color) {
        return String.format("#%08X", color);
    }

    private void updateFillColor() {
        String hex = fillColorInput.getText().toString();
        int color = parseColor(hex);
        for (KeyData key : getTargetKeys()) {
            key.fillColor = color;
        }
        fillColorPreview.setBackgroundColor(color);
        keyView.getKeyManager().saveToPreferences();
        keyView.invalidate();
        
        fillColorInput.setText(colorToHex(color));
    }

    private void updateBorderColor() {
        String hex = borderColorInput.getText().toString();
        int color = parseColor(hex);
        for (KeyData key : getTargetKeys()) {
            key.borderColor = color;
        }
        borderColorPreview.setBackgroundColor(color);
        keyView.getKeyManager().saveToPreferences();
        keyView.invalidate();
        
        borderColorInput.setText(colorToHex(color));
    }

    private void updateTextColor() {
        String hex = textColorInput.getText().toString();
        int color = parseColor(hex);
        for (KeyData key : getTargetKeys()) {
            key.textColor = color;
        }
        textColorPreview.setBackgroundColor(color);
        keyView.getKeyManager().saveToPreferences();
        keyView.invalidate();
        
        textColorInput.setText(colorToHex(color));
    }

    private void updateTextSize() {
        try {
            float size = Float.parseFloat(textSizeInput.getText().toString());
            for (KeyData key : getTargetKeys()) {
                key.textSizeSp = size;
            }
            keyView.getKeyManager().saveToPreferences();
            keyView.invalidate();
        } catch (NumberFormatException ignored) {}
    }

    private void updateRainOffsetX() {
        try {
            float x = Float.parseFloat(rainOffsetXInput.getText().toString());
            for (KeyData key : getTargetKeys()) {
                key.rainOffsetXDp = x;
            }
            keyView.getKeyManager().saveToPreferences();
        } catch (NumberFormatException ignored) {}
    }

    private void updateRainOffsetY() {
        try {
            float y = Float.parseFloat(rainOffsetYInput.getText().toString());
            for (KeyData key : getTargetKeys()) {
                key.rainOffsetYDp = y;
            }
            keyView.getKeyManager().saveToPreferences();
        } catch (NumberFormatException ignored) {}
    }

    private void updateRainWidth() {
        try {
            float width = Float.parseFloat(rainWidthInput.getText().toString());
            for (KeyData key : getTargetKeys()) {
                key.rainWidthPercent = width;
            }
            keyView.getKeyManager().saveToPreferences();
        } catch (NumberFormatException ignored) {}
    }

    private void updateRainColor() {
        String hex = rainColorInput.getText().toString();
        int color = parseColor(hex);
        for (KeyData key : getTargetKeys()) {
            key.rainColor = color;
        }
        rainColorPreview.setBackgroundColor(color);
        keyView.getKeyManager().saveToPreferences();
        
        rainColorInput.setText(colorToHex(color));
    }

    private void updateRainSpeed() {
        try {
            float speed = Float.parseFloat(rainSpeedInput.getText().toString());
            for (KeyData key : getTargetKeys()) {
                key.rainSpeedDp = speed;
            }
            keyView.getKeyManager().saveToPreferences();
        } catch (NumberFormatException ignored) {}
    }

    public void syncFromKeyView() {
        List<KeyData> keys = getTargetKeys();
        if (keys.isEmpty()) return;
        
        KeyData firstKey = keys.get(0);

        String displayName = getDisplayNameForInternalKey(firstKey.boundKey);
        keyBindBtn.setText(displayName);
        
        labelInput.setText(firstKey.displayLabel);
        widthInput.setText(String.valueOf((int) (firstKey.width / activity.getResources().getDisplayMetrics().density)));
        heightInput.setText(String.valueOf((int) (firstKey.height / activity.getResources().getDisplayMetrics().density)));
        cornerRadiusInput.setText(String.valueOf((int) firstKey.cornerRadiusDp));
        borderWidthInput.setText(String.valueOf((int) firstKey.borderWidthDp));
        textSizeInput.setText(String.valueOf((int) firstKey.textSizeSp));
        showCountSwitch.setChecked(firstKey.showCount);

        fillColorInput.setText(colorToHex(firstKey.fillColor));
        fillColorPreview.setBackgroundColor(firstKey.fillColor);

        borderColorInput.setText(colorToHex(firstKey.borderColor));
        borderColorPreview.setBackgroundColor(firstKey.borderColor);

        textColorInput.setText(colorToHex(firstKey.textColor));
        textColorPreview.setBackgroundColor(firstKey.textColor);

        mappingEnabledSwitch.setChecked(firstKey.mappingEnabled);
        mappingSettingsContainer.setVisibility(firstKey.mappingEnabled ? View.VISIBLE : View.GONE);
        String mappedDisplayName = getDisplayNameForInternalKey(firstKey.mappedKey);
        mappingBindBtn.setText(mappedDisplayName.isEmpty() ? "点击设置映射按键" : mappedDisplayName);

        rainEnabledSwitch.setChecked(firstKey.rainEnabled);
        rainSettingsContainer.setVisibility(firstKey.rainEnabled ? View.VISIBLE : View.GONE);
        rainOffsetXInput.setText(String.valueOf((int) firstKey.rainOffsetXDp));
        rainOffsetYInput.setText(String.valueOf((int) firstKey.rainOffsetYDp));
        rainWidthInput.setText(String.valueOf((int) firstKey.rainWidthPercent));
        rainColorInput.setText(colorToHex(firstKey.rainColor));
        rainColorPreview.setBackgroundColor(firstKey.rainColor);
        rainSpeedInput.setText(String.valueOf((int) firstKey.rainSpeedDp));
    }

    public boolean handleKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

        if (isListeningForKey) {
            String internalName = getInternalKeyName(event);
            for (KeyData key : getTargetKeys()) {
                key.boundKey = internalName;
            }
            String displayName = getDisplayNameForInternalKey(internalName);
            keyBindBtn.setText(displayName);
            keyView.getKeyManager().saveToPreferences();
            isListeningForKey = false;
            keyView.invalidate();
            if (inputListener != null) inputListener.resume();
            return true;
        }
        
        if (isListeningForMapping) {
            String internalName = getInternalKeyName(event);
            for (KeyData key : getTargetKeys()) {
                key.mappedKey = internalName;
            }
            String displayName = getDisplayNameForInternalKey(internalName);
            mappingBindBtn.setText(displayName);
            keyView.getKeyManager().saveToPreferences();
            isListeningForMapping = false;
            keyView.invalidate();
            if (inputListener != null) inputListener.resume();
            return true;
        }
        
        return false;
    }

    private String getInternalKeyName(KeyEvent event) {
        int keyCode = event.getKeyCode();
        Log.d(TAG, "getInternalKeyName: keyCode=" + keyCode);
        
        switch (keyCode) {
            case KeyEvent.KEYCODE_SPACE: return "space";
            case KeyEvent.KEYCODE_ENTER: return "enter";
            case KeyEvent.KEYCODE_DEL: return "backspace";
            case KeyEvent.KEYCODE_FORWARD_DEL: return "del";
            case KeyEvent.KEYCODE_TAB: return "tab";
            case KeyEvent.KEYCODE_ESCAPE: return "escape";
            case KeyEvent.KEYCODE_SHIFT_LEFT: return "lshift";
            case KeyEvent.KEYCODE_SHIFT_RIGHT: return "rshift";
            case KeyEvent.KEYCODE_CTRL_LEFT: return "lctrl";
            case KeyEvent.KEYCODE_CTRL_RIGHT: return "rctrl";
            case KeyEvent.KEYCODE_ALT_LEFT: return "lalt";
            case KeyEvent.KEYCODE_ALT_RIGHT: return "ralt";
            case KeyEvent.KEYCODE_DPAD_UP: return "up";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "down";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "left";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "right";
            case KeyEvent.KEYCODE_COMMA: return "comma";
            case KeyEvent.KEYCODE_PERIOD: return "dot";
            case KeyEvent.KEYCODE_SEMICOLON: return "semicolon";
            case KeyEvent.KEYCODE_SLASH: return "slash";
            case KeyEvent.KEYCODE_BACKSLASH: return "backslash";
            case KeyEvent.KEYCODE_APOSTROPHE: return "quote";
            case KeyEvent.KEYCODE_GRAVE: return "backquote";
            case KeyEvent.KEYCODE_LEFT_BRACKET: return "bracketleft";
            case KeyEvent.KEYCODE_RIGHT_BRACKET: return "bracketright";
            case KeyEvent.KEYCODE_MINUS: return "minus";
            case KeyEvent.KEYCODE_EQUALS: return "equal";
            case KeyEvent.KEYCODE_PAGE_UP: return "pageup";
            case KeyEvent.KEYCODE_PAGE_DOWN: return "pagedown";
            case KeyEvent.KEYCODE_MOVE_HOME: return "home";
            case KeyEvent.KEYCODE_MOVE_END: return "end";
            case KeyEvent.KEYCODE_CAPS_LOCK: return "capslock";
            case KeyEvent.KEYCODE_BACK: return "backspace";
            default:
                if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
                    return String.valueOf((char) ('a' + (keyCode - KeyEvent.KEYCODE_A)));
                }
                if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                    return String.valueOf((char) ('0' + (keyCode - KeyEvent.KEYCODE_0)));
                }
                return KeyEvent.keyCodeToString(keyCode);
        }
    }

    private String getDisplayNameForInternalKey(String internalName) {
        if (internalName == null || internalName.equals("未绑定") || internalName.isEmpty()) {
            return "未绑定";
        }
        
        switch (internalName) {
            case "space": return "空格";
            case "enter": return "回车";
            case "del": return "Del";
            case "tab": return "Tab";
            case "escape": return "Esc";
            case "lshift": return "左Shift";
            case "rshift": return "右Shift";
            case "lctrl": return "左Ctrl";
            case "rctrl": return "右Ctrl";
            case "lalt": return "左Alt";
            case "ralt": return "右Alt";
            case "up": return "↑";
            case "down": return "↓";
            case "left": return "←";
            case "right": return "→";
            case "comma": return ",";
            case "dot": return ".";
            case "semicolon": return ";";
            case "slash": return "/";
            case "backslash": return "\\";
            case "quote": return "'";
            case "backquote": return "`";
            case "bracketleft": return "[";
            case "bracketright": return "]";
            case "minus": return "-";
            case "equal": return "=";
            case "pageup": return "PgUp";
            case "pagedown": return "PgDn";
            case "home": return "Home";
            case "end": return "End";
            case "capslock": return "Caps";
            case "backspace": return "Back";
            default:
                if (internalName.length() == 1) return internalName.toUpperCase();
                return internalName;
        }
    }

    public void stopKeyListening() {
        isListeningForKey = false;
        isListeningForMapping = false;
        List<KeyData> keys = getTargetKeys();
        if (!keys.isEmpty()) {
            KeyData firstKey = keys.get(0);
            keyBindBtn.setText(getDisplayNameForInternalKey(firstKey.boundKey));
            mappingBindBtn.setText(firstKey.mappedKey.isEmpty() ? "点击设置映射按键" : getDisplayNameForInternalKey(firstKey.mappedKey));
        }
        if (inputListener != null) inputListener.resume();
    }
}