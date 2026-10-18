"""Preimplementation drawings. No network, private photos, or application rendering."""
from pathlib import Path
import json
from PIL import Image, ImageDraw, ImageFont, ImageOps

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent
THEMES = json.loads((ROOT / 'docs/visuals/complete-wardrobe-2026-10-18/skin-tokens.json').read_text())['themes']
FONT = '/System/Library/Fonts/STHeiti Light.ttc'
BOLD = '/System/Library/Fonts/STHeiti Medium.ttc'
STATES = ['衬衫详情与封面', '衬衫人台工作页', '生成确认']


def phone(theme, mode, state, width=360, scale=1):
    c = {**theme['modes']['light'], **theme['modes'][mode]}
    rgb = lambda value: tuple(value[:3])
    image = Image.new('RGBA', (width, 800), rgb(c['background']) + (255,))
    art = ImageOps.fit(Image.open(ROOT / f"app/src/main/res/drawable-nodpi/{c['art']}.png").convert('RGBA'), image.size)
    if mode == 'dark':
        tint = Image.new('RGBA', image.size, rgb(c['artTint']) + (255,))
        tint.putalpha(art.getchannel('A'))
        art = tint
    art.putalpha(art.getchannel('A').point(lambda a: round(a * c['artAlpha'])))
    image = Image.alpha_composite(image, art).convert('RGB')
    draw = ImageDraw.Draw(image)
    ink, accent, surface, card = map(rgb, [c['onSurface'], c['accent'], c['surface'], c['card']])
    muted = rgb(c['onBackground'])

    def text(x, y, value, size=14, color=ink, bold=False):
        draw.text((x, y), value, font=ImageFont.truetype(BOLD if bold else FONT, round(size * scale)), fill=color)

    def wrap(x, y, value, size=13, color=muted, limit=None):
        font = ImageFont.truetype(FONT, round(size * scale))
        rows = ['']
        for ch in value:
            if ch == '\n' or draw.textlength(rows[-1] + ch, font=font) > (limit or width - 2 * x):
                rows.append('')
                if ch == '\n':
                    continue
            rows[-1] += ch
        for i, row in enumerate(rows):
            text(x, y + i * size * scale * 1.5, row, size, color)
        return y + len(rows) * size * scale * 1.5

    def box(y, h):
        draw.rounded_rectangle((16, y, width - 16, y + h), radius=c['cardRadius'], fill=card, outline=rgb(c['divider']))

    def button(y, label, primary=True):
        h = 28 + 21 * scale
        draw.rounded_rectangle((16, y, width - 16, y + h), radius=c['buttonRadius'], fill=accent if primary else surface, outline=accent)
        font = ImageFont.truetype(BOLD, round(14 * scale))
        draw.text(((width - draw.textlength(label, font=font)) / 2, y + 12), label, font=font, fill=rgb(c['onPrimary']) if primary else accent)
        return y + h

    def blouse(y, h, generated=True):
        box(y, h)
        cx = width / 2
        s = min((h - 36) / 200, (width - 55) / 210)
        pts = [(0, 12), (-32, 20), (-68, 38), (-98, 118), (-70, 130), (-43, 75), (-43, 175), (-29, 184), (29, 184), (43, 175), (43, 75), (70, 130), (98, 118), (68, 38), (32, 20)]
        if generated:
            draw.ellipse((cx - 9*s, y + 12, cx + 9*s, y + 12 + 10*s), fill=rgb(c['outline']))
            draw.rectangle((cx - 6*s, y + 17, cx + 6*s, y + 30), fill=rgb(c['outline']))
        xy = [(cx + x*s, y + 20 + yy*s) for x, yy in pts]
        fill = tuple(round((a + 4*b) / 5) for a, b in zip(accent, surface))
        draw.polygon(xy, fill=fill, outline=accent, width=2)
        for p in [[(0,12),(-20,31),(-8,57),(0,39),(8,57),(20,31),(0,12)],[(0,43),(0,177)],[(-43,160),(43,160)],[(-43,169),(43,169)],[(-93,108),(-71,120)],[(93,108),(71,120)]]:
            draw.line([(cx+x*s,y+20+yy*s) for x,yy in p],fill=accent,width=2)
        for yy in [67,88,109,130,151]:
            draw.ellipse((cx+3*s,y+18+yy*s,cx+6*s,y+21+yy*s),fill=accent)
        text(27,y+h-22,'人台图示意' if generated else '衬衫原图示意',10,muted)

    for y in range(28,87):
        f=(y-28)/59; a,b=c['gradient']
        draw.line((0,y,width,y),fill=tuple(round(a[k]*(1-f)+b[k]*f) for k in range(3)))
    text(16,8,'9:41',10)
    text(16,43,'‹',24,rgb(c['topBarForeground']))
    text(46,46,'衬衫详情' if state==0 else 'AI 展示图',18,rgb(c['topBarForeground']),True)
    y=104
    if state==0:
        blouse(y,258); y+=269
        text(20,y,'2 / 2',12,muted)
        f=ImageFont.truetype(BOLD,round(14*scale))
        text(width-20-draw.textlength('设为封面',font=f),y,'设为封面',14,accent,True); y+=42
        box(y,89 if scale==1 else 124); text(28,y+12,'奶油色蕾丝衬衫',16,ink,True)
        wrap(28,y+30+16*scale,'类型：衬衫 · 尺码 M',12,limit=width-56)
        y+=105 if scale==1 else 140
        button(y,'生成衬衫人台图',False); y+=65
        y=wrap(20,y,'设为封面只调整相册顺序。原图、生成来源和未完成编辑草稿均保留。',12)
    elif state==1:
        box(y,130 if scale==1 else 230)
        text(28,y+12,'奶油色蕾丝衬衫',16,ink,True)
        wrap(28,y+26+18*scale,'仅将衬衫穿到固定无杆人台上，保留领口、袖型和下摆，下身留空。',13,limit=width-56)
        y+=146 if scale==1 else 246
        blouse(y,210,False); y+=222
        text(24,y,'◉ 参考图片 1',14,ink); y+=41
        box(y,95); text(28,y+12,'图像服务',15,ink,True); text(28,y+47,'Seedream · 已配置',13,muted); y+=110
        if scale==1:
            box(y,74); text(28,y+11,'补充说明（可选）',13,muted); text(28,y+38,'例如：保留原袖口与领结',13,muted)
        dock=800-(86 if scale==1 else 150)
        draw.rectangle((0,dock,width,800),fill=surface)
        wrap(18,dock+8,'原图 + 固定人台，共2张；API单独计费。',11)
        button(800-(28+21*scale)-10,'生成正面人台图')
    else:
        text(20,y,'确认生成正面人台图？',17,ink,True); y+=45
        blouse(y,230,False); y+=245
        text(22,y,'分类：衬衫',14); y+=35
        y=wrap(22,y,'将上传这张原图、固定无杆人台模板及补充说明至火山方舟，共2张图片，API单独计费。',14)+15
        y=wrap(22,y,'结果先在任务中心核对。确认添加后进入相册，设为封面由你另行选择。',13)+18
        button(min(y,700),'确认并加入任务')
    draw.rectangle((0,780,width,800),fill=surface)
    draw.rounded_rectangle((width/2-48,790,width/2+48,794),radius=2,fill=muted)
    return image


for mode in ['light','dark']:
    for state,label in enumerate(STATES):
        sheet=Image.new('RGB',(7*376,850),(237,234,231)); d=ImageDraw.Draw(sheet)
        for i,theme in enumerate(THEMES):
            d.text((i*376+12,10),f"{theme['name']} · {label}",font=ImageFont.truetype(BOLD,15),fill=(35,35,40))
            sheet.paste(phone(theme,mode,state),(i*376+8,42))
        sheet.save(OUT/f'{mode}-{state+1}.png')
sheet=Image.new('RGB',(4*336,850),(237,234,231)); d=ImageDraw.Draw(sheet)
for i,(mode,state) in enumerate([('light',0),('dark',0),('light',1),('dark',1)]):
    d.text((i*336+8,10),f'320dp / 2x · {STATES[state]}',font=ImageFont.truetype(BOLD,14),fill=(35,35,40))
    sheet.paste(phone(THEMES[-1],mode,state,320,2),(i*336+8,42))
sheet.save(OUT/'small-large-font.png')
print('Rendered preimplementation drawings: 42 panels plus 4 small/large-font studies.')
