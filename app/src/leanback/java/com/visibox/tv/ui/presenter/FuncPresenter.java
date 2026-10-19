package com.visibox.tv.ui.presenter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.visibox.tv.bean.Func;
import com.visibox.tv.databinding.AdapterFuncBinding;
import com.visibox.tv.ui.custom.GlassSheenDrawable;

public class FuncPresenter extends Presenter {

    private final OnClickListener listener;

    public FuncPresenter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {
        void onItemClick(Func item);
    }

    @NonNull
    @Override
    public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        ViewHolder holder = new ViewHolder(AdapterFuncBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        new GlassSheenDrawable(16).attach(holder.view);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        Func item = (Func) object;
        ViewHolder holder = (ViewHolder) viewHolder;
        holder.binding.text.setText(item.getText());
        holder.binding.icon.setImageResource(item.getDrawable());
        // ic_home_* 是纯白 PNG（旧深色主题遗留），浅色玻璃底上等于隐形，这里统一染成次级文字色
        holder.binding.icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(holder.view.getContext(), com.visibox.tv.R.color.text_glass_sub)));
        setOnClickListener(holder, view -> listener.onItemClick(item));
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
    }

    public static class ViewHolder extends Presenter.ViewHolder {

        private final AdapterFuncBinding binding;

        public ViewHolder(@NonNull AdapterFuncBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}