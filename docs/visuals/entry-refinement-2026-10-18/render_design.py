"""Offline drawing of the preimplementation UI design; no browser or network access."""
from pathlib import Path
import json,re,math
from PIL import Image,ImageDraw,ImageFont,ImageOps
ROOT=Path(__file__).resolve().parents[3]
OUT=Path(__file__).resolve().parent
themes=json.loads((ROOT/'docs/visuals/complete-wardrobe-2026-10-18/skin-tokens.json').read_text())['themes']
FONT='/System/Library/Fonts/STHeiti Light.ttc'
BOLD='/System/Library/Fonts/STHeiti Medium.ttc'
states=['登记','识图选图','确认发送','核对结果','识别未完成','房间柜架','键盘保存']
def rgb(a):return tuple(a[:3])
def phone(theme,mode,state,width=360,scale=1):
 c={**theme['modes']['light'],**theme['modes'][mode]};c['outlineVariant']=c['divider'];im=Image.new('RGB',(width,800),rgb(c['background']))
 art=ImageOps.fit(Image.open(ROOT/f"app/src/main/res/drawable-nodpi/{c['art']}.png").convert('RGBA'),im.size)
 if mode=='dark':
  tint=Image.new('RGBA',im.size,rgb(c['artTint'])+(255,));tint.putalpha(art.getchannel('A'));art=tint
 art.putalpha(art.getchannel('A').point(lambda a:round(a*c['artAlpha'])));im=Image.alpha_composite(im.convert('RGBA'),art).convert('RGB');d=ImageDraw.Draw(im)
 ink=rgb(c['onSurface']);muted=rgb(c['onBackground']);accent=rgb(c['accent']);surface=rgb(c['surface']);card=rgb(c['card']);radius=c['cardRadius'];y=104
 def txt(x,y,text,size=14,color=ink,bold=False):
  f=ImageFont.truetype(BOLD if bold else FONT,round(size*scale));d.text((x,y),text,font=f,fill=color);return f
 def lines(text,size,maxw):
  f=ImageFont.truetype(FONT,round(size*scale));rows=['']
  for ch in text:
   if ch=='\n':rows.append('');continue
   if d.textlength(rows[-1]+ch,font=f)>maxw:rows.append(ch)
   else:rows[-1]+=ch
  return rows
 def wrap(x,yy,text,size=13,color=muted,maxw=None):
  rows=lines(text,size,maxw or width-2*x)
  for i,t in enumerate(rows):txt(x,yy+i*size*scale*1.5,t,size,color)
  return yy+len(rows)*size*scale*1.5
 def box(yy,h):d.rounded_rectangle((16,yy,width-16,yy+h),radius=radius,fill=card,outline=rgb(c['outlineVariant']))
 def button(yy,label,primary=False):
  h=max(46,22+19*scale);d.rounded_rectangle((16,yy,width-16,yy+h),radius=c['buttonRadius'],fill=accent if primary else surface,outline=accent)
  f=ImageFont.truetype(BOLD,round(14*scale));tw=d.textlength(label,font=f);d.text(((width-tw)/2,yy+(h-16*scale)/2),label,font=f,fill=rgb(c['onPrimary']) if primary else accent)
 def field(label,value):
  nonlocal y
  h=25+32*scale;d.rounded_rectangle((16,y,width-16,y+h),radius=c['rSmall'],fill=surface,outline=rgb(c['outline']));txt(28,y+6,label,10,muted);txt(28,y+14+15*scale,value,15);y+=h+12
 def fold(label,summary='未填写'):
  nonlocal y
  h=48+13*(scale-1);box(y,h);txt(30,y+14,label,14);f=ImageFont.truetype(FONT,round(11*scale));txt(width-42-d.textlength(summary,font=f),y+16,summary,11,muted);txt(width-32,y+13,'⌄',14,accent);y+=h+12
 def picture(yy,h):
  box(yy,h);cx=width/2;top=yy+18;s=(h-32)/156
  points=[(cx+(x-60)*s,top+(z-10)*s) for x,z in [(41,16),(79,16),(88,46),(77,75),(106,142),(82,150),(38,150),(14,142),(43,75),(32,46)]]
  fill=tuple(round((a+b*3)/4) for a,b in zip(accent,surface));d.polygon(points,fill=fill,outline=accent,width=2)
  for a,b in [((43,75),(77,75)),((43,78),(30,140)),((77,78),(90,140)),((47,50),(73,50)),((48,60),(72,60))]:d.line((cx+(a[0]-60)*s,top+(a[1]-10)*s,cx+(b[0]-60)*s,top+(b[1]-10)*s),fill=accent,width=2)
  txt(width-112,yy+h-22,'商品图示意',10,muted)
 for yy in range(28,87):
  f=(yy-28)/59;a,b=c['gradient'];col=tuple(round(a[k]*(1-f)+b[k]*f) for k in range(3));d.line((0,yy,width,yy),fill=col)
 txt(17,8,'9:41',10);txt(width-62,8,'▮▮ ▰',10)
 title='添加物品' if state in ['登记','键盘保存'] else '卧室' if state=='房间柜架' else '核对识别结果' if state=='核对结果' else '识图填入'
 txt(16,45,'‹',24,rgb(c['topBarForeground']));txt(46,45,title,18,rgb(c['topBarForeground']),True)
 if state in ['登记','键盘保存']:
  picture(y,130);y+=138;txt(26,y,'＋ 添加照片',12,accent);txt(width-115,y,'识图填入 ›',12,accent);y+=32
  field('名称 *','草莓花园 JSK');field('类型 *','JSK')
  if scale<1.6:
   y=wrap(18,y,'名称和类型填好即可保存。',12)+10;box(y,118)
   for i,(k,v) in enumerate([('尺码','M'),('颜色','浅粉'),('收纳位置','卧室 › 衣柜')]):txt(29,y+12+i*34,k,13);txt(width-130,y+12+i*34,v,12,muted)
   y+=130;fold('规格与备注');fold('补充资料','按需填写')
 elif state=='识图选图':
  y=wrap(18,y,'选择要识别的图片',18,ink)+13;y=wrap(18,y,'商品图、参数图或尺码表都可以。')+15;button(y,'从相册选图');y+=67;y=wrap(18,y,'或使用已有图片')+12;picture(y,170);y+=182;txt(20,y,'使用这张图片 ›',14,accent);y+=45;y=wrap(18,y,'默认整图；框选和旋转可按需使用。')
 elif state in ['确认发送','识别未完成']:
  y=wrap(18,y,'将发送这张图片',17,ink)+12;picture(y,220);y+=232;txt(22,y,'框选 / 旋转',13,accent);txt(width-100,y,'更换图片',13,accent);y+=39
  if state=='确认发送':
   y=wrap(18,y,'仅发送上方图片，原图保留。请确认没有不需要上传的内容。')+16;explain='按你的账户计费，不会自动重试。核对建议后填入草稿，最后由你保存。';h=28+28*scale+len(lines(explain,12,width-56))*18*scale;box(y,h);txt(28,y+12,'识别服务：豆包',14,ink,True);wrap(28,y+20+22*scale,explain,12,maxw=width-56)
  else:
   y=wrap(18,y,'未能读取识别结果',18,rgb(c['error']))+16;y=wrap(18,y,'豆包已返回内容，但格式未能读取。原选图已保留，可重新识别或手动填写。',14)+18;y=wrap(18,y,'不会自动再次计费，也可以先保存物品。',12)
 elif state=='核对结果':
  y=wrap(18,y,'选好后一起填入，不会直接保存物品。')+17
  for i,(k,v,on) in enumerate([('名称','草莓花园 JSK',True),('类型','JSK  ›',False),('颜色','浅粉色',True),('尺码','M',True)]):
   h=76+22*(scale-1);box(y,h);d.rounded_rectangle((28,y+23,48,y+43),radius=4,fill=accent if on else surface,outline=accent)
   if on:d.line((32,y+33,36,y+38,44,y+28),fill=rgb(c['onPrimary']),width=2)
   txt(59,y+10,k,11,muted);txt(59,y+31*scale,v,15,ink,True);y+=h+8
  if scale<1.6:fold('详细规格','2 项待核对');wrap(18,y,'单位或口径不清的值暂不填入，可查看原图后手动补充。',12)
 else:
  box(y,84);txt(28,y+14,'卧室',17,ink,True);txt(28,y+47,'23 件物品 · 2 个收纳位置',12,muted);y+=99
  field('搜索','搜索物品')
  for label in ['＋ 新增物品','＋ 新增柜架 / 收纳盒','移入物品']:button(y,label);y+=max(46,22+19*scale)+10
  y=wrap(18,y,'添加常用收纳 ›',12,accent)+20;txt(18,y,'柜架与收纳盒',15,ink,True);y+=32;fold('白色衣柜','18 件');fold('配饰收纳盒','5 件')
 # A pinned primary action remains above the keyboard or navigation; body scrolls.
 if state!='房间柜架':
  keyboard=180 if state=='键盘保存' else 0;bottom=800-keyboard;h=max(46,22+19*scale);dockh=h+30
  d.rectangle((0,bottom-dockh,width,bottom),fill=surface);d.line((0,bottom-dockh,width,bottom-dockh),fill=rgb(c['outlineVariant']))
  labels={'登记':'保存物品','键盘保存':'保存物品','识图选图':'先选择图片','确认发送':'确认发送并识别','核对结果':'填入 3 项并返回','识别未完成':'重新识别'}
  button(bottom-dockh+12,labels[state],True)
  if keyboard:
   d.rectangle((0,bottom,width,800),fill=surface)
   for row,letters in enumerate(['QWERTYUIOP','ASDFGHJKL','123   空格   完成']):txt(15,bottom+19+row*43,'  '.join(letters),15)
 return im
for mode in ['light','dark']:
 for state in states:
  sheet=Image.new('RGB',(7*376,850),(236,233,230));d=ImageDraw.Draw(sheet)
  for i,t in enumerate(themes):
   d.text((i*376+13,10),f"{t['name']} · {state}",font=ImageFont.truetype(BOLD,16),fill=(35,35,40));sheet.paste(phone(t,mode,state),(i*376+8,42))
  sheet.save(OUT/f'{mode}-{states.index(state)+1}.png')
selected=Image.new('RGB',(4*344,850),(236,233,230));d=ImageDraw.Draw(selected)
for i,state in enumerate(['登记','确认发送','核对结果','房间柜架']):
 d.text((i*344+12,9),f'320dp / 2x · {state}',font=ImageFont.truetype(BOLD,16),fill=(40,40,40));selected.paste(phone(themes[0],'light',state,320,2),(i*344+8,42))
selected.save(OUT/'small-large-font.png')
print('Rendered 98 offline UI design panels and the 320dp/2x layout study; product UI unchanged.')
