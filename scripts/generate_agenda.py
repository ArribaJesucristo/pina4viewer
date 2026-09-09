import os
import re
import json
import urllib.request
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
    urls = [
        "https://www.markellinks.app/assets/links.json",
        "https://raw.githubusercontent.com/Icastresana/lista1/main/links.json"
    ]
    raw = ""
    for u in urls:
        raw = fetch_url(u)
        if raw.strip().startswith("[") or raw.strip().startswith("{"):
            break
    if not raw:
        return {}

    channels_by_base = OrderedDict()
    try:
        data = json.loads(raw)
        if isinstance(data, list):
            for item in data:
                title = item.get("title", "").strip()
                url = item.get("url", "").strip()
                h = extract_hash(url)
                if not h:
                    continue
                base = canonical_channel_name(title)
                if not base:
                    base = title
                if base not in channels_by_base:
                    channels_by_base[base] = []
                # Check if hash already in base
                if not any(x["streamId"] == h for x in channels_by_base[base]):
                    channels_by_base[base].append({
                        "name": f"{base} - Opción {len(channels_by_base[base]) + 1} [Markel]",
                        "streamId": h,
                        "type": "ACESTREAM",
                        "source": "Markel"
                    })
    except Exception as e:
        print(f"Error parsing Markel: {e}")
    return channels_by_base

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
                arena_events.append({
                    "title": cols[4].replace("-", " vs "),
                    "sport": normalize_sport(cols[2]),
                    "competition": cols[3],
                    "time": cols[1].replace(" CET", "").strip(),
                    "date": cols[0],
                    "channels": channels
                })

    print(f"ArenaVision cargado: {len(streams_map)} streams, {len(arena_events)} eventos de agenda.")
    return streams_map, arena_events

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
    "greece": "grecia", "japan": "japon", "brazil": "brasil"
}

COMP_WORDS = [
    r'champions\s*(?:-\s*fase liga)?',
    r'youth league\s*(?:-\s*fase liga)?',
    r'europa league\s*(?:-\s*fase liga)?',
    r'conference league\s*(?:-\s*fase liga)?',
    r'laliga\s*(?:hypermotion)?',
    r'primera federacion',
    r'segunda federacion',
    r'us open',
    r'f1\b|formula 1\b',
    r'motogp\b'
]

STAGE_PREFIXES = r'^(?:fase liga|fase de grupos|jornada \d+|ronda \d+|1/4 de final|1/8 de final|1/2 de final|primera ronda|segunda ronda|dia \d+)\s*[-–—:]*\s*'

MESES = {
    'enero': 1, 'febrero': 2, 'marzo': 3, 'abril': 4, 'mayo': 5, 'junio': 6,
    'julio': 7, 'agosto': 8, 'septiembre': 9, 'octubre': 10, 'noviembre': 11, 'diciembre': 12
}

def normalize_sport(sport_raw):
    s = clean_channel_name(sport_raw)
    if any(k in s for k in ['f1', 'formula', 'moto', 'motor', 'superbike', 'motogp', 'nascar', 'indycar', 'rally', 'dakar']):
        return 'MOTOR'
    if any(k in s for k in ['baloncesto', 'basket', 'nba', 'fiba', 'acb', 'euroliga']):
        return 'BALONCESTO'
    if any(k in s for k in ['tenis', 'tennis', 'us open', 'wta', 'atp', 'grand slam', 'roland garros', 'wimbledon', 'australian open', 'masters 1000']):
        return 'TENIS'
    if any(k in s for k in ['ciclismo', 'cycling', 'vuelta', 'giro', 'tour de francia']):
        return 'CICLISMO'
    if 'padel' in s:
        return 'PADEL'
    if any(k in s for k in ['box', 'mma', 'ufc', 'lucha']):
        return 'BOXEO'
    if 'rugby' in s:
        return 'RUGBY'
    if any(k in s for k in ['balonmano', 'handball']):
        return 'BALONMANO'
    if any(k in s for k in ['futbol', 'soccer', 'football', 'futsal', 'f sala', 'champions', 'uefa', 'laliga', 'europa league', 'conference', 'premier', 'bundesliga', 'serie a', 'copa del rey', 'youth league']):
        return 'FUTBOL'
    return sport_raw.strip().upper() if sport_raw.strip() else 'DEPORTES'

def clean_str(s):
    import unicodedata
    s = unicodedata.normalize('NFKD', s).encode('ASCII', 'ignore').decode('ASCII').lower()
    for k, v in NAME_TRANSLATIONS.items():
        s = re.sub(r'\b' + re.escape(k) + r'\b', v, s)
    s = re.sub(r'[^a-z0-9]', ' ', s)
    return re.sub(r'\s+', ' ', s).strip()

def team_tokens(team_str):
    c = clean_str(team_str)
    c = re.sub(r'\bat\b|\batl\b', 'atletico', c)
    c = re.sub(r'\br\b', 'real', c)
    c = re.sub(r'\bman\b', 'manchester', c)
    words = c.split()
    tokens = set(words)
    distinctive = {w for w in words if w not in GENERIC_WORDS and len(w) > 2}
    modifiers = {w for w in words if w in MODIFIER_WORDS}
    return tokens, distinctive, modifiers

def is_same_team(t1, t2):
    c1 = clean_str(t1)
    c2 = clean_str(t2)
    if c1 == c2:
        return True
    tok1, dist1, mod1 = team_tokens(t1)
    tok2, dist2, mod2 = team_tokens(t2)
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
        if any(d1 in d2 or d2 in d1 for d1 in dist1 for d2 in dist2 if len(d1) >= 4 and len(d2) >= 4):
            return True
        return False
    return tok1 == tok2

def split_sides(title):
    parts = re.split(r'\s+(?:vs\.?|v\.?|[-–—])\s+', title.strip(), flags=re.IGNORECASE)
    if len(parts) >= 2:
        return [parts[0].strip(), ' - '.join(parts[1:]).strip()]
    return [title.strip()]

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

    m1 = parse_time_minutes(ev1.get('time', ''))
    m2 = parse_time_minutes(ev2.get('time', ''))
    if m1 is not None and m2 is not None:
        diff = abs(m1 - m2)
        diff = min(diff, 1440 - diff)
        if diff > 45:
            return False

    sp1 = ev1.get('sport')
    sp2 = ev2.get('sport')
    if sp1 and sp2 and sp1 != "DEPORTES" and sp2 != "DEPORTES" and sp1 != sp2:
        return False

    sides1 = split_sides(ev1.get('title', ''))
    sides2 = split_sides(ev2.get('title', ''))

    if len(sides1) == 2 and len(sides2) == 2:
        match_direct = is_same_team(sides1[0], sides2[0]) and is_same_team(sides1[1], sides2[1])
        match_reversed = is_same_team(sides1[0], sides2[1]) and is_same_team(sides1[1], sides2[0])
        return match_direct or match_reversed
    elif len(sides1) == 1 and len(sides2) == 1:
        return clean_str(sides1[0]) == clean_str(sides2[0])
    return False

def parse_direct_event(raw_title, default_date="Hoy"):
    m_hour = re.search(r'\b(\d{1,2}:\d{2})\b', raw_title)
    ev_hour = m_hour.group(1) if m_hour else ""
    
    clean_t = re.sub(r'^\d{1,2}:\d{2}\s*', '', raw_title).strip()
    comp = ""
    if ":" in clean_t:
        parts = clean_t.split(":", 1)
        comp = parts[0].strip()
        teams = parts[1].strip()
    else:
        teams = clean_t
        for cw in COMP_WORDS:
            m_comp = re.match(r'^(' + cw + r')\s*[-–—:]\s*(.*)', teams, flags=re.IGNORECASE)
            if m_comp:
                comp = m_comp.group(1).strip()
                teams = m_comp.group(2).strip()
                break
                
    teams = re.sub(STAGE_PREFIXES, '', teams, flags=re.IGNORECASE).strip()
    teams = re.sub(r'\[.*?\]', '', teams).strip()
    teams = re.sub(r'\(.*?\)', '', teams).strip()
    
    sport = normalize_sport(comp + " " + teams)
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
                    if b_clean == p_clean or (len(p_clean) > 4 and (p_clean in b_clean or b_clean in p_clean)):
                        for ch in ch_list:
                            if ch["streamId"] not in seen_hashes:
                                seen_hashes.add(ch["streamId"])
                                matched.append(ch)

    return matched

def parse_marca_schedule(html_content, unified_channels):
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
                    "sport": normalize_sport(raw_sport),
                    "competition": raw_comp if raw_comp else normalize_sport(raw_sport),
                    "time": raw_hour if raw_hour else "",
                    "date": day_label,
                    "channels": matched_channels
                })
                event_counter += 1

    return events

def generate_piñavision_agenda():
    print("=== Generando agenda PIÑAVISION ===")
    markel = load_markel_channels()
    print(f"Canales Markel cargados: {len(markel)} bases")

    peticiones = load_peticiones_channels()
    print(f"Canales Peticiones cargados: {len(peticiones)} bases")

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
    all_bases = list(OrderedDict.fromkeys(list(markel.keys()) + list(peticiones.keys()) + list(arenavision_channels.keys())))

    for b in all_bases:
        unified_channels[b] = []
        if b in markel:
            unified_channels[b].extend(markel[b])
        if b in peticiones:
            unified_channels[b].extend(peticiones[b])
        if b in arenavision_channels:
            unified_channels[b].extend(arenavision_channels[b])

    print(f"Total bases unificadas: {len(unified_channels)}")

    direct_eventos = load_direct_eventos()
    print(f"Streams directos de eventos cargados: {len(direct_eventos)}")

    # Fetch Marca
    marca_html = fetch_url("https://www.marca.com/programacion-tv.html")
    if not marca_html:
        print("Aviso: No se pudo descargar Marca Guía TV.")
        schedule_events = []
    else:
        schedule_events = parse_marca_schedule(marca_html, unified_channels)
        print(f"Eventos emparejados con Marca: {len(schedule_events)}")

    # Setup dates for Madrid timezone
    from datetime import datetime, timedelta
    try:
        from zoneinfo import ZoneInfo
        madrid_tz = ZoneInfo("Europe/Madrid")
    except Exception:
        import datetime as dt
        madrid_tz = dt.timezone(dt.timedelta(hours=2))

    now_madrid = datetime.now(madrid_tz)
    today = now_madrid.date()
    today_str = today.strftime("%d/%m/%Y")
    tomorrow_str = (today + timedelta(days=1)).strftime("%d/%m/%Y")

    # Merge direct match streams from eventos.m3u into schedule events
    merged_direct_count = 0
    added_direct_count = 0
    if direct_eventos:
        for d_ev in direct_eventos:
            parsed_d = parse_direct_event(d_ev["raw_title"], default_date="Hoy")
            h = d_ev["streamId"]

            matched_s = False
            for s_ev in schedule_events:
                if is_same_event(s_ev, parsed_d, today):
                    if not any(c["streamId"] == h for c in s_ev["channels"]):
                        opt_num = sum(1 for c in s_ev["channels"] if "Directa" in c["name"]) + 1
                        s_ev["channels"].append({
                            "name": f"{s_ev['title']} - Opción Directa {opt_num} [Comunidad]",
                            "streamId": h,
                            "type": "ACESTREAM",
                            "source": "Comunidad"
                        })
                        merged_direct_count += 1
                    matched_s = True
                    break

            if not matched_s and parsed_d["time"]:
                schedule_events.append({
                    "id": f"pina_{len(schedule_events) + 1}",
                    "title": parsed_d["title"],
                    "sport": parsed_d["sport"],
                    "competition": parsed_d["competition"],
                    "time": parsed_d["time"],
                    "date": "Hoy",
                    "channels": [{
                        "name": f"{parsed_d['title']} - Opción 1 [Directo]",
                        "streamId": h,
                        "type": "ACESTREAM",
                        "source": "Directo"
                    }]
                })
                added_direct_count += 1

        print(f"Eventos directos integrados: {merged_direct_count} enlaces vinculados a partidos existentes, {added_direct_count} añadidos como partidos nuevos.")

    # Filter ArenaVision events: discard past events, normalize dates to 'Hoy' / 'Mañana'
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
                    existing_hashes = {c["streamId"] for c in s_ev["channels"]}
                    for ch in a_ev["channels"]:
                        if ch["streamId"] not in existing_hashes:
                            s_ev["channels"].append(ch)
                            existing_hashes.add(ch["streamId"])
                    matched = True
                    merged_arena_count += 1
                    break

            if not matched:
                a_ev["id"] = f"pina_{len(schedule_events) + 1}"
                schedule_events.append(a_ev)
                added_arena_count += 1

        print(f"ArenaVision integrado: {merged_arena_count} emparejados con eventos existentes, {added_arena_count} añadidos como nuevos eventos (descartados {len(arena_events) - len(filtered_arena_events)} pasados).")

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
        from datetime import datetime
        try:
            from zoneinfo import ZoneInfo
            madrid_tz = ZoneInfo("Europe/Madrid")
        except Exception:
            import datetime as dt
            madrid_tz = dt.timezone(dt.timedelta(hours=2))

        now_madrid = datetime.now(madrid_tz)
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
