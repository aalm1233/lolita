"""Explicit mappings for the already captured three-shop design sample.

These mappings are curated evidence, not a generic title/colour/style classifier.
Unknown fields stay null; original SKU properties always remain intact.
"""
import re

COLORS = ["地中海色", "浅蓝色", "蓝色", "黄色", "粉色", "黑色", "白色", "橘色", "紫色", "红色"]
ACCESSORIES = {"KC", "BNT", "Bonnet", "Bonnet（不单售）", "发圈", "袖套", "领花", "托领", "胸针", "发带头饰（不单售）"}
TYPES = {"JSK": "JSK", "背带裙": "JSK", "长OP": "OP", "娃娃裙": "OP", "短JSK": "JSK", "长JSK": "JSK", "正腰OP": "OP", "低腰OP": "OP", "内搭": "衬衫", "短袖内搭": "衬衫", "长袖内搭": "衬衫", "KC": "KC", "BNT": "Bonnet", "发圈": "发圈", "长南瓜裤": "裤装", "衬衫": "衬衫", "领花": "胸针", "托领": "其他配饰", "胸针": "胸针", "十字OP长款": "OP", "十字OP短款": "OP", "袖套": "手袖", "短半裙": "SK", "长半裙": "SK", "马甲": "其他服装", "内裙": "JSK", "可拆外裙": "罩裙", "双层姬袖衬衫": "衬衫", "印花SK": "SK", "背带罩裙": "罩裙", "中性立领衬衫": "衬衫", "中性款衬衫": "衬衫", "发带头饰（不单售）": "发带", "Bonnet（不单售）": "Bonnet"}


def styles_for(product):
    official = product.get("attributes", {}).get("风格")
    if official:
        return [{"value": official, "source": "商品参数", "field": "attributes.风格"}]
    if product["itemId"] in {"1016937432843", "1022012881368"} and "哥特" in product["title"]:
        return [{"value": "哥特", "source": "商品标题", "field": "title"}]
    return []


def enrich_sku(product, sku):
    props = sku["props"]
    raw = props.get("颜色分类", "")
    content, color = raw, None
    ident = product["itemId"]
    if "套装" in props:
        # All dimensions are mandatory: same colour/size is not the same SET.
        content, color = props["套装"], raw
    elif ident in {"1058557934909", "1090134478903"}:
        content, color = "双层姬袖衬衫＋托领＋胸针", raw
    elif ident == "1016937432843":
        match = re.fullmatch(r"十字op(长款|短款)(紫色|黑色)", raw)
        if match:
            content, color = "十字OP" + match[1], match[2]
    else:
        for candidate in COLORS:
            if raw.startswith(candidate):
                color = candidate
                content = raw[len(candidate):].strip()
                break
    if not content:
        content = raw  # An unparsed value must not become an empty component.
    part_names = re.split(r"[+＋]", content)
    is_set = content in {"帝政风SET（长款）", "帝政风SET（短款）"}
    if is_set:
        part_names = ["内裙", "可拆外裙"]
    # Includes supplied by a specific item's description, not by photos.
    form = "套装" if is_set else "组合" if len(part_names) > 1 else "小物" if content in ACCESSORIES else "单品"
    size = props.get("尺码")
    parts = []
    for index, name in enumerate(part_names):
        accessory = name in ACCESSORIES
        parts.append({"key": name, "name": name, "category": TYPES.get(name), "isAccessory": accessory,
                      "color": color if len(part_names) == 1 or index == 0 else None,
                      "size": size if len(part_names) == 1 or index == 0 else None,
                      "relation": "同一SKU包含" if len(part_names) > 1 else "独立SKU",
                      "uncertainty": "颜色和尺码未分别标注" if index > 0 else None})
    facts = list(styles_for(product))
    if is_set:
        facts.append({"value": "帝政风", "source": "款式名", "field": "skus[].props.套装"})
    return {**sku, "key": f"{ident}:{sku['id']}", "itemId": ident, "content": content,
            "color": color, "size": size, "form": form, "parts": parts, "styleFacts": facts,
            "notSoldAlone": "不单售" in content, "rawProperties": props}


def product_info(product):
    design = product.get("designInfo", {})
    components = product.get("components") or design.get("components", {})
    if isinstance(components, dict):
        components = [{"name": k, **v} for k, v in components.items()]
    included = list(product.get("included") or [])
    excluded = list(product.get("notIncluded") or [])
    if not excluded:
        excluded = ["模特搭配不等于购买内容；裙撑、鞋袜等未明确包含的物品不自动登记。"]
    gifts = product.get("gifts") or design.get("gifts") or []
    return {"styles": styles_for(product), "season": product.get("attributes", {}).get("适用季节"),
            "components": components, "included": included, "excluded": excluded,
            "gifts": gifts, "care": product.get("care") or design.get("care"),
            "materials": product.get("materials"), "craft": product.get("craft"),
            "sizes": product.get("sizeChart") or design.get("sizes"),
            "source": "products.json + 对应SKU原文/详情图；样本映射"}
