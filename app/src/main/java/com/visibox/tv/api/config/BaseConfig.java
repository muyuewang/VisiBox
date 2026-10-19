package com.visibox.tv.api.config;

import android.text.TextUtils;

import com.visibox.tv.App;
import com.visibox.tv.R;
import com.visibox.tv.bean.Config;
import com.visibox.tv.event.ConfigEvent;
import com.visibox.tv.impl.Callback;
import com.visibox.tv.server.Server;
import com.visibox.tv.utils.Notify;
import com.visibox.tv.utils.ResUtil;
import com.visibox.tv.utils.Task;
import com.visibox.tv.utils.UrlUtil;
import com.github.catvod.bean.Header;
import com.github.catvod.bean.Proxy;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InterruptedIOException;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

abstract class BaseConfig {

    public static final int VOD = 0;
    public static final int LIVE = 1;
    public static final int WALL = 2;

    private final AtomicInteger taskId = new AtomicInteger(0);

    /**
     * 快照通道。只对「由 load() 发起的那次加载」有效，所以放在 ThreadLocal 里：
     * ensureLoaded() 之类旁路调用跑在别的线程上，不该冒出一次 success 回调。
     */
    private final ThreadLocal<Snapshot> snapshot = new ThreadLocal<>();

    protected boolean sync;
    protected volatile Config config;
    private volatile Future<?> future;

    private static final class Snapshot {

        private final Callback callback;
        private final AtomicBoolean fired = new AtomicBoolean(false);

        private Snapshot(Callback callback) {
            this.callback = callback;
        }
    }

    protected abstract String getTag();

    protected abstract Config defaultConfig();

    protected abstract void load(Config config) throws Throwable;

    protected abstract boolean isLoaded();

    /** 是否启用配置快照，默认关闭（需要子类自己实现快照解析） */
    protected boolean cacheEnabled() {
        return false;
    }

    /**
     * 子类用本地快照解析成功后调用：先把界面放出来，网络刷新继续在后台跑。
     * 同一次加载只会生效一次，重复调用无副作用。
     */
    protected void onSnapshot() {
        Snapshot current = snapshot.get();
        if (current == null || !current.fired.compareAndSet(false, true)) return;
        App.post(current.callback::success);
        App.post(this::postEvent);
    }

    /** 本次加载是否已经用快照放行过（决定网络失败时要不要打扰用户） */
    protected boolean snapshotFired() {
        Snapshot current = snapshot.get();
        return current != null && current.fired.get();
    }

    public synchronized void ensureLoaded() {
        try {
            if (isLoaded()) return;
            if (config == null) config = defaultConfig();
            Server.get().start();
            load(config);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    protected void postEvent() {
        ConfigEvent.common();
    }

    public boolean needSync(String url) {
        return sync || config == null || TextUtils.isEmpty(config.getUrl()) || url.equals(config.getUrl());
    }

    public Config getConfig() {
        return config == null ? defaultConfig() : config;
    }

    protected void setHeaders(List<Header> headers) {
        OkHttp.responseInterceptor().addAll(headers);
    }

    protected void setProxy(List<Proxy> proxy) {
        OkHttp.authenticator().addAll(proxy);
        OkHttp.selector().addAll(proxy);
    }

    protected void setHosts(List<String> hosts) {
        OkHttp.dns().addAll(hosts);
    }

    public void load(Callback callback) {
        int id = taskId.incrementAndGet();
        if (future != null && !future.isDone()) future.cancel(true);
        future = Task.submit(() -> loadConfig(id, config, callback));
        callback.start();
    }

    protected void loadConfig(int id, Config config, Callback callback) {
        snapshot.set(new Snapshot(callback));
        try {
            Server.get().start();
            OkHttp.cancel(getTag());
            load(config);
            if (taskId.get() != id) return;
            if (config.equals(this.config)) config.update();
            App.post(() -> Notify.show(config.getNotice()));
            App.post(callback::success);
        } catch (Throwable e) {
            e.printStackTrace();
            if (isCanceled(e)) return;
            if (taskId.get() != id) return;
            if (snapshotFired()) {
                // 快照已经让界面可用了，这时候再弹「配置获取失败」只会让用户以为 App 坏了
                String url = config.getUrl();
                App.post(() -> Notify.show(ResUtil.getString(R.string.config_cache_tip, ConfigCache.ageText(ConfigCache.age(url)))));
            } else if (TextUtils.isEmpty(config.getUrl())) {
                App.post(() -> callback.error(""));
            } else {
                App.post(() -> callback.error(Notify.getError(R.string.error_config_get, e)));
            }
        } finally {
            if (taskId.get() == id) postEvent();
            snapshot.remove();
        }
    }

    protected boolean isCanceled(Throwable e) {
        if ("Canceled".equals(e.getMessage())) return true;
        if (e instanceof InterruptedException) return true;
        if (e instanceof InterruptedIOException) return true;
        return e.getCause() instanceof InterruptedIOException;
    }

    protected JsonArray fetchArray(JsonObject object, String key) {
        if (!object.has(key)) return new JsonArray();
        JsonElement element = object.get(key);
        if (element.isJsonObject()) return new JsonArray();
        if (element.isJsonPrimitive()) element = fetch(element.getAsString());
        JsonArray result = new JsonArray();
        for (JsonElement item : element.getAsJsonArray()) {
            if (item.isJsonPrimitive()) result.addAll(fetch(item.getAsString()));
            else if (item.isJsonObject()) result.add(item);
        }
        return result;
    }

    private JsonArray fetch(String url) {
        try {
            JsonElement parsed = JsonParser.parseString(OkHttp.string(UrlUtil.convert(url)));
            return parsed.isJsonArray() ? parsed.getAsJsonArray() : new JsonArray();
        } catch (Exception e) {
            return new JsonArray();
        }
    }
}
