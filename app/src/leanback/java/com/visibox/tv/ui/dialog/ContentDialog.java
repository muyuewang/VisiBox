package com.visibox.tv.ui.dialog;

import android.app.Activity;
import android.view.LayoutInflater;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.visibox.tv.R;
import com.visibox.tv.databinding.DialogContentBinding;
import com.visibox.tv.ui.custom.CustomMovement;
import com.visibox.tv.utils.GlassBlur;
import com.github.bassaer.library.MDColor;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ContentDialog {

    public static void show(Activity activity, CharSequence content) {
        new ContentDialog().create(activity, content);
    }

    public void create(Activity activity, CharSequence content) {
        DialogContentBinding binding = DialogContentBinding.inflate(LayoutInflater.from(activity));
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity).setView(binding.getRoot()).create();
        dialog.getWindow().setDimAmount(0);
        GlassBlur.glass(activity, dialog, binding.getRoot(), R.drawable.bg_glass_dialog);
        initView(binding.text, content);
        dialog.show();
    }

    private void initView(TextView view, CharSequence content) {
        view.setText(content, TextView.BufferType.SPANNABLE);
        view.setLinkTextColor(MDColor.BLUE_500);
        CustomMovement.bind(view);
    }
}
