#!/usr/bin/env python3
"""Generate a single comprehensive architecture overview SVG in the architect house style (light theme)."""
import html

W, H = 1680, 1140
OUT = "/home/shane/Code/cim-portal/architecture/overview.svg"

# Architect light-theme palette (from diagram.html :root)
BG = "#FCFCF8"; DOT = "#EFEEE7"; ZONE = "#F6F5F0"
BORDER = "#E4E3DE"; STRONG = "#BFBFB7"
TP = "#121210"; TS = "#55554F"; TT = "#7E7E7C"
# kind -> (stroke, fill)
COLORS = {
    "sage":   ("#8AA870", "#E7EDE0"),   # person
    "indigo": ("#6B7FC9", "#E2E6F0"),   # software system
    "plum":   ("#9B6B9E", "#EBE2E8"),   # container
    "teal":   ("#4A9C95", "#DCEBE6"),   # component
    "slate":  ("#6B8294", "#E2E6E6"),   # database
    "ochre":  ("#C99A5B", "#F3EADC"),   # external
}

out = []

def esc(s): return html.escape(str(s))

def band(x, y, w, color, kind):
    bh = 22
    p = (f'M {x+12} {y} H {x+w-12} Q {x+w} {y} {x+w} {y+12} V {y+bh} '
         f'H {x} V {y+12} Q {x} {y} {x+12} {y} Z')
    out.append(f'<path d="{p}" fill="{color}" fill-opacity="0.16" />')
    out.append(f'<text x="{x+w/2}" y="{y+13}" text-anchor="middle" dominant-baseline="middle" '
               f'font-size="9.5" font-weight="700" letter-spacing="0.06em" fill="{TS}">{esc(kind.upper())}</text>')

def card(x, y, w, h, ckey, kind, title, sub="", title_size=14, sub_size=11):
    stroke, fill = COLORS[ckey]
    out.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="12" fill="{fill}" stroke="{stroke}" stroke-width="2" />')
    band(x, y, w, stroke, kind)
    cy = y + 22 + (h - 22) / 2
    if sub:
        out.append(f'<text x="{x+w/2}" y="{cy-7}" text-anchor="middle" dominant-baseline="middle" '
                   f'font-size="{title_size}" font-weight="600" fill="{TP}">{esc(title)}</text>')
        out.append(f'<text x="{x+w/2}" y="{cy+13}" text-anchor="middle" dominant-baseline="middle" '
                   f'font-size="{sub_size}" fill="{TS}">{esc(sub)}</text>')
    else:
        out.append(f'<text x="{x+w/2}" y="{cy}" text-anchor="middle" dominant-baseline="middle" '
                   f'font-size="{title_size}" font-weight="600" fill="{TP}">{esc(title)}</text>')

def zone(x, y, w, h, ckey, label):
    stroke, _ = COLORS[ckey]
    out.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="16" fill="#ffffff" '
               f'fill-opacity="0.45" stroke="{stroke}" stroke-width="1.5" stroke-dasharray="2 0" />')
    out.append(f'<rect x="{x}" y="{y}" width="{w}" height="30" rx="16" fill="{stroke}" fill-opacity="0.12" />')
    out.append(f'<rect x="{x}" y="{y+18}" width="{w}" height="12" fill="{stroke}" fill-opacity="0.12" />')
    out.append(f'<text x="{x+18}" y="{y+19}" dominant-baseline="middle" font-size="12.5" font-weight="700" '
               f'fill="{stroke}">{esc(label)}</text>')

def arrow(d, label=None, lx=None, ly=None):
    out.append(f'<path d="{d}" stroke="{STRONG}" stroke-width="1.6" fill="none" marker-end="url(#arrow)" />')
    if label:
        tw = len(label) * 6.0 + 14
        out.append(f'<rect x="{lx-tw/2}" y="{ly-10}" width="{tw}" height="19" rx="9" fill="{BG}" stroke="{BORDER}" stroke-width="1" />')
        out.append(f'<text x="{lx}" y="{ly}" text-anchor="middle" dominant-baseline="middle" font-size="10.5" fill="{TS}">{esc(label)}</text>')

# ---------- canvas ----------
out.append(f'<svg viewBox="0 0 {W} {H}" width="{W}" height="{H}" xmlns="http://www.w3.org/2000/svg">')
out.append('<defs>'
           '<marker id="arrow" markerWidth="11" markerHeight="11" refX="8" refY="5" orient="auto">'
           f'<path d="M0,0 L10,5 L0,10 z" fill="{STRONG}" /></marker>'
           '<pattern id="dots" width="22" height="22" patternUnits="userSpaceOnUse">'
           f'<circle cx="1.5" cy="1.5" r="1.5" fill="{DOT}" /></pattern>'
           '<style>@import url("https://fonts.googleapis.com/css2?family=Instrument+Sans:wght@400;500;600;700&amp;display=swap");'
           'text{font-family:"Instrument Sans",system-ui,-apple-system,"Segoe UI",sans-serif;}</style>'
           '</defs>')
out.append(f'<rect x="0" y="0" width="{W}" height="{H}" fill="{BG}" />')
out.append(f'<rect x="0" y="0" width="{W}" height="{H}" fill="url(#dots)" />')

# title
out.append(f'<text x="50" y="52" font-size="26" font-weight="700" fill="{TP}">AP1 IT CIM Portal — Architecture Overview</text>')
out.append(f'<text x="50" y="80" font-size="13.5" fill="{TS}">Vue 3 SPA · Spring Boot 3 stateless resource server (JDK 21) · Oracle (uat/prod) / MariaDB (dev) · OIDC SSO + internal-login</text>')

# ---------- people (left) ----------
card(50, 150, 210, 86, "sage", "Person", "Portal User", "Factory / MES staff")
card(50, 300, 210, 86, "sage", "Person", "Portal Administrator", "PORTAL_ADMIN role")

# ---------- system boundary ----------
BX, BY, BW, BH = 300, 110, 1000, 1000
out.append(f'<rect x="{BX}" y="{BY}" width="{BW}" height="{BH}" rx="18" fill="{ZONE}" fill-opacity="0.6" '
           f'stroke="{STRONG}" stroke-width="1.3" stroke-dasharray="6 5" />')
out.append(f'<text x="{BX+20}" y="{BY+24}" font-size="12.5" font-weight="700" letter-spacing="0.06em" fill="{TT}">SYSTEM BOUNDARY · AP1 IT CIM PORTAL</text>')

# SPA container zone
zone(330, 150, 940, 250, "plum", "Container · Web SPA  (cim-portal-client — Vue 3, Vite, Pinia, oidc-client-ts)")
spa_x = [350, 584, 818, 1052]
spa = [("Auth & SSO", "PKCE · auth/config stores"),
       ("API Client", "Bearer + 401 handling"),
       ("Dashboard UI", "SystemCard · launch · locked"),
       ("Admin UI", "links · enums · security")]
for (xx, (t, s)) in zip(spa_x, spa):
    card(xx, 285, 200, 96, "teal", "Component", t, s, title_size=13)

# API container zone
zone(330, 440, 940, 470, "plum", "Container · Portal API  (cim-portal-server — Spring Boot 3.3, stateless OAuth2 resource server)")
api_r1 = [("Auth & Security", "MultiIssuer JWT · keys"),
          ("Portal / Home", "accessible · locked cards"),
          ("Link & Grant", "SOR: Link, Grant"),
          ("Enum", "SOR: EnumValue")]
for (xx, (t, s)) in zip(spa_x, api_r1):
    card(xx, 560, 200, 96, "teal", "Component", t, s, title_size=13)
api_r2 = [("Security Settings", "SOR: SecuritySetting"),
          ("User Directory", "SOR: UserInfo · roles"),
          ("Persistence & Migr.", "JPA · Flyway V1–V6")]
for (xx, (t, s)) in zip(spa_x[:3], api_r2):
    card(xx, 700, 200, 96, "teal", "Component", t, s, title_size=13)

# Relational DB (inside boundary, bottom)
card(470, 960, 660, 94, "slate", "Database", "Relational Database", "Oracle (uat/prod)  /  MariaDB (dev)  ·  Flyway-migrated", title_size=15, sub_size=12)

# ---------- externals (right) ----------
card(1360, 200, 270, 120, "ochre", "External System", "Company SSO (OIDC IdP)", "Keycloak in dev/test", title_size=14)
out.append(f'<text x="1495" y="296" text-anchor="middle" font-size="10.5" fill="{TS}">Authorization Code + PKCE · JWKS</text>')
card(1360, 380, 270, 100, "ochre", "External System", "Local Desktop App", "via custom URL scheme", title_size=14)

# ---------- arrows ----------
arrow("M 260 188 L 295 188 Q 305 188 305 198 L 305 230 L 327 230", "uses", 318, 168)
arrow("M 260 338 L 290 338 Q 300 338 300 328 L 300 300 L 327 300", "admin", 316, 360)
# SPA -> SSO
arrow("M 1270 250 L 1320 250 Q 1330 250 1330 255 L 1357 255", "OIDC · PKCE", 1300, 232)
# SPA -> Local app
arrow("M 1270 360 L 1320 360 Q 1330 360 1330 380 L 1357 405", "launch / download", 1298, 342)
# SPA -> API (REST Bearer)
arrow("M 700 400 L 700 437", "REST · Bearer (HTTPS)", 700, 420)
# API -> SSO (JWKS)
arrow("M 1270 600 L 1310 600 Q 1320 600 1320 590 L 1320 322 L 1340 322", "verify JWT · JWKS", 1300, 600)
# API -> DB
arrow("M 805 904 L 805 956", "JPA / JDBC · Flyway", 805, 932)

# ---------- legend ----------
LX, LY = 1360, 540
out.append(f'<rect x="{LX}" y="{LY}" width="270" height="250" rx="12" fill="#ffffff" stroke="{BORDER}" stroke-width="1" />')
out.append(f'<text x="{LX+16}" y="{LY+24}" font-size="12" font-weight="700" letter-spacing="0.05em" fill="{TT}">LEGEND</text>')
legend = [("sage", "Person / actor"), ("plum", "Container (deployable unit)"),
          ("teal", "Component (internal module)"), ("slate", "Datastore"),
          ("ochre", "External system"), (None, "Dashed box = system boundary")]
ly = LY + 50
for ckey, lbl in legend:
    if ckey:
        st, fl = COLORS[ckey]
        out.append(f'<rect x="{LX+16}" y="{ly-11}" width="22" height="16" rx="4" fill="{fl}" stroke="{st}" stroke-width="2" />')
    else:
        out.append(f'<rect x="{LX+16}" y="{ly-11}" width="22" height="16" rx="4" fill="none" stroke="{STRONG}" stroke-width="1.3" stroke-dasharray="3 2" />')
    out.append(f'<text x="{LX+48}" y="{ly}" dominant-baseline="middle" font-size="11.5" fill="{TS}">{esc(lbl)}</text>')
    ly += 33

out.append('</svg>')

with open(OUT, "w") as f:
    f.write("\n".join(out))
print("wrote", OUT, "bytes:", sum(len(x) for x in out))
