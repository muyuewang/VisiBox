package com.visibox.tv.utils;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 默认背景壁纸：浅色渐变底 + 四团柔化彩色光斑。
 * <p>
 * 与 dist/glass-preview.html 的 .tv / .blob 是同一套参数：
 * 底色 150deg #EFF2F8 → #DFE5F0，光斑 #2DD4AA / #6AA8FF / #A98BFF / #FFC46B，
 * 不透明度 55%，边缘柔化（预览图用 filter:blur(70px)，这里用径向渐变停靠点等效实现，
 * 免去实时模糊，低端盒子也能满帧绘制）。
 * <p>
 * 有这层颜色，上层的玻璃面板才有东西可"折射"，否则整页会糊成一片白。
 */
public class GlassWallpaper extends Drawable {

    private static final int COLOR_TOP = 0xFFEFF2F8;
    private static final int COLOR_BOTTOM = 0xFFDFE5F0;

    /** 光斑基色（预览图 .b1 ~ .b4） */
    private static final int[] BLOB_COLOR = {
            0xFF2DD4AA, 0xFF6AA8FF, 0xFFA98BFF, 0xFFFFC46B
    };

    /**
     * 光斑几何，全部为相对底图的比例：
     * {圆心X, 圆心Y, 宽, 高}
     * 由预览图的 left/top/right/bottom 百分比换算得出。
     */
    private static final float[][] BLOB_GEO = {
            {0.14f, 0.19f, 0.44f, 0.70f},
            {0.86f, 0.37f, 0.40f, 0.62f},
            {0.44f, 0.94f, 0.36f, 0.56f},
            {0.67f, 0.94f, 0.30f, 0.48f},
    };

    /** 预览图 .blob 的 opacity */
    private static final float BLOB_ALPHA = 0.55f;
    /** 柔化范围：0 ~ 0.72 保持实心，0.72 ~ 1 渐隐到底（等效 blur(70px) 的边缘） */
    private static final float[] BLOB_STOPS = {0f, 0.72f, 1f};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private LinearGradient base;
    private final Rect drawn = new Rect();

    public GlassWallpaper() {
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        drawn.set(bounds);
        base = null;
        invalidateSelf();
    }

    private void ensureBase() {
        if (base != null || drawn.isEmpty()) return;
        // 150deg：略偏垂直的左上交到右下渐变
        base = new LinearGradient(
                drawn.left, drawn.top,
                drawn.left + drawn.width() * 0.86f, drawn.bottom,
                COLOR_TOP, COLOR_BOTTOM, Shader.TileMode.CLAMP);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (drawn.isEmpty()) return;
        ensureBase();

        paint.setShader(base);
        paint.setAlpha(255);
        canvas.drawRect(drawn, paint);

        float w = drawn.width();
        float h = drawn.height();
        int alpha = Math.round(BLOB_ALPHA * 255);

        for (int i = 0; i < BLOB_GEO.length; i++) {
            float[] geo = BLOB_GEO[i];
            float rx = geo[2] * w * 0.5f;
            float ry = geo[3] * h * 0.5f;
            if (rx <= 0f || ry <= 0f) continue;
            float cx = drawn.left + geo[0] * w;
            float cy = drawn.top + geo[1] * h;

            int color = BLOB_COLOR[i];
            int solid = (color & 0x00FFFFFF) | (alpha << 24);
            int clear = color & 0x00FFFFFF;

            RadialGradient blob = new RadialGradient(
                    cx, cy, rx,
                    new int[]{solid, solid, clear},
                    BLOB_STOPS,
                    Shader.TileMode.CLAMP);

            int save = canvas.save();
            // 先画半径 rx 的正圆，再纵向拉伸 ry/rx 得到椭圆：横半径 rx、纵半径 ry
            canvas.scale(1f, ry / rx, cx, cy);
            paint.setShader(blob);
            paint.setAlpha(255);
            canvas.drawCircle(cx, cy, rx, paint);
            canvas.restoreToCount(save);
        }

        paint.setShader(null);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
