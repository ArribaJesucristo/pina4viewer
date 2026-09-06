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

    # Team translation dictionary for enhanced matching between English & Spanish listings
    NAME_TRANSLATIONS = {
        "turkey": "turquia", "asutralia": "australia", "belgium": "belgica",
        "czech republic": "republica checa", "czech": "checa", "spain": "espana",
        "germany": "alemania", "france": "francia", "italy": "italia",
        "united states": "usa", "south korea": "corea", "stage": "etapa"
    }

    def get_title_tokens(text):
        clean = clean_channel_name(text)
        for k, v in NAME_TRANSLATIONS.items():
            clean = clean.replace(k, v)
        return set([w for w in clean.split() if len(w) > 3 or w.isdigit()])

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
            a_tokens = get_title_tokens(a_ev["title"])

            for s_ev in schedule_events:
                # Merge only if same date!
                if s_ev["date"] != a_ev["date"]:
                    continue

                s_tokens = get_title_tokens(s_ev["title"])
                # Match if shares at least 2 tokens, or 1 long token (>4 chars) and same hour
                has_token_match = len(a_tokens & s_tokens) >= 2 or any(len(w) >= 5 and w in s_tokens for w in a_tokens)
                same_hour = s_ev.get("time", "").split(":")[0] == a_ev.get("time", "").split(":")[0] if s_ev.get("time") and a_ev.get("time") else False

                if has_token_match or (len(a_tokens & s_tokens) >= 1 and same_hour):
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
