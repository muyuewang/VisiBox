package com.visibox.tv.player.effect;

import android.media.audiofx.Equalizer;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import com.visibox.tv.Setting;

/**
 * 音效：基于系统 Equalizer（挂在 ExoPlayer 的 audio session 上）。
 * 好处是零额外解码开销，TV 盒子普遍支持；MPV 内核不接管，交由其自身音频管线。
 */
public final class AudioFx {

    private static final String TAG = "AudioFx";

    /** 5 段预设增益，单位 millibel */
    private static final int[][] PRESETS = {
            {0, 0, 0, 0, 0},          // 标准
            {350, 150, 0, 150, 350},  // 流行
            {450, 200, -100, 200, 450}, // 摇滚
            {250, 150, 0, 150, 250},  // 爵士
            {300, 100, -100, 100, 300}, // 古典
            {650, 350, 0, 0, 0},      // 低音增强
            {-250, 0, 350, 250, 0},   // 人声
    };

    private static Equalizer equalizer;
    private static int currentSession = C.AUDIO_SESSION_ID_UNSET;
    private static Player attached;

    private AudioFx() {
    }

    public static String[] getPresetNames() {
        return new String[]{"标准", "流行", "摇滚", "爵士", "古典", "低音增强", "人声"};
    }

    public static boolean isSupported() {
        return equalizer != null;
    }

    /** 绑定播放器，音频会话就绪后自动挂载 */
    public static void attach(Player player) {
        if (!(player instanceof ExoPlayer exo)) return;
        if (attached == player) return;
        attached = player;
        player.addListener(new Player.Listener() {
            @Override
            public void onAudioSessionIdChanged(int audioSessionId) {
                open(audioSessionId);
            }
        });
        open(exo.getAudioSessionId());
    }

    public static void detach() {
        released();
        attached = null;
    }

    private static synchronized void open(int sessionId) {
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId == 0) return;
        if (equalizer != null && currentSession == sessionId) {
            apply();
            return;
        }
        released();
        try {
            equalizer = new Equalizer(0, sessionId);
            currentSession = sessionId;
            equalizer.setEnabled(true);
            apply();
        } catch (Exception e) {
            Log.w(TAG, "equalizer unavailable: " + e.getMessage());
            released();
        }
    }

    /** 选择预设：把预设值写进设置并立即生效 */
    public static void setPreset(int index) {
        index = Math.min(Math.max(index, 0), PRESETS.length - 1);
        int[] gains = PRESETS[index];
        for (int i = 0; i < gains.length; i++) Setting.putAudioBand(i, gains[i]);
        Setting.putAudioPreset(index);
        apply();
    }

    /** 手动调某一段，自动切换为自定义 */
    public static void setBand(int index, int millibel) {
        Setting.putAudioBand(index, millibel);
        Setting.putAudioPreset(-1);
        apply();
    }

    public static void reset() {
        setPreset(0);
    }

    /** 把设置里的增益刷到均衡器上 */
    public static synchronized void apply() {
        if (equalizer == null) return;
        try {
            short bands = equalizer.getNumberOfBands();
            short[] range = equalizer.getBandLevelRange();
            for (int i = 0; i < bands; i++) {
                int gain = bandGain(i, bands);
                short level = (short) Math.min(Math.max(gain, range[0]), range[1]);
                equalizer.setBandLevel((short) i, level);
            }
            equalizer.setEnabled(true);
        } catch (Exception e) {
            Log.w(TAG, "apply failed: " + e.getMessage());
        }
    }

    /** 设备段数与我们的 5 段不一致时做线性映射 */
    private static int bandGain(int band, int bands) {
        if (bands <= 0) return 0;
        if (bands == 5) return Setting.getAudioBand(band);
        float position = bands == 1 ? 0f : band * 4f / (bands - 1);
        int left = (int) Math.floor(position);
        int right = Math.min(left + 1, 4);
        float fraction = position - left;
        return Math.round(Setting.getAudioBand(left) * (1 - fraction) + Setting.getAudioBand(right) * fraction);
    }

    private static synchronized void released() {
        if (equalizer == null) return;
        try {
            equalizer.setEnabled(false);
            equalizer.release();
        } catch (Exception ignored) {
        }
        equalizer = null;
        currentSession = C.AUDIO_SESSION_ID_UNSET;
    }

    @NonNull
    public static String describe() {
        int preset = Setting.getAudioPreset();
        if (preset >= 0 && preset < PRESETS.length) return getPresetNames()[preset];
        return "自定义";
    }
}
