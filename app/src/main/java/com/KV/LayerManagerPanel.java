package com.KV;

import android.app.Activity;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LayerManagerPanel {

    private Activity activity;
    private View panelView;
    private KeyView keyView;
    private KeyManager keyManager;
    
    private RecyclerView layerRecyclerView;
    private LayerAdapter adapter;
    
    private ItemTouchHelper itemTouchHelper;
    
    public interface OnLayerChangedListener {
        void onLayerChanged();
    }
    
    public interface OnCloseRequestedListener {
        void onCloseRequested();
    }
    
    private OnLayerChangedListener listener;
    private OnCloseRequestedListener closeListener;
    
    public LayerManagerPanel(Activity activity, View panelView, KeyView keyView) {
        this.activity = activity;
        this.panelView = panelView;
        this.keyView = keyView;
        this.keyManager = keyView.getKeyManager();
        
        initViews();
        setupRecyclerView();
    }
    
    public void setOnLayerChangedListener(OnLayerChangedListener listener) {
        this.listener = listener;
    }
    
    public void setOnCloseRequestedListener(OnCloseRequestedListener listener) {
        this.closeListener = listener;
    }
    
    private void initViews() {
        layerRecyclerView = panelView.findViewById(R.id.layerRecyclerView);
        
        panelView.findViewById(R.id.closeLayerPanelBtn).setOnClickListener(v -> {
            if (closeListener != null) {
                closeListener.onCloseRequested();
            }
        });
        
        View autoArrangeBtn = panelView.findViewById(R.id.autoArrangeBtn);
        if (autoArrangeBtn != null) {
            autoArrangeBtn.setOnClickListener(v -> {
                autoArrange();
            });
        }
        
        View resetZIndexBtn = panelView.findViewById(R.id.resetZIndexBtn);
        if (resetZIndexBtn != null) {
            resetZIndexBtn.setOnClickListener(v -> {
                resetAllZIndex();
            });
        }
    }
    
    private void setupRecyclerView() {
        layerRecyclerView.setLayoutManager(new LinearLayoutManager(activity));
        adapter = new LayerAdapter();
        layerRecyclerView.setAdapter(adapter);
        
        ItemTouchHelper.Callback callback = new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.START | ItemTouchHelper.END;
                return makeMovementFlags(dragFlags, 0);
            }
            
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, 
                                  RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();
                
                KeyData moved = adapter.keys.remove(fromPosition);
                adapter.keys.add(toPosition, moved);
                adapter.notifyItemMoved(fromPosition, toPosition);
                
                updateZIndexFromOrder();
                
                return true;
            }
            
            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            }
            
            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }
            
            @Override
            public boolean isItemViewSwipeEnabled() {
                return false;
            }
        };
        
        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(layerRecyclerView);
    }
    
    public void refreshList() {
        if (adapter != null) {
            adapter.updateKeys();
        }
    }
    
    private void updateZIndexFromOrder() {
        List<KeyData> adapterKeys = adapter.keys;
        List<KeyData> managerKeys = keyManager.getKeys();
        
        for (int i = 0; i < adapterKeys.size(); i++) {
            KeyData adapterKey = adapterKeys.get(i);
            for (KeyData managerKey : managerKeys) {
                if (managerKey.id.equals(adapterKey.id)) {
                    managerKey.zIndex = adapterKeys.size() - i;
                    break;
                }
            }
            adapterKey.zIndex = adapterKeys.size() - i;
        }
        
        keyManager.saveToPreferences();
        keyView.invalidate();
        keyView.requestLayout();
        
        if (listener != null) {
            listener.onLayerChanged();
        }
        
        refreshList();
    }
    
    private void autoArrange() {
        List<KeyData> adapterKeys = adapter.keys;
        List<KeyData> managerKeys = keyManager.getKeys();
        
        Collections.sort(adapterKeys, (a, b) -> {
            float yDiff = b.centerY - a.centerY;
            if (Math.abs(yDiff) > Math.min(a.height, b.height) / 2) {
                return Float.compare(b.centerY, a.centerY);
            }
            return Float.compare(a.centerX, b.centerX);
        });
        
        for (int i = 0; i < adapterKeys.size(); i++) {
            KeyData adapterKey = adapterKeys.get(i);
            adapterKey.zIndex = adapterKeys.size() - i;
            
            for (KeyData managerKey : managerKeys) {
                if (managerKey.id.equals(adapterKey.id)) {
                    managerKey.zIndex = adapterKeys.size() - i;
                    break;
                }
            }
        }
        
        keyManager.saveToPreferences();
        keyView.invalidate();
        keyView.requestLayout();
        
        if (listener != null) {
            listener.onLayerChanged();
        }
        
        refreshList();
    }
    
    private void resetAllZIndex() {
        for (KeyData key : keyManager.getKeys()) {
            key.zIndex = 0;
        }
        keyManager.saveToPreferences();
        keyView.invalidate();
        keyView.requestLayout();
        refreshList();
        
        if (listener != null) {
            listener.onLayerChanged();
        }
    }
    
    public void show() {
        refreshList();
        panelView.setVisibility(View.VISIBLE);
    }
    
    public void hide() {
        panelView.setVisibility(View.GONE);
    }
    
    public boolean isVisible() {
        return panelView.getVisibility() == View.VISIBLE;
    }
    
    private class LayerAdapter extends RecyclerView.Adapter<LayerAdapter.ViewHolder> {
        
        private List<KeyData> keys = new ArrayList<>();
        
        public LayerAdapter() {
            updateKeys();
        }
        
        public void updateKeys() {
            keys = new ArrayList<>(keyManager.getKeys());
            Collections.sort(keys, (a, b) -> Integer.compare(b.zIndex, a.zIndex));
            notifyDataSetChanged();
        }
        
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.layer_list_item, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            KeyData key = keys.get(position);
            
            String displayName = getDisplayName(key);
            holder.keyNameText.setText(displayName);
            holder.zIndexText.setText(String.valueOf(key.zIndex));
            
            int color = key.fillColor;
            holder.colorIndicator.setBackgroundColor(color);
            
            KeyData selectedKey = keyManager.getSelectedKey();
            if (selectedKey != null && key.id.equals(selectedKey.id)) {
                holder.itemView.setBackgroundColor(0xFFE3F2FD);
            } else {
                holder.itemView.setBackgroundColor(Color.WHITE);
            }
            
            // 点击列表项：选中对应格子，自动触发设置面板
            holder.itemView.setOnClickListener(v -> {
                keyView.selectKeyById(key.id);
                notifyDataSetChanged();
            });
            
            holder.dragHandle.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper.startDrag(holder);
                }
                return false;
            });
            
            holder.dragHandle.setOnClickListener(v -> {});
        }
        
        @Override
        public int getItemCount() {
            return keys.size();
        }
        
        private String getDisplayName(KeyData key) {
            if (key.displayLabel != null && !key.displayLabel.isEmpty()) {
                String lower = key.displayLabel.toLowerCase();
                if (lower.equals("kps") || lower.contains("tot")) {
                    return key.displayLabel;
                }
                return key.displayLabel;
            }
            if (key.boundKey != null && !key.boundKey.equals("未绑定")) {
                return key.boundKey;
            }
            return "未命名按键";
        }
        
        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView dragHandle;
            View colorIndicator;
            TextView keyNameText;
            TextView zIndexText;
            
            public ViewHolder(View itemView) {
                super(itemView);
                dragHandle = itemView.findViewById(R.id.dragHandle);
                colorIndicator = itemView.findViewById(R.id.colorIndicator);
                keyNameText = itemView.findViewById(R.id.keyNameText);
                zIndexText = itemView.findViewById(R.id.zIndexText);
            }
        }
    }
}