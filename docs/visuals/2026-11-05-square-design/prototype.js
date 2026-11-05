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
  this.s={screen:'feed',kind:'全部',shop:'all',query:'',event:'dorothy-cotton',sku:null,selectionKey:null,content:'',color:'',style:'',size:'',attrFilter:{},pendingFilter:{},registrationMode:'group',selectedParts:null,partOverrides:{},coordinate:'none',accessoryParent:'',reference:false,search:false,keyboard:false,...options.state};
  this.saved=new Map();this.selection=new Map();this.working=false;this.render();
 }
 event(){return DATA.events.find(e=>e.id===this.s.event)}
 product(){return ProductUI.product(this)}
 selected(){return ProductUI.selected(this)}
 amount(text){return this.o.hideMoney?'金额已隐藏':text}
 heading(){const names={attributes:'商品筛选',information:'套装、小物与商品资料',feed:'广场',detail:'商品动态',sku:'选择款式与尺码',confirm:'加入衣橱',success:'加入结果',tail:'尾款说明',duplicate:'已在衣橱',stale:'资料需要更新',loading:'广场',empty:'广场',partial:'广场',imagefail:'商品动态'};return names[this.s.screen]||'广场'}
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
  if(screen==='information')html=e.canAdd?button('选择规格后加入衣橱','add'):button('查看尾款说明','tail');
  if(screen==='attributes')html=button('应用商品筛选','apply-attributes');
  if(screen==='detail'||screen==='imagefail')html=e.canAdd?button('加入衣橱','add'):button('查看尾款说明','tail');
  if(screen==='sku')html=`<p class="caption">${this.s.sku?'已选完整规格，下一步核对保存':'请选择一个实际规格组合；也可暂不选规格'}</p>`+button(this.s.sku?'下一步：确认加入':'暂不选规格，继续','confirm');
  if(screen==='confirm')html=`<p class="caption">保存为心愿，不建立订单或付款记录。</p>`+button(this.s.registrationMode==='split'?'确认分件保存到心愿':'保存到心愿','save','primary',(this.working||this.o.partialImages)?'disabled':'');
  if(screen==='tail')html=button('返回广场','feed','secondary');
  if(screen==='duplicate')html=button('查看已登记物品','existing','primary')+button('另记一件相同规格','another','inline-action');
  if(screen==='stale')html=button('返回重新核对','detail','secondary');
  return html?`<footer class="dock">${html}</footer>`:'';
 }
 controls(){return ProductUI.controls(this)}
 attributes(){return ProductUI.attributes(this)}
 information(){return ProductUI.information(this)}
 feed(){return ProductUI.feed(this)}
 card(e){
  return `<article class="product-card"><button class="image-button" data-action="open:${e.id}" aria-label="查看${esc(e.title)}"><img class="cover" src="${esc(e.cover)}" alt="${esc(e.title)}官方商品展示"><span class="photo-tag">${e.kind}${e.state==='预告'?' · 预告':''}</span></button><div class="card-copy"><p class="shop-label">${shopName(e.shop)}</p><h4>${button(esc(e.title),'open:'+e.id,'title-button')}</h4>${ProductUI.cardInfo(this,e)}<p class="state">${esc(e.state)}</p><p class="date">${esc(e.date)}</p><p class="price">${esc(this.amount(e.price))}</p><p class="caption">${esc(e.priceNote)}</p>${button((e.canAdd?icon('plus'):'')+(e.canAdd?'加入衣橱':'查看尾款'),'card-add:'+e.id,'add-card')}</div></article>`;
 }
 detail(){
  const e=this.event(),p=this.product();
  const image=this.s.screen==='imagefail'?`<div class="blank">${icon('image')}<p>图片暂时无法显示</p>${button('重试图片','detail','secondary')}</div>`:`<button data-action="viewer" aria-label="查看完整商品图片"><img src="${e.cover}" alt="${esc(e.title)}完整展示"></button><span class="counter">1 / ${p.mainCount} · 主图</span>`;
  return `<div class="content"><p class="shop-label">${shopName(e.shop)}${e.shop==='lilizi'?' · 官方二店':''}</p><h1 class="product-name">${e.title}</h1><p class="caption">${e.subtitle}</p>${tags(e)}${ProductUI.detailSummary(this)}<div class="detail-photo">${image}</div><div class="panel"><p class="price">${esc(this.amount(e.price))}</p><p class="caption">${e.priceNote}</p><p>${e.date}</p><p>${e.note}</p>${e.kind==='尾款'?'<p class="error">这是尾款链接，不提供新建物品或直接记付款。</p>':''}</div><section class="panel"><h3 class="section-title">款式与资料</h3><p>${e.detail}</p><p>${e.material}</p>${e.id==='dorothy-cotton'?button('查看JSK官方尺码表','chart','inline-action'):''}<p class="caption">主图、款式图与详情图可分别完整查看；加入前核对具体款式。</p></section><section class="panel"><h3 class="section-title">来源与时间</h3><p>采集于 ${e.captured} · 淘宝官方店铺</p><p class="caption">${e.time?'活动日期按本批次官方说明记录。':'首次上架日期未核实，不把采集日当作上新日。'}</p><details><summary class="inline-action">查看来源与同款链接</summary>${e.ids.map(id=>`<p class="caption">${esc(DATA.products[id].title)}<br>商品ID ${id}</p>`).join('')}</details></section></div>`;
 }
 sku(){return ProductUI.sku(this)}
 confirm(){return ProductUI.confirm(this)}
 success(){const e=this.event(),r=this.lastRegistration;return `<div class="content result"><div class="result-icon">${icon('check')}</div><h3>已加入心愿</h3><p>${e.title}</p><img class="result-product" src="${this.product().previews[0]}" alt="${e.title}">${r?`<section class="panel result-receipt"><p>保存 ${r.units.length} 条物品 · ${r.mode==='split'?'分别登记':'整组登记'}</p>${r.units.map(u=>`<p><strong>${esc(u.name)}</strong><br><span class="caption">${esc(u.color||'颜色未设置')} / ${esc(u.size||'尺码未设置')} / ${esc(u.style||'风格未设置')}${u.parent?' · 关联至'+esc(u.parent):''}</span></p>`).join('')}${r.coordinate==='new'?'<p>已按本次选择新建衣橱套装</p>':''}</section>`:'<p>保留所选规格与商品资料。<br>购买与付款，等确认后再记录。</p>'}${button('查看物品','existing')}${button('继续逛广场','feed','secondary')}</div>`}
 tail(){const e=this.event();return `<div class="content"><h1 class="product-name">${e.title}</h1><p class="shop-label">${shopName(e.shop)} · ${e.subtitle}</p>${tags(e)}<p class="notice"><strong>${e.note}</strong><br>先核对自己订单的批次和规格。</p><section class="panel"><h3 class="section-title">本批尾款</h3><p class="price">${esc(this.amount(e.price))}</p><p>${e.priceNote}</p><p>${e.date}</p><p>${e.detail}</p></section><section class="panel"><h3 class="section-title">与我的衣橱</h3><p>未自动关联个人购买记录。</p><p class="caption">不按同名商品推断你付过定金，不自动建立待付、实付或提醒。已登记的付款仍在原价格页面处理。</p></section><p class="caption">${e.captured}采集，信息非实时。</p></div>`}
 duplicate(){const row=this.selected();return `<div class="content"><p class="notice"><strong>这个规格已经登记过</strong><br>保留原物品，避免重复加入。</p><div class="selection-preview"><img src="${this.event().cover}" alt="商品"><div><h4>${this.event().title}</h4><p class="caption">${row?esc(row.content+' / '+(row.color||'颜色未提供')+' / '+(row.size||'尺码未提供')):'同一公开来源与完整规格'} · 心愿</p></div></div><p class="caption">先查看原登记结果；整组和分件不会互相绕过重复提示。明确需要第二份时再另记。</p></div>`}
 stale(){return `<div class="content blank">${icon('refresh')}<h3>商品资料已更新</h3><p>所选规格或报价发生变化，请重新核对。已填内容保留。</p></div>`}
 emptyContent(){return `<div class="blank">${icon('search')}<h3>没有符合条件的动态</h3><p>试试其他店铺或动态类型。</p>${button('清除筛选','reset','secondary')}</div>`}
 empty(){return this.controls()+`<div class="feed-content">${this.emptyContent()}</div>`}
 loading(){return this.controls()+`<div class="feed-content"><div class="product-grid">${[1,2,3,4].map(()=>'<div class="product-card"><div class="skeleton photo"></div><div class="card-copy"><div class="skeleton short"></div><div class="skeleton"></div><div class="skeleton"></div></div></div>').join('')}</div></div>`}
 partial(){return this.controls()+`<div class="feed-content"><p class="notice"><strong>2家店铺资料可用</strong><br>另1家本次更新未完成，保留上次收录。${button('重试更新','retry-demo','inline-action')}</p><div class="product-grid">${DATA.events.slice(0,2).map(e=>this.card(e)).join('')}</div></div>`}
 keyboard(){return `<div class="mobile-keyboard" aria-label="设计示意键盘">${'QWERTYUIOPASDFGHJKLZXCVBNM'.split('').map(c=>`<span>${c}</span>`).join('')}</div>`}
 setSelection(){ProductUI.choose(this)}
 open(id){this.s.event=id;Object.assign(this.s,{sku:null,selectionKey:null,content:'',color:'',style:'',size:'',reference:false,name:undefined,category:undefined,registrationMode:'group',selectedParts:null,partOverrides:{},recordColor:undefined,recordStyle:undefined,coordinate:'none',coordinateName:undefined,accessoryParent:''},this.selection.get(id)||{});this.s.screen='detail';this.render()}
 toast(message){this.root.querySelector('.toast')?.remove();const toast=document.createElement('div');toast.className='toast';toast.role='status';toast.textContent=message;this.root.appendChild(toast);setTimeout(()=>toast.remove(),2800)}
 viewer(chart=false){const viewer=document.createElement('div');viewer.className='viewer';const p=this.product();viewer.innerHTML=`<header class="topbar"><h2>${chart?'JSK · 官方尺码图':'完整图片'}</h2>${button(icon('close'),'close','icon-btn','aria-label="关闭图片"')}</header><div class="viewer-scroll"><img src="${chart?'assets/cotton-jsk-chart.webp':p.previews[0]}" alt="${chart?'JSK官方尺码与材质图':'完整商品展示'}"></div>`;viewer.querySelector('button').onclick=()=>viewer.remove();viewer.querySelector('img').onclick=()=>viewer.classList.toggle('zoom');this.root.appendChild(viewer)}
 bind(){
  this.root.querySelectorAll('[data-action]').forEach(el=>el.onclick=()=>this.action(el.dataset.action));
  this.root.querySelector('.content-select')?.addEventListener('change',e=>{this.s.content=e.target.value;this.s.color='';this.s.size='';this.setSelection();this.render()});
  this.root.querySelector('.color-select')?.addEventListener('change',e=>{this.s.color=e.target.value;this.s.size='';this.setSelection();this.render()});
  this.root.querySelectorAll('.attribute-select').forEach(el=>el.addEventListener('change',()=>{this.s.pendingFilter={...(this.s.pendingFilter||{}),[el.dataset.filter]:el.value}}));
  this.root.querySelector('.record-color')?.addEventListener('input',e=>this.s.recordColor=e.target.value);
  this.root.querySelector('.record-style')?.addEventListener('change',e=>this.s.recordStyle=e.target.value);
  this.root.querySelector('.coordinate-select')?.addEventListener('change',e=>{this.s.coordinate=e.target.value;this.s.coordinateName??=this.event().title+' · '+(this.selected()?.color||'所选组合');this.render()});
  this.root.querySelector('.coordinate-name')?.addEventListener('input',e=>this.s.coordinateName=e.target.value);
  this.root.querySelector('.parent-select')?.addEventListener('change',e=>this.s.accessoryParent=e.target.value);
  this.root.querySelectorAll('.part-check').forEach(el=>el.addEventListener('change',()=>{const parts=new Set(this.s.selectedParts||[]);el.checked?parts.add(el.dataset.part):parts.delete(el.dataset.part);this.s.selectedParts=[...parts];if(!parts.has(this.s.accessoryParent))this.s.accessoryParent='';this.render()}));
  for(const field of ['color','size'])this.root.querySelectorAll('.part-'+field).forEach(el=>el.addEventListener('input',()=>{const key=el.dataset.part;this.s.partOverrides[key]={...(this.s.partOverrides[key]||{}),[field]:el.value}}));
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
   const split=this.s.registrationMode==='split'&&this.selected()?.parts.length>1;
   if(!split&&!this.root.querySelector('.category-select')?.value)return this.toast('请选择类型。');
   if(split&&!this.s.selectedParts?.length)return this.toast('请至少选择一条物品。');
   if(split&&this.s.coordinate==='new'&&!this.root.querySelector('.coordinate-name')?.value.trim())return this.toast('请填写新套装名称。');
   if(this.o.partialImages)return this.toast('请先完成图片准备，再保存。');
   if(this.s.name!==undefined&&!this.s.name.trim())return this.toast('请填写物品名称。');
   const key=this.s.event+'/'+(this.selected()?.key||'unspecified');
   if(this.saved.has(key)){this.s.screen='duplicate';this.render();return}
   this.lastRegistration=ProductUI.snapshot(this);this.working=true;this.render();setTimeout(()=>{this.saved.set(key,this.lastRegistration);this.working=false;this.s.keyboard=false;this.s.screen='success';this.render()},350);return;
  }
  else if(type==='registration'){this.s.registrationMode=value;this.s.reference=false}
  else if(type==='attributes'){this.s.pendingFilter={...this.s.attrFilter};this.s.screen='attributes'}
  else if(type==='apply-attributes'){this.s.attrFilter={...this.s.pendingFilter};this.s.screen='feed'}
  else if(type==='clear-attributes'){this.s.pendingFilter={};this.render();return}
  else if(type==='information'){this.s.infoReturn=this.s.screen;this.s.screen='information'}
  else if(type==='viewer')return this.viewer();
  else if(type==='chart')return this.viewer(true);
  else if(type==='back'){this.s.keyboard=false;this.s.screen={information:this.s.infoReturn||'detail',attributes:'feed',sku:'detail',confirm:'sku',success:'feed',tail:'detail',duplicate:'detail'}[this.s.screen]||'feed'}
  else if(type==='reset'){this.s.kind='全部';this.s.shop='all';this.s.query='';this.s.attrFilter={};this.s.pendingFilter={};this.s.screen='feed'}
  else if(type==='another'){this.saved.delete(this.s.event+'/'+(this.selected()?.key||'unspecified'));this.s.screen='confirm'}
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
 if(board==='information')designs=[
  {title:'单品、组合、颜色与风格',note:'商家风格参数和实际颜色单列，购买内容分组展示。',state:{screen:'information',event:'dorothy-cotton'}},
  {title:'配套与条件赠品',note:'衬衫含领花；购买两件赠裙摆胸花是另一条件。',state:{screen:'information',event:'victoria'},focus:'section:nth-of-type(3)'},
  {title:'帝政SET的构成',note:'内裙与可拆外裙属于SET；手工背链是对应批次赠品。',state:{screen:'information',event:'pilgrim-repeat'},focus:'section:nth-of-type(2)'},
  {title:'衬衫附属的小物',note:'可拆托领、胸针是配套，不按额外赠品处理。',state:{screen:'information',event:'sleeve-repeat'},focus:'section:nth-of-type(3)'}];
 if(board==='bundles'){
  const combo=DATA.products['1088022205740'].skus.find(r=>r.content==='JSK+KC+内搭'&&r.color==='蓝色'&&r.size==='M');
  const paired=DATA.products['1022012881368'].skus.find(r=>r.content==='衬衫+领花'&&r.color==='黑色'&&r.size==='M');
  designs=[{title:'实际购买组合',note:'完整组合保留一个SKU价，附属件规格未知时不跟随主裙。',state:{screen:'sku',event:'dorothy-cotton',sku:combo.id}},
   {title:'整组登记',note:'1条成组物品；组合参考总价只保存一次。',state:{screen:'confirm',event:'dorothy-cotton',sku:combo.id}},
   {title:'分件与衣橱套装',note:'JSK、KC、内搭分开登记；是否新建套装由用户选择。',state:{screen:'confirm',event:'dorothy-cotton',sku:combo.id,registrationMode:'split',coordinate:'new'}},
   {title:'小物关联与赠品边界',note:'领花可关联衬衫，半裙和条件赠品不会自动加入。',state:{screen:'confirm',event:'victoria',sku:paired.id,registrationMode:'split',accessoryParent:'衬衫'}}];
 }
 if(board.startsWith('attributes-'))designs=Object.entries(skinLabels).map(([skin,title])=>({title:`${title} · ${board.endsWith('dark')?'暗色':'浅色'}`,note:'颜色、风格、包含清单和组合选择保持同一业务语义。',skin,mode:board.endsWith('dark')?'dark':'light',width:320,state:{screen:'information'}}));
 if(board.startsWith('skins-'))designs=Object.entries(skinLabels).map(([skin,title])=>({title:`${title} · ${board.endsWith('dark')?'暗色':'浅色'}`,note:'相同业务与字号；背景、画框、轮廓和广场图标随皮肤变化。',skin,mode:board.endsWith('dark')?'dark':'light',width:320}));
 if(board==='states')designs=[{title:'加载中',note:'骨架对应真实双列卡片。',state:{screen:'loading'}},{title:'筛选无结果',note:'清除条件并保留其他页面状态。',state:{screen:'empty',shop:'lilizi',kind:'尾款'}},{title:'部分更新失败',note:'已成功店铺与旧快照继续可读。',state:{screen:'partial'}},{title:'重复来源与规格',note:'查看原物品，另记相同规格需明确动作。',state:{screen:'duplicate'}},{title:'资料更新冲突',note:'保留输入，拒绝陈旧报价或规格提交。',state:{screen:'stale'}},{title:'图片失败',note:'文字可继续查看；不伪造原图。',state:{screen:'imagefail'}}];
 if(board==='edge')designs=[{title:'320dp · 两倍字',note:'双列变单列；类型与店铺可横向滚动，不缩字号。',width:320,scale:2},
  {title:'320dp · 两倍字 · 键盘示意',note:'内容可滚动，保存栏在键盘上方；原生IME待实施验收。',width:320,scale:2,state:{screen:'confirm',keyboard:true}},
  {title:'金额隐藏',note:'公开报价在只读页同步隐藏；不透露个人拥有信息。',width:350,hideMoney:true}];
 const container=document.getElementById('board-content');container.innerHTML=`<div class="board ${(board.startsWith('skins-')||board.startsWith('attributes-'))?'skins':board==='states'?'states':''}"></div>`;
 window.phones=[];designs.forEach((design,i)=>{const wrap=document.createElement('section');wrap.className='specimen';wrap.innerHTML=`<h2>${esc(design.title)}</h2><div id="phone-${i}"></div><p>${esc(design.note)}</p>`;container.firstChild.appendChild(wrap);window.phones.push(new SquarePhone(wrap.querySelector('#phone-'+i),{...base,...design,width:design.width||(designs.length>3?350:390)}))});
}
for(const control of Object.values(controls))control.addEventListener('change',renderBoard);renderBoard();
