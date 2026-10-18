"""程序化生成液态玻璃风格壁纸。

思路：低分辨率画布上堆柔和光斑 → 大半径高斯模糊 → 放大到 1920x1080，
天然得到苹果那种「透过磨砂玻璃看色块」的观感；再叠一层细颗粒防色阶断层。
用法：python tools/gen_wallpaper.py
"""

import io
import os
import random

from PIL import Image, ImageDraw, ImageFilter, ImageChops

W, H = 1920, 1080
SW, SH = 480, 270  # 工作画布，1/4 边长

# 四张：晨雾（浅）/ 深海（最深）/ 暖阳（浅暖）/ 极光（中调）
PALETTES = [
    {
        "name": "晨雾",
        "top": (238, 241, 248),
        "bottom": (222, 231, 243),
        "blobs": [((201, 198, 242), 0.55), ((191, 233, 220), 0.50), ((255, 255, 255), 0.60), ((214, 226, 246), 0.55)],
        "seed": 11,
    },
    {
        "name": "深海",
        "top": (198, 217, 234),
        "bottom": (150, 180, 208),
        "blobs": [((143, 184, 222), 0.55), ((127, 199, 192), 0.45), ((96, 137, 184), 0.50), ((255, 255, 255), 0.35)],
        "seed": 23,
    },
    {
        "name": "暖阳",
        "top": (246, 238, 231),
        "bottom": (238, 219, 205),
        "blobs": [((245, 201, 174), 0.55), ((243, 217, 164), 0.50), ((255, 246, 236), 0.65), ((232, 205, 196), 0.50)],
        "seed": 37,
    },
    {
        "name": "极光",
        "top": (226, 226, 242),
        "bottom": (176, 196, 226),
        "blobs": [((185, 174, 234), 0.50), ((165, 216, 232), 0.50), ((159, 224, 208), 0.45), ((255, 255, 255), 0.40)],
        "seed": 53,
    },
]


def build_gradient(top, bottom):
    strip = Image.new("RGB", (1, 256))
    for y in range(256):
        t = y / 255
        strip.putpixel((0, y), (
            round(top[0] + (bottom[0] - top[0]) * t),
            round(top[1] + (bottom[1] - top[1]) * t),
            round(top[2] + (bottom[2] - top[2]) * t),
        ))
    return strip.resize((SW, SH), Image.BICUBIC)


def add_blobs(img, blobs, rnd):
    # 光斑直接以半透明叠在渐变上，不能用黑色底图层去 blend，否则整体被压暗
    out = img.copy()
    for color, alpha in blobs:
        cw = int(SW * rnd.uniform(0.50, 1.00))
        ch = int(SH * rnd.uniform(0.50, 1.00))
        cx = rnd.uniform(-0.15, 1.15) * SW
        cy = rnd.uniform(-0.15, 1.15) * SH
        blob = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
        ImageDraw.Draw(blob).ellipse((0, 0, cw - 1, ch - 1), fill=color + (255,))
        blob = blob.filter(ImageFilter.GaussianBlur(cw // 6))
        mask = blob.split()[3].point(lambda v: int(v * alpha))
        out.paste(blob.convert("RGB"), (int(cx - cw / 2), int(cy - ch / 2)), mask)
    return out


def vignette(img):
    # 内切椭圆做遮罩，四角落在遮罩外 → 只压暗边角
    mask = Image.new("L", (SW, SH), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, SW - 1, SH - 1), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(SW // 10)).resize((W, H), Image.BICUBIC)
    dark = Image.new("RGB", (W, H), (20, 26, 42))
    return Image.composite(img, Image.blend(img, dark, 0.18), mask)


def grain(img, sigma):
    noise = Image.effect_noise((W, H), sigma).convert("L")
    noise = Image.merge("RGB", (noise, noise, noise))
    return Image.blend(img, ImageChops.overlay(img, noise), 0.14)


def main():
    out_dir = os.path.join("dist", "wallpaper")
    os.makedirs(out_dir, exist_ok=True)
    for i, p in enumerate(PALETTES, 1):
        rnd = random.Random(p["seed"])
        img = build_gradient(p["top"], p["bottom"])
        img = add_blobs(img, p["blobs"], rnd)
        img = img.filter(ImageFilter.GaussianBlur(18)).resize((W, H), Image.BICUBIC)
        img = vignette(img)
        img = grain(img, 5)
        path = os.path.join(out_dir, "wallpaper_%d.webp" % i)
        img.save(path, "WEBP", quality=80, method=6)
        print("%s  %s  %.0f KB" % (path, p["name"], os.path.getsize(path) / 1024))


if __name__ == "__main__":
    main()
