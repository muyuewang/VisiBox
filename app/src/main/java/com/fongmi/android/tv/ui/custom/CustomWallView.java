package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.databinding.ViewWallBinding;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.utils.FileUtil;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;

import pl.droidsonroids.gif.GifDrawable;

public class CustomWallView extends FrameLayout implements DefaultLifecycleObserver {

    private ViewWallBinding binding;
    private GifDrawable drawable;
    private PlayerView video;
    private ExoPlayer player;
    private Drawable cache;

    public CustomWallView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isInEditMode()) init();
    }

    private void init() {
        binding = ViewWallBinding.inflate(LayoutInflater.from(getContext()), this, true);
        ((ComponentActivity) getContext()).getLifecycle().addObserver(this);
        refresh();
    }

    private void ensurePlayer() {
        if (player != null) return;
        player = new ExoPlayer.Builder(getContext()).build();
        player.setRepeatMode(Player.REPEAT_MODE_ALL);
        player.setPlayWhenReady(true);
        player.mute();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.type() == ConfigEvent.Type.WALL) refresh();
    }

    private void refresh() {
        stop();
        load();
    }

    private void stop() {
        if (player != null && player.isPlaying()) {
            player.stop();
            player.clearMediaItems();
        }
        if (video != null) {
            video.setPlayer(null);
            video.setVisibility(GONE);
        }
        if (drawable != null) {
            drawable.stop();
            drawable.recycle();
        }
    }

    private void load() {
        int wall = Setting.getWall();
        File file = FileUtil.getWall(wall);
        cache = Drawable.createFromPath(FileUtil.getWallCache().getAbsolutePath());
        if (Setting.getWallType() == 0 && wall != 0) loadRes(wall);
        else if (Setting.getWallType() == 2) loadVideo(file);
        else if (Setting.getWallType() == 1) loadGif(file);
        else loadImage();
    }

    private void loadRes(int wall) {
        if (wall == 1) binding.image.setImageResource(R.drawable.wallpaper_1);
        else if (wall == 2) binding.image.setImageResource(R.drawable.wallpaper_2);
        else if (wall == 3) binding.image.setImageResource(R.drawable.wallpaper_3);
        else if (wall == 4) binding.image.setImageResource(R.drawable.wallpaper_4);
    }

    private void loadVideo(File file) {
        ensurePlayer();
        ensureVideoView();
        video.setPlayer(player);
        video.setVisibility(VISIBLE);
        binding.image.setImageDrawable(cache);
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)));
        player.prepare();
    }

    private void loadGif(File file) {
        binding.image.setImageDrawable(cache);
        binding.image.setImageDrawable(drawable = gif(file));
    }

    private void loadImage() {
        if (cache != null) binding.image.setImageDrawable(cache);
        else binding.image.setImageResource(R.drawable.wallpaper_1);
    }

    private GifDrawable gif(File file) {
        try {
            return new GifDrawable(file);
        } catch (IOException e) {
            return null;
        }
    }

    private void ensureVideoView() {
        if (video != null) return;
        LayoutInflater inflater = LayoutInflater.from(getContext());
        video = (PlayerView) inflater.inflate(R.layout.view_wall_video, this, false);
        addView(video, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    @Override
    public void onCreate(@NonNull LifecycleOwner owner) {
        EventBus.getDefault().register(this);
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        if (drawable != null) drawable.start();
        if (player == null || video == null || video.getVisibility() != VISIBLE || player.getMediaItemCount() == 0) return;
        video.setPlayer(player);
        player.play();
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        if (drawable != null) drawable.pause();
        if (player == null || video == null || video.getVisibility() != VISIBLE || player.getMediaItemCount() == 0) return;
        video.setPlayer(null);
        player.pause();
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        EventBus.getDefault().unregister(this);
        if (drawable != null) drawable.recycle();
        if (video != null) removeView(video);
        if (player != null) player.release();
        drawable = null;
        binding = null;
        player = null;
        cache = null;
        video = null;
    }
}
