#!/usr/bin/env python3
"""Offline collector input -> App v1 ZIP. Images and revisions are computed, never guessed."""
import argparse
import copy
import fcntl
import hashlib
import json
import os
import re
import tempfile
import zipfile
from contextlib import contextmanager
from decimal import Decimal, InvalidOperation
from datetime import datetime
from pathlib import Path
from urllib.parse import parse_qs, urlparse
from zoneinfo import ZoneInfo

from android_contract import check as android_check

MAX_ZIP = 600 * 1024 * 1024
MAX_IMAGE = 20 * 1024 * 1024
MAX_JSON = 8 * 1024 * 1024
DEFAULT_STATE = Path.home() / "Downloads/taobao-shop-updates/catalog-state"
IDENTIFIER = re.compile(r"[A-Za-z0-9_-]{1,100}\Z")
SHA_PATH = re.compile(r"images/([a-f0-9]{64})\.(jpg|jpeg|png|webp)\Z")
INFO_KEYS = {"care", "components", "craft", "excluded", "gifts", "included", "materials", "season", "sizes", "source", "styles"}
PRIVATE_KEYS = {"cookie", "cookies", "authorization", "accesstoken", "refreshtoken", "password", "buyer", "buyerid",
                "buyernick", "userid", "addressid", "address", "phone", "mobile", "email", "收货地址", "买家", "手机号"}
EVENT_TEXT = ("title", "subtitle", "kind", "state", "date", "price", "priceNote", "note", "material", "detail", "captured")
PRODUCT_FIELDS = {"id", "revision", "shopId", "title", "url", "capturedAt", "pricePhase", "priceType", "skus", "info", "images", "materials", "evidenceIds", "coverage"}
SKU_FIELDS = {"id", "key", "itemId", "props", "rawProperties", "price", "priceType", "stock", "content", "color", "size", "form", "parts", "styleFacts", "notSoldAlone", "variantId", "photoPaths", "photoEvidenceIds", "variantEvidenceIds"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_json(path):
    def unique(pairs):
        out = {}
        for key, value in pairs:
            require(key not in out, "JSON包含重复字段")
            out[key] = value
        return out
    require(Path(path).stat().st_size <= 32 * 1024 * 1024, "输入JSON超过32MiB")
    return json.loads(Path(path).read_text(encoding="utf-8-sig"), object_pairs_hook=unique,
                      parse_constant=lambda _: (_ for _ in ()).throw(ValueError("JSON含非有限数字")))


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False) + "\n").encode()


def sha_file(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as source:
        for chunk in iter(lambda: source.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def atomic_json(path, value):
    path = Path(path); path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix="." + path.name, dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as out:
            out.write(encoded(value)); out.flush(); os.fsync(out.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary): os.unlink(temporary)


def local_file(root, relative):
    require(isinstance(relative, str) and relative and "\\" not in relative, "本地引用须为非空相对路径")
    path = Path(relative)
    require(not path.is_absolute() and ".." not in path.parts, "本地引用不得越出本次采集目录")
    root = Path(root).resolve(); candidate = root
    for part in path.parts:
        candidate = candidate / part
        require(not candidate.is_symlink(), "不接受符号链接资料")
    require(candidate.resolve().is_relative_to(root) and candidate.is_file(), "本地引用缺失或越界")
    return candidate


def fields(value, allowed, required=()):
    require(isinstance(value, dict), "此处必须为JSON对象")
    missing = sorted(set(required) - set(value)); extra = sorted(set(value) - set(allowed))
    require(not missing and not extra, "字段不符；缺少=" + ",".join(missing) + "；未支持=" + ",".join(extra))


def text(value, nullable=False):
    require((nullable and value is None) or isinstance(value, str), "文字/ID/规格必须保持字符串，未知用null")


def privacy(value):
    if isinstance(value, dict):
        for key, child in value.items():
            require(re.sub(r"[_\-\s]", "", key).lower() not in PRIVATE_KEYS, "公开资料含买家或凭证字段，停止打包")
            privacy(child)
    elif isinstance(value, list):
        for child in value: privacy(child)


def evidence_resolver(root, values):
    require(isinstance(values, dict), "evidence必须为对象")
    for key, value in values.items():
        require(IDENTIFIER.fullmatch(key), "证据ID无效")
        fields(value, {"path", "quote"}, {"path", "quote"})
        local_file(root, value["path"])
        require(isinstance(value["quote"], str) and 1 <= len(value["quote"]) <= 2000, "证据须保留原文或图中文字定位")
    def verify(ids):
        require(isinstance(ids, list) and ids and len(ids) == len(set(ids)) and all(i in values for i in ids), "缺少可落地核对的证据引用")
    return verify


def image_meta(path):
    from PIL import Image
    require(path.stat().st_size in range(1, MAX_IMAGE + 1), "原图为空或超过20MiB")
    with Image.open(path) as image:
        require(image.format in {"JPEG", "PNG", "WEBP"}, "原图格式不是JPEG/PNG/WebP")
        width, height = image.size
        require(1 <= width <= 50000 and 1 <= height <= 50000 and width * height <= 80_000_000, "原图尺寸超限")
        image.verify()
    with Image.open(path) as image: image.load()
    extension = path.suffix.lower().lstrip(".")
    require(extension in {"jpg", "jpeg", "png", "webp"}, "原图扩展名无效")
    sha = sha_file(path); target = "images/" + sha + "." + extension
    return {"path": target, "sha256": sha, "bytes": path.stat().st_size, "width": width, "height": height}


def baseline(path):
    require(Path(path).stat().st_size <= MAX_ZIP, "旧目录包超过600MiB")
    with zipfile.ZipFile(path) as archive:
        entries = archive.infolist(); names = [e.filename for e in entries]
        require(2 <= len(names) <= 4001 and len(names) == len(set(names)), "旧ZIP文件数量/重复项异常")
        require(all(n == "manifest.json" or SHA_PATH.fullmatch(n) for n in names), "旧ZIP包含额外文件")
        require(archive.getinfo("manifest.json").file_size <= MAX_JSON, "旧清单超过8MiB")
        catalog = json.loads(archive.read("manifest.json"))
        require(catalog["format"] == "lolita.square" and catalog["schemaVersion"] == 1, "旧包不是广场v1")
        assets = catalog["assets"]
        require(set(names) == {"manifest.json"} | {a["path"] for a in assets}, "旧包图片清单不符")
        for asset in assets:
            entry = archive.getinfo(asset["path"])
            require(0 < entry.file_size <= MAX_IMAGE and entry.file_size == asset["bytes"] and
                    entry.compress_size > 0 and entry.file_size <= entry.compress_size * 250, "旧原图大小或压缩比例异常")
            digest = hashlib.sha256(archive.read(entry)).hexdigest()
            require(digest == asset["sha256"] == SHA_PATH.fullmatch(asset["path"])[1], "旧原图SHA不符")
        return catalog


@contextmanager
def state_lock(state):
    state = Path(state); state.mkdir(parents=True, exist_ok=True)
    with (state / "current.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        yield state


def current(state):
    path = Path(state) / "current.json"
    if not path.exists(): return None
    data = read_json(path)
    require(data.get("version") == 1 and Path(data["package"]).is_file() and sha_file(data["package"]) == data["sha256"], "当前目录基线缺失或被修改；停止自动推进")
    return data


def configure(state, package, project):
    package = Path(package).expanduser().resolve()
    with state_lock(state):
        require(not (Path(state) / "current.json").exists(), "已有基线，不自动替换；请沿用现有目录")
        catalog = baseline(package); result = android_check(package, project)
        pointer = {"version": 1, "package": str(package), "sha256": sha_file(package), "project": str(Path(project).resolve()),
                   "packageId": catalog["packageId"], "revision": catalog["revision"], "androidContract": result}
        atomic_json(Path(state) / "current.json", pointer)
        return pointer


def normalize(data, root, old):
    fields(data, {"format", "schemaVersion", "packageId", "title", "capturedAt", "shops", "events", "products", "evidence", "deferred"},
           {"format", "schemaVersion", "packageId", "title", "capturedAt", "shops", "events", "products", "evidence", "deferred"})
    require(data["format"] == "lolita.square.collector" and type(data["schemaVersion"]) is int and data["schemaVersion"] == 1, "采集输入版本无效")
    require(IDENTIFIER.fullmatch(data["packageId"]), "包稳定ID无效")
    if old: require(data["packageId"] == old["packageId"], "必须沿用已有packageId")
    text(data["title"]); text(data["capturedAt"])
    require(len(encoded(data)) <= 32 * 1024 * 1024, "采集输入过大")
    require(isinstance(data["products"], list) and data["products"], "本轮无完整商品，不以旧包冒充本轮成功")
    require(len(data["products"]) <= 2000 and len(data["events"]) <= 2000 and len(data["shops"]) <= 200, "本轮目录超过App上限")
    privacy(data)
    verify = evidence_resolver(root, data["evidence"])
    require(isinstance(data["deferred"], list), "待核对项须为数组")
    for row in data["deferred"]:
        fields(row, {"shopId", "directory", "itemId", "reason"}, {"shopId", "reason"})
        require(isinstance(row["reason"], str) and row["reason"], "待核对项须写明原因")
    shops = []; events = []; products = []; sources = {}; assets = {}; warnings = []; single_specs = []
    for shop in data["shops"]:
        fields(shop, {"id", "name", "directory", "revision", "evidenceIds"}, {"id", "name", "directory", "evidenceIds"})
        text(shop["id"]); text(shop["name"]); verify(shop["evidenceIds"])
        shops.append({"id": shop["id"], "name": shop["name"]})
    for event in data["events"]:
        fields(event, {*EVENT_TEXT, "id", "revision", "shopId", "designId", "batchId", "productIds", "evidenceIds"},
               {*EVENT_TEXT, "id", "shopId", "designId", "batchId", "productIds", "evidenceIds"})
        verify(event["evidenceIds"])
        for key in (*EVENT_TEXT, "id", "shopId", "designId", "batchId"): text(event[key])
        require(event["kind"] in {"上新", "再贩", "现货", "尾款"}, "动态类别未确认，应留在deferred")
        events.append({k: v for k, v in event.items() if k not in {"revision", "evidenceIds"}})
    variants = {}
    for original in data["products"]:
        fields(original, PRODUCT_FIELDS, PRODUCT_FIELDS - {"revision", "materials"})
        product = copy.deepcopy(original); verify(product.pop("evidenceIds"))
        coverage = product.pop("coverage")
        fields(coverage, {"skuComplete", "detailsComplete", "skuCount"}, {"skuComplete", "detailsComplete", "skuCount"})
        require(coverage["skuComplete"] is True and coverage["detailsComplete"] is True and type(coverage["skuCount"]) is int and coverage["skuCount"] == len(product["skus"]), "商品规格/详情未采全，移入deferred，不截取打包")
        require(isinstance(product["id"], str) and re.fullmatch(r"[0-9]+", product["id"]), "公开商品ID须为完整十进制字符串")
        for key in ("shopId", "title", "url", "capturedAt", "pricePhase", "priceType"): text(product[key])
        require(product["priceType"] not in {"FULL", "DEPOSIT", "BALANCE", "PLACEHOLDER"}, "priceType填写可读价格说明；阶段代码只放pricePhase")
        url = urlparse(product["url"])
        require(url.scheme == "https" and url.hostname in {"item.taobao.com", "detail.tmall.com"} and parse_qs(url.query).get("id") == [product["id"]] and set(parse_qs(url.query)) == {"id"}, "只接受去跟踪参数的公开商品链接")
        fields(product["info"], INFO_KEYS)
        require(len(product["skus"]) <= 1000 and len(product["images"]) <= 300, "商品SKU或图片超过App上限")
        own = {}
        for image in product["images"]:
            fields(image, {"localPath", "role", "order", "url"}, {"localPath", "role", "order", "url"})
            path = local_file(root, image["localPath"])
            require("images" in Path(image["localPath"]).parts and not {"raw", "evidence"} & set(Path(image["localPath"]).parts), "包中只放商品原图，不放页面证据或截图")
            require(image["role"] in {"main", "sku", "detail", "promo"} and type(image["order"]) is int and image["order"] >= 0, "原图角色或顺序无效")
            u = urlparse("https:" + image["url"] if image["url"].startswith("//") else image["url"])
            require(u.scheme in {"https", "http"} and u.hostname and not u.username and not u.password, "原图须有公开来源URL")
            meta = image_meta(path); own[image["localPath"]] = meta["path"]
            assets[meta["path"]] = meta; sources[meta["path"]] = path
        def refs(paths):
            require(isinstance(paths, list) and len(paths) == len(set(paths)) and all(p in own for p in paths), "单品图片/尺码表引用不属于当前商品")
            return list(dict.fromkeys(own[p] for p in paths))
        product["images"] = [{"path": own[i["localPath"]], **{k: i[k] for k in ("role", "order", "url")}} for i in product["images"]]
        for sku in product["skus"]:
            fields(sku, SKU_FIELDS, SKU_FIELDS - {"photoPaths", "photoEvidenceIds", "variantId", "variantEvidenceIds"})
            for key in ("id", "key", "itemId", "priceType", "stock", "content", "form"): text(sku[key])
            require(sku["priceType"] not in {"FULL", "DEPOSIT", "BALANCE", "PLACEHOLDER"}, "SKU priceType须为可读价格说明，不填内部阶段代码")
            for key in ("color", "size", "price"): text(sku[key], nullable=True)
            require(re.fullmatch(r"[0-9]+", sku["id"]) and sku["id"] != "0", "SKU ID无效或为起价摘要")
            require(type(sku["notSoldAlone"]) is bool, "单独售卖标记须为布尔值")
            require(isinstance(sku["props"], dict) and sku["props"] == sku["rawProperties"] and all(isinstance(k, str) and isinstance(v, str) for k, v in sku["props"].items()), "SKU规格原文缺失或被改写")
            if sku["price"] is not None:
                try: amount = Decimal(sku["price"])
                except InvalidOperation: raise ValueError("报价须为精确十进制字符串或null")
                require(amount.is_finite() and 0 <= amount <= 100_000_000, "报价无效")
            for part in sku["parts"]:
                fields(part, {"key", "name", "category", "isAccessory", "color", "size", "relation", "uncertainty"}, {"key", "name", "category", "isAccessory", "color", "size", "relation", "uncertainty"})
                for key in ("key", "name", "relation"): text(part[key])
                for key in ("category", "color", "size", "uncertainty"): text(part[key], nullable=True)
                require(type(part["isAccessory"]) is bool, "小物标记须为布尔值")
            # One explicitly independent SKU names its only member. Never spread a bundle's values to members.
            if len(sku["parts"]) == 1 and sku["form"] in {"单品", "小物"} and not sku["notSoldAlone"]:
                member = sku["parts"][0]
                for field in ("color", "size"):
                    if member[field] is None and sku[field] is not None and not member["uncertainty"]:
                        member[field] = sku[field]
                        single_specs.append({"productId": product["id"], "skuId": sku["id"], "partKey": member["key"], "field": field, "source": "explicit independent single-member SKU"})
            for style in sku["styleFacts"]:
                fields(style, {"value", "source", "field", "evidenceIds"}, {"value", "source", "field", "evidenceIds"})
                verify(style.pop("evidenceIds"))
                for key in ("value", "source", "field"): text(style[key])
            if sku.get("photoPaths"): verify(sku.pop("photoEvidenceIds", []))
            else:
                sku.pop("photoEvidenceIds", None); warnings.append({"productId": product["id"], "skuId": sku["id"], "missing": "independentPhoto"})
            sku["photoPaths"] = refs(sku.get("photoPaths", []))
            variant_evidence = sku.pop("variantEvidenceIds", [])
            if variant_evidence: verify(variant_evidence)
            sku.setdefault("variantId", "v-" + hashlib.sha256(encoded([product["shopId"], product["id"], sku["id"]])).hexdigest())
            for event in events:
                if product["id"] in event["productIds"]:
                    variants.setdefault((product["shopId"], event["batchId"], sku["variantId"]), []).append((product, sku, bool(variant_evidence)))
        for material in product.get("materials", []) or []:
            fields(material, {"partKey", "detailPaths", "facts", "source", "sizeTable", "evidenceIds"}, {"partKey", "detailPaths", "facts", "source", "evidenceIds"})
            verify(material.pop("evidenceIds")); material["detailPaths"] = refs(material["detailPaths"])
            for key in ("partKey", "facts", "source"): text(material[key])
            table = material.get("sizeTable")
            if table is not None:
                fields(table, {"columns", "rows", "note", "evidenceIds"}, {"columns", "rows", "note", "evidenceIds"})
                verify(table.pop("evidenceIds")); text(table["note"])
                require(isinstance(table["columns"], list) and all(isinstance(c, str) for c in table["columns"]), "尺码表列名须为文字")
                for row in table["rows"]:
                    fields(row, {"size", "values"}, {"size", "values"}); text(row["size"])
                    require(isinstance(row["values"], list) and len(row["values"]) == len(table["columns"]) and all(isinstance(v, str) for v in row["values"]), "尺码表行列不对应；不插值补齐")
        product.pop("revision", None); products.append(product)
    require(len(assets) <= 4000 and sum(a["bytes"] for a in assets.values()) <= MAX_ZIP, "本轮原图总量超过App上限")
    for rows in variants.values():
        if len({p["id"] for p, _, _ in rows}) > 1:
            ids = {p["id"] for p, _, _ in rows}
            require(all(confirmed for _, _, confirmed in rows) and len({p["shopId"] for p, _, _ in rows}) == 1 and
                    any(ids <= set(e["productIds"]) for e in events), "跨链接variantId必须有同店同批证据")
            require(len({encoded(s["parts"]) for _, s, _ in rows}) == 1, "跨链接规格的部件/颜色/尺码不一致，禁止猜测归并")
    previous_variants = {}
    for product in (old or {}).get("products", []):
        for event in (old or {}).get("events", []):
            if product["id"] in event["productIds"]:
                for sku in product["skus"]:
                    previous_variants.setdefault((product["shopId"], event["batchId"], sku["variantId"]), []).append((product, sku))
    for key, rows in variants.items():
        for product, sku, confirmed in rows:
            for previous_product, previous_sku in previous_variants.get(key, []):
                if previous_product["id"] != product["id"]:
                    require(confirmed and previous_product["shopId"] == product["shopId"] and encoded(previous_sku["parts"]) == encoded(sku["parts"]), "复用旧目录跨链接variantId缺少依据或规格不一致")
    for rows in (shops, events, products): require(len(rows) == len({r["id"] for r in rows}), "本轮记录ID重复")
    require({p["id"] for p in products} == {i for e in events for i in e["productIds"]}, "每个动态须提供完整商品组，每个商品须归属动态")
    return {"shops": shops, "events": events, "products": products, "assets": list(assets.values()), "singleMemberSpecsFromSku": single_specs}, sources, warnings


def merge(old, incoming, data):
    changes = {}
    business_changes = {}
    result = {"format": "lolita.square", "schemaVersion": 1, "packageId": data["packageId"],
              "title": data["title"], "capturedAt": data["capturedAt"]}
    for kind in ("shops", "events", "products"):
        before = {r["id"]: r for r in (old or {}).get(kind, [])}; result[kind] = []; count = 0; meaningful = 0
        for value in incoming[kind]:
            previous = before.pop(value["id"], None); row = {k: v for k, v in value.items() if k != "revision"}
            if previous:
                immutable = ("shopId", "designId", "batchId") if kind == "events" else ("shopId",) if kind == "products" else ()
                require(all(previous[k] == row[k] for k in immutable), "已有记录店铺/设计/批次身份改变，须使用新ID")
            same = previous is not None and encoded({k: v for k, v in previous.items() if k != "revision"}) == encoded(row)
            ignored = {"revision", "capturedAt", "captured"}
            meaningful += previous is None or encoded({k: v for k, v in previous.items() if k not in ignored}) != encoded({k: v for k, v in row.items() if k not in ignored})
            row["revision"] = previous["revision"] if same else previous["revision"] + 1 if previous else 1
            result[kind].append(row); count += not same
        result[kind].extend(before.values()); changes[kind] = count
        business_changes[kind] = meaningful
    all_assets = {a["path"]: a for a in (old or {}).get("assets", [])}
    all_assets.update({a["path"]: a for a in incoming["assets"]})
    used = {i["path"] for p in result["products"] for i in p["images"]}
    result["assets"] = [all_assets[p] for p in sorted(used)]
    # A true replay preserves the existing package revision and byte content.
    same = old and encoded({k: v for k, v in old.items() if k != "revision"}) == encoded(result)
    result["revision"] = old["revision"] if same else old["revision"] + 1 if old else 1
    return result, changes, business_changes


def build(input_path, output, project, state=None, expected_baseline=None):
    from contextlib import nullcontext
    input_path = Path(input_path).resolve(); output = Path(output).resolve()
    with state_lock(state) if state else nullcontext():
        pointer = current(state) if state else None
        require((pointer or {}).get("sha256") == expected_baseline, "基线已变化或请求缺少基线SHA，请重新prepare；不覆盖较新目录")
        old = baseline(pointer["package"]) if pointer else None
        data = read_json(input_path)
        incoming, sources, warnings = normalize(data, input_path.parent, old)
        catalog, changes, business_changes = merge(old, incoming, data)
        require(len(encoded(catalog)) <= MAX_JSON, "合并目录清单超过8MiB，不截断")
        require(len(catalog["assets"]) <= 4000 and sum(a["bytes"] for a in catalog["assets"]) <= MAX_ZIP and
                sum(len(p["skus"]) for p in catalog["products"]) <= 20000, "合并目录超过App上限，不截断也不推进基线")
        output.parent.mkdir(parents=True, exist_ok=True)
        require(not output.exists(), "交付文件已存在，请保留原包并选择新文件名")
        temporary = output.with_name("." + output.name + ".candidate")
        require(not temporary.exists(), "已有候选文件，请先核对上次失败结果")
        # No source file is changed, and ZIP contains neither evidence nor the raw JSON envelope.
        with zipfile.ZipFile(temporary, "x", compression=zipfile.ZIP_STORED) as archive:
            archive.writestr("manifest.json", encoded(catalog))
            previous = zipfile.ZipFile(pointer["package"]) if pointer else None
            try:
                for asset in catalog["assets"]:
                    if asset["path"] in sources:
                        path = sources[asset["path"]]
                        require(sha_file(path) == asset["sha256"], "打包期间原图变化")
                        archive.write(path, asset["path"])
                    else:
                        with previous.open(asset["path"]) as reader, archive.open(asset["path"], "w") as writer:
                            import shutil
                            shutil.copyfileobj(reader, writer, 65536)
            finally:
                if previous: previous.close()
        require(temporary.stat().st_size <= MAX_ZIP, "合并包超过600MiB，须另行规划分包；不截断也不推进基线")
        contract = android_check(temporary, project)
        require(not output.exists(), "交付文件被其他任务创建，停止发布")
        os.replace(temporary, output)
        report = {"passed": True, "format": "lolita.square", "schemaVersion": 1, "packageId": catalog["packageId"],
                  "verifiedAt": datetime.now(ZoneInfo("Asia/Shanghai")).isoformat(),
                  "revision": catalog["revision"], "sha256": sha_file(output), "bytes": output.stat().st_size,
                  "counts": {k: len(catalog[k]) for k in ("shops", "events", "products", "assets")}, "changed": changes,
                  "businessChanged": business_changes,
                  "skuCount": sum(len(p["skus"]) for p in catalog["products"]), "androidContract": contract,
                  "deferred": data["deferred"], "missingMappings": warnings, "baselineRetainedWhenAbsent": True,
                  "singleMemberSpecsFromSku": incoming["singleMemberSpecsFromSku"],
                  "semanticReview": "结构/图片/证据引用自动校验通过，不等同于人工核对事实归属", "networkAccess": False}
        atomic_json(output.with_suffix(".quality.json"), report)
        if state:
            atomic_json(Path(state) / "current.json", {"version": 1, "package": str(output), "sha256": report["sha256"],
                        "project": str(Path(project).resolve()), "packageId": catalog["packageId"], "revision": catalog["revision"], "androidContract": contract})
        return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    setup = commands.add_parser("configure"); setup.add_argument("--baseline", required=True); setup.add_argument("--state", default=str(DEFAULT_STATE)); setup.add_argument("--project", required=True)
    run = commands.add_parser("build"); run.add_argument("--input", required=True); run.add_argument("--output", required=True); run.add_argument("--project", required=True); run.add_argument("--state"); run.add_argument("--expected-baseline")
    args = parser.parse_args()
    try:
        result = configure(args.state, args.baseline, args.project) if args.command == "configure" else build(args.input, args.output, args.project, args.state, args.expected_baseline)
        print(json.dumps(result, ensure_ascii=False, indent=2)); return 0
    except (OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile) as exc:
        print(json.dumps({"passed": False, "error": str(exc)}, ensure_ascii=False)); return 2


if __name__ == "__main__":
    raise SystemExit(main())
