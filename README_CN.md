# EvcarEnginePowerSoundSimulation

一个用于学习「电车模拟跑车声浪」的项目，同时包含 Web 前端 demo 和 Android app。

[English](./README.md)

## 概览

| 模块 | 说明 |
|---|---|
| **Web 前端** | 加速度拟合油门 → Web Audio 合成/采样播放 |
| **Android App** | BYD 车机数据源 + 加速度传感器方案，含完整算法链路和实时引擎声合成 |

## 核心算法

不直接使用油门/电门开度，而是用加速度拟合「等效油门」：

```text
fullThrottleAccel = (100 / 3.6) / zeroToHundredSeconds
estimatedThrottle = currentAccel / fullThrottleAccel
```

钳位到 `[0, 1]`，再映射到 RPM、音量、低通滤波、样本播放速度、谐波/FM 合成强度、换挡/回火瞬态。

这个方案适合电车声浪模拟，因为电车的加速体验往往比踏板原始数据更贴近用户听感。

## Web 前端

```bash
cd frontend
python3 -m http.server 4174
```

打开：http://127.0.0.1:4174

详细说明：[frontend/FRONTEND.md](./frontend/FRONTEND.md)

## Android App

用 Android Studio 打开 `android-app/` 目录，同步 Gradle 后运行到 BYD 车机或兼容模拟环境。

详细说明：[ANDROID_APP.md](./ANDROID_APP.md)

## 目录结构

```
EvcarEnginePowerSoundSimulation/
  frontend/          # Web demo
  android-app/       # Android 工程
  README.md          # 英文版
  README_CN.md       # 中文版（本文件）
```

## 音频素材

Web demo 默认引用：
- `Ferrari 250 GTO, Engine Sound.ogg`
- 来源：Wikimedia Commons
- 授权：CC BY 4.0
- 地址：https://commons.wikimedia.org/wiki/File:Ferrari_250_GTO,_Engine_Sound.ogg

正式发布需保留署名和授权说明。当前项目用于学习和演示。
