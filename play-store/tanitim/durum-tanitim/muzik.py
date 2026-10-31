"""CineStream durum tanıtımının arka plan müziği: sinematik elektronik, 120 BPM, La minör.
Tamamen burada üretilir (hazır müzik ya da örnek ses yok, telif sorunu yok).
Zamanlama videoya bağlı: logo videosundaki ışık patlaması 2,62 sn'de -> vuruş (drop) orada girer;
ölçüler 2 sn; uygulama turuna geçişte (10,62) zil; son vuruş 16,62; video 17,6 sn.
Kullanım: python3 muzik.py cikis.wav
"""
import math
import random
import struct
import sys
import wave

SR = 48000
DUR = 17.6
N = int(SR * DUR)
TAU = 2 * math.pi
DROP = 2.62
BAR = 2.0
FINAL = DROP + 7 * BAR  # 16.62
rnd = random.Random(2026)

L = [0.0] * N
R = [0.0] * N
RVL = [0.0] * N  # yankı gönderimi
RVR = [0.0] * N


def mtof(m):
    return 440.0 * 2 ** ((m - 69) / 12)


TAB = 2048
SAW = [sum(math.sin(TAU * h * i / TAB) / h for h in range(1, 40)) * (2 / math.pi) for i in range(TAB)]
SQR = [sum(math.sin(TAU * h * i / TAB) / h for h in range(1, 22, 2)) * (4 / math.pi) for i in range(TAB)]

# Akorlar (ölçü başına): giriş Am; drop'tan sonra Am C G F | Am F G | son vuruş Am
CHORDS = {
    "Am": ([57, 60, 64], 33, [69, 72, 76, 81]),
    "C": ([55, 60, 64], 36, [67, 72, 76, 79]),
    "G": ([55, 59, 62], 31, [67, 71, 74, 79]),
    "F": ([53, 57, 60], 29, [65, 69, 72, 77]),
}
PROG = ["Am", "C", "G", "F", "Am", "F", "G"]
bars = [(DROP + k * BAR, DROP + (k + 1) * BAR, c) for k, c in enumerate(PROG)]


def idx(t):
    return max(0, min(N, int(t * SR)))


def smooth(a, b, x):
    t = min(1.0, max(0.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


kicks = [DROP + 0.5 * k for k in range(int((FINAL - DROP) / 0.5))] + [FINAL]


def duck(t):
    """Vuruşta pad/bas/arp hafifçe alçalır (pompalama)."""
    last = None
    for k in kicks:
        if k <= t:
            last = k
        else:
            break
    if last is None:
        return 1.0
    return 1.0 - 0.5 * math.exp(-(t - last) / 0.11)


DUCK = [1.0] * N
for i in range(idx(DROP), N):
    DUCK[i] = duck(i / SR)

# ---------- Pad (3 sesli akor, her nota 3 ayrık ses, sonra süzgeç)
padL = [0.0] * N
padR = [0.0] * N
segments = [(0.0, DROP, "Am")] + bars + [(FINAL, DUR, "Am")]
for t0, t1, name in segments:
    notes = CHORDS[name][0]
    a, b = idx(max(0.0, t0 - 0.06)), idx(min(DUR, t1 + 0.12))
    for m in notes:
        for det, pl, pr in ((-0.08, 0.85, 0.35), (0.0, 0.6, 0.6), (0.08, 0.35, 0.85)):
            f = mtof(m) * 2 ** (det / 12)
            inc = f * TAB / SR
            ph = rnd.random() * TAB
            for i in range(a, b):
                t = i / SR
                env = smooth(t0 - 0.06, t0 + (0.9 if t0 == 0.0 else 0.08), t) * (1 - smooth(t1, t1 + 0.12, t))
                j = int(ph)
                v = SAW[j & 2047] * env
                padL[i] += v * pl
                padR[i] += v * pr
                ph += inc
                if ph >= TAB:
                    ph -= TAB


def svf_lowpass(buf, cutoff_at, q=0.7):
    lp = bp = 0.0
    out = [0.0] * N
    for i in range(N):
        fc = cutoff_at(i / SR)
        f = 2 * math.sin(math.pi * min(fc, SR / 6) / SR)
        hp = buf[i] - lp - q * bp
        bp += f * hp
        lp += f * bp
        out[i] = lp
    return out


def pad_cut(t):
    if t < DROP:
        return 300 + 2200 * smooth(0.0, DROP, t) ** 1.5
    if t < FINAL:
        return 2300 + 400 * math.sin(TAU * (t - DROP) / 8.0)
    return 3200 * math.exp(-(t - FINAL) * 1.2) + 600


padL = svf_lowpass(padL, pad_cut)
padR = svf_lowpass(padR, pad_cut)
for i in range(N):
    g = 0.055 * DUCK[i]
    L[i] += padL[i] * g
    R[i] += padR[i] * g
    RVL[i] += padL[i] * g * 0.5
    RVR[i] += padR[i] * g * 0.5

# ---------- Bas (sekizlik, testere + alt sinüs, süzgeç zarfı)
bass = [0.0] * N
for t0, t1, name in bars:
    root = CHORDS[name][1]
    for k in range(8):
        s = t0 + k * 0.25
        f = mtof(root + (12 if k % 2 else 0))
        inc = f * TAB / SR
        ph = 0.0
        lp = 0.0
        for i in range(idx(s), idx(s + 0.235)):
            tt = i / SR - s
            env = min(1.0, tt / 0.003) * (0.55 + 0.45 * math.exp(-tt / 0.06)) * min(1.0, (0.235 - tt) / 0.02)
            fc = 500 + 1600 * math.exp(-tt / 0.05)
            a = 1 - math.exp(-TAU * fc / SR)
            x = SAW[int(ph) & 2047] * 0.6 + math.sin(TAU * f * 0.5 * tt) * 0.8
            lp += a * (x - lp)
            bass[i] += lp * env
            ph += inc
            if ph >= TAB:
                ph -= TAB
# son vuruşta uzun alt nota
for i in range(idx(FINAL), N):
    tt = i / SR - FINAL
    bass[i] += math.sin(TAU * 55.0 * tt) * math.exp(-tt * 1.6) * min(1.0, tt / 0.004)
for i in range(N):
    v = bass[i] * 0.16 * DUCK[i]
    L[i] += v
    R[i] += v

# ---------- Arp (on altılık, kare dalga, ping-pong yankı)
arp = [0.0] * N
pattern = [0, 1, 2, 3, 2, 1, 0, 2]
arp_segments = [(0.55, DROP, "Am")] + bars
for t0, t1, name in arp_segments:
    tones = CHORDS[name][2]
    n = int(round((t1 - t0) / 0.125))
    for k in range(n):
        s = t0 + k * 0.125
        m = tones[pattern[k % 8]]
        f = mtof(m)
        inc = f * TAB / SR
        ph = 0.0
        vel = 0.75 + 0.25 * (1 if k % 4 == 0 else 0)
        if s < DROP:
            vel *= 0.35 + 0.65 * smooth(0.55, DROP, s)
        lp = 0.0
        for i in range(idx(s), idx(s + 0.12)):
            tt = i / SR - s
            env = min(1.0, tt / 0.002) * math.exp(-tt / 0.07) * vel
            fc = 2600 if s >= DROP else 900 + 1600 * smooth(0.55, DROP, s)
            a = 1 - math.exp(-TAU * fc / SR)
            lp += a * (SQR[int(ph) & 2047] - lp)
            arp[i] += lp * env
            ph += inc
            if ph >= TAB:
                ph -= TAB
d = int(0.375 * SR)
dl = [0.0] * N
dr = [0.0] * N
for i in range(N):
    x = arp[i]
    yl = dl[i - d] if i >= d else 0.0
    yr = dr[i - d] if i >= d else 0.0
    dl[i] = x + yr * 0.38
    dr[i] = yl * 0.38
    g = 0.045 * DUCK[i]
    L[i] += (x + 0.55 * yl) * g
    R[i] += (x + 0.55 * yr) * g
    RVL[i] += x * g * 0.4
    RVR[i] += x * g * 0.4


# ---------- Davul
def add(i, l, r=None, send=0.0):
    if 0 <= i < N:
        L[i] += l
        R[i] += l if r is None else r
        if send:
            RVL[i] += l * send
            RVR[i] += (l if r is None else r) * send


def kick(t0, amp=0.55):
    ph = 0.0
    for i in range(idx(t0), idx(t0 + 0.45)):
        tt = i / SR - t0
        f = 45 + 95 * math.exp(-tt * 28)
        ph += TAU * f / SR
        v = math.sin(ph) * math.exp(-tt * 6.5) * min(1.0, tt / 0.0015)
        if tt < 0.004:
            v += rnd.uniform(-1, 1) * 0.25
        add(i, v * amp)


def clap(t0, amp=0.22):
    lp = bp = 0.0
    for i in range(idx(t0), idx(t0 + 0.28)):
        tt = i / SR - t0
        env = sum(math.exp(-(tt - o) / 0.006) for o in (0.0, 0.009, 0.018) if tt >= o) * 0.5 + math.exp(-tt / 0.11) * 0.6
        f = 2 * math.sin(math.pi * 1500 / SR)
        hp = rnd.uniform(-1, 1) - lp - 0.9 * bp
        bp += f * hp
        lp += f * bp
        add(i, bp * env * amp, bp * env * amp * 0.9, send=0.35)


def hat(t0, amp=0.07, decay=0.03, pan=0.15):
    lp = 0.0
    for i in range(idx(t0), idx(t0 + decay * 5)):
        tt = i / SR - t0
        n = rnd.uniform(-1, 1)
        lp += 0.55 * (n - lp)
        v = (n - lp) * math.exp(-tt / decay) * amp
        add(i, v * (1 - pan), v * (1 + pan))


def crash(t0, amp=0.16, length=1.8):
    lp = 0.0
    parts = [(rnd.uniform(3000, 9000), rnd.random() * TAU) for _ in range(6)]
    for i in range(idx(t0), idx(t0 + length)):
        tt = i / SR - t0
        n = rnd.uniform(-1, 1)
        lp += 0.4 * (n - lp)
        metal = sum(math.sin(TAU * f * tt + p) for f, p in parts) / 6
        v = ((n - lp) * 0.8 + metal * 0.2) * math.exp(-tt / (length * 0.32)) * amp * min(1.0, tt / 0.002)
        add(i, v * rnd.uniform(0.9, 1.1), v * rnd.uniform(0.9, 1.1), send=0.2)


def snare(t0, amp):
    lp = 0.0
    for i in range(idx(t0), idx(t0 + 0.12)):
        tt = i / SR - t0
        n = rnd.uniform(-1, 1)
        lp += 0.3 * (n - lp)
        v = ((n - lp) * 0.8 + math.sin(TAU * 190 * tt) * 0.5) * math.exp(-tt / 0.045) * amp
        add(i, v, v, send=0.3)


for k in kicks:
    kick(k, 0.6 if k in (DROP, FINAL, DROP + 4 * BAR) else 0.5)
for t0, t1, name in bars:
    for b in (1, 3):
        clap(t0 + b * 0.5)
    for s in range(16):
        tt = t0 + s * 0.125
        if s % 2 == 1:
            hat(tt, 0.085 if s % 4 == 2 - 1 else 0.06, 0.05 if s % 4 == 3 else 0.028, 0.2 if s % 4 == 3 else -0.15)
        else:
            hat(tt, 0.03, 0.02, -0.1)
# geçişe trampet dolgusu (10,12-10,62), zil vuruşları
for k in range(8):
    snare(DROP + 4 * BAR - 0.5 + k * 0.0625, 0.05 + 0.1 * k / 7)
for t in (DROP, DROP + 4 * BAR, FINAL):
    crash(t, 0.17 if t != FINAL else 0.2, 1.8 if t != FINAL else 1.0)

# ---------- Giriş: yükselen gürültü ve ters zil, drop'ta alt düşüş
lp = bp = 0.0
for i in range(idx(0.6), idx(DROP)):
    t = i / SR
    u = (t - 0.6) / (DROP - 0.6)
    fc = 500 * (12 ** u)
    f = 2 * math.sin(math.pi * min(fc, SR / 6) / SR)
    hp = rnd.uniform(-1, 1) - lp - 0.6 * bp
    bp += f * hp
    lp += f * bp
    v = bp * 0.09 * u ** 2.2 * min(1.0, (DROP - t) * 200)
    add(i, v, v * 0.95, send=0.3)
lp = 0.0
for i in range(idx(DROP - 0.55), idx(DROP)):
    t = i / SR
    u = (t - (DROP - 0.55)) / 0.55
    n = rnd.uniform(-1, 1)
    lp += 0.35 * (n - lp)
    v = (n - lp) * 0.12 * u ** 3 * min(1.0, (DROP - t) * 300)
    add(i, v, v)
for i in range(idx(DROP), idx(DROP + 1.4)):
    tt = i / SR - DROP
    v = math.sin(TAU * (30 * tt + 25 * (1 - math.exp(-tt * 3)) / 3)) * math.exp(-tt * 2.2) * 0.35 * min(1.0, tt / 0.003)
    add(i, v)


# ---------- Yankı ve son
def reverb(x, shift):
    out = [0.0] * N
    for dly in [int(v * SR / 44100) + shift for v in (1557, 1617, 1491, 1422)]:
        buf = [0.0] * dly
        p = 0
        flt = 0.0
        for i in range(N):
            y = buf[p]
            flt = y * 0.75 + flt * 0.25
            buf[p] = x[i] + flt * 0.83
            p += 1
            if p == dly:
                p = 0
            out[i] += y * 0.25
    for dly in (int(225 * SR / 44100), int(556 * SR / 44100)):
        buf = [0.0] * dly
        p = 0
        for i in range(N):
            b = buf[p]
            y = -out[i] + b
            buf[p] = out[i] + b * 0.5
            p += 1
            if p == dly:
                p = 0
            out[i] = y
    return out


wl = reverb(RVL, 0)
wr = reverb(RVR, 19)
for i in range(N):
    t = i / SR
    g = min(1.0, t / 0.05) * (1 - smooth(DUR - 0.6, DUR, t))
    L[i] = math.tanh(1.1 * (L[i] + 0.5 * wl[i]) * g)
    R[i] = math.tanh(1.1 * (R[i] + 0.5 * wr[i]) * g)
peak = max(max(abs(v) for v in L), max(abs(v) for v in R)) or 1.0
k = 0.89 / peak
with wave.open(sys.argv[1] if len(sys.argv) > 1 else "muzik.wav", "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes(b"".join(struct.pack("<hh", int(L[i] * k * 32767), int(R[i] * k * 32767)) for i in range(N)))
print("müzik hazır, tepe", round(peak, 3))
