#!/usr/bin/env python3
"""把白底照片抠成透明 PNG，并且把背景里那些零散的点点一并清掉。

做法：
1. 以四边像素的中位色当背景色，从四条边往里泛洪，只吃掉连通的背景，
   主体内部的白色（衣服、牙齿）不会被误伤。
2. 剩下的前景里，凡是和主体不连通的小碎块（背景上没被吃干净的点点、
   噪点、边缘残留的一条细线）整体删掉。
3. 边缘羽化一圈，按不透明区域裁出 bbox，再缩放。

用法：python3 knock_out2.py 输出目录 图片1 图片2 ...
输出：输出目录/pet1.png pet2.png ...
"""
import os
import sys
from collections import deque

from PIL import Image, ImageFilter

TOL = 58          # 与背景色的距离阈值
ALPHA_CUT = 60    # 判定"不透明"的 alpha
KEEP_RATIO = 0.06 # 小于最大块 6% 的孤立块一律删掉
MAX_SIDE = 288


def border_color(im):
    w, h = im.size
    px = im.load()
    samples = []
    step = max(1, w // 64)
    for x in range(0, w, step):
        samples.append(px[x, 0])
        samples.append(px[x, h - 1])
    for y in range(0, h, step):
        samples.append(px[0, y])
        samples.append(px[w - 1, y])
    rs = sorted(s[0] for s in samples)
    gs = sorted(s[1] for s in samples)
    bs = sorted(s[2] for s in samples)
    m = len(samples) // 2
    return (rs[m], gs[m], bs[m])


def near(c, bg, tol):
    return abs(c[0] - bg[0]) + abs(c[1] - bg[1]) + abs(c[2] - bg[2]) <= tol * 3


def knockout(path, out_path):
    im = Image.open(path).convert("RGB")
    w, h = im.size
    if max(w, h) > 1100:
        k = 1100.0 / max(w, h)
        im = im.resize((int(w * k), int(h * k)), Image.LANCZOS)
        w, h = im.size
    bg = border_color(im)
    px = im.load()

    bgmask = bytearray(w * h)
    for y in range(h):
        base = y * w
        for x in range(w):
            if near(px[x, y], bg, TOL):
                bgmask[base + x] = 1

    seen = bytearray(w * h)
    q = deque()
    for x in range(w):
        for y in (0, h - 1):
            i = y * w + x
            if bgmask[i] and not seen[i]:
                seen[i] = 1
                q.append(i)
    for y in range(h):
        for x in (0, w - 1):
            i = y * w + x
            if bgmask[i] and not seen[i]:
                seen[i] = 1
                q.append(i)
    while q:
        i = q.popleft()
        y, x = divmod(i, w)
        for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
            if 0 <= nx < w and 0 <= ny < h:
                j = ny * w + nx
                if bgmask[j] and not seen[j]:
                    seen[j] = 1
                    q.append(j)

    fg = [not seen[i] for i in range(w * h)]

    # 找出所有连通块，只留大的
    comp = [-1] * (w * h)
    sizes = []
    for i in range(w * h):
        if fg[i] and comp[i] < 0:
            cid = len(sizes)
            n = 0
            qq = deque([i])
            comp[i] = cid
            while qq:
                j = qq.popleft()
                n += 1
                y, x = divmod(j, w)
                for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                    if 0 <= nx < w and 0 <= ny < h:
                        k = ny * w + nx
                        if fg[k] and comp[k] < 0:
                            comp[k] = cid
                            qq.append(k)
            sizes.append(n)
    keep = set()
    dropped = 0
    if sizes:
        biggest = max(sizes)
        for cid, n in enumerate(sizes):
            if n >= biggest * KEEP_RATIO or n >= biggest * 0.5:
                keep.add(cid)
            else:
                dropped += 1

    alpha = Image.new("L", (w, h), 0)
    ap = alpha.load()
    for y in range(h):
        base = y * w
        for x in range(w):
            i = base + x
            if fg[i] and comp[i] in keep:
                ap[x, y] = 255
    alpha = alpha.filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.GaussianBlur(0.8))

    out = im.convert("RGBA")
    out.putalpha(alpha)
    bbox = alpha.getbbox()
    if bbox:
        out = out.crop(bbox)
    w2, h2 = out.size
    side = max(w2, h2)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(out, ((side - w2) // 2, (side - h2) // 2), out)
    if side > MAX_SIDE:
        canvas = canvas.resize((MAX_SIDE, MAX_SIDE), Image.LANCZOS)
    canvas.save(out_path)
    op = sum(1 for p in canvas.getdata() if p[3] > ALPHA_CUT)
    print("%s -> %s  %dx%d  主体占比 %.1f%%  删掉碎块 %d 个"
          % (path, out_path, canvas.size[0], canvas.size[1],
             100.0 * op / (canvas.size[0] * canvas.size[1]), dropped))


def main():
    outdir = sys.argv[1]
    os.makedirs(outdir, exist_ok=True)
    for i, src in enumerate(sys.argv[2:], 1):
        knockout(src, os.path.join(outdir, "pet%d.png" % i))


if __name__ == "__main__":
    main()
