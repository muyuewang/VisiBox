package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.view.LayoutInflater;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.databinding.DialogContentBinding;
import com.fongmi.android.tv.ui.custom.CustomMovement;
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
        initView(binding.text, content);
        dialog.show();
    }

    private void initView(TextView view, CharSequence content) {
        view.setText(content, TextView.BufferType.SPANNABLE);
        view.setLinkTextColor(MDColor.BLUE_500);
        CustomMovement.bind(view);
    }
}
