package com.fongmi.android.tv.api;

import android.os.SystemClock;

import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.SiteHealth;
import com.fongmi.android.tv.utils.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 内容源健康度探测。
 *
 * <p>对每个源调用一次首页接口（{@link SiteApi#homeContent}），记录是否可用与实际耗时。
 * 探测本身是并发的，但结果按源列表顺序回调，便于界面显示稳定的进度。
 *
 * <p>判定标准：请求未抛异常即认为源存活。「返回空内容」单独标记，因为这类源通常
 * 只是首页接口不实现，但分类与搜索仍可用。
 */
public final class HealthChecker {

    public interface Listener {

        void onStart(int total);

        void onResult(SiteHealth health, int done, int total);

        void onFinish(List<SiteHealth> results);
    }

    private static final long PROBE_TIMEOUT = TimeUnit.SECONDS.toMillis(8);
    private static final long FRESH_TTL = TimeUnit.HOURS.toMillis(24);
    private static final ExecutorService PROBE_POOL = Executors.newFixedThreadPool(8);
    private static volatile Future<?> running;

    private HealthChecker() {
    }

    /**
     * 只探测「从未测过」或「超过 24 小时」的源，用于打开站点列表时的静默巡查。
     * 已有任务在跑或无需更新时直接返回，{@code done} 不会被调用。
     */
    public static void autoCheck(List<Site> sites, Runnable done) {
        if (isRunning()) return;
        List<SiteHealth> all = SiteHealth.findAll();
        long now = System.currentTimeMillis();
        List<Site> stale = new ArrayList<>();
        for (Site site : sites) {
            if (site.getKey().isEmpty()) continue;
            SiteHealth health = null;
            for (SiteHealth item : all) {
                if (item.getKey().equals(site.getKey())) {
                    health = item;
                    break;
                }
            }
            if (health == null || !health.isChecked() || now - health.getCheckedAt() > FRESH_TTL) stale.add(site);
        }
        if (stale.isEmpty()) return;
        check(stale, new Listener() {
            @Override
            public void onStart(int total) {
            }

            @Override
            public void onResult(SiteHealth health, int done, int total) {
            }

            @Override
            public void onFinish(List<SiteHealth> results) {
                if (done != null) com.fongmi.android.tv.App.post(done);
            }
        });
    }

    public static boolean isRunning() {
        Future<?> task = running;
        return task != null && !task.isDone();
    }

    public static void cancel() {
        Future<?> task = running;
        if (task != null) task.cancel(true);
        running = null;
    }

    /**
     * 启动一次探测，立即返回。回调在后台线程触发，界面层需自行切回主线程。
     */
    public static void check(List<Site> sites, Listener listener) {
        cancel();
        List<Site> targets = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (Site site : sites) {
            String key = site.getKey();
            if (key.isEmpty() || seen.contains(key)) continue;
            seen.add(key);
            targets.add(site);
        }
        running = Task.submit(() -> doCheck(targets, listener));
    }

    private static void doCheck(List<Site> targets, Listener listener) {
        int total = targets.size();
        listener.onStart(total);
        List<SiteHealth> results = new ArrayList<>();
        if (total == 0) {
            listener.onFinish(results);
            return;
        }
        List<Future<SiteHealth>> futures = new ArrayList<>();
        for (Site site : targets) futures.add(PROBE_POOL.submit(() -> probe(site)));
        int done = 0;
        for (int i = 0; i < futures.size(); i++) {
            SiteHealth health;
            try {
                Future<SiteHealth> future = futures.get(i);
                health = future.get(PROBE_TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                futures.get(i).cancel(true);
                health = failed(targets.get(i), "超时");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable e) {
                health = failed(targets.get(i), describe(e));
            }
            results.add(health);
            done++;
            listener.onResult(health, done, total);
            if (Thread.currentThread().isInterrupted()) return;
        }
        listener.onFinish(results);
    }

    /**
     * 同步探测单个源，包含落库。必须在后台线程调用。
     */
    public static SiteHealth probe(Site site) {
        SiteHealth health = new SiteHealth().key(site.getKey());
        health.setName(site.getName());
        long start = SystemClock.elapsedRealtime();
        try {
            Result result = SiteApi.homeContent(site);
            long cost = SystemClock.elapsedRealtime() - start;
            boolean empty = result == null || (result.getList().isEmpty() && result.getTypes().isEmpty());
            health.setOk(true);
            health.setLatency(cost);
            health.setMessage(empty ? "可连接，首页无内容" : "");
        } catch (Throwable e) {
            health.setOk(false);
            health.setLatency(SystemClock.elapsedRealtime() - start);
            health.setMessage(describe(e));
        }
        health.setCheckedAt(System.currentTimeMillis());
        return health.save();
    }

    private static SiteHealth failed(Site site, String message) {
        SiteHealth health = new SiteHealth().key(site.getKey());
        health.setName(site.getName());
        health.setOk(false);
        health.setLatency(0);
        health.setMessage(message);
        health.setCheckedAt(System.currentTimeMillis());
        return health.save();
    }

    private static String describe(Throwable e) {
        String message = e.getMessage();
        if (message == null || message.isEmpty()) message = e.getClass().getSimpleName();
        return message.length() > 60 ? message.substring(0, 60) : message;
    }

    /**
     * 按健康度重排源：可用 → 未检测 → 不可用，同档内按延迟升序。
     * 用于搜索与首页优先使用可用源。
     */
    public static List<Site> sortByHealth(List<Site> sites) {
        List<SiteHealth> all = SiteHealth.findAll();
        List<Site> sorted = new ArrayList<>(sites);
        sorted.sort((a, b) -> {
            SiteHealth ha = find(all, a.getKey());
            SiteHealth hb = find(all, b.getKey());
            int wa = ha == null ? 1 : ha.getWeight();
            int wb = hb == null ? 1 : hb.getWeight();
            if (wa != wb) return Integer.compare(wa, wb);
            long la = ha == null ? 0 : ha.getLatency();
            long lb = hb == null ? 0 : hb.getLatency();
            return Long.compare(la, lb);
        });
        return sorted;
    }

    private static SiteHealth find(List<SiteHealth> all, String key) {
        for (SiteHealth item : all) if (item.getKey().equals(key)) return item;
        return null;
    }
}
