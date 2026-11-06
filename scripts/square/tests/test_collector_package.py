"""Offline fixtures are synthetic. Integration executes the actual App Kotlin contract."""
import argparse
import copy
import csv
import hashlib
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from PIL import Image

PROJECT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(PROJECT / "skills/taobao-shop-updates/scripts"))
import square_package as package
import workflow


class CollectorPackageTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="square-collector-test-")
        self.root = Path(self.temp.name).resolve()
        self.directory = "shops/01-fixture"
        self.store = self.root / self.directory
        image = self.store / "images/123/main/001.png"
        image.parent.mkdir(parents=True)
        Image.new("RGB", (32, 48), (180, 160, 200)).save(image)
        self.image = str(image.relative_to(self.root))
        evidence = self.store / "raw/parameters.txt"; evidence.parent.mkdir()
        evidence.write_text("合成夹具：蓝色JSK，S，胸围80cm，310.40元。非真实商品。")
        part = dict(key="JSK", name="JSK", category="JSK", isAccessory=False, color="蓝色", size="S", relation="独立SKU", uncertainty=None)
        sku = dict(id="456", key="123:456", itemId="123", props={"颜色分类": "蓝色JSK", "尺码": "S"},
                   rawProperties={"颜色分类": "蓝色JSK", "尺码": "S"}, price="310.40", priceType="全款", stock="未知", content="JSK",
                   color="蓝色", size="S", form="单品", parts=[part], styleFacts=[], notSoldAlone=False,
                   photoPaths=[self.image], photoEvidenceIds=["ev-1"])
        product = dict(id="123", shopId="fixture", title="合成JSK", url="https://item.taobao.com/item.htm?id=123", capturedAt="2026-11-06",
                       pricePhase="FULL", priceType="全款", skus=[sku], info={"materials": "合成测试"},
                       images=[dict(localPath=self.image, role="main", order=0, url="https://img.alicdn.com/fixture.png")],
                       materials=[dict(partKey="JSK", detailPaths=[self.image], facts="胸围按原表", source="合成表", evidenceIds=["ev-1"],
                                       sizeTable=dict(columns=["胸围(cm)"], rows=[dict(size="S", values=["80"])], note="成衣平铺", evidenceIds=["ev-1"]))],
                       coverage=dict(skuComplete=True, detailsComplete=True, skuCount=1), evidenceIds=["ev-1"])
        event = dict(id="fixture-first", shopId="fixture", designId="fixture-design", batchId="fixture-batch", productIds=["123"], evidenceIds=["ev-1"])
        event.update({k: "" for k in package.EVENT_TEXT}); event.update(title="合成系列", kind="上新", state="已开售", captured="2026-11-06")
        self.data = dict(format="lolita.square.collector", schemaVersion=1, packageId="fixture-catalog", title="合成目录", capturedAt="2026-11-06",
                         shops=[dict(id="fixture", name="合成店", directory=self.directory, evidenceIds=["ev-1"])], events=[event], products=[product],
                         evidence={"ev-1": dict(path=str(evidence.relative_to(self.root)), quote="合成原文：蓝色JSK / S / 胸围80cm / 310.40元")}, deferred=[])
        self.input = self.root / "square-input.json"

    def tearDown(self): self.temp.cleanup()
    def write(self): package.atomic_json(self.input, self.data)
    def normalize(self): return package.normalize(copy.deepcopy(self.data), self.root, None)
    def catalog(self, path):
        with zipfile.ZipFile(path) as z: return json.loads(z.read("manifest.json"))
    def source_files(self):
        product = self.data["products"][0]
        raw = dict(itemId="123", title="合成JSK", url=product["url"], capturedAt=product["capturedAt"], status="已开售", priceType="全款",
                   skus=[dict(skuId=s["id"], props=s["rawProperties"], price=float(s["price"]), priceType=s["priceType"], stockText="未知") for s in product["skus"]])
        package.atomic_json(self.store / "products.json", {"products": [raw]})
        with (self.store / "sku-prices.csv").open("w", newline="") as out:
            w = csv.DictWriter(out, fieldnames=["itemId", "skuId", "price", "priceType"]); w.writeheader()
            for s in raw["skus"]: w.writerow(dict(itemId="123", skuId=s["skuId"], price=s["price"], priceType=s["priceType"]))
        path = self.root / self.image
        package.atomic_json(self.store / "image-manifest.json", [dict(itemId="123", role="main", order=0, localPath=str(path.relative_to(self.store)),
                          url=product["images"][0]["url"], sha256=package.sha_file(path), bytes=path.stat().st_size, width=32, height=48)])
        request = dict(date="2026-11-06", shops=[dict(directory=self.directory), dict(directory="shops/02-blocked")], appPackage=dict(input="square-input.json",
                       project=str(PROJECT), state=str(self.root / "state"), baselineSHA256=None))
        package.atomic_json(self.root / "request.json", request)
        self.data["deferred"] = [dict(shopId="requested-02", directory="shops/02-blocked", reason="合成拒绝访问")]

    def test_actual_android_accepts_zip_and_revision_replay_stale_failure_preserves_baseline(self):
        self.write(); first = self.root / "first.zip"
        report = package.build(self.input, first, PROJECT)
        self.assertTrue(report["androidContract"]["passed"])
        with zipfile.ZipFile(first) as z:
            self.assertEqual({"manifest.json", "images/" + package.sha_file(self.root / self.image) + ".png"}, set(z.namelist()))
            self.assertEqual((self.root / self.image).read_bytes(), z.read(next(n for n in z.namelist() if n.startswith("images/"))))
        state = self.root / "state"; pointer = package.configure(state, first, PROJECT)
        replay = self.root / "replay.zip"
        same = package.build(self.input, replay, PROJECT, state, pointer["sha256"])
        self.assertEqual(1, same["revision"]); self.assertEqual(dict(shops=0, events=0, products=0), same["businessChanged"])
        saved = package.current(state)
        with self.assertRaisesRegex(ValueError, "基线已变化"):
            package.build(self.input, self.root / "stale.zip", PROJECT, state, "0" * 64)
        self.assertEqual(saved, package.current(state))
        self.data["products"][0]["materials"][0]["partKey"] = "不存在的部件"; self.write()
        with self.assertRaisesRegex(ValueError, "Kotlin"):
            package.build(self.input, self.root / "bad.zip", PROJECT, state, saved["sha256"])
        self.assertEqual(saved, package.current(state)); self.assertFalse((self.root / "bad.zip").exists())

    def test_omitted_records_retained_and_new_observation_not_new_business(self):
        incoming, _, _ = self.normalize(); old, _, _ = package.merge(None, incoming, self.data)
        changed = copy.deepcopy(incoming); changed["products"][0]["capturedAt"] = "2026-11-07"
        result, revisions, business = package.merge(old, changed, self.data)
        self.assertEqual(2, result["products"][0]["revision"]); self.assertEqual(1, revisions["products"]); self.assertEqual(0, business["products"])
        next_input = copy.deepcopy(incoming)
        product = next_input["products"][0]; product["id"] = "999"; product["skus"][0].update(id="998", itemId="999", key="999:998")
        next_input["events"][0].update(id="fixture-second", batchId="second-batch", productIds=["999"])
        combined, _, _ = package.merge(result, next_input, self.data)
        self.assertEqual({"123", "999"}, {p["id"] for p in combined["products"]})

    def test_incomplete_sku_unknown_kind_and_private_data_rejected(self):
        for mutate in [lambda d: d["products"][0]["coverage"].update(skuComplete=False),
                       lambda d: d["events"][0].update(kind="猜测新品"),
                       lambda d: d["products"][0]["info"].update(userId="private")]:
            with self.subTest(mutate=mutate):
                value = copy.deepcopy(self.data); mutate(value)
                with self.assertRaises(ValueError): package.normalize(value, self.root, None)

    def test_photo_requires_evidence_and_current_product_membership(self):
        value = copy.deepcopy(self.data); value["products"][0]["skus"][0]["photoEvidenceIds"] = []
        with self.assertRaises(ValueError): package.normalize(value, self.root, None)
        value = copy.deepcopy(self.data); value["products"][0]["skus"][0]["photoPaths"] = ["other-product.png"]
        with self.assertRaises(ValueError): package.normalize(value, self.root, None)
        value = copy.deepcopy(self.data); value["products"][0]["skus"][0]["photoPaths"] = []
        _, _, warnings = package.normalize(value, self.root, None)
        self.assertEqual("independentPhoto", warnings[0]["missing"])

    def test_wrong_table_columns_and_unknown_member_spec_not_filled(self):
        value = copy.deepcopy(self.data); value["products"][0]["materials"][0]["sizeTable"]["rows"][0]["values"] = []
        with self.assertRaises(ValueError): package.normalize(value, self.root, None)
        value = copy.deepcopy(self.data); value["products"][0]["skus"][0]["parts"][0].update(color=None, size=None)
        value["products"][0]["skus"][0]["form"] = "组合"
        normalized, _, _ = package.normalize(value, self.root, None)
        self.assertIsNone(normalized["products"][0]["skus"][0]["parts"][0]["size"])
        value["products"][0]["skus"][0]["form"] = "单品"
        normalized, _, _ = package.normalize(value, self.root, None)
        self.assertEqual("S", normalized["products"][0]["skus"][0]["parts"][0]["size"])
        self.assertEqual(2, len(normalized["singleMemberSpecsFromSku"]))
        value["products"][0]["skus"][0]["size"] = None
        normalized, _, _ = package.normalize(value, self.root, None)
        self.assertIsNone(normalized["products"][0]["skus"][0]["parts"][0]["size"])

    def test_cross_link_variant_needs_explicit_same_batch_evidence(self):
        other = copy.deepcopy(self.data["products"][0]); other.update(id="999", url="https://item.taobao.com/item.htm?id=999")
        other["skus"][0].update(id="998", itemId="999", key="999:998", variantId="shared")
        self.data["products"][0]["skus"][0]["variantId"] = "shared"; self.data["products"].append(other)
        self.data["events"][0]["productIds"].append("999")
        with self.assertRaises(ValueError): self.normalize()
        for p in self.data["products"]: p["skus"][0]["variantEvidenceIds"] = ["ev-1"]
        self.normalize()
        self.data["products"][1]["skus"][0]["parts"][0]["size"] = "M"
        with self.assertRaises(ValueError): self.normalize()

    def test_bad_image_path_escape_and_symlink_rejected(self):
        with self.assertRaises(ValueError): package.local_file(self.root, "../outside.png")
        (self.root / "link.png").symlink_to(self.root / self.image)
        with self.assertRaises(ValueError): package.local_file(self.root, "link.png")
        (self.root / self.image).write_bytes(b"broken")
        with self.assertRaises((OSError, ValueError)): self.normalize()

    def test_protocol_relative_original_url_and_batch_scoped_variant_identity(self):
        self.data["products"][0]["images"][0]["url"] = "//img.alicdn.com/fixture.png"
        other = copy.deepcopy(self.data["products"][0]); other.update(id="999", url="https://item.taobao.com/item.htm?id=999")
        other["skus"][0].update(id="998", itemId="999", key="999:998", variantId="shared")
        other["skus"][0]["parts"][0]["size"] = "M"
        self.data["products"][0]["skus"][0]["variantId"] = "shared"
        self.data["products"].append(other)
        event = copy.deepcopy(self.data["events"][0]); event.update(id="second", batchId="second", productIds=["999"])
        self.data["events"].append(event)
        normalized, _, _ = self.normalize()
        self.assertEqual("//img.alicdn.com/fixture.png", normalized["products"][0]["images"][0]["url"])

    def test_source_qa_partial_shop_delivers_only_real_complete_data(self):
        self.source_files(); self.write()
        report = workflow.package(argparse.Namespace(output=str(self.root)))
        self.assertTrue(report["passed"]); self.assertEqual(1, report["counts"]["shops"])
        qa = package.read_json(self.root / "verification.json")
        self.assertEqual(["shops/02-blocked"], qa["deferredStores"])

    def test_source_sku_or_price_disagreement_stops_before_publication(self):
        self.source_files()
        for mode in ("price", "sku", "title", "capturedAt", "priceType"):
            value = copy.deepcopy(self.data)
            if mode == "price": value["products"][0]["skus"][0]["price"] = "999"
            elif mode == "sku": value["products"][0]["skus"][0]["id"] = "999"
            else: value["products"][0][mode] = "incorrect source metadata"
            package.atomic_json(self.input, value)
            with self.assertRaises(ValueError): workflow.package(argparse.Namespace(output=str(self.root)))
        self.assertFalse((self.root / "state/current.json").exists())
        self.assertFalse((self.root / "app-package").exists())

    def test_explicit_deferred_placeholder_cannot_block_or_enter_good_product_zip(self):
        self.source_files()
        raw = package.read_json(self.store / "products.json")
        raw["products"].append(dict(itemId="999", title="合成收藏跳转", url="https://item.taobao.com/item.htm?id=999", capturedAt="2026-11-06", priceType="占位价",
                                    skus=[dict(skuId="0", props={}, price=9999, priceType="占位价")]))
        package.atomic_json(self.store / "products.json", raw)
        with (self.store / "sku-prices.csv").open("a") as out: out.write("999,0,9999,占位价\n")
        self.data["deferred"].append(dict(shopId="fixture", itemId="999", reason="收藏跳转占位，未取得可售SKU")); self.write()
        report = workflow.package(argparse.Namespace(output=str(self.root)))
        self.assertTrue(report["passed"]); self.assertEqual(1, report["counts"]["products"])
        final = self.catalog(self.root / "app-package/lolita-square-2026-11-06.zip")
        self.assertEqual(["123"], [p["id"] for p in final["products"]])

    def test_long_run_status_retains_session_without_private_payload(self):
        events = self.root / "dsh-events.jsonl"
        events.write_text(json.dumps(dict(type="session_started", sessionId="session-fixture")) + "\n" +
                          json.dumps(dict(type="thinking", text="PRIVATE-CONTENT" * 14000)) + "\n" +
                          json.dumps(dict(type="tool_call")) + "\n")
        package.atomic_json(self.root / "launch.json", dict(events=events.name, status="running"))
        status = workflow.status(argparse.Namespace(output=str(self.root)))
        self.assertEqual("session-fixture", status["sessionId"])
        self.assertNotIn("PRIVATE-CONTENT", json.dumps(status))

    def test_inspected_candidate_cannot_disappear_without_deferred_reason(self):
        self.source_files()
        candidate = self.store / "raw/items/999.json"; candidate.parent.mkdir()
        package.atomic_json(candidate, {"title": "合成未确认候选"}); self.write()
        with self.assertRaisesRegex(ValueError, "候选商品"):
            workflow.package(argparse.Namespace(output=str(self.root)))
        self.assertFalse((self.root / "state/current.json").exists())

    def test_price_explanation_does_not_expose_internal_phase_code(self):
        self.data["products"][0]["priceType"] = "DEPOSIT"
        with self.assertRaisesRegex(ValueError, "priceType"):
            self.normalize()


if __name__ == "__main__": unittest.main()
