"""Pre-implementation, code-native artist board for the Square developer tools."""
from pathlib import Path
from html import escape

ROOT = Path(__file__).resolve().parents[2] / 'docs/visuals/2026-11-07-square-developer/artist'
SKINS = [('甜美','#fbf3f6','#b85e87'),('哥特','#f1edf5','#73538a'),('中华','#f7f1e8','#a64b49'),('古典','#f5f1e8','#8b7154'),('海军','#eef4f9','#376b99'),('田园','#f1f5ea','#657d45'),('维多利亚','#f5eef2','#92617b')]

def text(x, y, value, size=16, color='#332b32'):
    return f'<text x="{x}" y="{y}" font-size="{size}" fill="{color}" font-family="PingFang SC,sans-serif">{escape(value)}</text>'

def rect(x, y, w, h, color, radius=16, stroke='none'):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" fill="{color}" stroke="{stroke}"/>'

def phone(x, y, skin, dark=False, mode='service'):
    name, bg, accent = skin
    ink = '#f3eaf1' if dark else '#332b32'
    surface = '#302a32' if dark else '#fff'
    bg = '#211d24' if dark else bg
    s = rect(x,y,340,700,bg,24)+text(x+20,y+30,'9:41',12,ink)
    s += text(x+20,y+75,'广场',22,ink)+text(x+220,y+75,'♡  ▤  ⚙',22,accent)
    s += rect(x+18,y+102,304,44,surface)+text(x+30,y+130,'⌕  搜索商品或店铺',15,ink)
    s += text(x+20,y+186,'全部    上新    再贩    现货    尾款',15,ink)
    s += text(x+107,y+396,'暂无店铺资料',20,ink)
    s += rect(x+28,y+426,284,48,accent)+text(x+124,y+457,'导入资料包',16,'white')
    s += rect(x,y+642,340,58,surface,0)+text(x+14,y+677,'衣橱    穿搭    广场    统计    个人',14,ink)
    if mode != 'empty':
        s += rect(x,y,340,700,'#00000066',24)
        s += rect(x+22,y+190,296,350,surface,24)
        if mode=='service':
            s += text(x+45,y+235,'广场服务',22,ink)
            s += rect(x+44,y+264,252,72,bg,6,accent)+text(x+56,y+286,'服务地址',13,accent)
            s += text(x+56,y+315,'http://192.168.0.104:18765',14,ink)
            s += text(x+44,y+362,'清空地址后停止联网查询',13,ink)
            s += text(x+44,y+405,'使用内置地址',15,accent)
            s += text(x+44,y+452,'开发者工具',15,accent)
            s += text(x+183,y+510,'取消',15,accent)+text(x+254,y+510,'保存',15,accent)
        elif mode=='tools':
            s += text(x+45,y+238,'开发者工具',22,ink)
            s += rect(x+44,y+292,252,58,bg,12,accent)+text(x+76,y+327,'清空本机广场资料',16,accent)
            s += text(x+248,y+510,'关闭',15,accent)
        else:
            s += text(x+45,y+238,'清空本机广场资料？',21,ink)
            for i,line in enumerate(['删除本机广场目录与图片缓存。','衣橱、心愿、登记草稿和服务端','资料保留。下次刷新会重新获取。']):
                s += text(x+44,y+294+i*29,line,15,ink)
            s += text(x+183,y+510,'取消',15,accent)+text(x+254,y+510,'清空',15,'#b64d54' if not dark else '#ffb4ab')
    return s+text(x,y+730,f'{name} · {"暗" if dark else "浅"} · '+{'service':'内置地址','tools':'开发者入口','confirm':'清空确认','empty':'清空完成'}[mode],15)

def save(name,w,h,body):
    ROOT.mkdir(parents=True,exist_ok=True)
    (ROOT/name).write_text(f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">{rect(0,0,w,h,"#ebe7e3",0)}{body}</svg>')

body=text(25,42,'广场开发者工具｜前置艺术界面图',28)
body+=text(25,78,'复用皮肤弹窗与标准按钮；清空后只呈现空态，无成功弹窗打断。小屏大字内容可滚动。',16)
for i,mode in enumerate(['service','tools','confirm','empty']):
    body+=phone(25+i*370,110,SKINS[0],mode=mode)
save('main.svg',1500,890,body)
body=text(25,42,'七套皮肤 × 浅暗｜确认后才删除，主要动作清晰可达',28)
for i,skin in enumerate(SKINS):
    for j in range(2):
        body+=phone(25+i*360,85+j*775,skin,bool(j),'confirm')
save('skins.svg',2550,1650,body)
