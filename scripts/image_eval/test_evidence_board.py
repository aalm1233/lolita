import json
from pathlib import Path
import tempfile
import unittest
from PIL import Image
import evidence_board as board
from three_view import read, save, sha


class EvidenceBoardTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.input = self.root / "front.jpg"
        Image.new("RGB", (200, 200), "red").save(self.input)
        self.ref = {"input": str(self.input), "inputSha256": sha(self.input.read_bytes()),
                    "sha256": "original-sha", "sourceType": "NO_AI_PROVENANCE"}
        self.inventory = {"items": [{"itemId": 1, "name": "fictional", "category": "test", "references": [self.ref]}]}
        save(self.root / "inventory.json", self.inventory)
        self.spec = {"id": "case-1", "itemId": 1,
                     "views": [{"view": "front", "input": str(self.input), "confirmed": True}]}

    def tearDown(self):
        self.temp.cleanup()

    def test_missing_view_creates_no_job_and_is_locally_unknown(self):
        jobs = read(board.prepare(self.root, self.spec))
        self.assertEqual([j["evidenceView"] for j in jobs], ["front"])
        board.compose(self.root, self.spec)
        result = read(self.root / "evidence/case-1/board-result.json")
        self.assertEqual(result["states"]["side"], "UNKNOWN_NO_REQUEST")
        self.assertEqual(result["states"]["back"], "UNKNOWN_NO_REQUEST")
        self.assertEqual(result["paidCallsByComposer"], 0)

    def test_historical_ai_cannot_supply_new_view_evidence(self):
        self.ref["sourceType"] = "AI_GENERATED"
        save(self.root / "inventory.json", self.inventory)
        with self.assertRaisesRegex(ValueError, "PHOTOGRAPH"):
            board.prepare(self.root, self.spec)

    def test_changed_input_and_unconfirmed_view_rejected(self):
        self.spec["views"][0]["confirmed"] = False
        with self.assertRaisesRegex(ValueError, "NOT_CONFIRMED"):
            board.prepare(self.root, self.spec)
        self.spec["views"][0]["confirmed"] = True
        self.input.write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "CHANGED_INPUT"):
            board.prepare(self.root, self.spec)

    def test_generated_output_requires_exact_explicit_approval(self):
        board.prepare(self.root, self.spec)
        out = self.root / "runs/case-1-front"
        out.mkdir(parents=True)
        Image.new("RGB", (200, 200), "blue").save(out / "output.png")
        digest = sha((out / "output.png").read_bytes())
        save(out / "result.json", {"status": "SUCCESS", "outputSha256": digest})
        board.compose(self.root, self.spec)
        self.assertEqual(read(self.root / "evidence/case-1/board-result.json")["states"]["front"], "SOURCE_CROP")
        self.spec["approvedResults"] = {"front": "wrong"}
        with self.assertRaisesRegex(ValueError, "NOT_EXPLICITLY_APPROVED"):
            board.compose(self.root, self.spec)
        self.spec["approvedResults"] = {"front": digest}
        board.compose(self.root, self.spec)
        self.assertEqual(read(self.root / "evidence/case-1/board-result.json")["states"]["front"], "APPROVED_AI_EXTRACTION")

    def test_invalid_crop_and_duplicate_views_rejected(self):
        self.spec["views"][0]["crop"] = [0, 0, 2, 1]
        with self.assertRaisesRegex(ValueError, "INVALID_CROP"):
            board.prepare(self.root, self.spec)
        self.spec["views"][0].pop("crop")
        self.spec["views"].append(dict(self.spec["views"][0]))
        with self.assertRaisesRegex(ValueError, "DUPLICATE_VIEW"):
            board.prepare(self.root, self.spec)

    def test_sent_evidence_input_cannot_be_rewritten(self):
        jobs = read(board.prepare(self.root, self.spec))
        path = self.root / "evidence/case-1/front-input.jpg"
        digest = sha(path.read_bytes())
        save(self.root / "ledger.json", {"attempts": [{"id": jobs[0]["id"]}]})
        self.spec["views"][0]["crop"] = [0, 0, .5, .5]
        with self.assertRaisesRegex(ValueError, "ALREADY_SENT"):
            board.prepare(self.root, self.spec)
        self.assertEqual(sha(path.read_bytes()), digest)


if __name__ == "__main__":
    unittest.main()
