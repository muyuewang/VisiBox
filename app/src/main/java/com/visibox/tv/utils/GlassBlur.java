package com.visibox.tv.utils;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.ViewOutlineProvider;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.collection.LruCache;

import java.util.function.Consumer;

/**
 * 液态玻璃的真模糊。
 * <p>
 * 电视盒子多为 Android 6-9 且 GPU 很弱，RenderEffect（API 31）与实时模糊都不可用，
 * 因此走「1/8 降采样 + 两轮盒式模糊 + 内存缓存」：一次性算完，之后只做位图拉伸。
 */
public class GlassBlur {

    private static final int SCALE = 8;
    private static final int CACHE_KB = 6 * 1024;
    private static LruCache<String, Bitmap> cache;

    private static LruCache<String, Bitmap> cache() {
        if (cache == null) {
            cache = new LruCache<String, Bitmap>(CACHE_KB) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };
        }
        return cache;
    }

    public static void clear() {
        if (cache != null) cache.evictAll();
    }

    /** 模糊位图；key 非空时走缓存（key 会带上档位，切档自动失效） */
    @Nullable
    public static Bitmap blur(@Nullable Bitmap src, @Nullable String key) {
        int radius = Glass.radius();
        if (src == null || src.isRecycled() || radius <= 0) return null;
        String ck = key == null ? null : key + "#" + Glass.getLevel();
        if (ck != null) {
            Bitmap hit = cache().get(ck);
            if (hit != null && !hit.isRecycled()) return hit;
        }
        Bitmap out = doBlur(src, radius);
        if (out != null && ck != null) cache().put(ck, out);
        return out;
    }

    public static void into(ImageView view, @Nullable Bitmap src, @Nullable String key) {
        Bitmap out = blur(src, key);
        if (out != null) view.setImageBitmap(out);
    }

    private static Bitmap doBlur(Bitmap src, int radius) {
        int w = Math.max(1, src.getWidth() / SCALE);
        int h = Math.max(1, src.getHeight() / SCALE);
        Bitmap small;
        if (w == src.getWidth() && h == src.getHeight()) {
            small = src.copy(Bitmap.Config.ARGB_8888, true);
        } else {
            small = Bitmap.createScaledBitmap(src, w, h, true);
        }
        if (small == null) return null;
        blurSmall(small, radius);
        // 模糊会把颜色糊成灰，补一点饱和度，否则盖上层亮底罩后就是一片白
        // 1.8 与预览图的 saturate(180%) 对齐
        saturate(small, 1.8f);
        return small;
    }

    /** 提升饱和度：亮度保持，颜色按倍数拉开 */
    private static void saturate(Bitmap bmp, float factor) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int[] pix = new int[w * h];
        bmp.getPixels(pix, 0, w, 0, 0, w, h);
        for (int i = 0; i < pix.length; i++) {
            int c = pix[i];
            int a = (c >>> 24) & 0xFF;
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;
            int gray = (int) (0.299f * r + 0.587f * g + 0.114f * b);
            r = clamp((int) (gray + (r - gray) * factor), 0, 255);
            g = clamp((int) (gray + (g - gray) * factor), 0, 255);
            b = clamp((int) (gray + (b - gray) * factor), 0, 255);
            pix[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        bmp.setPixels(pix, 0, w, 0, 0, w, h);
    }

    /** 对已经是 1/8 尺寸的位图直接做两轮盒式模糊 */
    private static void blurSmall(Bitmap bmp, int radius) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int[] pix = new int[w * h];
        bmp.getPixels(pix, 0, w, 0, 0, w, h);
        for (int i = 0; i < 2; i++) {
            blurH(pix, w, h, radius);
            blurV(pix, w, h, radius);
        }
        bmp.setPixels(pix, 0, w, 0, 0, w, h);
    }

    private static void blurH(int[] pix, int w, int h, int r) {
        int wm = w - 1;
        int div = r * 2 + 1;
        int[] tmp = new int[w];
        for (int y = 0; y < h; y++) {
            int p = y * w;
            int sr = 0, sg = 0, sb = 0, sa = 0;
            for (int i = -r; i <= r; i++) {
                int c = pix[p + clamp(i, 0, wm)];
                sa += (c >> 24) & 0xFF;
                sr += (c >> 16) & 0xFF;
                sg += (c >> 8) & 0xFF;
                sb += c & 0xFF;
            }
            for (int x = 0; x < w; x++) {
                tmp[x] = (sa / div << 24) | (sr / div << 16) | (sg / div << 8) | (sb / div);
                int c1 = pix[p + clamp(x - r, 0, wm)];
                int c2 = pix[p + clamp(x + r + 1, 0, wm)];
                sr += ((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF);
                sg += ((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF);
                sb += (c2 & 0xFF) - (c1 & 0xFF);
                sa += ((c2 >> 24) & 0xFF) - ((c1 >> 24) & 0xFF);
            }
            System.arraycopy(tmp, 0, pix, p, w);
        }
    }

    private static void blurV(int[] pix, int w, int h, int r) {
        int hm = h - 1;
        int div = r * 2 + 1;
        for (int x = 0; x < w; x++) {
            int sr = 0, sg = 0, sb = 0, sa = 0;
            for (int i = -r; i <= r; i++) {
                int c = pix[clamp(i, 0, hm) * w + x];
                sa += (c >> 24) & 0xFF;
                sr += (c >> 16) & 0xFF;
                sg += (c >> 8) & 0xFF;
                sb += c & 0xFF;
            }
            for (int y = 0; y < h; y++) {
                pix[y * w + x] = (sa / div << 24) | (sr / div << 16) | (sg / div << 8) | (sb / div);
                int c1 = pix[clamp(y - r, 0, hm) * w + x];
                int c2 = pix[clamp(y + r + 1, 0, hm) * w + x];
                sr += ((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF);
                sg += ((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF);
                sb += (c2 & 0xFF) - (c1 & 0xFF);
                sa += ((c2 >> 24) & 0xFF) - ((c1 >> 24) & 0xFF);
            }
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.min(Math.max(v, min), max);
    }

    /**
     * 抓一帧窗口截图（1/8 尺寸）。API 26+ 用 PixelCopy，可连同 SurfaceView 里的视频画面；
     * 24/25 退回 decor.draw，丢视频但不丢 UI。
     */
    public static void capture(Activity activity, Consumer<Bitmap> callback) {
        int radius = Glass.radius();
        if (activity == null || radius <= 0) {
            callback.accept(null);
            return;
        }
        Window window = activity.getWindow();
        View decor = window == null ? null : window.getDecorView();
        if (decor == null || decor.getWidth() <= 0 || decor.getHeight() <= 0) {
            callback.accept(null);
            return;
        }
        int w = Math.max(1, decor.getWidth() / SCALE);
        int h = Math.max(1, decor.getHeight() / SCALE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Bitmap shot = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            try {
                PixelCopy.request(window, new Rect(0, 0, decor.getWidth(), decor.getHeight()), shot,
                        code -> post(code == PixelCopy.SUCCESS ? shot : null, radius, callback),
                        new Handler(Looper.getMainLooper()));
                return;
            } catch (Exception e) {
                // 落到下面的软件截图
            }
        }
        Bitmap shot = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(shot);
        canvas.scale(1f / SCALE, 1f / SCALE);
        try {
            decor.draw(canvas);
        } catch (Exception e) {
            shot = null;
        }
        post(shot, radius, callback);
    }

    private static void post(Bitmap shot, int radius, Consumer<Bitmap> callback) {
        if (shot == null) {
            callback.accept(null);
            return;
        }
        blurSmall(shot, radius);
        callback.accept(shot);
    }

    /** 把当前界面模糊后铺到弹窗背后 */
    public static void behind(Activity activity, Dialog dialog) {
        if (activity == null || dialog == null || !Glass.enabled()) return;
        capture(activity, shot -> {
            Window window = dialog.getWindow();
            if (shot == null || window == null) return;
            window.setBackgroundDrawable(new BitmapDrawable(activity.getResources(), shot));
        });
    }

    /** 把当前界面模糊后铺到视图背后（播放器控制条用），玻璃色罩盖在最上层 */
    public static void frost(Activity activity, View view, int tintRes) {
        if (activity == null || view == null || !Glass.enabled()) return;
        capture(activity, shot -> {
            if (shot == null) return;
            Drawable[] layers = {
                    new BitmapDrawable(activity.getResources(), shot),
                    AppCompatResources.getDrawable(activity, tintRes)
            };
            view.setBackground(new LayerDrawable(layers));
        });
    }

    /** 弹窗通用毛玻璃：透明窗口底 + 模糊当前界面 + 玻璃色罩 + 圆角裁剪 */
    public static void glass(Activity activity, Dialog dialog, View root, int tintRes) {
        Window window = dialog == null ? null : dialog.getWindow();
        if (window != null) window.setBackgroundDrawableResource(android.R.color.transparent);
        frost(activity, root, tintRes);
        if (root != null) clip(root, 28);
    }

    /** 给视图裁圆角，避免内容四角露出直角 */
    public static void clip(View view, int radiusDp) {
        if (view == null) return;
        float radius = ResUtil.dp2px(radiusDp);
        view.setClipToOutline(true);
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
    }
}
