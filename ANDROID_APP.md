# Android App

`android-app/` 是 BYD 车机 Android demo 工程的精简副本，保留了源码、Gradle 配置和 `bydauto-openapi.jar`，没有复制构建缓存。

## 目录

```text
android-app/
  build.gradle.kts
  settings.gradle.kts
  gradle.properties
  gradle/
  app/
    build.gradle.kts
    libs/bydauto-openapi.jar
    src/main/
```

## 改动目标

原始 demo 直接使用 `BYDAutoSpeedDevice.getAccelerateDeepness()` 或电门深度作为声浪油门输入。现在改为：

```text
车速变化 -> 纵向加速度 -> 根据零百时间拟合等效油门 -> 驱动声浪
```

这样更接近电车场景：很多电车的电门-加速度关系相对线性，声浪可以跟“车辆实际加速感”绑定，而不是只跟踏板开度绑定。

## 数据链路

### 1. 车速计算加速度

位置：

```text
android-app/app/src/main/java/com/byd/superpulse/car/BydCarDataSource.kt
```

`onSpeedChanged(speed)` 收到车速后：

```text
speedDeltaMps = (currentSpeedKmh - previousSpeedKmh) / 3.6
dtSeconds = currentTime - previousTime
acceleration = speedDeltaMps / dtSeconds
```

随后对加速度做简单低通平滑：

```text
smoothedAcceleration = old * 0.72 + new * 0.28
```

并写入：

```kotlin
CarTelemetry.longitudinalAccelerationMps2
```

### 2. 零百时间拟合油门

位置：

```text
android-app/app/src/main/java/com/byd/superpulse/audio/EngineSoundSynthesizer.kt
```

公式：

```text
fullThrottleAcceleration = (100 / 3.6) / zeroToHundredSeconds
estimatedThrottle = accelerationMps2 / fullThrottleAcceleration
```

再限制到 `0.0~1.0`，并做平滑：

```text
smoothEstimatedThrottle += (estimatedThrottle - smoothEstimatedThrottle) * 0.18
```

这个 `smoothEstimatedThrottle` 替代原来的 `throttleDepth` 参与 RPM、FM、加速扫频和音量变化。

### 3. UI 输入零百时间

位置：

```text
android-app/app/src/main/res/layout/activity_main.xml
android-app/app/src/main/java/com/byd/superpulse/MainActivity.kt
```

界面新增了“零百加速时间（秒）”输入框，默认 `7.5s`，允许 `2.0~30.0s`。遥测显示中会展示：

- 速度
- 纵向加速度
- 零百时间
- 拟合油门
- 原始电门，仅显示，不参与声浪
- 刹车、档位、方向盘、电源状态等

## 运行方式

用 Android Studio 打开：

```text
L:\byd car driver\EvcarEnginePowerSoundSimulation\android-app
```

同步 Gradle 后运行到 BYD 车机环境或兼容模拟环境。因为这个副本没有 `local.properties`，Android Studio 会根据本机 SDK 自动生成；如果命令行构建，需要补充：

```properties
sdk.dir=C\:\\Users\\tzx\\AppData\\Local\\Android\\Sdk
```

## 为什么不用油门数据

声浪最终想表达的是“车正在用多大力加速”。对电车来说，踏板开度、动力响应、能量回收、驾驶模式和车速区间之间会有映射差异。直接用加速度拟合油门有几个好处：

- 更贴近乘员体感
- 不依赖厂商是否暴露真实电门深度
- 零百时间可以快速适配不同车型
- 低速、中速时的声浪变化更容易和实际推背感一致

## 后续优化

- 用 IMU 纵向加速度辅助车速差分，提升低速和高频响应。
- 用刹车或负加速度触发减速声、回火或能量回收声。
- 按车速区间修正满电门加速度，因为真实车辆高车速段加速度会下降。
- 加驾驶模式系数，例如 Eco/Normal/Sport 使用不同零百时间或响应曲线。

