package com.visibox.tv.utils;

import android.content.res.ColorStateList;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;

import com.google.android.material.imageview.ShapeableImageView;
import com.visibox.tv.Setting;

public class Glass {

    public static final int LEVEL_OFF = 0;
    public static final int LEVEL_WEAK = 1;
    public static final int LEVEL_STRONG = 2;

    /** 作用在 1/8 缩略图上的盒式模糊半径 */
    private static final int[] RADIUS = {0, 7, 12};
    /** 焦点放大倍率 */
    private static final float[] SCALE = {1.0f, 1.04f, 1.06f};

    public static int getLevel() {
        return Setting.getGlass();
    }

    public static boolean enabled() {
        return getLevel() > LEVEL_OFF;
    }

    public static int radius() {
        return RADIUS[getLevel()];
    }

    public static float scale() {
        return SCALE[getLevel()];
    }

    /** 焦点动效：放大 + 上浮 + 抬升（抬升可让阴影落在邻居之上） */
    public static void focus(@NonNull View view) {
        view.setOnFocusChangeListener((v, has) -> {
            bindStroke(v, has);
            if (!enabled()) return;
            v.animate().cancel();
            v.animate().scaleX(has ? scale() : 1f)
                    .scaleY(has ? scale() : 1f)
                    .translationY(has ? -ResUtil.dp2px(4) : 0)
                    .setDuration(200)
                    .setInterpolator(has ? new OvershootInterpolator(0.8f) : new DecelerateInterpolator())
                    .start();
            v.setZ(has ? 2 : 0);
        });
    }

    /**
     * 焦点描边直接画在 ShapeableImageView 上，让它跟着海报自己的圆角(16dp)走。
     * 原来用 card 的 foreground 叠一个圆角矩形环，环的内圈半径和海报对不齐，
     * 焦点一上去海报的圆角就被环盖成直角。
     */
    private static void bindStroke(@NonNull View view, boolean focused) {
        View image = view.findViewById(com.visibox.tv.R.id.image);
        if (!(image instanceof ShapeableImageView)) return;
        ShapeableImageView poster = (ShapeableImageView) image;
        poster.setStrokeWidth(focused ? ResUtil.dp2px(2) : 0);
        poster.setStrokeColor(ColorStateList.valueOf(focused ? 0xCCFFFFFF : 0x00000000));
    }
}
