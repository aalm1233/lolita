"""Preimplementation composition studies; product names are controlled examples."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont,ImageOps
import json
ROOT=Path(__file__).resolve().parents[3];OUT=Path(__file__).resolve().parent
THEMES=json.loads((ROOT/'docs/visuals/complete-wardrobe-2026-10-18/skin-tokens.json').read_text())['themes']
FONT='/System/Library/Fonts/STHeiti Light.ttc';BOLD='/System/Library/Fonts/STHeiti Medium.ttc'
def page(t,mode,state='selected',width=360,scale=1):
 c={**t['modes']['light'],**t['modes'][mode]};rgb=lambda key:tuple(c[key][:3]);h=900
 im=Image.new('RGBA',(width,h),rgb('background'));art=ImageOps.fit(Image.open(ROOT/f"app/src/main/res/drawable-nodpi/{c['art']}.png").convert('RGBA'),im.size)
 if mode=='dark':
  tint=Image.new('RGBA',im.size,tuple(c['artTint'][:3])+(255,));tint.putalpha(art.getchannel('A'));art=tint
 art.putalpha(art.getchannel('A').point(lambda a:round(a*c['artAlpha'])));im=Image.alpha_composite(im,art).convert('RGB');d=ImageDraw.Draw(im)
 def text(x,y,s,size=13,key='onSurface',bold=False):d.text((x,y),s,font=ImageFont.truetype(BOLD if bold else FONT,round(size*scale)),fill=rgb(key))
 def wrap(x,y,s,size=13,key='onSurface',w=None):
  f=ImageFont.truetype(FONT,round(size*scale));lines=['']
  for ch in s:
   if d.textlength(lines[-1]+ch,font=f)>(w or width-x-28):lines.append(ch)
   else:lines[-1]+=ch
  for j,line in enumerate(lines):text(x,y+j*size*scale*1.5,line,size,key)
  return y+len(lines)*size*scale*1.5
 def card(y,height):d.rounded_rectangle((16,y,width-16,y+height),radius=c['cardRadius'],fill=rgb('card'),outline=rgb('divider'))
 text(16,9,'9:41',10)
 header=150 if scale>1 else 88
 for y in range(30,header):
  a,b=c['gradient'];p=(y-30)/(header-30);d.line((0,y,width,y),fill=tuple(round(a[k]*(1-p)+b[k]*p) for k in range(3)))
 text(16,46,'‹',24,'topBarForeground');wrap(46,45,'淘宝商品导入（试用）',17,'topBarForeground',width-66)
 y=header+17
 if scale==1:
  card(y,100);text(30,y+14,'核对商品资料',16,bold=True);wrap(30,y+45,'印花连裤袜 · 规格示意',14);y+=114
  card(y,138);text(30,y+14,'分别导入',16,bold=True)
  yy=wrap(30,y+46,'按颜色和尺码分别导入',14)
  d.rounded_rectangle((width-67,yy+4,width-29,yy+24),radius=10,fill=rgb('divider' if state=='off' else 'accent'))
  d.ellipse((width-(65 if state=='off' else 48),yy+6,width-(49 if state=='off' else 32),yy+22),fill=rgb('surface'))
  wrap(30,yy+34,'每个勾选组合保存一件；原文规格可在编辑时核对。',11);y+=152
 if state!='off':
  card(y,395*scale);y=wrap(30,y+14,'选择颜色和尺码组合',16)+16
  d.rounded_rectangle((30,y,width-30,y+40*scale),radius=c['rSmall'],outline=rgb('divider'),fill=rgb('surface'));text(42,y+11*scale,'搜索颜色、款式或尺码',12);y+=52*scale
  y=wrap(30,y,'已选 '+('0' if state=='empty' else '2')+' 个组合 · 筛选保留已选',12)+10
  for name,selected in [('黑色（示意）','S'),('白色（示意）','M')]:
   y=wrap(30,y,name,14)+8;x=30
   for size in ['S','M','L']:
    on=state!='empty' and size==selected;w=58;hh=32*scale
    d.rounded_rectangle((x,y,x+w,y+hh),radius=c['rSmall'],fill=rgb('accent' if on else 'surface'),outline=rgb('accent'))
    text(x+7 if on else x+17,y+8*scale,('✓ ' if on else '')+size,13,'onPrimary' if on else 'onSurface');x+=w+8
   y+=42*scale
   if state!='empty':
    y=wrap(30,y,name+' · '+selected+' 参考金额',11)+6
    d.rounded_rectangle((30,y,width-30,y+38*scale),radius=c['rSmall'],fill=rgb('surface'),outline=rgb('divider'));text(42,y+10*scale,'待核对，可留空',11);y+=50*scale
 else:
  card(y,130*scale);text(30,y+14,'参考报价',16,bold=True);wrap(30,y+50,'关闭分别导入时，仍按单件商品进入编辑。',13)
 dock=104 if scale==1 else 170;bottom=h if state!='keyboard' else h-220
 d.rectangle((0,bottom-dock,width,bottom),fill=rgb('surface'))
 label='核对并进入服饰编辑' if state=='off' else '请至少选择一个组合' if state=='empty' else '核对并逐件编辑（2件）'
 d.rounded_rectangle((16,bottom-dock+13,width-16,bottom-20),radius=c['buttonRadius'],fill=rgb('divider' if state=='empty' else 'accent'));wrap(28,bottom-dock+28,label,14,'onPrimary')
 if state=='keyboard':
  d.rectangle((0,bottom,width,h),fill='#292b34')
  for row,label in enumerate(['1     2     3','4     5     6','7     8     9','.     0     ✓']):d.text((38,bottom+16+row*48),label,font=ImageFont.truetype(FONT,25),fill='white')
 return im
for mode in ['light','dark']:
 board=Image.new('RGB',(7*380,950),'#e9e6e5');d=ImageDraw.Draw(board)
 for i,t in enumerate(THEMES):d.text((i*380+10,10),t['name']+' · '+mode,font=ImageFont.truetype(BOLD,17),fill='#252129');board.paste(page(t,mode),(i*380+10,42))
 board.save(OUT/f'design-{mode}.png')
board=Image.new('RGB',(4*380,950),'#e9e6e5');d=ImageDraw.Draw(board)
for i,(state,label,width,scale) in enumerate([('off','关闭：原单件',360,1),('empty','开启：空选',360,1),('selected','320dp / 2x · 滚动',320,2),('keyboard','键盘 / 主要操作',320,2)]):
 d.text((i*380+10,10),label,font=ImageFont.truetype(BOLD,17),fill='#252129');board.paste(page(THEMES[0],'light',state,width,scale),(i*380+10,42))
board.save(OUT/'design-states.png');print('Preimplementation designs rendered')
