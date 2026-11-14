"""Pre-implementation vector composition for the compact Square controls."""
from pathlib import Path
from html import escape
ROOT = Path(__file__).resolve().parents[2] / 'docs/visuals/2026-11-14-square-tabs/artist'
ROOT.mkdir(parents=True, exist_ok=True)
SKINS = [('甜美','#fff0f6','#b85e87'),('哥特','#f1edf5','#73538a'),('中华','#f7f1e8','#a64b49'),('古典','#f5f1e8','#8b7154'),('海军','#eef4f9','#376b99'),('田园','#f1f5ea','#657d45'),('维多利亚','#f5eef2','#92617b')]
def text(x,y,s,size=14,color='#352b34'):
 return f'<text x="{x}" y="{y}" font-family="PingFang SC,sans-serif" font-size="{size}" fill="{color}">{escape(s)}</text>'
def rect(x,y,w,h,c,r=0):
 return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{c}" rx="{r}"/>'
def phone(x,y,skin,dark=False,mode='feed'):
 name,bg,accent=skin; ink='#eee5ed' if dark else '#352b34'; bg='#242029' if dark else bg; card='#39323c' if dark else '#ffffff'
 s=rect(x,y,340,570,bg,22)+text(x+18,y+25,'9:41',12,ink)
 s+=f'<circle cx="{x+29}" cy="{y+55}" r="6" fill="none" stroke="{ink}" stroke-width="2"/><path d="M{x+34} {y+60} l5 5" stroke="{ink}" stroke-width="2"/>'
 for i,t in enumerate(['上新','再贩','现货','尾款']): s+=text(x+64+i*60,y+61,t,13,accent if i==0 else ink)
 s+=rect(x+64,y+72,28,2,accent,1)+f'<path d="M{x+307} {y+50} h18 l-7 8 v6 l-4 2 v-8z" fill="none" stroke="{ink}" stroke-width="1.5"/>'
 if mode=='selected': s+=rect(x+14,y+86,150,28,card,14)+text(x+24,y+105,'古典玩偶  ×',12,accent)
 for i in range(4):
  px=x+14+i%2*161; py=y+(128 if mode=='selected' else 88)+i//2*190
  s+=rect(px,py,150,178,card,12)+rect(px+4,py+4,142,114,'#54444f' if dark else '#e8dbe0',10)
  s+=f'<path d="M{px+56} {py+19} l-9 17 12 16 -24 57 h80 l-24 -57 12 -16 -9 -17 -10 6 -9 -6 -9 6z" fill="{accent}" opacity=".72"/>'
  s+=text(px+9,py+137,'古典玩偶 · 上新',11,ink)+text(px+9,py+160,'花园漫游 · JSK',14,ink)
 s+=rect(x,y+518,340,52,card)+text(x+14,y+550,'首页    穿搭    广场    统计    个人',14,ink)
 if mode=='filters':
  s+=rect(x,y+132,340,438,card,22)+rect(x+148,y+143,44,4,accent,2)+text(x+20,y+181,'筛选',20,ink)
  s+=text(x+20,y+210,'上新',12,accent)+text(x+20,y+249,'店铺',14,ink)+text(x+84,y+249,'全部店铺                       ›',14,accent)
  s+=text(x+20,y+294,'内容',14,ink)+text(x+20,y+327,'不限     单品     套装     小物',13,ink)+text(x+20,y+373,'颜色',14,ink)+text(x+20,y+407,'不限     黑色     白色     粉色',13,ink)
  s+=text(x+20,y+450,'风格',14,ink)+text(x+20,y+482,'不限     古典     甜美',13,ink)+rect(x+129,y+517,191,37,accent,12)+text(x+22,y+541,'清除属性',14,accent)+text(x+184,y+541,'完成（12）',14,'#fff')
 if mode=='empty': s+=rect(x+12,y+86,316,411,bg)+text(x+112,y+240,'暂无商品',18,ink)+text(x+90,y+285,'清除店铺筛选',14,accent)
 return s+text(x,y+598,f'{name} · {"暗" if dark else "浅"} · {mode}',15)
def save(name,w,h,body): (ROOT/name).write_text(f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}">{rect(0,0,w,h,"#e9e6e2")}{body}</svg>')
s=text(24,35,'广场｜与首页同高的单行导航',25)+text(24,66,'搜索 / 四类动态 / 筛选；默认上新；店铺移入筛选，选中后可直接清除。',16)
for i,mode in enumerate(['feed','filters','selected','empty']): s+=phone(24+i*366,95,SKINS[3],mode=mode)
s+=text(24,743,'字号和高度跟随首页：labelMedium、Tab最小34dp、图标32dp、上下2dp；大字分类可横向滚动。',16)
s+=text(24,775,'搜索展开复用首页输入栏与取消并保留筛选；店铺沿用可搜索选择框；筛选完成区在真实键盘上方。',16)
save('story.svg',1490,805,s)
for dark in [False,True]:
 s=text(24,35,'七皮肤浅暗｜单行分类与统一筛选',25)
 for i,skin in enumerate(SKINS):
  s+=phone(24+i*360,65,skin,dark)
  s+=phone(24+i*360,705,skin,dark,'filters')
 save('dark.svg' if dark else 'light.svg',2540,1325,s)
print(ROOT)
