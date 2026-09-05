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

def clean_base_name(name):
    # Strip community tags, resolutions, asterisks, arrows, hashes
    s = re.sub(r'\[.*?\]', '', name)
    s = re.sub(r'\(.*?\)', '', s)
    s = re.sub(r'-->.*', '', s)
    s = re.sub(r'#.*', '', s)
    s = re.sub(r'https?://\S+', '', s)
    s = re.sub(r'(?i)\b(1080p?|720p?|4k|fhd|hd|hevc|multiaudio|spa|esp|audio|stream)\b', '', s)
    s = re.sub(r'[*_~|]', '', s)
    s = re.sub(r'\s+', ' ', s).strip()
    return s

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
                base = clean_base_name(title)
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
    url = "https://raw.githubusercontent.com/Icastresana/lista1/main/peticiones"
    raw = fetch_url(url)
    if not raw:
        return {}

    channels_by_base = OrderedDict()
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
                community = "Comunidad"
                cu = current_title.upper()
                if "ELCANO" in cu:
                    community = "Elcano"
                elif "NEW LOOP" in cu:
                    community = "New Loop"
                elif "NEW ERA" in cu:
                    community = "New Era"
                elif "DIRECTOS" in cu:
                    community = "Directos"

                base = clean_base_name(current_title)
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

def clean_channel_name(s):
    import unicodedata
    s = unicodedata.normalize('NFKD', s).encode('ASCII', 'ignore').decode('ASCII').lower()
    s = re.sub(r'[^a-z0-9]', ' ', s)
    return re.sub(r'\s+', ' ', s).strip()

def normalize_sport(sport_raw):
    s = clean_channel_name(sport_raw)
    if any(k in s for k in ["f1", "formula", "moto", "motor", "superbike"]):
        return "MOTOR"
    if any(k in s for k in ["baloncesto", "basket", "nba"]):
        return "BALONCESTO"
    if any(k in s for k in ["tenis", "tennis"]):
        return "TENIS"
    if any(k in s for k in ["ciclismo", "cycling"]):
        return "CICLISMO"
    if "padel" in s:
        return "PADEL"
    if any(k in s for k in ["box", "mma", "ufc", "lucha"]):
        return "BOXEO"
    if "rugby" in s:
        return "RUGBY"
    if any(k in s for k in ["balonmano", "handball"]):
        return "BALONMANO"
    if any(k in s for k in ["futbol", "soccer", "football", "futsal", "f sala"]):
        return "FUTBOL"
    return sport_raw.strip().upper() if sport_raw.strip() else "DEPORTES"

def find_channels_for_event(marca_channel_str, unified_channels):
    parts = re.split(r'[/,|+()]', marca_channel_str)
    matched = []
    seen_hashes = set()

    for raw_part in parts:
        p = clean_channel_name(raw_part)
        if not p:
            continue

        target_keys = []
        if p in ("gol", "gol play"):
            target_keys.append("gol")
        elif "f1" in p or "formula 1" in p:
            target_keys.append("dazn f1")
        elif "motogp" in p or "moto gp" in p:
            target_keys.append("dazn motogp")
        elif "superbike" in p:
            target_keys.append("dazn 4")
        elif "tennis channel" in p:
            target_keys.append("tennis channel")
        elif "teledeporte" in p or p == "tdp":
            target_keys.append("teledeporte")
        elif p in ("la 1", "tve 1"):
            target_keys.append("la 1")
        elif p in ("la 2", "tve 2"):
            target_keys.append("la 2")
        elif "primera federacion" in p or "1 rfef" in p or "rfef" in p:
            target_keys.extend(["primera federacion", "rfef", "1 federacion"])
        elif "hypermotion" in p:
            m = re.search(r'\b([2-5])\b', p)
            if m:
                target_keys.append(f"laliga tv hypermotion {m.group(1)}")
            else:
                target_keys.append("laliga tv hypermotion")
        elif "liga de campeones" in p or "champions" in p or "l de campeones" in p:
            m = re.search(r'\b(\d+)\b', p)
            if m:
                target_keys.append(f"m l de campeones {m.group(1)}")
            else:
                target_keys.append("m l de campeones")
        elif "laliga" in p:
            m = re.search(r'\b([2-4])\b', p)
            num = m.group(1) if m else ""
            if "dazn" in p:
                target_keys.append(f"dazn laliga {num}".strip())
            else:
                target_keys.append(f"m laliga {num}".strip())
                target_keys.append(f"laliga tv {num}".strip())
        elif "dazn" in p:
            if "baloncesto" in p or "basket" in p:
                m = re.search(r'\b([2-3])\b', p)
                num = m.group(1) if m else ""
                target_keys.append(f"dazn baloncesto {num}".strip())
            else:
                m = re.search(r'\b([1-4])\b', p)
                target_keys.append(f"dazn {m.group(1)}" if m else "dazn 1")
        elif "baloncesto" in p or "basket" in p:
            m = re.search(r'\b([2-3])\b', p)
            target_keys.append(f"m baloncesto {m.group(1)}" if m else "m baloncesto")
        elif "deportes" in p:
            m = re.search(r'\b([2-8])\b', p)
            target_keys.append(f"m deportes {m.group(1)}" if m else "m deportes")
        elif "vamos" in p:
            m = re.search(r'\b([2-3])\b', p)
            target_keys.append(f"m vamos {m.group(1)}" if m else "m vamos")
        elif "movistar plus" in p or p == "movistar":
            m = re.search(r'\b([2])\b', p)
            target_keys.append("movistar plus 2" if m else "movistar plus")
        elif "eurosport" in p:
            m = re.search(r'\b([1-2])\b', p)
            target_keys.append(f"eurosport {m.group(1)}" if m else "eurosport 1")
        else:
            for base_name in unified_channels.keys():
                c_base = clean_channel_name(base_name)
                if p == c_base or (len(p) > 3 and (p in c_base or c_base in p)):
                    target_keys.append(c_base)

        for target in target_keys:
            target_clean = clean_channel_name(target)
            if not target_clean:
                continue
            for base_name, ch_list in unified_channels.items():
                c_base = clean_channel_name(base_name)
                if c_base == target_clean:
                    for ch in ch_list:
                        if ch["streamId"] not in seen_hashes:
                            seen_hashes.add(ch["streamId"])
                            matched.append(ch)

    return matched

def parse_marca_schedule(html_content, unified_channels):
    events = []
    event_pattern = re.compile(r'(?si)<li\s+class=[\'"]dailyevent[\'"]>(.*?)</li>')
    sport_pattern = re.compile(r'(?si)<span\s+class=[\'"]dailyday[\'"]>(.*?)</span>')
    hour_pattern = re.compile(r'(?si)<strong\s+class=[\'"]dailyhour[\'"]>(.*?)</strong>')
    comp_pattern = re.compile(r'(?si)<span\s+class=[\'"]dailycompetition[\'"]>(.*?)</span>')
    teams_pattern = re.compile(r'(?si)<h4\s+class=[\'"]dailyteams[\'"]>(.*?)</h4>')
    channel_pattern = re.compile(r'(?si)<span\s+class=[\'"]dailychannel[\'"]>(.*?)</span>')

    blocks = event_pattern.findall(html_content)
    event_counter = 1

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
                "date": "Hoy",
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

    # Unify channels by base name
    unified_channels = OrderedDict()
    all_bases = list(OrderedDict.fromkeys(list(markel.keys()) + list(peticiones.keys())))

    for b in all_bases:
        unified_channels[b] = []
        if b in markel:
            unified_channels[b].extend(markel[b])
        if b in peticiones:
            unified_channels[b].extend(peticiones[b])

    print(f"Total bases unificadas: {len(unified_channels)}")

    # Fetch Marca
    marca_html = fetch_url("https://www.marca.com/programacion-tv.html")
    if not marca_html:
        print("Aviso: No se pudo descargar Marca Guía TV.")
        schedule_events = []
    else:
        schedule_events = parse_marca_schedule(marca_html, unified_channels)
        print(f"Eventos emparejados con Marca: {len(schedule_events)}")

    # Add 24/7 channels at the end
    used_hashes = set()
    for ev in schedule_events:
        for ch in ev["channels"]:
            used_hashes.add(ch["streamId"])

    event_id = len(schedule_events) + 1
    channels_247 = []
    for base, ch_list in unified_channels.items():
        if not ch_list:
            continue
        # Filter sports 24/7
        cu = base.upper()
        is_sports = any(k in cu for k in [
            "DAZN", "M+", "LALIGA", "DEPORTES", "VAMOS", "GOL", "TELEDEPORTE", "TDP",
            "EUROSPORT", "RFEF", "FORMULA", "F1", "MOTOGP", "REAL MADRID", "BARÇA", "BETIS", "SEVILLA"
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
        with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
            json.dump(total_agenda, f, ensure_ascii=False, indent=2)
        print(f"Archivo guardado exitosamente en: {OUTPUT_FILE}")
    else:
        print("Error: No se generó ningún evento. Conservando agenda previa si existe.")

if __name__ == "__main__":
    generate_piñavision_agenda()
