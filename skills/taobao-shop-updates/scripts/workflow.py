#!/usr/bin/env python3
"""Local dsh orchestration and offline asset QA. No browser or network access."""
import argparse
import csv
import hashlib
import json
import math
import re
import shutil
import subprocess
import sys
from collections import Counter
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path
from urllib.parse import parse_qs, urlparse
from zoneinfo import ZoneInfo

SKILL = Path(__file__).resolve().parents[1]
DEFAULT_PROJECT = Path.home() / "code/lolita"


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def safe_name(value):
    return re.sub(r"[^\w\-]+", "-", value, flags=re.UNICODE).strip("-_")[:64] or "shop"


def inside(base, relative):
    path = (base / relative).resolve()
    if not path.is_relative_to(base.resolve()):
        raise ValueError("路径超出店铺目录")
    return path


def prepare(args):
    from square_package import current, baseline, DEFAULT_STATE, state_lock
    datetime.strptime(args.date, "%Y-%m-%d")
    names = list(dict.fromkeys(name.strip() for name in args.shop if name.strip()))
    if not names:
        raise ValueError("至少提供一个店名")
    stamp = datetime.now(ZoneInfo("Asia/Shanghai")).strftime("%H%M%S-%f")
    root = (Path(args.output).expanduser() if args.output else
            Path.home() / "Downloads/taobao-shop-updates" / args.date /
            ("-".join(safe_name(n) for n in names)[:100] + "-" + stamp)).resolve()
    root.mkdir(parents=True, exist_ok=False)
    project = Path(args.project).expanduser().resolve()
    if not (project / "app/src/main/java/com/lolita/app/data/square/SquarePackage.kt").is_file():
        raise ValueError("请通过 --project 指定当前Lolita工程")
    state = Path(args.state).expanduser().resolve()
    with state_lock(state):
        pointer = current(state)
        catalog = baseline(pointer["package"]) if pointer else None
    request = {"schemaVersion": 2, "date": args.date, "timezone": "Asia/Shanghai", "shops": [],
               "appPackage": {"format": "lolita.square", "schemaVersion": 1, "collectorFormat": "lolita.square.collector",
                              "project": str(project), "state": str(state), "baselineSHA256": pointer["sha256"] if pointer else None,
                              "packageId": catalog["packageId"] if catalog else "taobao-shop-catalog",
                              "input": "square-input.json", "context": "square-context.json"}}
    context = {"shops": (catalog or {}).get("shops", []), "events": (catalog or {}).get("events", []), "products": [],
               "baselinePackage": pointer["package"] if pointer else None, "freshCapture": False}
    if catalog:
        identity_dir = root / "catalog-context/products"; identity_dir.mkdir(parents=True)
        for product in catalog["products"]:
            identity = "catalog-context/products/" + product["id"] + ".json"
            write_json(root / identity, product)
            context["products"].append({**{k: product[k] for k in ("id", "shopId", "title", "url", "revision", "pricePhase")},
                                        "listing": product.get("listing"), "partKeys": sorted({p["key"] for sku in product["skus"] for p in sku["parts"]}), "identityFile": identity})
    write_json(root / "square-context.json", context)
    for i, name in enumerate(names, 1):
        directory = "shops/" + f"{i:02d}-" + safe_name(name)
        (root / directory).mkdir(parents=True)
        request["shops"].append({"name": name, "directory": directory})
    write_json(root / "request.json", request)
    template = (SKILL / "references/collector-task.md").read_text()
    # One substitution pass: names containing template markers stay data.
    values = {"REQUEST_JSON": json.dumps(request, ensure_ascii=False, indent=2),
              "OUTPUT_DIR": str(root), "SKILL_DIR": str(SKILL)}
    prompt = re.sub(r"\{\{(REQUEST_JSON|OUTPUT_DIR|SKILL_DIR)\}\}",
                    lambda m: values[m[1]], template)
    (root / "collector-task.txt").write_text(prompt, encoding="utf-8")
    return {"output": str(root), "shops": request["shops"], "status": "prepared"}


def source_qa(root, request, data):
    # Only explicitly successful shops enter the package; a blocked shop never masquerades as empty success.
    directories = {s["directory"] for s in data["shops"]}
    requested = {s["directory"] for s in request["shops"]}
    if not directories or not directories <= requested:
        raise ValueError("App输入店铺目录不对应本次请求")
    qa = {"passed": True, "scope": "offline-source-and-app-input", "stores": [], "deferredStores": []}
    for directory in sorted(requested):
        if directory not in directories:
            if not any(row.get("directory") == directory and row.get("reason") for row in data["deferred"]):
                raise ValueError("未完成店铺必须列在deferred中并写明目录/原因")
            qa["deferredStores"].append(directory); continue
        store = inside(root, directory)
        shop = next(s for s in data["shops"] if s["directory"] == directory)
        selected = {p["id"]: p for p in data["products"] if p["shopId"] == shop["id"]}
        if not selected:
            # An unavailable page can update status against a validated baseline without inventing fresh SKUs/images.
            if not data.get("listingUpdates") and not data.get("eventUpdates"):
                raise ValueError("店铺既无完整商品也无明确状态/动态资料修正")
            qa.setdefault("metadataOnlyStores" if data.get("eventUpdates") and not data.get("listingUpdates") else "statusOnlyStores", []).append(directory)
            continue
        original = {p["itemId"]: p for p in read_json(store / "products.json")["products"]}
        deferred = {p.get("itemId") for p in data["deferred"] if p["shopId"] == shop["id"]}
        if not set(selected) <= set(original) or set(original) - set(selected) - deferred:
            raise ValueError("App商品与原始清单不一致；未采用商品必须明确列待核对")
        inspected = {match[1] for path in (store / "raw/items").glob("*.json")
                     if (match := re.fullmatch(r"([0-9]+)(?:-ssr)?\.json", path.name))}
        if inspected - set(selected) - deferred:
            raise ValueError("已检查但未采用的候选商品须在deferred逐件说明，不能静默漏项")
        result = verify(argparse.Namespace(output=str(store), report=None, item_ids=set(selected)))
        qa["stores"].extend(result["stores"]); qa["passed"] &= result["passed"]
        if not result["passed"]: continue
        manifest = read_json(store / "image-manifest.json")
        for item_id, product in selected.items():
            source = original[item_id]
            if any(product.get(key) != source.get(key) for key in ("title", "capturedAt", "priceType")):
                raise ValueError("App商品标题/采集时间/价格阶段说明与原始清单不一致")
            source_skus = {s["skuId"]: s for s in source["skus"]}
            if {s["id"] for s in product["skus"]} != set(source_skus):
                raise ValueError("App输入未保留原始完整SKU集合")
            for sku in product["skus"]:
                raw = source_skus[sku["id"]]
                if sku["rawProperties"] != raw["props"] or money(sku["price"]) != money(raw["price"]) or sku["priceType"] != raw.get("priceType", source["priceType"]):
                    raise ValueError("App规格原文或报价与原始采集不一致")
            expected = {(directory + "/" + i["localPath"], i["role"], i["order"], i["url"]) for i in manifest if i["itemId"] == item_id}
            actual = {(i["localPath"], i["role"], i["order"], i["url"]) for i in product["images"]}
            if actual != expected:
                raise ValueError("App输入未保留全部原图归属/角色/顺序/来源")
    qa["scope"] = "offline-selected-complete-sources-and-app-input; deferred records are not claimed complete"
    write_json(root / "verification.json", qa)
    if not qa["passed"]:
        raise ValueError("原始采集文件核验未通过，请先修复verification.json中的实际失败")
    return qa


def package(args):
    from square_package import build
    root = Path(args.output).expanduser().resolve()
    request = read_json(root / "request.json")
    config = request.get("appPackage")
    if not config:
        raise ValueError("旧请求没有App契约，请重新prepare；保留已采集文件，不重新联网")
    source_qa(root, request, read_json(root / config["input"]))
    output = root / "app-package" / ("lolita-square-" + request["date"] + ".zip")
    return build(root / config["input"], output, config["project"], config["state"], config["baselineSHA256"])


def find_dsh(explicit):
    if explicit:
        path = Path(explicit).expanduser().resolve()
        if not path.is_file():
            raise ValueError("指定的 dsh 入口不存在")
        return str(path)
    installed = shutil.which("dsh")
    if installed:
        return installed
    candidates = list(Path.home().glob(".npm/_npx/*/node_modules/.bin/dsh"))
    if not candidates:
        raise ValueError("未找到已安装 dsh；停止委派，不自动安装或接管浏览器")
    return str(max(candidates, key=lambda p: p.stat().st_mtime))


def run(args):
    root = Path(args.output).expanduser().resolve()
    read_json(root / "request.json")
    if args.task_file and not args.session_id:
        raise ValueError("补充任务文件必须配合既有 --session-id 使用")
    prompt = inside(root, args.task_file) if args.task_file else root / "collector-task.txt"
    if not prompt.is_file():
        raise ValueError("缺少 collector-task.txt")
    cli = find_dsh(args.dsh)
    node = shutil.which("node")
    if not node:
        raise ValueError("未找到 node；不自动安装")
    command = [node, cli, "--profile", "headless"]
    if args.patch:
        patch = Path(args.patch).expanduser().resolve()
        if not patch.is_file():
            raise ValueError("指定的既有配置覆盖不存在")
        command += ["--patch", str(patch)]
    command += ["--json"]
    if args.session_id:
        command += ["--session-id", args.session_id]
    command += ["-"]
    stamp = datetime.now(ZoneInfo("Asia/Shanghai")).strftime("%Y%m%d-%H%M%S-%f")
    events = root / f"dsh-{stamp}.jsonl"
    diagnostics = root / f"dsh-{stamp}.stderr.log"
    launch = {"startedAt": stamp, "events": events.name, "diagnostics": diagnostics.name,
              "sessionId": args.session_id, "status": "running"}
    # Foreground child: callers can yield their exec session and poll locally.
    with prompt.open("rb") as source, events.open("xb") as out, diagnostics.open("xb") as err:
        child = subprocess.Popen(command, stdin=source, stdout=out, stderr=err, cwd=root)
        launch["pid"] = child.pid
        write_json(root / "launch.json", launch)
        print(json.dumps(launch, ensure_ascii=False), flush=True)
        try:
            code = child.wait()
        except KeyboardInterrupt:
            child.terminate()
            try:
                child.wait(timeout=10)
            except subprocess.TimeoutExpired:
                child.kill()
                child.wait()
            launch["status"] = "interrupted"
            write_json(root / "launch.json", launch)
            raise
    launch.update(status="exited", exitCode=code, finishedAt=datetime.now(ZoneInfo("Asia/Shanghai")).isoformat())
    write_json(root / "launch.json", launch)
    return launch


def status(args):
    root = Path(args.output).expanduser().resolve()
    launch = read_json(root / "launch.json") if (root / "launch.json").exists() else {"status": "prepared"}
    result = {"launch": launch}
    # Only event metadata; never print thinking, account objects or tool payloads.
    if launch.get("events"):
        event_path = inside(root, launch["events"])
        if event_path.exists():
            with event_path.open("rb") as source:
                head = source.read(65536).decode("utf-8", errors="replace").splitlines()
                source.seek(max(0, event_path.stat().st_size - 131072))
                tail = source.read().decode("utf-8", errors="replace").splitlines()
            lines = head + tail
            types = []
            for line in lines:
                try:
                    event = json.loads(line)
                except ValueError:
                    continue
                if not isinstance(event, dict):
                    continue
                session_id = event.get("sessionId")
                if isinstance(session_id, str) and re.fullmatch(r"[\w-]{1,160}", session_id):
                    result["sessionId"] = session_id
                kind = event.get("type")
                if isinstance(kind, str) and re.fullmatch(r"[\w.:-]{1,80}", kind):
                    types.append(kind)
            result["recentEventTypes"] = types[-8:]
    result["localFileCounts"] = []
    request_file = root / "request.json"
    if request_file.exists():
        for shop in read_json(request_file).get("shops", []):
            store = inside(root, shop["directory"])
            result["localFileCounts"].append({"directory": shop["directory"],
                "capturedItemFiles": len([p for p in (store / "raw/items").glob("*.json") if p.stem.isdigit()]),
                "originalImageFiles": len([p for p in (store / "images").rglob("*") if p.is_file() and p.suffix.lower() in {".png", ".jpg", ".jpeg", ".webp"}])})
    for name in ("progress.txt", "DONE.json", "cleanup.json"):
        path = root / name
        if path.exists():
            result[name] = path.read_text(encoding="utf-8")[-2400:]
    return result


def money(value):
    if value is None or value == "":
        return None
    number = Decimal(str(value))
    if not number.is_finite() or number < 0:
        raise ValueError("金额不是非负有限数")
    return number


def verify(args):
    try:
        from PIL import Image
    except ImportError:
        raise ValueError("缺少 Pillow；使用 uv run --with pillow python workflow.py verify ...")
    root = Path(args.output).expanduser().resolve()
    if (root / "request.json").exists():
        stores = [inside(root, shop["directory"]) for shop in read_json(root / "request.json")["shops"]]
    elif (root / "products.json").exists():
        stores = [root]
    else:
        stores = sorted(p for p in root.iterdir() if p.is_dir() and (p / "products.json").exists())
    report = {"passed": True, "scope": "offline-file-integrity-only", "stores": [], "errors": []}
    if not stores:
        report["errors"].append("未找到店铺商品文件")
    for store in stores:
        errors = []
        counts = {"directory": str(store), "products": 0, "skus": 0, "imageFiles": 0, "distinctSHA256": 0}
        try:
            data = read_json(store / "products.json")
            products = data["products"]
            selected_ids = getattr(args, "item_ids", None)
            if selected_ids is not None:
                products = [p for p in products if p.get("itemId") in selected_ids]
            if not isinstance(products, list) or not products:
                raise ValueError("products 必须为非空数组；阻塞不代表采集完整")
            expected, ids = {}, []
            for product in products:
                item_id = product["itemId"]
                if not isinstance(item_id, str) or not re.fullmatch(r"[0-9]+", item_id):
                    errors.append("商品ID必须为完整数字字符串")
                ids.append(item_id)
                url = urlparse(product["url"])
                host = url.hostname or ""
                if not any(host == d or host.endswith("." + d) for d in ("taobao.com", "tmall.com")):
                    errors.append(f"商品 {item_id} URL域名不正确")
                if parse_qs(url.query).get("id") != [item_id]:
                    errors.append(f"商品 {item_id} URL编号不一致")
                for sku in product["skus"]:
                    sku_id = sku["skuId"]
                    if not isinstance(sku_id, str) or not re.fullmatch(r"[0-9]+", sku_id) or sku_id == "0":
                        errors.append(f"商品 {item_id} SKU编号异常或为起价摘要")
                    key = (item_id, sku_id)
                    if key in expected:
                        errors.append(f"重复SKU: {item_id}/{sku_id}")
                    price = sku.get("price")
                    if price is not None and (isinstance(price, bool) or not isinstance(price, (int, float)) or not math.isfinite(price)):
                        errors.append(f"SKU {sku_id} 的 JSON 金额必须是数值或null")
                    expected[key] = (money(price), sku.get("priceType", product.get("priceType")))
            if len(ids) != len(set(ids)):
                errors.append("重复商品ID")
            with (store / "sku-prices.csv").open(encoding="utf-8-sig", newline="") as source:
                reader = csv.DictReader(source)
                if not {"itemId", "skuId", "price", "priceType"}.issubset(reader.fieldnames or []):
                    raise ValueError("CSV缺少必要列")
                rows = list(reader)
                if selected_ids is not None:
                    rows = [r for r in rows if r.get("itemId") in selected_ids]
            keys = [(r["itemId"], r["skuId"]) for r in rows]
            if Counter(keys) != Counter(expected.keys()):
                errors.append("CSV与products的SKU集合或重复数量不一致")
            for row, key in zip(rows, keys):
                if key in expected and (money(row["price"]), row["priceType"]) != expected[key]:
                    errors.append(f"CSV报价阶段/金额不一致: {key[0]}/{key[1]}")
            counts.update(products=len(products), skus=len(expected))
            manifest = read_json(store / "image-manifest.json")
            if selected_ids is not None:
                manifest = [i for i in manifest if i.get("itemId") in selected_ids]
            if not isinstance(manifest, list) or not manifest:
                raise ValueError("image-manifest必须为非空数组")
            seen_paths, hashes = set(), set()
            for entry in manifest:
                label = entry.get("localPath", "缺少路径")
                try:
                    if entry.get("itemId") not in ids or entry.get("role") not in {"main", "sku", "detail", "promo"} or type(entry.get("order")) is not int or entry["order"] < 0:
                        raise ValueError("图片商品归属/角色/顺序无效")
                    image_source = entry.get("url", "")
                    image_url = urlparse("https:" + image_source if image_source.startswith("//") else image_source)
                    if image_url.scheme not in {"https", "http"} or not image_url.hostname or image_url.username or image_url.password:
                        raise ValueError("图片缺少公开来源URL")
                    path = inside(store, entry["localPath"])
                    if path in seen_paths:
                        raise ValueError("manifest重复文件路径")
                    seen_paths.add(path)
                    if not path.is_file():
                        raise ValueError("图片文件不存在")
                    digest = hashlib.sha256(path.read_bytes()).hexdigest()
                    if digest != entry["sha256"]:
                        raise ValueError("SHA256不一致")
                    if path.stat().st_size != entry["bytes"]:
                        raise ValueError("字节数不一致")
                    with Image.open(path) as img:
                        if img.size != (entry["width"], entry["height"]):
                            raise ValueError("尺寸不一致")
                        img.load()
                    hashes.add(digest)
                except (OSError, ValueError, KeyError, TypeError) as exc:
                    errors.append(f"{label}: {exc}")
            counts.update(imageFiles=len(seen_paths), distinctSHA256=len(hashes))
        except (OSError, ValueError, KeyError, TypeError, InvalidOperation) as exc:
            errors.append(str(exc))
        counts["errors"] = errors
        report["stores"].append(counts)
    report["passed"] = not report["errors"] and all(not s["errors"] for s in report["stores"])
    if args.report:
        write_json(Path(args.report).expanduser(), report)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subs = parser.add_subparsers(dest="action", required=True)
    p = subs.add_parser("prepare")
    p.add_argument("--shop", action="append", required=True)
    p.add_argument("--date", required=True)
    p.add_argument("--output")
    p.add_argument("--project", default=str(DEFAULT_PROJECT))
    p.add_argument("--state", default=str(Path.home() / "Downloads/taobao-shop-updates/catalog-state"))
    p = subs.add_parser("run")
    p.add_argument("--output", required=True)
    p.add_argument("--dsh")
    p.add_argument("--session-id")
    p.add_argument("--task-file", help="接续会话时使用本次输出目录内的补充指令文件")
    p.add_argument("--patch", help="仅指定已核实获得用户授权的既有配置覆盖")
    p = subs.add_parser("status")
    p.add_argument("--output", required=True)
    p = subs.add_parser("verify")
    p.add_argument("--output", required=True)
    p.add_argument("--report")
    p = subs.add_parser("package", help="只读本地采集结果，验收并生成App可导入ZIP")
    p.add_argument("--output", required=True)
    args = parser.parse_args()
    try:
        result = globals()[args.action](args)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        if args.action == "verify" and not result["passed"]:
            return 1
        if args.action == "run":
            return result.get("exitCode", 0)
        return 0
    except (OSError, ValueError, KeyError) as exc:
        print(json.dumps({"error": str(exc)}, ensure_ascii=False), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
