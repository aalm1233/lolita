#!/usr/bin/env python3
"""Private, offline HTML and integrity report. Does not upload photos."""
import argparse
import collections
import html
import json
from pathlib import Path
import time
from three_view import read, save, sha, ledger


def main():
    p = argparse.ArgumentParser()
    p.add_argument("root", type=Path)
    args = p.parse_args()
    root = args.root.resolve()
    attempts = ledger(root)["attempts"]
    inventory = read(root / "inventory.json")
    reviews = read(root / "reviews.json") if (root / "reviews.json").exists() else {}
    failures, verified, count = [], [], 0
    cards = []
    for run in attempts:
        folder = root / "runs" / run["id"]
        result = {}
        manifest = read(folder / "manifest.json")
        for path, expected in zip(manifest["inputs"], manifest["inputSha256"]):
            if sha(Path(path).read_bytes()) != expected:
                failures.append({"id": run["id"], "failure": "INPUT_CHANGED"})
        if sha((folder / "prompt.txt").read_bytes()) != manifest["promptSha256"]:
            failures.append({"id": run["id"], "failure": "PROMPT_CHANGED"})
        if run["status"] == "SUCCESS":
            result = read(folder / "result.json")
            if sha((folder / "output.png").read_bytes()) != result["outputSha256"]:
                failures.append({"id": run["id"], "failure": "OUTPUT_CHANGED"})
            else:
                verified.append(run["id"])
            count += result.get("outputCount", 0)
        review = reviews.get(run["id"])
        def picture(path):
            path = Path(path)
            relative = path.resolve().relative_to(root)
            return f'<a href="{html.escape(str(relative), quote=True)}" target="_blank"><img loading="lazy" src="{html.escape(str(relative), quote=True)}"></a>'
        inputs = "".join(picture(path) for path in manifest["inputs"])
        preview = folder / "preview-labeled.png"
        if preview.exists():
            provenance = read(folder / "preview-provenance.json")
            if (sha(preview.read_bytes()) != provenance["previewSha256"] or
                    provenance["rawSha256"] != result.get("outputSha256")):
                failures.append({"id": run["id"], "failure": "PREVIEW_CHANGED"})
        output = picture(preview if preview.exists() else folder / "output.png") if (folder / "output.png").exists() else html.escape(run["status"])
        text = json.dumps(review, ensure_ascii=False, indent=2) if review else "尚未主助手视觉核对"
        cards.append(f'<article data-strategy="{html.escape(run["strategy"])}"><h2>{html.escape(run["id"])}</h2>'
                     f'<div class="inputs">{inputs}</div><div class="output">{output}</div><pre>{html.escape(text)}</pre></article>')
    baseline = inventory["baseline"]
    backup_ok = sha(Path(baseline["backupPath"]).read_bytes()) == baseline["sha256"]
    original_ok = all(sha((root / "originals" / Path(row["member"]).name).read_bytes()) == row["sha256"] for row in inventory["images"])
    boards = []
    for path in sorted((root / "evidence").glob("*/board-result.json")):
        board = read(path)
        image_path = path.with_name("board.jpg")
        if sha(image_path.read_bytes()) != board["sha256"]:
            failures.append({"board": path.parent.name, "failure": "BOARD_CHANGED"})
        boards.append({"id": path.parent.name, "states": board["states"]})
    summary = {"createdAt": time.time(), "limit": 500, "attempts": len(attempts), "remaining": 500 - len(attempts),
               "statuses": dict(collections.Counter(a["status"] for a in attempts)), "returnedImages": count,
               "assistantReviewed": len(set(reviews) & {a["id"] for a in attempts}),
               "strategies": dict(collections.Counter(a["strategy"] for a in attempts)),
               "distinctItems": len({a["itemId"] for a in attempts}), "verifiedOutputs": len(verified),
               "integrityFailures": failures, "backupUnchanged": backup_ok, "originalsUnchanged": original_ok,
               "inventoryImages": len(inventory["images"]), "inventoryItems": len(inventory["items"]),
               "imageIODecoderFallbacks": sum(i.get("decoder") == "macOS ImageIO" for i in inventory["images"]),
               "evidenceBoards": boards}
    save(root / "summary.json", summary)
    strategies = sorted({a["strategy"] for a in attempts})
    opts = '<option value="">全部路线</option>' + "".join(f'<option>{html.escape(s)}</option>' for s in strategies)
    page = '<!doctype html><meta charset="utf-8"><title>三视图私有评估</title><style>[hidden]{display:none!important}body{font:16px system-ui;background:#eee9e2;margin:24px;color:#222}header{position:sticky;top:0;background:#eee9e2;padding:12px;z-index:1}article{padding:18px;background:white;border-radius:12px;margin:20px 0;display:grid;grid-template-columns:1fr 2fr;gap:16px}h2,pre{grid-column:1/-1}img{max-width:100%;max-height:700px;object-fit:contain}.inputs img{max-height:320px}.inputs{display:flex;flex-wrap:wrap;gap:8px}.inputs a{max-width:48%}pre{white-space:pre-wrap;background:#f6f5f2;padding:12px}select{font:inherit;padding:8px}</style>'
    page += '<header><h1>商品提取与三视图 · 私有评估</h1><p>点击原图或结果可放大。未知视图不是重建成功；AI派生图不是新的照片证据。全部资料仅本机保留。</p>'
    page += f'<p>已调用{len(attempts)}/500，返回{count}图，逐图核对{summary["assistantReviewed"]}次；基准SHA复核：{backup_ok}</p><select id="filter">{opts}</select></header>'
    page += '<section><h2>有证据的视角 · 缺失视角固定未知</h2><p>下列仅为概览级提取；纹理与细绳仍有重绘，未确认的结果保留原裁切。无真实尺寸或等物理比例保证。</p>'
    page += " ".join(f'<a href="evidence/{html.escape(b["id"])}/board.jpg" target="_blank">{html.escape(b["id"])}</a>' for b in boards) + '</section>'
    page += "".join(cards) + '<script>document.querySelector("select").onchange=e=>document.querySelectorAll("article").forEach(a=>a.hidden=!!e.target.value&&a.dataset.strategy!==e.target.value)</script>'
    (root / "index.html").write_text(page)
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == "__main__":
    main()
