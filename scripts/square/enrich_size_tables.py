#!/usr/bin/env python3
"""Bind already-collected, reviewed size tables to explicit components. No network access."""
import argparse
import json
import zipfile

# Source labels were checked against the collected parameter sheets; no fuzzy matching.
BINDINGS = {
    '1088022205740': {'JSK': 'JSK（棉版）', '背带裙': '背带裙（棉版）', 'KC': 'KC（棉版）', '内搭': '内搭（棉版）'},
    '1090713386610': {'JSK': 'JSK（棉版）', '背带裙': '背带裙（棉版）', 'KC': 'KC（棉版）', '内搭': '内搭（棉版）'},
    '1087061914876': {'长OP': '长OP（雪纺版）', '娃娃裙': '娃娃裙（雪纺版）', 'KC': 'KC（雪纺版）', '发圈': '发圈（雪纺版）'},
    '1092851076916': {'长OP': '长OP（雪纺版）', '娃娃裙': '娃娃裙（雪纺版）', 'KC': 'KC（雪纺版）'},
    '1088131401518': {k: k for k in ('短JSK', '长JSK', 'KC', '短袖内搭', '长袖内搭')},
    '1092848472915': {k: k for k in ('短JSK', '长JSK', 'KC', '短袖内搭', '长袖内搭')},
    '1089865027952': {k: k for k in ('正腰OP', '低腰OP', 'KC', 'BNT', '长南瓜裤')},
    '1016937432843': {'十字OP长款': '十字OP', '十字OP短款': '十字OP', '袖套': '袖套'},
    '1022012881368': {k: k for k in ('马甲', '衬衫', '短半裙', '长半裙')},
    '1057768531418': {'印花SK': '印花SK', '背带罩裙': '背带罩裙'},
    '1060482620961': {'中性立领衬衫': '中性立领衬衫'},
    '1058557934909': {'双层姬袖衬衫': '双层姬袖衬衫'},
}


def enrich(catalog):
    count = 0
    for product in catalog['products']:
        changed = False
        for material in product.get('materials', []):
            key = BINDINGS.get(product['id'], {}).get(material['partKey'])
            if key is None:
                continue
            source = product['info']['sizes'][key]
            assert isinstance(source, dict) and source
            assert all(isinstance(row, dict) and row for row in source.values())
            columns = list(dict.fromkeys(col for row in source.values() for col in row))
            if product['id'] == '1016937432843':
                excluded = '短款裙长' if material['partKey'] == '十字OP长款' else '长款裙长' if material['partKey'] == '十字OP短款' else None
                columns = [c for c in columns if excluded is None or not c.startswith(excluded)]
            assert all(set(columns) <= set(row) for row in source.values())
            rows = [{'size': str(size), 'values': [str(values[c]) for c in columns]} for size, values in source.items()]
            assert all(isinstance(values[c], (str, int, float)) for values in source.values() for c in columns)
            note = ''
            if product['id'] == '1016937432843' and material['partKey'].startswith('十字OP'):
                note = '人体立体尺码，非成衣平铺尺寸。'
            elif product['id'] in ('1088022205740', '1090713386610') and material['partKey'] == 'JSK':
                note = '平铺成衣尺寸，手工误差约2cm。'
            material['sizeTable'] = {'columns': columns, 'rows': rows, 'note': note}
            count += 1
            changed = True
        if changed:
            product['revision'] += 1
    catalog['revision'] += 1
    catalog['title'] = '古典玩偶 · 莉莉子 · 新路德维希 / 浏览资料'
    return count


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source')
    parser.add_argument('output')
    args = parser.parse_args()
    with zipfile.ZipFile(args.source) as src:
        catalog = json.loads(src.read('manifest.json'))
        count = enrich(catalog)
        with zipfile.ZipFile(args.output, 'w', compression=zipfile.ZIP_STORED) as dst:
            dst.writestr('manifest.json', json.dumps(catalog, ensure_ascii=False, separators=(',', ':')))
            for entry in src.infolist():
                if entry.filename != 'manifest.json':
                    with src.open(entry) as reader, dst.open(entry.filename, 'w') as writer:
                        while chunk := reader.read(65536):
                            writer.write(chunk)
    print(json.dumps({'tables': count, 'images': len(catalog['assets']), 'output': args.output}, ensure_ascii=False))


if __name__ == '__main__':
    main()
