import os
import re
import json
import urllib.request
import difflib
from collections import OrderedDict
from html import unescape

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUTPUT_FILE = os.path.join(BASE_DIR, "agenda.json")

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
}

def fetch_url(url, timeout=15):
    try:
        req = urllib.request.Request(url, headers=HEADERS)
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read()
            charset = resp.headers.get_content_charset()
            if charset:
                try:
                    return raw.decode(charset, errors="replace")
                except Exception:
                    pass
            # Try utf-8 first
            try:
                return raw.decode("utf-8")
            except UnicodeDecodeError:
                return raw.decode("iso-8859-15", errors="replace")
    except Exception as e:
        print(f"Error fetching {url}: {e}")
        return ""

def canonical_channel_name(raw_name):
    # Strip community tags, resolutions, asterisks, arrows, hashes
    s = re.sub(r'\[.*?\]', '', raw_name)
    s = re.sub(r'\(.*?\)', '', s)
    s = re.sub(r'-->.*', '', s)
    s = re.sub(r'#.*', '', s)
    s = re.sub(r'https?://\S+', '', s)
    s = re.sub(r'(?i)\b(1080p?|720p?|4k|fhd|hd|hevc|multiaudio|spa|esp|audio|stream|spain)\b', '', s)
    s = re.sub(r'[*_~|]', '', s)
    s = re.sub(r'\s+', ' ', s).strip()
    if not s:
        return ""

    low = s.lower().replace(".", " ").replace("+", "plus").replace("-", " ")
    low = re.sub(r'\s+', ' ', low).strip()

    # M+ Liga de Campeones
    m_champ = re.search(r'(?:liga de campeones|l de campeones|champions)\s*(\d*)', low)
    if m_champ and ("m " in low or "mplus" in low or "movistar" in low or low.startswith("l ")):
        num = m_champ.group(1).strip()
        return f"M+ Liga de Campeones {num}".strip()

    # M+ LaLiga
    m_laliga = re.search(r'(?:movistar|mplus|m)\s+laliga\s*(\d*)', low)
    if m_laliga:
        num = m_laliga.group(1).strip()
        return f"M+ LaLiga {num}".strip()

    # DAZN LaLiga
    m_dazn_la = re.search(r'dazn\s+la\s*liga\s*(\d*)', low)
    if m_dazn_la:
        num = m_dazn_la.group(1).strip()
        return f"DAZN LaLiga {num}".strip()

    # DAZN numbered
    m_dazn = re.search(r'^dazn\s*([1-4])$', low)
    if m_dazn:
        return f"DAZN {m_dazn.group(1)}"

    # DAZN F1
    if "dazn" in low and ("f1" in low or "formula" in low):
        return "DAZN F1"

    # DAZN MotoGP
    if "dazn" in low and "moto" in low:
        return "DAZN MotoGP"

    # DAZN Baloncesto
    m_dazn_b = re.search(r'dazn\s+baloncesto\s*(\d*)', low)
    if m_dazn_b:
        num = m_dazn_b.group(1).strip()
        return f"DAZN Baloncesto {num}".strip()

    # LaLiga TV Hypermotion
    m_hyper = re.search(r'(?:laliga tv hypermotion|laliga hypermotion|hypermotion)\s*(\d*)', low)
    if m_hyper:
        num = m_hyper.group(1).strip()
        return f"LaLiga TV Hypermotion {num}".strip()

    # Primera Federación / 1ª RFEF
    if re.search(r'(?:primera\s+federaci[oó]n|1[ªa]?\s*rfef)', low):
        m_rfef = re.search(r'\b([1-9])\b', low)
        num_str = f" {m_rfef.group(1)}" if m_rfef else ""
        return f"Primera Federación{num_str}".strip()

    # Movistar Plus+
    if low in ("movistar", "movistar plus", "movistar plusplus", "movistar plus 1", "mplus", "m plus"):
        return "Movistar Plus+"
    if low in ("movistar plus 2", "movistar plusplus 2", "mplus 2"):
        return "Movistar Plus+ 2"

    # M+ Deportes
    m_dep = re.search(r'(?:m\s+deportes|mplus\s+deportes|movistar\s+deportes)\s*(\d*)', low)
    if m_dep:
        num = m_dep.group(1).strip()
        return f"M+ Deportes {num}".strip()

    # M+ Vamos
    m_vam = re.search(r'(?:m\s+vamos|mplus\s+vamos|vamos)\s*(\d*)', low)
    if m_vam:
        num = m_vam.group(1).strip()
        return f"M+ Vamos {num}".strip()

    # M+ Baloncesto
    m_bal = re.search(r'(?:m\s+baloncesto|mplus\s+baloncesto|movistar\s+baloncesto)\s*(\d*)', low)
    if m_bal:
        num = m_bal.group(1).strip()
        return f"M+ Baloncesto {num}".strip()

    # M+ Golf
    m_golf = re.search(r'(?:m\s+golf|mplus\s+golf|movistar\s+golf)\s*(\d*)', low)
    if m_golf:
        num = m_golf.group(1).strip()
        return f"M+ Golf {num}".strip()

    # Eurosport
    m_euro = re.search(r'eurosport\s*(\d*)', low)
    if m_euro:
        num = m_euro.group(1).strip()
        return f"Eurosport {num if num else '1'}".strip()

    # Teledeporte
    if "teledeporte" in low or low == "tdp":
        return "Teledeporte"

    # Gol Play
    if low in ("gol", "gol play", "gol television"):
        return "Gol Play"

    # La 1 / La 2
    if low in ("la 1", "tve 1", "tve1"):
        return "La 1"
    if low in ("la 2", "tve 2", "tve2"):
        return "La 2"

    # Esport 3
    if low in ("esport 3", "esport3", "esports 3", "esports3"):
        return "Esport3"

    return s

def clean_base_name(name):
    return canonical_channel_name(name)

def extract_hash(url_or_hash):
    u = url_or_hash.strip()
    m = re.search(r'([a-fA-F0-9]{40})', u)
    if m:
        return m.group(1).lower()
    return ""

def load_markel_channels():
    # Canales de Markel desactivados por peticion: la mayoria de enlaces se encuentran caidos/offline
    return OrderedDict()

def load_peticiones_channels():
    sources = [
        ("https://raw.githubusercontent.com/Icastresana/lista1/main/peticiones", "Comunidad"),
        ("https://raw.githubusercontent.com/Icastresana/lista1/main/Probando", "Comunidad"),
        ("https://raw.githubusercontent.com/GitCorion/Elcano/main/base.txt", "Elcano"),
        ("https://raw.githubusercontent.com/rciptv2019/RCacediariosRC/main/RC%20ACESTREAMS%20DIARIOS", "RCP"),
        ("https://raw.githubusercontent.com/rciptv2019/RCacediariosRC/main/RC%20ACESTREAM%20DIARIOS%202", "RCP")
    ]
    channels_by_base = OrderedDict()

    for url, default_source in sources:
        raw = fetch_url(url)
        if not raw:
            continue
        current_title = ""
        for line in raw.splitlines():
            line = line.strip()
            if not line:
                continue
            if line.startswith("#EXTINF:"):
                parts = line.split(",")
                current_title = parts[-1].strip() if len(parts) > 1 else ""
            elif not line.startswith("#"):
                h = extract_hash(line)
                if h and current_title:
                    community = default_source
                    cu = current_title.upper()
                    if "ELCANO" in cu:
                        community = "Elcano"
                    elif "NEW LOOP" in cu:
                        community = "New Loop"
                    elif "NEW ERA" in cu:
                        community = "New Era"
                    elif "DIRECTOS" in cu:
                        community = "Directos"
                    elif "RC " in cu or "RCP" in cu:
                        community = "RCP"

                    base = canonical_channel_name(current_title)
                    if not base:
                        base = current_title
                    if base not in channels_by_base:
                        channels_by_base[base] = []
                    if not any(x["streamId"] == h for x in channels_by_base[base]):
                        channels_by_base[base].append({
                            "name": f"{base} - Opción {len(channels_by_base[base]) + 1} [{community}]",
                            "streamId": h,
                            "type": "ACESTREAM",
                            "source": community
                        })
                current_title = ""
    return channels_by_base


def is_valid_direct_stream_url(url):
    """Valida que una URL de stream directo no contenga placeholders SSAI/macros sin resolver."""
    if not url or not (url.startswith("http://") or url.startswith("https://")):
        return False
    # Filtra plantillas publicitarias como [LMT], [DEVICE_ID], [IP], [UA], [IFA_TYPE], [APP_BUNDLE]
    if re.search(r'\[[A-Z0-9_]{2,}\]', url) or any(ch.isspace() for ch in url):
        return False
    return True


def load_tdt_sports_channels():
    """
    Carga canales deportivos en abierto de TDTChannels (Teledeporte, Gol Play, Esport3, Real Madrid TV).
    """
    url = "https://www.tdtchannels.com/lists/tv.m3u"
    raw = fetch_url(url, timeout=8)
    if not raw:
        return OrderedDict()
    return parse_tdt_sports_m3u(raw)


def parse_tdt_sports_m3u(raw):
    """Extrae canales deportivos válidos de un listado M3U de TDTChannels descartando URLs con macros."""
    channels_by_base = OrderedDict()
    current_name = ""
    for line in raw.splitlines():
        line = line.strip()
        if not line:
            continue
        if line.startswith("#EXTINF:"):
            m = re.search(r',([^,]+)$', line)
            current_name = m.group(1).strip() if m else ""
        elif not line.startswith("#") and (line.startswith("http://") or line.startswith("https://")):
            if not is_valid_direct_stream_url(line):
                current_name = ""
                continue
            low = current_name.lower()
            if any(k in low for k in ['teledeporte', 'gol play', 'esport3', 'real madrid tv']):
                base = canonical_channel_name(current_name)
                if not base:
                    base = current_name
                if base not in channels_by_base:
                    channels_by_base[base] = []
                if not any(x["streamId"] == line for x in channels_by_base[base]):
                    channels_by_base[base].append({
                        "name": f"{base} [TDT Oficial]",
                        "streamId": line,
                        "type": "DIRECT",
                        "source": "TDTChannels"
                    })
            current_name = ""
    return channels_by_base

def load_direct_eventos():
    urls = [
        "https://raw.githubusercontent.com/Icastresana/lista1/main/eventos.m3u",
        "https://raw.githubusercontent.com/rciptv2019/RCacediariosRC/main/RC%20EVENTOS%20DIARIOS"
    ]
    eventos = []
    seen_hashes = set()
    for url in urls:
        raw = fetch_url(url)
        if not raw:
            continue
        current_title = ""
        for line in raw.splitlines():
            line = line.strip()
            if not line:
                continue
            if line.startswith("#EXTINF:"):
                parts = line.split(",")
                current_title = parts[-1].strip() if len(parts) > 1 else ""
            elif not line.startswith("#"):
                h = extract_hash(line)
                if h and current_title and h not in seen_hashes:
                    seen_hashes.add(h)
                    eventos.append({
                        "raw_title": current_title,
                        "streamId": h
                    })
                current_title = ""
    return eventos

def load_arenavision():
    import urllib.parse
    mirrors = [
        "http://www.arena4viewer.in/misguia2.php",
        "https://www.arena4viewer.pl/misguia2.php",
        "https://www.arena4viewer.cool/misguia2.php",
        "https://www.arena4viewer.top/misguia2.php"
    ]
    data = urllib.parse.urlencode({'key': 'fc8c75bd41f06b0fa1d32c8b0b76493d', 'expire': '20250000'}).encode('utf-8')
    html = ""
    for u in mirrors:
        try:
            req = urllib.request.Request(u, data=data, headers={'User-Agent': 'Apache-HttpClient/UNAVAILABLE (java 1.4)'})
            with urllib.request.urlopen(req, timeout=6) as r:
                html = r.read().decode('utf-8', errors='ignore')
                if "streams" in html:
                    break
        except Exception:
            continue

    if not html or "streams" not in html:
        print("Aviso: No se pudo conectar a los mirrors de ArenaVision.")
        return {}, []

    streams_map = {}
    s_match = re.search(r'streams[^>]*>(.*?)</div>', html, re.DOTALL)
    if s_match:
        for part in s_match.group(1).split(","):
            sub = part.split("#")
            if len(sub) >= 2:
                ch_key = sub[0].strip().lower().replace("av", "")
                h = extract_hash(sub[1])
                if ch_key and h:
                    streams_map[ch_key] = h

    arena_events = []
    rows = re.findall(r'<tr[^>]*>(.*?)</tr>', html, re.DOTALL)
    for r in rows:
        cols = [re.sub(r'<[^>]+>', '', c).replace('&nbsp;', ' ').strip() for c in re.findall(r'<td[^>]*>(.*?)</td>', r, re.DOTALL)]
        if len(cols) >= 6:
            raw_channels = cols[5]
            ch_nums = re.findall(r'\b(\d+)\b', raw_channels)
            channels = []
            for num in ch_nums:
                if num in streams_map:
                    channels.append({
                        "name": f"ArenaVision {num} (AV{num})",
                        "streamId": streams_map[num],
                        "type": "ACESTREAM",
                        "source": "ArenaVision"
                    })
            if cols[4] and channels:
                sport_prefix = cols[2].strip()
                comp_text = cols[3].strip()
                if sport_prefix.upper() in ["F1", "MOTOGP"] and sport_prefix.upper() not in comp_text.upper():
                    comp_text = f"{sport_prefix.upper()} - {comp_text}"
                norm_sp = normalize_sport(cols[2], cols[3])
                title_val = cols[4].strip()
                if norm_sp not in ("MOTOR", "CICLISMO"):
                    if ' - ' in title_val:
                        title_val = title_val.replace(' - ', ' vs ')
                    elif '-' in title_val and len(title_val.split('-')) == 2:
                        p1, p2 = title_val.split('-', 1)
                        title_val = f"{p1.strip()} vs {p2.strip()}"
                arena_events.append({
                    "title": title_val,
                    "sport": norm_sp,
                    "competition": comp_text if comp_text else cols[3],
                    "time": cols[1].replace(" CET", "").strip(),
                    "date": cols[0],
                    "channels": channels
                })

    print(f"ArenaVision cargado: {len(streams_map)} streams, {len(arena_events)} eventos de agenda.")
    return streams_map, arena_events

def load_livetv_events(today):
    """
    Scraper para LiveTV (livetv.sx y sus mirrors).
    Extrae la lista de eventos del día y busca en paralelo los enlaces AceStream activos
    (acestream://...) en sus fichas técnicas, categorizando idioma y bitrate.
    """
    from concurrent.futures import ThreadPoolExecutor, as_completed
    base_domains = ['livetv.sx', 'liveru.sx', 'livetv842.me']
    html = ""
    used_domain = ""
    for domain in base_domains:
        url = f"https://{domain}/enx/allupcoming/"
        try:
            html = fetch_url(url, timeout=6)
            if html and "/enx/eventinfo/" in html:
                used_domain = domain
                break
        except Exception:
            continue

    if not html:
        print("Aviso: No se pudo conectar a los mirrors de LiveTV.")
        return []

    blocks = re.findall(r'<table[^>]*>.*?/enx/eventinfo/\d+[^"]*.*?</table>', html, re.DOTALL | re.IGNORECASE)
    if not blocks:
        return []

    today_day = str(today.day)
    today_month_name = today.strftime("%B")  # e.g. November

    candidates = []
    seen_links = set()
    for b in blocks:
        link_m = re.search(r'href="(/enx/eventinfo/\d+[^"]*)"[^>]*>(.*?)</a>', b, re.DOTALL | re.IGNORECASE)
        if not link_m:
            continue
        link = link_m.group(1)
        if link in seen_links:
            continue
        seen_links.add(link)

        title_raw = link_m.group(2)
        title = re.sub(r'&ndash;', 'vs', title_raw).strip()
        title = re.sub(r'<[^>]+>', '', title).strip()
        title = re.sub(r'\s+', ' ', title)

        icon_m = re.search(r'<img[^>]*src="[^"]*/img/icons/[^"]*"[^>]*alt="([^"]*)"', b, re.IGNORECASE)
        if not icon_m:
            icon_m = re.search(r'<img[^>]*alt="([^"]*)"[^>]*src="[^"]*/img/icons/[^"]*"', b, re.IGNORECASE)
        sport_alt = icon_m.group(1).strip() if icon_m else ""

        desc_m = re.search(r'<span class="evdesc">(.*?)</span>', b, re.DOTALL | re.IGNORECASE)
        desc = re.sub(r'<[^>]+>', ' ', desc_m.group(1)).strip() if desc_m else ""
        desc = re.sub(r'\s+', ' ', desc)

        # Date filter: check if event is for today
        is_today = False
        if today_month_name.lower() in desc.lower() and today_day in desc:
            is_today = True
        elif 'today' in desc.lower() or 'at ' in desc.lower():
            is_today = True
        elif not any(m in desc.lower() for m in ['january', 'february', 'march', 'april', 'may', 'june', 'july', 'august', 'september', 'october', 'november', 'december']):
            is_today = True

        if not is_today:
            continue

        time_m = re.search(r'(\d{1,2}:\d{2})', desc)
        time_str = time_m.group(1) if time_m else ""
        if len(time_str) == 4 and time_str[1] == ':':
            time_str = '0' + time_str

        # Extract competition text if available in parentheses
        comp_m = re.search(r'\(([^)]+)\)', desc)
        comp_text = comp_m.group(1).strip() if comp_m else sport_alt

        candidates.append({
            'link': f"https://{used_domain}{link}",
            'title': title,
            'sport_alt': sport_alt,
            'comp_text': comp_text,
            'time': time_str,
        })

    def check_event(cand):
        try:
            ev_html = fetch_url(cand['link'], timeout=6)
            if not ev_html:
                return None
            rows = re.findall(r'<tr[^>]*>.*?</tr>', ev_html, re.DOTALL)
            channels = []
            seen_hashes = set()
            for row in rows:
                if 'acestream://' in row or "show_webplayer('acestream'" in row:
                    hash_m = re.search(r'acestream://([a-fA-F0-9]{40})', row)
                    if not hash_m:
                        hash_m = re.search(r"show_webplayer\('acestream',\s*'([a-fA-F0-9]{40})'", row)
                    if not hash_m:
                        continue
                    h = hash_m.group(1).lower()
                    if h in seen_hashes:
                        continue
                    seen_hashes.add(h)

                    lang_m = re.search(r'title="([^"]+)"\s+width=16\s+src="[^"]*/linkflag/', row)
                    lang = lang_m.group(1) if lang_m else ""

                    bitrate_m = re.search(r'class="bitrate"[^>]*>([^<]+)</td>', row)
                    bitrate = bitrate_m.group(1).strip() if bitrate_m else ""

                    name_parts = ["[LiveTV]"]
                    if lang:
                        name_parts.append(lang)
                    if bitrate:
                        name_parts.append(bitrate)
                    else:
                        name_parts.append(f"Opción {len(channels) + 1}")

                    channels.append({
                        "name": " ".join(name_parts),
                        "streamId": h,
                        "type": "ACESTREAM",
                        "source": "LiveTV"
                    })

            if channels:
                norm_sport = normalize_sport(cand['sport_alt'], cand['comp_text'])
                return {
                    "title": cand['title'],
                    "sport": norm_sport,
                    "competition": cand['comp_text'] if cand['comp_text'] else cand['sport_alt'],
                    "time": cand['time'],
                    "date": "Hoy",
                    "channels": channels
                }
        except Exception:
            return None
        return None

    livetv_events = []
    with ThreadPoolExecutor(max_workers=8) as executor:
        futures = [executor.submit(check_event, c) for c in candidates]
        for f in as_completed(futures):
            res = f.result()
            if res:
                livetv_events.append(res)

    print(f"LiveTV cargado: {len(livetv_events)} eventos con enlaces AceStream.")
    return livetv_events


def clean_channel_name(s):
    import unicodedata
    s = unicodedata.normalize('NFKD', s).encode('ASCII', 'ignore').decode('ASCII').lower()
    s = re.sub(r'[^a-z0-9]', ' ', s)
    return re.sub(r'\s+', ' ', s).strip()

# Dictionaries for sports matching and team disambiguation
GENERIC_WORDS = {
    'fc', 'cf', 'cd', 'ud', 'sd', 'rcd', 'sad', 'club', 'balompie', 'deportivo',
    'sporting', 'racing', 'union', 'real', 'team', 'de', 'del', 'la', 'las', 'el',
    'los', 'san', 'santa', 'st', 'afc', 'sc', 'ac'
}

MODIFIER_WORDS = {
    'castilla', 'filial', 'juvenil', 'femenino', 'fem', 'women', 'basket', 'baloncesto', 'futsal', 'academy'
}

NAME_TRANSLATIONS = {
    "turkey": "turquia", "asutralia": "australia", "belgium": "belgica",
    "czech republic": "republica checa", "czech": "checa", "spain": "espana",
    "germany": "alemania", "france": "francia", "italy": "italia",
    "united states": "usa", "south korea": "corea", "stage": "etapa",
    "netherlands": "holanda", "portugal": "portugal", "england": "inglaterra",
    "switzerland": "suiza", "sweden": "suecia", "norway": "noruega",
    "denmark": "dinamarca", "austria": "austria", "croatia": "croacia",
    "poland": "polonia", "scotland": "escocia", "ireland": "irlanda",
    "greece": "grecia", "japan": "japon", "brazil": "brasil",
    "vienna": "viena", "tokyo": "tokio", "rome": "roma", "munich": "munich",
    "london": "londres",
    # Team variations and aliases
    "feyenord": "feyenoord",
    "napoli": "napoles",
    "barca": "barcelona",
    "bayern munchen": "bayern", "bayern munich": "bayern",
    "inter milan": "inter", "internazionale": "inter",
    "seahawks": "seahawks", "patriots": "patriots",
    "racing santander": "racing santander",
    "sporting gijon": "sporting gijon",
    "real sporting": "sporting gijon",
    "deportivo la coruna": "deportivo la coruna",
    "rc deportivo": "deportivo la coruna",
    "morabanc andorra": "andorra",
    "valencia basket": "valencia",
    "athletic club": "athletic",
    "atletico madrid": "atletico madrid"
}

TENNIS_STOPWORDS = {
    'atp', 'wta', 'itf', 'tournament', 'torneo', 'open', 'abierto',
    'masters', '1000', '500', '250', 'grand', 'slam', 'cup', 'copa',
    'de', 'del', 'la', 'el', 'tennis', 'tenis', 'championship',
    'championships', 'men', 'women', 'singles', 'doubles', 'final',
    'finals', 'semifinal', 'semifinales', 'cuartos', 'octavos', 'ronda',
    'round', 'vs', 'v'
}

TENNIS_STAGE_KEYWORDS = {
    'final', 'finals', 'semifinal', 'semifinales', 'semis', 'cuartos',
    'cuartos de final', '1/4', '1/2', 'octavos', 'octavos de final',
    'ronda', 'round', 'primera ronda', 'segunda ronda', 'tercera ronda',
    'previas', 'qualy', 'clasificacion', 'pista central', 'centre court',
    'court 1', 'pista 1', 'jornada', 'directo', 'tournament'
}

def split_sides(title):
    t = title.strip()
    parts = re.split(r'\s+(?:vs\.?|v\.?|[-–—])\s+', t, flags=re.IGNORECASE)
    if len(parts) >= 2:
        return [parts[0].strip(), ' - '.join(parts[1:]).strip()]
    parts_vs = re.split(r'\s+(?:vs\.?|v\.?)\s+', t, flags=re.IGNORECASE)
    if len(parts_vs) >= 2:
        return [parts_vs[0].strip(), ' - '.join(parts_vs[1:]).strip()]
    if '-' in t:
        sub = t.split('-')
        if len(sub) == 2:
            return [sub[0].strip(), sub[1].strip()]
    return [t]

def is_generic_stage_title(title, comp=""):
    c = clean_str(title)
    if not c or c in ('00', ''):
        return True
    words = c.split()
    if any(k in words for k in ['final', 'finals', 'semifinal', 'semifinales', 'semis', 'cuartos', 'octavos', 'carrera', 'race', 'ronda', 'round']):
        return True
    if all(w in TENNIS_STOPWORDS or w in TENNIS_STAGE_KEYWORDS or w in GENERIC_WORDS for w in words):
        return True
    title_tokens = {w for w in words if w not in TENNIS_STOPWORDS and len(w) > 2}
    if not title_tokens:
        return True
    if comp:
        comp_tokens = {w for w in clean_str(comp).split() if w not in TENNIS_STOPWORDS and len(w) > 2}
        if title_tokens.issubset(comp_tokens):
            return True
    return False

def tennis_tournament_tokens(comp_str, title_str=""):
    comp_clean = clean_str(comp_str)
    tokens = {w for w in comp_clean.split() if w not in TENNIS_STOPWORDS and len(w) > 2}
    if not tokens and title_str and len(split_sides(title_str)) == 1:
        title_clean = clean_str(title_str)
        tokens = {w for w in title_clean.split() if w not in TENNIS_STOPWORDS and w not in TENNIS_STAGE_KEYWORDS and len(w) > 2}
    return tokens

def normalize_competition(comp_str, sport=""):
    c = clean_str(comp_str)
    if not c:
        return ""
    if any(k in c for k in ["spanish league 2", "laliga hypermotion", "segunda division", "segunda", "laliga 2"]):
        return "laliga hypermotion"
    if any(k in c for k in ["spanish league", "laliga ea sports", "laliga", "primera division", "la liga"]) and sport != "BALONCESTO":
        return "laliga"
    if any(k in c for k in ["premier league", "english premier league", "epl"]):
        return "premier league"
    if any(k in c for k in ["champions league", "uefa champions league"]):
        return "champions league"
    if any(k in c for k in ["europa league", "uefa europa league"]):
        return "europa league"
    if any(k in c for k in ["conference league"]):
        return "conference league"
    if any(k in c for k in ["copa del rey", "spanish cup"]):
        return "copa del rey"
    if any(k in c for k in ["liga endesa", "acb", "spanish league"]) and sport == "BALONCESTO":
        return "acb"
    if any(k in c for k in ["euroliga", "euroleague"]):
        return "euroliga"
    if any(k in c for k in ["eurocup"]):
        return "eurocup"
    if any(k in c for k in ["nba"]):
        return "nba"
    if any(k in c for k in ["mexico gp", "g p mexico", "gp mexico", "gran premio de mexico"]):
        return "mexico gp"
    return c

COMP_WORDS = [
    r'champions\s*(?:-\s*fase liga)?',
    r'youth league\s*(?:-\s*fase liga)?',
    r'europa league\s*(?:-\s*fase liga)?',
    r'conference league\s*(?:-\s*fase liga)?',
    r'laliga\s*(?:hypermotion)?',
    r'primera federacion',
    r'segunda federacion',
    r'copa del rey',
    r'eurocup',
    r'euroliga',
    r'wta\s*(?:torneo\s+de\s+[a-z\s]+)?',
    r'atp\s*(?:torneo\s+de\s+[a-z\s]+|masters\s+[a-z\s]+)?',
    r'masters\s+[a-z\s]+',
    r'us open',
    r'australian open',
    r'roland garros',
    r'wimbledon',
    r'international championship',
    r'dp world tour championship',
    r'f1\b|formula 1\b',
    r'motogp\b'
]

STAGE_PREFIXES = r'^(?:fase liga|fase de grupos|liga regular|jornada \d+|\d+[\xaa\xba]?\s*ronda|1/4 de final|1/8 de final|1/2 de final|semifinales?|cuartos|octavos|primera ronda|segunda ronda|dia \d+)\s*[-–—:]*\s*'

MESES = {
    'enero': 1, 'febrero': 2, 'marzo': 3, 'abril': 4, 'mayo': 5, 'junio': 6,
    'julio': 7, 'agosto': 8, 'septiembre': 9, 'octubre': 10, 'noviembre': 11, 'diciembre': 12
}

def _contains_keyword(text, keywords):
    """Comprueba coincidencias de palabras/frases completas para evitar falsos positivos por subcadena."""
    return any(re.search(r'(?<!\w)' + re.escape(kw) + r'(?!\w)', text) for kw in keywords)


def normalize_sport(sport_raw, comp_raw=""):
    combined = f"{sport_raw} {comp_raw}".strip()
    s = clean_channel_name(combined)
    if _contains_keyword(s, ['triatlon', 'triathlon', 'world championship series', 'ironman', 'duatlon']):
        return 'TRIATLON'
    if _contains_keyword(s, ['nfl', 'american football', 'futbol americano', 'super bowl', 'patriots', 'seahawks', 'chiefs', '49ers']):
        return 'FUTBOL AMERICANO'
    if _contains_keyword(s, ['f1', 'formula 1', 'formula', 'moto', 'motor', 'superbike', 'motogp', 'moto2', 'moto3', 'nascar', 'indycar', 'rally', 'dakar']):
        return 'MOTOR'
    if _contains_keyword(s, ['baloncesto', 'basket', 'basketball', 'nba', 'fiba', 'acb', 'euroliga', 'eurocup']):
        return 'BALONCESTO'
    if _contains_keyword(s, ['tenis', 'tennis', 'us open', 'wta', 'atp', 'grand slam', 'roland garros', 'wimbledon', 'australian open', 'masters 1000']):
        return 'TENIS'
    if _contains_keyword(s, ['ciclismo', 'cycling', 'vuelta', 'giro', 'tour de francia', 'tour de france']):
        return 'CICLISMO'
    if _contains_keyword(s, ['padel', 'paddle']):
        return 'PADEL'
    if _contains_keyword(s, ['boxeo', 'boxing', 'box', 'mma', 'ufc', 'lucha']):
        return 'BOXEO'
    if _contains_keyword(s, ['rugby']):
        return 'RUGBY'
    if _contains_keyword(s, ['balonmano', 'handball']):
        return 'BALONMANO'
    if _contains_keyword(s, ['snooker', 'billar', 'pool', 'abierto de inglaterra', 'english open', 'uk championship', 'international championship']):
        return 'SNOOKER'
    if _contains_keyword(s, ['golf', 'pga', 'liv golf', 'ryder cup', 'masters golf', 'dp world']):
        return 'GOLF'
    if _contains_keyword(s, ['beisbol', 'baseball', 'mlb']):
        return 'BEISBOL'
    if _contains_keyword(s, ['hockey', 'nhl']):
        return 'HOCKEY'
    if _contains_keyword(s, [
        'futbol', 'soccer', 'football', 'futsal', 'f sala', 'champions', 'uefa', 'laliga', 'europa league',
        'conference', 'premier league', 'bundesliga', 'serie a', 'copa del rey', 'youth league', 'conmebol', 'libertadores',
        'sudamericana', 'rayo', 'cadiz', 'vallecano', 'barca', 'barcelona', 'sevilla', 'betis', 'valencia',
        'athletic', 'celta', 'osasuna', 'espanyol', 'getafe', 'mallorca', 'alaves', 'villarreal', 'girona', 'leganes',
        'valladolid', 'milan', 'juventus', 'arsenal', 'chelsea', 'liverpool', 'manchester', 'bayern', 'dortmund', 'psg',
        'porto', 'benfica', 'sporting', 'boca', 'river', 'palmeiras', 'flamengo'
    ]):
        return 'FUTBOL'
    
    clean_fallback = sport_raw.strip().upper()
    if len(clean_fallback.split()) > 3 or len(clean_fallback) > 20:
        return 'OTROS'
    return clean_fallback if clean_fallback else 'OTROS'

def clean_str(s):
    import unicodedata
    s = unicodedata.normalize('NFKD', s).encode('ASCII', 'ignore').decode('ASCII').lower()
    for k, v in NAME_TRANSLATIONS.items():
        s = re.sub(r'\b' + re.escape(k) + r'\b', v, s)
    s = re.sub(r'[^a-z0-9]', ' ', s)
    return re.sub(r'\s+', ' ', s).strip()

def team_tokens(team_str, sport=""):
    c = clean_str(team_str)
    c = re.sub(r'\bat\b|\batl\b', 'atletico', c)
    c = re.sub(r'\br\b', 'real', c)
    c = re.sub(r'\bman\b', 'manchester', c)
    words = c.split()
    tokens = set(words)
    distinctive = {w for w in words if w not in GENERIC_WORDS and len(w) > 2}
    if not distinctive:
        distinctive = {w for w in words if len(w) > 2}
    modifiers = {w for w in words if w in MODIFIER_WORDS}
    if sport == "BALONCESTO":
        modifiers.discard("basket")
        modifiers.discard("baloncesto")
    return tokens, distinctive, modifiers

def is_same_team(t1, t2, sport=""):
    c1 = clean_str(t1)
    c2 = clean_str(t2)
    if not c1 or not c2:
        return False
    if c1 == c2:
        return True

    # Racing Santander short name alias check
    if ('racing' in c1.split() and 'santander' in c2.split()) or ('racing' in c2.split() and 'santander' in c1.split()):
        return True
    # Sporting Gijon short name alias check
    if ('sporting' in c1.split() and 'gijon' in c2.split()) or ('sporting' in c2.split() and 'gijon' in c1.split()):
        return True

    # High fuzzy similarity check
    if len(c1) >= 4 and len(c2) >= 4:
        if difflib.SequenceMatcher(None, c1, c2).ratio() >= 0.82:
            return True

    tok1, dist1, mod1 = team_tokens(t1, sport)
    tok2, dist2, mod2 = team_tokens(t2, sport)
    if mod1 != mod2:
        return False

    # Check Manchester City vs Manchester United
    if ('city' in tok1 and 'united' in tok2) or ('united' in tok1 and 'city' in tok2):
        return False

    # Check Real Madrid vs Atletico Madrid
    is_real1 = 'real' in tok1
    is_real2 = 'real' in tok2
    is_atl1 = 'atletico' in tok1
    is_atl2 = 'atletico' in tok2
    if (is_real1 and is_atl2) or (is_real2 and is_atl1):
        return False

    # Check Inter vs AC Milan
    is_inter1 = 'inter' in tok1 or 'internazionale' in tok1
    is_inter2 = 'inter' in tok2 or 'internazionale' in tok2
    is_acmilan1 = 'milan' in tok1 and not is_inter1
    is_acmilan2 = 'milan' in tok2 and not is_inter2
    if (is_inter1 and is_acmilan2) or (is_inter2 and is_acmilan1):
        return False

    if dist1 and dist2:
        if dist1 & dist2:
            return True
        for d1 in dist1:
            for d2 in dist2:
                if d1 in d2 or d2 in d1:
                    return True
                if len(d1) >= 4 and len(d2) >= 4 and difflib.SequenceMatcher(None, d1, d2).ratio() >= 0.80:
                    return True
        return False
    return tok1 == tok2

def parse_time_minutes(t_str):
    if not t_str or ':' not in t_str:
        return None
    m = re.search(r'(\d{1,2}):(\d{2})', t_str)
    if m:
        return int(m.group(1)) * 60 + int(m.group(2))
    return None

def normalize_date_to_key(date_str, today):
    from datetime import timedelta
    if not date_str:
        return ''
    d_clean = date_str.strip().lower()
    if 'hoy' in d_clean:
        return today.strftime('%d/%m/%Y')
    if 'mañana' in d_clean or 'manana' in d_clean:
        return (today + timedelta(days=1)).strftime('%d/%m/%Y')
    m = re.search(r'(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})', date_str)
    if m:
        return f'{int(m.group(1)):02d}/{int(m.group(2)):02d}/{m.group(3)}'
    m_sp = re.search(r'(\d{1,2})\s+de\s+([a-z]+)\s+de\s+(\d{4})', d_clean)
    if m_sp:
        day = int(m_sp.group(1))
        mes_name = m_sp.group(2)
        year = int(m_sp.group(3))
        if mes_name in MESES:
            return f'{day:02d}/{MESES[mes_name]:02d}/{year}'
    return date_str.strip()

def is_same_event(ev1, ev2, today):
    d1 = normalize_date_to_key(ev1.get('date', ''), today)
    d2 = normalize_date_to_key(ev2.get('date', ''), today)
    if d1 and d2 and d1 != d2:
        return False

    # 1. Sport compatibility check FIRST!
    sp1 = ev1.get('sport')
    sp2 = ev2.get('sport')
    is_nfl1 = sp1 in ("NFL", "FUTBOL AMERICANO")
    is_nfl2 = sp2 in ("NFL", "FUTBOL AMERICANO")
    if is_nfl1 and is_nfl2:
        pass
    elif sp1 and sp2 and sp1 not in ("DEPORTES", "OTROS") and sp2 not in ("DEPORTES", "OTROS") and sp1 != sp2:
        return False

    m1 = parse_time_minutes(ev1.get('time', ''))
    m2 = parse_time_minutes(ev2.get('time', ''))
    diff = None
    if m1 is not None and m2 is not None:
        raw_diff = abs(m1 - m2)
        diff = min(raw_diff, 1440 - raw_diff)

    sides1 = split_sides(ev1.get('title', ''))
    sides2 = split_sides(ev2.get('title', ''))

    # 2. Cycling stage check
    t1_low = ev1.get('title', '').lower()
    t2_low = ev2.get('title', '').lower()
    if 'etapa' in t1_low or 'stage' in t1_low or 'etapa' in t2_low or 'stage' in t2_low:
        m_st1 = re.search(r'\b(?:etapa|stage)\s*(\d+)\b', t1_low)
        m_st2 = re.search(r'\b(?:etapa|stage)\s*(\d+)\b', t2_low)
        if m_st1 and m_st2 and m_st1.group(1) == m_st2.group(1):
            return True

    # 3. Exact 2-player / 2-team match
    if len(sides1) == 2 and len(sides2) == 2:
        match_direct = is_same_team(sides1[0], sides2[0], sp1) and is_same_team(sides1[1], sides2[1], sp1)
        match_reversed = is_same_team(sides1[0], sides2[1], sp1) and is_same_team(sides1[1], sides2[0], sp1)
        if match_direct or match_reversed:
            # Same 2 teams/players on the same date: generous tolerance (up to 3 hours for timezones / TV slot shifts / tennis delays)
            if diff is None or diff <= 180:
                return True
            return False

        # In tennis, if both events have 2 players that don't match, they are different matches
        if sp1 == "TENIS" and sp2 == "TENIS":
            return False

        # One team matches + competition matches (e.g. Racing - Real Madrid vs Real Madrid with competition)
        if is_same_team(sides1[0], sides2[0], sp1) or is_same_team(sides1[1], sides2[1], sp1) or is_same_team(sides1[0], sides2[1], sp1) or is_same_team(sides1[1], sides2[0], sp1):
            c1 = normalize_competition(ev1.get('competition', ''), sp1)
            c2 = normalize_competition(ev2.get('competition', ''), sp2)
            if c1 and c2 and (c1 == c2 or c1 in c2 or c2 in c1):
                if diff is None or diff <= 60:
                    return True
        # Both specify 2 teams and neither pair matches -> different matches!
        return False

    # 4. Tennis Tournament & Stage Matching (when at least one event does not have 2 explicit players)
    if sp1 == "TENIS" and sp2 == "TENIS":
        tourn1 = tennis_tournament_tokens(ev1.get('competition', ''), ev1.get('title', ''))
        tourn2 = tennis_tournament_tokens(ev2.get('competition', ''), ev2.get('title', ''))
        if tourn1 & tourn2:
            gen1 = is_generic_stage_title(ev1.get('title', ''), ev1.get('competition', ''))
            gen2 = is_generic_stage_title(ev2.get('title', ''), ev2.get('competition', ''))
            if gen1 or gen2:
                combined_text = clean_str(ev1.get('title', '') + ' ' + ev2.get('title', '')).split()
                is_final = any(k in combined_text for k in ['final', 'finals']) and not any(k in combined_text for k in ['semifinal', 'semifinales', 'cuartos', 'octavos'])
                max_tennis_diff = 150 if is_final else 75
                if diff is None or diff <= max_tennis_diff:
                    return True

    # 5. F1 / Motorsport Check
    if is_f1_event(ev1) and is_f1_event(ev2):
        if diff is None or diff <= 120:
            return True

    # 6. Share streamId hash check (only if sports are compatible and neither event has contradictory team pairs)
    h1 = {c["streamId"] for c in ev1.get("channels", []) if c.get("streamId")}
    h2 = {c["streamId"] for c in ev2.get("channels", []) if c.get("streamId")}
    if h1 and h2 and (h1 & h2):
        if len(sides1) == 2 and len(sides2) == 2:
            if not (is_same_team(sides1[0], sides2[0], sp1) or is_same_team(sides1[1], sides2[1], sp1) or
                    is_same_team(sides1[0], sides2[1], sp1) or is_same_team(sides1[1], sides2[0], sp1)):
                return False
        if diff is None or diff <= 180:
            return True

    # 7. Single team / partial match (with time tolerance <= 45 min)
    if diff is not None and diff > 45:
        return False
    if (m1 is None) != (m2 is None):
        return False

    if len(sides1) == 1 and len(sides2) == 1:
        if is_same_team(sides1[0], sides2[0], sp1):
            return True
    elif (len(sides1) == 2 and len(sides2) == 1) or (len(sides1) == 1 and len(sides2) == 2):
        pair = sides1 if len(sides1) == 2 else sides2
        single = sides2[0] if len(sides1) == 2 else sides1[0]
        if is_same_team(pair[0], single, sp1) or is_same_team(pair[1], single, sp1):
            return True

    return False

def parse_direct_event(raw_title, default_date="Hoy"):
    m_hour = re.search(r'\b(\d{1,2}:\d{2})\b', raw_title)
    ev_hour = m_hour.group(1) if m_hour else ""
    
    # Strip hour pattern from clean_t before any colon split
    clean_t = re.sub(r'\b\d{1,2}:\d{2}\b', '', raw_title)
    clean_t = re.sub(r'\s+', ' ', clean_t).strip()
    
    comp = ""
    teams = clean_t
    if ":" in clean_t:
        parts = clean_t.split(":", 1)
        p0 = parts[0].strip()
        p1 = parts[1].strip()
        if p1 and len(p1) > 2 and not any(sep in p0.lower() for sep in [" vs ", " vs. ", " - "]):
            comp = p0
            teams = p1
    
    if not comp:
        for cw in COMP_WORDS:
            m_comp = re.match(r'^(' + cw + r')\s*[-–—:]\s*(.*)', teams, flags=re.IGNORECASE)
            if m_comp:
                comp = m_comp.group(1).strip()
                teams = m_comp.group(2).strip()
                break

    if not comp and ' - ' in teams:
        dash_parts = teams.split(' - ')
        if len(dash_parts) >= 3:
            comp = dash_parts[0].strip()
            teams = ' - '.join(dash_parts[1:]).strip()
                
    teams = re.sub(STAGE_PREFIXES, '', teams, flags=re.IGNORECASE).strip()
    teams = re.sub(r'\[.*?\]', '', teams).strip()
    teams = re.sub(r'\(.*?\)', '', teams).strip()
    teams = re.sub(r'^[-–—:]+|[-–—:]+$', '', teams).strip()
    
    sport = normalize_sport(comp + " " + teams, comp)
    return {
        "title": teams if teams else clean_t,
        "time": ev_hour,
        "date": default_date,
        "sport": sport,
        "competition": comp if comp else sport
    }

def channel_matches(target_canonical, base_canonical):
    if target_canonical.lower() == base_canonical.lower():
        return True
    m1 = re.search(r'\b(\d+)\b', target_canonical)
    m2 = re.search(r'\b(\d+)\b', base_canonical)
    num1 = m1.group(1) if m1 else "1"
    num2 = m2.group(1) if m2 else "1"
    b1 = re.sub(r'\b\d+\b', '', target_canonical).strip().lower()
    b2 = re.sub(r'\b\d+\b', '', base_canonical).strip().lower()
    return b1 == b2 and num1 == num2

def find_channels_for_event(marca_channel_str, unified_channels):
    # Split on /, |, comma, or parentheses, but NOT '+' to prevent breaking 'M+' or 'Movistar Plus+'
    parts = re.split(r'[/,|()]', marca_channel_str)
    matched = []
    seen_hashes = set()

    for raw_part in parts:
        raw_part = raw_part.strip()
        if not raw_part:
            continue

        target_canon = canonical_channel_name(raw_part)
        p_clean = clean_channel_name(raw_part)
        m_num = re.search(r'\b(\d+)\b', p_clean)
        target_num = m_num.group(1) if m_num else "1"

        # 1. Match canonical
        for base_name, ch_list in unified_channels.items():
            if channel_matches(target_canon, base_name):
                for ch in ch_list:
                    if ch["streamId"] not in seen_hashes:
                        seen_hashes.add(ch["streamId"])
                        matched.append(ch)

        # 2. Fallback fuzzy check only if no canonical match, AND channel numbers must match
        if not matched and p_clean:
            for base_name, ch_list in unified_channels.items():
                b_clean = clean_channel_name(base_name)
                b_num_m = re.search(r'\b(\d+)\b', b_clean)
                b_num = b_num_m.group(1) if b_num_m else "1"
                if target_num == b_num:
                    # Avoid matching generic provider names against specific subchannels
                    if b_clean in ("movistar", "movistar plus", "dazn", "eurosport", "m", "mplus"):
                        continue
                    if b_clean == p_clean or (len(p_clean) > 4 and len(b_clean) > 4 and (p_clean in b_clean or b_clean in p_clean)):
                        for ch in ch_list:
                            if ch["streamId"] not in seen_hashes:
                                seen_hashes.add(ch["streamId"])
                                matched.append(ch)

    return matched

def parse_marca_header_date(h_text, today):
    from datetime import timedelta, date
    if not h_text or not today:
        return None
    h_clean = h_text.lower().strip()
    m = re.search(r'(\d{1,2})\s+de\s+([a-záéíóú]+)\s+de\s+(\d{4})', h_clean)
    if m:
        day = int(m.group(1))
        mes_str = m.group(2)
        year = int(m.group(3))
        month = MESES.get(mes_str)
        if month:
            try:
                target_date = date(year, month, day)
                if target_date < today:
                    return "PAST"
                if target_date == today:
                    return "Hoy"
                elif target_date == today + timedelta(days=1):
                    return "Mañana"
                else:
                    return target_date.strftime("%d/%m/%Y")
            except Exception:
                pass
    return None

def parse_marca_schedule(html_content, unified_channels, today=None):
    events = []
    sections = re.findall(r'<li\s+class=["\']content-item["\']>(.*?)(?=<li\s+class=["\']content-item["\']|</ul>|</ol>\s*</div>)', html_content, re.DOTALL)

    event_pattern = re.compile(r'(?si)<li\s+class=[\'"]dailyevent[\'"]>(.*?)</li>')
    sport_pattern = re.compile(r'(?si)<span\s+class=[\'"]dailyday[\'"]>(.*?)</span>')
    hour_pattern = re.compile(r'(?si)<strong\s+class=[\'"]dailyhour[\'"]>(.*?)</strong>')
    comp_pattern = re.compile(r'(?si)<span\s+class=[\'"]dailycompetition[\'"]>(.*?)</span>')
    teams_pattern = re.compile(r'(?si)<h4\s+class=[\'"]dailyteams[\'"]>(.*?)</h4>')
    channel_pattern = re.compile(r'(?si)<span\s+class=[\'"]dailychannel[\'"]>(.*?)</span>')
    header_pattern = re.compile(r'(?si)<span\s+class=[\'"]title-section-widget[\'"]>(.*?)</span>')

    # If sections not found, fallback to parsing all dailyevents
    if not sections:
        sections = [html_content]

    day_index = 0
    event_counter = 1

    for section in sections:
        blocks = event_pattern.findall(section)
        if not blocks:
            continue

        h_match = header_pattern.search(section)
        h_text = re.sub(r'<[^>]+>', ' ', h_match.group(1)).strip() if h_match else ""

        parsed_label = parse_marca_header_date(h_text, today) if today else None
        if parsed_label == "PAST":
            continue
        elif parsed_label:
            day_label = parsed_label
        else:
            if day_index == 0:
                day_label = "Hoy"
            elif day_index == 1:
                day_label = "Mañana"
            else:
                day_label = h_text if h_text else f"Día +{day_index}"

        day_index += 1

        for block in blocks:
            sM = sport_pattern.search(block)
            raw_sport = re.sub(r'<[^>]+>', '', sM.group(1)).strip() if sM else ""

            hM = hour_pattern.search(block)
            raw_hour = re.sub(r'<[^>]+>', '', hM.group(1)).strip() if hM else ""

            cM = comp_pattern.search(block)
            raw_comp = re.sub(r'<[^>]+>', '', cM.group(1)).strip() if cM else ""

            tM = teams_pattern.search(block)
            raw_teams = unescape(re.sub(r'<[^>]+>', '', tM.group(1)).strip()) if tM else ""

            chM = channel_pattern.search(block)
            raw_channel = re.sub(r'<[^>]+>', '', chM.group(1)).strip() if chM else ""

            if not raw_teams or not raw_channel:
                continue

            matched_channels = find_channels_for_event(raw_channel, unified_channels)
            if matched_channels:
                events.append({
                    "id": f"pina_{event_counter}",
                    "title": raw_teams,
                    "sport": normalize_sport(raw_sport, raw_comp),
                    "competition": raw_comp if raw_comp else normalize_sport(raw_sport, raw_comp),
                    "time": raw_hour if raw_hour else "",
                    "date": day_label,
                    "channels": matched_channels
                })
                event_counter += 1

    return events

def consolidate_events(events, today):
    consolidated = []
    merged_count = 0
    for ev in events:
        merged = False
        for c_ev in consolidated:
            if is_same_event(c_ev, ev, today):
                existing_hashes = {ch["streamId"] for ch in c_ev.get("channels", []) if ch.get("streamId")}
                for ch in ev.get("channels", []):
                    h = ch.get("streamId")
                    if h and h not in existing_hashes:
                        c_ev["channels"].append(ch)
                        existing_hashes.add(h)

                # Title selection: avoid "00", favor clean casing over ALL_CAPS, and prefer specific match over generic stage
                t1 = c_ev.get("title", "").strip()
                t2 = ev.get("title", "").strip()
                comp1 = c_ev.get("competition", "").strip()
                comp2 = ev.get("competition", "").strip()
                if t1 in ("00", ""):
                    c_ev["title"] = t2
                elif t2 not in ("00", ""):
                    gen1 = is_generic_stage_title(t1, comp1)
                    gen2 = is_generic_stage_title(t2, comp2)
                    if gen1 and not gen2:
                        c_ev["title"] = t2
                        c_ev["time"] = ev.get("time") or c_ev.get("time")
                    elif not gen1 and gen2:
                        pass
                    elif t1.isupper() and not t2.isupper():
                        c_ev["title"] = t2
                    elif not t1.isupper() and t2.isupper():
                        pass
                    elif len(t2) > len(t1) and not t2.isupper():
                        c_ev["title"] = t2

                # Sport selection: favor specific sports over DEPORTES/OTROS/generic
                s1 = c_ev.get("sport", "").strip()
                s2 = ev.get("sport", "").strip()
                specific_sports = ("FUTBOL AMERICANO", "NFL", "CICLISMO", "BALONCESTO", "TENIS", "MOTOR", "SNOOKER", "GOLF", "PADEL", "BOXEO", "RUGBY", "BALONMANO", "BEISBOL", "HOCKEY", "FUTBOL")
                if s1 in ("DEPORTES", "OTROS", "") and s2 in specific_sports:
                    c_ev["sport"] = s2

                # Competition selection: prefer non-empty and non-generic
                comp1 = c_ev.get("competition", "").strip()
                comp2 = ev.get("competition", "").strip()
                generic_comps = ("", s1, "DEPORTES", "OTROS", "SPANISH LEAGUE", "SPANISH LEAGUE 2", "BASILEA TOURNAMENT", "VIENNA TOURNAMENT")
                if comp1 in generic_comps and comp2 and comp2 not in ("DEPORTES", "OTROS"):
                    c_ev["competition"] = comp2

                merged = True
                merged_count += 1
                break
        if not merged:
            consolidated.append(ev)
    if merged_count > 0:
        print(f"Consolidación final: {merged_count} eventos duplicados fusionados con éxito.")
    return consolidated

def is_f1_event(ev):
    sport = ev.get('sport', '').upper()
    comp = ev.get('competition', '').upper()
    title = ev.get('title', '').upper()
    full = f"{sport} {comp} {title}"
    if any(k in full for k in ["F1", "FORMULA 1", "FORMULA", "FRMULA 1", "FÓRMULA 1"]):
        return True
    if sport == "MOTOR":
        f1_keywords = [
            "AZERBAIJAN", "BAKU", "MONZA", "SINGAPORE", "SINGAPUR", "SILVERSTONE",
            "SUZUKA", "INTERLAGOS", "AUSTIN", "LAS VEGAS", "ABU DHABI", "MELBOURNE",
            "MONACO", "CATALUNYA", "MONTREAL", "RED BULL RING", "HUNGARORING",
            "ZANDVOORT", "QATAR", "JEDDAH", "BAHRAIN", "SAKHIR", "SHANGHAI", "MIAMI",
            "IMOLA", "SPA-FRANCORCHAMPS", "MEXICO", "MÉXICO", "HERMANOS RODRIGUEZ",
            "SAO PAULO", "LUSAIL", "YAS MARINA"
        ]
        if any(k in full for k in f1_keywords):
            if not any(m in full for m in ["MOTOGP", "MOTO GP", "MOTO2", "MOTO3"]):
                return True
    return False

def is_motogp_event(ev):
    sport = ev.get('sport', '').upper()
    comp = ev.get('competition', '').upper()
    title = ev.get('title', '').upper()
    full = f"{sport} {comp} {title}"
    return any(k in full for k in ["MOTOGP", "MOTO GP", "MOTO2", "MOTO3", "SUPERBIKE"])

def enrich_thematic_channels(events, unified_channels):
    f1_channels = unified_channels.get("DAZN F1", [])
    motogp_channels = unified_channels.get("DAZN MotoGP", [])

    enriched_count = 0
    for ev in events:
        if is_f1_event(ev) and f1_channels:
            existing_hashes = {c["streamId"] for c in ev.get("channels", []) if c.get("streamId")}
            added = 0
            for ch in f1_channels:
                if ch.get("streamId") and ch["streamId"] not in existing_hashes:
                    ev["channels"].append(ch)
                    existing_hashes.add(ch["streamId"])
                    added += 1
            if added > 0:
                enriched_count += 1
                print(f"Enriquecido evento F1 '{ev.get('title')}' ({ev.get('competition')}) con {added} opciones de DAZN F1.")

        elif is_motogp_event(ev) and motogp_channels:
            existing_hashes = {c["streamId"] for c in ev.get("channels", []) if c.get("streamId")}
            added = 0
            for ch in motogp_channels:
                if ch.get("streamId") and ch["streamId"] not in existing_hashes:
                    ev["channels"].append(ch)
                    existing_hashes.add(ch["streamId"])
                    added += 1
            if added > 0:
                enriched_count += 1
                print(f"Enriquecido evento MotoGP '{ev.get('title')}' ({ev.get('competition')}) con {added} opciones de DAZN MotoGP.")

    if enriched_count > 0:
        print(f"Enriquecimiento temático completado: {enriched_count} eventos complementados con canales 24/7 especializados.")

def load_previous_agenda(today):
    if not os.path.exists(OUTPUT_FILE):
        return []
    try:
        with open(OUTPUT_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)
        if not isinstance(data, list):
            return []
        from datetime import datetime

        # Check metadata timestamp to see when the previous agenda was generated
        meta_date = None
        for item in data:
            if item.get("_metadata") or "updatedAt" in item:
                upd = item.get("updatedAt", "")
                if upd and len(upd) >= 10:
                    try:
                        meta_date = datetime.strptime(upd[:10], "%d/%m/%Y").date()
                    except Exception:
                        pass
                break

        from datetime import datetime, timedelta

        # Check metadata timestamp to see when the previous agenda was generated
        meta_date = None
        for item in data:
            if item.get("_metadata") or "updatedAt" in item:
                upd = item.get("updatedAt", "")
                if upd and len(upd) >= 10:
                    try:
                        meta_date = datetime.strptime(upd[:10], "%d/%m/%Y").date()
                    except Exception:
                        pass
                    break

        # If previous agenda is from today, keep 'Hoy'; if from yesterday, promote 'Mañana' -> 'Hoy'
        is_same_day = (meta_date == today)
        is_yesterday = (meta_date == (today - timedelta(days=1)))

        prev_events = []
        for ev in data:
            if ev.get("_metadata") or ev.get("sport") == "DIRECTO 24/7" or ev.get("date") == "24/7":
                continue
            d = ev.get("date", "")
            # Descartar canales de Markel persistidos de agendas previas
            cleaned_channels = [
                ch for ch in ev.get("channels", [])
                if ch.get("source") != "Markel" and "[markel]" not in ch.get("name", "").lower()
            ]
            if not cleaned_channels:
                continue
            ev_copy = dict(ev)
            ev_copy["channels"] = cleaned_channels

            if d == "Hoy":
                if is_same_day:
                    prev_events.append(ev_copy)
            elif d in ("Mañana", "Manana"):
                if is_yesterday:
                    ev_copy["date"] = "Hoy"
                    prev_events.append(ev_copy)
            elif re.match(r'^\d{2}/\d{2}/\d{4}$', d):
                try:
                    ev_d = datetime.strptime(d, "%d/%m/%Y").date()
                    if ev_d == today:
                        ev_copy["date"] = "Hoy"
                        prev_events.append(ev_copy)
                except Exception:
                    pass
        return prev_events
    except Exception as e:
        print(f"Aviso al cargar agenda previa: {e}")
        return []

def get_madrid_now():
    """Devuelve datetime aware en Europe/Madrid usando ZoneInfo con fallback CET/CEST automático."""
    from datetime import datetime, timezone, timedelta
    try:
        from zoneinfo import ZoneInfo
        return datetime.now(ZoneInfo("Europe/Madrid"))
    except Exception:
        now_utc = datetime.now(timezone.utc)
        year = now_utc.year
        # Último domingo de marzo a las 01:00 UTC -> último domingo de octubre a las 01:00 UTC
        mar_31 = datetime(year, 3, 31, 1, 0, tzinfo=timezone.utc)
        dst_start = mar_31 - timedelta(days=(mar_31.weekday() + 1) % 7)
        oct_31 = datetime(year, 10, 31, 1, 0, tzinfo=timezone.utc)
        dst_end = oct_31 - timedelta(days=(oct_31.weekday() + 1) % 7)
        offset_hours = 2 if dst_start <= now_utc < dst_end else 1
        return now_utc.astimezone(timezone(timedelta(hours=offset_hours)))


def generate_piñavision_agenda():
    print("=== Generando agenda PIÑAVISION ===")
    markel = load_markel_channels()
    if markel:
        print(f"Canales Markel cargados: {len(markel)} bases")
    else:
        print("Fuente Markel desactivada (canales caídos/inestables eliminados).")

    peticiones = load_peticiones_channels()
    print(f"Canales Peticiones cargados: {len(peticiones)} bases")

    tdt_sports = load_tdt_sports_channels()
    print(f"Canales TDT en abierto cargados: {len(tdt_sports)} bases")

    arena_streams, arena_events = load_arenavision()

    # Add ArenaVision streams to unified channels so they can be viewed in 24/7 TV too
    arenavision_channels = OrderedDict()
    for num, h in arena_streams.items():
        base = f"ArenaVision {num}"
        arenavision_channels[base] = [{
            "name": f"ArenaVision {num} (AV{num})",
            "streamId": h,
            "type": "ACESTREAM",
            "source": "ArenaVision"
        }]

    # Unify channels by base name
    unified_channels = OrderedDict()
    all_bases = list(OrderedDict.fromkeys(list(markel.keys()) + list(peticiones.keys()) + list(arenavision_channels.keys()) + list(tdt_sports.keys())))

    for b in all_bases:
        unified_channels[b] = []
        if b in markel:
            unified_channels[b].extend(markel[b])
        if b in peticiones:
            unified_channels[b].extend(peticiones[b])
        if b in arenavision_channels:
            unified_channels[b].extend(arenavision_channels[b])
        if b in tdt_sports:
            unified_channels[b].extend(tdt_sports[b])

    print(f"Total bases unificadas: {len(unified_channels)}")

    direct_eventos = load_direct_eventos()
    print(f"Streams directos de eventos cargados: {len(direct_eventos)}")

    # Setup dates for Madrid timezone
    from datetime import timedelta
    now_madrid = get_madrid_now()
    today = now_madrid.date()
    today_str = today.strftime("%d/%m/%Y")
    tomorrow_str = (today + timedelta(days=1)).strftime("%d/%m/%Y")
    now_minutes = now_madrid.hour * 60 + now_madrid.minute

    # Fetch Marca
    marca_html = fetch_url("https://www.marca.com/programacion-tv.html")
    if not marca_html:
        print("Aviso: No se pudo descargar Marca Guía TV.")
        schedule_events = []
    else:
        schedule_events = parse_marca_schedule(marca_html, unified_channels, today)
        print(f"Eventos emparejados con Marca: {len(schedule_events)}")

    # Enrich confirmed events with channels from previous agenda (retention of stream links)
    prev_events = load_previous_agenda(today)
    if prev_events:
        enriched_count = 0
        for prev_ev in prev_events:
            for s_ev in schedule_events:
                if is_same_event(s_ev, prev_ev, today):
                    existing_hashes = {c["streamId"] for c in s_ev.get("channels", []) if c.get("streamId")}
                    for ch in prev_ev.get("channels", []):
                        h = ch.get("streamId")
                        if h and h not in existing_hashes:
                            s_ev["channels"].append(ch)
                            existing_hashes.add(h)
                    enriched_count += 1
                    break
        print(f"Persistencia de canales de agenda previa: {enriched_count} eventos enriquecidos.")

    # 1. Integrate ArenaVision events (which have verified dates DD/MM/YYYY)
    filtered_arena_events = []
    if arena_events:
        for a_ev in arena_events:
            d_raw = a_ev.get("date", "").strip()
            try:
                ev_date = datetime.strptime(d_raw, "%d/%m/%Y").date()
            except Exception:
                continue

            # Skip past events (yesterday or older)
            if ev_date < today:
                continue

            if ev_date == today:
                a_ev["date"] = "Hoy"
            elif ev_date == today + timedelta(days=1):
                a_ev["date"] = "Mañana"
            else:
                a_ev["date"] = d_raw

            filtered_arena_events.append(a_ev)

        added_arena_count = 0
        merged_arena_count = 0
        for a_ev in filtered_arena_events:
            matched = False
            for s_ev in schedule_events:
                if is_same_event(s_ev, a_ev, today):
                    existing_hashes = {c["streamId"] for c in s_ev.get("channels", []) if c.get("streamId")}
                    for ch in a_ev.get("channels", []):
                        if ch.get("streamId") and ch["streamId"] not in existing_hashes:
                            s_ev["channels"].append(ch)
                            existing_hashes.add(ch["streamId"])
                    t_s = s_ev.get("title", "").strip()
                    t_a = a_ev.get("title", "").strip()
                    comp_s = s_ev.get("competition", "").strip()
                    comp_a = a_ev.get("competition", "").strip()
                    gen_s = is_generic_stage_title(t_s, comp_s)
                    gen_a = is_generic_stage_title(t_a, comp_a)
                    if gen_s and not gen_a:
                        s_ev["title"] = t_a
                        s_ev["time"] = a_ev.get("time") or s_ev.get("time")
                    elif not gen_s and gen_a:
                        pass
                    elif t_s.isupper() and not t_a.isupper():
                        s_ev["title"] = t_a

                    if comp_s in ("", s_ev.get("sport", ""), "DEPORTES", "OTROS") and comp_a and comp_a not in ("DEPORTES", "OTROS"):
                        s_ev["competition"] = comp_a
                    matched = True
                    merged_arena_count += 1
                    break

            if not matched:
                a_ev["id"] = f"pina_{len(schedule_events) + 1}"
                schedule_events.append(a_ev)
                added_arena_count += 1

        print(f"ArenaVision integrado: {merged_arena_count} emparejados con eventos existentes, {added_arena_count} añadidos como nuevos eventos (descartados {len(arena_events) - len(filtered_arena_events)} pasados).")

    # 2. Integrate LiveTV events
    livetv_events = load_livetv_events(today)
    if livetv_events:
        added_livetv_count = 0
        merged_livetv_count = 0
        for l_ev in livetv_events:
            matched = False
            for s_ev in schedule_events:
                if is_same_event(s_ev, l_ev, today):
                    existing_hashes = {c["streamId"] for c in s_ev.get("channels", []) if c.get("streamId")}
                    for ch in l_ev.get("channels", []):
                        if ch.get("streamId") and ch["streamId"] not in existing_hashes:
                            s_ev["channels"].append(ch)
                            existing_hashes.add(ch["streamId"])
                    matched = True
                    merged_livetv_count += 1
                    break

            if not matched:
                l_ev["id"] = f"pina_{len(schedule_events) + 1}"
                schedule_events.append(l_ev)
                added_livetv_count += 1

        print(f"LiveTV integrado: {merged_livetv_count} emparejados con eventos existentes, {added_livetv_count} añadidos como nuevos eventos.")


    # 2. Enrich verified schedule events for 'Hoy' with direct streams (eventos.m3u)
    # Undated streams from community m3u are ONLY attached to confirmed events to avoid reviving yesterday's matches
    merged_direct_count = 0
    if direct_eventos:
        for d_ev in direct_eventos:
            parsed_d = parse_direct_event(d_ev["raw_title"], default_date="Hoy")
            h = d_ev["streamId"]

            for s_ev in schedule_events:
                if s_ev.get("date") == "Hoy" and is_same_event(s_ev, parsed_d, today):
                    if not any(c["streamId"] == h for c in s_ev["channels"]):
                        opt_num = sum(1 for c in s_ev["channels"] if "Directa" in c["name"]) + 1
                        s_ev["channels"].append({
                            "name": f"{s_ev['title']} - Opción Directa {opt_num} [Comunidad]",
                            "streamId": h,
                            "type": "ACESTREAM",
                            "source": "Comunidad"
                        })
                        merged_direct_count += 1
                    break

        print(f"Eventos directos vinculados a partidos confirmados de hoy: {merged_direct_count} enlaces agregados.")

    # Filter out events from 'Hoy' that started more than 3 hours ago (Madrid time)
    now_minutes = now_madrid.hour * 60 + now_madrid.minute
    cutoff_minutes_today = now_minutes - 180  # 3 hours ago
    if cutoff_minutes_today > 0:
        valid_schedule_events = []
        purged_today_count = 0
        for ev in schedule_events:
            d = ev.get("date", "")
            t = ev.get("time", "")
            if d == "Hoy" and t and ":" in t:
                try:
                    parts = t.split(":")
                    ev_minutes = int(parts[0]) * 60 + int(parts[1])
                    if ev_minutes < cutoff_minutes_today:
                        purged_today_count += 1
                        continue
                except Exception:
                    pass
            valid_schedule_events.append(ev)
        print(f"Purgados {purged_today_count} eventos de 'Hoy' que comenzaron hace más de 3 horas (anteriores a {cutoff_minutes_today // 60:02d}:{cutoff_minutes_today % 60:02d}).")
        schedule_events = valid_schedule_events

    # Consolidate duplicate events across all integrated sources
    schedule_events = consolidate_events(schedule_events, today)

    # Enrich sports with dedicated 24/7 channels (e.g. F1 -> DAZN F1, MotoGP -> DAZN MotoGP)
    enrich_thematic_channels(schedule_events, unified_channels)

    # Re-assign clean IDs
    for idx, ev in enumerate(schedule_events, 1):
        ev["id"] = f"pina_{idx}"

    # Chronological sorting for all schedule events
    def get_event_sort_key(ev):
        d = ev.get("date", "")
        t = ev.get("time", "")
        if d == "Hoy":
            day_rank = 0
        elif d == "Mañana":
            day_rank = 1
        else:
            try:
                dt_obj = datetime.strptime(d, "%d/%m/%Y").date()
                day_rank = (dt_obj - today).days
            except Exception:
                day_rank = 99

        time_tuple = (99, 99)
        if t and ":" in t:
            parts = t.split(":")
            try:
                time_tuple = (int(parts[0]), int(parts[1]))
            except Exception:
                pass

        return (day_rank, time_tuple)
    schedule_events.sort(key=get_event_sort_key)

    # Add 24/7 channels at the end
    event_id = len(schedule_events) + 1
    channels_247 = []
    for base, ch_list in unified_channels.items():
        if not ch_list:
            continue
        # Filter sports 24/7 (include ArenaVision channels too)
        cu = base.upper()
        is_sports = any(k in cu for k in [
            "DAZN", "M+", "LALIGA", "DEPORTES", "VAMOS", "GOL", "TELEDEPORTE", "TDP",
            "EUROSPORT", "RFEF", "FORMULA", "F1", "MOTOGP", "REAL MADRID", "BARÇA", "BETIS", "SEVILLA",
            "ARENAVISION", "AV"
        ])
        if is_sports:
            channels_247.append({
                "id": str(event_id),
                "title": base,
                "sport": "DIRECTO 24/7",
                "competition": "CANALES DEPORTIVOS",
                "time": "",
                "date": "24/7",
                "channels": ch_list
            })
            event_id += 1

    total_agenda = schedule_events + channels_247
    print(f"Total eventos generados: {len(total_agenda)} (Eventos: {len(schedule_events)}, Canales 24/7: {len(channels_247)})")

    if total_agenda:
        now_madrid = get_madrid_now()
        updated_str = now_madrid.strftime("%d/%m/%Y %H:%M")

        metadata_item = {
            "_metadata": True,
            "updatedAt": updated_str,
            "timezone": "Madrid,Paris,Bruselas"
        }
        total_agenda.insert(0, metadata_item)

        with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
            json.dump(total_agenda, f, ensure_ascii=False, indent=2)
        print(f"Archivo guardado exitosamente con metadatos ({updated_str}) en: {OUTPUT_FILE}")
    else:
        print("Error: No se generó ningún evento. Conservando agenda previa si existe.")

if __name__ == "__main__":
    generate_piñavision_agenda()
