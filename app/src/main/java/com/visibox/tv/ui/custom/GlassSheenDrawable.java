package com.visibox.tv.ui.custom;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.visibox.tv.utils.Glass;
import com.visibox.tv.utils.ResUtil;

/**
 * 液态玻璃的「高光扫过」：聚焦时一条斜向高光带掠过表面。
 * 同时接管焦点动效（放大 + 上浮 + 抬升）。
 */
public class GlassSheenDrawable extends Drawable implements View.OnFocusChangeListener {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private final Matrix matrix = new Matrix();
    private final float radius;

    private ValueAnimator animator;
    private float progress = -1;

    public GlassSheenDrawable(int radiusDp) {
        this.radius = ResUtil.dp2px(radiusDp);
    }

    public void attach(@NonNull View view) {
        view.setForeground(this);
        view.setOnFocusChangeListener(this);
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        v.animate().cancel();
        if (Glass.enabled()) {
            v.animate().scaleX(hasFocus ? Glass.scale() : 1f)
                    .scaleY(hasFocus ? Glass.scale() : 1f)
                    .translationY(hasFocus ? -ResUtil.dp2px(4) : 0)
                    .setDuration(200)
                    .setInterpolator(hasFocus ? new OvershootInterpolator(0.8f) : new DecelerateInterpolator())
                    .start();
            v.setZ(hasFocus ? 2 : 0);
        }
        if (hasFocus && Glass.getLevel() == Glass.LEVEL_STRONG) start();
        else stop();
    }

    private void start() {
        stop();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(700);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidateSelf();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                progress = -1;
                invalidateSelf();
            }
        });
        animator.start();
    }

    private void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        progress = -1;
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (progress < 0 || !Glass.enabled()) return;
        Rect b = getBounds();
        if (b.isEmpty()) return;
        rect.set(b);
        clip.reset();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
        float w = b.width();
        float h = b.height();
        float band = w * 0.45f;
        float x = -band + (w + band) * progress;
        LinearGradient shader = new LinearGradient(0, 0, band, 0,
                new int[]{0x00FFFFFF, 0x4DFFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        matrix.reset();
        matrix.postRotate(18f);
        matrix.postTranslate(x, -h * 0.25f);
        shader.setLocalMatrix(matrix);
        paint.setShader(shader);
        canvas.save();
        canvas.clipPath(clip);
        canvas.drawRect(b.left - w, b.top - h, b.right + w, b.bottom + h, paint);
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
