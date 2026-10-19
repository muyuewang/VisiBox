package com.visibox.tv.api.config;

import android.text.TextUtils;

import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;
import com.visibox.tv.R;
import com.visibox.tv.utils.ResUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

/**
 * 配置内容快照。
 *
 * <p>点播配置每次冷启动都要联网拉一次 JSON，配置站一挂、DNS 一被污染，用户就只能对着转圈等超时。
 * 这里把每次「解析成功」的原始 JSON 留一份本地副本：下次启动先用它出界面，网络请求继续在后台跑，
 * 拉到新内容再覆盖并刷新。副作用是断网时也能进 App 换源、看历史、改设置。
 *
 * <p>只缓存 http 配置；本地文件（file:）本身就没有网络开销。缓存放在应用私有目录，
 * 用户手动清理数据时会一起消失，不会留下"看不见的脏数据"。
 */
public final class ConfigCache {

    /** 最多保留几份快照（用户可能存了多个配置地址来回切） */
    private static final int MAX = 8;
    private static final String SUFFIX = ".json";

    private ConfigCache() {
    }

    /** 只有网络配置值得缓存 */
    public static boolean enabled(String url) {
        return !TextUtils.isEmpty(url) && url.startsWith("http");
    }

    private static File dir() {
        File dir = new File(Path.files(), "config");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File file(String url) {
        return new File(dir(), "cfg-" + Util.md5(url) + SUFFIX);
    }

    /** 读取快照，没有或读失败返回 null */
    public static String read(String url) {
        if (!enabled(url)) return null;
        try {
            File file = file(url);
            if (!Path.exists(file)) return null;
            String json = Path.read(file);
            return TextUtils.isEmpty(json) ? null : json;
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 写入快照。先写临时文件再改名，避免写到一半被杀进程留下半截 JSON
     * （半截 JSON 比没有更糟：下次启动会白白解析失败一次）。
     */
    public static void write(String url, String json) {
        if (!enabled(url) || TextUtils.isEmpty(json)) return;
        try {
            File file = file(url);
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            Path.write(tmp, json.getBytes(StandardCharsets.UTF_8));
            if (!Path.exists(tmp)) return;
            // 先试直接改名（POSIX 覆盖同名文件是原子的）；失败再退化成删旧改名
            if (!tmp.renameTo(file)) {
                if (file.exists() && !file.delete()) {
                    tmp.delete();
                    return;
                }
                if (!tmp.renameTo(file)) {
                    tmp.delete();
                    return;
                }
            }
            clean();
            android.util.Log.d("ConfigCache", "saved " + json.length() + " chars -> " + file.getName());
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /** 快照解析失败（格式变了、半截文件）时丢弃 */
    public static void drop(String url) {
        if (!enabled(url)) return;
        try {
            File file = file(url);
            if (file.exists()) file.delete();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /** 快照的写入时间，没有则返回 0 */
    public static long age(String url) {
        if (!enabled(url)) return 0;
        File file = file(url);
        if (!file.exists()) return 0;
        long time = file.lastModified();
        return time <= 0 ? 0 : System.currentTimeMillis() - time;
    }

    /** 快照有多旧，给人看：刚刚 / 12 分钟 / 3 小时 / 2 天 */
    public static String ageText(long millis) {
        long minute = millis / 60000;
        if (minute < 1) return ResUtil.getString(R.string.cache_age_now);
        if (minute < 60) return ResUtil.getString(R.string.cache_age_minute, minute);
        long hour = minute / 60;
        if (hour < 24) return ResUtil.getString(R.string.cache_age_hour, hour);
        return ResUtil.getString(R.string.cache_age_day, hour / 24);
    }

    /** 只留最近 MAX 份，旧的删掉 */
    private static void clean() {
        try {
            File[] files = dir().listFiles((d, name) -> name.startsWith("cfg-") && name.endsWith(SUFFIX));
            if (files == null || files.length <= MAX) return;
            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
            for (int i = MAX; i < files.length; i++) files[i].delete();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
