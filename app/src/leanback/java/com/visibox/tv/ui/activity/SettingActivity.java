package com.visibox.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.viewbinding.ViewBinding;

import com.visibox.tv.BuildConfig;
import com.visibox.tv.R;
import com.visibox.tv.Setting;
import com.visibox.tv.Updater;
import com.visibox.tv.api.config.LiveConfig;
import com.visibox.tv.api.config.MultiConfig;
import com.visibox.tv.api.config.VodConfig;
import com.visibox.tv.api.config.WallConfig;
import com.visibox.tv.bean.Config;
import com.visibox.tv.bean.Live;
import com.visibox.tv.bean.Site;
import com.visibox.tv.databinding.ActivitySettingBinding;
import com.visibox.tv.db.AppDatabase;
import com.visibox.tv.event.ConfigEvent;
import com.visibox.tv.event.RefreshEvent;
import com.visibox.tv.impl.Callback;
import com.visibox.tv.impl.ConfigCallback;
import com.visibox.tv.impl.DohCallback;
import com.visibox.tv.impl.LiveCallback;
import com.visibox.tv.impl.SiteCallback;
import com.visibox.tv.ui.base.BaseActivity;
import com.visibox.tv.ui.dialog.ConfigDialog;
import com.visibox.tv.ui.dialog.CrashLogDialog;
import com.visibox.tv.ui.dialog.DohDialog;
import com.visibox.tv.ui.dialog.HistoryDialog;
import com.visibox.tv.ui.dialog.LiveDialog;
import com.visibox.tv.ui.dialog.RestoreDialog;
import com.visibox.tv.ui.dialog.SiteDialog;
import com.visibox.tv.utils.FileChooser;
import com.visibox.tv.utils.CrashLog;
import com.visibox.tv.utils.FileUtil;
import com.visibox.tv.utils.GlassBlur;
import com.visibox.tv.utils.Notify;
import com.visibox.tv.utils.PermissionUtil;
import com.visibox.tv.utils.ResUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

public class SettingActivity extends BaseActivity implements ConfigCallback, SiteCallback, LiveCallback, DohCallback {

    private ActivitySettingBinding mBinding;
    private String[] size;
    private int type;
    private boolean morePage;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingActivity.class));
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    private int getDohIndex() {
        return Math.max(0, VodConfig.get().getDoh().indexOf(Doh.objectFrom(Setting.getDoh())));
    }

    private String[] getDohList() {
        List<String> list = new ArrayList<>();
        for (Doh item : VodConfig.get().getDoh()) list.add(item.getName());
        return list.toArray(new String[0]);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.vod.requestFocus();
        mBinding.vodUrl.setText(VodConfig.getDesc());
        mBinding.liveUrl.setText(LiveConfig.getDesc());
        mBinding.wallUrl.setText(WallConfig.getDesc());
        mBinding.versionText.setText(BuildConfig.VERSION_NAME);
        setMultiText();
        setCacheText();
        setOtherText();
    }

    private void setMultiText() {
        int count = MultiConfig.count(VodConfig.get().getConfig());
        if (!MultiConfig.isEnabled()) mBinding.multiText.setText(R.string.setting_multi_off);
        else mBinding.multiText.setText(ResUtil.getString(R.string.setting_multi_on, count));
    }

    private void toggleMulti(View view) {
        boolean enabled = MultiConfig.toggle();
        setMultiText();
        Notify.show(enabled ? R.string.setting_multi_on_toast : R.string.setting_multi_off_toast);
        // 立即重载当前配置，应用或撤销合并
        VodConfig.load(VodConfig.get().getConfig(), getCallback());
    }

    private String getRenderText() {
        return getString(Setting.getRender() == 0 ? R.string.setting_render_surface : R.string.setting_render_texture);
    }

    private String getScaleText() {
        String[] items = ResUtil.getStringArray(R.array.select_scale);
        int index = Math.min(Math.max(Setting.getScale(), 0), items.length - 1);
        return items[index];
    }

    private String getPlayerSelText() {
        return getString(Setting.getPlayer() == 1 ? R.string.setting_player_mpv : R.string.setting_player_exo);
    }

    private void setPlayerSel(View view) {
        Setting.putPlayer(Setting.getPlayer() == 1 ? 0 : 1);
        mBinding.playerSelText.setText(getPlayerSelText());
        Notify.show(R.string.setting_restart_tip);
    }

    private String getSiteListText() {
        return getString(Setting.getSiteMode() == 0 ? R.string.setting_sitelist_grid : R.string.setting_sitelist_list);
    }

    private void setSiteList(View view) {
        Setting.putSiteMode(Math.abs(Setting.getSiteMode() - 1));
        Notify.show(R.string.setting_restart_tip);
    }

    private String getHomeColText() {
        // 列数与「图片尺寸」同源：column = |size - 7|
        return ResUtil.getString(R.string.setting_col_value, Math.abs(Setting.getSize() - 7));
    }

    private void setBoot(View view) {
        Setting.putBoot(!Setting.getBoot());
        mBinding.bootText.setText(getSwitch(Setting.getBoot()));
    }

    private void setHistoryNum(View view) {
        int[] values = {10, 20, 30, 50, 100};
        int current = Setting.getHistoryNum();
        int next = values[0];
        for (int i = 0; i < values.length; i++) if (values[i] == current) { next = values[(i + 1) % values.length]; break; }
        Setting.putHistoryNum(next);
        mBinding.historyNumText.setText(ResUtil.getString(R.string.setting_num_value, next));
    }

    private void setHomeCol(View view) {
        // 列数与「图片尺寸」同源（column = |size - 7|），循环 1..7 列
        Setting.putSize((Setting.getSize() + 6) % 7);
        mBinding.homeColText.setText(getHomeColText());
        mBinding.glassText.setText(getGlassText());
    }

    private void setGlass(View view) {
        // 关 → 弱 → 强：低端盒子可关掉真模糊与动效
        Setting.putGlass((Setting.getGlass() + 1) % 3);
        if (Setting.getGlass() == 0) GlassBlur.clear();
        mBinding.glassText.setText(getGlassText());
    }

    private String getGlassText() {
        return switch (Setting.getGlass()) {
            case 0 -> ResUtil.getString(R.string.setting_glass_off);
            case 1 -> ResUtil.getString(R.string.setting_glass_weak);
            default -> ResUtil.getString(R.string.setting_glass_strong);
        };
    }

    private void onLine(View view) {
        // 多仓场景下「线路」= 切换仓库配置，与首页线路一致
        HistoryDialog.create(this).type(type = 0).show();
    }

    private void setRender(View view) {
        Setting.putRender(Setting.getRender() == 0 ? 1 : 0);
        mBinding.renderText.setText(getRenderText());
        mBinding.playerSelText.setText(getPlayerSelText());
        Notify.show(R.string.setting_restart_tip);
    }

    private void setBuffer(View view) {
        int[] values = {1, 2, 3, 4, 5, 6, 8, 10};
        Setting.putBuffer(next(values, Setting.getBuffer()));
        mBinding.bufferText.setText(ResUtil.getString(R.string.setting_buffer_value, Setting.getBuffer()));
        Notify.show(R.string.setting_restart_tip);
    }

    private void setScale(View view) {
        String[] items = ResUtil.getStringArray(R.array.select_scale);
        Setting.putScale((Math.max(Setting.getScale(), 0) + 1) % items.length);
        mBinding.scaleText.setText(getScaleText());
        Notify.show(R.string.setting_restart_tip);
    }

    private void setDanmaku(View view) {
        Setting.putDanmakuShow(!Setting.isDanmakuShow());
        mBinding.danmakuText.setText(getSwitch(Setting.isDanmakuShow()));
    }

    private void setHistoryShow(View view) {
        Setting.putHistoryShow(!Setting.isHistoryShow());
        mBinding.historyShowText.setText(getSwitch(Setting.isHistoryShow()));
        // 立即生效：首页监听 HOME 事件走 getVideo()→getHistory()，即时增删「最近观看」区
        RefreshEvent.home();
    }

    private void setSourceLock(View view) {
        Setting.putSourceLock(!Setting.isSourceLock());
        mBinding.sourceLockText.setText(getSwitch(Setting.isSourceLock()));
        Notify.show(Setting.isSourceLock() ? R.string.setting_source_lock_tip : R.string.setting_source_lock_off_tip);
    }

    private String getSearchUiText() {
        return getString(Setting.getSearchUi() == 1 ? R.string.setting_search_ui_vertical : R.string.setting_search_ui_horizontal);
    }

    private void setSearchUi(View view) {
        Setting.putSearchUi(Setting.getSearchUi() == 1 ? 0 : 1);
        mBinding.searchUiText.setText(getSearchUiText());
        // 立即生效：搜索页在 next start 时按新方向渲染（数据即时保留，无需重启）
        Notify.show(R.string.setting_effect_now);
    }

    private void setThread(View view) {
        int[] values = {4, 8, 12, 16, 24, 32};
        Setting.putThread(next(values, Setting.getThread()));
        mBinding.threadText.setText(ResUtil.getString(R.string.setting_thread_value, Setting.getThread()));
        mBinding.bootText.setText(getSwitch(Setting.getBoot()));
        mBinding.historyNumText.setText(ResUtil.getString(R.string.setting_num_value, Setting.getHistoryNum()));
        mBinding.homeColText.setText(getHomeColText());
        mBinding.glassText.setText(getGlassText());
    }

    /**
     * 在候选值里取下一个，找不到当前值就回到第一个。
     */
    private int next(int[] values, int current) {
        for (int i = 0; i < values.length; i++) if (values[i] == current) return values[(i + 1) % values.length];
        return values[0];
    }

    private void setOtherText() {
        mBinding.dohText.setText(getDohList()[getDohIndex()]);
        mBinding.incognitoText.setText(getSwitch(Setting.isIncognito()));
        mBinding.sizeText.setText((size = ResUtil.getStringArray(R.array.select_size))[Setting.getSize()]);
        mBinding.renderText.setText(getRenderText());
        mBinding.playerSelText.setText(getPlayerSelText());
        mBinding.bufferText.setText(ResUtil.getString(R.string.setting_buffer_value, Setting.getBuffer()));
        mBinding.scaleText.setText(getScaleText());
        mBinding.danmakuText.setText(getSwitch(Setting.isDanmakuShow()));
        mBinding.threadText.setText(ResUtil.getString(R.string.setting_thread_value, Setting.getThread()));
        mBinding.historyShowText.setText(getSwitch(Setting.isHistoryShow()));
        mBinding.sourceLockText.setText(getSwitch(Setting.isSourceLock()));
        mBinding.searchUiText.setText(getSearchUiText());
        mBinding.bootText.setText(getSwitch(Setting.getBoot()));
        mBinding.historyNumText.setText(ResUtil.getString(R.string.setting_num_value, Setting.getHistoryNum()));
        mBinding.homeColText.setText(getHomeColText());
        mBinding.glassText.setText(getGlassText());
    }

    private void setCacheText() {
        FileUtil.getCacheSize(new Callback() {
            @Override
            public void success(String result) {
                mBinding.cacheText.setText(result);
            }
        });
    }

    @Override
    protected void initEvent() {
        mBinding.vod.setOnClickListener(this::onVod);
        mBinding.localSource.setOnClickListener(this::onLocalSource);
        mBinding.doh.setOnClickListener(this::setDoh);
        mBinding.live.setOnClickListener(this::onLive);
        mBinding.wall.setOnClickListener(this::onWall);
        mBinding.size.setOnClickListener(this::setSize);
        mBinding.cache.setOnClickListener(this::onCache);
        mBinding.backup.setOnClickListener(this::onBackup);
        mBinding.player.setOnClickListener(this::onPlayer);
        mBinding.restore.setOnClickListener(this::onRestore);
        mBinding.version.setOnClickListener(this::onVersion);
        mBinding.vod.setOnLongClickListener(this::onVodEdit);
        mBinding.vodHome.setOnClickListener(this::onVodHome);
        mBinding.live.setOnLongClickListener(this::onLiveEdit);
        mBinding.liveHome.setOnClickListener(this::onLiveHome);
        mBinding.wall.setOnLongClickListener(this::onWallEdit);
        mBinding.incognito.setOnClickListener(this::setIncognito);
        mBinding.vodHistory.setOnClickListener(this::onVodHistory);
        mBinding.liveHistory.setOnClickListener(this::onLiveHistory);
        mBinding.wallDefault.setOnClickListener(this::setWallDefault);
        mBinding.wallRefresh.setOnClickListener(this::setWallRefresh);
        mBinding.wallRefresh.setOnLongClickListener(this::onWallHistory);
        mBinding.multi.setOnClickListener(this::toggleMulti);
        mBinding.line.setOnClickListener(this::onLine);
        mBinding.playerSel.setOnClickListener(this::setPlayerSel);
        mBinding.boot.setOnClickListener(this::setBoot);
        mBinding.historyNum.setOnClickListener(this::setHistoryNum);
        mBinding.homeCol.setOnClickListener(this::setHomeCol);
        mBinding.glass.setOnClickListener(this::setGlass);
        mBinding.render.setOnClickListener(this::setRender);
        mBinding.buffer.setOnClickListener(this::setBuffer);
        mBinding.scale.setOnClickListener(this::setScale);
        mBinding.danmaku.setOnClickListener(this::setDanmaku);
        mBinding.thread.setOnClickListener(this::setThread);
        mBinding.historyShow.setOnClickListener(this::setHistoryShow);
        mBinding.sourceLock.setOnClickListener(this::setSourceLock);
        mBinding.searchUi.setOnClickListener(this::setSearchUi);
        mBinding.customize.setOnClickListener(v -> showMore(true));
        mBinding.back.setOnClickListener(v -> showMore(false));
        mBinding.crashLog.setOnClickListener(v -> CrashLogDialog.show(this));
    }

    /**
     * 一级页只放常用项，不常用的（DoH/渲染/缓冲/弹幕/列数/玻璃等）收进「个性化设置」，
     * 两页共用同一个布局文件里的两组容器，切换只是显隐，点击逻辑不用重复写。
     */
    private void showMore(boolean show) {
        morePage = show;
        mBinding.pageMain.setVisibility(show ? View.GONE : View.VISIBLE);
        mBinding.pageMore.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            // 崩溃条数进页面时现算：用户可能刚从崩溃界面回来
            mBinding.crashText.setText(ResUtil.getString(R.string.crash_log_count, CrashLog.count()));
            mBinding.doh.requestFocus();
        } else {
            mBinding.vod.requestFocus();
        }
    }

    @Override
    public void onBackPressed() {
        if (morePage) {
            showMore(false);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void setConfig(Config config) {
        if (config.getUrl().startsWith("file")) {
            PermissionUtil.requestFile(this, allGranted -> load(config));
        } else {
            load(config);
        }
    }

    private void load(Config config) {
        switch (config.getType()) {
            case 0:
                VodConfig.load(config, getCallback());
                break;
            case 1:
                LiveConfig.load(config, getCallback());
                break;
            case 2:
                Setting.putWall(0);
                WallConfig.load(config, getCallback());
                break;
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void start() {
                Notify.progress(getActivity());
            }

            @Override
            public void success() {
                Notify.dismiss();
                setCacheText();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
            }
        };
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public void setLive(Live item) {
        LiveConfig.get().setHome(item);
    }

    private void onVod(View view) {
        ConfigDialog.create(this).launcher(launcher).type(type = 0).show();
    }

    // 内置本地源：一键把 assets 里的聚合配置设为当前配置地址并加载，完全离线可用
    private void onLocalSource(View view) {
        Config config = Config.find("assets://local/depot.json", 0);
        load(config);
        Notify.show(R.string.setting_local_source_toast);
    }

    private void onLive(View view) {
        ConfigDialog.create(this).launcher(launcher).type(type = 1).show();
    }

    private void onWall(View view) {
        ConfigDialog.create(this).launcher(launcher).type(type = 2).show();
    }

    private boolean onVodEdit(View view) {
        ConfigDialog.create(this).launcher(launcher).type(type = 0).edit().show();
        return true;
    }

    private boolean onLiveEdit(View view) {
        ConfigDialog.create(this).launcher(launcher).type(type = 1).edit().show();
        return true;
    }

    private boolean onWallEdit(View view) {
        ConfigDialog.create(this).launcher(launcher).type(type = 2).edit().show();
        return true;
    }

    private void onVodHome(View view) {
        SiteDialog.create(this).action().show();
    }

    private void onLiveHome(View view) {
        LiveDialog.create(this).action().show();
    }

    private void onVodHistory(View view) {
        HistoryDialog.create(this).type(type = 0).show();
    }

    private void onLiveHistory(View view) {
        HistoryDialog.create(this).type(type = 1).show();
    }

    private void onPlayer(View view) {
        SettingPlayerActivity.start(this);
    }

    private void onVersion(View view) {
        Updater.create().force().start(this);
    }

    private void setWallDefault(View view) {
        Setting.putWall(Setting.getWall() == 4 ? 1 : Setting.getWall() + 1);
        Setting.putWallType(0);
        ConfigEvent.wall();
    }

    private void setWallRefresh(View view) {
        Setting.putWall(0);
        WallConfig.get().load(getCallback());
    }

    private boolean onWallHistory(View view) {
        HistoryDialog.create(this).type(type = 2).show();
        return true;
    }

    private void setIncognito(View view) {
        Setting.putIncognito(!Setting.isIncognito());
        mBinding.incognitoText.setText(getSwitch(Setting.isIncognito()));
    }

    private void setSize(View view) {
        int index = (Setting.getSize() + 1) % size.length;
        mBinding.sizeText.setText(size[index]);
        Setting.putSize(index);
        RefreshEvent.size();
    }

    private void setDoh(View view) {
        DohDialog.create(this).index(getDohIndex()).show();
    }

    @Override
    public void setDoh(Doh doh) {
        OkHttp.dns().setDoh(doh);
        Setting.putDoh(doh.toString());
        mBinding.dohText.setText(doh.getName());
    }

    private void onCache(View view) {
        FileUtil.clearCache(new Callback() {
            @Override
            public void success() {
                setCacheText();
            }
        });
    }

    private void onBackup(View view) {
        PermissionUtil.requestFile(this, allGranted -> AppDatabase.backup(new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.backup_success);
            }

            @Override
            public void error() {
                Notify.show(R.string.backup_fail);
            }
        }));
    }

    private void onRestore(View view) {
        PermissionUtil.requestFile(this, allGranted -> RestoreDialog.create(getActivity()).show(new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.restore_success);
                setOtherText();
                initConfig();
            }

            @Override
            public void error() {
                Notify.show(R.string.restore_fail);
            }
        }));
    }

    private void initConfig() {
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
        WallConfig.get().init().load();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.type() != ConfigEvent.Type.COMMON) return;
        mBinding.vodUrl.setText(VodConfig.getDesc());
        mBinding.liveUrl.setText(LiveConfig.getDesc());
        mBinding.wallUrl.setText(WallConfig.getDesc());
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        setConfig(Config.find("file:/" + FileChooser.getPathFromUri(result.getData().getData()).replace(Path.rootPath(), ""), type));
    });
}
