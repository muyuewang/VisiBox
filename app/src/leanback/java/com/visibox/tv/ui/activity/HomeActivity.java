package com.visibox.tv.ui.activity;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.splashscreen.SplashScreen;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.viewbinding.ViewBinding;

import com.visibox.tv.App;
import com.visibox.tv.Product;
import com.visibox.tv.R;
import com.visibox.tv.Setting;
import com.visibox.tv.Updater;
import com.visibox.tv.api.HealthChecker;
import com.visibox.tv.api.config.LiveConfig;
import com.visibox.tv.api.config.VodConfig;
import com.visibox.tv.api.config.WallConfig;
import com.visibox.tv.bean.Cache;
import com.visibox.tv.bean.Class;
import com.visibox.tv.bean.Config;
import com.visibox.tv.bean.Func;
import com.visibox.tv.bean.History;
import com.visibox.tv.bean.Result;
import com.visibox.tv.bean.Site;
import com.visibox.tv.bean.SiteHealth;
import com.visibox.tv.bean.Style;
import com.visibox.tv.bean.Vod;
import com.visibox.tv.databinding.ActivityHomeBinding;
import com.visibox.tv.db.AppDatabase;
import com.visibox.tv.event.CastEvent;
import com.visibox.tv.event.ConfigEvent;
import com.visibox.tv.event.RefreshEvent;
import com.visibox.tv.event.ServerEvent;
import com.visibox.tv.impl.Callback;
import com.visibox.tv.impl.ConfigCallback;
import com.visibox.tv.impl.SiteCallback;
import com.visibox.tv.model.SiteViewModel;
import com.visibox.tv.player.Source;
import com.visibox.tv.server.Server;
import com.visibox.tv.service.DLNARendererService;
import com.visibox.tv.service.PlaybackService;
import com.visibox.tv.ui.adapter.BaseDiffCallback;
import com.visibox.tv.ui.base.BaseActivity;
import com.visibox.tv.ui.custom.CustomRowPresenter;
import com.visibox.tv.ui.custom.GlassSheenDrawable;
import com.visibox.tv.ui.custom.CustomSelector;
import com.visibox.tv.ui.custom.CustomTitleView;
import com.visibox.tv.ui.dialog.ConfigDialog;
import com.visibox.tv.ui.dialog.HistoryDialog;
import com.visibox.tv.ui.dialog.SiteDialog;
import com.visibox.tv.ui.presenter.FuncPresenter;
import com.visibox.tv.ui.presenter.HeaderPresenter;
import com.visibox.tv.ui.presenter.HistoryPresenter;
import com.visibox.tv.ui.presenter.ProgressPresenter;
import com.visibox.tv.ui.presenter.VodPresenter;
import com.visibox.tv.utils.Clock;
import com.visibox.tv.utils.FileChooser;
import com.visibox.tv.utils.ImgUtil;
import com.visibox.tv.utils.KeyUtil;
import com.visibox.tv.utils.Notify;
import com.visibox.tv.utils.PermissionUtil;
import com.visibox.tv.utils.ResUtil;
import com.visibox.tv.utils.UrlUtil;
import com.github.catvod.net.OkHttp;
import com.google.common.collect.Lists;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class HomeActivity extends BaseActivity implements CustomTitleView.Listener, ConfigCallback, SiteCallback, VodPresenter.OnClickListener, FuncPresenter.OnClickListener, HistoryPresenter.OnClickListener {

    private ActivityHomeBinding mBinding;
    private ArrayObjectAdapter mHistoryAdapter;
    private ArrayObjectAdapter mFuncAdapter;
    private ArrayObjectAdapter mAdapter;
    private HistoryPresenter mPresenter;
    private SiteViewModel mViewModel;
    private Result mResult;
    private Clock mClock;
    private boolean fellBack;
    private boolean categoryMode;
    private boolean categoryScrollPending;
    private String currentType = "";
    // 当前选中的分类胶囊；用对象引用比对，避免 typeId 重复时整排一起亮
    private TextView mSelectedChip;

    // 首页「配置」里的「选择」按钮要选本地配置文件；原来没把 launcher 传进去，点了就 NPE
    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        String path = "file:/" + FileChooser.getPathFromUri(result.getData().getData()).replace(com.github.catvod.utils.Path.rootPath(), "");
        setConfig(Config.find(path, 0));
    });

    private Site getHome() {
        return VodConfig.get().getHome();
    }

    private Config getConfig() {
        return VodConfig.get().getConfig();
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHomeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        checkAction(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mResult = Result.empty();
        mClock = Clock.create(mBinding.clock);
        mBinding.progressLayout.showProgress();
        DLNARendererService.start(this);
        Updater.create().start(this);
        setRecyclerView();
        setViewModel();
        setAdapter();
        initConfig();
        setTitle();
        setLogo();
    }

    @Override
    protected void initEvent() {
        mBinding.title.setListener(this);
        new GlassSheenDrawable(24).attach(mBinding.home);
        mBinding.home.setOnClickListener(v -> {
            // 一次点击就出源选择：原来分类模式下第一次点击只是回首页并 return，
            // 想换源得再点一次，加上焦点要先挪上来，体感就是「点好多下才出来」
            if (categoryMode) getVideo();
            showSiteDialog();
        });
        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mPresenter.isDelete()) setHistoryDelete(false);
            }
        });
    }

    private void checkAction(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            VideoActivity.push(this, intent.getStringExtra(Intent.EXTRA_TEXT));
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            PermissionUtil.requestFile(this, allGranted -> checkType(intent));
        } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String keyword = intent.getStringExtra(SearchManager.QUERY);
            if (!TextUtils.isEmpty(keyword)) SearchActivity.start(this, keyword);
        }
    }

    private void checkType(Intent intent) {
        if ("text/plain".equals(intent.getType()) || UrlUtil.path(intent.getData()).endsWith(".m3u")) {
            loadLive("file:/" + FileChooser.getPathFromUri(intent.getData()));
        } else {
            VideoActivity.push(this, intent.getData().toString());
        }
    }

    @SuppressLint("RestrictedApi")
    private void setRecyclerView() {
        CustomSelector selector = new CustomSelector();
        selector.addPresenter(Integer.class, new HeaderPresenter());
        selector.addPresenter(String.class, new ProgressPresenter());
        selector.addPresenter(Vod.class, new VodPresenter(this, Style.list()));
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), VodPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16, FocusHighlight.ZOOM_FACTOR_SMALL, HorizontalGridView.FOCUS_SCROLL_ALIGNED), HistoryPresenter.class);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(16));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(this, result -> {
            mAdapter.remove("progress");
            setCategories(result.getTypes());
            addVideo(mResult = result);
            Cache.clear().put(result);
            // 进入分类/换源后内容落在首屏之外，定位一次即可；
            // 后续加载（分页等）不能回滚，否则用户正在下滑会被拽回顶部
            if (categoryScrollPending) {
                categoryScrollPending = false;
                App.post(() -> {
                    int position = mAdapter.indexOf(R.string.home_recommend);
                    if (position == -1) position = 0;
                    mBinding.recycler.scrollToPosition(position);
                    if (categoryMode) {
                        mBinding.recycler.requestFocus();
                        mBinding.recycler.setSelectedPosition(position);
                    }
                }, 300);
            }
        });
    }

    private void setAdapter() {
        mHistoryAdapter = new ArrayObjectAdapter(mPresenter = new HistoryPresenter(this));
        // 功能按钮行固定在顶栏，不随内容列表隐藏，空源时也能操作
        mBinding.funcRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        mBinding.funcRecycler.setAdapter(new ItemBridgeAdapter(mFuncAdapter = new ArrayObjectAdapter(new FuncPresenter(this))));
        // 启动即填充：原来只在 ConfigEvent.COMMON 里调 setFunc()，而该事件仅在「网络异步加载完成」时发出，
        // 走本地缓存启动的路径永远不会触发，导致搜索/配置/线路/设置整行空白，源坏了也没入口换源
        setFunc();
        // 启动即选中「主页」：分类未选时它就是当前项，胶囊是青绿实底
        mBinding.home.setSelected(true);
        mAdapter.add(R.string.home_history);
        mAdapter.add(R.string.home_recommend);
    }

    /**
     * 首页「历史」：定位到最近观看区。分类模式下该区被移除，提示先按主页返回。
     */
    private void gotoHistory() {
        if (History.get().isEmpty() || mAdapter.indexOf(R.string.home_history) == -1) {
            Notify.show(categoryMode ? R.string.home_back_tip : R.string.home_history_empty);
            return;
        }
        int position = getHistoryIndex();
        mBinding.recycler.scrollToPosition(position);
        mBinding.recycler.requestFocus();
        mBinding.recycler.setSelectedPosition(position);
    }

    /**
     * 在「主页」右侧展示当前源的分类。内容不变则不重建，避免每次加载都闪一下。
     */
    private void setCategories(List<Class> types) {
        if (types == null || types.isEmpty()) return;
        if (types.size() == mBinding.categories.getChildCount()) {
            boolean same = true;
            for (int i = 0; i < types.size(); i++) {
                TextView chip = (TextView) mBinding.categories.getChildAt(i);
                if (!types.get(i).getTypeName().contentEquals(chip.getText())) {
                    same = false;
                    break;
                }
            }
            if (same) return;
        }
        mBinding.categories.removeAllViews();
        for (int i = 0; i < types.size(); i++) {
            Class type = types.get(i);
            int index = i;
            TextView chip = new TextView(this);
            chip.setText(type.getTypeName());
            chip.setTextSize(16);
            chip.setTextColor(androidx.core.content.ContextCompat.getColorStateList(this, R.color.pill_text));
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setPadding(ResUtil.dp2px(16), ResUtil.dp2px(6), ResUtil.dp2px(16), ResUtil.dp2px(6));
            chip.setBackgroundResource(R.drawable.selector_home_pill);
            chip.setFocusable(true);
            // 记下自己的 typeId，markCategory 靠它判断选中，别用入参互相比较
            chip.setTag(type.getTypeId());
            if (!currentType.isEmpty() && type.getTypeId().equals(currentType)) mSelectedChip = chip;
            chip.setSelected(chip == mSelectedChip);
            chip.setOnClickListener(v -> {
                mSelectedChip = chip;
                loadCategory(type, index);
            });
            new GlassSheenDrawable(24).attach(chip);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(ResUtil.dp2px(8));
            mBinding.categories.addView(chip, params);
        }
    }

    /**
     * 点分类：清掉推荐区，按分类重新拉取。
     */
    private void markCategory(String typeId) {
        // 只让「当前点中的那一个」是选中态。之前拿入参和自己比，恒为 true，
        // 一点分类整排胶囊全变选中色（用户看到的就是「未选中的都是绿的」）
        for (int i = 0; i < mBinding.categories.getChildCount(); i++) {
            View child = mBinding.categories.getChildAt(i);
            child.setSelected(child == mSelectedChip);
        }
    }

    private void loadCategory(Class type, int index) {
        categoryMode = true;
        categoryScrollPending = true;
        currentType = type.getTypeId();
        // 进了分类，「主页」不再是当前项；选中色只落在被点的那个分组上
        mBinding.home.setSelected(false);
        markCategory(currentType);
        mResult = Result.empty();
        // 分类模式只展示海报墙：最近观看、更新推荐一并移除
        int from = mAdapter.indexOf(R.string.home_history);
        if (from == -1) from = 0;
        if (mAdapter.size() > from) mAdapter.removeItems(from, mAdapter.size() - from);
        // 分类模式对齐点播页：顶栏与功能行都收起，让分类 Tab + 海报墙铺满
        // （换源/设置走「主页」：一次点击即先回首页再弹源选择，不会摸不到入口）
        mBinding.toolbar.setVisibility(View.GONE);
        mBinding.funcRecycler.setVisibility(View.GONE);
        mBinding.progressLayout.showProgress();
        mViewModel.categoryContent(getHome().getKey(), type.getTypeId(), "1", true, new HashMap<>());
    }

    private void setTitle() {
        List<String> items = Arrays.asList(getHome().getName(), getConfig().getName(), getString(R.string.app_name));
        Optional<String> optional = items.stream().filter(s -> !TextUtils.isEmpty(s)).findFirst();
        optional.ifPresent(s -> mBinding.title.setText(s));
        setNotice();
    }

    /**
     * 顶部滚动公告取配置里的 notice 字段，没配就用默认文案。
     */
    private void setNotice() {
        String notice = getConfig().getNotice();
        mBinding.notice.setText(TextUtils.isEmpty(notice) ? getString(R.string.home_notice) : notice);
        mBinding.notice.setSelected(true);
    }

    /**
     * 首页的「配置」按钮直接弹配置地址框，保存后在此加载对应配置。
     */
    @Override
    public void setConfig(Config config) {
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

    private void initConfig() {
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
        WallConfig.get().init();
        // 兜底：订阅站不可达时不会无限转圈，20 秒后先放行界面（配置若稍后到达会自动刷新）
        // 放行时必须给可操作提示：空白页 + 无提示会让用户以为 App 坏了，也不知道去哪换源
        App.post(() -> {
            if (isFinishing() || !mBinding.progressLayout.isProgress()) return;
            mBinding.progressLayout.setEmptyText(getString(R.string.home_timeout_tip));
            mBinding.progressLayout.showEmpty();
            mBinding.home.requestFocus();
        }, 20000);
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                showContent();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
                showContent();
            }
        };
    }

    private void showContent() {
        mBinding.progressLayout.showContent();
        checkAction(getIntent());
        setFocus();
    }

    private void loadLive(String url) {
        LiveConfig.load(Config.find(url, 1), new Callback() {
            @Override
            public void success() {
                LiveActivity.start(getActivity());
            }
        });
    }

    private void setFocus() {
        mBinding.title.setSelected(true);
        App.post(() -> mBinding.title.setFocusable(true), 500);
        if (!mBinding.title.hasFocus()) mBinding.recycler.requestFocus();
    }

    private void getVideo() {
        fellBack = false;
        categoryMode = false;
        categoryScrollPending = true;
        currentType = "";
        // 回到首页 = 「主页」是当前选中项，胶囊保持青绿实底
        mBinding.home.setSelected(true);
        // 分类胶囊同步清掉选中色，否则回首页后还留着上一轮的高亮
        mSelectedChip = null;
        markCategory(currentType);
        mResult = Result.empty();
        mBinding.toolbar.setVisibility(View.VISIBLE);
        mBinding.funcRecycler.setVisibility(View.VISIBLE);
        // 从分类模式退出时标题被移除过，这里补回结构
        if (mAdapter.indexOf(R.string.home_recommend) == -1) mAdapter.add(R.string.home_recommend);
        if (mAdapter.indexOf(R.string.home_history) == -1) mAdapter.add(0, R.string.home_history);
        int index = getRecommendIndex();
        boolean gone = mAdapter.indexOf("progress") == -1;
        boolean hasItem = gone && mAdapter.size() > index;
        if (hasItem) mAdapter.removeItems(index, mAdapter.size() - index);
        if (gone) mAdapter.add("progress");
        getHistory();
        mViewModel.homeContent();
    }

    private void addVideo(Result result) {
        Style style = result.getStyle(getHome().getStyle());
        if (style.isList()) mAdapter.addAll(mAdapter.size(), result.getList());
        else addGrid(result.getList(), style);
        if (!result.getList().isEmpty()) {
            mBinding.progressLayout.showContent();
        } else {
            onEmptyHome(result);
        }
    }

    /**
     * 首页没有推荐内容时的兜底：
     * 1. 该源有分类（片单/网盘类常见）→ 自动加载第一个分类，避免整屏空白
     * 2. 连分类都没有 → 显示提示，引导用户走「主页」浏览
     */
    private void onEmptyHome(Result result) {
        if (!fellBack && !categoryMode && !result.getTypes().isEmpty()) {
            fellBack = true;
            Class type = result.getTypes().get(0);
            currentType = type.getTypeId();
            markCategory(currentType);
            categoryScrollPending = true;
            mViewModel.categoryContent(getHome().getKey(), type.getTypeId(), "1", true, new HashMap<>());
            return;
        }
        mBinding.progressLayout.setEmptyText(emptyTip());
        mBinding.progressLayout.showEmpty();
        mBinding.home.requestFocus();
    }

    /**
     * 首页没有内容时的提示文案。
     *
     * <p>「无推荐内容」和「源根本连不上」对用户是两件事：前者换分类看，后者必须换源。
     * 有健康记录且明确失败时，直接把失败原因写出来，比让用户猜强。
     */
    private String emptyTip() {
        Site site = getHome();
        SiteHealth health = site == null ? null : HealthChecker.healthOf(site.getKey());
        if (health != null && health.isChecked() && !health.isOk()) {
            String reason = health.getMessage() == null ? "" : health.getMessage();
            if (!reason.isEmpty()) return ResUtil.getString(R.string.home_site_down_tip, reason);
        }
        return getString(R.string.home_empty_tip);
    }

    private void addGrid(List<Vod> items, Style style) {
        List<ListRow> rows = new ArrayList<>();
        VodPresenter presenter = new VodPresenter(this, style);
        for (List<Vod> part : Lists.partition(items, Product.getColumn(style))) {
            ArrayObjectAdapter adapter = new ArrayObjectAdapter(presenter);
            adapter.addAll(0, part);
            rows.add(new ListRow(adapter));
        }
        mAdapter.addAll(mAdapter.size(), rows);
    }

    private void setFunc() {
        List<Func> items = new ArrayList<>();
        items.add(Func.create(R.string.home_history_btn));
        if (LiveConfig.hasUrl()) items.add(Func.create(R.string.home_live));
        items.add(Func.create(R.string.home_search));
        items.add(Func.create(R.string.home_config));
        items.add(Func.create(R.string.home_line));
        items.add(Func.create(R.string.home_local));
        items.add(Func.create(R.string.home_keep));
        items.add(Func.create(R.string.home_push));
        items.add(Func.create(R.string.home_setting));
        mFuncAdapter.setItems(items, new BaseDiffCallback<Func>());
    }

    private void getHistory() {
        getHistory(false);
    }

    private void getHistory(boolean renew) {
        // 分类模式下历史区已整体移除，此时插入会打乱结构并把视图拽回顶部；
        // 挂起处理，退出分类（getVideo）时再恢复
        if (categoryMode) return;
        List<History> items = History.get();
        int historyIndex = getHistoryIndex();
        int recommendIndex = getRecommendIndex();
        boolean exist = recommendIndex - historyIndex == 2;
        if (renew) mHistoryAdapter = new ArrayObjectAdapter(mPresenter = new HistoryPresenter(this));
        if ((items.isEmpty() && exist) || (renew && exist)) mAdapter.removeItems(historyIndex, 1);
        if ((!items.isEmpty() && !exist) || (renew && exist)) mAdapter.add(historyIndex, new ListRow(mHistoryAdapter));
        mHistoryAdapter.setItems(items, new BaseDiffCallback<History>());
    }

    private void setHistoryDelete(boolean delete) {
        mPresenter.setDelete(delete);
        mHistoryAdapter.notifyArrayItemRangeChanged(0, mHistoryAdapter.size());
    }

    private void clearHistory() {
        mAdapter.removeItems(getHistoryIndex(), 1);
        History.delete(VodConfig.getCid());
        mPresenter.setDelete(false);
        mHistoryAdapter.clear();
    }

    private int getHistoryIndex() {
        return mAdapter.indexOf(R.string.home_history) + 1;
    }

    private int getRecommendIndex() {
        return mAdapter.indexOf(R.string.home_recommend) + 1;
    }

    private void setLogo() {
        ImgUtil.logo(mBinding.logo);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        switch (event.type()) {
            case VOD:
                RefreshEvent.history();
                RefreshEvent.home();
                setLogo();
                break;
            case COMMON:
                setFunc();
                break;
            case BOOT:
                LiveActivity.start(this);
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case HOME:
                getVideo();
                setTitle();
                break;
            case HISTORY:
                getHistory();
                break;
            case SIZE:
                getVideo();
                getHistory(true);
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        switch (event.type()) {
            case SEARCH:
                SearchActivity.start(this, event.text());
                break;
            case PUSH:
                VideoActivity.push(this, event.text());
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        if (VodConfig.get().getConfig().equals(event.config())) {
            VideoActivity.cast(this, event.history().save(VodConfig.getCid()));
        } else {
            VodConfig.load(event.config(), getCallback(event));
        }
    }

    private Callback getCallback(CastEvent event) {
        return new Callback() {
            @Override
            public void success() {
                onCastEvent(event);
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }

    @Override
    public void onItemClick(Func item) {
        if (item.getResId() == R.string.home_vod) VodActivity.start(this, mResult);
        else if (item.getResId() == R.string.home_live) LiveActivity.start(this);
        else if (item.getResId() == R.string.home_keep) KeepActivity.start(this);
        else if (item.getResId() == R.string.home_push) PushActivity.start(this);
        else if (item.getResId() == R.string.home_search) SearchActivity.start(this);
        else if (item.getResId() == R.string.home_setting) SettingActivity.start(this);
        else if (item.getResId() == R.string.home_history_btn) gotoHistory();
        else if (item.getResId() == R.string.home_config) ConfigDialog.create(this).launcher(launcher).type(0).show();
        else if (item.getResId() == R.string.home_line) HistoryDialog.create(this).show();
        else if (item.getResId() == R.string.home_local) startActivity(new Intent(this, FileActivity.class));
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isAction()) mViewModel.action(getHome().getKey(), item.getAction());
        else if (getHome().isIndex()) CollectActivity.start(this, item.getName());
        else VideoActivity.start(this, getHome().getKey(), item.getId(), item.getName(), item.getPic());
    }

    @Override
    public boolean onLongClick(Vod item) {
        if (item.isAction()) return false;
        CollectActivity.start(this, item.getName());
        return true;
    }

    @Override
    public void onItemClick(History item) {
        VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(History item) {
        mHistoryAdapter.remove(item.delete());
        if (mHistoryAdapter.size() > 0) return;
        mAdapter.removeItems(getHistoryIndex(), 1);
        mPresenter.setDelete(false);
    }

    @Override
    public boolean onLongClick() {
        if (mPresenter.isDelete()) clearHistory();
        else setHistoryDelete(true);
        return true;
    }

    @Override
    public void showDialog() {
        showSiteDialog();
    }

    /**
     * 站点选择：接口没加载出站点（源挂了 / 站点全被隐藏）时 SiteDialog 会静默什么都不做，
     * 用户就会一直点「主页」也不出东西。这里改成直接给「配置」入口，并说明原因。
     */
    private void showSiteDialog() {
        boolean hasSite = false;
        for (Site site : VodConfig.get().getSites()) {
            if (!site.isHide()) {
                hasSite = true;
                break;
            }
        }
        if (!hasSite) {
            Notify.show(R.string.site_empty_tip);
            ConfigDialog.create(this).launcher(launcher).type(0).show();
            return;
        }
        SiteDialog.create(this).show();
    }

    @Override
    public void onRefresh() {
        getVideo();
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public void onBackPressed() {
        // 分类浏览中按返回回到首页视图，而不是退出应用
        if (categoryMode) {
            getVideo();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event)) showDialog();
        if (KeyUtil.isActionDown(event) & KeyUtil.isDownKey(event) && getCurrentFocus() == mBinding.title) return mBinding.recycler.getChildAt(0).requestFocus();
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mClock.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mClock.stop();
    }

    @Override
    protected void onBackInvoked() {
        if (mBinding.progressLayout.isProgress()) {
            showContent();
        } else if (mPresenter.isDelete()) {
            setHistoryDelete(false);
        } else if (mBinding.recycler.getSelectedPosition() != 0) {
            mBinding.recycler.scrollToPosition(0);
        } else {
            if (PlaybackService.isRunning()) moveTaskToBack(true);
            else super.onBackInvoked();
        }
    }

    @Override
    protected void onDestroy() {
        DLNARendererService.stop(this);
        LiveConfig.get().clear();
        VodConfig.get().clear();
        AppDatabase.backup();
        OkHttp.get().clear();
        Source.get().exit();
        Server.get().stop();
        super.onDestroy();
    }
}