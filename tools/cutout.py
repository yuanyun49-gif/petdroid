#!/usr/bin/env python3
"""去白底。只把 RGB 都 >= 250 且三通道差 < 10 的像素变透明，不动人物细节。

用法：python3 cutout.py 输入.png 输出.png
"""

import sys

try:
    from PIL import Image
except ImportError:
    sys.exit("缺 Pillow，先 pip install pillow")

import numpy as np


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)

    img = Image.open(sys.argv[1]).convert("RGBA")
    data = np.array(img)

    r, g, b = data[:, :, 0], data[:, :, 1], data[:, :, 2]
    white = (
        (r >= 250) & (g >= 250) & (b >= 250)
        & (np.abs(r.astype(int) - g.astype(int)) < 10)
        & (np.abs(g.astype(int) - b.astype(int)) < 10)
    )
    data[white, 3] = 0

    Image.fromarray(data).save(sys.argv[2])
    print("抠完了：%d 个像素变透明 -> %s" % (int(white.sum()), sys.argv[2]))


if __name__ == "__main__":
    main()
