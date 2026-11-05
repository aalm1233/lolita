/* Offline design prototype. No network calls and no writes to app/user data. */
const DATA=window.SQUARE_DATA;
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const money=n=>'¥'+Number(n).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2});
const shopName=id=>DATA.shops.find(s=>s.id===id).name;
const paths={back:'M15 5 8 12l7 7',search:'M10 18a8 8 0 1 0 0-16 8 8 0 0 0 0 16m6-2 6 6',plus:'M12 5v14M5 12h14',check:'m5 12 4 4L19 6',clock:'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18m0 4v5l3 2',home:'M4 21V5h16v16M8 5V2h8v3M12 9v10M9 11h6m-7 9h8',outfit:'M5 6h4l3 3 3-3h4l-2 6h-2l4 9H5l4-9H7L5 6M10 6V3h4v3',stats:'M4 21h17M6 17v-5h3v5m3 0V8h3v9m3 0V3h3v14',person:'M8 8a4 4 0 1 0 8 0 4 4 0 1 0-8 0M4 22c0-10 16-10 16 0',image:'M3 3h18v18H3zM3 17l6-7 5 6 3-3 4 5M15 7h.01',close:'M6 6l12 12M18 6 6 18',refresh:'M20 8a8 8 0 1 0 1 6M20 3v5h-5'};
function icon(name,skin='classic'){
 if(name==='square'){
  const variants={sweet:'M3 11Q1 8 5 7l2-4h10l2 4q4 1 2 4M4 11v10h16V11M9 21v-7h6v7M3 10q2 4 5 0 4 4 8 0 3 4 5 0M7 3q5 6 10 0',gothic:'M3 10 6 3l4 5 2-6 2 6 4-5 3 7M4 10v11h16V10M9 21v-5l3-3 3 3v5M4 11l4 2 4-3 4 3 4-2',chinese:'M2 8q4 1 5-4l5 2 5-2q1 5 5 4M4 9h16M5 10v11h14V10M9 21v-7h6v7M2 13q4-1 5-3m10 0q1 2 5 3',classic:'M3 8 6 4h12l3 4M3 9h18M5 9v12h14V9M8 9v12m8-12v12M10 21v-5q2-4 4 0v5M9 5h6',navy:'M4 10 6 5h12l2 5M3 10h18M5 11v10h14V11M9 21v-6h6v6M10 2h4v3M7 12h.01m10 0h.01M9 8h6',country:'M3 11 7 3h10l4 8M3 11q2 3 4 0 3 4 5 0 3 4 5 0 2 3 4 0M5 13v8h14v-8M9 21v-6h6v6M6 6h12',victorian:'M3 10V7q2-5 6-2 3-5 6 0 4-3 6 2v3M4 10h16M5 10v11h14V10M9 21v-6q3-5 6 0v6M6 7h2m8 0h2'};
  return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="${variants[skin]}"/></svg>`;
 }
 return `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="${paths[name]||paths.image}"/></svg>`;
}
function button(text,action,cls='primary',extra=''){return `<button type="button" class="${cls}" data-action="${action}" ${extra}>${text}</button>`}
function tags(event){return `<div class="tag-row">${event.tags.map(t=>`<span class="tag">${t}</span>`).join('')}<span class="tag">${esc(event.state)}</span></div>`}

class SquarePhone{
 constructor(root,options={}){
  this.root=root;this.o={skin:'classic',mode:'light',scale:1,width:390,...options};
  this.s={screen:'feed',kind:'全部',shop:'all',query:'',event:'dorothy-cotton',sku:null,style:'',size:'',reference:false,search:false,keyboard:false,...options.state};
  this.saved=new Map();this.selection=new Map();this.working=false;this.render();
 }
 event(){return DATA.events.find(e=>e.id===this.s.event)}
 product(){return DATA.products[this.event().ids[0]]}
 selected(){return this.product().skus.find(k=>k.id===this.s.sku)}
 amount(text){return this.o.hideMoney?'金额已隐藏':text}
 heading(){const names={feed:'广场',detail:'商品动态',sku:'选择款式与尺码',confirm:'加入衣橱',success:'加入结果',tail:'尾款说明',duplicate:'已在衣橱',stale:'资料需要更新',loading:'广场',empty:'广场',partial:'广场',imagefail:'商品动态'};return names[this.s.screen]||'广场'}
 render(){
  this.root.className='phone'+(this.o.scale===2?' large':'');this.root.dataset.skin=this.o.skin;this.root.dataset.mode=this.o.mode;this.root.style.setProperty('--scale',this.o.scale);this.root.style.width=this.o.width+'px';
  const isFeed=['feed','empty','loading','partial'].includes(this.s.screen);
  const header=`<div class="statusbar"><span>9:41</span><span>◔ ▰</span></div><header class="topbar">${isFeed?`<span class="identity">${icon('square',this.o.skin)}</span>`:button(icon('back'),'back','icon-btn','aria-label="返回"')}<h2>${this.heading()}</h2>${isFeed?button(icon('search'),'search','icon-btn','aria-label="搜索店铺或商品"'):''}</header>`;
  const search=this.s.search?`<div class="searchbox"><input type="text" class="search-input" placeholder="搜索店铺、系列或款式" aria-label="搜索店铺、系列或款式" value="${esc(this.s.query)}"></div>`:'';
  let content=this[this.s.screen==='imagefail'?'detail':this.s.screen]();
  this.root.innerHTML=header+search+`<main class="page">${content}</main>`+(isFeed?this.nav():this.dock())+(this.s.keyboard?this.keyboard():'');
  this.bind();
 }
 nav(){return `<nav class="nav" aria-label="底部导航">${[['home','首页'],['square','广场'],['outfit','穿搭'],['stats','统计'],['person','个人']].map(([i,n])=>button(`${icon(i,this.o.skin)}<span>${n}</span>`,n==='广场'?'feed':'nav-demo',n==='广场'?'active':'','aria-label="'+n+'"')).join('')}</nav>`}
 dock(){
  const screen=this.s.screen,e=this.event();let html='';
  if(screen==='detail'||screen==='imagefail')html=e.canAdd?button('加入衣橱','add'):button('查看尾款说明','tail');
  if(screen==='sku')html=`<p class="caption">${this.s.sku?'已选完整规格，下一步核对保存':'请选择一个实际规格组合；也可暂不选规格'}</p>`+button(this.s.sku?'下一步：确认加入':'暂不选规格，继续','confirm');
  if(screen==='confirm')html=`<p class="caption">保存为心愿，不建立订单或付款记录。</p>`+button('保存到心愿','save','primary',(this.working||this.o.partialImages)?'disabled':'');
  if(screen==='tail')html=button('返回广场','feed','secondary');
  if(screen==='duplicate')html=button('查看已登记物品','existing','primary')+button('另记一件相同规格','another','inline-action');
  if(screen==='stale')html=button('返回重新核对','detail','secondary');
  return html?`<footer class="dock">${html}</footer>`:'';
 }
 controls(){return `<div class="feed-controls"><div class="kind-tabs" role="tablist" aria-label="动态类型">${['全部','上新','再贩','现货','尾款'].map(k=>button(k,'kind:'+k,this.s.kind===k?'active':'',`role="tab" aria-selected="${this.s.kind===k}"`)).join('')}</div><div class="store-row" aria-label="店铺筛选">${[['all','全部店铺'],...DATA.shops.map(s=>[s.id,s.name])].map(([id,name])=>button(name,'shop:'+id,'chip'+(this.s.shop===id?' active':''),`aria-pressed="${this.s.shop===id}"`)).join('')}</div><p class="snapshot">${icon('clock')}11月3—5日采集 · 非实时库存</p></div>`}
 feed(){
  const events=DATA.events.filter(e=>(this.s.kind==='全部'||e.tags.includes(this.s.kind))&&(this.s.shop==='all'||e.shop===this.s.shop)&&(`${e.title}${e.subtitle}${shopName(e.shop)}`).toLowerCase().includes(this.s.query.toLowerCase()));
  const info=this.s.kind==='再贩'?'<p class="notice"><strong>本次收录的再贩预约已结束</strong><br>可查看原款和批次；尾款开放不代表重新接单。</p>':this.s.kind==='尾款'?'<p class="notice"><strong>仅供已付对应批次定金者核对</strong><br>店铺补款信息不代表你的个人待付。</p>':'';
  const title=this.s.kind==='全部'?'店铺近况':this.s.kind==='上新'?'上新与预告':this.s.kind==='现货'?'现货与掉落':this.s.kind==='再贩'?'再贩与批次':'尾款与截止时间';
  return this.controls()+`<div class="feed-content"><div class="featured"><h3>${title}</h3><small>${events.length}条动态</small></div>${info}${events.length?`<div class="product-grid">${events.map(e=>this.card(e)).join('')}</div>`:this.emptyContent()}<p class="caption" style="margin-top:20px">日期未明确的商品按采集时间收录，不能据此判断首次上架。每店的采集时间见商品详情。</p></div>`;
 }
 card(e){
  return `<article class="product-card"><button class="image-button" data-action="open:${e.id}" aria-label="查看${esc(e.title)}"><img class="cover" src="${esc(e.cover)}" alt="${esc(e.title)}官方商品展示"><span class="photo-tag">${e.kind}${e.state==='预告'?' · 预告':''}</span></button><div class="card-copy"><p class="shop-label">${shopName(e.shop)}</p><h4>${button(esc(e.title),'open:'+e.id,'title-button')}</h4><p class="state">${esc(e.state)}</p><p class="date">${esc(e.date)}</p><p class="price">${esc(this.amount(e.price))}</p><p class="caption">${esc(e.priceNote)}</p>${button((e.canAdd?icon('plus'):'')+(e.canAdd?'加入衣橱':'查看尾款'),'card-add:'+e.id,'add-card')}</div></article>`;
 }
 detail(){
  const e=this.event(),p=this.product();
  const image=this.s.screen==='imagefail'?`<div class="blank">${icon('image')}<p>图片暂时无法显示</p>${button('重试图片','detail','secondary')}</div>`:`<button data-action="viewer" aria-label="查看完整商品图片"><img src="${e.cover}" alt="${esc(e.title)}完整展示"></button><span class="counter">1 / ${p.mainCount} · 主图</span>`;
  return `<div class="content"><p class="shop-label">${shopName(e.shop)}${e.shop==='lilizi'?' · 官方二店':''}</p><h1 class="product-name">${e.title}</h1><p class="caption">${e.subtitle}</p>${tags(e)}<div class="detail-photo">${image}</div><div class="panel"><p class="price">${esc(this.amount(e.price))}</p><p class="caption">${e.priceNote}</p><p>${e.date}</p><p>${e.note}</p>${e.kind==='尾款'?'<p class="error">这是尾款链接，不提供新建物品或直接记付款。</p>':''}</div><section class="panel"><h3 class="section-title">款式与资料</h3><p>${e.detail}</p><p>${e.material}</p>${e.id==='dorothy-cotton'?button('查看JSK官方尺码表','chart','inline-action'):''}<p class="caption">主图、款式图与详情图可分别完整查看；加入前核对具体款式。</p></section><section class="panel"><h3 class="section-title">来源与时间</h3><p>采集于 ${e.captured} · 淘宝官方店铺</p><p class="caption">${e.time?'活动日期按本批次官方说明记录。':'首次上架日期未核实，不把采集日当作上新日。'}</p><details><summary class="inline-action">查看来源与同款链接</summary>${e.ids.map(id=>`<p class="caption">${esc(DATA.products[id].title)}<br>商品ID ${id}</p>`).join('')}</details></section></div>`;
 }
 sku(){
  const e=this.event(),p=this.product(),sku=this.selected(),styles=[...new Set(p.skus.map(k=>k.props['颜色分类']||Object.values(k.props)[0]))];
  const matching=p.skus.filter(k=>(k.props['颜色分类']||Object.values(k.props)[0])===this.s.style);
  const sizes=[...new Set(matching.map(k=>k.props['尺码']||'均码'))];
  const quote=sku?this.amount(money(sku.price)):this.amount(e.price);
  return `<div class="content"><p class="steps"><strong>选择规格</strong><span>›</span><span>核对资料</span><span>›</span><span>保存</span></p><div class="selection-preview"><img src="${e.cover}" alt="${e.title}"><div><h4>${e.title}</h4><p class="shop-label">${shopName(e.shop)}</p><p class="price">${esc(quote)}</p><p class="caption">${e.kind==='再贩'?'历史资料 · 金额不预填':'参考报价，保存不记付款'}</p></div></div>${e.state==='预告'?'<p class="notice">尚未开售；可先保存心愿，报价待正式开售核对。</p>':''}${e.kind==='再贩'?'<p class="notice">此批预约已结束。保留款式资料，不采用下架页面金额。</p>':''}<div class="field"><label for="style-${this.root.id}">颜色 / 款式</label><select id="style-${this.root.id}" class="style-select"><option value="">未选择</option>${styles.map(v=>`<option ${v===this.s.style?'selected':''} value="${esc(v)}">${esc(v)}</option>`).join('')}</select><p class="caption">包含单品、配件与套餐，请按规格原文选择。</p></div><div class="field"><span class="field-label">尺码</span><div class="choice-wrap">${sizes.length?sizes.map(size=>button(esc(size),'size:'+size,'choice'+(size===this.s.size?' active':''))).join(''):'<span class="caption">先选择颜色 / 款式，再查看实际尺码</span>'}</div></div>${sku?`<section class="panel"><div class="kv"><span>本次选择</span><strong>${esc(Object.values(sku.props).join(' / '))}</strong></div><div class="kv"><span>页面状态</span><strong>${esc(sku.stock)}</strong></div><p class="caption">无货规格仍可保存心愿；库存以店铺最新页面为准。</p></section>`:''}<p class="caption">未选规格可继续，参考金额留空。默认只加入一件，不自动展开全部颜色和尺码。</p>${e.id==='dorothy-cotton'?button('查看JSK官方尺码表','chart','inline-action'):''}</div>`;
 }
 confirm(){
  const e=this.event(),sku=this.selected(),eligible=sku&&e.kind!=='再贩'&&e.state!=='预告';
  const spec=sku?Object.values(sku.props).join(' / '):'未选择规格';
  this.s.name??=e.title+(sku?' · '+spec:'');
  const types=['JSK','OP','SK','衬衫','KC','其他配饰','套装（待拆分核对）'];
  const suggestion=sku?types.find(t=>t!=='套装（待拆分核对）'&&spec.includes(t))||'':'';
  const selectedType=this.s.category===undefined?suggestion:this.s.category;
  return `<div class="content"><p class="steps"><span>选择规格</span><span>›</span><strong>核对资料</strong><span>›</span><span>保存</span></p><div class="selection-preview"><img src="${e.cover}" alt="${e.title}"><div><h4>${e.title}</h4><p class="caption">${esc(spec)}</p></div></div><section class="panel"><h3 class="section-title">登记到衣橱</h3><div class="field"><label>名称<input class="item-name" type="text" value="${esc(this.s.name)}"></label></div><div class="kv"><span>品牌</span><strong>${shopName(e.shop)}</strong></div><div class="field"><label>类型<select class="category-select"><option value="">请选择类型</option>${types.map(t=>`<option value="${t}" ${t===selectedType?'selected':''}>${t}</option>`).join('')}</select></label></div><div class="kv"><span>拥有状态</span><strong>心愿</strong></div><p class="caption">如已购入，可进入完整编辑核对拥有与购买资料。</p></section><section class="panel"><h3 class="section-title">参考金额</h3>${eligible?`<label class="check"><input class="reference-check" type="checkbox" ${this.s.reference?'checked':''}><span>同时保存所选规格的参考金额<br><strong>${esc(this.amount(money(sku.price)))}</strong><br><span class="caption">${e.captured}采集 · ${esc(sku.priceType)}</span></span></label>`:`<p>${e.kind==='再贩'?'下架标价不作为参考金额。':e.state==='预告'?'预告价格待核对，参考金额留空。':'未选择完整规格，参考金额留空。'}</p>`}<p class="caption">参考金额不代表购买、应付或实付。</p></section><section class="panel"><h3 class="section-title">图片与说明</h3><p>保存完整商品相册、详情图与适用尺码图，保留来源；${this.product().imageCount}张本链接图片待核对。</p><p class="caption">${this.o.partialImages?'图片尚未完整准备，请重试后保存。':'保留原图顺序与来源，保存前核对图片是否完整。'}</p></section>${this.o.partialImages?'<p class="error">尚有图片未准备完成，保存暂不可用。</p>':''}${button('进入完整编辑','edit-demo','inline-action')}</div>`;
 }
 success(){const e=this.event();return `<div class="content result"><div class="result-icon">${icon('check')}</div><h3>已加入心愿</h3><p>${e.title}</p><img class="result-product" src="${e.cover}" alt="${e.title}"><p>保留所选规格与商品资料。<br>购买与付款，等确认后再记录。</p>${button('查看物品','existing')}${button('继续逛广场','feed','secondary')}</div>`}
 tail(){const e=this.event();return `<div class="content"><h1 class="product-name">${e.title}</h1><p class="shop-label">${shopName(e.shop)} · ${e.subtitle}</p>${tags(e)}<p class="notice"><strong>${e.note}</strong><br>先核对自己订单的批次和规格。</p><section class="panel"><h3 class="section-title">本批尾款</h3><p class="price">${esc(this.amount(e.price))}</p><p>${e.priceNote}</p><p>${e.date}</p><p>${e.detail}</p></section><section class="panel"><h3 class="section-title">与我的衣橱</h3><p>未自动关联个人购买记录。</p><p class="caption">不按同名商品推断你付过定金，不自动建立待付、实付或提醒。已登记的付款仍在原价格页面处理。</p></section><p class="caption">${e.captured}采集，信息非实时。</p></div>`}
 duplicate(){return `<div class="content"><p class="notice"><strong>这个规格已经登记过</strong><br>保留原物品，避免重复加入。</p><div class="selection-preview"><img src="${this.event().cover}" alt="商品"><div><h4>${this.event().title}</h4><p class="caption">蓝色 JSK / M · 心愿</p></div></div><p class="caption">只按已保存的公开来源和完整规格提示，不凭相似名称合并。需要第二件可明确另记。</p></div>`}
 stale(){return `<div class="content blank">${icon('refresh')}<h3>商品资料已更新</h3><p>所选规格或报价发生变化，请重新核对。已填内容保留。</p></div>`}
 emptyContent(){return `<div class="blank">${icon('search')}<h3>没有符合条件的动态</h3><p>试试其他店铺或动态类型。</p>${button('清除筛选','reset','secondary')}</div>`}
 empty(){return this.controls()+`<div class="feed-content">${this.emptyContent()}</div>`}
 loading(){return this.controls()+`<div class="feed-content"><div class="product-grid">${[1,2,3,4].map(()=>'<div class="product-card"><div class="skeleton photo"></div><div class="card-copy"><div class="skeleton short"></div><div class="skeleton"></div><div class="skeleton"></div></div></div>').join('')}</div></div>`}
 partial(){return this.controls()+`<div class="feed-content"><p class="notice"><strong>2家店铺资料可用</strong><br>另1家本次更新未完成，保留上次收录。${button('重试更新','retry-demo','inline-action')}</p><div class="product-grid">${DATA.events.slice(0,2).map(e=>this.card(e)).join('')}</div></div>`}
 keyboard(){return `<div class="mobile-keyboard" aria-label="设计示意键盘">${'QWERTYUIOPASDFGHJKLZXCVBNM'.split('').map(c=>`<span>${c}</span>`).join('')}</div>`}
 setSelection(){const p=this.product();this.s.sku=p.skus.find(k=>(k.props['颜色分类']||Object.values(k.props)[0])===this.s.style&&(k.props['尺码']||'均码')===this.s.size)?.id||null;this.selection.set(this.s.event,{style:this.s.style,size:this.s.size,sku:this.s.sku})}
 open(id){this.s.event=id;Object.assign(this.s,{sku:null,style:'',size:'',reference:false,name:undefined,category:undefined},this.selection.get(id)||{});this.s.screen='detail';this.render()}
 toast(message){this.root.querySelector('.toast')?.remove();const toast=document.createElement('div');toast.className='toast';toast.role='status';toast.textContent=message;this.root.appendChild(toast);setTimeout(()=>toast.remove(),2800)}
 viewer(chart=false){const viewer=document.createElement('div');viewer.className='viewer';const p=this.product();viewer.innerHTML=`<header class="topbar"><h2>${chart?'JSK · 官方尺码图':'完整图片'}</h2>${button(icon('close'),'close','icon-btn','aria-label="关闭图片"')}</header><div class="viewer-scroll"><img src="${chart?'assets/cotton-jsk-chart.webp':p.previews[0]}" alt="${chart?'JSK官方尺码与材质图':'完整商品展示'}"></div>`;viewer.querySelector('button').onclick=()=>viewer.remove();viewer.querySelector('img').onclick=()=>viewer.classList.toggle('zoom');this.root.appendChild(viewer)}
 bind(){
  this.root.querySelectorAll('[data-action]').forEach(el=>el.onclick=()=>this.action(el.dataset.action));
  this.root.querySelector('.style-select')?.addEventListener('change',e=>{this.s.style=e.target.value;this.s.size='';this.s.sku=null;this.setSelection();this.render()});
  this.root.querySelector('.category-select')?.addEventListener('change',e=>this.s.category=e.target.value);
  this.root.querySelector('.reference-check')?.addEventListener('change',e=>this.s.reference=e.target.checked);
  this.root.querySelector('.item-name')?.addEventListener('input',e=>this.s.name=e.target.value);
  this.root.querySelector('.search-input')?.addEventListener('input',e=>{const input=e.target;this.s.query=input.value;const start=input.selectionStart;this.render();const next=this.root.querySelector('.search-input');next.focus();next.setSelectionRange(start,start)});
 }
 action(action){
  const [type,...parts]=action.split(':'),value=parts.join(':');
  if(type==='open')return this.open(value);
  if(type==='kind'){this.s.kind=value;this.s.screen='feed'}
  else if(type==='shop'){this.s.shop=value;this.s.screen='feed'}
  else if(type==='search'){this.s.search=!this.s.search}
  else if(type==='size'){this.s.size=value;this.setSelection()}
  else if(type==='card-add'){this.open(value);this.s.screen=this.event().canAdd?'sku':'tail'}
  else if(type==='add'){if(!this.event().canAdd)return;this.s.screen='sku'}
  else if(type==='confirm'){this.s.screen='confirm'}
  else if(type==='save'){
   if(this.working)return;
   if(!this.root.querySelector('.category-select')?.value)return this.toast('请选择类型。');
   if(this.o.partialImages)return this.toast('请先完成图片准备，再保存。');
   if(this.s.name!==undefined&&!this.s.name.trim())return this.toast('请填写物品名称。');
   const key=this.s.event+'/'+(this.s.sku||'unspecified');
   if(this.saved.has(key)){this.s.screen='duplicate';this.render();return}
   this.working=true;this.render();setTimeout(()=>{this.saved.set(key,{name:this.s.name,reference:this.s.reference});this.working=false;this.s.keyboard=false;this.s.screen='success';this.render()},350);return;
  }
  else if(type==='viewer')return this.viewer();
  else if(type==='chart')return this.viewer(true);
  else if(type==='back'){this.s.keyboard=false;this.s.screen={sku:'detail',confirm:'sku',success:'feed',tail:'detail',duplicate:'detail'}[this.s.screen]||'feed'}
  else if(type==='reset'){this.s.kind='全部';this.s.shop='all';this.s.query='';this.s.screen='feed'}
  else if(type==='another'){this.saved.delete(this.s.event+'/'+(this.s.sku||'unspecified'));this.s.screen='confirm'}
  else if(['feed','detail','tail'].includes(type)){this.s.screen=type}
  else if(type==='existing')return this.toast('原型演示：正式版进入该物品的衣橱详情。');
  else if(type==='edit-demo')return this.toast('正式版衔接现有完整物品编辑，保留本页已填内容。');
  else if(type==='retry-demo')return this.toast('状态演示：保留已有快照；不在此原型联网。');
  else return this.toast('设计预览保留现有入口，本次聚焦广场。');
  this.render();
 }
}
window.SquarePhone=SquarePhone;
const skinLabels={sweet:'甜美',gothic:'哥特',chinese:'中华',classic:'古典',navy:'海军',country:'田园',victorian:'维多利亚'};
const controls={board:document.getElementById('board'),skin:document.getElementById('skin'),mode:document.getElementById('mode'),scale:document.getElementById('scale')};
const params=new URLSearchParams(location.search);for(const key of Object.keys(controls))if(params.has(key))controls[key].value=params.get(key);
function renderBoard(){
 const board=controls.board.value,base={skin:controls.skin.value,mode:controls.mode.value,scale:Number(controls.scale.value)};
 let designs=[];
 if(board==='interactive')designs=[{title:'广场 · 可点击预览',note:'切换分类或店铺，点商品查看详情；加入衣橱需先核对，再模拟保存。',...base}];
 if(board==='overview')designs=[
  {title:'上新 · 含已开售与预告',note:'同一设计全款、定金链接合并展示；阶段分开。',state:{kind:'上新'}},
  {title:'再贩 · 批次与历史状态',note:'预约已结束仍可留心愿；尾款信息独立标注。',state:{kind:'再贩'}},
  {title:'现货 · 含掉落',note:'保留部分无货、预售发货等页面冲突。',state:{kind:'现货'}},
  {title:'尾款 · 只看信息',note:'无加入衣橱或一键记付款，明确适用定金批次。',state:{kind:'尾款'}}];
 if(board==='flow'){
  const chosen=DATA.products['1088022205740'].skus.find(k=>k.props['颜色分类']==='蓝色 JSK'&&k.props['尺码']==='M');
  designs=[{title:'查看商品',note:'完整看图，先看活动状态和价格阶段。',state:{screen:'detail'}},
   {title:'选择实际规格',note:'选款式和尺码后只采用该完整SKU报价。',state:{screen:'sku',style:'蓝色 JSK',size:'M',sku:chosen.id}},
   {title:'确认加入衣橱',note:'默认心愿，参考金额需主动勾选；不建账。',state:{screen:'confirm',style:'蓝色 JSK',size:'M',sku:chosen.id}},
   {title:'保存结果',note:'提供查看物品、继续浏览的明确去向。',state:{screen:'success'}}];
 }
 if(board.startsWith('skins-'))designs=Object.entries(skinLabels).map(([skin,title])=>({title:`${title} · ${board.endsWith('dark')?'暗色':'浅色'}`,note:'相同业务与字号；背景、画框、轮廓和广场图标随皮肤变化。',skin,mode:board.endsWith('dark')?'dark':'light',width:320}));
 if(board==='states')designs=[{title:'加载中',note:'骨架对应真实双列卡片。',state:{screen:'loading'}},{title:'筛选无结果',note:'清除条件并保留其他页面状态。',state:{screen:'empty',shop:'lilizi',kind:'尾款'}},{title:'部分更新失败',note:'已成功店铺与旧快照继续可读。',state:{screen:'partial'}},{title:'重复来源与规格',note:'查看原物品，另记相同规格需明确动作。',state:{screen:'duplicate'}},{title:'资料更新冲突',note:'保留输入，拒绝陈旧报价或规格提交。',state:{screen:'stale'}},{title:'图片失败',note:'文字可继续查看；不伪造原图。',state:{screen:'imagefail'}}];
 if(board==='edge')designs=[{title:'320dp · 两倍字',note:'双列变单列；类型与店铺可横向滚动，不缩字号。',width:320,scale:2},
  {title:'320dp · 两倍字 · 键盘示意',note:'内容可滚动，保存栏在键盘上方；原生IME待实施验收。',width:320,scale:2,state:{screen:'confirm',keyboard:true}},
  {title:'金额隐藏',note:'公开报价在只读页同步隐藏；不透露个人拥有信息。',width:350,hideMoney:true}];
 const container=document.getElementById('board-content');container.innerHTML=`<div class="board ${board.startsWith('skins-')?'skins':board==='states'?'states':''}"></div>`;
 window.phones=[];designs.forEach((design,i)=>{const wrap=document.createElement('section');wrap.className='specimen';wrap.innerHTML=`<h2>${esc(design.title)}</h2><div id="phone-${i}"></div><p>${esc(design.note)}</p>`;container.firstChild.appendChild(wrap);window.phones.push(new SquarePhone(wrap.querySelector('#phone-'+i),{...base,...design,width:design.width||(designs.length>3?350:390)}))});
}
for(const control of Object.values(controls))control.addEventListener('change',renderBoard);renderBoard();
