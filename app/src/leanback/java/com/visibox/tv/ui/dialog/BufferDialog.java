package com.visibox.tv.ui.dialog;

import android.view.LayoutInflater;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.visibox.tv.Setting;
import com.visibox.tv.R;
import com.visibox.tv.databinding.DialogBufferBinding;
import com.visibox.tv.impl.BufferCallback;
import com.visibox.tv.utils.GlassBlur;
import com.visibox.tv.utils.KeyUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class BufferDialog {

    private final DialogBufferBinding binding;
    private final BufferCallback callback;
    private final AlertDialog dialog;
    private final FragmentActivity activity;

    public static BufferDialog create(FragmentActivity activity) {
        return new BufferDialog(activity);
    }

    public BufferDialog(FragmentActivity activity) {
        this.callback = (BufferCallback) activity;
        this.binding = DialogBufferBinding.inflate(LayoutInflater.from(activity));
        this.dialog = new MaterialAlertDialogBuilder(activity).setView(binding.getRoot()).create();
        this.activity = activity;
    }

    public void show() {
        initDialog();
        initView();
        initEvent();
    }

    private void initDialog() {
        dialog.getWindow().setDimAmount(0);
        GlassBlur.glass(activity, dialog, binding.getRoot(), R.drawable.bg_glass_dialog);
        dialog.show();
    }

    private void initView() {
        binding.slider.setValue(Setting.getBuffer());
    }

    private void initEvent() {
        binding.slider.addOnChangeListener((slider, value, fromUser) -> callback.setBuffer((int) value));
        binding.slider.setOnKeyListener((view, keyCode, event) -> {
            boolean enter = KeyUtil.isEnterKey(event);
            if (enter) dialog.dismiss();
            return enter;
        });
    }
}
