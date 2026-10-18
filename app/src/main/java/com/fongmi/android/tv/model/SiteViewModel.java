package com.fongmi.android.tv.model;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.api.HealthChecker;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.exception.ExtractException;
import com.fongmi.android.tv.utils.SearchDedup;
import com.fongmi.android.tv.utils.Task;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SiteViewModel extends ViewModel {

    private final MutableLiveData<Result> result;
    private final MutableLiveData<Result> player;
    private final MutableLiveData<Result> search;
    private final MutableLiveData<Result> action;

    private final Map<TaskType, ListenableFuture<?>> futures;
    private final Map<TaskType, AtomicInteger> taskIds;
    private final List<Future<?>> searchFuture;
    private final AtomicInteger searchEpoch;
    private final Set<String> searchSeen;

    public SiteViewModel() {
        result = new MutableLiveData<>();
        player = new MutableLiveData<>();
        search = new MutableLiveData<>();
        action = new MutableLiveData<>();
        searchEpoch = new AtomicInteger(0);
        searchSeen = ConcurrentHashMap.newKeySet();
        searchFuture = new CopyOnWriteArrayList<>();
        futures = new EnumMap<>(TaskType.class);
        taskIds = new EnumMap<>(TaskType.class);
        for (TaskType type : TaskType.values()) taskIds.put(type, new AtomicInteger(0));
    }

    public LiveData<Result> getResult() {
        return result;
    }

    public LiveData<Result> getPlayer() {
        return player;
    }

    public LiveData<Result> getSearch() {
        return search;
    }

    public LiveData<Result> getAction() {
        return action;
    }

    public SiteViewModel init() {
        search.setValue(null);
        result.setValue(null);
        player.setValue(null);
        action.setValue(null);
        return this;
    }

    public void homeContent() {
        execute(TaskType.RESULT, result, () -> SiteApi.homeContent(VodConfig.get().getHome()));
    }

    public void categoryContent(String key, String tid, String page, boolean filter, HashMap<String, String> extend) {
        execute(TaskType.RESULT, result, () -> SiteApi.categoryContent(key, tid, page, filter, extend));
    }

    public void action(String key, String act) {
        execute(TaskType.ACTION, action, () -> SiteApi.action(key, act));
    }

    public void detailContent(String key, String id) {
        execute(TaskType.RESULT, result, () -> SiteApi.detailContent(key, id));
    }

    public void playerContent(String key, String flag, String id) {
        execute(TaskType.PLAYER, player, () -> SiteApi.playerContent(key, flag, id));
    }

    public void searchContent(Site site, String keyword, boolean quick, String page) {
        execute(TaskType.RESULT, result, SearchTask.create(site, keyword, quick, page));
    }

    public void searchContent(List<Site> sites, String keyword, boolean quick) {
        int epoch = stopSearch();
        // 可用源排前，失效源排后，避免先返回一堆超时结果
        // 同时搜索的源数量受「搜索线程」设置约束，避免一次打满所有源
        java.util.concurrent.Semaphore gate = new java.util.concurrent.Semaphore(com.fongmi.android.tv.Setting.getThread());
        HealthChecker.sortByHealth(sites).forEach(site -> {
            FluentFuture<Result> future = FluentFuture.from(Task.largeExecutor().submit(() -> {
                gate.acquire();
                try {
                    return SearchTask.create(site, keyword, quick).call();
                } finally {
                    gate.release();
                }
            })).withTimeout(Constant.TIMEOUT_SEARCH, TimeUnit.MILLISECONDS, Task.scheduler());
            searchFuture.add(future);
            future.addCallback(Task.callback(
                    result -> {
                        if (searchEpoch.get() != epoch) return;
                        // 跨源同名片子只保留先到的一条，避免多仓/多源时列表被重复项淹没
                        List<Vod> items = SearchDedup.filter(result.getList(), searchSeen);
                        if (items.isEmpty()) return;
                        result.setList(items);
                        search.postValue(result);
                    }
            ), MoreExecutors.directExecutor());
        });
    }

    private void execute(TaskType type, MutableLiveData<Result> liveData, Callable<Result> callable) {
        AtomicInteger taskId = Objects.requireNonNull(taskIds.get(type));
        int currentId = taskId.incrementAndGet();
        ListenableFuture<?> old = futures.get(type);
        if (old != null) old.cancel(true);
        FluentFuture<Result> future = FluentFuture.from(Task.executor().submit(callable)).withTimeout(Constant.TIMEOUT_VOD, TimeUnit.MILLISECONDS, Task.scheduler());
        futures.put(type, future);
        future.addCallback(Task.callback(
                result -> {
                    if (taskId.get() == currentId) liveData.postValue(result);
                },
                error -> {
                    if (taskId.get() != currentId) return;
                    if (error instanceof CancellationException) return;
                    if (error instanceof ExtractException) liveData.postValue(Result.error(error.getMessage()));
                    else liveData.postValue(Result.empty());
                    error.printStackTrace();
                }
        ), MoreExecutors.directExecutor());
    }

    public int stopSearch() {
        int epoch = searchEpoch.incrementAndGet();
        searchSeen.clear();
        searchFuture.forEach(future -> future.cancel(true));
        searchFuture.clear();
        return epoch;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopSearch();
        futures.values().forEach(future -> future.cancel(true));
    }

    private enum TaskType {RESULT, PLAYER, ACTION}
}
