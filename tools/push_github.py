#!/usr/bin/env python3
"""把本地工程通过 GitHub Contents API 推上去。没有 git 也能用。"""

import base64
import json
import os
import re
import sys
import urllib.error
import urllib.request

TOKEN_FILE = "/data/user/0/com.ai.assistance.operit/files/datastore/github_auth_preferences.preferences_pb"
API = "https://api.github.com"
OWNER = "yuanyun49-gif"
REPO = "petdroid"


def token():
    raw = open(TOKEN_FILE, "rb").read().decode("utf-8", "ignore")
    m = re.search(r"(?:gho_|ghp_|github_pat_)[A-Za-z0-9_]+", raw)
    if not m:
        raise SystemExit("没找到 token")
    return m.group(0)


def req(method, url, payload=None):
    body = json.dumps(payload).encode() if payload is not None else None
    r = urllib.request.Request(url, method=method, data=body)
    r.add_header("Authorization", "Bearer " + TOKEN)
    r.add_header("Accept", "application/vnd.github+json")
    r.add_header("User-Agent", "petdroid-uploader")
    if body:
        r.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(r, timeout=60) as resp:
            return resp.status, json.loads(resp.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:400]


TOKEN = token()


def main():
    src = os.path.abspath(sys.argv[1])

    status, resp = req("POST", API + "/user/repos", {
        "name": REPO,
        "public": True,
        "description": "Android 悬浮桌宠，会盯着你在刷什么",
        "auto_init": False,
    })
    print("建仓库:", status, resp if status >= 400 else "ok")

    ok = fail = 0
    for root, dirs, files in os.walk(src):
        for name in files:
            full = os.path.join(root, name)
            rel = os.path.relpath(full, src).replace(os.sep, "/")
            with open(full, "rb") as f:
                content = base64.b64encode(f.read()).decode()
            url = "%s/repos/%s/%s/contents/%s" % (API, OWNER, REPO, rel)
            status, resp = req("PUT", url, {
                "message": "add " + rel,
                "content": content,
            })
            if status in (200, 201):
                ok += 1
                print("ok  ", rel)
            else:
                fail += 1
                print("fail", rel, status, resp)

    print("完成 %d 个，失败 %d 个" % (ok, fail))
    print("仓库地址 https://github.com/%s/%s" % (OWNER, REPO))


if __name__ == "__main__":
    main()