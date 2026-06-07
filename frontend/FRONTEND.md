# Frontend Demo

前端 demo 用浏览器里的 Web Audio API 模拟“加速度拟合油门 -> 声浪参数”的流程。它不再直接输入油门，而是输入：

- 车辆零百加速时间，单位秒
- 当前纵向加速度，单位 `m/s²`

## 拟合公式

假设电车电门比较线性，满电门时的平均零百加速度为：

```text
fullThrottleAcceleration = (100 / 3.6) / zeroToHundredSeconds
```

其中 `100 / 3.6 = 27.78 m/s`，表示从 0 km/h 到 100 km/h 的速度变化。

当前等效油门为：

```text
estimatedThrottle = currentAcceleration / fullThrottleAcceleration
```

最后限制在 `0.0~1.0`：

```text
estimatedThrottle = clamp(estimatedThrottle, 0, 1)
```

举例：如果一辆车零百 `7.5s`，满电门平均加速度约为 `3.70 m/s²`。当当前纵向加速度是 `1.85 m/s²`，等效油门就是约 `50%`。

## 声音映射

`app.js` 会把拟合油门转换为声浪参数：

- RPM: `900 + estimatedThrottle * 6200`
- 样本播放速度: 低油门慢，高油门快
- 音量: 油门越大，音量越大
- 低通滤波: 油门越大，截止频率越高，声音越亮
- 合成层谐波: 叠加 1 阶、2 阶、3 阶谐波
- 噪声层: 高油门时增加粗糙感

当前真实样本来自 Wikimedia Commons 的 Ferrari 250 GTO 引擎声。远程样本加载失败时，合成层仍会工作。

## 运行方式

```powershell
cd "L:\byd car driver\EvcarEnginePowerSoundSimulation\frontend"
python -m http.server 4174
```

然后打开：

```text
http://127.0.0.1:4174
```

## 关键文件

- `index.html`: 输入控件、仪表盘、音频标签
- `styles.css`: 页面布局和仪表样式
- `app.js`: 加速度拟合油门、Web Audio、Canvas 动画
