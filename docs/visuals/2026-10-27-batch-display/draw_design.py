"""前置界面设计；插画为布局示意，不是实际生图结果。"""
from PIL import Image, ImageDraw, ImageFont
from pathlib import Path

OUT = Path(__file__).parent
FONT = '/System/Library/Fonts/STHeiti Light.ttc'
SKINS = [('甜美','#fff6f8','#c34f79',20),('哥特','#f1edf4','#6d4787',3),
         ('中华','#faf3ec','#a64d43',10),('古典','#f7f3eb','#8b6b41',8),
         ('海军','#edf4fa','#285778',12),('田园','#f3f6eb','#647b47',24),
         ('维多利亚','#f7eff4','#905b79',28)]

def phone(name,bg,accent,radius,dark=False,phase=0):
    im=Image.new('RGB',(390,844),'#19191c' if dark else bg);d=ImageDraw.Draw(im)
    fg='#f1edf1' if dark else '#302b32';muted='#c0b9c4' if dark else '#756c77'
    surface='#29262e' if dark else '#ffffff'
    def text(x,y,s,size=15,color=fg):d.text((x,y),s,font=ImageFont.truetype(FONT,size),fill=color)
    def card(y,h):d.rounded_rectangle((16,y,374,y+h),radius,fill=surface,outline=accent,width=1)
    def button(y,label):d.rounded_rectangle((16,y,374,y+46),radius,fill=accent);text(32,y+14,label,16,'#ffffff')
    text(20,16,'9:41',12);text(20,50,'‹',26);text(54,54,'批量展示图',18);text(314,54,'设置',14)
    labels=['选物品','核对结果','已保存']
    for j,label in enumerate(labels):
        x=16+j*121;d.rounded_rectangle((x,98,x+112,134),radius,fill=accent if j==phase else surface);text(x+22,109,label,15,'#fff' if j==phase else muted)
    card(150,164);text(30,166,'按物品类型生成',18,accent)
    text(30,199,'裙装、衬衫使用人台；其他物品用主视图。',14)
    d.rounded_rectangle((30,230,360,272),10,outline=muted);text(44,244,'搜索名称／品牌／类型',14,muted)
    text(30,287,'全部品类　⌄                 已选 2 件',14)
    for j,(title,mode) in enumerate([('奶油花园 JSK','正面人台图'),('蝴蝶结玛丽珍鞋','单品主视图')]):
        x=16+j*183
        d.rounded_rectangle((x,330,x+175,666),radius,fill=surface)
        text(x+12,344,title,15)
        d.rounded_rectangle((x+10,378,x+165,560),12,fill='#eae6e5')
        cx=x+87
        if j==0:
            d.ellipse((cx-17,390,cx+17,422),fill='#d2c8b9')
            d.polygon([(cx-28,414),(cx+28,414),(cx+20,460),(cx+64,535),(cx-64,535),(cx-20,460)],fill='#a78698')
            d.line([(cx-22,415),(cx-20,459),(cx+20,459),(cx+22,415)],fill='#f5ead9',width=5)
            for sy in [487,505,526]:d.arc((cx-60,sy-25,cx+60,sy+18),0,180,fill='#e9dacd',width=4)
            for dx,dy in [(-27,477),(15,482),(-11,508),(39,520)]:
                d.ellipse((cx+dx,dy,cx+dx+8,dy+8),fill='#f4dfd6')
        else:
            for dy,dx in [(0,0),(47,-8)]:
                d.rounded_rectangle((cx-55+dx,441+dy,cx+53+dx,471+dy),13,fill='#4a3541')
                d.arc((cx-25+dx,413+dy,cx+25+dx,459+dy),180,360,fill='#4a3541',width=10)
                d.polygon([(cx-1+dx,439+dy),(cx-21+dx,427+dy),(cx-21+dx,450+dy),(cx+1+dx,439+dy),(cx+22+dx,427+dy),(cx+22+dx,450+dy)],fill='#9c7588')
        text(x+12,575,mode,14,accent)
        text(x+12,603,'✓  本次处理' if phase==0 else '✓  已核对结果' if phase==1 else '已追加至物品相册',14)
        text(x+12,634,'调整参考图' if phase==0 else '对照原图' if phase==1 else '设为封面',14,accent)
    text(20,687,'每件一张 · 保留原图 · 不自动替换封面',13,muted)
    if phase==1:text(20,713,'✓ 组成完整　✓ 颜色造型正确　✓ 无多余物品',13)
    text(20,753,'发送前核对实际图片与费用' if phase==0 else '本地保存不计费',13,muted)
    button(782,['核对 2 件输入与费用','确认并保存 2 张展示图','继续整理其他物品'][phase])
    return im

flow=Image.new('RGB',(1218,900),'#e8e4e9');d=ImageDraw.Draw(flow)
for j in range(3):flow.paste(phone(*SKINS[0],phase=j),(12+402*j,42))
d.text((14,10),'前置界面图 · 输入 → 集中核对 → 相册保存（插画示意）',font=ImageFont.truetype(FONT,20),fill='#413849')
flow.save(OUT/'flow.png')
for dark in [False,True]:
    board=Image.new('RGB',(2814,900),'#17151b' if dark else '#e8e4e9');d=ImageDraw.Draw(board)
    for j,s in enumerate(SKINS):
        board.paste(phone(*s,dark=dark,phase=1),(12+j*400,42));d.text((24+j*400,10),s[0]+(' · 暗' if dark else ' · 浅'),font=ImageFont.truetype(FONT,20),fill='#eee' if dark else '#413849')
    board.save(OUT/('skins-dark.png' if dark else 'skins-light.png'))
