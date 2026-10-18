package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.Vod;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 聚合搜索去重。
 *
 * <p>多源并发搜索时，同一部片子在十几个源里都会命中，直接堆叠会让列表极长且难以选择。
 * 这里按「归一化片名」合并：全角转半角、去掉空白与标点、去掉括号内的附带说明
 * （如「(国语)」）、去掉清晰度与更新状态后缀（如「HD」「更新至12集」）。
 *
 * <p>去重是**按到达顺序保留第一条**，不做跨源内容比对——那需要二次请求详情页，
 * 会让搜索慢一个数量级，收益不成比例。
 */
public final class SearchDedup {

    /**
     * 括号及其内容：中英文圆括号、方括号、书名号内的补充说明。
     */
    private static final Pattern BRACKET = Pattern.compile("[\\(（\\[【《][^\\)）\\]】》]*[\\)）\\]】》]");

    /**
     * 清晰度、语言、更新进度等跨站差异较大的后缀。
     */
    private static final Pattern NOISE = Pattern.compile("(?i)(hdrip|hdts|hdtc|bluray|web-?dl|remux|1080p|720p|2160p|4k|8k|hd|bd|tc|ts|国语|粤语|英语|双语|中字|中文字幕|简中|繁中|高清|蓝光|抢先版|枪版|完结|全集|更新至\\d+[集期话]?|第?\\d+[季部])");

    /**
     * 空白与标点，含中文标点。
     */
    private static final Pattern PUNCT = Pattern.compile("[\\s\\p{Punct}，。、；：！？…—“”‘’《》〈〉「」『』【】〔〕（）·・]+");

    private SearchDedup() {
    }

    /**
     * 过滤掉已出现过的条目。{@code seen} 由调用方按整轮搜索持有，跨批累积。
     */
    public static List<Vod> filter(List<Vod> items, Set<String> seen) {
        List<Vod> result = new ArrayList<>();
        if (items == null || items.isEmpty()) return result;
        for (Vod item : items) {
            String key = key(item);
            if (key.isEmpty()) {
                result.add(item);
                continue;
            }
            if (seen.add(key)) result.add(item);
        }
        return result;
    }

    public static String key(Vod vod) {
        if (vod == null) return "";
        return normalize(vod.getName());
    }

    /**
     * 归一化片名。返回空串表示无法判定，调用方应保留该条目而不是丢弃。
     */
    public static String normalize(String name) {
        if (name == null) return "";
        String value = toHalfWidth(name);
        value = BRACKET.matcher(value).replaceAll("");
        value = NOISE.matcher(value).replaceAll("");
        value = PUNCT.matcher(value).replaceAll("");
        return value.trim().toLowerCase();
    }

    /**
     * 全角字符转半角，避免「ＴＶ」与「TV」被当成两个名字。
     */
    private static String toHalfWidth(String input) {
        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '\u3000') {
                chars[i] = ' ';
            } else if (chars[i] >= '\uFF01' && chars[i] <= '\uFF5E') {
                chars[i] = (char) (chars[i] - 0xFEE0);
            }
        }
        return new String(chars);
    }

    /**
     * 调试用：返回归一化过程中被去掉的部分，便于核对误杀。
     */
    static String explain(String name) {
        Matcher bracket = BRACKET.matcher(toHalfWidth(name));
        StringBuilder removed = new StringBuilder();
        while (bracket.find()) removed.append(bracket.group());
        return "normalized=" + normalize(name) + ", removed=" + removed;
    }
}
