"""前置界面设计，手绘服装仅为构图示意，不代表模型输出。"""
from PIL import Image, ImageDraw, ImageFont
from pathlib import Path
import runpy
OUT=Path(__file__).parent
SKINS=runpy.run_path(str(OUT.parent/'2026-10-27-batch-display/draw_design.py'))['SKINS']
FONT='/System/Library/Fonts/STHeiti Light.ttc'
def phone(name,bg,accent,radius,dark=False,viewer=False):
    im=Image.new('RGB',(390,844),'#19191c' if dark else bg);d=ImageDraw.Draw(im)
    fg='#f5f0f4' if dark else '#302b32';muted='#bdb5c2' if dark else '#756c77';surface='#29262e' if dark else '#ffffff'
    def txt(x,y,s,n=15,c=fg):d.text((x,y),s,font=ImageFont.truetype(FONT,n),fill=c)
    def button(box,s,filled=False):
        d.rounded_rectangle(box,radius,fill=accent if filled else surface,outline=accent)
        txt(box[0]+12,box[1]+12,s,15,'#ffffff' if filled else accent)
    def photo(box,result=False):
        x,y,r,b=box;d.rounded_rectangle(box,10,fill='#e9e6e4' if result else '#e9e1d8');cx=(x+r)//2;w=(r-x)*.37;h=(b-y)*.72;top=y+(b-y-h)/2
        if result:d.ellipse((cx-12,top-15,cx+12,top+20),fill='#d0c8b9')
        else:d.line((cx-40,top-8,cx,top-23,cx+40,top-8),fill='#958679',width=3)
        d.polygon([(cx-w*.45,top),(cx+w*.45,top),(cx+w*.32,top+h*.35),(cx+w,top+h),(cx-w,top+h),(cx-w*.32,top+h*.35)],fill='#977e98')
        for f in [.55,.74,.91]:d.line((cx-w*f,top+h*f,cx+w*f,top+h*f),fill='#f2e5dd',width=4)
        for dx,dy in [(-.3,.5),(.25,.65),(-.25,.84),(.5,.88)]:
            d.ellipse((cx+w*dx-4,top+h*dy-4,cx+w*dx+4,top+h*dy+4),fill='#e0b3bd')
    txt(20,16,'9:41',12);txt(20,53,'‹',26);txt(54,57,'对比展示图' if viewer else '批量展示图',18)
    if viewer:
        txt(20,106,'奶油花园 JSK',18);txt(20,139,'本次原图与生成图 · 同步缩放',14,muted)
        for j,s in enumerate(['并排','原图','生成图']):button((16+j*121,176,128+j*121,220),s,j==0)
        txt(24,264,'本次原图',14);txt(209,264,'生成图',14)
        photo((16,295,188,644));photo((202,295,374,644),True)
        for j,s in enumerate(['缩小','放大','还原']):button((16+j*121,676,128+j*121,722),s)
        txt(20,742,'双指缩放、拖动查看细节',14,muted);button((16,784,374,830),'查看完整原图')
    else:
        for j,s in enumerate(['选物品','核对结果','已保存']):button((16+j*121,98,128+j*121,139),s,j==1)
        d.rounded_rectangle((16,156,374,270),radius,fill=surface);txt(30,172,'核对展示图',18,accent);txt(30,204,'对照本次原图，核对后追加相册。',14)
        txt(30,238,'搜索物品　⌕　　　　　已选 1 件',14,muted)
        d.rounded_rectangle((16,286,374,683),radius,fill=surface);txt(30,304,'奶油花园 JSK',16)
        txt(30,336,'本次原图',14,muted);txt(207,336,'生成图',14,muted)
        photo((30,363,183,557));photo((207,363,360,557),True)
        txt(30,573,'裙装 · 单件　／　正面人台图',14,muted);button((30,603,360,647),'放大对比')
        txt(30,659,'☑  已查看此结果',14)
        txt(20,703,'下方继续核对组成、颜色与数量',14,muted);txt(20,753,'本地保存不计费',13,muted);button((16,782,374,830),'确认并保存 1 张展示图',True)
    return im
board=Image.new('RGB',(816,900),'#e8e4e9');d=ImageDraw.Draw(board)
d.text((16,10),'前置手绘 · 单件一行 → 同屏放大对照（插画示意）',font=ImageFont.truetype(FONT,20),fill='#413849')
for j in range(2):board.paste(phone(*SKINS[0],viewer=bool(j)),(12+j*402,42))
board.save(OUT/'flow.png')
for dark in [False,True]:
    board=Image.new('RGB',(2814,1776),'#17151b' if dark else '#e8e4e9');d=ImageDraw.Draw(board)
    for j,s in enumerate(SKINS):
        d.text((24+j*400,8),s[0]+(' · 暗' if dark else ' · 浅'),font=ImageFont.truetype(FONT,20),fill='#eeeeee' if dark else '#413849')
        for k in range(2):board.paste(phone(*s,dark=dark,viewer=bool(k)),(12+j*400,42+k*860))
    board.save(OUT/('skins-dark.png' if dark else 'skins-light.png'))
