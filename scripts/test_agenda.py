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

if __name__ == "__main__":
    unittest.main()
