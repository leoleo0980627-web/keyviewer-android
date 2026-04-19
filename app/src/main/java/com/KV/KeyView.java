package com.KV;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class KeyView extends View {

    private static final String TAG = "KV_KeyView";

    private int gridSizePx;
    private int defaultKeySizePx;
    private int selectionPaddingPx;
    private int handleSizePx;

    private Matrix canvasMatrix = new Matrix();
    private Matrix inverseMatrix = new Matrix();
    private float[] tempPoints = new float[2];

    private float scaleFactor = 1.0f;
    private float translateX = 0f;
    private float translateY = 0f;
    private static final float MIN_SCALE = 0.3f;
    private static final float MAX_SCALE = 3.0f;
    private static final float DEFAULT_SCALE = 1.0f;

    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;
    private boolean isLongPressTriggered = false;

    private static final int NONE = 0;
    private static final int DRAG_BODY = 1;
    private static final int DRAG_TOP = 2;
    private static final int DRAG_BOTTOM = 3;
    private static final int DRAG_LEFT = 4;
    private static final int DRAG_RIGHT = 5;
    private static final int PAN_CANVAS = 6;

    private int dragMode = NONE;
    private float lastTouchX;
    private float lastTouchY;
    private float minKeySize;

    private Paint gridPaint;
    private Paint keyFillPaint;
    private Paint keyBorderPaint;
    private Paint keyPressedPaint;
    private Paint rainPaint;
    private TextPaint textPaint;

    private Drawable selectionBorderDrawable;
    private Drawable handleDrawable;
    private Drawable multiSelectBorderDrawable;

    private KeyManager keyManager = new KeyManager();
    private KeyData selectedKey;
    private List<KeyData> selectedKeys = new ArrayList<>();
    private boolean batchEditMode = false;

    private RectF handleTopRect = new RectF();
    private RectF handleBottomRect = new RectF();
    private RectF handleLeftRect = new RectF();
    private RectF handleRightRect = new RectF();
    private RectF selectionRect = new RectF();

    private OnSelectionChangedListener selectionListener;
    private OnKeyUpdatedListener keyUpdatedListener;

    private Handler handler = new Handler();

    private boolean spaceZoomLocked = false;
    private boolean drawBackground = true;
    private boolean editable = true;

    private static final int RAIN_UPDATE_INTERVAL = 16;
    private long lastUpdateTime = 0;
    private float rainMaxHeightPx;
    private float rainCutLineY;

    private static final Map<String, String> KEY_DISPLAY_MAP = new HashMap<>();
    static {
        KEY_DISPLAY_MAP.put("lctrl", "L Ctrl");
        KEY_DISPLAY_MAP.put("rctrl", "R Ctrl");
        KEY_DISPLAY_MAP.put("lalt", "L Alt");
        KEY_DISPLAY_MAP.put("ralt", "R Alt");
        KEY_DISPLAY_MAP.put("lshift", "L⇧");
        KEY_DISPLAY_MAP.put("rshift", "R⇧");
        KEY_DISPLAY_MAP.put("enter", "⏎");
        KEY_DISPLAY_MAP.put("tab", "↹");
        KEY_DISPLAY_MAP.put("space", "⎵");
        KEY_DISPLAY_MAP.put("capslock", "⇪");
        KEY_DISPLAY_MAP.put("backspace", "back");
        KEY_DISPLAY_MAP.put("del", "del");
        KEY_DISPLAY_MAP.put("pageup", "pg⇑");
        KEY_DISPLAY_MAP.put("pagedown", "pg⇓");
        KEY_DISPLAY_MAP.put("home", "home");
        KEY_DISPLAY_MAP.put("end", "end");
        KEY_DISPLAY_MAP.put("up", "↑");
        KEY_DISPLAY_MAP.put("down", "↓");
        KEY_DISPLAY_MAP.put("left", "←");
        KEY_DISPLAY_MAP.put("right", "→");
        KEY_DISPLAY_MAP.put("comma", ",");
        KEY_DISPLAY_MAP.put("dot", ".");
        KEY_DISPLAY_MAP.put("semicolon", ";");
        KEY_DISPLAY_MAP.put("slash", "/");
        KEY_DISPLAY_MAP.put("backslash", "\\");
        KEY_DISPLAY_MAP.put("quote", "'");
        KEY_DISPLAY_MAP.put("backquote", "~");
        KEY_DISPLAY_MAP.put("bracketleft", "[");
        KEY_DISPLAY_MAP.put("bracketright", "]");
        KEY_DISPLAY_MAP.put("minus", "-");
        KEY_DISPLAY_MAP.put("equal", "=");
    }

    public interface OnSelectionChangedListener {
        void onSelectionChanged(boolean hasSelection);
    }

    public interface OnKeyUpdatedListener {
        void onKeyUpdated(KeyData key);
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionListener = listener;
    }

    public void setOnKeyUpdatedListener(OnKeyUpdatedListener listener) {
        this.keyUpdatedListener = listener;
    }

    public KeyView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        gridSizePx = (int) (15 * density);
        defaultKeySizePx = (int) (50 * density);
        int borderWidthPx = (int) (5 * density);
        selectionPaddingPx = (int) (15 * density);
        handleSizePx = (int) (12 * density);
        minKeySize = handleSizePx + borderWidthPx * 2;

        rainMaxHeightPx = 200 * density;
        rainCutLineY = -rainMaxHeightPx - 10 * density;

        gridPaint = new Paint();
        gridPaint.setColor(context.getResources().getColor(R.color.grid_line));
        gridPaint.setStrokeWidth(1f);

        keyFillPaint = new Paint();
        keyFillPaint.setStyle(Paint.Style.FILL);
        keyFillPaint.setAntiAlias(true);

        keyPressedPaint = new Paint();
        keyPressedPaint.setStyle(Paint.Style.FILL);
        keyPressedPaint.setColor(context.getResources().getColor(R.color.key_pressed));
        keyPressedPaint.setAntiAlias(true);

        keyBorderPaint = new Paint();
        keyBorderPaint.setStyle(Paint.Style.STROKE);
        keyBorderPaint.setAntiAlias(true);

        rainPaint = new Paint();
        rainPaint.setStyle(Paint.Style.FILL);
        rainPaint.setAntiAlias(true);

        textPaint = new TextPaint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
        textPaint.setFakeBoldText(true);
        
        try {
            Typeface typeface = Typeface.createFromAsset(context.getAssets(), "fonts/adofai.ttf");
            textPaint.setTypeface(typeface);
        } catch (Exception e) {
            // 字体加载失败，使用默认字体
        }

        selectionBorderDrawable = context.getResources().getDrawable(R.drawable.bg_key_border);
        handleDrawable = context.getResources().getDrawable(R.drawable.bg_handle);
        multiSelectBorderDrawable = context.getResources().getDrawable(R.drawable.bg_multi_select);

        gestureDetector = new GestureDetector(context, new GestureListener());
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());

        keyManager.setContext(context);
        keyManager.loadFromPreferences();
    }

    public void setBatchEditMode(boolean enabled) {
        this.batchEditMode = enabled;
        if (!enabled) {
            selectedKeys.clear();
        } else {
            selectedKey = null;
            keyManager.clearSelection();
        }
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(false);
        }
        invalidate();
    }

    public boolean isBatchEditMode() {
        return batchEditMode;
    }

    public List<KeyData> getSelectedKeys() {
        if (batchEditMode) {
            return selectedKeys;
        } else if (selectedKey != null) {
            List<KeyData> list = new ArrayList<>();
            list.add(selectedKey);
            return list;
        }
        return new ArrayList<>();
    }

    public void setDrawBackground(boolean draw) {
        this.drawBackground = draw;
        invalidate();
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        FloatService service = FloatService.getInstance();
        if (service != null && service.getFloatView() != null) {
            service.getFloatView().postInvalidate();
        }
    }

    public boolean isDraggingKey() {
        return dragMode == DRAG_BODY || dragMode == DRAG_TOP || dragMode == DRAG_BOTTOM ||
                dragMode == DRAG_LEFT || dragMode == DRAG_RIGHT;
    }

    public KeyManager getKeyManager() {
        return keyManager;
    }

    public KeyData getSelectedKey() {
        return selectedKey;
    }

    public void addNewKey() {
        float worldCenterX = -translateX / scaleFactor + getWidth() / 2f / scaleFactor;
        float worldCenterY = -translateY / scaleFactor + getHeight() / 2f / scaleFactor;
        
        worldCenterX = Math.round(worldCenterX / gridSizePx) * gridSizePx;
        worldCenterY = Math.round(worldCenterY / gridSizePx) * gridSizePx;
        
        KeyData newKey = keyManager.createNewKey(worldCenterX, worldCenterY, 
                defaultKeySizePx, defaultKeySizePx);
        
        if (!batchEditMode) {
            selectedKey = newKey;
            keyManager.setSelectedKey(newKey.id);
        }
        updateRectsForSelected();
        
        keyManager.saveToPreferences();
        
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(!batchEditMode || !selectedKeys.isEmpty());
        }
        if (keyUpdatedListener != null) {
            keyUpdatedListener.onKeyUpdated(newKey);
        }
        invalidate();
    }

    public void deleteSelectedKey() {
        if (batchEditMode) {
            for (KeyData key : selectedKeys) {
                keyManager.deleteKey(key.id);
            }
            selectedKeys.clear();
            if (selectionListener != null) {
                selectionListener.onSelectionChanged(false);
            }
        } else if (selectedKey != null) {
            keyManager.deleteKey(selectedKey.id);
            selectedKey = null;
            if (selectionListener != null) {
                selectionListener.onSelectionChanged(false);
            }
        }
        keyManager.saveToPreferences();
        invalidate();
    }

    public void clearSelection() {
        selectedKey = null;
        selectedKeys.clear();
        keyManager.clearSelection();
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(false);
        }
        invalidate();
    }

    public void zoomIn() {
        if (spaceZoomLocked) return;
        scaleFactor = Math.min(MAX_SCALE, scaleFactor * 1.1f);
        updateMatrix();
        invalidate();
    }

    public void zoomOut() {
        if (spaceZoomLocked) return;
        scaleFactor = Math.max(MIN_SCALE, scaleFactor * 0.9f);
        updateMatrix();
        invalidate();
    }

    public void zoomReset() {
        if (spaceZoomLocked) return;
        scaleFactor = DEFAULT_SCALE;
        translateX = 0;
        translateY = 0;
        updateMatrix();
        invalidate();
    }

    public boolean isKeyBound(String keyName) {
        if (keyName == null) return false;
        for (KeyData key : keyManager.getKeys()) {
            if (key.boundKey != null && key.boundKey.equals(keyName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取按键映射后的键名
     * @param originalKey 原始按下的键名
     * @return 映射后的键名，如果没有映射则返回原键名
     */
    public String getMappedKey(String originalKey) {
        if (originalKey == null) return null;
        
        for (KeyData key : keyManager.getKeys()) {
            if (key.boundKey != null && key.boundKey.equals(originalKey)) {
                if (key.mappingEnabled && key.mappedKey != null && !key.mappedKey.isEmpty()) {
                    Log.d(TAG, "getMappedKey: " + originalKey + " -> " + key.mappedKey);
                    return key.mappedKey;
                }
                break;
            }
        }
        return originalKey;
    }

    public void notifyKeyPressed(String keyName) {
        if ("space".equals(keyName)) {
            spaceZoomLocked = true;
            handler.postDelayed(() -> spaceZoomLocked = false, 200);
        }
    }

    public void setKeyPressed(String keyCode, boolean pressed) {
        float density = getResources().getDisplayMetrics().density;
        for (KeyData key : keyManager.getKeys()) {
            if (key.boundKey != null && key.boundKey.equals(keyCode)) {
                key.isPressed = pressed;
                
                if (pressed && key.rainEnabled) {
                    key.isKeyPressed = true;
                    long now = System.currentTimeMillis();
                    if (now - key.lastParticleTime > 8) {
                        key.lastParticleTime = now;
                        float particleX = key.centerX + key.rainOffsetXDp * density;
                        float particleY = key.centerY - key.height / 2f - key.rainOffsetYDp * density;
                        float particleWidth = key.width * (key.rainWidthPercent / 100f);
                        KeyData.RainParticle p = new KeyData.RainParticle(
                                particleX, particleY, particleWidth, 
                                key.rainColor, 
                                key.rainSpeedDp * density);
                        key.rainParticles.add(p);
                    }
                } else if (!pressed && key.rainEnabled) {
                    key.isKeyPressed = false;
                    for (KeyData.RainParticle p : key.rainParticles) {
                        p.isReleased = true;
                    }
                    if (key.rainParticles.isEmpty()) {
                        float particleX = key.centerX + key.rainOffsetXDp * density;
                        float particleY = key.centerY - key.height / 2f - key.rainOffsetYDp * density;
                        float particleWidth = key.width * (key.rainWidthPercent / 100f);
                        KeyData.RainParticle p = new KeyData.RainParticle(
                                particleX, particleY, particleWidth, 
                                key.rainColor, 
                                key.rainSpeedDp * density);
                        p.height = 10 * density;
                        p.isReleased = true;
                        key.rainParticles.add(p);
                    }
                }
            }
        }
        invalidate();
    }

    private void updateRainParticles() {
        long now = System.currentTimeMillis();
        if (lastUpdateTime == 0) {
            lastUpdateTime = now;
            return;
        }
        float deltaTime = (now - lastUpdateTime) / 16f;
        lastUpdateTime = now;
        
        float density = getResources().getDisplayMetrics().density;
        GlobalSettings settings = GlobalSettings.getInstance(getContext());
        rainMaxHeightPx = settings.globalRainHeightDp * density;
        rainCutLineY = -rainMaxHeightPx - 10 * density;
        
        List<KeyData> keys = keyManager.getKeys();
        for (int i = keys.size() - 1; i >= 0; i--) {
            KeyData key = keys.get(i);
            if (!key.rainEnabled) continue;
            
            if (key.isKeyPressed) {
                for (KeyData.RainParticle p : key.rainParticles) {
                    if (!p.isReleased) {
                        float growth = key.rainSpeedDp * density * deltaTime;
                        p.height += growth;
                    }
                }
            }
            
            Iterator<KeyData.RainParticle> iterator = key.rainParticles.iterator();
            while (iterator.hasNext()) {
                KeyData.RainParticle p = iterator.next();
                
                if (p.isReleased) {
                    p.y -= p.speed * deltaTime;
                }
                
                float particleTop = p.y - p.height;
                if (particleTop <= rainCutLineY) {
                    p.hasTouchedLine = true;
                }
                
                if (p.hasTouchedLine && p.y <= rainCutLineY) {
                    iterator.remove();
                }
            }
        }
    }

    private void updateMatrix() {
        canvasMatrix.reset();
        canvasMatrix.postTranslate(translateX, translateY);
        canvasMatrix.postScale(scaleFactor, scaleFactor, 0, 0);
        canvasMatrix.invert(inverseMatrix);
    }

    private float[] screenToWorld(float screenX, float screenY) {
        tempPoints[0] = screenX;
        tempPoints[1] = screenY;
        inverseMatrix.mapPoints(tempPoints);
        return tempPoints;
    }

    private void updateRectsForSelected() {
        if (selectedKey == null) return;
        
        float halfW = selectedKey.width / 2f;
        float halfH = selectedKey.height / 2f;
        
        selectedKey.rect.set(
            selectedKey.centerX - halfW,
            selectedKey.centerY - halfH,
            selectedKey.centerX + halfW,
            selectedKey.centerY + halfH
        );
        
        selectionRect.set(
            selectedKey.rect.left - selectionPaddingPx,
            selectedKey.rect.top - selectionPaddingPx,
            selectedKey.rect.right + selectionPaddingPx,
            selectedKey.rect.bottom + selectionPaddingPx
        );
        
        float handleHalf = handleSizePx / 2f;
        float centerX = selectedKey.rect.centerX();
        float centerY = selectedKey.rect.centerY();
        
        handleTopRect.set(centerX - handleHalf, selectedKey.rect.top - handleHalf,
                centerX + handleHalf, selectedKey.rect.top + handleHalf);
        handleBottomRect.set(centerX - handleHalf, selectedKey.rect.bottom - handleHalf,
                centerX + handleHalf, selectedKey.rect.bottom + handleHalf);
        handleLeftRect.set(selectedKey.rect.left - handleHalf, centerY - handleHalf,
                selectedKey.rect.left + handleHalf, centerY + handleHalf);
        handleRightRect.set(selectedKey.rect.right - handleHalf, centerY - handleHalf,
                selectedKey.rect.right + handleHalf, centerY + handleHalf);
    }

    private void updateAllKeyRects() {
        for (KeyData key : keyManager.getKeys()) {
            float halfW = key.width / 2f;
            float halfH = key.height / 2f;
            key.rect.set(
                key.centerX - halfW,
                key.centerY - halfH,
                key.centerX + halfW,
                key.centerY + halfH
            );
        }
        updateRectsForSelected();
    }

    private void snapSelectedToGrid() {
        if (selectedKey == null) return;
        
        selectedKey.centerX = Math.round(selectedKey.centerX / gridSizePx) * gridSizePx;
        selectedKey.centerY = Math.round(selectedKey.centerY / gridSizePx) * gridSizePx;
        
        float halfW = selectedKey.width / 2f;
        float halfH = selectedKey.height / 2f;
        selectedKey.centerX = Math.max(halfW, selectedKey.centerX);
        selectedKey.centerY = Math.max(halfH, selectedKey.centerY);
        
        updateRectsForSelected();
        
        keyManager.saveToPreferences();
        
        if (keyUpdatedListener != null) {
            keyUpdatedListener.onKeyUpdated(selectedKey);
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateMatrix();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.save();
        canvas.concat(canvasMatrix);

        if (drawBackground) {
            canvas.drawColor(getResources().getColor(R.color.background_light));

            int width = getWidth();
            int height = getHeight();
            float startX = -translateX / scaleFactor;
            float startY = -translateY / scaleFactor;
            float endX = startX + width / scaleFactor;
            float endY = startY + height / scaleFactor;

            int gridStartX = ((int) (startX / gridSizePx) - 1) * gridSizePx;
            int gridStartY = ((int) (startY / gridSizePx) - 1) * gridSizePx;
            int gridEndX = ((int) (endX / gridSizePx) + 2) * gridSizePx;
            int gridEndY = ((int) (endY / gridSizePx) + 2) * gridSizePx;

            for (int x = gridStartX; x <= gridEndX; x += gridSizePx) {
                canvas.drawLine(x, gridStartY, x, gridEndY, gridPaint);
            }
            for (int y = gridStartY; y <= gridEndY; y += gridSizePx) {
                canvas.drawLine(gridStartX, y, gridEndX, y, gridPaint);
            }
        }

        // 先绘制键雨粒子（在所有按键下方）
        List<KeyData> keys = keyManager.getKeys();
        for (int i = keys.size() - 1; i >= 0; i--) {
            KeyData key = keys.get(i);
            if (key.rainEnabled) {
                for (KeyData.RainParticle p : key.rainParticles) {
                    rainPaint.setColor(p.color);
                    float left = p.x - p.width / 2f;
                    float top = p.y - p.height;
                    float right = p.x + p.width / 2f;
                    float bottom = p.y;
                    canvas.drawRect(left, top, right, bottom, rainPaint);
                }
            }
        }

        updateAllKeyRects();
        
        // 按 zIndex 排序后绘制（数字越大越靠上）
        List<KeyData> sortedKeys = new ArrayList<>(keys);
        sortedKeys.sort((a, b) -> Integer.compare(a.zIndex, b.zIndex));
        
        for (KeyData key : sortedKeys) {
            drawKey(canvas, key);
        }

        if (batchEditMode) {
            for (KeyData key : selectedKeys) {
                multiSelectBorderDrawable.setBounds(
                    (int) key.rect.left - 5, (int) key.rect.top - 5,
                    (int) key.rect.right + 5, (int) key.rect.bottom + 5);
                multiSelectBorderDrawable.draw(canvas);
            }
        }

        if (selectedKey != null && !batchEditMode) {
            selectionBorderDrawable.setBounds(
                (int) selectionRect.left, (int) selectionRect.top,
                (int) selectionRect.right, (int) selectionRect.bottom);
            selectionBorderDrawable.draw(canvas);

            handleDrawable.setBounds(
                (int) handleTopRect.left, (int) handleTopRect.top,
                (int) handleTopRect.right, (int) handleTopRect.bottom);
            handleDrawable.draw(canvas);

            handleDrawable.setBounds(
                (int) handleBottomRect.left, (int) handleBottomRect.top,
                (int) handleBottomRect.right, (int) handleBottomRect.bottom);
            handleDrawable.draw(canvas);

            handleDrawable.setBounds(
                (int) handleLeftRect.left, (int) handleLeftRect.top,
                (int) handleLeftRect.right, (int) handleLeftRect.bottom);
            handleDrawable.draw(canvas);

            handleDrawable.setBounds(
                (int) handleRightRect.left, (int) handleRightRect.top,
                (int) handleRightRect.right, (int) handleRightRect.bottom);
            handleDrawable.draw(canvas);
        }

        canvas.restore();
    }

    private void drawKey(Canvas canvas, KeyData key) {
        float density = getResources().getDisplayMetrics().density;
        float cornerRadius = key.cornerRadiusDp * density;
        
        Paint fillPaint = key.isPressed ? keyPressedPaint : keyFillPaint;
        if (!key.isPressed) {
            fillPaint.setColor(key.fillColor);
        }
        canvas.drawRoundRect(key.rect, cornerRadius, cornerRadius, fillPaint);
        
        keyBorderPaint.setColor(key.borderColor);
        keyBorderPaint.setStrokeWidth(key.borderWidthDp * density);
        canvas.drawRoundRect(key.rect, cornerRadius, cornerRadius, keyBorderPaint);
        
        String displayText = getDisplayTextForKey(key);
        if (displayText != null && !displayText.isEmpty()) {
            String[] lines = displayText.split("\n");
            
            float actualTextSize = key.textSizeSp * density;
            
            // 多行时根据数字位数调整字体大小
            if (lines.length >= 2) {
                String countStr = lines[lines.length - 1];
                int digitCount = countStr.length();
                if (digitCount >= 9) actualTextSize *= 0.5f;
                else if (digitCount >= 8) actualTextSize *= 0.55f;
                else if (digitCount >= 7) actualTextSize *= 0.6f;
                else if (digitCount >= 6) actualTextSize *= 0.7f;
                else if (digitCount >= 5) actualTextSize *= 0.8f;
                else if (digitCount >= 4) actualTextSize *= 0.9f;
            }
            
            textPaint.setTextSize(actualTextSize);
            
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float lineHeight = metrics.descent - metrics.ascent;
            float totalHeight = lineHeight * lines.length;
            
            float centerY = key.rect.centerY();
            
            if (lines.length == 1) {
                // 单行：直接垂直居中
                float y = centerY - (metrics.ascent + metrics.descent) / 2;
                canvas.drawText(lines[0], key.rect.centerX(), y, textPaint);
            } else {
                // 多行：整体居中
                float firstLineBaseline = centerY - totalHeight / 2 - metrics.ascent;
                for (int i = 0; i < lines.length; i++) {
                    float y = firstLineBaseline + i * lineHeight;
                    canvas.drawText(lines[i], key.rect.centerX(), y, textPaint);
                }
            }
        }
    }

    /**
     * 获取按键显示文本
     * 
     * 逻辑说明：
     * 1. 如果是 KPS/TOTAL 特殊标签，显示动态内容（始终带数字）
     * 2. 如果有自定义显示文字：
     *    - 如果 showCount 为 true 且按键已绑定，显示 "文字\n次数"
     *    - 否则只显示文字
     * 3. 如果没有自定义文字：
     *    - 如果按键已绑定：
     *      - showCount 为 true 时显示 "按键名\n次数"
     *      - 否则只显示按键名
     *    - 未绑定则显示空
     */
    private String getDisplayTextForKey(KeyData key) {
        boolean hasCustomLabel = key.displayLabel != null && !key.displayLabel.isEmpty();
        
        // 处理特殊标签 (KPS/TOTAL) - 这些始终显示动态内容
        if (hasCustomLabel) {
            String lower = key.displayLabel.toLowerCase();
            if (lower.equals("kps")) {
                return "KPS\n" + keyManager.getKPS();
            } else if (lower.contains("tot")) {
                return "TOTAL\n" + keyManager.getTotalPressCount();
            }
        }
        
        // 获取基础显示文本
        String baseText;
        if (hasCustomLabel) {
            baseText = key.displayLabel;
        } else {
            // 没有自定义标签，使用绑定的按键名
            if (key.boundKey == null || key.boundKey.equals("未绑定") || key.boundKey.isEmpty()) {
                return "";
            }
            String displayName = KEY_DISPLAY_MAP.get(key.boundKey);
            if (displayName == null) {
                displayName = key.boundKey.toUpperCase();
            }
            baseText = displayName;
        }
        
        // 根据 showCount 决定是否显示次数
        if (key.showCount) {
            // 确保按键已绑定且不是未绑定状态
            if (key.boundKey != null && !key.boundKey.equals("未绑定") && !key.boundKey.isEmpty()) {
                int count = keyManager.getKeyCount(key.boundKey);
                return baseText + "\n" + count;
            }
        }
        
        return baseText;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!editable) return false;
        
        scaleGestureDetector.onTouchEvent(event);

        float screenX = event.getX();
        float screenY = event.getY();
        float[] world = screenToWorld(screenX, screenY);
        float worldX = world[0];
        float worldY = world[1];

        gestureDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragMode = NONE;
                isLongPressTriggered = false;

                if (!batchEditMode && selectedKey != null) {
                    if (handleTopRect.contains(worldX, worldY)) {
                        dragMode = DRAG_TOP;
                    } else if (handleBottomRect.contains(worldX, worldY)) {
                        dragMode = DRAG_BOTTOM;
                    } else if (handleLeftRect.contains(worldX, worldY)) {
                        dragMode = DRAG_LEFT;
                    } else if (handleRightRect.contains(worldX, worldY)) {
                        dragMode = DRAG_RIGHT;
                    }
                }

                if (dragMode == NONE) {
                    KeyData hitKey = keyManager.findKeyAt(worldX, worldY);
                    if (hitKey != null) {
                        if (batchEditMode) {
                            if (selectedKeys.contains(hitKey)) {
                                selectedKeys.remove(hitKey);
                            } else {
                                selectedKeys.add(hitKey);
                            }
                            if (selectionListener != null) {
                                selectionListener.onSelectionChanged(!selectedKeys.isEmpty());
                            }
                            invalidate();
                        } else {
                            selectedKey = hitKey;
                            keyManager.setSelectedKey(hitKey.id);
                            keyManager.bringToFront(hitKey.id);
                            updateRectsForSelected();
                            dragMode = DRAG_BODY;
                            
                            if (selectionListener != null) {
                                selectionListener.onSelectionChanged(true);
                            }
                            if (keyUpdatedListener != null) {
                                keyUpdatedListener.onKeyUpdated(hitKey);
                            }
                            invalidate();
                        }
                    } else {
                        if (!batchEditMode && selectedKey != null) {
                            selectedKey = null;
                            keyManager.clearSelection();
                            if (selectionListener != null) {
                                selectionListener.onSelectionChanged(false);
                            }
                            invalidate();
                        }
                    }
                }

                if (dragMode != NONE && selectedKey != null) {
                    lastTouchX = screenX;
                    lastTouchY = screenY;
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (dragMode == NONE) return true;

                float dx = screenX - lastTouchX;
                float dy = screenY - lastTouchY;

                switch (dragMode) {
                    case PAN_CANVAS:
                        translateX += dx;
                        translateY += dy;
                        updateMatrix();
                        break;

                    case DRAG_BODY:
                        if (selectedKey != null) {
                            float worldDx = dx / scaleFactor;
                            float worldDy = dy / scaleFactor;
                            selectedKey.centerX += worldDx;
                            selectedKey.centerY += worldDy;
                            
                            float halfW = selectedKey.width / 2f;
                            float halfH = selectedKey.height / 2f;
                            selectedKey.centerX = Math.max(halfW, Math.min(selectedKey.centerX, getWidth() / scaleFactor - halfW));
                            selectedKey.centerY = Math.max(halfH, Math.min(selectedKey.centerY, getHeight() / scaleFactor - halfH));
                            
                            updateRectsForSelected();
                        }
                        break;

                    case DRAG_TOP:
                        if (selectedKey != null) {
                            float worldDyTop = dy / scaleFactor;
                            float newHeight = selectedKey.height - worldDyTop;
                            if (newHeight >= minKeySize) {
                                selectedKey.height = newHeight;
                                selectedKey.centerY += worldDyTop / 2f;
                            }
                            updateRectsForSelected();
                        }
                        break;

                    case DRAG_BOTTOM:
                        if (selectedKey != null) {
                            float worldDyBottom = dy / scaleFactor;
                            float newHeight = selectedKey.height + worldDyBottom;
                            if (newHeight >= minKeySize) {
                                selectedKey.height = newHeight;
                                selectedKey.centerY += worldDyBottom / 2f;
                            }
                            updateRectsForSelected();
                        }
                        break;

                    case DRAG_LEFT:
                        if (selectedKey != null) {
                            float worldDxLeft = dx / scaleFactor;
                            float newWidth = selectedKey.width - worldDxLeft;
                            if (newWidth >= minKeySize) {
                                selectedKey.width = newWidth;
                                selectedKey.centerX += worldDxLeft / 2f;
                            }
                            updateRectsForSelected();
                        }
                        break;

                    case DRAG_RIGHT:
                        if (selectedKey != null) {
                            float worldDxRight = dx / scaleFactor;
                            float newWidth = selectedKey.width + worldDxRight;
                            if (newWidth >= minKeySize) {
                                selectedKey.width = newWidth;
                                selectedKey.centerX += worldDxRight / 2f;
                            }
                            updateRectsForSelected();
                        }
                        break;
                }

                invalidate();
                lastTouchX = screenX;
                lastTouchY = screenY;
                return true;

            case MotionEvent.ACTION_UP:
                if (dragMode == DRAG_BODY || dragMode == DRAG_TOP || dragMode == DRAG_BOTTOM ||
                        dragMode == DRAG_LEFT || dragMode == DRAG_RIGHT) {
                    snapSelectedToGrid();
                    invalidate();
                }
                dragMode = NONE;
                isLongPressTriggered = false;
                return true;

            case MotionEvent.ACTION_CANCEL:
                dragMode = NONE;
                isLongPressTriggered = false;
                return true;
        }

        return super.onTouchEvent(event);
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public void onLongPress(MotionEvent e) {
            if (batchEditMode) return;
            
            float screenX = e.getX();
            float screenY = e.getY();
            float[] world = screenToWorld(screenX, screenY);
            float worldX = world[0];
            float worldY = world[1];

            KeyData hitKey = keyManager.findKeyAt(worldX, worldY);
            boolean hitHandle = false;
            if (selectedKey != null) {
                hitHandle = handleTopRect.contains(worldX, worldY) ||
                        handleBottomRect.contains(worldX, worldY) ||
                        handleLeftRect.contains(worldX, worldY) ||
                        handleRightRect.contains(worldX, worldY);
            }

            if (hitKey == null && !hitHandle) {
                dragMode = PAN_CANVAS;
                isLongPressTriggered = true;
                lastTouchX = e.getX();
                lastTouchY = e.getY();
            }
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (spaceZoomLocked) return false;
            
            float newScale = scaleFactor * detector.getScaleFactor();
            newScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScale));

            float focusX = detector.getFocusX();
            float focusY = detector.getFocusY();

            float scaleChange = newScale / scaleFactor;
            translateX = focusX - scaleChange * (focusX - translateX);
            translateY = focusY - scaleChange * (focusY - translateY);

            scaleFactor = newScale;
            updateMatrix();
            invalidate();
            return true;
        }
    }

    private Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            updateRainParticles();
            invalidate();
            postDelayed(this, RAIN_UPDATE_INTERVAL);
        }
    };

    public void startRefreshing() {
        post(refreshRunnable);
    }

    public void stopRefreshing() {
        removeCallbacks(refreshRunnable);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startRefreshing();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopRefreshing();
    }
}