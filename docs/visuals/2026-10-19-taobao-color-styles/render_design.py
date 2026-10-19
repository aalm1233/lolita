"""Preimplementation drawings; names are placeholders, not scraped product facts."""
from pathlib import Path
import json
from PIL import Image, ImageDraw, ImageFont, ImageOps

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent
THEMES = json.loads((ROOT / 'docs/visuals/complete-wardrobe-2026-10-18/skin-tokens.json').read_text())['themes']
FONT = '/System/Library/Fonts/STHeiti Light.ttc'
BOLD = '/System/Library/Fonts/STHeiti Medium.ttc'

def screen(theme, mode, state='selected', width=360, scale=1):
    c = {**theme['modes']['light'], **theme['modes'][mode]}
    rgb = lambda key: tuple(c[key][:3])
    h = 930 if scale == 1 else 1260
    im = Image.new('RGBA', (width, h), rgb('background'))
    art = ImageOps.fit(Image.open(ROOT / f"app/src/main/res/drawable-nodpi/{c['art']}.png").convert('RGBA'), im.size)
    if mode == 'dark':
        tint = Image.new('RGBA', im.size, tuple(c['artTint'][:3]) + (255,))
        tint.putalpha(art.getchannel('A')); art = tint
    art.putalpha(art.getchannel('A').point(lambda a: round(a*c['artAlpha'])))
    im = Image.alpha_composite(im, art).convert('RGB'); d = ImageDraw.Draw(im)
    def text(x, y, value, size=14, key='onSurface', bold=False):
        d.text((x,y),value,font=ImageFont.truetype(BOLD if bold else FONT,round(size*scale)),fill=rgb(key))
    def wrap(x,y,value,size=13,key='onSurface',maxw=None):
        font=ImageFont.truetype(FONT,round(size*scale)); lines=['']
        for char in value:
            if d.textlength(lines[-1]+char,font=font) > (maxw or width-x-24): lines.append(char)
            else: lines[-1] += char
        for i,line in enumerate(lines): text(x,y+i*size*scale*1.55,line,size,key)
        return y+len(lines)*size*scale*1.55
    def card(y,height):
        d.rounded_rectangle((16,y,width-16,y+height),radius=c['cardRadius'],fill=rgb('card'),outline=rgb('divider'))
    def title(y,value):
        d.rounded_rectangle((28,y+2,31,y+20*scale),radius=1,fill=rgb('accent'))
        text(41,y,value,15,bold=True)
        return y+31*scale
    text(18,10,'9:41',10)
    for y in range(30,88):
        a,b=c['gradient'];p=(y-30)/58
        d.line((0,y,width,y),fill=tuple(round(a[k]*(1-p)+b[k]*p) for k in range(3)))
    text(17,46,'‹',22,'topBarForeground');text(46,47,'淘宝商品导入（试用）',16,'topBarForeground',True)
    y=104
    card(y,145*scale);yy=title(y+15,'核对商品资料')
    yy=wrap(28,yy,'摇篮时光 · 印花连裤袜',15)
    yy=wrap(28,yy+7,'类别：连裤袜',12)
    wrap(28,yy+7,'每件保留完整主图和详情原图。',12)
    y += 145*scale+14
    blockheight=(350 if state!='off' else 128)*scale
    card(y,blockheight); yy=title(y+15,'分别导入')
    yy=wrap(28,yy,'按颜色分类分别导入',14,maxw=width-114)
    on=state!='off'; sx=width-83;sy=y+48*scale
    d.rounded_rectangle((sx,sy,sx+43,sy+25),radius=13,fill=rgb('accent' if on else 'divider'))
    cx=sx+30 if on else sx+13;d.ellipse((cx-9,sy+4,cx+9,sy+22),fill=rgb('surface'))
    yy=wrap(28,max(yy+10,sy+35),'颜色分类可能包含不同款式；只勾选需要保存的款式，尺码不拆分。',12)
    if on:
        for i in range(4):
            yy+=13*scale; x=29; checked=state=='selected' and i in [0,2]
            d.rounded_rectangle((x,yy,x+20,yy+20),radius=3,fill=rgb('accent' if checked else 'surface'),outline=rgb('accent'))
            if checked:d.line((x+4,yy+10,x+8,yy+15,x+17,yy+5),fill=rgb('onPrimary'),width=2)
            yy=wrap(60,yy-1,f'款式 {i+1}（名称示意）',14)
            if i<3:d.line((60,yy+8,width-30,yy+8),fill=rgb('divider'))
    y += blockheight+14
    card(y,90*scale);yy=title(y+15,'商品参数');wrap(28,yy,'品牌、材质与原始规格继续保留。',12)
    y += 90*scale+16
    wrap(18,y,'进入编辑后逐件核对，保存到心愿；参考报价不代表实付。',12)
    dock=105*scale
    d.rectangle((0,h-dock,width,h),fill=rgb('surface'))
    label='核对并逐件编辑（2件）' if state=='selected' else '请至少选择一款' if state=='empty' else '核对并进入服饰编辑'
    d.rounded_rectangle((16,h-dock+14,width-16,h-dock+64*scale),radius=c['buttonRadius'],fill=rgb('accent' if state!='empty' else 'divider'))
    wrap(28,h-dock+26,label,13,'onPrimary')
    return im

OUT.mkdir(parents=True,exist_ok=True)
for mode in ['light','dark']:
    board=Image.new('RGB',(7*382,1000),(235,232,229));d=ImageDraw.Draw(board)
    for i,theme in enumerate(THEMES):
        d.text((i*382+12,12),theme['name']+' · '+mode,font=ImageFont.truetype(BOLD,17),fill=(40,38,45))
        board.paste(screen(theme,mode),(i*382+10,48))
    board.save(OUT/f'design-{mode}.png')
board=Image.new('RGB',(4*382,1340),(235,232,229));d=ImageDraw.Draw(board)
for i,(state,width,scale) in enumerate([('off',360,1),('empty',360,1),('selected',360,1),('selected',320,1.65)]):
    label=['开关关闭','空选择禁用','仅选两款','小屏大字 · 内容滚动'][i]
    d.text((i*382+12,12),label,font=ImageFont.truetype(BOLD,17),fill=(40,38,45))
    board.paste(screen(THEMES[0],'light',state,width,scale),(i*382+10,48))
board.save(OUT/'design-states.png')
print('Preimplementation: 14 skin panels and 4 state studies.')
