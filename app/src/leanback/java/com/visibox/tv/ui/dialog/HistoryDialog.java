package com.visibox.tv.ui.dialog;

import android.app.Activity;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;

import com.visibox.tv.R;
import com.visibox.tv.bean.Config;
import com.visibox.tv.databinding.DialogHistoryBinding;
import com.visibox.tv.impl.ConfigCallback;
import com.visibox.tv.ui.adapter.ConfigAdapter;
import com.visibox.tv.ui.custom.SpaceItemDecoration;
import com.visibox.tv.utils.GlassBlur;
import com.visibox.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class HistoryDialog implements ConfigAdapter.OnClickListener {

    private final DialogHistoryBinding binding;
    private final ConfigCallback callback;
    private final AlertDialog dialog;
    private ConfigAdapter adapter;
    private boolean readOnly;
    private int type;

    public static HistoryDialog create(Activity activity) {
        return new HistoryDialog(activity);
    }

    public HistoryDialog type(int type) {
        this.type = type;
        return this;
    }

    public HistoryDialog(Activity activity) {
        this.callback = (ConfigCallback) activity;
        this.binding = DialogHistoryBinding.inflate(LayoutInflater.from(activity));
        this.dialog = new MaterialAlertDialogBuilder(activity).setView(binding.getRoot()).create();
    }

    public HistoryDialog readOnly() {
        this.readOnly = true;
        return this;
    }

    public void show() {
        setRecyclerView();
        setDialog();
    }

    private void setRecyclerView() {
        adapter = new ConfigAdapter(this);
        setTitle();
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(false);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        binding.recycler.setAdapter(adapter.readOnly(readOnly).addAll(type));
    }

    private void setTitle() {
        int resId;
        if (type == 1) resId = R.string.dialog_live_line_title;
        else if (type == 2) resId = R.string.dialog_history_title;
        else resId = R.string.dialog_line_title;
        binding.title.setText(resId);
    }

    private void setDialog() {
        if (adapter.getItemCount() == 0) return;
        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.width = (int) (ResUtil.getScreenWidth() * 0.4f);
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        dialog.getWindow().setAttributes(params);
        dialog.getWindow().setDimAmount(0);
        // 真毛玻璃 + 圆角裁剪
        Activity activity = (Activity) binding.getRoot().getContext();
        GlassBlur.glass(activity, dialog, binding.getRoot(), R.drawable.bg_glass_dialog);
        dialog.show();
    }

    @Override
    public void onTextClick(Config item) {
        callback.setConfig(item);
        dialog.dismiss();
    }

    @Override
    public void onDeleteClick(Config item) {
        if (adapter.remove(item) == 0) dialog.dismiss();
    }
}