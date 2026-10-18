package com.visibox.tv.player.engine;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;

import com.visibox.tv.App;
import com.visibox.tv.R;
import com.visibox.tv.bean.Track;
import com.visibox.tv.player.exo.TrackUtil;
import com.visibox.tv.utils.ResUtil;

import java.util.List;

/**
 * MPV 引擎，与 ExoPlayerEngine 并列，可在设置里切换。
 *
 * <p>注意：MPV 出错时没有 Exo 那套可恢复错误分类，一律按 FATAL 上抛，
 * 由播放器界面提示用户。倍速、音轨、字幕、硬解均可用于 MPV。
 */
public class MpvPlayerEngine implements PlayerEngine {

    private final Player.Listener listener;
    private MpvPlayer player;
    private int decode;
    private PlaySpec spec;

    public MpvPlayerEngine(int decode, Player.Listener listener) {
        this.listener = listener;
        this.decode = decode;
        this.player = build();
    }

    private MpvPlayer build() {
        MpvPlayer player = new MpvPlayer(App.get(), decode == HARD);
        player.addListener(listener);
        return player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void release() {
        player.release();
    }

    @Override
    public Player rebuild(Player.Listener listener) {
        player.release();
        return player = build();
    }

    @Override
    public int getDecode() {
        return decode;
    }

    @Override
    public void setDecode(int decode) {
        this.decode = decode;
    }

    @Override
    public boolean isHard() {
        return decode == HARD;
    }

    @Override
    public String getDecodeText() {
        return ResUtil.getStringArray(R.array.select_decode)[decode];
    }

    @Override
    public void start(PlaySpec spec) {
        this.spec = spec;
        player.setRequestHeaders(spec.getHeaders());
        player.setMediaItem(TextUtils.isEmpty(spec.getFormat()) ? MediaItem.fromUri(spec.getUrl()) : new MediaItem.Builder().setUri(spec.getUrl()).setMimeType(spec.getFormat()).build());
        player.prepare();
        player.play();
    }

    @Override
    public void setMetadata(MediaMetadata data) {
    }

    @Override
    public boolean isLive() {
        return false;
    }

    @Override
    public boolean isVod() {
        return true;
    }

    @Override
    public void setTrack(List<Track> tracks) {
        TrackUtil.setTrackSelection(player, tracks);
    }

    @Override
    public void resetTrack() {
        TrackUtil.reset(player);
    }

    @Override
    public boolean haveTrack(int type) {
        return TrackUtil.count(getCurrentTracks(), type) > 0;
    }

    @Override
    public Tracks getCurrentTracks() {
        return player.getCurrentTracks();
    }

    @Override
    public String getErrorMessage(PlaybackException e) {
        return e.getMessage() == null ? ResUtil.getString(R.string.error_play_timeout) : e.getMessage();
    }

    @Override
    public ErrorAction handleError(@NonNull PlaybackException e) {
        return ErrorAction.FATAL;
    }
}
