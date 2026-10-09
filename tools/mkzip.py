#!/usr/bin/env python3
"""把工程打包成 zip，方便直接传上 GitHub。用法：python3 mkzip.py 工程目录 输出.zip"""

import os
import sys
import zipfile


def main():
    src, out = sys.argv[1], sys.argv[2]
    src = os.path.abspath(src)
    count = 0
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for root, dirs, files in os.walk(src):
            for name in files:
                full = os.path.join(root, name)
                rel = os.path.relpath(full, os.path.dirname(src))
                z.write(full, rel)
                count += 1
    print("%d 个文件 -> %s" % (count, out))


if __name__ == "__main__":
    main()
