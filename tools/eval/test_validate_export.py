import copy
import unittest
from validate_export import validate


class ExportContractTest(unittest.TestCase):
    def setUp(self):
        self.document = {"transcript": [
            {"speaker_id": "speaker_1", "start_ms": 0, "end_ms": 1000, "text": "Привет!"},
            {"speaker_id": "speaker_2", "start_ms": 800, "end_ms": 1400, "text": "Здравствуйте."},
            {"speaker_id": "speaker_1", "start_ms": 1500, "end_ms": 2000, "text": "Начнём?"},
        ]}

    def test_overlaps_and_returning_speaker_are_valid(self):
        self.assertEqual((3, 2), validate(self.document, 2000))

    def test_wrong_fields_types_speakers_times_and_text_are_rejected(self):
        for field, value in [("speaker_id", "speaker_0"), ("speaker_id", "speaker_4"),
                             ("start_ms", True), ("start_ms", 0.5), ("start_ms", -1),
                             ("end_ms", 0), ("text", " "), ("text", None)]:
            doc = copy.deepcopy(self.document)
            doc["transcript"][0][field] = value
            with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                validate(doc)
        for doc in [{"transcript": []}, {"transcript": [], "metrics": {}},
                    {"transcript": [dict(self.document["transcript"][0], extra=1)]}]:
            with self.assertRaises(ValueError):
                validate(doc)
        with self.assertRaises(ValueError):
            validate(self.document, 1999)
        self.document["transcript"].reverse()
        with self.assertRaises(ValueError):
            validate(self.document)


if __name__ == "__main__":
    unittest.main()
