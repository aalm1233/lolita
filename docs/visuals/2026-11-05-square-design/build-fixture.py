"""Build public-product design fixtures from existing local dsh archives, offline.

Run with: uv run --with pillow python build-fixture.py
Originals are never changed. Preview copies are for this design only.
"""
import hashlib
import json
from pathlib import Path
from PIL import Image
from product_attributes import enrich_sku, product_info

HERE = Path(__file__).resolve().parent
PROJECT = HERE.parents[2]
SOURCES = {
    "puppets": ("古典玩偶", Path.home() / "Downloads/puppets-and-doll-latest-2026-11-03"),
    "lilizi": ("莉莉子", Path.home() / "Downloads/lilizi-neoludwig-latest-2026-11-05/lilizi"),
    "neo": ("新路德维希", Path.home() / "Downloads/lilizi-neoludwig-latest-2026-11-05/neo-ludwig"),
}
EVENTS = [
    dict(id="dorothy-cotton", shop="puppets", title="漫游序章", subtitle="绿野仙踪 · 多萝西", kind="上新", tags=["上新"], state="已开售", date="10月30日 20:00 开售", time="2026-10-30T20:00:00+08:00", ids=["1088022205740", "1090713386610"], price="JSK ¥899.00", priceNote="全款活动价 · KC另选", note="2027年5月31日起发货", material="KOKKA 定制印花竹节棉；里料为机械弹平纹。", detail="JSK小微高腰、侧袋；背带裙为低腰中长款。单件、配件和套餐分别选取。"),
    dict(id="dorothy-chiffon", shop="puppets", title="风与梦", subtitle="绿野仙踪 · 雪纺版", kind="上新", tags=["上新"], state="已开售", date="10月30日 20:00 开售", time="2026-10-30T20:00:00+08:00", ids=["1087061914876", "1092851076916"], price="长OP ¥969.00", priceNote="全款活动价 · 非发圈起价", note="2027年4月起发货", material="以本款对应详情图所列面料为准。", detail="长OP与娃娃裙分别选择；发圈、KC为独立配件。"),
    dict(id="dorothy-solid", shop="puppets", title="多萝西 · 纯色", subtitle="长款 / 短款", kind="上新", tags=["上新"], state="已开售", date="10月30日 20:00 开售", time="2026-10-30T20:00:00+08:00", ids=["1088131401518", "1092848472915"], price="短JSK ¥739.00", priceNote="全款活动价 · 长款另选", note="2027年4月起发货", material="款式、面料及洗护完整保留官方详情图。", detail="短JSK、长JSK、短袖内搭、长袖内搭与KC独立核对。"),
    dict(id="faded-photo", shop="puppets", title="褪色相片", subtitle="玩偶的衣橱", kind="上新", tags=["上新"], state="预告", date="11月11日 20:00 开售", time="2026-11-11T20:00:00+08:00", ids=["1089865027952"], price="正腰OP ¥3,110.40", priceNote="采集页标价 · 开售前待核对", note="尚未开售，可先加入心愿", material="材质、尺码及配件见本款官方详情图。", detail="正腰OP与低腰OP不是同一版型；KC、BNT、长南瓜裤另选。"),
    dict(id="liana", shop="lilizi", title="莉安娜 · 十字OP", subtitle="长款 / 短款", kind="现货", tags=["现货"], state="部分规格无货", date="上架日期未提供", time=None, ids=["1016937432843"], price="长OP ¥625.00", priceNote="现货页价 · 部分尺码无货", note="标题现货；页面另示预售20天内发货", material="面料、袖套及裙装信息按对应原图查看。", detail="紫色、黑色；长短OP与袖套分别选取。旧预约截止日期未标年份，不作为此次上新时间。"),
    dict(id="victoria", shop="lilizi", title="维多利亚的赞礼", subtitle="掉落 · 哥特系列", kind="现货", tags=["现货"], state="掉落现货", date="上架日期未提供", time=None, ids=["1022012881368"], price="短半裙 ¥473.00", priceNote="现货页价 · 衬衫与马甲另选", note="掉落商品，按具体规格核对", material="对应款式材质见官方详情原图。", detail="衬衫+领花、马甲、长半裙与短半裙不是一件整套；不将马甲起价当作全套售价。"),
    dict(id="pilgrim-repeat", shop="neo", title="音乐朝圣者", subtitle="再贩 · 第二批次", kind="再贩", tags=["再贩"], state="预约已结束", date="6月22日—7月2日 预约", time="2026-06-22T10:00:00+08:00", ids=["1057768531418", "1060482620961"], price="本批预约已结束", priceNote="下架占位价不作为参考金额", note="本批尾款预告：11月25日—12月24日", material="投票2.0柄图 + 3.0定制暗纹提花；巴赫乐谱主题。", detail="裙装与中性衬衫、小物分链接。历史预约全款与当前第一批尾款分开，不跨批相加。"),
    dict(id="sleeve-repeat", shop="neo", title="双层姬袖衬衫", subtitle="再贩 · 第二批次", kind="再贩", tags=["再贩"], state="预约已结束", date="6月22日—7月2日 预约", time="2026-06-22T10:00:00+08:00", ids=["1058557934909"], price="历史页标价 ¥278.00", priceNote="已下架 · 不预填参考金额", note="本批尾款预告：11月25日—12月24日", material="材质及完整尺码表以本批官方图为准。", detail="白色、黑色；历史再贩可作为心愿资料保存，不表示现在可下单。"),
    dict(id="pilgrim-balance", shop="neo", title="音乐朝圣者", subtitle="第一批次 · 尾款", kind="尾款", tags=["再贩", "尾款"], state="尾款开放", date="11月12日 17:00 截止", time="2026-10-15T10:00:00+08:00", ids=["1089219231672"], price="尾款 ¥39.00–478.00", priceNote="不含定金 · 非整件售价", note="仅限已付第一批次定金者", material="本批商品信息与对应尾款规格见原图。", detail="尾款期10月15日10:00至11月12日17:00；尾款后15个工作日内发货。不同批次不可套用。"),
    dict(id="sleeve-balance", shop="neo", title="双层姬袖衬衫", subtitle="第一批次 · 尾款", kind="尾款", tags=["再贩", "尾款"], state="尾款开放", date="尾款截止日未提供", time=None, ids=["1090134478903"], price="尾款 ¥222.00", priceNote="不含定金 · 非全款", note="不能套用音乐朝圣者的截止时间", material="仅对应第一批次双层姬袖衬衫。", detail="本页未给出开放日与截止日；尾款支付后15个工作日内发货。"),
]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    (HERE / "assets").mkdir(parents=True, exist_ok=True)
    provenance, products, counts = [], {}, {}
    for key, (name, root) in SOURCES.items():
        path = root / "products.json"
        data = json.loads(path.read_text())
        counts[key] = {"links": len(data["products"]), "skus": sum(len(p["skus"]) for p in data["products"])}
        provenance.append({"type": "public-product-data", "archive": str(root.relative_to(Path.home())), "sha256": sha(path)})
        for p in data["products"]:
            images = p["images"]
            entries = [e for values in images.values() for e in values] if isinstance(images, dict) else images
            main_images = [e for e in entries if e["role"] == "main"][:2]
            previews = []
            for i, entry in enumerate(main_images):
                source = root / entry["localPath"]
                dest = HERE / "assets" / f"{p['itemId']}-{i+1}.webp"
                with Image.open(source) as im:
                    im = im.convert("RGB")
                    im.thumbnail((960, 960))
                    im.save(dest, "WEBP", quality=84)
                previews.append("assets/" + dest.name)
                provenance.append({"type": "design-preview", "output": "assets/" + dest.name,
                                   "outputSHA256": sha(dest), "archive": str(root.relative_to(Path.home())),
                                   "source": entry["localPath"], "sourceSHA256": sha(source),
                                   "originalURL": entry["url"], "transform": "contain <=960px; webp84; no crop"})
            skus = []
            for sku in p["skus"]:
                props = sku.get("props") or {v["prop"]: v["name"] for v in sku.get("values", [])}
                skus.append(enrich_sku(p, {"id": sku["skuId"], "props": props, "price": sku.get("price"),
                             "priceType": p["priceType"], "stock": sku.get("stockText") or sku.get("quantityText") or "库存未知"}))
            products[p["itemId"]] = {"id": p["itemId"], "title": p["title"], "url": p["url"],
                                      "capturedAt": p["capturedAt"], "priceType": p["priceType"], "skus": skus,
                                      "isDeposit": p["linkType"] == "deposit", "info": product_info(p),
                                      "previews": previews, "imageCount": sum(e["role"] != "promo" for e in entries), "mainCount": sum(e["role"] == "main" for e in entries)}
    # Only preview resources and public fields; never raw SSR or inferred old dates.
    for event in EVENTS:
        event["cover"] = products[event["ids"][0]]["previews"][0]
        event["captured"] = "2026-11-03" if event["shop"] == "puppets" else "2026-11-05"
        event["canAdd"] = event["kind"] != "尾款"
    chart_source = SOURCES["puppets"][1] / "images/1088022205740/detail/044_1a013c82c2.webp"
    chart_dest = HERE / "assets/cotton-jsk-chart.webp"
    with Image.open(chart_source) as im:
        im.save(chart_dest, "WEBP", quality=92)
    provenance.append({"type": "design-size-chart", "source": "images/1088022205740/detail/044_1a013c82c2.webp",
                       "archive": str(SOURCES["puppets"][1].relative_to(Path.home())), "sourceSHA256": sha(chart_source),
                       "output": "assets/cotton-jsk-chart.webp", "outputSHA256": sha(chart_dest), "transform": "webp92; original dimensions; no crop"})
    for skin in ["sweet", "gothic", "chinese", "classic", "navy", "country", "victorian"]:
        source = PROJECT / f"app/src/main/res/drawable-nodpi/bg_art_{skin}.png"
        dest = HERE / f"assets/bg-{skin}.webp"
        with Image.open(source) as im:
            im = im.convert("RGBA"); im.thumbnail((600, 1000)); im.save(dest, "WEBP", quality=70)
        provenance.append({"type": "existing-app-background-preview", "source": str(source.relative_to(PROJECT)),
                           "sourceSHA256": sha(source), "output": "assets/" + dest.name, "outputSHA256": sha(dest)})
    payload = {"asOf": "2026-11-05", "shops": [{"id": k, "name": v[0], "captured": "11月3日" if k == "puppets" else "11月5日"} for k, v in SOURCES.items()],
               "counts": counts, "products": products, "events": EVENTS}
    (HERE / "data.js").write_text("window.SQUARE_DATA = " + json.dumps(payload, ensure_ascii=False, indent=2) + ";\n")
    (HERE / "asset-sources.json").write_text(json.dumps(provenance, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"events": len(EVENTS), "links": len(products), "skus": sum(c["skus"] for c in counts.values()), "assets": len(list((HERE / 'assets').iterdir()))}))


if __name__ == "__main__":
    main()
