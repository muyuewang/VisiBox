package com.visibox.tv.utils;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import com.visibox.tv.bean.SiteHealth;

/**
 * 在站点名称前加一枚健康状态圆点，不改布局，只改文本。
 *
 * <p>灰色 = 未检测，绿色 = 可用，红色 = 不可用。
 */
public final class HealthMark {

    public static final int COLOR_UNKNOWN = 0xFF9E9E9E;
    public static final int COLOR_OK = 0xFF43A047;
    public static final int COLOR_FAIL = 0xFFE53935;

    private HealthMark() {
    }

    public static CharSequence decorate(String name, SiteHealth health) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        builder.append("● ");
        builder.setSpan(new ForegroundColorSpan(color(health)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.append(name == null ? "" : name);
        return builder;
    }

    public static int color(SiteHealth health) {
        if (health == null || !health.isChecked()) return COLOR_UNKNOWN;
        return health.isOk() ? COLOR_OK : COLOR_FAIL;
    }
}
