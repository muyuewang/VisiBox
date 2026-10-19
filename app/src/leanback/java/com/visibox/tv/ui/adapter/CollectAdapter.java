package com.visibox.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.visibox.tv.bean.Collect;
import com.visibox.tv.databinding.AdapterFilterBinding;

import java.util.ArrayList;
import java.util.List;

public class CollectAdapter extends RecyclerView.Adapter<CollectAdapter.ViewHolder> {

    private final List<Collect> mItems;

    private Listener mListener;

    public interface Listener {
        void onItemClick(int position);
    }

    public CollectAdapter() {
        mItems = new ArrayList<>();
    }

    public void setListener(@Nullable Listener listener) {
        this.mListener = listener;
    }

    public void add(Collect item) {
        mItems.add(item);
        notifyItemInserted(mItems.size() - 1);
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public boolean contains(Collect item) {
        return item != null && mItems.contains(item);
    }

    @Nullable
    public Collect get(int position) {
        if (position < 0 || position >= mItems.size()) return null;
        return mItems.get(position);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterFilterBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Collect item = get(holder.getBindingAdapterPosition());
        if (item == null) return;
        holder.binding.text.setText(item.getSite().getName());
        holder.binding.text.setActivated(item.isActivated());
        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && mListener != null) mListener.onItemClick(pos);
        });
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterFilterBinding binding;

        ViewHolder(@NonNull AdapterFilterBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
