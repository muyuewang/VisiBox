package com.visibox.tv.ui.dialog;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.visibox.tv.App;
import com.visibox.tv.R;
import com.visibox.tv.Setting;
import com.visibox.tv.api.HealthChecker;
import com.visibox.tv.api.config.VodConfig;
import com.visibox.tv.bean.Site;
import com.visibox.tv.bean.SiteHealth;
import com.visibox.tv.databinding.DialogSiteBinding;
import com.visibox.tv.impl.SiteCallback;
import com.visibox.tv.ui.adapter.SiteAdapter;
import com.visibox.tv.ui.custom.SpaceItemDecoration;
import com.visibox.tv.utils.Notify;
import com.visibox.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public class SiteDialog implements SiteAdapter.OnClickListener {

    private final DialogSiteBinding binding;
    private final SiteAdapter adapter;
    private final AlertDialog dialog;
    private final int GRID_COUNT = 10;

    private RecyclerView.ItemDecoration decoration;
    private SiteCallback callback;
    private int type;

    public static SiteDialog create(Activity activity) {
        return new SiteDialog(activity);
    }

    public SiteDialog(Activity activity) {
        this.adapter = new SiteAdapter(this);
        this.binding = DialogSiteBinding.inflate(LayoutInflater.from(activity));
        this.dialog = new MaterialAlertDialogBuilder(activity).setView(binding.getRoot()).create();
        if (activity instanceof SiteCallback) this.callback = (SiteCallback) activity;
    }

    public SiteDialog search() {
        type = 1;
        return this;
    }

    public SiteDialog action() {
        binding.action.setVisibility(View.VISIBLE);
        return this;
    }

    public void show() {
        // 没有可选站点时不能静默返回：setDialog() 里会 return，界面毫无反应，
        // 用户只会反复点「主页」却什么都出不来
        if (adapter.getItemCount() == 0) {
            Notify.show(R.string.site_empty_tip);
            return;
        }
        setType(type);
        initView();
        initEvent();
        checkHealth();
    }

    /**
     * 打开站点列表时巡查源可用性，完成后刷新状态点。不阻塞界面。
     *
     * <p>如果历史记录里「测过的源全挂」，多半是配置里那批域名整批失效了，
     * 直接全量重测一遍——否则用户看到的永远是几天前的红点，会以为源再也救不回来。
     */
    private void checkHealth() {
        List<Site> items = adapter.getItems();
        if (HealthChecker.allFailed(items)) {
            recheckAll(items);
            return;
        }
        HealthChecker.autoCheck(items, adapter::refreshHealth);
    }

    /**
     * 全量重测。只给一句开始提示和一句结果提示，中间进度不刷屏。
     */
    private void recheckAll(List<Site> items) {
        Notify.show(ResUtil.getString(R.string.site_check_start, items.size()));
        HealthChecker.check(items, new HealthChecker.Listener() {
            @Override
            public void onStart(int total) {
            }

            @Override
            public void onResult(SiteHealth health, int done, int total) {
            }

            @Override
            public void onFinish(List<SiteHealth> results) {
                App.post(() -> {
                    adapter.refreshHealth();
                    int ok = HealthChecker.countOk(items);
                    // 全挂时直接给「去换配置」，这比「0/12 可用」有用得多
                    if (ok == 0) Notify.show(R.string.site_all_down);
                    else Notify.show(ResUtil.getString(R.string.site_check_done, ok, items.size()));
                });
            }
        });
    }

    /**
     * 长按单个源 = 立刻重测它。红点可能来自几天前的探测，用户需要一个当场验证的手段。
     */
    @Override
    public boolean onItemLongClick(Site item) {
        Notify.show(R.string.site_checking);
        HealthChecker.probeAsync(item, health -> {
            adapter.refreshHealth();
            String message = health.getMessage() == null ? "" : health.getMessage();
            if (health.isOk()) Notify.show(ResUtil.getString(R.string.site_check_one_ok, item.getName(), health.getLatency()));
            else Notify.show(ResUtil.getString(R.string.site_check_one_fail, item.getName(), message));
        });
        return true;
    }

    private boolean list() {
        return Setting.getSiteMode() == 0 || adapter.getItemCount() < GRID_COUNT;
    }

    private int getCount() {
        return list() ? 1 : Math.max(2, Math.min((int) Math.ceil((double) adapter.getItemCount() / GRID_COUNT), 3));
    }

    private int getIcon() {
        return list() ? R.drawable.ic_site_grid : R.drawable.ic_site_list;
    }

    private float getWidth() {
        return 0.4f + (getCount() - 1) * 0.2f;
    }

    private void initView() {
        setRecyclerView();
        setDialog();
        setMode();
    }

    private void initEvent() {
        binding.mode.setOnClickListener(this::setMode);
        binding.select.setOnClickListener(v -> adapter.selectAll());
        binding.cancel.setOnClickListener(v -> adapter.cancelAll());
        binding.search.setOnClickListener(v -> setType(v.isActivated() ? 0 : 1));
        binding.change.setOnClickListener(v -> setType(v.isActivated() ? 0 : 2));
    }

    private void setRecyclerView() {
        binding.recycler.setAdapter(adapter);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setItemAnimator(null);
        if (decoration != null) binding.recycler.removeItemDecoration(decoration);
        binding.recycler.addItemDecoration(decoration = new SpaceItemDecoration(getCount(), 16));
        binding.recycler.setLayoutManager(new GridLayoutManager(dialog.getContext(), getCount()));
        if (!binding.mode.hasFocus()) binding.recycler.post(() -> binding.recycler.scrollToPosition(VodConfig.getHomeIndex()));
    }

    private void setDialog() {
        if (adapter.getItemCount() == 0) return;
        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.width = (int) (ResUtil.getScreenWidth() * getWidth());
        dialog.getWindow().setAttributes(params);
        dialog.getWindow().setDimAmount(0);
        dialog.show();
    }

    private void setMode() {
        if (adapter.getItemCount() < GRID_COUNT) Setting.putSiteMode(0);
        binding.mode.setEnabled(adapter.getItemCount() >= GRID_COUNT);
        binding.mode.setImageResource(getIcon());
    }

    private void setType(int type) {
        binding.search.setActivated(type == 1);
        binding.change.setActivated(type == 2);
        binding.select.setClickable(type > 0);
        binding.cancel.setClickable(type > 0);
        adapter.setType(this.type = type);
    }

    private void setMode(View view) {
        Setting.putSiteMode(Math.abs(Setting.getSiteMode() - 1));
        initView();
    }

    @Override
    public void onItemClick(Site item) {
        if (callback == null) return;
        callback.setSite(item);
        dialog.dismiss();
    }
}
