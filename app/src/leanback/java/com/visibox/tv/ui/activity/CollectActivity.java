package com.visibox.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.visibox.tv.App;
import com.visibox.tv.R;
import com.visibox.tv.Setting;
import com.visibox.tv.api.config.VodConfig;
import com.visibox.tv.bean.Collect;
import com.visibox.tv.bean.Site;
import com.visibox.tv.bean.Vod;
import com.visibox.tv.databinding.ActivityCollectBinding;
import com.visibox.tv.model.SiteViewModel;
import com.visibox.tv.ui.adapter.CollectAdapter;
import com.visibox.tv.ui.base.BaseActivity;
import com.visibox.tv.ui.fragment.CollectFragment;
import com.visibox.tv.utils.ResUtil;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

public class CollectActivity extends BaseActivity {

    private static final String TAG = "CollectDebug";

    private ActivityCollectBinding mBinding;
    private CollectAdapter mAdapter;
    private SiteViewModel mViewModel;
    private List<Site> mSites;
    private View mOldView;
    private final SparseArray<CollectFragment> mFragments = new SparseArray<>();
    /** 「全部」标签的累计结果，Fragment 重建时用它回填，避免聚合结果丢失 */
    private final List<Vod> mAllItems = new ArrayList<>();
    private boolean mAlive;

    public static void start(Activity activity, String keyword) {
        Intent intent = new Intent(activity, CollectActivity.class);
        intent.putExtra("keyword", keyword);
        activity.startActivity(intent);
    }

    @Nullable
    private CollectFragment getFragment(int position) {
        return mFragments.get(position);
    }

    @Nullable
    private CollectFragment getAllFragment() {
        return getFragment(0);
    }

    private String getKeyword() {
        return getIntent().getStringExtra("keyword");
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityCollectBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        getIntent().putExtras(intent);
        App.removeCallbacks(mRunnable);
        mViewModel.stopSearch();
        clearFragments();
        mAllItems.clear();
        mAdapter.clear();
        setPager();
        search();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mAlive = true;
        setRecyclerView();
        setViewModel();
        saveKeyword();
        setSites();
        setPager();
        search();
    }

    @Override
    protected void initEvent() {
        mAdapter.setListener(position -> {
            if (!mAlive || position == RecyclerView.NO_POSITION) return;
            if (position < 0 || position >= mAdapter.getItemCount()) return;
            if (position == mBinding.pager.getCurrentItem()) return;
            App.removeCallbacks(mRunnable);
            mBinding.recycler.setSelectedPosition(position);
            setPage(position);
        });
        mBinding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                if (mBinding.recycler.getSelectedPosition() == position) return;
                mBinding.recycler.setSelectedPosition(position);
                mBinding.recycler.requestFocus();
            }
        });
        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                onChildSelected(child, position);
            }
        });
    }

    private void setRecyclerView() {
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(8));
        mBinding.recycler.setAdapter(mAdapter = new CollectAdapter());
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getSearch().observe(this, result -> {
            if (!mAlive || result == null || result.getList().isEmpty()) return;
            mAllItems.addAll(result.getList());
            CollectFragment fragment = getAllFragment();
            if (fragment != null) fragment.addVideo(result.getList());
            addCollect(Collect.create(result.getList()));
        });
    }

    /** 单个 tab 的数据更新失败不能拖垮整个页面；同一站点只建一个标签 */
    private void addCollect(Collect item) {
        try {
            if (mAdapter.contains(item)) return;
            mAdapter.add(item);
        } catch (Exception e) {
            Log.e(TAG, "addCollect failed", e);
            return;
        }
        PagerAdapter adapter = mBinding.pager.getAdapter();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void saveKeyword() {
        List<String> items = Setting.getKeyword().isEmpty() ? new ArrayList<>() : App.gson().fromJson(Setting.getKeyword(), new TypeToken<List<String>>() {}.getType());
        items.remove(getKeyword());
        items.add(0, getKeyword());
        if (items.size() > 9) items.remove(9);
        Setting.putKeyword(App.gson().toJson(items));
    }

    private void setSites() {
        List<Site> all = VodConfig.get().getSites();
        mSites = all.stream().filter(Site::isSearchable).toList();
        // 兜底：部分订阅把 searchable 全部标 0，过滤后变死页，此时回退为全部站点
        if (mSites.isEmpty() && !all.isEmpty()) mSites = all;
        Log.d(TAG, "setSites: all=" + all.size() + " searchable=" + mSites.size());
    }

    private void clearFragments() {
        FragmentManager fm = getSupportFragmentManager();
        for (int i = 0; i < mFragments.size(); i++) {
            CollectFragment fragment = mFragments.valueAt(i);
            if (fragment != null && fragment.isAdded()) fm.beginTransaction().remove(fragment).commitAllowingStateLoss();
        }
        mFragments.clear();
        if (mOldView != null) mOldView.setActivated(false);
        mOldView = null;
    }

    private void setPager() {
        mBinding.pager.setAdapter(new PageAdapter(getSupportFragmentManager()));
    }

    private void search() {
        mAllItems.clear();
        mAdapter.clear();
        // 先放「全部」标签，保证页面在任何情况下都不是白板
        addCollect(Collect.all());
        if (mSites.isEmpty()) {
            Log.w(TAG, "search aborted: no sites, keyword=" + getKeyword());
            mBinding.result.setText(getString(R.string.collect_no_site, getKeyword()));
            return;
        }
        mBinding.result.setText(getString(R.string.collect_result, getKeyword()));
        mViewModel.searchContent(mSites, getKeyword(), false);
    }

    private void onChildSelected(@Nullable RecyclerView.ViewHolder child, int position) {
        if (mOldView != null) mOldView.setActivated(false);
        if ((mOldView = child != null ? child.itemView : null) == null) return;
        mOldView.setActivated(true);
        Collect item = mAdapter.get(position);
        if (item != null) item.setActivated(true);
        if (!mAlive || position < 0 || position >= mAdapter.getItemCount()) return;
        if (position == mBinding.pager.getCurrentItem()) return;
        App.removeCallbacks(mRunnable);
        App.post(mRunnable, 100);
    }

    private void setPage(int position) {
        if (!mAlive) return;
        if (position < 0 || position >= mAdapter.getItemCount()) return;
        if (position == mBinding.pager.getCurrentItem()) return;
        try {
            mBinding.pager.setCurrentItem(position);
        } catch (Exception ignored) {
        }
    }

    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            setPage(mBinding.recycler.getSelectedPosition());
        }
    };

    @Override
    protected void onBackInvoked() {
        mAlive = false;
        App.removeCallbacks(mRunnable);
        mViewModel.stopSearch();
        super.onBackInvoked();
    }

    @Override
    protected void onDestroy() {
        mAlive = false;
        App.removeCallbacks(mRunnable);
        if (mViewModel != null) {
            mViewModel.stopSearch();
            mViewModel.getSearch().removeObservers(this);
        }
        clearFragments();
        super.onDestroy();
    }

    class PageAdapter extends FragmentStatePagerAdapter {

        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            if (position == 0) {
                // 「全部」用累计结果重建，Fragment 被重建时也不会丢数据
                Collect all = Collect.all();
                all.setList(new ArrayList<>(mAllItems));
                return CollectFragment.newInstance(getKeyword(), all);
            }
            return CollectFragment.newInstance(getKeyword(), mAdapter.get(position));
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            Fragment fragment = (Fragment) super.instantiateItem(container, position);
            if (fragment instanceof CollectFragment collect) mFragments.put(position, collect);
            return fragment;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            if (object instanceof CollectFragment) mFragments.remove(position);
            super.destroyItem(container, position, object);
        }

        @Override
        public int getCount() {
            return mAdapter.getItemCount();
        }

        @Override
        public int getItemPosition(@NonNull Object object) {
            // 保留已存在的 Fragment（含已加载的海报），只有新增标签才创建
            return POSITION_UNCHANGED;
        }

        @Nullable
        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public void restoreState(@Nullable Parcelable state, @Nullable ClassLoader loader) {
        }
    }
}
