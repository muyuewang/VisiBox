package com.fongmi.android.tv.player.danmaku;

import androidx.media3.common.Player;

import master.flame.danmaku.danmaku.model.AbsDanmakuSync;

public class Sync extends AbsDanmakuSync {

    private final Player player;

    public Sync(Player player) {
        this.player = player;
    }

    @Override
    public long getUptimeMillis() {
        return player.getCurrentPosition();
    }

    @Override
    public int getSyncState() {
        return player.isPlaying() ? SYNC_STATE_PLAYING : SYNC_STATE_HALT;
    }

    @Override
    public long getThresholdTimeMills() {
        return 1000L;
    }

    @Override
    public boolean isSyncPlayingState() {
        return true;
    }
}
