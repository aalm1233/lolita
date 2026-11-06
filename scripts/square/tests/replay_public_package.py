#!/usr/bin/env python3
"""Replay a previously reviewed public ZIP offline. Not a new capture or production-state update."""
import argparse
import copy
import json
import sys
import zipfile
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(PROJECT / "skills/taobao-shop-updates/scripts"))
import square_package as package


def replay(source, output):
    original = package.baseline(source)
    root = Path(output).resolve(); root.mkdir(parents=True, exist_ok=False)
    with zipfile.ZipFile(source) as archive:
        # baseline() bounded and checked all entry names and original SHA before extraction.
        archive.extractall(root)
    (root / "manifest.json").rename(root / "reviewed-manifest.json")
    data = copy.deepcopy(original)
    for key in ("assets", "revision"): data.pop(key)
    data["format"] = "lolita.square.collector"; data["evidence"] = {}; data["deferred"] = []
    def proof(key, quote):
        data["evidence"][key] = {"path": "reviewed-manifest.json", "quote": quote[:2000]}
        return [key]
    for index, shop in enumerate(data["shops"]):
        shop["directory"] = "reviewed-public-package"
        shop["evidenceIds"] = proof("shop-" + str(index), json.dumps(original["shops"][index], ensure_ascii=False))
    for index, event in enumerate(data["events"]):
        event["evidenceIds"] = proof("event-" + str(index), json.dumps(original["events"][index], ensure_ascii=False))
    for index, product in enumerate(data["products"]):
        evidence = proof("product-" + str(index), "归档清单products[" + str(index) + "]；" + product["title"] + "；此为原已核对公开资料的离线回放，不是新采集。")
        product["evidenceIds"] = evidence
        product["coverage"] = {"skuComplete": True, "detailsComplete": True, "skuCount": len(product["skus"])}
        for image in product["images"]: image["localPath"] = image.pop("path")
        for sku in product["skus"]:
            if sku["price"] is not None: sku["price"] = str(sku["price"])
            sku["photoEvidenceIds"] = evidence; sku["variantEvidenceIds"] = evidence
            for style in sku["styleFacts"]: style["evidenceIds"] = evidence
        for material in product.get("materials", []):
            material["evidenceIds"] = evidence
            if material.get("sizeTable"): material["sizeTable"]["evidenceIds"] = evidence
    input_path = root / "square-input.json"; package.atomic_json(input_path, data)
    target = root / "replayed-square.zip"
    report = package.build(input_path, target, PROJECT)
    with zipfile.ZipFile(source) as before, zipfile.ZipFile(target) as after:
        for asset in original["assets"]:
            if before.read(asset["path"]) != after.read(asset["path"]):
                raise AssertionError("Original image bytes changed")
    report.update(replayOnly=True, freshTaobaoCapture=False, productionStateUpdated=False,
                  originalImagesByteIdentical=len(original["assets"]),
                  sizeTables=sum(bool(m.get("sizeTable")) for p in data["products"] for m in p.get("materials", [])))
    package.atomic_json(root / "replay-verification.json", report)
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source"); parser.add_argument("output")
    args = parser.parse_args(); replay(args.source, args.output)
