#!/usr/bin/env python3
"""Yasal metinlerin web (HTML) sürümlerini üretir.

Kaynak: app/src/main/assets/legal/*.json (uygulama içinde gösterilen metinlerle aynı dosyalar).
Çıktı (depo kökü): gizlilik-politikasi.html, kullanim-kosullari.html (Türkçe),
privacy-policy.html, terms-of-service.html (İngilizce).
Kullanım: python3 tools/legal_html.py
"""
import html
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app", "src", "main", "assets", "legal")

STYLE = """<style>
  :root {
    --bg: #0D0A16;
    --bg-panel: #17122A;
    --bg-panel-alt: #1E1832;
    --border: #2E2547;
    --text: #EDE9F7;
    --text-dim: #A79FC4;
    --text-faint: #756D93;
    --accent: #E93DE0;
    --accent-soft: #C084FC;
    --accent-blue: #4CC9F0;
    --radius: 14px;
  }
  * { box-sizing: border-box; }
  body {
    margin: 0;
    background: radial-gradient(ellipse at top, #1A1330 0%, #0D0A16 55%);
    color: var(--text);
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    line-height: 1.65;
    -webkit-font-smoothing: antialiased;
  }
  .wrap { max-width: 760px; margin: 0 auto; padding: 56px 24px 96px; }
  header.top {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-bottom: 8px;
  }
  .logo-mark {
    width: 40px; height: 40px; border-radius: 10px;
    background: linear-gradient(135deg, var(--accent) 0%, #7C3AED 100%);
    display: flex; align-items: center; justify-content: center;
    font-weight: 800; font-size: 18px; color: #fff;
    flex-shrink: 0;
    box-shadow: 0 4px 18px rgba(233,61,224,0.35);
  }
  .brand { font-size: 19px; font-weight: 700; letter-spacing: -0.01em; }
  .brand span { color: var(--accent-soft); }
  h1 {
    font-size: 32px;
    font-weight: 800;
    letter-spacing: -0.02em;
    margin: 40px 0 6px;
    color: #fff;
  }
  .meta {
    color: var(--text-faint);
    font-size: 14px;
    margin-bottom: 40px;
    padding-bottom: 28px;
    border-bottom: 1px solid var(--border);
  }
  h2 {
    font-size: 19px;
    font-weight: 700;
    color: #fff;
    margin: 44px 0 14px;
    padding-top: 4px;
    display: flex;
    align-items: center;
    gap: 10px;
  }
  h2::before {
    content: "";
    width: 4px; height: 18px;
    background: linear-gradient(180deg, var(--accent), var(--accent-blue));
    border-radius: 2px;
    display: inline-block;
  }
  p { color: var(--text-dim); margin: 0 0 14px; font-size: 15.5px; }
  ul { margin: 0 0 16px; padding-left: 0; list-style: none; }
  li {
    color: var(--text-dim);
    font-size: 15.5px;
    padding: 10px 0 10px 26px;
    position: relative;
    border-bottom: 1px solid var(--border);
  }
  li:last-child { border-bottom: none; }
  li::before {
    content: "";
    position: absolute; left: 4px; top: 19px;
    width: 6px; height: 6px; border-radius: 50%;
    background: var(--accent-soft);
  }
  li strong { color: var(--text); }
  .callout {
    background: var(--bg-panel);
    border: 1px solid var(--border);
    border-left: 3px solid var(--accent-blue);
    border-radius: var(--radius);
    padding: 18px 20px;
    margin: 18px 0 24px;
    font-size: 14.5px;
    color: var(--text-dim);
  }
  .callout strong { color: var(--accent-blue); }
  .card {
    background: var(--bg-panel);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    padding: 22px 24px;
    margin: 16px 0;
  }
  .fill {
    background: var(--bg-panel-alt);
    border: 1px dashed var(--border);
    border-radius: 8px;
    padding: 2px 8px;
    color: var(--accent-soft);
    font-size: 0.95em;
  }
  footer {
    margin-top: 64px;
    padding-top: 24px;
    border-top: 1px solid var(--border);
    color: var(--text-faint);
    font-size: 13px;
    text-align: center;
  }
  a { color: var(--accent-blue); text-decoration: none; }
  a:hover { text-decoration: underline; }
  strong.hl { color: #fff; }
</style>"""

OUTPUTS = [
    ("privacy", "tr", "gizlilik-politikasi.html", "kullanim-kosullari.html", "privacy-policy.html"),
    ("terms", "tr", "kullanim-kosullari.html", "gizlilik-politikasi.html", "terms-of-service.html"),
    ("privacy", "en", "privacy-policy.html", "terms-of-service.html", "gizlilik-politikasi.html"),
    ("terms", "en", "terms-of-service.html", "privacy-policy.html", "kullanim-kosullari.html"),
]
OTHER_TITLE = {("privacy", "tr"): "Hizmet Şartları", ("terms", "tr"): "Gizlilik Politikası",
               ("privacy", "en"): "Terms of Service", ("terms", "en"): "Privacy Policy"}
LANG_SWITCH = {"tr": "English", "en": "Türkçe"}

URL = re.compile(r"(https?://[^\s)<]+)")


def fmt(text, tokens):
    for k, v in tokens.items():
        text = text.replace(k, v)
    text = html.escape(text, quote=False)
    text = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", text)
    text = URL.sub(r'<a href="\1" rel="noopener">\1</a>', text)
    email = tokens["{EMAIL}"]
    return text.replace(email, f'<a href="mailto:{email}">{email}</a>') if "href" not in text else text


def build(doc, lang, out, other, switch):
    info = json.load(open(os.path.join(SRC, "info.json"), encoding="utf-8"))
    data = json.load(open(os.path.join(SRC, f"{doc}_{lang}.json"), encoding="utf-8"))
    tokens = {"{DEVELOPER}": info["developer"], "{EMAIL}": info["email"], "{UPDATED}": info["updated_" + lang]}
    parts = [f"<!DOCTYPE html>\n<html lang=\"{lang}\">\n<head>\n<meta charset=\"UTF-8\">",
             "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">",
             f"<title>{html.escape(data['title'])} — CineStream</title>", STYLE, "</head>\n<body>\n<div class=\"wrap\">",
             "  <header class=\"top\">\n    <div class=\"logo-mark\">C</div>\n    <div class=\"brand\">Cine<span>Stream</span></div>\n  </header>",
             f"  <h1>{html.escape(data['title'])}</h1>",
             f"  <div class=\"meta\">{fmt(data['updated'], tokens)} · <a href=\"{other}\">{OTHER_TITLE[(doc, lang)]}</a> · <a href=\"{switch}\">{LANG_SWITCH[lang]}</a></div>",
             f"  <p>{fmt(data['intro'], tokens)}</p>",
             f"  <div class=\"callout\">{fmt(data['highlight'], tokens)}</div>"]
    for s in data["sections"]:
        parts.append(f"  <h2>{fmt(s['h'], tokens)}</h2>")
        parts += [f"  <p>{fmt(p, tokens)}</p>" for p in s.get("p", [])]
        if s.get("list"):
            parts.append("  <ul>\n" + "\n".join(f"    <li>{fmt(li, tokens)}</li>" for li in s["list"]) + "\n  </ul>")
        parts += [f"  <p>{fmt(p, tokens)}</p>" for p in s.get("after", [])]
    parts.append("  <footer>© 2026 CineStream</footer>\n</div>\n</body>\n</html>\n")
    with open(os.path.join(ROOT, out), "w", encoding="utf-8") as f:
        f.write("\n".join(parts))
    print("yazıldı:", out)


if __name__ == "__main__":
    for doc, lang, out, other, switch in OUTPUTS:
        build(doc, lang, out, other, switch)
