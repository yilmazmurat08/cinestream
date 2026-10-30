"""CineStream logo açılışının sesi: tamamen burada üretilir (hazır müzik ya da ses dosyası yok, telif sorunu yok).
Zamanlama anim.html ile aynıdır: şeritler 0-1.8 sn, lazerler 0.95/1.10, logo çizimi 1.55-2.5, patlama 2.62,
çerçeve 3.12-3.87, yazı 4.0, parlama 4.85. Kullanım: python3 synth.py cikis.wav
"""
import math
import random
import struct
import sys
import wave

SR = 48000
DUR = 8.0
N = int(SR * DUR)
T_BURST = 2.62
TAU = 2 * math.pi

L = [0.0] * N
R = [0.0] * N
SL = [0.0] * N  # yankı (reverb) gönderimi
SRR = [0.0] * N
rnd = random.Random(11)


def put(i, v, pan=0.0, send=0.0):
    gl = math.sqrt(0.5 * (1 - pan))
    gr = math.sqrt(0.5 * (1 + pan))
    L[i] += v * gl
    R[i] += v * gr
    if send:
        SL[i] += v * gl * send
        SRR[i] += v * gr * send


def smooth(a, b, x):
    t = min(1.0, max(0.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def rng(t0, t1):
    return range(max(0, int(t0 * SR)), min(N, int(t1 * SR)))


# 1) Alt pad: patlamadan önce minör (gerilim), sonra majör (aydınlık)
for i in range(N):
    t = i / SR
    env = 0.10 * smooth(0, 1.6, t) + 0.04 * smooth(1.6, 2.6, t)
    env *= 1 - smooth(6.6, 8.0, t)
    trem = 1 + 0.15 * math.sin(TAU * 0.2 * t)
    x = smooth(T_BURST, T_BURST + 0.4, t)
    pre = (110.0, 130.81, 164.81)
    post = (110.0, 138.59, 164.81, 220.0)
    l = r = 0.0
    for f in pre:
        w = (1 - x) / 3
        l += w * math.sin(TAU * f * 1.0025 * t)
        r += w * math.sin(TAU * f * 0.9975 * t)
    for f in post:
        w = x / 4
        l += w * math.sin(TAU * f * 1.0025 * t)
        r += w * math.sin(TAU * f * 0.9975 * t)
    sub = 0.5 * math.sin(TAU * 55 * t) * (1 - 0.5 * x)
    L[i] += env * trem * (l + sub)
    R[i] += env * trem * (r + sub)

# 2) Hava (yumuşak gürültü yatağı)
for ch, buf in ((0, L), (1, R)):
    lp = 0.0
    for i in range(N):
        t = i / SR
        fc = 900 + 900 * smooth(T_BURST, T_BURST + 0.5, t)
        a = 1 - math.exp(-TAU * fc / SR)
        lp += a * (rnd.uniform(-1, 1) - lp)
        buf[i] += lp * 0.035 * smooth(0, 1.0, t) * (1 - smooth(6.8, 8.0, t))


def svf_noise(t0, t1, fc0, fc1, amp, pan0, pan1, q=0.5, send=0.3, shape=None):
    """Bant geçiren gürültü: süpürülen 'vınn' sesi (lazer, şerit, çerçeve)."""
    lp = bp = 0.0
    span = t1 - t0
    for i in rng(t0, t1):
        u = (i / SR - t0) / span
        fc = fc0 * (fc1 / fc0) ** u
        f = 2 * math.sin(math.pi * min(fc, SR / 6) / SR)
        hp = rnd.uniform(-1, 1) - lp - q * bp
        bp += f * hp
        lp += f * bp
        e = shape(u) if shape else math.sin(math.pi * u) ** 2
        put(i, bp * amp * e, pan0 + (pan1 - pan0) * u, send)


# 3) Enerji şeritleri: hafif ıslıklı vınlama + çıtırtı
for t0, t1, p0, p1, f0 in ((0.05, 1.62, -0.8, 0.0, 500), (0.18, 1.72, 0.8, 0.0, 650), (0.30, 1.80, -0.6, 0.1, 800)):
    for i in rng(t0, t1 + 0.2):
        t = i / SR
        u = min(1.0, (t - t0) / (t1 - t0))
        f = f0 + 900 * u * u + 40 * math.sin(TAU * 7 * t)
        e = 0.03 * math.sin(math.pi * min(1.0, (t - t0) / (t1 + 0.2 - t0))) ** 1.5
        put(i, e * math.sin(TAU * f * t + 2 * math.sin(TAU * 3 * t)), p0 + (p1 - p0) * u, 0.4)
    svf_noise(t0, t1 + 0.15, 1200, 3500, 0.10, p0, p1, q=0.35, send=0.3)
for _ in range(160):
    t = rnd.uniform(0.15, 2.5) if rnd.random() < 0.55 else rnd.uniform(1.55, 2.5)
    a = rnd.uniform(0.04, 0.14)
    pan = rnd.uniform(-0.7, 0.7)
    for k, i in enumerate(rng(t, t + 0.006)):
        put(i, a * rnd.uniform(-1, 1) * math.exp(-k / (SR * 0.0012)), pan, 0.2)

# 4) Lazerler: hızlı süpürme, soldan sağa / sağdan sola
svf_noise(0.93, 1.35, 4500, 700, 0.55, -0.9, 0.9, q=0.3, send=0.5, shape=lambda u: min(1.0, u * 8) * math.exp(-3 * u))
svf_noise(1.08, 1.52, 5000, 800, 0.55, 0.9, -0.9, q=0.3, send=0.5, shape=lambda u: min(1.0, u * 8) * math.exp(-3 * u))

# 5) Yükselen ses (logo çizilirken) ve ters zil
for i in rng(1.55, T_BURST):
    t = i / SR
    u = (t - 1.55) / (T_BURST - 1.55)
    cut = min(1.0, (T_BURST - t) / 0.006)
    ph = TAU * 150 * (2 ** (4 * u) - 1) / (4 * math.log(2)) * (T_BURST - 1.55)
    v = (0.10 * math.sin(ph) + 0.05 * math.sin(1.5 * ph)) * u * u * cut
    put(i, v, 0.0, 0.3)
svf_noise(1.55, T_BURST, 2000, 9000, 0.22, -0.3, 0.3, q=0.6, send=0.2, shape=lambda u: u ** 3 * min(1.0, (1 - u) * 300))
lp = 0.0
for i in rng(2.25, T_BURST):
    u = (i / SR - 2.25) / (T_BURST - 2.25)
    lp += 0.2 * (rnd.uniform(-1, 1) - lp)
    put(i, lp * 0.35 * u ** 4 * min(1.0, (1 - u) * 300), rnd.uniform(-0.3, 0.3), 0.2)

# 6) Patlama: alt bas darbe + gövde + çatırtı + kuyruk + ışıltılar
lp1 = lp2 = 0.0
lpc = 0.0
for i in rng(T_BURST, DUR):
    tau = i / SR - T_BURST
    att = min(1.0, tau / 0.002)
    ph = TAU * (38 * tau + 70 * (1 - math.exp(-9 * tau)) / 9)
    v = 0.95 * math.exp(-2.0 * tau) * math.sin(ph) * att
    n = rnd.uniform(-1, 1)
    lp1 += 0.15 * (n - lp1)
    lp2 += 0.15 * (lp1 - lp2)
    v += 0.9 * math.exp(-16 * tau) * lp2 * att
    lpc += 0.35 * (n - lpc)
    v += 0.35 * math.exp(-35 * tau) * (n - lpc)
    put(i, v, 0.0, 0.35)
for ch, buf in ((0, L), (1, R)):
    lp = 0.0
    for i in rng(T_BURST, DUR):
        tau = i / SR - T_BURST
        lp += 0.3 * (rnd.uniform(-1, 1) - lp)
        buf[i] += lp * 0.10 * math.exp(-1.4 * tau) * min(1.0, tau / 0.01)
for k in range(46):
    t = T_BURST + 0.03 + (rnd.random() ** 1.6) * 1.6
    f = rnd.uniform(2500, 7500)
    a = rnd.uniform(0.02, 0.06)
    dk = rnd.uniform(18, 32)
    pan = rnd.uniform(-0.9, 0.9)
    for i in rng(t, t + 0.25):
        tau = i / SR - t
        put(i, a * math.exp(-dk * tau) * math.sin(TAU * f * tau), pan, 0.6)


def bell(t0, base, amp, pan=0.0, send=0.7):
    parts = ((1.0, 1.0, 1.8), (2.0, 0.5, 1.3), (2.756, 0.32, 1.0), (5.404, 0.14, 0.6))
    for i in rng(t0, min(DUR, t0 + 3.0)):
        tau = i / SR - t0
        att = min(1.0, tau / 0.003)
        v = 0.0
        for ratio, a, d in parts:
            v += a * math.exp(-tau / d * 2.2) * math.sin(TAU * base * ratio * tau)
        put(i, amp * att * v, pan, send)


# 7) Simge çerçevesi: iki yönlü vınlama, buluşunca zil
svf_noise(3.10, 3.90, 700, 2600, 0.16, -0.7, -0.1, q=0.45, send=0.4)
svf_noise(3.10, 3.90, 760, 2700, 0.16, 0.7, 0.1, q=0.45, send=0.4)
bell(3.87, 1046.5, 0.08)

# 8) Yazı: üç zil (Mi-Si-Mi) ve parlama ışıltısı
bell(4.00, 659.25, 0.19, -0.2)
bell(4.18, 987.77, 0.14, 0.2)
bell(4.36, 1318.5, 0.10, 0.0)
svf_noise(4.85, 5.45, 5000, 9000, 0.06, -0.6, 0.6, q=0.5, send=0.6)
for i in rng(4.85, 5.45):
    u = (i / SR - 4.85) / 0.6
    put(i, 0.015 * math.sin(math.pi * u) * math.sin(TAU * (2000 + 2000 * u) * (i / SR)), -0.5 + u, 0.6)


# 9) Basit yankı (Schroeder): 4 tarak + 2 tüm geçiren süzgeç
def reverb(x, seed_shift):
    combs = [int(d * SR / 44100) + seed_shift for d in (1557, 1617, 1491, 1422)]
    out = [0.0] * N
    for d in combs:
        buf = [0.0] * d
        idx = 0
        filt = 0.0
        for i in range(N):
            y = buf[idx]
            filt = y * 0.8 + filt * 0.2
            buf[idx] = x[i] + filt * 0.84
            idx += 1
            if idx == d:
                idx = 0
            out[i] += y * 0.25
    for d in (int(225 * SR / 44100), int(556 * SR / 44100)):
        buf = [0.0] * d
        idx = 0
        for i in range(N):
            b = buf[idx]
            y = -out[i] + b
            buf[idx] = out[i] + b * 0.5
            idx += 1
            if idx == d:
                idx = 0
            out[i] = y
    return out


wl = reverb(SL, 0)
wr = reverb(SRR, 23)
for i in range(N):
    L[i] += 0.55 * wl[i]
    R[i] += 0.55 * wr[i]

# 10) Son: giriş/çıkış yumuşatma, yumuşak sınırlama, -1 dB tepe
for i in range(N):
    t = i / SR
    g = min(1.0, t / 0.02) * (1 - smooth(7.0, 8.0, t))
    L[i] = math.tanh(1.2 * L[i] * g) / math.tanh(1.2)
    R[i] = math.tanh(1.2 * R[i] * g) / math.tanh(1.2)
peak = max(max(abs(v) for v in L), max(abs(v) for v in R)) or 1.0
k = 0.89 / peak
with wave.open(sys.argv[1] if len(sys.argv) > 1 else "ses.wav", "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes(b"".join(struct.pack("<hh", int(L[i] * k * 32767), int(R[i] * k * 32767)) for i in range(N)))
print("ses hazır, tepe", round(peak, 3))
