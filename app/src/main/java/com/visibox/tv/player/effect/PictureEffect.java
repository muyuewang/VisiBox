package com.visibox.tv.player.effect;

import androidx.media3.common.Effect;
import androidx.media3.common.Player;
import androidx.media3.effect.Brightness;
import androidx.media3.effect.Contrast;
import androidx.media3.effect.HslAdjustment;
import androidx.media3.effect.RgbAdjustment;
import androidx.media3.exoplayer.ExoPlayer;

import com.visibox.tv.Setting;
import com.visibox.tv.player.engine.MpvPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * 画面调节：亮度 / 对比度 / 饱和度 / 色温 / 色调。
 * 走 media3 官方 GlEffect 管线，仅在用户改过参数时才挂载，避免白吃 GPU。
 */
public final class PictureEffect {

    private PictureEffect() {
    }

    /** 把当前设置应用到播放器；播放中修改也会即时生效 */
    public static void apply(Player player) {
        if (player instanceof MpvPlayer mpv) {
            mpv.setPicture(Setting.getPictureBrightness(), Setting.getPictureContrast(), Setting.getPictureSaturation(), Setting.getPictureHue());
            return;
        }
        if (!(player instanceof ExoPlayer exo)) return;
        try {
            exo.setVideoEffects(build());
        } catch (Exception ignored) {
            // 部分设备 / 隧道模式下不支持视频特效，静默降级为原始画面
        }
    }

    public static List<Effect> build() {
        List<Effect> effects = new ArrayList<>();
        if (Setting.isPictureDefault()) return effects;
        float temperature = Setting.getPictureTemperature();
        if (temperature != 0f) {
            // 暖：抬红压蓝；冷：压红抬蓝
            effects.add(new RgbAdjustment.Builder()
                    .setRedScale(1f + temperature * 0.18f)
                    .setGreenScale(1f + Math.abs(temperature) * 0.03f)
                    .setBlueScale(1f - temperature * 0.18f)
                    .build());
        }
        float brightness = Setting.getPictureBrightness();
        if (brightness != 1f) effects.add(new Brightness(brightness - 1f));
        float contrast = Setting.getPictureContrast();
        if (contrast != 1f) effects.add(new Contrast(contrast - 1f));
        float saturation = Setting.getPictureSaturation();
        float hue = Setting.getPictureHue();
        if (saturation != 1f || hue != 0f) {
            HslAdjustment.Builder builder = new HslAdjustment.Builder();
            if (saturation != 1f) builder.adjustSaturation((saturation - 1f) * 100f);
            if (hue != 0f) builder.adjustHue(hue);
            effects.add(builder.build());
        }
        return effects;
    }
}
