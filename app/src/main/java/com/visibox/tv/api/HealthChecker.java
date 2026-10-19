package com.visibox.tv.api;

import android.os.SystemClock;

import com.visibox.tv.bean.Result;
import com.visibox.tv.bean.Site;
import com.visibox.tv.bean.SiteHealth;
import com.visibox.tv.utils.Task;

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
                if (done != null) com.visibox.tv.App.post(done);
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

    /**
     * 把异常翻译成人话。
     *
     * <p>原始 message 是 {@code java.net.UnknownHostException: Unable to resolve host "gh-proxy.top"}
     * 这类东西，用户看不懂，界面也没法直接展示。这里按常见失败类型归成一句短说明，
     * 归不进去的才回退到原文（截断）。
     */
    private static String describe(Throwable e) {
        String raw = e.getMessage() == null ? "" : e.getMessage();
        String lower = raw.toLowerCase();
        String type = e.getClass().getSimpleName();
        if (type.contains("UnknownHost") || lower.contains("unknown host") || lower.contains("unable to resolve"))
            return "域名解析失败";
        if (type.contains("SocketTimeout") || lower.contains("timeout") || lower.contains("timed out"))
            return "连接超时";
        if (type.contains("ConnectException") || lower.contains("refused"))
            return "连接被拒绝";
        if (type.contains("SSL") || type.contains("Cert") || lower.contains("certificate"))
            return "证书验证失败";
        if (type.contains("NoRouteToHost") || lower.contains("unreachable"))
            return "网络不可达";
        // 状态码要按词边界匹配：直接 contains("5") 会把任何带 5 的错误都误判成服务端错误
        if (raw.matches("(?s).*\\b401\\b.*") || raw.matches("(?s).*\\b403\\b.*")) return "接口拒绝访问";
        if (raw.matches("(?s).*\\b404\\b.*")) return "接口不存在";
        if (raw.matches("(?s).*\\b5\\d{2}\\b.*")) return "服务端错误";
        if (raw.isEmpty()) return type;
        return raw.length() > 60 ? raw.substring(0, 60) : raw;
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

    /**
     * 单源异步重测，回调切回主线程。
     *
     * <p>源列表上的红点可能是几天前的结果（域名早就恢复了），用户需要一个当场能验证的入口，
     * 而不是等 24 小时 TTL 过期。
     */
    public static void probeAsync(Site site, ProbeCallback callback) {
        PROBE_POOL.submit(() -> {
            SiteHealth health = probe(site);
            if (callback != null) com.visibox.tv.App.post(() -> callback.onResult(health));
        });
    }

    public interface ProbeCallback {

        void onResult(SiteHealth health);
    }

    /**
     * 统计已检测的源里可用的数量；未检测的不计入分母。
     */
    public static int countOk(List<Site> sites) {
        List<SiteHealth> all = SiteHealth.findAll();
        int ok = 0;
        for (Site site : sites) {
            SiteHealth health = find(all, site.getKey());
            if (health != null && health.isChecked() && health.isOk()) ok++;
        }
        return ok;
    }

    public static int countChecked(List<Site> sites) {
        List<SiteHealth> all = SiteHealth.findAll();
        int count = 0;
        for (Site site : sites) {
            SiteHealth health = find(all, site.getKey());
            if (health != null && health.isChecked()) count++;
        }
        return count;
    }

    /**
     * 「所有检测过的源都失败了」——用来给出换配置的明确建议。
     * 一个都没测过时返回 false，避免刚装好就报错。
     */
    public static boolean allFailed(List<Site> sites) {
        int checked = countChecked(sites);
        return checked > 0 && countOk(sites) == 0;
    }

    /**
     * 取某个源的健康快照，未测过时返回空记录（{@code isChecked()} 为 false）。
     */
    public static SiteHealth healthOf(String key) {
        return SiteHealth.find(key);
    }
}
