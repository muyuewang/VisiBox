package com.visibox.tv.api.config;

import android.text.TextUtils;

import com.visibox.tv.App;
import com.visibox.tv.api.Decoder;
import com.visibox.tv.api.loader.BaseLoader;
import com.visibox.tv.bean.Config;
import com.visibox.tv.bean.Depot;
import com.visibox.tv.bean.Parse;
import com.visibox.tv.bean.Rule;
import com.visibox.tv.bean.Site;
import com.visibox.tv.event.ConfigEvent;
import com.visibox.tv.event.RefreshEvent;
import com.visibox.tv.impl.Callback;
import com.visibox.tv.utils.UrlUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.bean.Header;
import com.github.catvod.bean.Proxy;
import com.github.catvod.utils.Json;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class VodConfig extends BaseConfig {

    private static final String TAG = VodConfig.class.getSimpleName();

    private Site home;
    private String wall;
    private Parse parse;
    private List<Doh> doh;
    private List<Rule> rules;
    private List<Site> sites;
    private List<String> ads;
    private List<String> flags;
    private List<Parse> parses;
    /** 正在解析本地快照：这条路径不联网，爬虫 jar 也不下载，留给随后的网络刷新去做 */
    private boolean fromSnapshot;

    public static VodConfig get() {
        return Loader.INSTANCE;
    }

    public static int getCid() {
        return get().getConfig().getId();
    }

    public static String getUrl() {
        return get().getConfig().getUrl();
    }

    public static String getDesc() {
        return get().getConfig().getDesc();
    }

    public static int getHomeIndex() {
        return get().getSites().indexOf(get().getHome());
    }

    public static boolean hasParse() {
        return !get().getParses().isEmpty();
    }

    public static void load(Config config, Callback callback) {
        get().clear().config(config).load(callback);
    }

    public VodConfig init() {
        return config(Config.vod());
    }

    public VodConfig config(Config config) {
        this.config = config;
        return this;
    }

    public VodConfig clear() {
        ads = null;
        doh = null;
        home = null;
        wall = null;
        parse = null;
        sites = null;
        flags = null;
        rules = null;
        parses = null;
        BaseLoader.get().clear();
        RuleConfig.get().invalidate();
        return this;
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected Config defaultConfig() {
        return Config.vod();
    }

    @Override
    protected void postEvent() {
        super.postEvent();
        ConfigEvent.vod();
    }

    @Override
    protected boolean cacheEnabled() {
        return true;
    }

    @Override
    protected void load(Config config) throws Throwable {
        String url = UrlUtil.convert(config.getUrl());
        // 一、先用本地快照把界面撑起来（stale-while-revalidate）
        if (cacheEnabled() && ConfigCache.enabled(url)) {
            String cached = ConfigCache.read(url);
            if (!TextUtils.isEmpty(cached)) {
                try {
                    JsonObject object = Json.parse(cached).getAsJsonObject();
                    fromSnapshot = true;
                    checkJson(config, object);
                    android.util.Log.d("VodConfig", "snapshot hit, sites=" + getSites().size() + ", age=" + ConfigCache.age(url) + "ms");
                    onSnapshot();
                } catch (Throwable e) {
                    // 快照坏了（格式变更、写了一半）就当没有，清掉让下面网络流程全权接管
                    android.util.Log.w("VodConfig", "snapshot invalid", e);
                    ConfigCache.drop(url);
                } finally {
                    fromSnapshot = false;
                }
            }
        }
        // 二、联网刷新，成功才覆盖快照；失败时旧快照原样保留
        String json = Decoder.getJson(url, TAG);
        // 先解析再落盘：{"msg":"..."} 这类错误响应也能通过 JSON 语法检查，
        // 但 checkJson 会抛异常，缓存它等于下次启动白解析一遍
        checkJson(config, Json.parse(json).getAsJsonObject());
        ConfigCache.write(url, json);
    }

    @Override
    protected boolean isLoaded() {
        return !getSites().isEmpty();
    }

    private void checkJson(Config config, JsonObject object) throws Throwable {
        if (object.has("msg")) {
            throw new Exception(object.get("msg").getAsString());
        } else if (object.has("urls")) {
            parseDepot(config, object);
        } else {
            parseConfig(config, object);
        }
    }

    private void parseDepot(Config config, JsonObject object) throws Throwable {
        List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
        List<Config> configs = new ArrayList<>();
        for (Depot item : items) configs.add(Config.find(item, VOD));
        if (configs.isEmpty()) throw new Exception("Depot urls is empty");
        load(this.config = configs.get(0));
        Config.delete(config.getUrl());
    }

    private void parseConfig(Config config, JsonObject object) {
        initList(object);
        initLive(config, object);
        initWall(config, object);
        initSite(config, object);
        initParse(config, object);
        config.setLogo(Json.safeString(object, "logo"));
        config.setNotice(Json.safeString(object, "notice"));
    }

    private void initList(JsonObject object) {
        setHeaders(Header.arrayFrom(fetchArray(object, "headers")));
        setProxy(Proxy.arrayFrom(fetchArray(object, "proxy")));
        setRules(Rule.arrayFrom(fetchArray(object, "rules")));
        setDoh(Doh.arrayFrom(fetchArray(object, "doh")));
        setFlags(Json.safeListString(object, "flags"));
        setHosts(Json.safeListString(object, "hosts"));
        setAds(Json.safeListString(object, "ads"));
    }

    private void initLive(Config config, JsonObject object) {
        if (Json.isEmpty(object, "lives")) return;
        Config temp = Config.find(config, LIVE).save();
        boolean sync = LiveConfig.get().needSync(config.getUrl());
        if (sync) LiveConfig.get().config(temp.update()).parse(object);
    }

    private void initWall(Config config, JsonObject object) {
        if (Json.isEmpty(object, "wallpaper")) return;
        this.wall = Json.safeString(object, "wallpaper");
        Config temp = Config.find(wall, config.getName(), WALL).save();
        boolean sync = WallConfig.get().needSync(wall);
        if (sync) WallConfig.get().config(temp.update());
    }

    private void initSite(Config config, JsonObject object) {
        String spider = Json.safeString(object, "spider");
        // 快照路径跳过 jar 下载：parseJar 每次冷启动都会同步拉一遍爬虫 jar，
        // 真让它跑就等于把「秒开」又拖回网络等待。jar 由随后的网络刷新装载，
        // 用户在刷新完成前点开站点时，getSpider() 也会按需把它装上，不会缺类。
        if (!fromSnapshot) BaseLoader.get().parseJar(spider, true);
        List<Site> items = Json.safeListElement(object, "sites").stream().map(e -> Site.objectFrom(e, spider)).distinct().collect(Collectors.toCollection(ArrayList::new));
        // 多仓：把其他已勾选仓库的站点并入当前站点表，站点各自携带自己的 spider
        items.addAll(MultiConfig.loadExtra(config, items));
        setSites(items);
        Map<String, Site> saved = Site.findAll().stream().collect(Collectors.toMap(Site::getKey, Function.identity()));
        getSites().forEach(site -> site.sync(saved.get(site.getKey())));
        setHome(config, getSites().isEmpty() ? new Site() : getSites().stream().filter(item -> item.getKey().equals(config.getHome())).findFirst().orElse(getSites().get(0)), false);
    }

    private void initParse(Config config, JsonObject object) {
        setParses(Json.safeListElement(object, "parses").stream().map(Parse::objectFrom).distinct().collect(Collectors.toCollection(ArrayList::new)));
        setParse(config, getParses().isEmpty() ? new Parse() : getParses().stream().filter(item -> item.getName().equals(config.getParse())).findFirst().orElse(getParses().get(0)), false);
    }

    public List<Site> getSites() {
        return sites == null ? Collections.emptyList() : sites;
    }

    private void setSites(List<Site> sites) {
        this.sites = sites;
    }

    public List<Parse> getParses() {
        return parses == null ? Collections.emptyList() : parses;
    }

    private void setParses(List<Parse> parses) {
        if (!parses.isEmpty()) parses.add(0, Parse.god());
        this.parses = parses;
    }

    public List<Doh> getDoh() {
        List<Doh> items = Doh.get(App.get());
        if (doh == null) return items;
        items.removeAll(doh);
        items.addAll(doh);
        return items;
    }

    private void setDoh(List<Doh> doh) {
        this.doh = doh;
    }

    public List<Rule> getRules() {
        return rules == null ? Collections.emptyList() : rules;
    }

    private void setRules(List<Rule> rules) {
        this.rules = rules;
        RuleConfig.get().invalidate();
    }

    public List<Parse> getParses(int type) {
        return getParses().stream().filter(item -> item.getType() == type).toList();
    }

    public List<Parse> getParses(int type, String flag) {
        List<Parse> items = getParses(type);
        List<Parse> filter = items.stream().filter(item -> item.getExt().getFlag().contains(flag)).toList();
        return filter.isEmpty() ? items : filter;
    }

    public List<String> getFlags() {
        return flags == null ? Collections.emptyList() : flags;
    }

    private void setFlags(List<String> flags) {
        this.flags = flags;
    }

    public List<String> getAds() {
        return ads == null ? Collections.emptyList() : ads;
    }

    private void setAds(List<String> ads) {
        this.ads = ads;
        RuleConfig.get().invalidate();
    }

    public Parse getParse() {
        return parse == null ? new Parse() : parse;
    }

    public void setParse(Parse parse) {
        setParse(getConfig(), parse, true);
    }

    public Site getHome() {
        return home == null ? new Site() : home;
    }

    public void setHome(Site site) {
        setHome(getConfig(), site, true);
        RefreshEvent.home();
    }

    public String getWall() {
        return TextUtils.isEmpty(wall) ? "" : wall;
    }

    public Parse getParse(String name) {
        return getParses().stream().filter(item -> item.getName().equals(name)).findFirst().orElse(new Parse());
    }

    public Site getSite(String key) {
        return getSites().stream().filter(item -> item.getKey().equals(key)).findFirst().orElse(new Site());
    }

    private void setParse(Config config, Parse parse, boolean save) {
        this.parse = parse;
        this.parse.setActivated(true);
        config.setParse(parse.getName());
        getParses().forEach(item -> item.setActivated(parse));
        if (save) config.save();
    }

    private void setHome(Config config, Site site, boolean save) {
        home = site;
        home.setActivated(true);
        config.setHome(home.getKey());
        if (save) config.save();
        getSites().forEach(item -> item.setActivated(home));
    }

    private static class Loader {
        static volatile VodConfig INSTANCE = new VodConfig();
    }
}
