"""Offline tests for paid-call accounting and privacy boundaries; no paid requests."""
import concurrent.futures
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import urllib.error

from PIL import Image

import three_view as ev


class EvaluationSafetyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.key = self.root / "key.json"
        self.key.write_text(json.dumps({"ark_api_key": "test-key-never-log"}))
        self.key.chmod(0o600)
        self.image = self.root / "input.png"
        Image.new("RGB", (32, 32), "blue").save(self.image)

    def tearDown(self):
        self.temp.cleanup()

    def job(self, run_id):
        return {"id": run_id, "strategy": "grounded", "inputs": [str(self.image)],
                "category": "test", "name": "fictional"}

    def test_concurrent_reservations_cannot_exceed_500(self):
        def reserve(i):
            try:
                ev.reserve(self.root, self.job(f"test-{i}"), [], "p")
                return True
            except ValueError:
                return False
        with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
            self.assertEqual(sum(pool.map(reserve, range(510))), 500)
        data = ev.ledger(self.root)
        self.assertEqual(len(data["attempts"]), 500)
        self.assertEqual(len({a["id"] for a in data["attempts"]}), 500)

    def test_unknown_attempt_never_retried(self):
        ev.reserve(self.root, self.job("unknown"), [], "p")
        with self.assertRaisesRegex(ValueError, "ALREADY_RESERVED"):
            ev.reserve(self.root, self.job("unknown"), [], "p")
        self.assertEqual(len(ev.ledger(self.root)["attempts"]), 1)

    def test_network_timeout_is_counted_and_does_not_leak(self):
        with patch.object(ev.urllib.request, "build_opener") as build:
            build.return_value.open.side_effect = TimeoutError("secret upstream text")
            result = ev.run_one(self.root, self.job("timeout"), self.key)
            self.assertEqual(build.return_value.open.call_count, 1)
        self.assertEqual(result["status"], "FAILED")
        self.assertEqual(result["failure"], "TimeoutError")
        self.assertEqual(len(ev.ledger(self.root)["attempts"]), 1)
        all_json = "".join(p.read_text() for p in self.root.rglob("*.json") if p != self.key)
        self.assertNotIn("test-key-never-log", all_json)
        self.assertNotIn("secret upstream", all_json)

    def test_http_error_keeps_code_but_not_message(self):
        error = urllib.error.HTTPError(ev.ENDPOINT, 429, "private message", {},
                                      io.BytesIO(b'{"error":{"code":"SetLimitExceeded","message":"secret"}}'))
        with patch.object(ev.urllib.request, "build_opener") as build:
            build.return_value.open.side_effect = error
            result = ev.run_one(self.root, self.job("limited"), self.key)
        self.assertEqual(result["serviceCode"], "SetLimitExceeded")
        self.assertNotIn("secret", json.dumps(result))

    def test_quota_failure_stops_new_dispatch(self):
        path = self.root / "jobs.json"
        ev.save(path, [self.job(f"j-{i}") for i in range(10)])
        with patch.object(ev, "run_one", return_value={"status": "FAILED", "httpStatus": 429}) as run:
            ev.run_batch(self.root, path, self.key)
            self.assertLessEqual(run.call_count, 3)

    def test_insecure_credential_fails_before_reservation(self):
        self.key.chmod(0o644)
        with self.assertRaisesRegex(ValueError, "INSECURE"):
            ev.run_one(self.root, self.job("bad-key"), self.key)
        self.assertEqual(len(ev.ledger(self.root)["attempts"]), 0)

    def test_redirect_is_not_followed(self):
        self.assertIsNone(ev.NoRedirect().redirect_request(None, None, None, None, None))

    def test_normalization_strips_metadata_and_composites_alpha(self):
        source = Image.new("RGBA", (2000, 1000), (255, 0, 0, 0))
        buf = io.BytesIO()
        source.save(buf, format="PNG", pnginfo=None)
        raw, size = ev.normalized(buf.getvalue())
        self.assertEqual(size, [1536, 768])
        with Image.open(io.BytesIO(raw)) as im:
            self.assertEqual(im.getpixel((0, 0)), (255, 255, 255))
            self.assertFalse(im.getexif())

    def test_bad_png_is_a_failed_paid_attempt(self):
        class Reply:
            status = 200
            def __enter__(self): return self
            def __exit__(self, *args): pass
            def read(self, limit): return b'{"data":[{"b64_json":"aGVsbG8="}]}'
        with patch.object(ev.urllib.request, "build_opener") as build:
            build.return_value.open.return_value = Reply()
            result = ev.run_one(self.root, self.job("invalid-png"), self.key)
        self.assertEqual(result["status"], "FAILED")
        self.assertFalse((self.root / "runs/invalid-png/output.png").exists())
        self.assertEqual(len(ev.ledger(self.root)["attempts"]), 1)


if __name__ == "__main__":
    unittest.main()
