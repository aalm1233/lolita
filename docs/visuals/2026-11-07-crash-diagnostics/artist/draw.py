"""Pre-implementation composition study; Pillow, not an Android screenshot."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).parent
FONT = '/System/Library/Fonts/STHeiti Medium.ttc'
font = lambda n: ImageFont.truetype(FONT, n)
names = ['甜美', '哥特', '中华', '古典', '海军', '田园', '维多利亚']
accents = ['#CB7695', '#A58BBD', '#A44C4C', '#A57C59', '#416C9B', '#6D8859', '#A68B59']
im = Image.new('RGB', (2240, 1060), '#ECEBEA'); d = ImageDraw.Draw(im)
d.text((24, 14), '前置界面图 · 个人页诊断导出 / 七皮肤浅暗（布局设计，非原生截图）', font=font(24), fill='#353239')
for row in range(2):
    for col, (name, accent) in enumerate(zip(names, accents)):
        x, y = col * 320 + 14, row * 500 + 58
        bg, fg, card, muted = ('#202025', '#F3EDEB', '#302E35', '#C6BDC7') if row else ('#FAF6F5', '#342D35', '#FFFFFF', '#706874')
        d.rounded_rectangle((x, y, x+292, y+475), radius=22, fill=bg, outline=accent, width=2)
        d.text((x+18, y+15), name+(' · 暗色' if row else ' · 浅色'), font=font(16), fill=accent)
        d.text((x+18, y+54), '个人', font=font(20), fill=fg)
        for j, (title, desc) in enumerate([('皮肤选择', '切换应用主题风格'), ('检查更新', '当前版本'), ('导出诊断记录', '设备、异常位置与内存状态\n不含衣橱内容')]):
            yy = y+103+j*100
            d.rounded_rectangle((x+12, yy, x+280, yy+88), radius=[16,4,8,10,6,18,6][col], fill=card, outline=accent if j==2 else None)
            d.rounded_rectangle((x+25, yy+20, x+61, yy+56), radius=5, fill=bg)
            d.line([(x+34,yy+28),(x+45,yy+28),(x+52,yy+35),(x+52,yy+49),(x+34,yy+49),(x+34,yy+28)], fill=accent, width=2)
            d.text((x+74,yy+15), title, font=font(18), fill=fg)
            d.multiline_text((x+74,yy+43), desc, font=font(13), fill=muted, spacing=4)
            d.line([(x+260,yy+35),(x+267,yy+42),(x+260,yy+49)], fill=muted, width=2)
        d.text((x+19,y+424), '点击 → 系统保存位置 → 保存 / 取消', font=font(14), fill=accent)
im.save(ROOT/'seven-skins-light-dark.png')
im = Image.new('RGB', (1060,860), '#F1EEEE'); d=ImageDraw.Draw(im)
d.text((24,20), '320dp · 两倍字体 / 系统保存交互', font=font(26), fill='#342D35')
x,y=25,76
d.rounded_rectangle((x,y,x+448,y+750), radius=20, fill='#FAF6F5', outline='#CB7695', width=2)
d.text((x+22,y+28), '个人', font=font(34), fill='#342D35')
d.rounded_rectangle((x+18,y+118,x+430,y+450), radius=20, fill='white')
d.multiline_text((x+96,y+146), '导出诊断\n记录', font=font(40), fill='#342D35', spacing=10)
d.multiline_text((x+96,y+265), '设备、异常位置\n与内存状态；\n不含衣橱内容', font=font(30), fill='#706874', spacing=14)
d.text((520,120), '系统文件选择器', font=font(28), fill='#342D35')
for yy,title,desc in [(210,'保存','导出为 TXT；原记录保留'),(370,'取消','不导出，回到个人页'),(530,'写入失败','提示重试；原记录保留')]:
    d.rounded_rectangle((515,yy,1020,yy+120),radius=16,fill='white')
    d.text((535,yy+18),title,font=font(25),fill='#A04C70')
    d.text((535,yy+66),desc,font=font(21),fill='#655A65')
d.text((520,730),'无自动上传，无衣橱 / 图片内容',font=font(22),fill='#655A65')
im.save(ROOT/'small-font-save-states.png')
