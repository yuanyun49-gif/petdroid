# 桌宠 PetDroid

一个最小的安卓悬浮桌宠。它会看你前台在用哪个应用，蹲在那儿，待久了就冒一句话。

包名：`com.fish.petdroid`，可以直接装在 Android 8.0 以上。

## 它现在会做的事

- 悬浮在别的应用上面，拖哪放哪
- 每 2 秒看你一眼在用哪个应用
- 进了配置过的应用，待满 8 秒冒第一句话，之后每 30 秒一句
- 戳它一下会说「别戳我」
- 消息会自己消失，不挡事

## 想改话

打开 `app/src/main/java/com/fish/petdroid/AppMessages.java`，照着原来的格式加：

```java
MESSAGES.put("别的应用包名", new String[]{
        "第一句",
        "第二句"
});
```

包名怎么查：装个「包名查看器」，或者看应用商店网页版 URL 里的 package 参数。

## 换成自己的图

把白底图抠成透明 PNG，命名 `pet.png`，丢进 `app/src/main/assets/`，覆盖原来的。

仓库里带了一个 `tools/cutout.py`，只去纯白，不动人物细节：

```bash
python3 tools/cutout.py 你的白底图.png app/src/main/assets/pet.png
```

没有放图也能编，它会自己画一个蓝圆脸顶着。

## 怎么编出 apk

不需要电脑，也不需要装 Android SDK。用 GitHub 云端编：

1. 新建一个仓库，把 `petdroid/` 里的东西全传上去，注意 `.github/workflows/build.yml` 也要在，它是构建脚本
2. 传完 push 到 main 分支，Actions 自动开始跑
3. 跑完进那次构建记录，页面最下面 Artifacts 里下 `petdroid-apk`，解压出 `app-debug.apk`
4. 传到手机装

第一次装要开「允许安装未知来源应用」。装过旧版必须先卸载，签名不一样会冲突。

## 装完要做的事

1. 开app，点「1. 允许悬浮窗」，跳到系统设置里给它开
2. 点「2. 允许使用情况访问」，在列表里找到桌宠，打开
3. 点「叫它出来」

少给第二个权限，它就只能站着，不知道你在刷什么。

## 已知的坑

- 国内新系统对后台比较狠，可能需要顺手把桌宠加进电池白名单
- 使用情况访问被系统清掉之后，它会在角落发呆，重新点一次第 2 步就行
- 想让它睡觉，点「让它回去睡」
