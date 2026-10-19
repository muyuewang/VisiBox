package com.visibox.tv.ui.adapter;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.visibox.tv.Setting;
import com.visibox.tv.api.config.VodConfig;
import com.visibox.tv.bean.Site;
import com.visibox.tv.bean.SiteHealth;
import com.visibox.tv.databinding.AdapterSiteBinding;
import com.visibox.tv.utils.HealthMark;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SiteAdapter extends RecyclerView.Adapter<SiteAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Site> mItems;
    private final Map<String, SiteHealth> health;
    private int type;

    public SiteAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
        this.health = new HashMap<>();
        this.addAll();
        refreshHealth();
    }

    public interface OnClickListener {

        void onItemClick(Site item);

        /**
         * 长按站点。只在「选择源」模式（type == 0）下回调，用于重测该源的可用性。
         */
        default boolean onItemLongClick(Site item) {
            return false;
        }
    }

    public void setType(int type) {
        this.type = type;
        notifyDataSetChanged();
    }

    /**
     * 重新读取健康度快照并刷新列表上的状态点。
     */
    public void refreshHealth() {
        health.clear();
        for (SiteHealth item : SiteHealth.findAll()) health.put(item.getKey(), item);
        notifyDataSetChanged();
    }

    public void selectAll() {
        setEnable(type != 3);
    }

    public void cancelAll() {
        setEnable(type == 3);
    }

    private void addAll() {
        for (Site site : VodConfig.get().getSites()) if (!site.isHide()) mItems.add(site);
    }

    public List<Site> getItems() {
        return mItems;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSiteBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Site item = mItems.get(position);
        holder.binding.text.setText(HealthMark.decorate(item.getName(), health.get(item.getKey())));
        holder.binding.check.setChecked(getChecked(item));
        holder.binding.text.setSelected(item.isActivated());
        holder.binding.text.setActivated(item.isActivated());
        holder.binding.check.setVisibility(type == 0 ? View.GONE : View.VISIBLE);
        holder.binding.getRoot().setOnLongClickListener(v -> setLongListener(item));
        holder.binding.getRoot().setOnClickListener(v -> setListener(item, position));
        holder.binding.text.setGravity(Setting.getSiteMode() == 0 ? Gravity.CENTER : Gravity.START);
    }

    private boolean getChecked(Site item) {
        if (type == 1) return item.isSearchable();
        if (type == 2) return item.isChangeable();
        return false;
    }

    private void setListener(Site item, int position) {
        if (type == 0) listener.onItemClick(item);
        if (type == 1) item.setSearchable(!item.isSearchable()).save();
        if (type == 2) item.setChangeable(!item.isChangeable()).save();
        if (type != 0) notifyItemChanged(position);
    }

    private boolean setLongListener(Site item) {
        if (type == 1) {
            setEnable(!item.isSearchable());
            return true;
        }
        if (type == 2) {
            setEnable(!item.isChangeable());
            return true;
        }
        // 选择源模式：长按 = 重测这一个源（红点可能是几天前的旧结果）
        return listener.onItemLongClick(item);
    }

    private void setEnable(boolean enable) {
        if (type == 1) for (Site site : mItems) site.setSearchable(enable).save();
        if (type == 2) for (Site site : mItems) site.setChangeable(enable).save();
        notifyItemRangeChanged(0, getItemCount());
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterSiteBinding binding;

        ViewHolder(@NonNull AdapterSiteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
