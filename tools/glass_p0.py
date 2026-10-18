#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""P0 浅色玻璃材质批量改造"""
import pathlib, re, sys

ROOT = pathlib.Path(r"C:\Users\fengxin\WorkBuddy\tvbox\base\app\src")
LOG = []

def rw(p, fn):
    if not p.exists():
        LOG.append(("MISS", str(p)))
        return
    old = p.read_text(encoding="utf-8")
    new = fn(old)
    if new != old:
        p.write_text(new, encoding="utf-8")
        LOG.append(("OK", str(p)))
    else:
        LOG.append(("SKIP", str(p)))

# ---------- 1. 卡片名条 ----------
def vod_name(t):
    t = t.replace('android:bottomLeftRadius="8dp"', 'android:bottomLeftRadius="16dp"')
    t = t.replace('android:bottomRightRadius="8dp"', 'android:bottomRightRadius="16dp"')
    return t.replace('@color/black_20', '#0DFFFFFF')

for ss in ("leanback", "mobile"):
    rw(ROOT / ss / "res/drawable/shape_vod_name.xml", vod_name)
    rw(ROOT / ss / "res/drawable/shape_vod_list.xml",
       lambda t: t.replace('@color/black_20', '#0DFFFFFF'))

# ---------- 2. item / text 系列：圆角 + 玻璃填充 ----------
def make(name):
    def f(t):
        t = t.replace('android:radius="4dp"', 'android:radius="14dp"')
        t = t.replace('android:radius="20dp"', 'android:radius="24dp"')
        if "focused" in name:
            t = t.replace('@color/black_40', '@color/glass_fill_strong')
            t = t.replace('@color/white"', '@color/accent_glass_edge"')
        elif "selected" in name:
            t = t.replace('@color/black_30', '@color/glass_fill')
            t = t.replace('@color/white"', '@color/accent_glass_edge"')
        elif "activated" in name:
            t = t.replace('@color/black_40', '@color/glass_fill')
        elif "normal" in name:
            t = t.replace('@color/black_20', '@color/glass_fill_soft')
        return t
    return f

for ss in ("leanback", "mobile"):
    d = ROOT / ss / "res/drawable"
    if not d.exists():
        continue
    for p in sorted(d.glob("shape_item_*.xml")) + sorted(d.glob("shape_text_*.xml")):
        rw(p, make(p.stem))

# ---------- 3. 功能行 ----------
def func(t):
    return (t.replace('#40FFFFFF', '@color/glass_fill_strong')
             .replace('#FF2DD4AA', '@color/accent_glass_edge')
             .replace('#1FFFFFFF', '@color/glass_fill_soft')
             .replace('android:radius="12dp"', 'android:radius="16dp"'))

for ss in ("leanback", "mobile"):
    rw(ROOT / ss / "res/drawable/selector_func.xml", func)

# ---------- 4. 颜色选择器 ----------
rw(ROOT / "main/res/color/pill_text.xml", lambda t: (
    '<?xml version="1.0" encoding="utf-8"?>\n<selector xmlns:android="http://schemas.android.com/apk/res/android">\n'
    '    <item android:color="@color/text_glass" android:state_focused="true" />\n'
    '    <item android:color="@color/accent_glass" android:state_selected="true" />\n'
    '    <item android:color="@color/text_glass_sub" />\n</selector>\n'))

rw(ROOT / "main/res/color/text.xml", lambda t: (
    t.replace('@color/yellow_500', '@color/accent_glass')
     .replace('<item android:color="@color/white" />', '<item android:color="@color/text_glass" />')))

# ---------- 5. 卡片圆角 ----------
def style(t):
    t = t.replace('<item name="cornerSizeTopLeft">8dp</item>', '<item name="cornerSizeTopLeft">16dp</item>')
    t = t.replace('<item name="cornerSizeTopRight">8dp</item>', '<item name="cornerSizeTopRight">16dp</item>')
    t = t.replace('<item name="cornerSize">8dp</item>', '<item name="cornerSize">16dp</item>')
    t = t.replace('<item name="cornerSizeBottomLeft">8dp</item>', '<item name="cornerSizeBottomLeft">12dp</item>')
    return t

rw(ROOT / "main/res/values/styles.xml", style)

# ---------- 6. 窗口底色 ----------
def wins(t):
    if "glass_window" in t:
        return t
    return t.replace(
        '<item name="android:windowFullscreen">true</item>',
        '<item name="android:windowBackground">@drawable/bg_glass_window</item>\n'
        '        <item name="android:windowFullscreen">true</item>')

for ss in ("leanback", "mobile"):
    rw(ROOT / ss / "res/values/styles.xml", wins)

# ---------- 7. 文字色翻转（跳过压在视频/海报上的） ----------
SKIP = {
    "view_widget_vod.xml", "view_progress.xml", "view_widget_live.xml",
    "view_control_live.xml", "activity_live.xml", "activity_cast.xml",
    "adapter_vod_rect.xml", "adapter_vod_oval.xml", "adapter_vod_list.xml",
}
total = 0
for ss in ("leanback", "mobile"):
    d = ROOT / ss / "res/layout"
    if not d.exists():
        continue
    for p in sorted(d.glob("*.xml")):
        if p.name in SKIP:
            continue
        def flip(t):
            return t.replace('android:textColor="@color/white"', 'android:textColor="@color/text_glass"')
        before = p.read_text(encoding="utf-8")
        after = flip(before)
        if after != before:
            n = before.count('android:textColor="@color/white"')
            p.write_text(after, encoding="utf-8")
            total += n
            LOG.append(("TEXT+%d" % n, str(p)))
LOG.append(("TOTAL", "textColor flipped = %d" % total))

# ---------- 8. 首页公告色（亮底不能用亮橙） ----------
for ss in ("leanback", "mobile"):
    rw(ROOT / ss / "res/layout/activity_home.xml",
       lambda t: t.replace('#FFFF7043', '#FFC7532B'))

for a, b in LOG:
    print("%-10s %s" % (a, b))
