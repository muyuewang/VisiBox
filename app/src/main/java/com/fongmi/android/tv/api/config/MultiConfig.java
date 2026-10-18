package com.fongmi.android.tv.api.config;

import android.text.TextUtils;

import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Prefers;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 多仓聚合。
 *
 * <p>TVBox 生态里「仓」就是一份配置 JSON，原生行为是同一时刻只激活一个仓，换仓要重新
 * 加载整套站点。开启聚合后，**已保存的其他仓会与当前仓同时生效**：各自的 sites 合并到
 * 同一张站点表，用户不必来回切换。
 *
 * <p>合并规则：
 * <ul>
 *   <li>按 {@code site.key} 去重，同 key 视为同一站点，保留先加载的（主仓优先）</li>
 *   <li>每个站点自带 {@code jar}（spider 地址），不同仓的爬虫互不干扰</li>
 *   <li>主仓的 home / parse / 直播 / 壁纸保持不变，附加仓只贡献站点</li>
 *   <li>单个仓加载失败只跳过它，不影响主配置</li>
 * </ul>
 *
 * <p>只用一个总开关，成员自动取「设置里已保存的全部仓」，避免再维护一份勾选列表。
 * 不想参与聚合的仓，从配置历史里删掉即可。
 */
public final class MultiConfig {

    private static final String KEY_ENABLED = "multi_repo";
    private static final String TAG = "MultiConfig";

    private MultiConfig() {
    }

    public static boolean isEnabled() {
        return Prefers.getBoolean(KEY_ENABLED);
    }

    public static void setEnabled(boolean enabled) {
        Prefers.put(KEY_ENABLED, enabled);
    }

    public static boolean toggle() {
        boolean enabled = !isEnabled();
        setEnabled(enabled);
        return enabled;
    }

    /**
     * 可参与聚合的仓：除当前主仓之外、所有已保存的配置。
     */
    public static List<Config> members(Config primary) {
        List<Config> result = new ArrayList<>();
        String mainUrl = primary == null ? "" : primary.getUrl();
        for (Config config : Config.getAll(BaseConfig.VOD)) {
            if (config == null || TextUtils.isEmpty(config.getUrl())) continue;
            if (config.getUrl().equals(mainUrl)) continue;
            result.add(config);
        }
        return result;
    }

    public static int count(Config primary) {
        return members(primary).size();
    }

    /**
     * 加载附加仓的站点。任一步失败只跳过该仓，不影响主配置。
     */
    public static List<Site> loadExtra(Config primary, List<Site> exists) {
        List<Site> result = new ArrayList<>();
        if (!isEnabled()) return result;
        Set<String> keys = new HashSet<>();
        for (Site site : exists) keys.add(site.getKey());
        for (Config config : members(primary)) {
            try {
                result.addAll(load(config.getUrl(), keys));
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    private static List<Site> load(String url, Set<String> keys) throws Throwable {
        String json = Decoder.getJson(UrlUtil.convert(url), TAG);
        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject()) return List.of();
        JsonObject object = parsed.getAsJsonObject();
        // 指向另一个仓列表的配置不作为附加仓，避免递归
        if (object.has("msg") || object.has("urls")) return List.of();
        String spider = Json.safeString(object, "spider");
        BaseLoader.get().parseJar(spider, true);
        List<Site> items = new ArrayList<>();
        for (JsonElement element : Json.safeListElement(object, "sites")) {
            try {
                Site site = Site.objectFrom(element, spider);
                if (site.getKey().isEmpty()) continue;
                if (!keys.add(site.getKey())) continue;
                items.add(site);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        return items;
    }
}
