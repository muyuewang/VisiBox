package com.fongmi.android.tv.player.engine;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.AdPlaybackState;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.BasePlayer;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.Util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import dev.jdtech.mpv.MPVLib;

/**
 * 基于 libmpv 的 media3 Player 适配层（移植自 Findroid MPVPlayer，GPL-3.0）。
 *
 * <p>只实现本应用用到的能力：播放/暂停/拖动/倍速/音轨字幕切换/Surface 渲染。
 * 未覆盖的 media3 方法一律安全空实现，避免运行时抛异常。
 */
public class MpvPlayer extends BasePlayer implements MPVLib.EventObserver {

    private final MPVLib mpvLib;
    private final Handler handler;
    private final Set<Player.Listener> listeners = new CopyOnWriteArraySet<>();
    private final List<MediaItem> mediaItems = new ArrayList<>();
    private final Timeline timeline = new MpvTimeline();

    private int playbackState = STATE_IDLE;
    private boolean playWhenReady;
    private boolean isPlayerReady;
    private boolean isSeekable;
    private int currentIndex;
    private long positionMs = C.TIME_UNSET;
    private long durationMs = C.TIME_UNSET;
    private long cacheMs = C.TIME_UNSET;
    private long initialSeekTo = C.TIME_UNSET;
    private int initialIndex;
    private PlaybackParameters parameters = PlaybackParameters.DEFAULT;
    private Tracks tracks = Tracks.EMPTY;
    private final List<String[]> initialCommands = new ArrayList<>();
    private final Map<String, String> headers = new HashMap<>();
    private final String videoOutput;
    private final boolean hardwareDecode;

    public MpvPlayer(Context context, boolean hardwareDecode) {
        this.handler = new Handler(Looper.getMainLooper());
        this.videoOutput = "gpu";
        this.hardwareDecode = hardwareDecode;
        File configDir = new File(context.getFilesDir(), "mpv");
        File cacheDir = new File(context.getCacheDir(), "mpv");
        if (!configDir.exists()) configDir.mkdirs();
        if (!cacheDir.exists()) cacheDir.mkdirs();
        writeFontsConf(context, configDir);
        MPVLib lib = MPVLib.create(context);
        if (lib == null) throw new IllegalStateException("MPVLib.create() failed");
        mpvLib = lib;
        applyOptions(configDir, cacheDir);
        mpvLib.init();
        mpvLib.addObserver(this);
        observe("track-list", MPVLib.MpvFormat.MPV_FORMAT_STRING);
        observe("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        observe("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        observe("seekable", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
        observe("time-pos", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("duration", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("demuxer-cache-time", MPVLib.MpvFormat.MPV_FORMAT_INT64);
        observe("speed", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE);
    }

    private void applyOptions(File configDir, File cacheDir) {
        mpvLib.setOptionString("config", "yes");
        mpvLib.setOptionString("config-dir", configDir.getPath());
        mpvLib.setOptionString("gpu-shader-cache-dir", cacheDir.getPath());
        mpvLib.setOptionString("icc-cache-dir", cacheDir.getPath());
        mpvLib.setOptionString("profile", "fast");
        mpvLib.setOptionString("vo", videoOutput);
        mpvLib.setOptionString("ao", "audiotrack");
        mpvLib.setOptionString("gpu-context", "android");
        mpvLib.setOptionString("opengl-es", "yes");
        mpvLib.setOptionString("hwdec", hardwareDecode ? "mediacodec" : "no");
        mpvLib.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1");
        mpvLib.setOptionString("tls-verify", "no");
        mpvLib.setOptionString("cache", "yes");
        mpvLib.setOptionString("demuxer-max-bytes", "64MiB");
        mpvLib.setOptionString("demuxer-max-back-bytes", "32MiB");
        mpvLib.setOptionString("sub-scale-with-window", "yes");
        mpvLib.setOptionString("sub-use-margins", "no");
        mpvLib.setOptionString("force-window", "no");
        mpvLib.setOptionString("keep-open", "always");
        mpvLib.setOptionString("save-position-on-quit", "no");
        mpvLib.setOptionString("ytdl", "no");
    }

    private void writeFontsConf(Context context, File configDir) {
        File config = new File(configDir, "fonts.conf");
        if (config.exists()) return;
        File fontcache = new File(context.getCacheDir(), "fontconfig");
        if (!fontcache.exists()) fontcache.mkdirs();
        String content = "<fontconfig><dir>/system/fonts/</dir><dir>/product/fonts/</dir>"
                + "<cachedir>" + fontcache.getPath() + "</cachedir></fontconfig>";
        try {
            Files.write(config.toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    private void observe(String name, int format) {
        mpvLib.observeProperty(name, format);
    }

    /**
     * 设置请求头，mpv 在 loadfile 前生效。键值以 mpv 的 http-header-fields 语法传递。
     */
    public void setRequestHeaders(Map<String, String> headers) {
        this.headers.clear();
        if (headers != null) this.headers.putAll(headers);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : this.headers.entrySet()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(entry.getKey()).append(": ").append(entry.getValue());
        }
        if (sb.length() > 0) mpvLib.setOptionString("http-header-fields", sb.toString());
    }

    // ------------------------------------------------------------------ 事件

    @Override
    public void eventProperty(@NonNull String property) {
    }

    @Override
    public void eventProperty(@NonNull String property, @NonNull String value) {
        handler.post(() -> {
            if ("track-list".equals(property)) {
                tracks = parseTracks(value);
                dispatch(listener -> listener.onTracksChanged(tracks));
            }
        });
    }

    @Override
    public void eventProperty(@NonNull String property, boolean value) {
        handler.post(() -> {
            switch (property) {
                case "eof-reached":
                    if (value && isPlayerReady) {
                        notifyState(STATE_ENDED);
                        resetInternalState();
                    }
                    break;
                case "paused-for-cache":
                    if (isPlayerReady) notifyState(value ? STATE_BUFFERING : STATE_READY);
                    break;
                case "seekable":
                    isSeekable = value;
                    break;
            }
        });
    }

    @Override
    public void eventProperty(@NonNull String property, long value) {
        handler.post(() -> {
            switch (property) {
                case "time-pos":
                    positionMs = value * C.MILLIS_PER_SECOND;
                    break;
                case "duration":
                    durationMs = value * C.MILLIS_PER_SECOND;
                    break;
                case "demuxer-cache-time":
                    cacheMs = value * C.MILLIS_PER_SECOND;
                    break;
            }
        });
    }

    @Override
    public void eventProperty(@NonNull String property, double value) {
        handler.post(() -> {
            if ("speed".equals(property)) {
                parameters = new PlaybackParameters((float) value);
                dispatch(listener -> listener.onPlaybackParametersChanged(parameters));
            }
        });
    }

    @Override
    public void event(int eventId) {
        handler.post(() -> {
            if (eventId == MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED) {
                isSeekable = Boolean.TRUE.equals(mpvLib.getPropertyBoolean("seekable"));
                Double duration = mpvLib.getPropertyDouble("duration");
                if (duration != null) durationMs = (long) (duration * C.MILLIS_PER_SECOND);
            } else if (eventId == MPVLib.MpvEvent.MPV_EVENT_SEEK) {
                notifyState(STATE_BUFFERING);
                dispatch(listener -> listener.onPositionDiscontinuity(DISCONTINUITY_REASON_SEEK));
            } else if (eventId == MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART) {
                if (!isPlayerReady) {
                    isPlayerReady = true;
                    if (positionMs != C.TIME_UNSET && positionMs > 0) seekTo(positionMs);
                    if (playWhenReady) mpvLib.setPropertyBoolean("pause", false);
                    dispatch(Player.Listener::onRenderedFirstFrame);
                } else {
                    notifyState(STATE_READY);
                }
            }
        });
    }

    private void notifyState(int state) {
        boolean changed = playbackState != state;
        playbackState = state;
        if (changed) dispatch(listener -> listener.onPlaybackStateChanged(state));
        dispatch(listener -> listener.onIsPlayingChanged(isPlaying()));
    }

    private void dispatch(java.util.function.Consumer<Player.Listener> action) {
        handler.post(() -> {
            for (Player.Listener listener : listeners) {
                try {
                    action.accept(listener);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private void resetInternalState() {
        isPlayerReady = false;
        isSeekable = false;
        playbackState = STATE_IDLE;
        playWhenReady = false;
        positionMs = C.TIME_UNSET;
        durationMs = C.TIME_UNSET;
        cacheMs = C.TIME_UNSET;
        tracks = Tracks.EMPTY;
        parameters = PlaybackParameters.DEFAULT;
        initialCommands.clear();
    }

    // ------------------------------------------------------------------ 播放控制

    @Override
    public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
        mpvLib.command(new String[]{"playlist-clear"});
        this.mediaItems.clear();
        this.mediaItems.addAll(mediaItems);
    }

    @Override
    public void setMediaItems(List<MediaItem> mediaItems, int startWindowIndex, long startPositionMs) {
        mpvLib.command(new String[]{"playlist-clear"});
        this.mediaItems.clear();
        this.mediaItems.addAll(mediaItems);
        initialIndex = startWindowIndex;
        initialSeekTo = startPositionMs;
    }

    @Override
    public void addMediaItems(int index, List<MediaItem> mediaItems) {
        this.mediaItems.addAll(index, mediaItems);
    }

    @Override
    public void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
    }

    @Override
    public void removeMediaItems(int fromIndex, int toIndex) {
    }

    @Override
    public void replaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems) {
    }

    @Override
    public void prepare() {
        for (int i = 0; i < mediaItems.size(); i++) {
            MediaItem item = mediaItems.get(i);
            mpvLib.command(new String[]{"loadfile", String.valueOf(item.localConfiguration == null ? "" : item.localConfiguration.uri), i == 0 ? "replace" : "append"});
        }
        prepareItem(initialIndex);
    }

    private void prepareItem(int index) {
        resetInternalState();
        currentIndex = index;
        if (initialSeekTo != C.TIME_UNSET) positionMs = initialSeekTo;
        notifyState(STATE_BUFFERING);
    }

    @Override
    public int getPlaybackState() {
        return playbackState;
    }

    @Override
    public int getPlaybackSuppressionReason() {
        return PLAYBACK_SUPPRESSION_REASON_NONE;
    }

    @Nullable
    @Override
    public PlaybackException getPlayerError() {
        return null;
    }

    @Override
    public void setPlayWhenReady(boolean playWhenReady) {
        boolean changed = this.playWhenReady != playWhenReady;
        this.playWhenReady = playWhenReady;
        if (changed) {
            dispatch(listener -> listener.onPlayWhenReadyChanged(playWhenReady, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST));
            if (isPlayerReady) mpvLib.setPropertyBoolean("pause", !playWhenReady);
        }
        dispatch(listener -> listener.onIsPlayingChanged(isPlaying()));
    }

    @Override
    public boolean getPlayWhenReady() {
        return playWhenReady;
    }

    @Override
    public void setRepeatMode(int repeatMode) {
    }

    @Override
    public int getRepeatMode() {
        return REPEAT_MODE_OFF;
    }

    @Override
    public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    }

    @Override
    public boolean getShuffleModeEnabled() {
        return false;
    }

    @Override
    public boolean isLoading() {
        return playbackState == STATE_BUFFERING;
    }

    @Override
    public void seekTo(int mediaItemIndex, long positionMs, int seekCommand, boolean isRepeating) {
        if (mediaItemIndex != currentIndex) {
            prepareItem(mediaItemIndex);
            play();
            return;
        }
        long target = positionMs != C.TIME_UNSET ? positionMs : initialSeekTo;
        if (isPlayerReady) {
            mpvLib.command(new String[]{"seek", String.valueOf(target / (double) C.MILLIS_PER_SECOND), "absolute"});
            initialSeekTo = C.TIME_UNSET;
        } else {
            initialSeekTo = target;
            this.positionMs = target;
        }
    }

    @Override
    public long getSeekBackIncrement() {
        return C.DEFAULT_SEEK_BACK_INCREMENT_MS;
    }

    @Override
    public long getSeekForwardIncrement() {
        return C.DEFAULT_SEEK_FORWARD_INCREMENT_MS;
    }

    @Override
    public long getMaxSeekToPreviousPosition() {
        return C.DEFAULT_MAX_SEEK_TO_PREVIOUS_POSITION_MS;
    }

    @Override
    public void setPlaybackParameters(PlaybackParameters playbackParameters) {
        if (parameters.speed != playbackParameters.speed) {
            mpvLib.setPropertyDouble("speed", playbackParameters.speed);
        }
        parameters = playbackParameters;
        dispatch(listener -> listener.onPlaybackParametersChanged(parameters));
    }

    @NonNull
    @Override
    public PlaybackParameters getPlaybackParameters() {
        return parameters;
    }

    @Override
    public void stop() {
        mpvLib.command(new String[]{"stop", "keep-playlist"});
    }

    @Override
    public void release() {
        resetInternalState();
        mpvLib.removeObserver(this);
        mpvLib.destroy();
        listeners.clear();
    }

    @Override
    public void setVolume(float volume) {
        mpvLib.setPropertyDouble("volume", volume * 100d);
    }

    @Override
    public float getVolume() {
        Integer volume = mpvLib.getPropertyInt("volume");
        return volume == null ? 1f : volume / 100f;
    }

    // ------------------------------------------------------------------ Surface

    @Override
    public void clearVideoSurface() {
        setVideoSurface(null);
    }

    @Override
    public void clearVideoSurface(@Nullable Surface surface) {
        if (surface == null) return;
        mpvLib.setOptionString("vo", "null");
        mpvLib.setOptionString("force-window", "no");
        mpvLib.detachSurface();
    }

    @Override
    public void setVideoSurface(@Nullable Surface surface) {
        if (surface == null) {
            clearVideoSurface(null);
            return;
        }
        mpvLib.attachSurface(surface);
        mpvLib.setOptionString("force-window", "yes");
        mpvLib.setOptionString("vo", videoOutput);
    }

    @Override
    public void setVideoSurfaceHolder(@Nullable SurfaceHolder holder) {
        if (holder == null) return;
        setVideoSurface(holder.getSurface());
    }

    @Override
    public void clearVideoSurfaceHolder(@Nullable SurfaceHolder holder) {
        clearVideoSurface(null);
    }

    private final SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            mpvLib.attachSurface(holder.getSurface());
            mpvLib.setOptionString("force-window", "yes");
            mpvLib.setOptionString("vo", videoOutput);
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
            mpvLib.setPropertyString("android-surface-size", width + "x" + height);
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            mpvLib.setOptionString("vo", "null");
            mpvLib.setOptionString("force-window", "no");
            mpvLib.detachSurface();
        }
    };

    @Override
    public void setVideoSurfaceView(@Nullable SurfaceView view) {
        if (view == null) return;
        view.getHolder().addCallback(surfaceCallback);
        if (view.getHolder().getSurface() != null && view.getHolder().getSurface().isValid()) {
            surfaceCallback.surfaceCreated(view.getHolder());
            android.graphics.Point size = new android.graphics.Point();
            view.getDisplay().getSize(size);
            surfaceCallback.surfaceChanged(view.getHolder(), 0, size.x, size.y);
        }
    }

    @Override
    public void clearVideoSurfaceView(@Nullable SurfaceView view) {
        if (view != null) view.getHolder().removeCallback(surfaceCallback);
        clearVideoSurface(null);
    }

    private final TextureView.SurfaceTextureListener textureCallback = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull android.graphics.SurfaceTexture texture, int width, int height) {
            setVideoSurface(new Surface(texture));
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull android.graphics.SurfaceTexture texture, int width, int height) {
            mpvLib.setPropertyString("android-surface-size", width + "x" + height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull android.graphics.SurfaceTexture texture) {
            clearVideoSurface(null);
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull android.graphics.SurfaceTexture texture) {
        }
    };

    @Override
    public void setVideoTextureView(@Nullable TextureView view) {
        if (view == null) return;
        view.setSurfaceTextureListener(textureCallback);
        if (view.isAvailable()) textureCallback.onSurfaceTextureAvailable(view.getSurfaceTexture(), view.getWidth(), view.getHeight());
    }

    @Override
    public void clearVideoTextureView(@Nullable TextureView view) {
        if (view != null) view.setSurfaceTextureListener(null);
        clearVideoSurface(null);
    }

    // ------------------------------------------------------------------ 状态查询

    @NonNull
    @Override
    public Looper getApplicationLooper() {
        return handler.getLooper();
    }

    @Override
    public void addListener(@NonNull Player.Listener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(@NonNull Player.Listener listener) {
        listeners.remove(listener);
    }

    @NonNull
    @Override
    public Commands getAvailableCommands() {
        return new Player.Commands.Builder().addAll(permanentCommands()).build();
    }

    private static Player.Commands permanentCommands() {
        return new Player.Commands.Builder().addAll(
                COMMAND_PLAY_PAUSE, COMMAND_SET_SPEED_AND_PITCH, COMMAND_GET_CURRENT_MEDIA_ITEM,
                COMMAND_GET_METADATA, COMMAND_CHANGE_MEDIA_ITEMS, COMMAND_SET_VIDEO_SURFACE,
                COMMAND_GET_TRACKS, COMMAND_SET_TRACK_SELECTION_PARAMETERS, COMMAND_SEEK_BACK,
                COMMAND_SEEK_FORWARD, COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_TO_DEFAULT_POSITION,
                COMMAND_SEEK_TO_MEDIA_ITEM).build();
    }

    @NonNull
    @Override
    public Tracks getCurrentTracks() {
        return tracks;
    }

    @NonNull
    @Override
    public TrackSelectionParameters getTrackSelectionParameters() {
        return TrackSelectionParameters.DEFAULT;
    }

    @Override
    public void setTrackSelectionParameters(@NonNull TrackSelectionParameters parameters) {
        Set<Integer> disabled = parameters.disabledTrackTypes;
        for (Map.Entry<TrackGroup, TrackSelectionOverride> override : parameters.overrides.entrySet()) {
            int type = override.getValue().getType();
            String mpvType = mpvTrackType(type);
            if (mpvType == null || override.getKey().length == 0) continue;
            mpvLib.setPropertyString(mpvType, String.valueOf(override.getKey().getFormat(0).id));
        }
        for (int type : new int[]{C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT, C.TRACK_TYPE_VIDEO}) {
            if (disabled.contains(type)) mpvLib.setPropertyString(mpvTrackType(type), "no");
        }
    }

    @NonNull
    @Override
    public MediaMetadata getMediaMetadata() {
        return MediaMetadata.EMPTY;
    }

    @NonNull
    @Override
    public MediaMetadata getPlaylistMetadata() {
        return MediaMetadata.EMPTY;
    }

    @Override
    public void setPlaylistMetadata(@NonNull MediaMetadata mediaMetadata) {
    }

    @NonNull
    @Override
    public Timeline getCurrentTimeline() {
        return timeline;
    }

    @Override
    public int getCurrentPeriodIndex() {
        return currentIndex;
    }

    @Override
    public int getCurrentMediaItemIndex() {
        return currentIndex;
    }

    @Override
    public long getDuration() {
        return durationMs == C.TIME_UNSET ? C.TIME_UNSET : durationMs;
    }

    @Override
    public long getCurrentPosition() {
        return positionMs == C.TIME_UNSET ? 0 : positionMs;
    }

    @Override
    public long getBufferedPosition() {
        return cacheMs == C.TIME_UNSET ? getCurrentPosition() : cacheMs;
    }

    @Override
    public long getTotalBufferedDuration() {
        return getBufferedPosition();
    }

    @Override
    public boolean isPlayingAd() {
        return false;
    }

    @Override
    public int getCurrentAdGroupIndex() {
        return C.INDEX_UNSET;
    }

    @Override
    public int getCurrentAdIndexInAdGroup() {
        return C.INDEX_UNSET;
    }

    @Override
    public long getContentPosition() {
        return getCurrentPosition();
    }

    @Override
    public long getContentBufferedPosition() {
        return getBufferedPosition();
    }

    @NonNull
    @Override
    public AudioAttributes getAudioAttributes() {
        return AudioAttributes.DEFAULT;
    }

    @NonNull
    @Override
    public VideoSize getVideoSize() {
        Integer width = mpvLib.getPropertyInt("width");
        Integer height = mpvLib.getPropertyInt("height");
        if (width == null || height == null || width <= 0 || height <= 0) return VideoSize.UNKNOWN;
        return new VideoSize(width, height);
    }

    @NonNull
    @Override
    public androidx.media3.common.util.Size getSurfaceSize() {
        String[] size = new String[0];
        String raw = mpvLib.getPropertyString("android-surface-size");
        if (raw != null) size = raw.split("x");
        try {
            return new androidx.media3.common.util.Size(Integer.parseInt(size[0]), Integer.parseInt(size[1]));
        } catch (Exception e) {
            return androidx.media3.common.util.Size.UNKNOWN;
        }
    }

    @NonNull
    @Override
    public CueGroup getCurrentCues() {
        return new CueGroup(Collections.emptyList(), 0);
    }

    @NonNull
    @Override
    public DeviceInfo getDeviceInfo() {
        return new DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_LOCAL).build();
    }

    @Override
    public int getDeviceVolume() {
        Integer volume = mpvLib.getPropertyInt("volume");
        return volume == null ? 100 : volume;
    }

    @Override
    public boolean isDeviceMuted() {
        return Boolean.TRUE.equals(mpvLib.getPropertyBoolean("mute"));
    }

    @Override
    public void setDeviceVolume(int volume, int flags) {
        mpvLib.setPropertyInt("volume", volume);
    }

    @Override
    public void setDeviceMuted(boolean muted, int flags) {
        mpvLib.setPropertyBoolean("mute", muted);
    }

    @Override
    public void setDeviceVolume(int volume) {
        setDeviceVolume(volume, C.VOLUME_FLAG_SHOW_UI);
    }

    @Override
    public void setDeviceMuted(boolean muted) {
        setDeviceMuted(muted, C.VOLUME_FLAG_SHOW_UI);
    }

    @Override
    public void mute() {
        mpvLib.setPropertyBoolean("mute", true);
    }

    @Override
    public void unmute() {
        mpvLib.setPropertyBoolean("mute", false);
    }

    @Override
    public void increaseDeviceVolume() {
    }

    @Override
    public void increaseDeviceVolume(int flags) {
    }

    @Override
    public void decreaseDeviceVolume(int flags) {
    }

    @Override
    public void decreaseDeviceVolume() {
    }

    private static String mpvTrackType(int type) {
        if (type == C.TRACK_TYPE_AUDIO) return "aid";
        if (type == C.TRACK_TYPE_TEXT) return "sid";
        if (type == C.TRACK_TYPE_VIDEO) return "vid";
        return null;
    }

    @Override
    public void setAudioAttributes(@NonNull AudioAttributes audioAttributes, boolean handleAudioFocus) {
    }

    // ------------------------------------------------------------------ Timeline

    private class MpvTimeline extends Timeline {

        @Override
        public int getWindowCount() {
            return mediaItems.size();
        }

        @Override
        public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
            MediaItem item = windowIndex >= 0 && windowIndex < mediaItems.size() ? mediaItems.get(windowIndex) : new MediaItem.Builder().build();
            return window.set(windowIndex, item, null, C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET, isSeekable, false, item.liveConfiguration, 0, Util.msToUs(durationMs == C.TIME_UNSET ? C.TIME_UNSET : durationMs), windowIndex, windowIndex, 0);
        }

        @Override
        public int getPeriodCount() {
            return mediaItems.size();
        }

        @NonNull
        @Override
        public Period getPeriod(int periodIndex, Period period, boolean setIds) {
            return period.set(periodIndex, periodIndex, periodIndex, Util.msToUs(durationMs == C.TIME_UNSET ? C.TIME_UNSET : durationMs), 0, AdPlaybackState.NONE, false);
        }

        @Override
        public int getIndexOfPeriod(Object uid) {
            return uid instanceof Integer ? (int) uid : C.INDEX_UNSET;
        }

        @NonNull
        @Override
        public Object getUidOfPeriod(int periodIndex) {
            return periodIndex;
        }
    }

    // ------------------------------------------------------------------ 轨道解析

    private Tracks parseTracks(String trackList) {
        List<Tracks.Group> groups = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(trackList);
            for (int i = 0; i < array.length(); i++) groups.add(buildGroup(array.getJSONObject(i)));
            if (!groups.isEmpty()) return new Tracks(groups);
        } catch (Exception ignored) {
        }
        return Tracks.EMPTY;
    }

    private Tracks.Group buildGroup(JSONObject json) throws Exception {
        String type = json.optString("type");
        int trackType = type.equals("audio") ? C.TRACK_TYPE_AUDIO : type.equals("sub") ? C.TRACK_TYPE_TEXT : C.TRACK_TYPE_VIDEO;
        String codec = json.isNull("codec") ? null : json.optString("codec");
        androidx.media3.common.Format.Builder format = new androidx.media3.common.Format.Builder();
        format.setId(json.optInt("id"));
        String label = json.isNull("title") ? null : json.optString("title");
        if (label != null) format.setLabel(label);
        String lang = json.isNull("lang") ? null : json.optString("lang");
        if (lang != null) format.setLanguage(lang);
        if (json.optBoolean("default")) format.setSelectionFlags(C.SELECTION_FLAG_DEFAULT);
        if (codec != null) format.setCodecs(codec);
        if (trackType == C.TRACK_TYPE_AUDIO) {
            format.setSampleMimeType("audio/" + (codec == null ? "raw" : codec));
            format.setChannelCount(json.optInt("demux-channel-count"));
            format.setSampleRate(json.optInt("demux-samplerate"));
        } else if (trackType == C.TRACK_TYPE_VIDEO) {
            format.setSampleMimeType("video/" + (codec == null ? "raw" : codec));
            format.setWidth(json.optInt("demux-w"));
            format.setHeight(json.optInt("demux-h"));
        } else {
            format.setSampleMimeType("text/" + (codec == null ? "raw" : codec));
        }
        androidx.media3.common.TrackGroup group = new androidx.media3.common.TrackGroup(format.build());
        boolean selected = json.optBoolean("selected");
        boolean[] trackSelected = new boolean[group.length];
        java.util.Arrays.fill(trackSelected, selected);
        return new Tracks.Group(group, false, new int[group.length], trackSelected);
    }

    @NonNull
    @Override
    public String toString() {
        return "MpvPlayer";
    }
}
