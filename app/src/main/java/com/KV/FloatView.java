package com.KV;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

public class FloatView extends View {

    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;
    private KeyView keyView;
    private GlobalSettings settings;
    
    private float lastTouchX;
    private float lastTouchY;
    private boolean isDragging = false;
    private static final int DRAG_THRESHOLD = 10;
    
    private int contentWidth;
    private int contentHeight;
    private int keysOnlyHeight;
    
    private float floatOffsetX = 0;
    private float floatOffsetY = 0;
    
    private boolean updating = false;

    public FloatView(Context context, KeyView sharedKeyView) {
        super(context);
        settings = GlobalSettings.getInstance(context);
        this.keyView = sharedKeyView;
        init();
    }

    private void init() {
        windowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        setBackgroundColor(Color.TRANSPARENT);
    }

    public void setupWindowParams() {
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }
        
        measureContent();
        calculateCenterPosition();
        
        layoutParams = new WindowManager.LayoutParams(
            contentWidth > 0 ? contentWidth : 300,
            contentHeight > 0 ? contentHeight : 200,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = settings.floatWindowX;
        layoutParams.y = settings.floatWindowY;
    }

    private void measureContent() {
        if (keyView == null) return;
        
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;
        
        for (KeyData key : keyView.getKeyManager().getKeys()) {
            float halfW = key.width / 2f;
            float halfH = key.height / 2f;
            minX = Math.min(minX, key.centerX - halfW);
            minY = Math.min(minY, key.centerY - halfH);
            maxX = Math.max(maxX, key.centerX + halfW);
            maxY = Math.max(maxY, key.centerY + halfH);
        }
        
        if (minX == Float.MAX_VALUE) {
            contentWidth = 300;
            contentHeight = 200;
            keysOnlyHeight = 200;
        } else {
            float density = getResources().getDisplayMetrics().density;
            
            int baseWidth = (int) (maxX - minX) + 40;
            keysOnlyHeight = (int) (maxY - minY) + 40;
            
            contentWidth = baseWidth;
            
            int rainHeight = (int) (settings.globalRainHeightDp * density);
            contentHeight = keysOnlyHeight + rainHeight;
            
            floatOffsetX = -minX + 20;
            floatOffsetY = rainHeight + (-minY + 20);
        }
    }

    private void calculateCenterPosition() {
        int screenWidth = windowManager.getDefaultDisplay().getWidth();
        int screenHeight = windowManager.getDefaultDisplay().getHeight();
        
        settings.floatWindowX = (screenWidth - contentWidth) / 2;
        settings.floatWindowY = (screenHeight - keysOnlyHeight) / 2 - (contentHeight - keysOnlyHeight);
        settings.save();
    }

    public void attachToWindow() {
        // 开启悬浮窗前，重置预览的缩放和平移，让按键居中显示
        if (keyView != null) {
            keyView.setEditable(false);
            keyView.zoomReset();
            keyView.startRefreshing();
        }
        
        setupWindowParams();
        windowManager.addView(this, layoutParams);
        
        postDelayed(() -> {
            measureContent();
            calculateCenterPosition();
            if (layoutParams != null) {
                layoutParams.width = contentWidth;
                layoutParams.height = contentHeight;
                layoutParams.x = settings.floatWindowX;
                layoutParams.y = settings.floatWindowY;
                windowManager.updateViewLayout(FloatView.this, layoutParams);
            }
        }, 100);
    }

    public void updatePositionFromSettings() {
        if (layoutParams != null) {
            layoutParams.x = settings.floatWindowX;
            layoutParams.y = settings.floatWindowY;
            windowManager.updateViewLayout(this, layoutParams);
        }
    }

    public void detachFromWindow() {
        if (keyView != null) {
            keyView.setEditable(true);
            keyView.stopRefreshing();
            // 不恢复预览位置，保持居中
        }
        try {
            windowManager.removeView(this);
        } catch (IllegalArgumentException e) {
            // 已经移除
        }
    }

    public KeyView getKeyView() {
        return keyView;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(contentWidth, contentHeight);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.TRANSPARENT);
        
        if (keyView != null) {
            canvas.save();
            float scale = settings.floatWindowScale;
            canvas.scale(scale, scale);
            canvas.translate(floatOffsetX, floatOffsetY);
            
            keyView.setDrawBackground(false);
            keyView.draw(canvas);
            keyView.setDrawBackground(true);
            
            canvas.restore();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getRawX();
        float y = event.getRawY();
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = x;
                lastTouchY = y;
                isDragging = false;
                return true;
                
            case MotionEvent.ACTION_MOVE:
                float dx = x - lastTouchX;
                float dy = y - lastTouchY;
                
                if (!isDragging && (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD)) {
                    isDragging = true;
                }
                
                if (isDragging) {
                    layoutParams.x += dx;
                    layoutParams.y += dy;
                    windowManager.updateViewLayout(this, layoutParams);
                    
                    settings.floatWindowX = layoutParams.x;
                    settings.floatWindowY = layoutParams.y;
                    settings.save();
                }
                
                lastTouchX = x;
                lastTouchY = y;
                return true;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                return true;
        }
        
        return super.onTouchEvent(event);
    }

    public void updateScale() {
        if (updating) return;
        updating = true;
        
        measureContent();
        if (layoutParams != null) {
            layoutParams.width = contentWidth;
            layoutParams.height = contentHeight;
            windowManager.updateViewLayout(this, layoutParams);
        }
        requestLayout();
        invalidate();
        
        updating = false;
    }
    
    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateScale();
    }
}