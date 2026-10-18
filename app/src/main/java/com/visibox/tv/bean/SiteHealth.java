package com.visibox.tv.bean;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.visibox.tv.App;
import com.visibox.tv.db.AppDatabase;

import java.util.List;

/**
 * 单个内容源的健康记录。
 *
 * <p>以 {@code Site.key} 为主键，每次探测覆盖写入。未探测过时 {@code checkedAt == 0}。
 */
@Entity
public class SiteHealth {

    /**
     * 未探测状态。与「探测过但为 0ms」区分开，避免把没测过的源当成极快。
     */
    public static final long NEVER = 0L;

    @PrimaryKey
    @NonNull
    private String key = "";
    private String name;
    private boolean ok;
    private long latency;
    private long checkedAt;
    private String message;

    public static List<SiteHealth> findAll() {
        return AppDatabase.get().getSiteHealthDao().findAll();
    }

    public static SiteHealth find(String key) {
        SiteHealth item = AppDatabase.get().getSiteHealthDao().find(key);
        return item == null ? new SiteHealth().key(key) : item;
    }

    public SiteHealth save() {
        if (getKey().isEmpty()) return this;
        AppDatabase.get().getSiteHealthDao().insertOrUpdate(this);
        return this;
    }

    public static void deleteAll() {
        AppDatabase.get().getSiteHealthDao().deleteAll();
    }

    public boolean isChecked() {
        return checkedAt > NEVER;
    }

    public String getKey() {
        return key == null ? "" : key;
    }

    public SiteHealth key(String key) {
        this.key = key == null ? "" : key;
        return this;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name == null ? "" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public long getLatency() {
        return latency;
    }

    public void setLatency(long latency) {
        this.latency = latency;
    }

    public long getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(long checkedAt) {
        this.checkedAt = checkedAt;
    }

    public String getMessage() {
        return message == null ? "" : message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 排序权重：越小越健康，用于搜索时优先使用可用线路。
     */
    public int getWeight() {
        if (!isChecked()) return 1;
        return ok ? 0 : 2;
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }
}
