package com.visibox.tv.utils;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import com.visibox.tv.bean.SiteHealth;

import java.util.Locale;

/**
 * 在站点名称前加一枚健康状态圆点，不改布局，只改文本。
 *
 * <p>灰色 = 未检测，绿色 = 可用，红色 = 不可用。
 *
 * <p>只有圆点不够用：同样是红点，域名解析失败和连接超时该做的事完全一样，
 * 但用户看不出差别。所以失败时在名字后面补上原因，明显偏慢时补上耗时。
 */
public final class HealthMark {

    public static final int COLOR_UNKNOWN = 0xFF9E9E9E;
    public static final int COLOR_OK = 0xFF43A047;
    public static final int COLOR_FAIL = 0xFFE53935;
    /** 后面补充说明的灰色，不抢主体文字。 */
    private static final int COLOR_HINT = 0xFF9E9E9E;
    /** 超过这个耗时才算「慢」，否则一排数字太吵。 */
    private static final long SLOW_THRESHOLD = 1200;

    private HealthMark() {
    }

    public static CharSequence decorate(String name, SiteHealth health) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        builder.append("● ");
        builder.setSpan(new ForegroundColorSpan(color(health)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.append(name == null ? "" : name);
        return builder;
    }

    /**
     * 名字后面的补充说明：失败给原因，慢给耗时，其余为空。
     * 供列表行内独立右对齐展示，避免与站点名挤作一行导致排版参差。
     */
    public static CharSequence suffix(SiteHealth health) {
        if (health == null || !health.isChecked()) return "";
        if (!health.isOk()) {
            String message = health.getMessage();
            return message == null || message.isEmpty() ? "" : message;
        }
        long latency = health.getLatency();
        if (latency < SLOW_THRESHOLD) return "";
        return String.format(Locale.US, "%.1fs", latency / 1000f);
    }

    /**
     * 同 {@link #suffix(SiteHealth)}，但带上「 · 」分隔前缀，适合直接拼在站点名后。
     * TV 端网格单元格紧凑，后缀与名字合在一个 TextView 里展示。
     */
    public static CharSequence prefixHint(SiteHealth health) {
        CharSequence suffix = suffix(health);
        return suffix.length() == 0 ? "" : " · " + suffix;
    }

    public static int color(SiteHealth health) {
        if (health == null || !health.isChecked()) return COLOR_UNKNOWN;
        return health.isOk() ? COLOR_OK : COLOR_FAIL;
    }

    /**
     * 这条记录是否明确表示「不可用」。未检测过不算失败。
     */
    public static boolean failed(SiteHealth health) {
        return health != null && health.isChecked() && !health.isOk();
    }
}
