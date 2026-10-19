package com.visibox.tv.player;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;

import com.visibox.tv.App;
import com.visibox.tv.Constant;
import com.visibox.tv.R;
import com.visibox.tv.Setting;
import com.visibox.tv.bean.Danmaku;
import com.visibox.tv.bean.Result;
import com.visibox.tv.bean.Sub;
import com.visibox.tv.bean.Track;
import com.visibox.tv.impl.ParseCallback;
import com.visibox.tv.player.danmaku.DanPlayer;
import com.visibox.tv.player.engine.ExoPlayerEngine;
import com.visibox.tv.player.engine.MpvPlayerEngine;
import com.visibox.tv.player.engine.PlaySpec;
import com.visibox.tv.player.engine.PlayerEngine;
import com.visibox.tv.utils.Notify;
import com.visibox.tv.utils.ResUtil;
import com.visibox.tv.utils.Util;
import com.google.common.net.HttpHeaders;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import master.flame.danmaku.ui.widget.DanmakuView;

public class PlayerManager implements ParseCallback {

    private final Runnable runnable;
    private final Callback callback;
    private PlayerEngine engine;
    private DanPlayer danPlayer;
    private VideoSize videoSize;
    private ParseJob parseJob;
    private PlaySpec spec;
    private Player player;

    private boolean initTrack;
    private boolean engineSwitched;
    private int retry;

    public PlayerManager(Callback callback) {
        this.runnable = () -> callback.onError(ResUtil.getString(R.string.error_play_timeout));
        this.engine = createEngine(listener);
        this.player = engine.getPlayer();
        this.callback = callback;
    }

    /**
     * 按设置选择播放内核：0=Exo（默认），1=MPV。切换在下次起播时生效。
     */
    private PlayerEngine createEngine(Player.Listener listener) {
        return Setting.getPlayer() == 1 ? new MpvPlayerEngine(PlayerEngine.HARD, listener) : new ExoPlayerEngine(PlayerEngine.HARD, listener);
    }

    public void release() {
        stopParse();
        App.removeCallbacks(runnable);
        if (danPlayer != null) {
            danPlayer.release();
            danPlayer = null;
        }
        if (engine != null) {
            player.removeListener(listener);
            engine.release();
            engine = null;
            player = null;
        }
    }

    public Player getPlayer() {
        return player;
    }

    public Tracks getCurrentTracks() {
        return engine.getCurrentTracks();
    }

    public MediaItem getCurrentMediaItem() {
        return player.getCurrentMediaItem();
    }

    public int getPlaybackState() {
        return player.getPlaybackState();
    }

    public boolean isPlaying() {
        return player.isPlaying();
    }

    public String getUrl() {
        return spec != null ? spec.getUrl() : null;
    }

    public String getKey() {
        return spec != null ? spec.getKey() : null;
    }

    public List<Danmaku> getDanmakus() {
        return spec != null ? spec.getDanmakus() : null;
    }

    public Map<String, String> getHeaders() {
        return spec == null || spec.getHeaders() == null ? new HashMap<>() : spec.getHeaders();
    }

    public float getSpeed() {
        return player.getPlaybackParameters().speed;
    }

    public boolean isEmpty() {
        return spec == null || TextUtils.isEmpty(spec.getUrl());
    }

    public boolean isPortrait() {
        return getVideoHeight() > getVideoWidth();
    }

    public boolean isLandscape() {
        return getVideoWidth() > getVideoHeight();
    }

    public boolean isLive() {
        return engine.isLive();
    }

    public boolean isVod() {
        return engine.isVod();
    }

    public boolean haveTrack(int type) {
        return engine.haveTrack(type);
    }

    public boolean haveDanmaku() {
        return getDanmakus() != null && getDanmakus().stream().anyMatch(Danmaku::isSelected);
    }

    public boolean canSetOpening(long position, long duration) {
        return position > 0 && duration > 0 && position <= Constant.getOpEdLimit(duration);
    }

    public boolean canSetEnding(long position, long duration) {
        return position > 0 && duration > 0 && duration - position <= Constant.getOpEdLimit(duration);
    }

    public int getVideoWidth() {
        return videoSize == null ? 0 : videoSize.width;
    }

    public int getVideoHeight() {
        return videoSize == null ? 0 : videoSize.height;
    }

    public long getPosition() {
        return player.getCurrentPosition();
    }

    public String getSizeText() {
        return (getVideoWidth() == 0 && getVideoHeight() == 0) ? "" : getVideoWidth() + " x " + getVideoHeight();
    }

    public String getSpeedText() {
        return String.format(Locale.getDefault(), "%.2f", getSpeed());
    }

    public String getDecodeText() {
        return engine.getDecodeText();
    }

    public String getPositionTime(long delta) {
        long time = Math.max(0, Math.min(getPosition() + delta, Math.max(0, getDuration())));
        return Util.timeMs(time);
    }

    public long getDuration() {
        return player.getDuration();
    }

    public String getDurationTime() {
        return Util.timeMs(Math.max(0, getDuration()));
    }

    public void setSub(Sub sub) {
        if (spec != null) spec.setSub(sub);
        setMediaItem();
    }

    public void setFormat(String format) {
        if (spec != null) spec.setFormat(format);
        setMediaItem();
    }

    public static MediaMetadata buildMetadata(String title, String artist, String artUri) {
        Uri artwork = TextUtils.isEmpty(artUri) ? null : Uri.parse(artUri);
        return new MediaMetadata.Builder().setTitle(title).setArtist(artist).setArtworkUri(artwork).build();
    }

    public void setMetadata(MediaMetadata data) {
        if (spec != null) spec.setMetadata(data);
        engine.setMetadata(data);
    }

    public void setDanmakuView(DanmakuView view) {
        danPlayer = new DanPlayer(view);
        danPlayer.attachPlayer(player);
    }

    public void setDanmakuSize(float size) {
        danPlayer.setTextSize(size);
    }

    /** 弹幕样式改动后即时刷新 */
    public void applyDanmakuStyle() {
        if (danPlayer != null) danPlayer.applyStyle();
    }

    public String setSpeed(float speed) {
        if (!player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)) return getSpeedText();
        player.setPlaybackParameters(player.getPlaybackParameters().withSpeed(speed));
        return getSpeedText();
    }

    public String addSpeed() {
        float speed = getSpeed();
        float addon = speed >= 2 ? 1f : 0.25f;
        speed = speed >= 5 ? 0.25f : Math.min(speed + addon, 5.0f);
        return setSpeed(speed);
    }

    public String addSpeed(float value) {
        return setSpeed(Math.min(getSpeed() + value, 5));
    }

    public String subSpeed(float value) {
        return setSpeed(Math.max(getSpeed() - value, 0.25f));
    }

    public String toggleSpeed() {
        return setSpeed(getSpeed() == 1 ? Setting.getSpeed() : 1);
    }

    public void setTrack(List<Track> tracks) {
        if (!tracks.isEmpty()) engine.setTrack(tracks);
    }

    public void play() {
        player.play();
    }

    public void pause() {
        player.pause();
    }

    public void stop() {
        if (danPlayer != null) danPlayer.stop();
        player.stop();
        stopParse();
    }

    public void setRepeatOne(boolean repeat) {
        player.setRepeatMode(repeat ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
    }

    public void seekTo(long time) {
        player.seekTo(time);
    }

    public void reset() {
        App.removeCallbacks(runnable);
        retry = 0;
        engineSwitched = false;
    }

    public void clear() {
        spec = null;
    }

    public void resetTrack() {
        engine.resetTrack();
    }

    /**
     * 运行时切换播放内核（仅本次播放生效，不改动设置里的偏好）。
     */
    public void switchEngine() {
        player.removeListener(listener);
        engine.release();
        boolean mpv = !(engine instanceof MpvPlayerEngine);
        engine = mpv ? new MpvPlayerEngine(PlayerEngine.HARD, listener) : new ExoPlayerEngine(PlayerEngine.HARD, listener);
        rebuildPlayer();
    }

    public void toggleDecode() {
        engine.setDecode(engine.isHard() ? PlayerEngine.SOFT : PlayerEngine.HARD);
        rebuildPlayer();
        setMediaItem();
    }

    private void rebuildPlayer() {
        player = engine.rebuild(listener);
        if (danPlayer != null) danPlayer.attachPlayer(player);
        callback.onPlayerRebuild(player);
    }

    public void start(PlaySpec spec, long timeout) {
        this.spec = spec;
        setMediaItem(timeout);
    }

    public void parse(String key, Result result, boolean useParse, MediaMetadata metadata) {
        stopParse();
        spec = PlaySpec.fromParse(result, key, metadata);
        parseJob = ParseJob.create(this).start(result, useParse);
    }

    private void stopParse() {
        if (parseJob != null) parseJob.stop();
        parseJob = null;
    }

    public void setMediaItem() {
        setMediaItem(Constant.TIMEOUT_PLAY);
    }

    private void setMediaItem(long timeout) {
        if (spec == null || spec.getUrl() == null) return;
        setDanmakus(spec.getDanmakus());
        engine.start(spec.checkUa());
        App.post(runnable, timeout);
        callback.onPrepare();
        initTrack = false;
    }

    public void startBrowse(PlaySpec spec) {
        reset();
        clear();
        stopParse();
        start(spec, Constant.TIMEOUT_PLAY);
    }

    private void setDanmakus(List<Danmaku> items) {
        if (danPlayer != null) setDanmaku(items == null || items.isEmpty() ? Danmaku.empty() : items.get(0));
    }

    public void setDanmaku(Danmaku item) {
        if (spec != null) spec.setDanmaku(item);
        if (danPlayer != null) danPlayer.setDanmaku(item);
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        if (!TextUtils.isEmpty(from)) Notify.show(ResUtil.getString(R.string.parse_from, from));
        if (headers != null) headers.remove(HttpHeaders.RANGE);
        if (spec != null) spec.setHeaders(headers);
        if (spec != null) spec.setUrl(url);
        setMediaItem();
    }

    @Override
    public void onParseError() {
        callback.onError(ResUtil.getString(R.string.error_play_parse));
    }

    public interface Callback {

        void onPrepare();

        void onTracksChanged();

        void onTitlesChanged();

        void onError(String msg);

        void onPlayerRebuild(Player newPlayer);
    }

    private final Player.Listener listener = new Player.Listener() {

        @Override
        public void onPlaybackStateChanged(int state) {
            if (state != Player.STATE_IDLE) App.removeCallbacks(runnable);
        }

        @Override
        public void onVideoSizeChanged(@NonNull VideoSize size) {
            videoSize = size;
        }

        @Override
        public void onTracksChanged(@NonNull Tracks tracks) {
            if (tracks.isEmpty() || initTrack) return;
            setTrack(Track.find(getKey()));
            callback.onTracksChanged();
            initTrack = true;
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException e) {
            PlayerEngine.ErrorAction action = engine.handleError(e);
            if (action == PlayerEngine.ErrorAction.RECOVERED) return;
            // Exo 播放失败自动切 MPV 重试一次；MPV 再失败则报错
            if (action == PlayerEngine.ErrorAction.FATAL && !engineSwitched && !(engine instanceof MpvPlayerEngine)) {
                engineSwitched = true;
                switchEngine();
                setMediaItem();
                return;
            }
            if (++retry > 2) {
                callback.onError(engine.getErrorMessage(e));
                return;
            }
            switch (action) {
                case DECODE:
                    toggleDecode();
                    break;
                case FATAL:
                    callback.onError(engine.getErrorMessage(e));
                    break;
            }
        }
    };
}
