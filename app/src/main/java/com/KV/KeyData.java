package com.KV;

import android.graphics.RectF;
import java.util.ArrayList;
import java.util.List;

public class KeyData {
    public String id;
    public float centerX;
    public float centerY;
    public float width;
    public float height;
    
    public String boundKey = "未绑定";
    public String displayLabel = "";
    
    // ===== 形状设置 =====
    public float cornerRadiusDp = 8f;
    public float borderWidthDp = 5f;
    
    // ===== 颜色：放开时 =====
    public int fillColorUp = 0xFF999999;
    public int borderColorUp = 0xFF666666;
    public int textColorUp = 0xFFFFFFFF;
    public int countColorUp = 0xFFFFFFFF;
    
    // ===== 颜色：按下时 =====
    public int fillColorDown = 0xFFFFFFFF;
    public int borderColorDown = 0xFF666666;
    public int textColorDown = 0xFFFFFFFF;
    public int countColorDown = 0xFFFFFFFF;
    
    // ===== 旧字段兼容（保留但不再使用）=====
    @Deprecated
    public int fillColor = 0xFF999999;
    @Deprecated
    public int borderColor = 0xFF666666;
    
    // ===== 文字设置 =====
    public float textSizeSp = 14f;
    public boolean showCount = true;
    
    // ===== 映射按键 =====
    public boolean mappingEnabled = false;
    public String mappedKey = "";
    
    // ===== 键雨设置 =====
    public boolean rainEnabled = false;
    public float rainOffsetXDp = 0f;
    public float rainOffsetYDp = 0f;
    public float rainWidthPercent = 100f;
    public int rainColor = 0xFFFFFFFF;
    public float rainSpeedDp = 5f;
    
    // ===== KPS/TOTAL 特殊控制 =====
    public boolean kpsTotalNoWrap = false;
    public float kpsTotalLabelOffsetX = 0f;
    
    // ===== 图层顺序 =====
    public int zIndex = 0;
    
    // ===== 运行时状态 =====
    public boolean isPressed = false;
    public boolean isKeyPressed = false;
    public long lastParticleTime = 0;
    public List<RainParticle> rainParticles = new ArrayList<>();
    
    public transient RectF rect = new RectF();
    
    public KeyData(String id, float centerX, float centerY, float width, float height) {
        this.id = id;
        this.centerX = centerX;
        this.centerY = centerY;
        this.width = width;
        this.height = height;
    }
    
    public boolean isSpecialKey() {
        if (displayLabel == null || displayLabel.isEmpty()) return false;
        String lower = displayLabel.toLowerCase();
        return lower.equals("kps") || lower.contains("tot");
    }
    
    public String getDisplayText() {
        if (displayLabel != null && !displayLabel.isEmpty()) {
            return displayLabel;
        }
        return boundKey;
    }
    
    public void migrateFromOldFields() {
        if (fillColorUp == 0xFF999999 && fillColor != 0xFF999999) {
            fillColorUp = fillColor;
        }
        if (borderColorUp == 0xFF666666 && borderColor != 0xFF666666) {
            borderColorUp = borderColor;
            borderColorDown = borderColor;
        }
    }
    
    public static class RainParticle {
        public float x;
        public float y;
        public float width;
        public float height;
        public int color;
        public float speed;
        public boolean isReleased;
        public boolean hasTouchedLine;
        
        public RainParticle(float x, float y, float width, int color, float speed) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = 0;
            this.color = color;
            this.speed = speed;
            this.isReleased = false;
            this.hasTouchedLine = false;
        }
    }
}