package com.visibox.tv.ui.dialog;

import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.visibox.tv.R;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * 通用「参数调节」面板：一行一项，滑杆可直接用遥控器左右键调节。
 * TV / 手机两套 UI 共用，避免为每个设置项单独写一套布局。
 */
public final class AdjustDialog {

    private final FragmentActivity activity;
    private final LinearLayout container;
    private final AlertDialog dialog;

    private AdjustDialog(FragmentActivity activity, String title) {
        this.activity = activity;
        this.container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(28);
        container.setPadding(padding, dp(24), padding, dp(20));
        container.setBackgroundResource(R.drawable.shape_glass_strong);

        TextView header = new TextView(activity);
        header.setText(title);
        header.setTextColor(color(R.color.text_glass));
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), 0, dp(8), dp(12));
        container.addView(header, params(0));

        this.dialog = new MaterialAlertDialogBuilder(activity).setView(container).create();
    }

    public static AdjustDialog create(FragmentActivity activity, String title) {
        return new AdjustDialog(activity, title);
    }

    /** 加一行滑杆，unit 为空表示不带单位 */
    public AdjustDialog slider(String label, float min, float max, float step, float value, String unit, Consumer<Float> onChange) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(8), dp(8), dp(8), dp(4));

        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView valueText = new TextView(activity);
        valueText.setTextColor(color(R.color.accent_glass));
        valueText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        valueText.setGravity(Gravity.END);
        valueText.setText(format(value, unit));
        head.addView(label(label), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(valueText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(head, params(0));

        Slider slider = new Slider(activity, null, com.google.android.material.R.attr.sliderStyle);
        slider.setValueFrom(min);
        slider.setValueTo(max);
        slider.setStepSize(step);
        slider.setValue(Math.min(Math.max(value, min), max));
        slider.setTickVisible(false);
        slider.addOnChangeListener((s, v, fromUser) -> {
            valueText.setText(format(v, unit));
            if (fromUser) onChange.accept(v);
        });
        row.addView(slider, params(0));

        container.addView(row, params(0));
        return this;
    }

    /** 加一行「点击循环切换」的选项 */
    public AdjustDialog choice(String label, String[] values, int index, Consumer<Integer> onChange) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackground(rowBackground());
        row.setFocusable(true);
        row.setClickable(true);

        TextView valueText = new TextView(activity);
        valueText.setTextColor(color(R.color.accent_glass));
        valueText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        valueText.setGravity(Gravity.END);
        final int[] cursor = {Math.min(Math.max(index, 0), values.length - 1)};
        valueText.setText(values[cursor[0]]);
        row.addView(label(label), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(valueText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Runnable apply = () -> {
            cursor[0] = (cursor[0] + 1) % values.length;
            valueText.setText(values[cursor[0]]);
            onChange.accept(cursor[0]);
        };
        row.setOnClickListener(v -> apply.run());
        row.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                apply.run();
                return true;
            }
            return false;
        });

        container.addView(row, params(6));
        return this;
    }

    /** 加一行普通操作按钮（例如「重置」） */
    public AdjustDialog action(String label, Runnable action) {
        TextView row = new TextView(activity);
        row.setText(label);
        row.setTextColor(color(R.color.text_glass));
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackground(rowBackground());
        row.setFocusable(true);
        row.setClickable(true);
        row.setOnClickListener(v -> action.run());
        container.addView(row, params(6));
        return this;
    }

    public void show() {
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
    }

    public void dismiss() {
        dialog.dismiss();
    }

    // ---------------- 内部工具 ----------------

    private TextView label(String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(color(R.color.text_glass));
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    private StateListDrawable rowBackground() {
        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(new int[]{android.R.attr.state_focused}, ContextCompat.getDrawable(activity, R.drawable.shape_glass_selected));
        drawable.addState(new int[]{}, ContextCompat.getDrawable(activity, R.drawable.shape_glass_soft));
        return drawable;
    }

    private String format(float value, String unit) {
        String text = Math.abs(value - Math.round(value)) < 0.001f ? String.valueOf(Math.round(value)) : String.format(Locale.US, "%.2f", value);
        if ("%".equals(unit)) return text + "%";
        return text + (TextUtils.isEmpty(unit) ? "" : unit);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private int color(int res) {
        return ContextCompat.getColor(activity, res);
    }

    private LinearLayout.LayoutParams params(int marginTopDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(marginTopDp);
        return params;
    }
}
