package com.visibox.tv.ui.dialog;

import android.app.Activity;
import android.view.LayoutInflater;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.visibox.tv.api.HealthChecker;
import com.visibox.tv.api.config.VodConfig;
import com.visibox.tv.bean.Site;
import com.visibox.tv.databinding.DialogSiteBinding;
import com.visibox.tv.impl.SiteCallback;
import com.visibox.tv.ui.adapter.SiteAdapter;
import com.visibox.tv.ui.custom.SpaceItemDecoration;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SiteDialog implements SiteAdapter.OnClickListener {

    private DialogSiteBinding binding;
    private SiteCallback callback;
    private SiteAdapter adapter;
    private AlertDialog dialog;

    public static SiteDialog create(Activity activity) {
        return new SiteDialog(activity);
    }

    public static SiteDialog create(Fragment fragment) {
        return new SiteDialog(fragment);
    }

    public SiteDialog(Activity activity) {
        if (activity instanceof SiteCallback) this.callback = (SiteCallback) activity;
        init(activity);
    }

    public SiteDialog(Fragment fragment) {
        if (fragment instanceof SiteCallback) this.callback = (SiteCallback) fragment;
        init(fragment.requireActivity());
    }

    private void init(Activity activity) {
        this.binding = DialogSiteBinding.inflate(LayoutInflater.from(activity));
        this.dialog = new MaterialAlertDialogBuilder(activity).setView(binding.getRoot()).create();
        this.adapter = new SiteAdapter(this);
    }

    public SiteDialog search() {
        this.adapter.search(true);
        return this;
    }

    public SiteDialog change() {
        this.adapter.change(true);
        return this;
    }

    public SiteDialog all() {
        this.adapter.search(true);
        this.adapter.change(true);
        return this;
    }

    public void show() {
        setRecyclerView();
        setDialog();
        checkHealth();
    }

    /**
     * 打开站点列表时静默巡查过期源，完成后刷新状态点。不阻塞界面。
     */
    private void checkHealth() {
        HealthChecker.autoCheck(adapter.getItems(), adapter::refreshHealth);
    }

    private void setRecyclerView() {
        binding.recycler.setAdapter(adapter);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 8));
        binding.recycler.post(() -> binding.recycler.scrollToPosition(VodConfig.getHomeIndex()));
    }

    private void setDialog() {
        if (adapter.getItemCount() == 0) return;
        dialog.getWindow().setDimAmount(0);
        dialog.show();
    }

    @Override
    public void onTextClick(Site item) {
        if (callback == null) return;
        callback.setSite(item);
        dialog.dismiss();
    }

    @Override
    public void onSearchClick(int position, Site item) {
        item.setSearchable(!item.isSearchable()).save();
        adapter.notifyItemChanged(position);
    }

    @Override
    public void onChangeClick(int position, Site item) {
        item.setChangeable(!item.isChangeable()).save();
        adapter.notifyItemChanged(position);
    }

    @Override
    public boolean onSearchLongClick(Site item) {
        boolean result = !item.isSearchable();
        for (Site site : adapter.getItems()) site.setSearchable(result).save();
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        return true;
    }

    @Override
    public boolean onChangeLongClick(Site item) {
        boolean result = !item.isChangeable();
        for (Site site : adapter.getItems()) site.setChangeable(result).save();
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        return true;
    }
}
