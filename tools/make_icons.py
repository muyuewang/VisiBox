"""从 1024 主图生成 Android 各密度图标资源。

用法：
    python tools/make_icons.py <master.png> <res_dir>

产物：
    mipmap-*/ic_launcher.png           传统方形图标
    mipmap-*/ic_launcher_round.png     圆形图标
    mipmap-*/ic_launcher_foreground.png 自适应图标前景（透明底）
并把检测到的背景色打印出来，供 colors.xml 使用。
"""

import math
import os
import sys

from PIL import Image, ImageDraw

LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
FOREGROUND = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

# 自适应图标：内容占 108dp 画布的比例，留出安全区
SAFE_RATIO = 0.62
# 传统图标：内容占画布的比例
LEGACY_RATIO = 0.82
# 主图裁切时围绕标记的留白比例
CROP_PAD = 0.13


def strip_watermark(img):
    """右下角平台水印：用四角中位色覆盖。"""
    w, h = img.size
    samples = []
    for cx, cy in ((2, 2), (w - 10, 2), (2, h - 10), (w - 10, h - 10)):
        patch = img.crop((cx, cy, cx + 8, cy + 8)).resize((1, 1), Image.BOX)
        samples.append(patch.getpixel((0, 0))[:3])
    bg = tuple(sorted(c[i] for c in samples)[2] for i in range(3))
    draw = ImageDraw.Draw(img)
    draw.rectangle([int(w * 0.78), int(h * 0.92), w, h], fill=bg + (255,))
    return bg


def mark_bbox(img):
    """用「绿色主导度」找标记范围，避开深色背景与微弱渐变。"""
    w, h = img.size
    pixels = img.load()
    min_x, min_y, max_x, max_y = w, h, 0, 0
    found = False
    for y in range(0, h, 2):
        for x in range(0, w, 2):
            r, g, b = pixels[x, y][:3]
            if g - max(r, b) > 25 and g > 90:
                found = True
                min_x = min(min_x, x)
                min_y = min(min_y, y)
                max_x = max(max_x, x)
                max_y = max(max_y, y)
    if not found:
        raise SystemExit("未能在图中定位标记，请检查主图")
    return min_x, min_y, max_x, max_y


def crop_square(img, bbox, pad=CROP_PAD):
    """以标记的垂直范围定边长、以画面中心定水平位置。

    水平方向不用 bbox 宽度：图中有一条横贯画面的地平线，会把宽度撑满。
    """
    x0, y0, x1, y1 = bbox
    w, h = img.size
    side = min(max(y1 - y0, 1) * (1 + pad * 2), min(w, h))
    cx, cy = w / 2, (y0 + y1) / 2
    left = min(max(cx - side / 2, 0), w - side)
    top = min(max(cy - side / 2, 0), h - side)
    return img.crop((int(left), int(top), int(left + side), int(top + side)))


def key_transparent(img):
    """把深色背景扣成透明，保留标记及其柔光。"""
    out = img.convert("RGBA")
    pixels = out.load()
    w, h = out.size
    for y in range(h):
        for x in range(w):
            r, g, b, _ = pixels[x, y]
            alpha = max(0, min(255, int((g - max(r, b)) * 8)))
            pixels[x, y] = (r, g, b, alpha)
    return out


def fit(img, size, ratio, bg=None):
    """把图像按比例缩放到画布中央。bg 为 None 时画布透明。"""
    canvas = Image.new("RGBA", (size, size), bg if bg else (0, 0, 0, 0))
    inner = int(size * ratio)
    art = img.resize((inner, inner), Image.LANCZOS)
    canvas.alpha_composite(art, ((size - inner) // 2, (size - inner) // 2))
    return canvas


def round_mask(img):
    size = img.size[0]
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def main():
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    master_path, res_dir = sys.argv[1], sys.argv[2]

    img = Image.open(master_path).convert("RGBA")
    bg = strip_watermark(img)
    bbox = mark_bbox(img)
    square = crop_square(img, bbox)
    transparent = key_transparent(square)
    opaque_bg = bg + (255,)

    for density, size in LEGACY.items():
        folder = os.path.join(res_dir, "mipmap-" + density)
        os.makedirs(folder, exist_ok=True)
        icon = fit(square, size, LEGACY_RATIO, opaque_bg)
        icon.convert("RGB").save(os.path.join(folder, "ic_launcher.png"))
        round_mask(icon).save(os.path.join(folder, "ic_launcher_round.png"))

        fg_size = FOREGROUND[density]
        fg = fit(transparent, fg_size, SAFE_RATIO)
        fg.save(os.path.join(folder, "ic_launcher_foreground.png"))

    playstore = fit(square, 512, LEGACY_RATIO, opaque_bg).convert("RGB")
    playstore.save(os.path.join(res_dir, "..", "ic_launcher-playstore.png"))

    print("background=#%02X%02X%02X" % bg)
    print("bbox=", bbox)


if __name__ == "__main__":
    main()
