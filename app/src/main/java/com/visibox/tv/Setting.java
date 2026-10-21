package com.visibox.tv;


import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.github.catvod.utils.Prefers;

public class Setting {

    public static String getDoh() {
        return Prefers.getString("doh");
    }

    public static void putDoh(String doh) {
        Prefers.put("doh", doh);
    }

    public static String getKeyword() {
        return Prefers.getString("keyword");
    }

    public static void putKeyword(String keyword) {
        Prefers.put("keyword", keyword);
    }

    public static String getHot() {
        return Prefers.getString("hot");
    }

    public static void putHot(String hot) {
        Prefers.put("hot", hot);
    }

    public static String getUa() {
        return Prefers.getString("ua");
    }

    public static void putUa(String ua) {
        Prefers.put("ua", ua);
    }

    public static int getWall() {
        return Prefers.getInt("wall", 1);
    }

    public static void putWall(int wall) {
        Prefers.put("wall", wall);
    }

    public static int getWallType() {
        return Prefers.getInt("wall_type", 0);
    }

    public static void putWallType(int type) {
        Prefers.put("wall_type", type);
    }

    public static int getReset() {
        return Prefers.getInt("reset", 0);
    }

    public static void putReset(int reset) {
        Prefers.put("reset", reset);
    }

    public static int getRender() {
        return Prefers.getInt("render", 0);
    }

    public static void putRender(int render) {
        Prefers.put("render", render);
    }

    public static int getSize() {
        return Prefers.getInt("size", 2);
    }

    public static void putSize(int size) {
        Prefers.put("size", size);
    }

    public static int getScale() {
        return Prefers.getInt("scale");
    }

    public static void putScale(int scale) {
        Prefers.put("scale", scale);
    }

    public static int getLiveScale() {
        return Prefers.getInt("scale_live", getScale());
    }

    public static void putLiveScale(int scale) {
        Prefers.put("scale_live", scale);
    }

    public static int getBuffer() {
        return Math.min(Math.max(Prefers.getInt("buffer"), 1), 10);
    }

    public static void putBuffer(int buffer) {
        Prefers.put("buffer", buffer);
    }

    public static int getHistoryNum() {
        int num = Prefers.getInt("history_num", 30);
        int[] values = {10, 20, 30, 50, 100};
        for (int v : values) if (v == num) return num;
        return 30;
    }

    public static void putHistoryNum(int num) {
        Prefers.put("history_num", num);
    }

    public static int getGlass() {
        return Math.min(Math.max(Prefers.getInt("glass_level", 2), 0), 2);
    }

    public static void putGlass(int level) {
        Prefers.put("glass_level", level);
    }

    public static boolean getBoot() {
        return Prefers.getBoolean("boot_start");
    }

    public static void putBoot(boolean boot) {
        Prefers.put("boot_start", boot);
    }

    public static int getPlayer() {
        return Prefers.getInt("player");
    }

    public static void putPlayer(int player) {
        Prefers.put("player", player);
    }

    public static int getThread() {
        return Math.min(Math.max(Prefers.getInt("thread", 16), 4), 32);
    }

    public static void putThread(int thread) {
        Prefers.put("thread", thread);
    }

    public static int getBackground() {
        return Prefers.getInt("background", 2);
    }

    public static void putBackground(int background) {
        Prefers.put("background", background);
    }

    public static int getSiteMode() {
        return Prefers.getInt("site_mode");
    }

    public static void putSiteMode(int mode) {
        Prefers.put("site_mode", mode);
    }

    public static int getSyncMode() {
        return Prefers.getInt("sync_mode");
    }

    public static void putSyncMode(int mode) {
        Prefers.put("sync_mode", mode);
    }

    public static boolean isIncognito() {
        return Prefers.getBoolean("incognito");
    }

    public static void putIncognito(boolean incognito) {
        Prefers.put("incognito", incognito);
    }

    public static boolean isBootLive() {
        return Prefers.getBoolean("boot_live");
    }

    public static void putBootLive(boolean boot) {
        Prefers.put("boot_live", boot);
    }

    public static boolean isInvert() {
        return Prefers.getBoolean("invert");
    }

    public static void putInvert(boolean invert) {
        Prefers.put("invert", invert);
    }

    public static boolean isAcross() {
        return Prefers.getBoolean("across", true);
    }

    public static void putAcross(boolean across) {
        Prefers.put("across", across);
    }

    public static boolean isChange() {
        return Prefers.getBoolean("change", true);
    }

    public static void putChange(boolean change) {
        Prefers.put("change", change);
    }

    public static boolean getUpdate() {
        return Prefers.getBoolean("update", true);
    }

    public static void putUpdate(boolean update) {
        Prefers.put("update", update);
    }

    public static boolean isCaption() {
        return Prefers.getBoolean("caption");
    }

    public static void putCaption(boolean caption) {
        Prefers.put("caption", caption);
    }

    public static boolean isTunnel() {
        return Prefers.getBoolean("tunnel");
    }

    public static void putTunnel(boolean tunnel) {
        Prefers.put("tunnel", tunnel);
    }

    public static boolean isAudioPrefer() {
        return Prefers.getBoolean("audio_prefer");
    }

    public static void putAudioPrefer(boolean audioPrefer) {
        Prefers.put("audio_prefer", audioPrefer);
    }

    public static boolean isVideoPrefer() {
        return Prefers.getBoolean("video_prefer");
    }

    public static void putVideoPrefer(boolean videoPrefer) {
        Prefers.put("video_prefer", videoPrefer);
    }

    public static boolean isPreferAAC() {
        return Prefers.getBoolean("prefer_aac");
    }

    public static void putPreferAAC(boolean preferAAC) {
        Prefers.put("prefer_aac", preferAAC);
    }

    public static boolean isDanmakuLoad() {
        return Prefers.getBoolean("danmaku_load");
    }

    public static void putDanmakuLoad(boolean danmakuLoad) {
        Prefers.put("danmaku_load", danmakuLoad);
    }

    public static boolean isAdblock() {
        return Prefers.getBoolean("adblock", true);
    }

    public static void putAdblock(boolean adblock) {
        Prefers.put("adblock", adblock);
    }

    public static boolean isDanmakuShow() {
        return Prefers.getBoolean("danmaku_show");
    }

    public static void putDanmakuShow(boolean danmakuShow) {
        Prefers.put("danmaku_show", danmakuShow);
    }

    public static boolean isZhuyin() {
        return Prefers.getBoolean("zhuyin");
    }

    public static void putZhuyin(boolean zhuyin) {
        Prefers.put("zhuyin", zhuyin);
    }

    public static float getSpeed() {
        return Math.min(Math.max(Prefers.getFloat("speed", 3), 2), 5);
    }

    public static void putSpeed(float speed) {
        Prefers.put("speed", speed);
    }

    /** 跳过静音片段（对白空档自动加速） */
    public static boolean isSkipSilence() {
        return Prefers.getBoolean("skip_silence");
    }

    public static void putSkipSilence(boolean skip) {
        Prefers.put("skip_silence", skip);
    }

    public static float getSubtitleTextSize() {
        return Prefers.getFloat("subtitle_text_size");
    }

    public static void putSubtitleTextSize(float value) {
        Prefers.put("subtitle_text_size", value);
    }

    public static float getSubtitlePosition() {
        return Prefers.getFloat("subtitle_position");
    }

    public static void putSubtitlePosition(float value) {
        Prefers.put("subtitle_position", value);
    }

    // ==================== 画面调节 ====================
    // 统一用 0.5 ~ 1.5 的倍率表达，1.0 为原始画面

    public static float getPictureBrightness() {
        return clamp(Prefers.getFloat("picture_brightness", 1f), 0.5f, 1.5f);
    }

    public static void putPictureBrightness(float value) {
        Prefers.put("picture_brightness", clamp(value, 0.5f, 1.5f));
    }

    public static float getPictureContrast() {
        return clamp(Prefers.getFloat("picture_contrast", 1f), 0.5f, 1.5f);
    }

    public static void putPictureContrast(float value) {
        Prefers.put("picture_contrast", clamp(value, 0.5f, 1.5f));
    }

    public static float getPictureSaturation() {
        return clamp(Prefers.getFloat("picture_saturation", 1f), 0f, 2f);
    }

    public static void putPictureSaturation(float value) {
        Prefers.put("picture_saturation", clamp(value, 0f, 2f));
    }

    /** 色温：-1 冷（偏蓝） ~ 1 暖（偏红） */
    public static float getPictureTemperature() {
        return clamp(Prefers.getFloat("picture_temperature", 0f), -1f, 1f);
    }

    public static void putPictureTemperature(float value) {
        Prefers.put("picture_temperature", clamp(value, -1f, 1f));
    }

    /** 色调：-30 ~ 30 度 */
    public static float getPictureHue() {
        return clamp(Prefers.getFloat("picture_hue", 0f), -30f, 30f);
    }

    public static void putPictureHue(float value) {
        Prefers.put("picture_hue", clamp(value, -30f, 30f));
    }

    public static boolean isPictureDefault() {
        return getPictureBrightness() == 1f && getPictureContrast() == 1f && getPictureSaturation() == 1f && getPictureTemperature() == 0f && getPictureHue() == 0f;
    }

    public static void resetPicture() {
        putPictureBrightness(1f);
        putPictureContrast(1f);
        putPictureSaturation(1f);
        putPictureTemperature(0f);
        putPictureHue(0f);
    }

    // ==================== 音效（均衡器） ====================

    /** 预设索引：-1 自定义，0 标准，1 ~ 6 预设音效 */
    public static int getAudioPreset() {
        return Math.min(Math.max(Prefers.getInt("audio_preset", 0), -1), 6);
    }

    public static void putAudioPreset(int preset) {
        Prefers.put("audio_preset", preset);
    }

    /** 单段增益，单位 millibel（1/100 dB），-1500 ~ 1500 */
    public static int getAudioBand(int index) {
        return (int) clamp(Prefers.getInt("audio_band_" + index, 0), -1500, 1500);
    }

    public static void putAudioBand(int index, int value) {
        Prefers.put("audio_band_" + index, (int) clamp(value, -1500, 1500));
    }

    public static boolean isAudioDefault() {
        if (getAudioPreset() != 0) return false;
        for (int i = 0; i < 5; i++) if (getAudioBand(i) != 0) return false;
        return true;
    }

    // ==================== 字幕样式 ====================

    public static int getSubtitleColor() {
        return Prefers.getInt("subtitle_color", android.graphics.Color.WHITE);
    }

    public static void putSubtitleColor(int color) {
        Prefers.put("subtitle_color", color);
    }

    /** 0 无描边 / 1 描边 / 2 阴影 / 3 浮雕 / 4 凹陷 */
    public static int getSubtitleEdge() {
        return Math.min(Math.max(Prefers.getInt("subtitle_edge", 1), 0), 4);
    }

    public static void putSubtitleEdge(int type) {
        Prefers.put("subtitle_edge", type);
    }

    public static int getSubtitleEdgeColor() {
        return Prefers.getInt("subtitle_edge_color", android.graphics.Color.BLACK);
    }

    public static void putSubtitleEdgeColor(int color) {
        Prefers.put("subtitle_edge_color", color);
    }

    /** 0 透明底 / 1 半透明黑底 */
    public static int getSubtitleBackground() {
        return Prefers.getInt("subtitle_background", 0);
    }

    public static void putSubtitleBackground(int type) {
        Prefers.put("subtitle_background", type);
    }

    /** 字幕距底部的比例，0 ~ 40（%） */
    public static float getSubtitleBottom() {
        return clamp(Prefers.getFloat("subtitle_bottom", 8f), 0f, 40f);
    }

    public static void putSubtitleBottom(float percent) {
        Prefers.put("subtitle_bottom", clamp(percent, 0f, 40f));
    }

    public static boolean isSubtitleDefault() {
        return getSubtitleColor() == android.graphics.Color.WHITE && getSubtitleEdge() == 1 && getSubtitleEdgeColor() == android.graphics.Color.BLACK && getSubtitleBackground() == 0 && getSubtitleBottom() == 8f;
    }

    public static void resetSubtitle() {
        putSubtitleColor(android.graphics.Color.WHITE);
        putSubtitleEdge(1);
        putSubtitleEdgeColor(android.graphics.Color.BLACK);
        putSubtitleBackground(0);
        putSubtitleBottom(8f);
        putSubtitleTextSize(0f);
    }

    // ==================== 弹幕样式 ====================

    public static float getDanmakuSize() {
        return clamp(Prefers.getFloat("danmaku_size", 0.8f), 0.5f, 2f);
    }

    public static void putDanmakuSize(float value) {
        Prefers.put("danmaku_size", clamp(value, 0.5f, 2f));
    }

    public static float getDanmakuSpeed() {
        return clamp(Prefers.getFloat("danmaku_speed", 1.2f), 0.5f, 2f);
    }

    public static void putDanmakuSpeed(float value) {
        Prefers.put("danmaku_speed", clamp(value, 0.5f, 2f));
    }

    public static float getDanmakuAlpha() {
        return clamp(Prefers.getFloat("danmaku_alpha", 0.8f), 0.1f, 1f);
    }

    public static void putDanmakuAlpha(float value) {
        Prefers.put("danmaku_alpha", clamp(value, 0.1f, 1f));
    }

    /** 每种类型最多显示的行数 */
    public static int getDanmakuLines() {
        return (int) clamp(Prefers.getInt("danmaku_lines", 2), 1, 8);
    }

    public static void putDanmakuLines(int lines) {
        Prefers.put("danmaku_lines", (int) clamp(lines, 1, 8));
    }

    /** 0 描边 / 1 阴影 / 2 无 */
    public static int getDanmakuStyle() {
        return Math.min(Math.max(Prefers.getInt("danmaku_style", 0), 0), 2);
    }

    public static void putDanmakuStyle(int style) {
        Prefers.put("danmaku_style", style);
    }

    /** 弹幕时间偏移，毫秒 */
    public static int getDanmakuOffset() {
        return (int) clamp(Prefers.getInt("danmaku_offset", 0), -10000, 10000);
    }

    public static void putDanmakuOffset(int offset) {
        Prefers.put("danmaku_offset", (int) clamp(offset, -10000, 10000));
    }

    public static boolean isDanmakuDefault() {
        return getDanmakuSize() == 0.8f && getDanmakuSpeed() == 1.2f && getDanmakuAlpha() == 0.8f && getDanmakuLines() == 2 && getDanmakuStyle() == 0 && getDanmakuOffset() == 0;
    }

    public static void resetDanmaku() {
        putDanmakuSize(0.8f);
        putDanmakuSpeed(1.2f);
        putDanmakuAlpha(0.8f);
        putDanmakuLines(2);
        putDanmakuStyle(0);
        putDanmakuOffset(0);
    }

    // ==================== 首页与搜索个性化 ====================

    /** 最近观看区是否在首页显示（默认显示） */
    public static boolean isHistoryShow() {
        return Prefers.getBoolean("history_show", true);
    }

    public static void putHistoryShow(boolean show) {
        Prefers.put("history_show", show);
    }

    /** 首页源是否锁定（锁定后不响应换源与配置覆盖） */
    public static boolean isSourceLock() {
        return Prefers.getBoolean("source_lock");
    }

    public static void putSourceLock(boolean lock) {
        Prefers.put("source_lock", lock);
    }

    /** 搜索 UI 方向：0 横向 / 1 纵向（默认横向） */
    public static int getSearchUi() {
        return Prefers.getInt("search_ui", 0);
    }

    public static void putSearchUi(int mode) {
        Prefers.put("search_ui", mode);
    }

    private static float clamp(float value, float min, float max) {
        return Math.min(Math.max(value, min), max);
    }

    public static boolean isBackgroundOff() {
        return getBackground() == 0;
    }

    public static boolean isBackgroundOn() {
        return getBackground() == 1 || getBackground() == 2;
    }

    public static boolean isBackgroundPiP() {
        return getBackground() == 2;
    }

    public static boolean hasCaption() {
        return new Intent(Settings.ACTION_CAPTIONING_SETTINGS).resolveActivity(App.get().getPackageManager()) != null;
    }

    public static boolean hasFileManager() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && (new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + App.get().getPackageName())).resolveActivity(App.get().getPackageManager()) != null || new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).resolveActivity(App.get().getPackageManager()) != null);
    }
}
