"""Code-native SVG design, archived before the LAN UI implementation."""
from pathlib import Path
from html import escape

ROOT = Path(__file__).resolve().parents[2] / 'docs/visuals/2026-11-06-square-lan/artist'
ROOT.mkdir(parents=True, exist_ok=True)
SKINS = [('甜美','#fbf3f6','#b85e87'),('哥特','#f1edf5','#73538a'),('中华','#f7f1e8','#a64b49'),('古典','#f5f1e8','#8b7154'),('海军','#eef4f9','#376b99'),('田园','#f1f5ea','#657d45'),('维多利亚','#f5eef2','#92617b')]

def text(x,y,s,size=16,color='#332b32'):
 return f'<text x="{x}" y="{y}" font-size="{size}" fill="{color}" font-family="PingFang SC, sans-serif">{escape(s)}</text>'
def rect(x,y,w,h,fill,r=12,stroke='none'):
 return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{fill}" stroke="{stroke}"/>'
def phone(x,y,bg,accent,dark=False,mode='feed',label=''):
 ink='#f3eaf1' if dark else '#332b32'; surface='#302a32' if dark else '#ffffff'; bg='#211d24' if dark else bg
 s=rect(x,y,330,680,bg,24)+text(x+16,y+26,'9:41',12,ink)+text(x+18,y+65,'广场',22,ink)+text(x+205,y+65,'♡   ▤   ⚙',21,accent)
 s+=rect(x+14,y+88,302,44,surface,12)+text(x+28,y+116,'⌕  搜索商品或店铺',15,ink)
 s+=text(x+17,y+161,'全部     上新     再贩     现货     尾款',15,ink)+rect(x+15,y+170,39,3,accent,1)+text(x+17,y+200,'全部店铺⌄                       筛选',14,accent)
 if mode!='empty':
  for i in range(4):
   px=x+14+(i%2)*157;py=y+218+(i//2)*194
   s+=rect(px,py,145,183,surface,12)+rect(px+4,py+4,137,118,'#e6dbe3' if not dark else '#4c3c4e',9)
   # Garment artwork is illustrative, not an actual collected product.
   s+=f'<path d="M{px+60} {py+18} l-10 17 11 18 -24 57 h71 l-23 -57 10 -18 -10 -17 -8 6 -9 -6z" fill="{accent}" opacity=".75"/>'
   s+=text(px+10,py+140,['古典玩偶','莉莉子'][i%2],12,ink)+text(px+10,py+164,['漫游 · JSK','十字花园 · OP'][i%2],15,ink)
 else:
  s+=text(x+92,y+350,'暂无店铺资料',20,ink)+rect(x+30,y+382,270,44,accent)+text(x+118,y+410,'导入资料包',16,'white')
 if mode=='refresh':
  s+=f'<circle cx="{x+165}" cy="{y+232}" r="20" fill="{surface}"/><path d="M{x+156} {y+233} a10 10 0 1 1 18 4" fill="none" stroke="{accent}" stroke-width="3"/>'
 s+=rect(x,y+626,330,54,surface,0)+text(x+13,y+654,'衣橱     穿搭     广场     统计     个人',14,ink)
 if mode=='settings':
  s+=rect(x,y,330,680,'#00000066',24)+rect(x+22,y+174,286,294,surface,24)+text(x+45,y+213,'广场服务',22,ink)
  s+=text(x+43,y+250,'服务地址',13,accent)+rect(x+42,y+264,246,62,bg,6,accent)+text(x+53,y+290,'http://192.168.1.8:18765',13,ink)
  s+=text(x+43,y+350,'清空地址后停止联网查询',13,ink)+text(x+171,y+433,'取消',15,accent)+text(x+242,y+433,'保存',15,accent)
 return s+text(x,y+710,label,16,ink if not dark else '#333333')

def save(name,w,h,body):
 (ROOT/name).write_text(f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">{rect(0,0,w,h,"#ebe7e3",0)}{body}</svg>')

s=text(30,42,'广场内网更新｜先行艺术界面图',28)+text(30,73,'保留逛选布局；自动更新无遮挡，离线无错误提示；插画仅说明构图。',16)
for i,(mode,label) in enumerate([('refresh','下拉刷新 · 已有内容继续可操作'),('feed','断网缓存 · 不显示网络错误'),('empty','无缓存离线 · 保留本地导入'),('settings','服务配置 · 本机保存，可清空')]):
 s+=phone(30+i*360,102,SKINS[0][1],SKINS[0][2],mode=mode,label=label)
save('main.svg',1470,850,s)
s=text(30,40,'七套皮肤 × 浅暗｜广场服务与刷新',28)
for i,(name,bg,accent) in enumerate(SKINS):
 for j in range(2):
  x=25+i*350;y=75+j*760
  s+=phone(x,y,bg,accent,bool(j),mode='settings' if j else 'refresh',label=f'{name} · {"暗" if j else "浅"}')
save('skins.svg',2480,1600,s)
print(ROOT)
