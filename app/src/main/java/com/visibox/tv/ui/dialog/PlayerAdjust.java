package com.visibox.tv.ui.dialog;

import android.graphics.Color;

import androidx.fragment.app.FragmentActivity;

import com.visibox.tv.R;
import com.visibox.tv.Setting;
import com.visibox.tv.player.effect.AudioFx;
import com.visibox.tv.player.effect.PictureEffect;

/**
 * 播放调节面板集合：画面 / 音效 / 字幕 / 弹幕。
 * 所有改动立即写设置，并通过回调通知当前播放器刷新。
 */
public final class PlayerAdjust {

    private static final int[] SUBTITLE_COLORS = {
            Color.WHITE, Color.YELLOW, 0xFF7CFF7C, 0xFF6EE7F0, 0xFF7CA8FF, 0xFFFF9AD5, 0xFFFFB347, 0xFF000000
    };
    private static final int[] EDGE_COLORS = {
            Color.BLACK, Color.WHITE, 0xFF7CFF7C, 0xFF6EE7F0, Color.YELLOW, 0xFFFF9AD5, 0xFFFFB347
    };

    private PlayerAdjust() {
    }

    /** 画面调节；player 可为 null（仅在设置页预调） */
    public static void picture(FragmentActivity activity, Runnable onChanged) {
        AdjustDialog dialog = AdjustDialog.create(activity, activity.getString(R.string.adjust_picture));
        dialog.slider(activity.getString(R.string.picture_brightness), 0.5f, 1.5f, 0.05f, Setting.getPictureBrightness(), "", value -> {
            Setting.putPictureBrightness(value);
            onChanged.run();
        });
        dialog.slider(activity.getString(R.string.picture_contrast), 0.5f, 1.5f, 0.05f, Setting.getPictureContrast(), "", value -> {
            Setting.putPictureContrast(value);
            onChanged.run();
        });
        dialog.slider(activity.getString(R.string.picture_saturation), 0f, 2f, 0.05f, Setting.getPictureSaturation(), "", value -> {
            Setting.putPictureSaturation(value);
            onChanged.run();
        });
        dialog.slider(activity.getString(R.string.picture_temperature), -1f, 1f, 0.05f, Setting.getPictureTemperature(), "", value -> {
            Setting.putPictureTemperature(value);
            onChanged.run();
        });
        dialog.slider(activity.getString(R.string.picture_hue), -30f, 30f, 1f, Setting.getPictureHue(), "°", value -> {
            Setting.putPictureHue(value);
            onChanged.run();
        });
        dialog.action(activity.getString(R.string.adjust_reset), () -> {
            Setting.resetPicture();
            onChanged.run();
            dialog.dismiss();
        });
        dialog.show();
    }

    /** 音效（均衡器） */
    public static void audio(FragmentActivity activity, Runnable onChanged) {
        AdjustDialog dialog = AdjustDialog.create(activity, activity.getString(R.string.adjust_audio));
        String[] presets = activity.getResources().getStringArray(R.array.audio_presets);
        dialog.choice(activity.getString(R.string.audio_preset), presets, Math.max(Setting.getAudioPreset(), 0), index -> {
            AudioFx.setPreset(index);
            onChanged.run();
        });
        String[] bands = activity.getResources().getStringArray(R.array.audio_bands);
        for (int i = 0; i < 5 && i < bands.length; i++) {
            final int index = i;
            int gain = Setting.getAudioBand(index);
            dialog.slider(bands[index], -1200f, 1200f, 100f, gain, "", value -> {
                AudioFx.setBand(index, Math.round(value));
                onChanged.run();
            });
        }
        dialog.action(activity.getString(R.string.adjust_reset), () -> {
            AudioFx.reset();
            onChanged.run();
            dialog.dismiss();
        });
        dialog.show();
    }

    /** 字幕样式；onChanged 负责把新样式刷到 PlayerView */
    public static void subtitle(FragmentActivity activity, Runnable onChanged) {
        AdjustDialog dialog = AdjustDialog.create(activity, activity.getString(R.string.adjust_subtitle));
        String[] colors = activity.getResources().getStringArray(R.array.subtitle_colors);
        dialog.choice(activity.getString(R.string.subtitle_color), colors, colorIndex(Setting.getSubtitleColor()), index -> {
            Setting.putSubtitleColor(SUBTITLE_COLORS[index]);
            onChanged.run();
        });
        String[] edges = activity.getResources().getStringArray(R.array.subtitle_edges);
        dialog.choice(activity.getString(R.string.subtitle_edge), edges, Setting.getSubtitleEdge(), index -> {
            Setting.putSubtitleEdge(index);
            onChanged.run();
        });
        String[] edgeColors = activity.getResources().getStringArray(R.array.edge_colors);
        dialog.choice(activity.getString(R.string.subtitle_edge_color), edgeColors, Math.max(indexOf(EDGE_COLORS, Setting.getSubtitleEdgeColor()), 0), index -> {
            Setting.putSubtitleEdgeColor(EDGE_COLORS[index]);
            onChanged.run();
        });
        String[] backgrounds = activity.getResources().getStringArray(R.array.subtitle_backgrounds);
        dialog.choice(activity.getString(R.string.subtitle_background), backgrounds, Math.min(Setting.getSubtitleBackground(), 1), index -> {
            Setting.putSubtitleBackground(index);
            onChanged.run();
        });
        dialog.slider(activity.getString(R.string.subtitle_size), 0.6f, 2f, 0.05f, Setting.getSubtitleTextSize() <= 0 ? 1f : Setting.getSubtitleTextSize(), "", value -> {
            Setting.putSubtitleTextSize(value);
            onChanged.run();
        });
        dialog.slider(activity.getString(R.string.subtitle_bottom), 0f, 40f, 1f, Setting.getSubtitleBottom(), "%", value -> {
            Setting.putSubtitleBottom(value);
            onChanged.run();
        });
        dialog.action(activity.getString(R.string.adjust_reset), () -> {
            Setting.resetSubtitle();
            onChanged.run();
            dialog.dismiss();
        });
        dialog.show();
    }

    /** 弹幕样式 */
    public static void danmaku(FragmentActivity activity, Runnable onChanged) {
        AdjustDialog dialog = AdjustDialog.create(activity, activity.getString(R.string.adjust_danmaku));
        dialog.slider(activity.getString(R.string.danmaku_size), 0.5f, 2f, 0.05f, Setting.getDanmakuSize(), "", value -> {
            Setting.putDanmakuSize(value);
            onChanged.run();
        });
        dialog.slider(activity.getString(R.string.danmaku_speed), 0.5f, 2f, 0.05f, Setting.getDanmakuSpeed(), "", value -> {
            Setting.putDanmakuSpeed(value);
            onChanged.run();
        });
        dialog.slider(activity.getString(R.string.danmaku_alpha), 0.1f, 1f, 0.05f, Setting.getDanmakuAlpha(), "", value -> {
            Setting.putDanmakuAlpha(value);
            onChanged.run();
        });
        dialog.slider(activity.getString(R.string.danmaku_lines), 1f, 8f, 1f, Setting.getDanmakuLines(), "", value -> {
            Setting.putDanmakuLines(Math.round(value));
            onChanged.run();
        });
        String[] styles = activity.getResources().getStringArray(R.array.danmaku_styles);
        dialog.choice(activity.getString(R.string.danmaku_style), styles, Setting.getDanmakuStyle(), index -> {
            Setting.putDanmakuStyle(index);
            onChanged.run();
        });
        dialog.slider(activity.getString(R.string.danmaku_offset), -5000f, 5000f, 100f, Setting.getDanmakuOffset(), "ms", value -> {
            Setting.putDanmakuOffset(Math.round(value));
            onChanged.run();
        });
        dialog.action(activity.getString(R.string.adjust_reset), () -> {
            Setting.resetDanmaku();
            onChanged.run();
            dialog.dismiss();
        });
        dialog.show();
    }

    /** 便于设置页展示当前状态 */
    public static String pictureSummary(FragmentActivity activity) {
        return Setting.isPictureDefault() ? activity.getString(R.string.adjust_default) : activity.getString(R.string.adjust_custom);
    }

    public static String audioSummary(FragmentActivity activity) {
        return Setting.isAudioDefault() ? activity.getString(R.string.adjust_default) : AudioFx.describe();
    }

    public static String subtitleSummary(FragmentActivity activity) {
        return Setting.isSubtitleDefault() ? activity.getString(R.string.adjust_default) : activity.getString(R.string.adjust_custom);
    }

    public static String danmakuSummary(FragmentActivity activity) {
        return Setting.isDanmakuDefault() ? activity.getString(R.string.adjust_default) : activity.getString(R.string.adjust_custom);
    }

    /** 画面改动后刷新播放器 */
    public static void applyPicture(androidx.media3.common.Player player) {
        PictureEffect.apply(player);
    }

    private static int colorIndex(int color) {
        int index = indexOf(SUBTITLE_COLORS, color);
        return Math.max(index, 0);
    }

    private static int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) if (values[i] == value) return i;
        return -1;
    }
}
