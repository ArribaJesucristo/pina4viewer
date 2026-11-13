import unittest
import json
import os
from datetime import date
from scripts.generate_agenda import (
    is_same_event,
    is_same_team,
    split_sides,
    tennis_tournament_tokens,
    is_generic_stage_title,
    normalize_competition,
    load_markel_channels
)

class TestAgendaPipeline(unittest.TestCase):

    def setUp(self):
        self.today = date(2026, 11, 4)

    def test_markel_channels_disabled(self):
        channels = load_markel_channels()
        self.assertEqual(len(channels), 0, "Markel channels must be deactivated / empty")

    def test_tennis_tournament_and_stage_matching(self):
        # Specific player match vs generic stage title (e.g. Marca vs ArenaVision)
        ev_marca = {
            "title": "Lehecka - Vacherot",
            "sport": "TENIS",
            "competition": "ATP BASILEA",
            "time": "15:30",
            "date": "Hoy",
            "channels": []
        }
        ev_arena = {
            "title": "FINAL",
            "sport": "TENIS",
            "competition": "BASILEA TOURNAMENT",
            "time": "15:30",
            "date": "Hoy",
            "channels": []
        }
        self.assertTrue(is_same_event(ev_marca, ev_arena, self.today),
                        "Should merge specific tennis match with generic FINAL in same tournament")

    def test_tennis_distinct_matches_do_not_merge(self):
        # Two different tennis matches at the same tournament and time
        ev1 = {
            "title": "Alcaraz - Sinner",
            "sport": "TENIS",
            "competition": "ATP BASILEA",
            "time": "15:30",
            "date": "Hoy",
            "channels": []
        }
        ev2 = {
            "title": "Djokovic - Medvedev",
            "sport": "TENIS",
            "competition": "ATP BASILEA",
            "time": "15:30",
            "date": "Hoy",
            "channels": []
        }
        self.assertFalse(is_same_event(ev1, ev2, self.today),
                         "Should NOT merge two different matches with different players")

    def test_football_alias_and_competition_normalization(self):
        # Racing Santander vs Real Madrid (Marca vs ArenaVision)
        ev_marca = {
            "title": "Racing - Real Madrid",
            "sport": "FUTBOL",
            "competition": "LALIGA EA SPORTS",
            "time": "16:15",
            "date": "Hoy",
            "channels": []
        }
        ev_arena = {
            "title": "RACING SANTANDER vs REAL MADRID",
            "sport": "FUTBOL",
            "competition": "SPANISH LEAGUE",
            "time": "16:15",
            "date": "Hoy",
            "channels": []
        }
        self.assertTrue(is_same_event(ev_marca, ev_arena, self.today))

        # Ceuta vs Sporting Gijon
        ev_c1 = {
            "title": "Ceuta FC - Real Sporting",
            "sport": "FUTBOL",
            "competition": "LALIGA HYPERMOTION",
            "time": "16:15",
            "date": "Hoy",
            "channels": []
        }
        ev_c2 = {
            "title": "CEUTA vs SPORTING GIJON",
            "sport": "FUTBOL",
            "competition": "SPANISH LEAGUE 2",
            "time": "16:15",
            "date": "Hoy",
            "channels": []
        }
        self.assertTrue(is_same_event(ev_c1, ev_c2, self.today))

    def test_basketball_basket_modifier_handling(self):
        ev_b1 = {
            "title": "MoraBanc Andorra - Valencia Basket",
            "sport": "BALONCESTO",
            "competition": "LIGA ENDESA",
            "time": "18:00",
            "date": "Hoy",
            "channels": []
        }
        ev_b2 = {
            "title": "ANDORRA vs VALENCIA",
            "sport": "BALONCESTO",
            "competition": "SPANISH LEAGUE",
            "time": "18:00",
            "date": "Hoy",
            "channels": []
        }
        self.assertTrue(is_same_event(ev_b1, ev_b2, self.today))

    def test_motor_f1_deduplication(self):
        ev_f1_1 = {
            "title": "Autodromo Hermanos Rodriguez",
            "sport": "MOTOR",
            "competition": "G.P. MÉXICO",
            "time": "21:00",
            "date": "Hoy",
            "channels": []
        }
        ev_f1_2 = {
            "title": "F1 vs RACE",
            "sport": "MOTOR",
            "competition": "F1 - MEXICO GP",
            "time": "21:00",
            "date": "Hoy",
            "channels": []
        }
        self.assertTrue(is_same_event(ev_f1_1, ev_f1_2, self.today))

    def test_shared_stream_id_merging(self):
        ev_s1 = {
            "title": "Lehecka - Vacherot",
            "sport": "TENIS",
            "competition": "ATP BASILEA",
            "time": "15:30",
            "date": "Hoy",
            "channels": [{"streamId": "abcdef1234567890abcdef1234567890abcdef12", "name": "Option 1"}]
        }
        ev_s2 = {
            "title": "FINAL",
            "sport": "TENIS",
            "competition": "BASILEA TOURNAMENT",
            "time": "15:30",
            "date": "Hoy",
            "channels": [{"streamId": "abcdef1234567890abcdef1234567890abcdef12", "name": "Option 2"}]
        }
        self.assertTrue(is_same_event(ev_s1, ev_s2, self.today))

    def test_tennis_time_difference_exact_players_merging(self):
        # 60 minute difference between TV slot (Marca 16:00) and session start (ArenaVision 15:00)
        ev_marca = {
            "title": "Djokovic - Hijikata",
            "sport": "TENIS",
            "competition": "MASTERS PARÍS",
            "time": "16:00",
            "date": "Hoy",
            "channels": []
        }
        ev_arena = {
            "title": "DJOKOVIC vs HIJIKATA",
            "sport": "TENIS",
            "competition": "PARIS MASTERS",
            "time": "15:00",
            "date": "Hoy",
            "channels": []
        }
        self.assertTrue(is_same_event(ev_marca, ev_arena, self.today),
                        "Should merge exact same tennis players despite 60m difference in TV vs session schedule")

    def test_tennis_hyphen_without_spaces_split(self):
        # ArenaVision formats match titles as PLAYER1-PLAYER2 without spaces
        sides = split_sides("DJOKOVIC-HIJIKATA")
        self.assertEqual(len(sides), 2)
        self.assertEqual(sides[0], "DJOKOVIC")
        self.assertEqual(sides[1], "HIJIKATA")

    def test_tennis_different_opponents_do_not_merge(self):
        # Michelsen vs Sinner must NEVER merge with Michelsen vs Alcaraz
        ev1 = {
            "title": "Michelsen - Sinner",
            "sport": "TENIS",
            "competition": "MASTERS PARÍS",
            "time": "19:00",
            "date": "Hoy",
            "channels": []
        }
        ev2 = {
            "title": "MICHELSEN vs ALCARAZ",
            "sport": "TENIS",
            "competition": "PARIS MASTERS",
            "time": "19:00",
            "date": "Hoy",
            "channels": []
        }
        self.assertFalse(is_same_event(ev1, ev2, self.today),
                         "Must not merge matches with different opponents")

    def test_tennis_tournament_tokens_no_player_leakage(self):
        # Lehecka playing in Paris vs Lehecka playing in Vienna must not share tournament tokens
        tok_paris = tennis_tournament_tokens("MASTERS PARÍS", "Lehecka - Vacherot")
        tok_vienna = tennis_tournament_tokens("ATP VIENNA", "Lehecka - Sinner")
        self.assertEqual(len(tok_paris & tok_vienna), 0,
                         "Player names must not leak into tournament tokens")

    def test_tournament_title_recognized_as_generic_stage(self):
        self.assertTrue(is_generic_stage_title("PARIS MASTERS", "PARIS MASTERS"))
        self.assertTrue(is_generic_stage_title("ATP BASILEA", "BASILEA TOURNAMENT"))
        self.assertTrue(is_generic_stage_title("FINAL", "PARIS MASTERS"))
        self.assertFalse(is_generic_stage_title("Djokovic - Hijikata", "MASTERS PARÍS"))

    def test_direct_event_parsing_and_sports(self):
        from scripts.generate_agenda import parse_direct_event
        res_tennis = parse_direct_event("11:00 WTA Torneo de Hong Kong - 2ª Ronda Q. Halys - F. Tiafoe")
        self.assertEqual(res_tennis["sport"], "TENIS")
        self.assertEqual(res_tennis["competition"], "WTA Torneo de Hong Kong")
        self.assertEqual(res_tennis["title"], "Q. Halys - F. Tiafoe")
        self.assertEqual(res_tennis["time"], "11:00")

        res_basket = parse_direct_event("18:00 Eurocup - Liga Regular Lietkabelis Panevezys - Kids&Us Manresa")
        self.assertEqual(res_basket["sport"], "BALONCESTO")
        self.assertEqual(res_basket["competition"], "Eurocup")
        self.assertEqual(res_basket["title"], "Lietkabelis Panevezys - Kids&Us Manresa")
        self.assertEqual(res_basket["time"], "18:00")

    def test_agenda_json_output(self):
        agenda_path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "agenda.json")
        self.assertTrue(os.path.exists(agenda_path), "agenda.json must exist")
        with open(agenda_path, "r", encoding="utf-8") as f:
            data = json.load(f)
        self.assertIsInstance(data, list)
        self.assertGreater(len(data), 10)

        # Check metadata
        meta = data[0]
        self.assertTrue(meta.get("_metadata"), "First item must be metadata")
        self.assertIn("updatedAt", meta)

        # Check zero Markel channels across entire agenda
        for ev in data[1:]:
            for ch in ev.get("channels", []):
                self.assertNotEqual(ch.get("source"), "Markel", f"Event {ev.get('id')} has Markel source")
                self.assertNotIn("[markel]", ch.get("name", "").lower(), f"Event {ev.get('id')} has Markel name")


    def test_livetv_events_parsing_and_merging(self):
        ev_marca = {
            "title": "Real Madrid - Milan",
            "sport": "FUTBOL",
            "competition": "CHAMPIONS LEAGUE",
            "time": "21:00",
            "date": "Hoy",
            "channels": []
        }
        ev_livetv = {
            "title": "Real Madrid vs AC Milan",
            "sport": "FUTBOL",
            "competition": "Champions League",
            "time": "21:00",
            "date": "Hoy",
            "channels": [{
                "name": "[LiveTV] English 8000kbps",
                "streamId": "5f966c123759de46dff29c379266b7a403452033",
                "type": "ACESTREAM",
                "source": "LiveTV"
            }]
        }
        self.assertTrue(is_same_event(ev_marca, ev_livetv, self.today),
                        "LiveTV match must merge with Marca equivalent")

        # Tennis match matching
        ev_tennis_marca = {
            "title": "Michelsen - Alcaraz",
            "sport": "TENIS",
            "competition": "MASTERS PARÍS",
            "time": "19:00",
            "date": "Hoy",
            "channels": []
        }
        ev_tennis_livetv = {
            "title": "Alex Michelsen vs Carlos Alcaraz",
            "sport": "TENIS",
            "competition": "ATP. Paris",
            "time": "19:00",
            "date": "Hoy",
            "channels": []
        }
        self.assertTrue(is_same_event(ev_tennis_marca, ev_tennis_livetv, self.today),
                        "LiveTV tennis full names must match Marca last names")


    def test_direct_m3u_and_tdt_integration(self):
        from scripts.generate_agenda import load_direct_eventos, load_tdt_sports_channels
        # Verify direct events M3U contains multiple sources
        direct_events = load_direct_eventos()
        self.assertGreater(len(direct_events), 0, "Direct events M3U must load events")
        # Verify every direct event has valid streamId and raw_title
        for ev in direct_events:
            self.assertTrue(ev.get("raw_title"), "Direct event must have a title")
            self.assertTrue(ev.get("streamId"), "Direct event must have a streamId")

        # Verify TDT sports channels extraction
        tdt_channels = load_tdt_sports_channels()
        self.assertIsInstance(tdt_channels, dict)
        if tdt_channels:
            for base, chs in tdt_channels.items():
                for c in chs:
                    self.assertEqual(c["type"], "DIRECT")
                    self.assertTrue(c["streamId"].startswith("http"))

    def test_sport_normalization_word_boundaries(self):
        from scripts.generate_agenda import normalize_sport
        self.assertEqual(
            normalize_sport("Triatlón", "WORLD CHAMPIONSHIP SERIES"),
            "TRIATLON"
        )
        self.assertEqual(
            normalize_sport("", "World Championship Series Sprint (Abu Dhabi)"),
            "TRIATLON"
        )
        # 'pool' must NOT steal 'Liverpool' matches as SNOOKER
        self.assertEqual(normalize_sport("", "Liverpool - Arsenal"), "FUTBOL")
        # 'champions' with spaces/words keeps working for UEFA Champions League
        self.assertEqual(normalize_sport("Fútbol", "Champions League"), "FUTBOL")
        self.assertEqual(normalize_sport("Padel", "Premier Paddle"), "PADEL")

    def test_tdt_macro_placeholder_filtering(self):
        from scripts.generate_agenda import is_valid_direct_stream_url, parse_tdt_sports_m3u
        self.assertFalse(is_valid_direct_stream_url("https://stream.ads.ottera.tv/playlist.m3u8?lmt=[LMT]&did=[DEVICE_ID]"))
        self.assertFalse(is_valid_direct_stream_url("https://example.com/live.m3u8?ip=[IP]&ua=[UA]"))
        self.assertTrue(is_valid_direct_stream_url("https://rtvelivestream.rtve.es/rtvesec/tdp/tdp_main_dvr.m3u8"))

        sample_m3u = "\n".join([
            "#EXTM3U",
            "#EXTINF:-1,Real Madrid TV",
            "https://stream.ads.ottera.tv/playlist.m3u8?lmt=[LMT]&did=[DEVICE_ID]",
            "#EXTINF:-1,Teledeporte",
            "https://rtvelivestream.rtve.es/rtvesec/tdp/tdp_main_dvr.m3u8",
        ])
        parsed = parse_tdt_sports_m3u(sample_m3u)
        self.assertNotIn("Real Madrid TV", parsed)
        self.assertIn("Teledeporte", parsed)

    def test_previous_agenda_midnight_rollover(self):
        import json
        import tempfile
        from datetime import timedelta
        from unittest.mock import patch
        from scripts import generate_agenda as ga

        yesterday = self.today - timedelta(days=1)
        payload = [
            {"_metadata": True, "updatedAt": f"{yesterday.strftime('%d/%m/%Y')} 23:47", "timezone": "Madrid,Paris,Bruselas"},
            {
                "id": "1",
                "title": "Real Madrid - Barcelona",
                "sport": "FUTBOL",
                "competition": "LaLiga",
                "time": "21:00",
                "date": "Hoy",
                "channels": [{"name": "M+ LaLiga", "streamId": "1234567890abcdef1234567890abcdef12345678", "type": "ACESTREAM", "source": "ArenaVision"}]
            },
            {
                "id": "2",
                "title": "Betis - Sevilla",
                "sport": "FUTBOL",
                "competition": "LaLiga",
                "time": "20:00",
                "date": "Mañana",
                "channels": [{"name": "DAZN", "streamId": "abcdef1234567890abcdef1234567890abcdef12", "type": "ACESTREAM", "source": "ArenaVision"}]
            }
        ]
        with tempfile.NamedTemporaryFile("w", suffix=".json", encoding="utf-8", delete=False) as tf:
            json.dump(payload, tf, ensure_ascii=False)
            temp_path = tf.name
        try:
            with patch.object(ga, "OUTPUT_FILE", temp_path):
                loaded = ga.load_previous_agenda(self.today)
            titles = {ev["title"]: ev for ev in loaded}
            self.assertNotIn("Real Madrid - Barcelona", titles)
            self.assertIn("Betis - Sevilla", titles)
            self.assertEqual(titles["Betis - Sevilla"]["date"], "Hoy")
        finally:
            if os.path.exists(temp_path):
                os.remove(temp_path)


if __name__ == "__main__":
    unittest.main()
