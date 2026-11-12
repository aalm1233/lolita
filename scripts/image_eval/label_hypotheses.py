#!/usr/bin/env python3
"""Append a local provenance footer to speculative previews; keep raw PNGs intact."""
import argparse
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from three_view import ledger, read, save, sha


def main():
    p = argparse.ArgumentParser()
    p.add_argument("root", type=Path)
    args = p.parse_args()
    written = 0
    for run in ledger(args.root)["attempts"]:
        if run["strategy"] not in ("infer-labeled", "infer-photoreal") or run["status"] != "SUCCESS":
            continue
        folder = args.root / "runs" / run["id"]
        raw = folder / "output.png"
        result = read(folder / "result.json")
        if sha(raw.read_bytes()) != result["outputSha256"]:
            raise ValueError("CHANGED_RAW_OUTPUT")
        with Image.open(raw) as im:
            im = im.convert("RGBA")
            preview = Image.new("RGBA", (im.width, im.height + 96), "#eee9e2")
            preview.paste(im, (0, 0))
            font = ImageFont.truetype("/System/Library/Fonts/STHeiti Light.ttc", 25)
            ImageDraw.Draw(preview).text((24, im.height + 30), "AI推测研究图 · 未见侧背为推测，不作真实商品资料", font=font, fill="black")
            out = folder / "preview-labeled.png"
            preview.save(out)
        save(folder / "preview-provenance.json", {"rawSha256": result["outputSha256"],
                                                 "previewSha256": sha(out.read_bytes()), "paidCalls": 0,
                                                 "scope": "Provenance footer only, original output file retained"})
        written += 1
    print(f"Labeled {written} speculative previews locally; no paid requests.")


if __name__ == "__main__":
    main()
