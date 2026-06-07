// ── V8 Cross-Plane Engine Sound Synthesizer v2 ──
// Architecture: order-tracking harmonic synthesis + mechanical noise layer
// References:
//   - Baldan et al. "Physically informed car engine sound synthesis" (2014)
//   - Order-based approach: each engine order synthesized independently
//   - Source-path-contribution: mechanical path || exhaust pulse path || intake path
//
// Audio graph:
//   [Mechanical] noiseMech → mechBP → mechGain ──────────────┐
//   [Exhaust]   oscFiring(4th) → gainFiring →                │
//               oscFiringDetune → gainFiringDetune →         │
//               oscSub(2nd) → gainSub →                      ├→ masterOut
//               oscHarm2(8th) → gainHarm2 →                  │
//               oscHarm3(12th) → gainHarm3 →                 │
//               noiseExhaust → noiseGain →                   │
//                 all → synthMaster → exhaustRes → v8LP ────┘
//   [Intake]    noiseIntake → intakeHP → intakeGain ─────────┘

const V8_SAMPLE_URL =
  "https://upload.wikimedia.org/wikipedia/commons/8/8a/Ferrari_250_GTO%2C_Engine_Sound.ogg";

// ── DOM refs ──
const accelerationInput = document.getElementById("acceleration");
const accelerationValue = document.getElementById("accelerationValue");
const zeroToHundredInput = document.getElementById("zeroToHundred");
const throttleValue = document.getElementById("throttleValue");
const gearValue = document.getElementById("gearValue");
const loadValue = document.getElementById("loadValue");
const rpmValue = document.getElementById("rpm");
const statusText = document.getElementById("status");
const sampleEl = document.getElementById("engineSample");
const scene = document.getElementById("scene");
const mechNoiseSlider = document.getElementById("mechNoise");
const mechNoiseValue = document.getElementById("mechNoiseValue");
const exhaustNoiseSlider = document.getElementById("exhaustNoise");
const exhaustNoiseValue = document.getElementById("exhaustNoiseValue");
const intakeNoiseSlider = document.getElementById("intakeNoise");
const intakeNoiseValue = document.getElementById("intakeNoiseValue");
const exhaustToneSlider = document.getElementById("exhaustTone");
const exhaustToneValue = document.getElementById("exhaustToneValue");
const sceneCtx = scene.getContext("2d");
const tach = document.getElementById("tach");
const tachCtx = tach.getContext("2d");

// ── State ──
let audioContext;
let sampleSource, sampleGain;
let running = false;
let smoothThrottle = 0;
let smoothAcceleration = 0;
let rpm = 900;
let phase = 0;
let burblePhase = 0;

// ── Exhaust path nodes ──
let oscFiring, oscFiringDetune;   // 4th order: firing frequency (RPM*4/60)
let oscSub;                       // 2nd order: V8 rumble (RPM*2/60)
let oscHarm2, oscHarm3;          // 8th, 12th orders
let gainFiring, gainFiringDetune, gainSub, gainHarm2, gainHarm3;
let noiseExhaust, noiseExhaustGain;
let synthMaster;
let exhaustResonance;
let v8Filter;

// ── Mechanical path nodes (valve train, timing chain, piston movement) ──
let noiseMech, mechGain, mechFilter;

// ── Intake path nodes (air suction whisper) ──
let noiseIntake, intakeGain, intakeFilter;

// ── Master output ──
let masterOut;

// ── Helpers ──
function makeNoiseBuffer(ctx, durationSec) {
  const len = ctx.sampleRate * durationSec;
  const buffer = ctx.createBuffer(1, len, ctx.sampleRate);
  const data = buffer.getChannelData(0);
  for (let i = 0; i < len; i += 1) {
    data[i] = (Math.random() * 2 - 1) * 0.5;
  }
  return buffer;
}

// ── Audio graph setup ──
async function ensureAudio() {
  if (audioContext) return;

  audioContext = new AudioContext();

  // ═══ Exhaust path ═══

  // 4th order: main firing tone (sawtooth for rich harmonics)
  oscFiring = audioContext.createOscillator();
  oscFiring.type = "sawtooth";
  oscFiring.frequency.value = 60;

  oscFiringDetune = audioContext.createOscillator();
  oscFiringDetune.type = "sawtooth";
  oscFiringDetune.frequency.value = 60;

  // 2nd order: V8 cross-plane sub-rumble
  oscSub = audioContext.createOscillator();
  oscSub.type = "sine";
  oscSub.frequency.value = 30;

  // 8th order: harmonic enrichment
  oscHarm2 = audioContext.createOscillator();
  oscHarm2.type = "sawtooth";
  oscHarm2.frequency.value = 120;

  // 12th order: high-RPM zing
  oscHarm3 = audioContext.createOscillator();
  oscHarm3.type = "sine";
  oscHarm3.frequency.value = 180;

  // Per-order gains
  gainFiring = audioContext.createGain();
  gainFiring.gain.value = 0;
  gainFiringDetune = audioContext.createGain();
  gainFiringDetune.gain.value = 0;
  gainSub = audioContext.createGain();
  gainSub.gain.value = 0;
  gainHarm2 = audioContext.createGain();
  gainHarm2.gain.value = 0;
  gainHarm3 = audioContext.createGain();
  gainHarm3.gain.value = 0;

  // Exhaust noise (crackle / texture)
  noiseExhaust = audioContext.createBufferSource();
  noiseExhaust.buffer = makeNoiseBuffer(audioContext, 3);
  noiseExhaust.loop = true;
  noiseExhaustGain = audioContext.createGain();
  noiseExhaustGain.gain.value = 0;

  // Exhaust resonance filter (peaking ~130 Hz, simulates exhaust pipe)
  exhaustResonance = audioContext.createBiquadFilter();
  exhaustResonance.type = "peaking";
  exhaustResonance.frequency.value = 130;
  exhaustResonance.Q.value = 2.0;
  exhaustResonance.gain.value = 4;

  // Exhaust lowpass (cutoff tracks RPM)
  v8Filter = audioContext.createBiquadFilter();
  v8Filter.type = "lowpass";
  v8Filter.frequency.value = 900;
  v8Filter.Q.value = 0.55;

  // Exhaust master
  synthMaster = audioContext.createGain();
  synthMaster.gain.value = 0;

  // ═══ Mechanical path (engine body sound) ═══
  noiseMech = audioContext.createBufferSource();
  noiseMech.buffer = makeNoiseBuffer(audioContext, 5);
  noiseMech.loop = true;

  mechFilter = audioContext.createBiquadFilter();
  mechFilter.type = "bandpass";
  mechFilter.frequency.value = 380;  // center: lower, in valve train range / timing chain range
  mechFilter.Q.value = 2.5;          // narrow: create resonant hum, not white noise

  mechGain = audioContext.createGain();
  mechGain.gain.value = 0;

  // ═══ Intake path (air suction) ═══
  noiseIntake = audioContext.createBufferSource();
  noiseIntake.buffer = makeNoiseBuffer(audioContext, 4);
  noiseIntake.loop = true;

  intakeFilter = audioContext.createBiquadFilter();
  intakeFilter.type = "highpass";
  intakeFilter.frequency.value = 1800;  // only high-frequency hiss
  intakeFilter.Q.value = 0.5;

  intakeGain = audioContext.createGain();
  intakeGain.gain.value = 0;

  // ═══ Sample path ═══
  sampleGain = audioContext.createGain();
  sampleGain.gain.value = 0;

  // ═══ Master output ═══
  masterOut = audioContext.createGain();
  masterOut.gain.value = 0.85;  // overall output trim

  // ═══ Routing ═══

  // Exhaust: oscillators → per-gain → synthMaster → exhaustRes → v8Filter → master
  oscFiring.connect(gainFiring);
  oscFiringDetune.connect(gainFiringDetune);
  oscSub.connect(gainSub);
  oscHarm2.connect(gainHarm2);
  oscHarm3.connect(gainHarm3);
  noiseExhaust.connect(noiseExhaustGain);

  gainFiring.connect(synthMaster);
  gainFiringDetune.connect(synthMaster);
  gainSub.connect(synthMaster);
  gainHarm2.connect(synthMaster);
  gainHarm3.connect(synthMaster);
  noiseExhaustGain.connect(synthMaster);

  synthMaster.connect(exhaustResonance);
  exhaustResonance.connect(v8Filter);
  v8Filter.connect(masterOut);

  // Mechanical: noise → bandpass → gain → master (parallel, not through exhaust)
  noiseMech.connect(mechFilter);
  mechFilter.connect(mechGain);
  mechGain.connect(masterOut);

  // Intake: noise → highpass → gain → master
  noiseIntake.connect(intakeFilter);
  intakeFilter.connect(intakeGain);
  intakeGain.connect(masterOut);

  // Sample: through exhaust filter for consistency, then direct to master
  sampleSource = audioContext.createMediaElementSource(sampleEl);
  sampleEl.volume = 0; // mute browser's direct audio output; only Web Audio path
  sampleSource.connect(sampleGain);
  sampleGain.connect(masterOut);

  // Output
  masterOut.connect(audioContext.destination);

  // Start all sources
  oscFiring.start();
  oscFiringDetune.start();
  oscSub.start();
  oscHarm2.start();
  oscHarm3.start();
  noiseExhaust.start();
  noiseMech.start();
  noiseIntake.start();
}

// ── Gear estimator ──
function currentGear(t, r) {
  if (t < 0.04) return "N";
  if (r < 2400) return "1";
  if (r < 3900) return "2";
  if (r < 5600) return "3";
  return "S";
}

// ── Acceleration → virtual throttle ──
function estimateThrottleFromAcceleration(accelerationMps2, zeroToHundredSeconds) {
  const t100 = Math.min(30, Math.max(2, zeroToHundredSeconds));
  const fullG = (100 / 3.6) / t100;
  return Math.min(1, Math.max(0, accelerationMps2 / fullG));
}

// ── Main update loop ──
function updateAudio() {
  const accelerationMps2 = Number(accelerationInput.value);
  const zeroToHundredSeconds = Number(zeroToHundredInput.value) || 7.5;
  const dt = 1 / 60;

  smoothAcceleration += (accelerationMps2 - smoothAcceleration) * 0.12;
  const t = estimateThrottleFromAcceleration(smoothAcceleration, zeroToHundredSeconds);
  smoothThrottle += (t - smoothThrottle) * 0.08;

  const targetRpm = smoothAcceleration > 0.01 ? (900 + smoothThrottle * 6900) : 0;
  rpm += (targetRpm - rpm) * 0.07;

  if (audioContext) {
    const now = audioContext.currentTime;
    const rpmNorm = (rpm - 900) / 6900;  // 0 at idle, 1 at redline
    const enabled = running && smoothAcceleration > 0.03;

    const mechTrim = Number(mechNoiseSlider.value) / 100;
    const exhaustTrim = Number(exhaustNoiseSlider.value) / 100;
    const intakeTrim = Number(intakeNoiseSlider.value) / 100;
    const exhaustToneTrim = Number(exhaustToneSlider.value) / 100;

    // ═══ Engine orders (frequencies) ═══
    // 4-stroke V8: 4 firing events per crank revolution
    const order4  = rpm / 60 * 4;   // 4th order: firing frequency
    const order2  = rpm / 60 * 2;   // 2nd order: V8 cross-plane rumble
    const order8  = rpm / 60 * 8;   // 8th order
    const order12 = rpm / 60 * 12;  // 12th order

    oscFiring.frequency.setTargetAtTime(order4, now, 0.04);
    oscFiringDetune.frequency.setTargetAtTime(order4 * 1.006, now, 0.04);
    oscSub.frequency.setTargetAtTime(order2, now, 0.05);
    oscHarm2.frequency.setTargetAtTime(order8, now, 0.04);
    oscHarm3.frequency.setTargetAtTime(order12, now, 0.04);

    // ═══ Burble: tanh-shaped pulse modulation (not smooth sine) ═══
    //
    // Cross-plane V8: exhaust pulses within each bank are uneven
    // (270°-180°-180°-270°). This creates amplitude modulation at
    // crank frequency (RPM/60). We use tanh to shape the sine into
    // sharper pulses at idle, smoothing to near-sine at high RPM.
    burblePhase += 2 * Math.PI * (rpm / 60) * dt;
    if (burblePhase > Math.PI * 2) burblePhase -= Math.PI * 2;

    // Sharpness: 4.5 at idle (strong pulse shaping), 1.5 at redline (gentle)
    const burbleSharpness = 1.5 + (1 - rpmNorm) * 2.0;
    const burbleRaw = Math.sin(burblePhase);
    const burblePulse = Math.tanh(burbleRaw * burbleSharpness);

    // Also mix in 2× crank for the secondary exhaust rhythm
    const burbleRaw2 = Math.sin(burblePhase * 2);
    const burblePulse2 = Math.tanh(burbleRaw2 * burbleSharpness * 0.7);

    // Burble depth: 15% at idle, fades to ~5% at redline
    const burbleDepth = 0.15 * (1 - rpmNorm * 0.82);
    const burble = 1 + burblePulse * burbleDepth + burblePulse2 * burbleDepth * 0.3;

    // ═══ Exhaust path gains ═══
    // Main firing: reduced from 0.18 → 0.10 base
    const firingBase = enabled ? 0.10 : 0;
    // Detune copy: adds thickness without dominating
    const firingDetuneBase = firingBase * 0.30;

    // 2nd order sub-rumble: prominent at idle, fades at high RPM
    const subFactor = enabled ? 0.14 * (1 - rpmNorm * 0.80) : 0;

    // 8th order: grows with RPM (adds aggression)
    const harm2Factor = enabled ? 0.03 + rpmNorm * 0.10 : 0;

    // 12th order: quadratic growth (high-RPM scream)
    const harm3Factor = enabled ? rpmNorm * rpmNorm * 0.08 : 0;

    gainFiring.gain.setTargetAtTime(firingBase * burble, now, 0.06);
    gainFiringDetune.gain.setTargetAtTime(firingDetuneBase * burble, now, 0.06);
    gainSub.gain.setTargetAtTime(subFactor, now, 0.08);
    gainHarm2.gain.setTargetAtTime(harm2Factor, now, 0.06);
    gainHarm3.gain.setTargetAtTime(harm3Factor, now, 0.06);

    // Exhaust crackle noise: subtle at mid, pronounced at >70% throttle
    const exhaustNoiseFactor = enabled
      ? 0.004 + smoothThrottle * 0.015 + Math.max(0, smoothThrottle - 0.7) * 0.025
      : 0;
    noiseExhaustGain.gain.setTargetAtTime(exhaustNoiseFactor * exhaustTrim, now, 0.04);

    // Exhaust master volume: reduced from 0.55-0.85 → 0.30-0.55
    const synthVol = enabled ? 0.30 + smoothThrottle * 0.25 : 0;
    synthMaster.gain.setTargetAtTime(synthVol * exhaustToneTrim, now, 0.07);

    // ═══ Mechanical path gains (valve train / rotating assembly) ═══
    // This is the "engine running" sound — continuous, mid-frequency,
    // present even at idle. It's the primary carrier of realism.
    const mechVol = enabled ? 0.04 + smoothThrottle * 0.04 : 0;
    mechGain.gain.setTargetAtTime(mechVol * mechTrim, now, 0.06);

    // Mechanical filter center: shifts up with RPM
    // Idle: ~380 Hz (valve train / timing chain range)
    // Redline: ~2200 Hz
    const mechCenter = 380 + rpm * 0.24;
    mechFilter.frequency.setTargetAtTime(mechCenter, now, 0.08);

    // ═══ Intake path (air suction whisper) ═══
    const intakeVol = enabled ? smoothThrottle * smoothThrottle * 0.06 : 0;
    intakeGain.gain.setTargetAtTime(intakeVol * intakeTrim, now, 0.08);

    // ═══ Exhaust filter: cutoff tracks RPM ═══
    const cutoff = 160 + rpm * 0.58;
    v8Filter.frequency.setTargetAtTime(cutoff, now, 0.06);
    v8Filter.Q.setTargetAtTime(0.55 + rpmNorm * 0.5, now, 0.1);

    // Exhaust pipe resonance
    exhaustResonance.gain.setTargetAtTime(3 + smoothThrottle * 7, now, 0.1);

    // ═══ Sample overlay (very subtle, just for texture) ═══
    if (enabled && sampleEl.duration) {
      sampleEl.playbackRate = 0.55 + smoothThrottle * 1.3;
      sampleGain.gain.setTargetAtTime(0.03 + smoothThrottle * 0.05, now, 0.1);
    } else {
      sampleGain.gain.setTargetAtTime(0, now, 0.2);
    }
  }

  // ── UI updates ──
  accelerationValue.textContent = `${smoothAcceleration.toFixed(2)} m/s²`;
  throttleValue.textContent = `${Math.round(smoothThrottle * 100)}%`;
  rpmValue.textContent = String(Math.round(rpm));
  gearValue.textContent = currentGear(smoothThrottle, rpm);
  loadValue.textContent = smoothThrottle.toFixed(2);
  mechNoiseValue.textContent = `${Math.round(Number(mechNoiseSlider.value))}%`;
  exhaustNoiseValue.textContent = `${Math.round(Number(exhaustNoiseSlider.value))}%`;
  intakeNoiseValue.textContent = `${Math.round(Number(intakeNoiseSlider.value))}%`;
  exhaustToneValue.textContent = `${Math.round(Number(exhaustToneSlider.value))}%`;

  drawTach();
  drawScene();
  requestAnimationFrame(updateAudio);
}

// ── Tachometer ──
function drawTach() {
  const w = tach.width;
  const h = tach.height;
  tachCtx.clearRect(0, 0, w, h);
  tachCtx.lineWidth = 14;
  tachCtx.strokeStyle = "#293137";
  tachCtx.beginPath();
  tachCtx.arc(w / 2, h * 0.92, 112, Math.PI, 0);
  tachCtx.stroke();

  const pct = Math.min(1, rpm / 7800);
  const end = Math.PI + pct * Math.PI;
  const grad = tachCtx.createLinearGradient(36, 0, w - 36, 0);
  grad.addColorStop(0, "#5aa7c7");
  grad.addColorStop(0.55, "#d7b46a");
  grad.addColorStop(1, "#df5045");
  tachCtx.strokeStyle = grad;
  tachCtx.beginPath();
  tachCtx.arc(w / 2, h * 0.92, 112, Math.PI, end);
  tachCtx.stroke();

  const needle = Math.PI + pct * Math.PI;
  const nx = w / 2 + Math.cos(needle) * 98;
  const ny = h * 0.92 + Math.sin(needle) * 98;
  tachCtx.strokeStyle = "#f4f1ea";
  tachCtx.lineWidth = 4;
  tachCtx.beginPath();
  tachCtx.moveTo(w / 2, h * 0.92);
  tachCtx.lineTo(nx, ny);
  tachCtx.stroke();

  tachCtx.fillStyle = "#f4f1ea";
  tachCtx.font = "700 18px Segoe UI, Arial";
  tachCtx.textAlign = "center";
  tachCtx.fillText("x1000", w / 2, h - 12);
}

// ── Scene canvas ──
function drawScene() {
  const w = scene.width;
  const h = scene.height;
  phase += 0.01 + smoothThrottle * 0.08;
  sceneCtx.clearRect(0, 0, w, h);

  const sky = sceneCtx.createLinearGradient(0, 0, 0, h);
  sky.addColorStop(0, "#10161b");
  sky.addColorStop(0.55, "#171717");
  sky.addColorStop(1, "#060606");
  sceneCtx.fillStyle = sky;
  sceneCtx.fillRect(0, 0, w, h);

  sceneCtx.fillStyle = "#1f2427";
  for (let i = 0; i < 9; i += 1) {
    const x = ((i * 130 - phase * 80) % (w + 160)) - 80;
    sceneCtx.fillRect(x, 250, 84, 5);
  }

  const carX = w * 0.5;
  const carY = h * 0.62;
  const shake = running ? Math.sin(phase * 5) * smoothThrottle * 4 : 0;
  sceneCtx.save();
  sceneCtx.translate(carX, carY + shake);
  sceneCtx.fillStyle = "#d7b46a";
  sceneCtx.beginPath();
  sceneCtx.moveTo(-220, 32);
  sceneCtx.quadraticCurveTo(-150, -36, -52, -42);
  sceneCtx.lineTo(78, -42);
  sceneCtx.quadraticCurveTo(170, -34, 224, 28);
  sceneCtx.lineTo(214, 48);
  sceneCtx.lineTo(-224, 48);
  sceneCtx.closePath();
  sceneCtx.fill();
  sceneCtx.fillStyle = "#101417";
  sceneCtx.beginPath();
  sceneCtx.moveTo(-82, -34);
  sceneCtx.lineTo(-30, -78);
  sceneCtx.lineTo(68, -76);
  sceneCtx.lineTo(118, -34);
  sceneCtx.closePath();
  sceneCtx.fill();
  sceneCtx.fillStyle = "#060606";
  for (const x of [-140, 142]) {
    sceneCtx.beginPath();
    sceneCtx.arc(x, 50, 42, 0, Math.PI * 2);
    sceneCtx.fill();
    sceneCtx.fillStyle = "#5c6970";
    sceneCtx.beginPath();
    sceneCtx.arc(x, 50, 18, 0, Math.PI * 2);
    sceneCtx.fill();
    sceneCtx.fillStyle = "#060606";
  }
  sceneCtx.fillStyle = `rgba(223, 80, 69, ${0.08 + smoothThrottle * 0.55})`;
  sceneCtx.beginPath();
  sceneCtx.ellipse(-248, 42, 68 + smoothThrottle * 120, 13 + smoothThrottle * 16, 0, 0, Math.PI * 2);
  sceneCtx.fill();
  sceneCtx.restore();
}

// ── Controls ──
document.getElementById("start").addEventListener("click", async () => {
  await ensureAudio();
  await audioContext.resume();
  running = true;
  statusText.textContent = "RUN";
  rpm = 1400; // startup burst
  setTimeout(() => { rpm = 900; }, 400);

  sampleEl.play().catch(() => {
    statusText.textContent = "SYNTH";
  });
});

document.getElementById("stop").addEventListener("click", () => {
  running = false;
  statusText.textContent = "IDLE";
  sampleEl.pause();
});

sampleEl.addEventListener("error", () => {
  statusText.textContent = "SYNTH";
});

console.info("V8 Cross-Plane Synthesizer v2 ready.");
requestAnimationFrame(updateAudio);
