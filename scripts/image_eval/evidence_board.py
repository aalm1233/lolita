#!/usr/bin/env python3
"""Evidence-bound prototype: explicitly confirmed view crops, local unknown slots.

prepare ROOT SPEC.json writes frozen jobs; compose ROOT SPEC.json builds a
research board. This script never calls a paid API. A generated image is never
accepted as new evidence for a missing view. Result acceptance must be explicit.
"""
import argparse
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from three_view import read, save, sha

VIEWS = {"front": "正面", "side": "右侧面", "back": "背面"}


def prepare(root, spec):
    inventory = read(root / "inventory.json")
    item = next(i for i in inventory["items"] if i["itemId"] == spec["itemId"])
    refs = {str(Path(r["input"]).resolve()): r for r in item["references"] if "input" in r}
    if not spec["id"] or any(c not in "abcdefghijklmnopqrstuvwxyz0123456789-" for c in spec["id"]):
        raise ValueError("INVALID_SPEC_ID")
    if (root / "ledger.json").exists():
        sent = {a["id"] for a in read(root / "ledger.json")["attempts"]}
        if any(f'{spec["id"]}-{view}' in sent for view in VIEWS):
            raise ValueError("EVIDENCE_ALREADY_SENT_CANNOT_REWRITE")
    folder = root / "evidence" / spec["id"]
    folder.mkdir(parents=True, exist_ok=True)
    jobs, records = [], []
    if len({v["view"] for v in spec["views"]}) != len(spec["views"]):
        raise ValueError("DUPLICATE_VIEW")
    for view in spec["views"]:
        kind = view["view"]
        if kind not in VIEWS or view.get("confirmed") is not True:
            raise ValueError("VIEW_NOT_CONFIRMED")
        source = Path(view["input"]).resolve()
        ref = refs.get(str(source))
        if ref is None or ref["sourceType"] != "NO_AI_PROVENANCE":
            raise ValueError("SOURCE_NOT_ITEM_PHOTOGRAPH_EVIDENCE")
        if sha(source.read_bytes()) != ref["inputSha256"]:
            raise ValueError("CHANGED_INPUT")
        box = view.get("crop", [0, 0, 1, 1])
        if len(box) != 4 or not (0 <= box[0] < box[2] <= 1 and 0 <= box[1] < box[3] <= 1):
            raise ValueError("INVALID_CROP")
        with Image.open(source) as im:
            crop = im.crop(tuple(round(v * (im.width if j % 2 == 0 else im.height)) for j, v in enumerate(box)))
            if min(crop.size) < 32:
                raise ValueError("CROP_TOO_SMALL")
            out = folder / f"{kind}-input.jpg"
            crop.convert("RGB").save(out, quality=95)
        note = (f"已确认输入已经是{VIEWS[kind]}视角，只清理背景和非目标支撑物，严格保持原视角，不转动、不镜像。"
                + spec.get("target", "") + view.get("notes", ""))
        job_id = f'{spec["id"]}-{kind}'
        jobs.append({"id": job_id, "itemId": spec["itemId"], "category": item["category"],
                     "name": item["name"], "strategy": "extract", "inputs": [str(out)],
                     "notes": note, "size": "1024x1536", "evidenceView": kind,
                     "sourceSha256": ref["sha256"], "sourceInputSha256": ref["inputSha256"]})
        records.append({**view, "cropPath": str(out), "cropSha256": sha(out.read_bytes()), "jobId": job_id,
                        "sourceType": ref["sourceType"], "evidenceCaveat": "Explicitly annotated exported reference; absence of AI provenance alone does not prove photographic authenticity"})
    save(folder / "evidence.json", {**spec, "views": records, "unknownViews": [v for v in VIEWS if v not in [r["view"] for r in records]]})
    save(folder / "jobs.json", jobs)
    return folder / "jobs.json"


def compose(root, spec):
    folder = root / "evidence" / spec["id"]
    frozen = read(folder / "evidence.json")
    if any(c not in "abcdefghijklmnopqrstuvwxyz0123456789-" for c in frozen["id"]):
        raise ValueError("INVALID_SPEC_ID")
    views = {v["view"]: v for v in frozen["views"]}
    approval = spec.get("approvedResults", {})
    sheet = Image.new("RGB", (1536, 1024), "#eeeeec")
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.truetype("/System/Library/Fonts/STHeiti Light.ttc", 28)
    small = ImageFont.truetype("/System/Library/Fonts/STHeiti Light.ttc", 19)
    states = {}
    for index, (kind, label) in enumerate(VIEWS.items()):
        x = index * 512
        evidence = views.get(kind)
        if not evidence:
            draw.text((x + 210, 470), "未知", font=font, fill="#555555")
            states[kind] = "UNKNOWN_NO_REQUEST"
        else:
            path = Path(evidence["cropPath"])
            if sha(path.read_bytes()) != evidence["cropSha256"]:
                raise ValueError("CHANGED_CROP")
            state = "SOURCE_CROP"
            expected = approval.get(kind)
            if expected:
                output = root / "runs" / evidence["jobId"] / "output.png"
                result = read(output.parent / "result.json")
                if result["status"] != "SUCCESS" or sha(output.read_bytes()) != expected or expected != result["outputSha256"]:
                    raise ValueError("RESULT_NOT_EXPLICITLY_APPROVED")
                path, state = output, "APPROVED_AI_EXTRACTION"
            with Image.open(path) as im:
                im = im.convert("RGB")
                im.thumbnail((480, 850), Image.Resampling.LANCZOS)
                sheet.paste(im, (x + (512 - im.width) // 2, 30 + (850 - im.height) // 2))
            states[kind] = state
        draw.text((x + 200, 910), label, font=font, fill="black")
        draw.text((x + 45, 960), states[kind], font=small, fill="#555555")
    output = folder / "board.jpg"
    sheet.save(output, quality=95)
    save(folder / "board-result.json", {"states": states, "sha256": sha(output.read_bytes()),
                                         "scale": "fit per cell; no measured physical scale", "paidCallsByComposer": 0})
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["prepare", "compose"])
    parser.add_argument("root", type=Path)
    parser.add_argument("spec", type=Path)
    args = parser.parse_args()
    spec = read(args.spec)
    print(prepare(args.root, spec) if args.command == "prepare" else compose(args.root, spec))


if __name__ == "__main__":
    main()
