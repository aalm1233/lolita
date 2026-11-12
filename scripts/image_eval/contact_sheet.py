#!/usr/bin/env python3
"""Build private evaluation comparisons; originals remain unchanged."""
import argparse
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


def main():
    p = argparse.ArgumentParser()
    p.add_argument("root", type=Path)
    p.add_argument("jobs", type=Path)
    p.add_argument("--prefix", default="comparison")
    args = p.parse_args()
    jobs = json.loads(args.jobs.read_text())
    grouped = {}
    for job in jobs:
        grouped.setdefault(job.get("itemId", job["id"]), []).append(job)
    font = ImageFont.truetype("/System/Library/Fonts/STHeiti Light.ttc", 18)
    paths = []
    for index, (item, rows) in enumerate(grouped.items()):
        columns = min(len(rows) + 1, 4)
        count = (len(rows) + 1 + columns - 1) // columns
        sheet = Image.new("RGB", (600 * columns, 450 * count), "#e9e7e3")
        draw = ImageDraw.Draw(sheet)
        cells = [(Path(rows[0]["inputs"][0]), f"{item} source")]
        for job in rows:
            cells.append((args.root / "runs" / job["id"] / "output.png", job["id"]))
        for cell, (path, label) in enumerate(cells):
            x, y = cell % columns * 600, cell // columns * 450
            if path.exists():
                with Image.open(path) as im:
                    im = im.convert("RGB")
                    im.thumbnail((590, 410))
                    sheet.paste(im, (x + (600 - im.width) // 2, y + (410 - im.height) // 2))
            draw.text((x + 8, y + 418), label, font=font, fill="black")
        path = args.root / f"{args.prefix}-{item}.jpg"
        sheet.save(path, quality=94)
        paths.append(str(path))
    print(json.dumps(paths, ensure_ascii=False))


if __name__ == "__main__":
    main()
