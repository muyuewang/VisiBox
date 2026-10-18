package com.fongmi.android.tv.utils;

public class Github {

    /**
     * 自建发布源根地址，例如 {@code https://raw.githubusercontent.com/<you>/<repo>/<branch>}。
     *
     * <p>留空表示关闭在线更新检查。默认留空，是为了避免误拉上游 FongMi 的安装包而把本项目覆盖掉。
     */
    public static final String URL = "";

    public static boolean isEnabled() {
        return !URL.isEmpty();
    }

    private static String getUrl(String name) {
        return URL + "/apk/" + name;
    }

    public static String getJson(String name) {
        return getUrl(name + ".json");
    }

    public static String getApk(String name) {
        return getUrl(name + ".apk");
    }
}
