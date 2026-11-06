"""Offline, curated first square package. Never requests Taobao or changes originals."""
import argparse, hashlib, json, zipfile, re
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
SAMPLE = PROJECT / 'docs/visuals/2026-11-05-square-design/data.js'
ROOTS = {
 'puppets': Path.home()/'Downloads/puppets-and-doll-latest-2026-11-03',
 'lilizi': Path.home()/'Downloads/lilizi-neoludwig-latest-2026-11-05/lilizi',
 'neo': Path.home()/'Downloads/lilizi-neoludwig-latest-2026-11-05/neo-ludwig',
}

def build(output,refined=False):
 sample=json.loads(SAMPLE.read_text().split('=',1)[1].strip().removesuffix(';'))
 assets={}; paths={}; products=[]; provenance=[]
 for shop,root in ROOTS.items():
  canonical=root/'products.json'
  provenance.append({'shopId':shop,'canonicalSha256':hashlib.sha256(canonical.read_bytes()).hexdigest()})
  for p in json.loads(canonical.read_text())['products']:
   product=sample['products'][p['itemId']]
   old=p['images']; old=[x for role in old.values() for x in role] if isinstance(old,dict) else old
   images=[]
   for entry in old:
    source=root/entry['localPath']; b=source.read_bytes(); sha=hashlib.sha256(b).hexdigest()
    assert sha==entry['sha256'],f"Source hash mismatch: {source}"
    ext=source.suffix.lower(); assert ext in {'.png','.webp','.jpg','.jpeg'}
    path=f'images/{sha}{ext}'
    assets[path]={'path':path,'sha256':sha,'bytes':len(b),'width':entry['width'],'height':entry['height']}
    paths[path]=source
    images.append({'path':path,'role':entry['role'],'order':entry['order'],'url':entry['url']})
   # Curated payment-pair size labels: retain the exact terms in rawProperties.
   for sku in product['skus']:
    original_size=sku.get('size')
    if product['isDeposit'] and original_size:
     match=re.fullmatch(r'(XS|S|M|L|XL|XXL|均码)【点击则同意定金发收藏卡片一张，定金须先确认收货，请勿与全款预售混拍】',original_size)
     if match:
      sku['size']=match[1]
      for part in sku['parts']:
       if part.get('size')==original_size:part['size']=match[1]
    # These three pairs are the explicitly curated full/deposit links of one design.
    paired=p['itemId'] in {'1088022205740','1090713386610','1087061914876','1092851076916','1088131401518','1092848472915'}
    identity={'content':sku['content'],'color':sku.get('color'),'size':sku.get('size'),
       'otherDimensions':{k:v for k,v in sku['props'].items() if k not in {'颜色分类','尺码','套装'}}}
    if not paired:identity['itemId']=p['itemId']
    sku['variantId']='v-'+hashlib.sha256(json.dumps(identity,ensure_ascii=False,sort_keys=True).encode()).hexdigest()
   phase='DEPOSIT'  if product['isDeposit'] else 'BALANCE' if '尾款'==p['linkType'] else 'PLACEHOLDER' if '预约' in p['linkType'] else 'FULL'
   products.append({'id':product['id'],'revision':1,'shopId':shop,'title':product['title'],'url':product['url'],
       'capturedAt':product['capturedAt'],'pricePhase':phase,'priceType':product['priceType'],
       'skus':product['skus'],'info':product['info'],'images':images})
   if refined:
    from refine_materials import enrich
    enrich(products[-1],p,root,PROJECT)
 events=[]
 for e in sample['events']:
  events.append({k:v for k,v in e.items() if k in ['id','title','subtitle','kind','state','date','price','priceNote','note','material','detail','captured']}|
    {'revision':1,'shopId':e['shop'],'productIds':e['ids'],'batchId':e['id'],'designId':e['id'].removesuffix('-repeat').removesuffix('-balance')})
 payload={'format':'lolita.square','schemaVersion':1,'packageId':'three-shops-2026-11-05','revision':1,
  'title':'三店首包 · 2026年11月5日','capturedAt':'2026-11-05',
  'shops':[{'id':s['id'],'name':s['name'],'revision':1} for s in sample['shops']],
  'events':events,'products':products,'assets':list(assets.values())}
 if refined:
  payload.update(revision=2,title='三店部件资料包 · 2026年11月6日修订',capturedAt='2026-11-06')
 output.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(output,'w',compression=zipfile.ZIP_STORED) as z:
  def write(name,data):
   entry=zipfile.ZipInfo(name,(2026,11,5,0,0,0));entry.external_attr=0o100644 << 16
   z.writestr(entry,data)
  write('manifest.json',json.dumps(payload,ensure_ascii=False,separators=(',',':')).encode())
  for path in sorted(paths):write(path,paths[path].read_bytes())
 report={'file':str(output),'bytes':output.stat().st_size,'sha256':hashlib.sha256(output.read_bytes()).hexdigest(),
  'shops':len(payload['shops']),'events':len(events),'products':len(products),'skus':sum(len(p['skus']) for p in products),
  'imageReferences':sum(len(p['images']) for p in products),'uniqueOriginalImages':len(assets),'sourceManifests':provenance}
 output.with_suffix('.receipt.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
 print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('output',type=Path);parser.add_argument('--refined',action='store_true');args=parser.parse_args();build(args.output,args.refined)
