"""Offline bindings from explicit public SKU image references; no network access."""
import json,re
from pathlib import Path

def image_key(url):
    # Original CDN object ID survives host, format and thumbnail transformations.
    match=re.search(r'(O1CN[^/!?~]+?)(?:_!!|!!)',url or '')
    return match[1] if match else (url or '').split('?')[0].split('/')[-1]

def find_sku_base(node):
    if isinstance(node,dict):
        if isinstance(node.get('skuBase'),dict):return node['skuBase']
        for value in node.values():
            found=find_sku_base(value)
            if found:return found
    if isinstance(node,list):
        for value in node:
            found=find_sku_base(value)
            if found:return found
    return None

def enrich(product,canonical,root,project):
    props=canonical.get('skuProps',[])
    raw=root/'raw/items'/f"{product['id']}-ssr.json"
    if raw.exists():
        base=find_sku_base(json.loads(raw.read_text()))
        if base:props=base.get('props',props)
    values={}
    for prop in props:
        for value in prop.get('values',[]):
            if isinstance(value,dict) and value.get('image'):
                values[(prop['name'],value['name'])]=value['image']
    images={image_key(i['url']):i['path'] for i in product['images'] if i['role'] in ('sku','main')}
    for sku in product['skus']:
        sku['photoPaths']=list(dict.fromkeys(images[image_key(values[(key,value)])] for key,value in sku['props'].items()
            if (key,value) in values and image_key(values[(key,value)]) in images))
    mapping=project/'docs/visuals/2026-11-06-square-refinement/artist/component-materials.json'
    entries=json.loads(mapping.read_text()) if mapping.exists() else []
    if isinstance(entries,dict):entries=entries.get('mappings',[])
    by_part={}
    for row in entries:
        if str(row['productId'])!=product['id'] or row.get('status')!='verified':continue
        paths=[i['path'] for i in product['images'] if i['role']=='detail' and i['order'] in row['detailOrders']]
        for key in row['partKeys']:
            by_part[key]={'partKey':key,'detailPaths':list(dict.fromkeys(paths)), 'facts':'资料图说明：'+row.get('evidence',''),'source':row.get('source',row.get('evidence','本地原图人工核对'))}
    components=product['info'].get('components',[])
    if isinstance(components,dict):components=[dict(value,name=key) for key,value in components.items()]
    def normalized(name):return re.sub(r'（[^）]*）|\([^)]*\)|\s','',name).lower()
    for part in {p['key']:p for sku in product['skus'] for p in sku['parts']}.values():
        facts=[]
        for component in components:
            if normalized(component.get('name',''))==normalized(part['name']):
                for key,label in [('fabric','材质'),('cut','版型与细节'),('type','类型'),('care','洗护'),('note','说明')]:
                    if component.get(key):facts.append(f"{label}：{component[key]}")
        if facts:
            material=by_part.setdefault(part['key'],{'partKey':part['key'],'detailPaths':[],'facts':'','source':'已采集商品部件明文资料'})
            material['facts']='\n'.join(facts+([material['facts']] if material['facts'] else []))
    product['materials']=list(by_part.values());product['revision']=2
