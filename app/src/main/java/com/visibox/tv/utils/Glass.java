package com.visibox.tv.utils;

import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;

import com.visibox.tv.Setting;

public class Glass {

    public static final int LEVEL_OFF = 0;
    public static final int LEVEL_WEAK = 1;
    public static final int LEVEL_STRONG = 2;

    /** 作用在 1/8 缩略图上的盒式模糊半径 */
    private static final int[] RADIUS = {0, 4, 8};
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
}
