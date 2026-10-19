"""Preimplementation art boards. Illustrated products are controlled design examples."""
from pathlib import Path
import json, math
from PIL import Image, ImageDraw, ImageFont, ImageOps
ROOT=Path(__file__).resolve().parents[3];OUT=Path(__file__).resolve().parent
THEMES=json.loads((ROOT/'docs/visuals/complete-wardrobe-2026-10-18/skin-tokens.json').read_text())['themes']
FONT='/System/Library/Fonts/STHeiti Light.ttc';BOLD='/System/Library/Fonts/STHeiti Medium.ttc'

def draw(theme, mode, page, width=360, scale=1):
    c={**theme['modes']['light'],**theme['modes'][mode]};rgb=lambda key:tuple(c[key][:3]);height=900
    im=Image.new('RGBA',(width,height),rgb('background'))
    art=ImageOps.fit(Image.open(ROOT/f"app/src/main/res/drawable-nodpi/{c['art']}.png").convert('RGBA'),im.size)
    if mode=='dark':
        tint=Image.new('RGBA',im.size,tuple(c['artTint'][:3])+(255,));tint.putalpha(art.getchannel('A'));art=tint
    art.putalpha(art.getchannel('A').point(lambda a:round(a*c['artAlpha'])));im=Image.alpha_composite(im,art).convert('RGB');d=ImageDraw.Draw(im)
    def text(x,y,value,size=14,key='onSurface',bold=False):
        d.text((x,y),value,font=ImageFont.truetype(BOLD if bold else FONT,round(size*scale)),fill=rgb(key))
    def wrap(x,y,value,size=13,key='onSurface',maxw=None):
        font=ImageFont.truetype(FONT,round(size*scale));rows=['']
        for char in value:
            if d.textlength(rows[-1]+char,font=font)>(maxw or width-x-22):rows.append(char)
            else:rows[-1]+=char
        for i,row in enumerate(rows):text(x,y+i*size*scale*1.5,row,size,key)
        return y+len(rows)*size*scale*1.5
    def card(x,y,w,h):d.rounded_rectangle((x,y,x+w,y+h),radius=c['cardRadius'],fill=rgb('card'),outline=rgb('divider'))
    def chip(x,y,label,on=False):
        f=ImageFont.truetype(FONT,round(12*scale));w=d.textlength(label,font=f)+22;h=32*scale
        d.rounded_rectangle((x,y,x+w,y+h),radius=c['rSmall'],fill=rgb('accent' if on else 'surface'),outline=rgb('accent'))
        text(x+11,y+8*scale,label,12,'onPrimary' if on else 'onSurface');return x+w+8
    def illustration(box,kind):
        x,y,w,h=box;paper=rgb('surface');ink=rgb('accent');d.rounded_rectangle((x,y,x+w,y+h),radius=c['rSmall'],fill=paper)
        def pts(values):return [(x+a*w,y+b*h) for a,b in values]
        if kind=='shoe':
            p=pts([(.12,.65),(.25,.4),(.42,.48),(.51,.63),(.79,.61),(.88,.7),(.87,.77),(.14,.77)])
            d.polygon(p,fill=ink);d.arc((x+w*.2,y+h*.32,x+w*.61,y+h*.78),190,360,fill=ink,width=4)
            d.line(pts([(.14,.79),(.87,.79)]),fill=rgb('divider'),width=3)
        elif kind=='dress':
            d.polygon(pts([(.35,.12),(.65,.12),(.7,.3),(.62,.43),(.88,.88),(.12,.88),(.38,.43),(.3,.3)]),fill=ink)
            for z in [.45,.53,.79]:d.line(pts([(.3,z),(.7,z)]),fill=paper,width=2)
        else:
            for ox in [0,.3]:
                p=pts([(.2+ox,.12),(.37+ox,.12),(.37+ox,.6),(.51+ox,.76),(.49+ox,.85),(.31+ox,.86),(.2+ox,.69)])
                d.polygon(p,fill=ink)
                for yy in [.3,.45,.57]:d.ellipse((x+w*(.25+ox),y+h*yy,x+w*(.29+ox),y+h*(yy+.04)),fill=paper)
    text(16,8,'9:41',10)
    for yy in range(30,88):
        a,b=c['gradient'];p=(yy-30)/58;d.line((0,yy,width,yy),fill=tuple(round(a[k]*(1-p)+b[k]*p) for k in range(3)))
    text(16,47,'‹',24,'topBarForeground');text(47,48,'选择试衣物品' if page=='picker' else '生成单品展示图',17,'topBarForeground',True)
    y=108
    if page=='picker':
        y=wrap(18,y,'可选择已拥有与心愿物品，包含鞋履、袜装和挂载小物；筛选保留已选。',12)+14
        d.rounded_rectangle((16,y,width-16,y+48*scale),radius=c['rSmall'],fill=rgb('surface'),outline=rgb('divider'))
        text(29,y+14*scale,'搜索物品名称',13);y+=48*scale+12
        x=16
        for label,on in [('全部',True),('已拥有',False),('心愿',False)]:x=chip(x,y,label,on)
        y+=42*scale;x=16
        for label,on in [('全部类型',True),('鞋履',False),('袜装',False)]:x=chip(x,y,label,on)
        y+=46*scale;y=wrap(18,y,'已选3件 · 筛选后仍保留',13)+12
        cols=2;gap=12;w=(width-32-gap)/2;h=210*scale
        for i,(name,kind,status) in enumerate([('主裙（示意）','dress','已拥有'),('玛丽珍鞋（示意）','shoe','心愿'),('印花连裤袜（示意）','sock','心愿')]):
            x=16+(i%cols)*(w+gap);yy=y+(i//cols)*(h+12);card(x,yy,w,h)
            illustration((x+5,yy+5,w-10,120*scale),kind);text(x+12,yy+132*scale,name,11)
            wrap(x+12,yy+155*scale,status+' · 已选择',11,maxw=w-24)
        action='完成搭配选择（3件）'
    else:
        card(16,y,width-32,118*scale);yy=wrap(30,y+14,'选择一张原图',16)+10
        wrap(30,yy,'可从商品图或详情图中选一张；长图可框选需要生成的单品。',12,maxw=width-60);y+=118*scale+14
        x=chip(16,y,'商品图（2）');chip(x,y,'详情图（4）',True);y+=45*scale
        y=wrap(18,y,'当前选择：详情图2',13)+12
        card(16,y,width-32,285*scale);illustration((27,y+10,width-54,195*scale),'sock')
        text(30,y+216*scale,'● 详情图2 · 已选',14,'accent',True);text(30,y+247*scale,'放大查看     框选要生成的单品',11,'accent');y+=300*scale
        card(16,y,width-32,140*scale);illustration((28,y+8,80,120*scale),'sock');wrap(123,y+20,'详情图3',14)
        action='核对费用并生成'
    dock=100*scale;d.rectangle((0,height-dock,width,height),fill=rgb('surface'))
    d.rounded_rectangle((16,height-dock+14,width-16,height-24),radius=c['buttonRadius'],fill=rgb('accent'))
    wrap(27,height-dock+30,action,14,'onPrimary')
    return im

OUT.mkdir(exist_ok=True,parents=True)
for page in ['picker','sources']:
    for mode in ['light','dark']:
        board=Image.new('RGB',(7*380,950),(236,233,230));d=ImageDraw.Draw(board)
        for i,t in enumerate(THEMES):
            d.text((i*380+12,12),t['name']+' · '+mode,font=ImageFont.truetype(BOLD,17),fill=(40,38,45));board.paste(draw(t,mode,page),(i*380+10,45))
        board.save(OUT/f'design-{page}-{mode}.png')
board=Image.new('RGB',(2*340,950),(236,233,230));d=ImageDraw.Draw(board)
for i,page in enumerate(['picker','sources']):
    d.text((i*340+10,12),'320dp / 2x · 内容滚动',font=ImageFont.truetype(BOLD,17),fill=(40,38,45));board.paste(draw(THEMES[0],'light',page,320,2),(i*340+10,45))
board.save(OUT/'design-small.png')
print('28 preimplementation skin panels and 2 small-screen studies rendered.')
