import random
random.seed(7)
def stars(n,w=190,h=150,c="#fff"):
    return "".join(f'<circle cx="{random.uniform(4,w-4):.1f}" cy="{random.uniform(4,h):.1f}" r="{random.choice([.6,.8,1,1.3]):.1f}" fill="{c}" opacity="{random.uniform(.5,1):.2f}"/>' for _ in range(n))
def title(t1,t2,color,font="Impact, 'Arial Black', sans-serif",size=25,y=238):
    s=f'<text x="95" y="{y}" text-anchor="middle" font-family="{font}" font-size="{size}" fill="{color}" letter-spacing="1.5" style="paint-order:stroke" stroke="rgba(0,0,0,.55)" stroke-width="3">{t1}</text>'
    if t2: s+=f'<text x="95" y="{y+26}" text-anchor="middle" font-family="{font}" font-size="{size}" fill="{color}" letter-spacing="1.5" style="paint-order:stroke" stroke="rgba(0,0,0,.55)" stroke-width="3">{t2}</text>'
    return s
sheen='<path d="M0 0 L120 0 L40 285 L0 285Z" fill="url(#sheen)"/>'
defs='''<defs><linearGradient id="sheen" x1="0" x2="1"><stop offset="0" stop-color="#fff" stop-opacity=".22"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></linearGradient>
<linearGradient id="dark" x1="0" y1="0" x2="0" y2="1"><stop offset=".55" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".7"/></linearGradient></defs>'''
def wrap(body): return f'<svg xmlns="http://www.w3.org/2000/svg" width="190" height="285" viewBox="0 0 190 285">{defs}{body}<rect width="190" height="285" fill="url(#dark)"/>{sheen}</svg>'

# 1 Gece Yarısı Treni
windows="".join(f'<rect x="{38+i*13}" y="163" width="8" height="6" rx="1" fill="#ffe08a"/>' for i in range(9))
train=f'''<linearGradient id="t1" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0b1445"/><stop offset=".55" stop-color="#3b1d8f"/><stop offset="1" stop-color="#c2410c"/></linearGradient>
<rect width="190" height="285" fill="url(#t1)"/>{stars(60)}
<circle cx="140" cy="52" r="40" fill="#fde68a" opacity=".18"/><circle cx="140" cy="52" r="26" fill="#fff7d6"/><circle cx="131" cy="45" r="5" fill="#f5e3a3"/><circle cx="148" cy="60" r="3.5" fill="#f5e3a3"/>
<path d="M0 150 L30 112 L58 140 L92 96 L128 138 L160 108 L190 142 L190 285 L0 285Z" fill="#1e1463"/>
<path d="M0 176 L40 148 L80 172 L120 144 L190 178 L190 285 L0 285Z" fill="#150c45"/>
<rect x="0" y="176" width="190" height="7" fill="#0a0626"/>
{"".join(f'<path d="M{8+i*32} 183 q16 -1 16 34 M{24+i*32} 183 q0 36 16 34" stroke="#0a0626" stroke-width="5" fill="none"/>' for i in range(6))}
<rect x="0" y="216" width="190" height="69" fill="#0a0626"/>
<rect x="30" y="158" width="128" height="18" rx="4" fill="#111a4a"/>{windows}
<path d="M158 158 h14 q10 2 12 18 h-26z" fill="#1b2a7a"/><rect x="166" y="162" width="8" height="6" rx="1" fill="#bfe3ff"/>
<path d="M184 170 L190 160 L190 180Z" fill="#fff3b0" opacity=".85"/>
<circle cx="170" cy="146" r="7" fill="#cbd5e1" opacity=".55"/><circle cx="160" cy="136" r="9" fill="#cbd5e1" opacity=".4"/><circle cx="146" cy="126" r="11" fill="#cbd5e1" opacity=".28"/>
{title("GECE YARISI","TRENİ","#fde68a",size=22,y=236)}'''

# 2 Sonsuz Yörünge
orbit=f'''<radialGradient id="o1" cx=".3" cy=".2" r="1"><stop offset="0" stop-color="#7c3aed"/><stop offset=".45" stop-color="#1e1b6b"/><stop offset="1" stop-color="#050414"/></radialGradient>
<radialGradient id="pl" cx=".35" cy=".3"><stop offset="0" stop-color="#fbcfe8"/><stop offset=".5" stop-color="#e879f9"/><stop offset="1" stop-color="#6d28d9"/></radialGradient>
<rect width="190" height="285" fill="url(#o1)"/>
<ellipse cx="150" cy="60" rx="70" ry="40" fill="#ec4899" opacity=".22"/><ellipse cx="40" cy="170" rx="70" ry="46" fill="#22d3ee" opacity=".16"/>{stars(80,h=220)}
<ellipse cx="95" cy="116" rx="82" ry="20" fill="none" stroke="#fde68a" stroke-width="5" opacity=".9" transform="rotate(-18 95 116)"/>
<circle cx="95" cy="112" r="48" fill="url(#pl)"/><path d="M52 100 q43 14 86 -6" stroke="#fff" stroke-opacity=".35" stroke-width="5" fill="none"/><path d="M50 122 q45 16 90 -4" stroke="#4c1d95" stroke-opacity=".5" stroke-width="6" fill="none"/>
<path d="M23 136 A82 20 -18 0 0 173 96" fill="none" stroke="#fde68a" stroke-width="5" transform="rotate(0)" opacity="0"/>
<path d="M26 60 l10 -22 l10 22 l-4 16 h-12z" fill="#e2e8f0"/><circle cx="36" cy="56" r="3.5" fill="#38bdf8"/><path d="M30 76 l6 22 l6 -22z" fill="#fb923c"/>
<circle cx="158" cy="186" r="9" fill="#93c5fd"/><circle cx="155" cy="183" r="3" fill="#e0f2fe"/>
{title("SONSUZ","YÖRÜNGE","#f5d0fe",size=24,y=236)}'''

# 3 Gölge Avcısı
bld="".join(f'<rect x="{x}" y="{y}" width="{w}" height="{285-y}" fill="{c}"/>' + "".join(f'<rect x="{x+3+j*6}" y="{y+6+k*9}" width="3" height="5" fill="{random.choice(["#f0abfc","#67e8f9","#fde68a","#1e1b4b","#1e1b4b"])}"/>' for j in range(int(w//6)) for k in range(int((200-y)//9))) for x,y,w,c in [(0,96,34,"#1a1040"),(36,70,30,"#231455"),(68,110,26,"#1a1040"),(96,58,36,"#2a1766"),(134,92,28,"#1f124d"),(164,74,26,"#231455")])
rain="".join(f'<line x1="{random.uniform(0,190):.0f}" y1="{random.uniform(0,230):.0f}" x2="{random.uniform(0,190):.0f}" y2="0" stroke="#a5f3fc" stroke-width=".6" opacity=".0"/>' for _ in range(0))
rain="".join(f'<line x1="{x}" y1="{y}" x2="{x-5}" y2="{y+16}" stroke="#a5f3fc" stroke-width=".8" opacity=".45"/>' for x,y in [(random.uniform(0,190),random.uniform(0,220)) for _ in range(46)])
shadow=f'''<linearGradient id="g1" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0f0a3c"/><stop offset=".6" stop-color="#be185d"/><stop offset="1" stop-color="#22d3ee"/></linearGradient>
<rect width="190" height="285" fill="url(#g1)"/>{bld}
<rect x="0" y="196" width="190" height="89" fill="#0a0620"/><ellipse cx="95" cy="200" rx="80" ry="8" fill="#f0abfc" opacity=".35"/>
<path d="M95 132 l-22 6 l44 0z M84 138 h22 v6 h-22z" fill="#05030f"/><circle cx="95" cy="150" r="8" fill="#05030f"/>
<path d="M78 160 q17 -8 34 0 l10 52 h-12 l-4 -30 l-4 46 h-8 l-3 -40 l-3 40 h-8 l-2 -46 l-6 30 h-10z" fill="#05030f"/>{rain}
{title("GÖLGE","AVCISI","#a5f3fc",size=24,y=240)}'''

# 4 Kızıl Sınır
red=f'''<linearGradient id="k1" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#7f1d1d"/><stop offset=".45" stop-color="#f97316"/><stop offset=".7" stop-color="#fbbf24"/><stop offset="1" stop-color="#7c2d12"/></linearGradient>
<radialGradient id="sun"><stop offset="0" stop-color="#fff7d6"/><stop offset=".6" stop-color="#fde047"/><stop offset="1" stop-color="#f97316"/></radialGradient>
<rect width="190" height="285" fill="url(#k1)"/><circle cx="95" cy="112" r="74" fill="#fde047" opacity=".25"/><circle cx="95" cy="112" r="50" fill="url(#sun)"/>
<path d="M30 60 q8 -6 16 0 q8 -6 16 0" stroke="#450a0a" stroke-width="2" fill="none"/><path d="M130 44 q6 -5 12 0 q6 -5 12 0" stroke="#450a0a" stroke-width="2" fill="none"/>
<path d="M0 150 L24 128 L40 140 L62 104 L84 150 L110 120 L140 150 L162 126 L190 150 L190 285 L0 285Z" fill="#9a3412"/>
<path d="M0 176 q50 -26 100 -6 q50 18 90 -10 L190 285 L0 285Z" fill="#7c2d12"/>
<path d="M0 204 q60 -22 120 -4 q40 12 70 0 L190 285 L0 285Z" fill="#431407"/>
<path d="M112 168 q8 -6 14 0 l8 2 l2 8 l-6 0 l0 14 h-4 v-12 h-10 v12 h-4 v-14 q-4 -4 0 -10z" fill="#1c0a02"/><circle cx="121" cy="158" r="4" fill="#1c0a02"/><path d="M114 154 h14 l-2 -4 h-10z" fill="#1c0a02"/>
<path d="M150 166 v20 M150 172 h8 v-8 M150 178 h-7 v-6" stroke="#1c0a02" stroke-width="3.5" fill="none" stroke-linecap="round"/>
{title("KIZIL","SINIR","#fff7d6",size=25,y=238)}'''

# 5 Eski Konak
konak=f'''<linearGradient id="e1" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#022c22"/><stop offset=".5" stop-color="#065f46"/><stop offset="1" stop-color="#1e1b4b"/></linearGradient>
<rect width="190" height="285" fill="url(#e1)"/>{stars(36,h=110,c="#d9f99d")}
<circle cx="138" cy="56" r="42" fill="#bef264" opacity=".2"/><circle cx="138" cy="56" r="27" fill="#ecfccb"/><circle cx="146" cy="50" r="24" fill="#065f46" opacity=".0"/>
<path d="M118 40 q6 -4 10 2 M150 66 q-6 4 -10 0" stroke="#a3e635" stroke-width="1.5" fill="none"/>
<path d="M0 196 q95 -44 190 0 L190 285 L0 285Z" fill="#03120e"/>
<path d="M52 176 v-52 l14 -16 l14 16 h24 l14 -22 l14 22 v52z" fill="#051a15"/><path d="M48 124 l18 -20 l18 20z M96 124 l22 -30 l22 30z" fill="#03120e"/><rect x="72" y="92" width="6" height="16" fill="#03120e"/>
{"".join(f'<rect x="{x}" y="{y}" width="8" height="11" fill="#fde047"/><rect x="{x+3.5}" y="{y}" width="1" height="11" fill="#051a15"/>' for x,y in [(60,134),(74,134),(60,154),(104,132),(120,132),(120,154)])}
<rect x="88" y="150" width="10" height="26" rx="5" fill="#0a2e25"/>
<path d="M22 196 v-44 M22 170 l-12 -14 M22 162 l12 -16 M22 180 l-10 -8 M168 190 v-50 M168 160 l12 -14 M168 152 l-12 -14 M168 172 l10 -8" stroke="#03120e" stroke-width="3" fill="none" stroke-linecap="round"/>
<ellipse cx="60" cy="204" rx="70" ry="9" fill="#d9f99d" opacity=".16"/><ellipse cx="140" cy="212" rx="70" ry="9" fill="#d9f99d" opacity=".12"/>
<path d="M30 70 q5 -5 10 0 q5 -5 10 0" stroke="#03120e" stroke-width="2" fill="none"/>
{title("ESKİ","KONAK","#ecfccb",font="Georgia, serif",size=27,y=238)}'''

# 6 Mavi Dalga
dalga=f'''<linearGradient id="d1" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fb923c"/><stop offset=".35" stop-color="#fde68a"/><stop offset=".6" stop-color="#22d3ee"/><stop offset="1" stop-color="#0c4a6e"/></linearGradient>
<linearGradient id="w1" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#67e8f9"/><stop offset="1" stop-color="#0369a1"/></linearGradient>
<rect width="190" height="285" fill="url(#d1)"/><circle cx="52" cy="62" r="42" fill="#fff" opacity=".25"/><circle cx="52" cy="62" r="26" fill="#fffbeb"/>
<path d="M190 70 C120 62 70 110 78 168 C84 204 122 214 150 198 C120 196 104 178 108 156 C112 132 140 122 168 134 C150 108 168 84 190 92Z" fill="url(#w1)"/>
<path d="M190 70 C150 66 120 84 106 110" stroke="#ecfeff" stroke-width="6" fill="none" stroke-linecap="round"/>
{"".join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="#ecfeff" opacity=".9"/>' for x,y,r in [(100,104,3),(92,118,2.2),(110,92,2.5),(122,82,2),(88,134,1.8),(132,76,2.6),(84,150,2)])}
<path d="M0 190 q30 -18 60 -6 q34 14 70 4 q30 -8 60 6 L190 285 L0 285Z" fill="#075985"/><path d="M0 214 q40 -14 80 -2 q50 14 110 -6 L190 285 L0 285Z" fill="#0c4a6e"/>
<path d="M112 176 q22 -8 44 4 l-44 4z" fill="#fef3c7"/><path d="M130 150 l6 14 l-4 12 h-4 l2 -12 l-6 -8z" fill="#082f49"/><circle cx="131" cy="145" r="4" fill="#082f49"/><path d="M128 158 l-12 6 M136 158 l12 -4" stroke="#082f49" stroke-width="3" stroke-linecap="round"/>
<path d="M20 110 q6 -5 12 0 q6 -5 12 0" stroke="#7c2d12" stroke-width="2" fill="none"/>
{title("MAVİ","DALGA","#ecfeff",size=26,y=240)}'''
for n,b in [("p1",train),("p2",orbit),("p3",shadow),("p4",red),("p5",konak),("p6",dalga)]:
    open(n+".svg","w").write(wrap(b))
