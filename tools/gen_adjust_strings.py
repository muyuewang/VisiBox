#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""一次性把「画面/音效/字幕/弹幕」相关字符串与数组写入三套 strings.xml。

可重复执行：已存在同名 name 的条目会被跳过，不会重复写入。
"""
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1] / "app/src/main/res"

# name -> (default_en, zh_rCN, zh_rTW)
STRINGS = {
    "adjust_picture": ("Picture", "画面调节", "畫面調節"),
    "adjust_audio": ("Audio effect", "音效", "音效"),
    "adjust_subtitle": ("Subtitle style", "字幕样式", "字幕樣式"),
    "adjust_danmaku": ("Danmaku style", "弹幕样式", "彈幕樣式"),
    "adjust_reset": ("Reset to default", "恢复默认", "恢復預設"),
    "adjust_default": ("Default", "默认", "預設"),
    "adjust_custom": ("Adjusted", "已调整", "已調整"),
    "picture_brightness": ("Brightness", "亮度", "亮度"),
    "picture_contrast": ("Contrast", "对比度", "對比度"),
    "picture_saturation": ("Saturation", "饱和度", "飽和度"),
    "picture_temperature": ("Temperature", "色温", "色溫"),
    "picture_hue": ("Hue", "色调", "色調"),
    "audio_preset": ("Preset", "预设", "預設"),
    "subtitle_color": ("Text color", "文字颜色", "文字顏色"),
    "subtitle_edge": ("Edge style", "描边样式", "描邊樣式"),
    "subtitle_edge_color": ("Edge color", "描边颜色", "描邊顏色"),
    "subtitle_background": ("Background", "背景", "背景"),
    "subtitle_size": ("Text size", "字号", "字號"),
    "subtitle_bottom": ("Bottom offset", "底部距离", "底部距離"),
    "danmaku_size": ("Text size", "弹幕字号", "彈幕字號"),
    "danmaku_speed": ("Scroll speed", "滚动速度", "滾動速度"),
    "danmaku_alpha": ("Opacity", "透明度", "透明度"),
    "danmaku_lines": ("Max lines", "显示行数", "顯示行數"),
    "danmaku_style": ("Edge style", "描边样式", "描邊樣式"),
    "danmaku_offset": ("Time offset", "时间偏移", "時間偏移"),
}

ARRAYS = {
    "audio_presets": [
        ("Standard", "标准", "標準"),
        ("Pop", "流行", "流行"),
        ("Rock", "摇滚", "搖滾"),
        ("Jazz", "爵士", "爵士"),
        ("Classical", "古典", "古典"),
        ("Bass boost", "低音增强", "低音增強"),
        ("Vocal", "人声", "人聲"),
    ],
    "audio_bands": [
        ("60 Hz", "60 Hz", "60 Hz"),
        ("230 Hz", "230 Hz", "230 Hz"),
        ("910 Hz", "910 Hz", "910 Hz"),
        ("3.6 kHz", "3.6 kHz", "3.6 kHz"),
        ("14 kHz", "14 kHz", "14 kHz"),
    ],
    "subtitle_colors": [
        ("White", "白色", "白色"),
        ("Yellow", "黄色", "黃色"),
        ("Green", "绿色", "綠色"),
        ("Cyan", "青色", "青色"),
        ("Blue", "蓝色", "藍色"),
        ("Pink", "粉色", "粉色"),
        ("Orange", "橙色", "橙色"),
        ("Black", "黑色", "黑色"),
    ],
    "subtitle_edges": [
        ("None", "无描边", "無描邊"),
        ("Outline", "描边", "描邊"),
        ("Shadow", "阴影", "陰影"),
        ("Raised", "浮雕", "浮雕"),
        ("Depressed", "凹陷", "凹陷"),
    ],
    "edge_colors": [
        ("Black", "黑色", "黑色"),
        ("White", "白色", "白色"),
        ("Green", "绿色", "綠色"),
        ("Cyan", "青色", "青色"),
        ("Yellow", "黄色", "黃色"),
        ("Pink", "粉色", "粉色"),
        ("Orange", "橙色", "橙色"),
    ],
    "subtitle_backgrounds": [
        ("Transparent", "透明", "透明"),
        ("Dim black", "半透明黑", "半透明黑"),
    ],
    "danmaku_styles": [
        ("Outline", "描边", "描邊"),
        ("Shadow", "阴影", "陰影"),
        ("None", "无", "無"),
    ],
}

FILES = {
    "values": 0,
    "values-zh-rCN": 1,
    "values-zh-rTW": 2,
}


def esc(text):
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def build_block(idx):
    lines = ["", "    <!-- 播放调节 / Player adjust -->"]
    for name, texts in STRINGS.items():
        lines.append('    <string name="%s">%s</string>' % (name, esc(texts[idx])))
    for name, items in ARRAYS.items():
        lines.append("")
        lines.append('    <string-array name="%s">' % name)
        for item in items:
            lines.append("        <item>%s</item>" % esc(item[idx]))
        lines.append("    </string-array>")
    return "\n".join(lines) + "\n"


for folder, idx in FILES.items():
    path = ROOT / folder / "strings.xml"
    text = path.read_text(encoding="utf-8")
    block = build_block(idx)
    added = []
    for name, texts in STRINGS.items():
        if 'name="%s"' % name in text:
            continue
        added.append('    <string name="%s">%s</string>' % (name, esc(texts[idx])))
    for name, items in ARRAYS.items():
        if 'name="%s"' % name in text:
            continue
        added.append("")
        added.append('    <string-array name="%s">' % name)
        for item in items:
            added.append("        <item>%s</item>" % esc(item[idx]))
        added.append("    </string-array>")
    if not added:
        print("skip %s (already applied)" % folder)
        continue
    if not text.rstrip().endswith("</resources>"):
        raise SystemExit("unexpected tail in %s" % path)
    text = text.rstrip()[: -len("</resources>")].rstrip() + "\n\n" + "\n".join(added) + "\n\n</resources>\n"
    path.write_text(text, encoding="utf-8")
    print("ok   %s (+%d entries)" % (folder, len(added)))
