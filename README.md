# EvcarEnginePowerSoundSimulation

A project for studying EV simulated combustion engine sound. Contains both a web frontend demo and an Android app.

[中文版](./README_CN.md)

## Overview

| Module | Description |
|---|---|
| **Web Frontend** | Acceleration-to-throttle fitting → Web Audio synthesis/sample playback |
| **Android App** | BYD car data source + accelerometer-based synthesis with full algorithm pipeline |

## Core Algorithm

Instead of reading throttle directly, estimate equivalent throttle from acceleration:

```text
fullThrottleAccel = (100 / 3.6) / zeroToHundredSeconds
estimatedThrottle = currentAccel / fullThrottleAccel
```

Clamp to `[0, 1]`, then map to RPM, volume, filter cutoff, sample playback rate, and harmonic/FM synthesis parameters.

This approach suits EVs because the perceived acceleration experience often matters more than raw pedal data for sound feedback.

## Web Frontend

```bash
cd frontend
python3 -m http.server 4174
```

Open: http://127.0.0.1:4174

Details: [frontend/FRONTEND.md](./frontend/FRONTEND.md)

## Android App

Open `android-app/` in Android Studio, sync Gradle, and run on a BYD vehicle environment or compatible emulator.

Details: [ANDROID_APP.md](./ANDROID_APP.md)

## Directory Structure

```
EvcarEnginePowerSoundSimulation/
  frontend/          # Web demo
  android-app/       # Android project
  README.md          # This file (English)
  README_CN.md       # Chinese version
```

## Audio Assets

The web demo references:
- `Ferrari 250 GTO, Engine Sound.ogg`
- Source: Wikimedia Commons
- License: CC BY 4.0
- URL: https://commons.wikimedia.org/wiki/File:Ferrari_250_GTO,_Engine_Sound.ogg

Retain attribution and license notice for production use. This project is for learning and demonstration.
