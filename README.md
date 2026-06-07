# EvcarEnginePowerSoundSimulation

一个用于学习“电车模拟跑车声浪”的小项目。现在项目同时包含：

- Web 前端 demo：用零百时间和纵向加速度拟合油门，再用 Web Audio 播放/合成声浪。
- Android app demo：读取 BYD 车速变化，估算纵向加速度，再按零百时间拟合等效油门驱动声浪。

## 目录

```text
EvcarEnginePowerSoundSimulation/
  frontend/
    index.html
    styles.css
    app.js
    FRONTEND.md
  ANDROID_APP.md
  README.md
  android-app/
```

## 核心方案

不直接使用油门/电门开度，而是用加速度拟合“等效油门”：

```text
fullThrottleAcceleration = (100 / 3.6) / zeroToHundredSeconds
estimatedThrottle = currentAccelerationMps2 / fullThrottleAcceleration
```

然后把 `estimatedThrottle` 限制在 `0~1`，再映射到：

- RPM
- 音量
- 低通滤波
- 样本播放速度
- 谐波/FM 合成强度
- 换挡/回火等瞬态

这个方法适合电车声浪模拟，因为电车的加速体验往往比踏板原始数据更能代表用户听感。

## Web 运行

```powershell
cd "L:\byd car driver\EvcarEnginePowerSoundSimulation\frontend"
python -m http.server 4174
```

打开：

```text
http://127.0.0.1:4174
```

详细说明见：

[frontend/FRONTEND.md](./frontend/FRONTEND.md)

## Android App

Android 工程在：

```text
L:\byd car driver\EvcarEnginePowerSoundSimulation\android-app
```

用 Android Studio 打开这个目录，同步 Gradle 后运行到 BYD 车机环境或兼容模拟环境。

详细说明见：

[ANDROID_APP.md](./ANDROID_APP.md)

## 免费音频素材

Web demo 默认引用：

- `Ferrari 250 GTO, Engine Sound.ogg`
- 来源: Wikimedia Commons
- 授权: CC BY 4.0
- 地址: https://commons.wikimedia.org/wiki/File:Ferrari_250_GTO,_Engine_Sound.ogg

正式发布时需要保留署名和授权说明。当前项目用于学习和演示。
