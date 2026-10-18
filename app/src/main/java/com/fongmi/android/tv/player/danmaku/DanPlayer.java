package com.fongmi.android.tv.player.danmaku;

import androidx.annotation.NonNull;
import androidx.media3.common.Player;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

import master.flame.danmaku.controller.DrawHandler;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.ui.widget.DanmakuView;

public class DanPlayer implements DrawHandler.Callback, Player.Listener {

    private final DanmakuContext context;
    private final DanmakuView view;
    private Future<?> future;
    private Player player;

    public DanPlayer(DanmakuView view) {
        context = DanmakuContext.create();
        view.setCallback(this);
        this.view = view;
        initContext();
    }

    private void initContext() {
        Map<Integer, Integer> lines = new HashMap<>();
        lines.put(BaseDanmaku.TYPE_FIX_TOP, 2);
        lines.put(BaseDanmaku.TYPE_SCROLL_RL, 2);
        lines.put(BaseDanmaku.TYPE_SCROLL_LR, 2);
        lines.put(BaseDanmaku.TYPE_FIX_BOTTOM, 2);
        context.setScaleTextSize(0.8f);
        context.setMaximumLines(lines);
        context.setScrollSpeedFactor(1.2f);
        context.setDanmakuTransparency(0.8f);
        context.setDanmakuMargin(ResUtil.dp2px(8));
        context.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3);
    }

    public void attachPlayer(Player player) {
        this.player = player;
        player.addListener(this);
        context.setDanmakuSync(new Sync(player));
    }

    public void detachPlayer() {
        player.removeListener(this);
    }

    private boolean isPrepared() {
        return view.isPrepared();
    }

    private void cancel() {
        if (future == null) return;
        OkHttp.cancel("danmaku");
        future.cancel(true);
        future = null;
    }

    private void play() {
        if (isPrepared()) view.resume();
    }

    private void pause() {
        if (isPrepared()) view.pause();
    }

    public void stop() {
        cancel();
        view.stop();
    }

    public void release() {
        cancel();
        detachPlayer();
        view.release();
    }

    public void setDanmaku(Danmaku item) {
        cancel();
        future = Task.submit(() -> {
            if (item.isEmpty()) view.stop();
            else view.prepare(new Parser().load(new Loader().load(item).getDataSource()), context);
        });
    }

    public void setTextSize(float size) {
        context.setScaleTextSize(size);
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        if (isPlaying) play();
        else pause();
    }

    @Override
    public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
        if (isPrepared()) view.seekTo(newPosition.positionMs);
    }

    @Override
    public void prepared() {
        App.post(() -> {
            if (!isPrepared()) return;
            view.start(player.getCurrentPosition());
            if (!player.isPlaying()) view.pause();
            view.show();
        });
    }

    @Override
    public void updateTimer(DanmakuTimer danmakuTimer) {
    }

    @Override
    public void danmakuShown(BaseDanmaku baseDanmaku) {
    }

    @Override
    public void drawingFinished() {
    }
}