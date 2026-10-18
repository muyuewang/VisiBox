package com.fongmi.android.tv;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.HandlerCompat;

import com.fongmi.android.tv.utils.Notify;
import com.fongmi.hook.Hook;
import com.github.catvod.Init;
import com.google.gson.Gson;

public class App extends Application implements Application.ActivityLifecycleCallbacks {

    private static volatile App instance;

    private final Handler handler;
    private final Gson gson;
    private final long time;

    private Activity activity;
    private Hook hook;

    public App() {
        instance = this;
        gson = new Gson();
        time = System.currentTimeMillis();
        // 爬虫 posted 到主线程的任务抛异常（第三方爬虫自身 bug）时吞掉，
        // 避免 config 切换瞬间整个应用崩溃；非爬虫异常照常抛出
        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void dispatchMessage(@NonNull android.os.Message msg) {
                try {
                    super.dispatchMessage(msg);
                } catch (Throwable t) {
                    if (fromSpider(t)) android.util.Log.e("App", "spider crash swallowed", t);
                    else throw t;
                }
            }
        };
    }

    public static App get() {
        return instance;
    }

    public static Gson gson() {
        return get().gson;
    }

    public static long time() {
        return get().time;
    }

    public static Activity activity() {
        return get().activity;
    }

    public static void post(Runnable runnable) {
        get().handler.post(runnable);
    }

    public static void post(Runnable runnable, long delayMillis) {
        get().handler.removeCallbacks(runnable);
        if (delayMillis >= 0) get().handler.postDelayed(runnable, delayMillis);
    }

    public static void removeCallbacks(Runnable runnable) {
        get().handler.removeCallbacks(runnable);
    }

    public static void removeCallbacks(Runnable... runnable) {
        for (Runnable r : runnable) get().handler.removeCallbacks(r);
    }

    public void setHook(Hook hook) {
        this.hook = hook;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Init.set(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Notify.createChannel();
        registerActivityLifecycleCallbacks(this);
        guardSpiderCrash();
    }

    /**
     * 第三方爬虫在切换配置等场景下会在主线程抛出 NPE 等异常，直接杀死整个应用。
     * 崩溃栈位于 com.github.catvod.spider 内的异常只记日志并放行主线程循环，
     * 应用自身的崩溃仍走系统默认处理。
     */
    private void guardSpiderCrash() {
        Thread.UncaughtExceptionHandler def = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            if (fromSpider(throwable)) {
                android.util.Log.e("App", "spider crash swallowed", throwable);
                return;
            }
            if (def != null) def.uncaughtException(thread, throwable);
        });
    }

    private static boolean fromSpider(Throwable throwable) {
        for (StackTraceElement element : throwable.getStackTrace()) {
            if (element.getClassName().startsWith("com.github.catvod.spider")) return true;
        }
        return false;
    }

    @Override
    public PackageManager getPackageManager() {
        return hook != null ? hook : getBaseContext().getPackageManager();
    }

    @Override
    public String getPackageName() {
        return hook != null ? hook.getPackageName() : getBaseContext().getPackageName();
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (activity != activity()) this.activity = activity;
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (activity == activity()) this.activity = null;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
    }
}