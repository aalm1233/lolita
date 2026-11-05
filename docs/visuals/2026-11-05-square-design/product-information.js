/* Product content, colour and style are independent from activity and SKU phase. */
window.ProductUI = (() => {
 const unique=xs=>[...new Set(xs)];
 const catalog=()=>window.SQUARE_DATA;
 const colorValue=row=>row.color||'__unknown__';
 const lines=value=>value==null?[]:typeof value==='string'?[value]:Array.isArray(value)?value.flatMap(lines):Object.entries(value).map(([k,v])=>k+'：'+lines(v).join('；'));
 function rows(phone,event=phone.event()){
  return event.ids.flatMap(id=>catalog().products[id].isDeposit?[]:catalog().products[id].skus);
 }
 function selected(phone){
  const hits=rows(phone).filter(row=>phone.s.selectionKey?row.key===phone.s.selectionKey:row.id===phone.s.sku);
  return hits.length===1?hits[0]:null;
 }
 function product(phone){return catalog().products[selected(phone)?.itemId||phone.event().ids[0]]}
 function restoreChoice(phone){const row=selected(phone);if(row&&!phone.s.content){phone.s.content=row.content;phone.s.color=colorValue(row);phone.s.size=row.size||'__unknown__'}return row}
 function choose(phone){
  const hits=rows(phone).filter(r=>r.content===phone.s.content&&colorValue(r)===phone.s.color&&(r.size||'__unknown__')===phone.s.size);
  phone.s.sku=hits.length===1?hits[0].id:null;
  phone.s.selectionKey=hits.length===1?hits[0].key:null;
  phone.s.reference=false;phone.s.name=undefined;phone.s.registrationMode='group';phone.s.selectedParts=null;phone.s.category=undefined;phone.s.recordColor=undefined;phone.s.recordStyle=undefined;phone.s.partOverrides={};phone.s.coordinate='none';phone.s.accessoryParent='';
  phone.selection.set(phone.s.event,{content:phone.s.content,color:phone.s.color,size:phone.s.size,sku:phone.s.sku,selectionKey:phone.s.selectionKey});
 }
 function facts(phone,event=phone.event()){return unique(rows(phone,event).flatMap(r=>r.styleFacts.map(f=>f.value)))}
 function styleText(phone,event=phone.event()){const values=facts(phone,event);return values.length===1&&values[0]==='帝政风'?'含帝政风SET':values.join(' / ')||'风格未标注'}
 function filterMatches(row,filter){return (!filter.color||colorValue(row)===filter.color)&&(!filter.form||row.form===filter.form)&&(!filter.aesthetic||(filter.aesthetic==='__unknown__'?!row.styleFacts.length:row.styleFacts.some(f=>f.value===filter.aesthetic)))}
 function matches(phone,event){
  const query=phone.s.query.toLowerCase(),all=rows(phone,event);
  const text=[event.title,event.subtitle,shopName(event.shop),...all.map(r=>r.content+' '+(r.color||'')+' '+r.styleFacts.map(f=>f.value).join(' '))].join(' ').toLowerCase();
  return (phone.s.kind==='全部'||event.tags.includes(phone.s.kind))&&(phone.s.shop==='all'||event.shop===phone.s.shop)&&text.includes(query)&&all.some(r=>filterMatches(r,phone.s.attrFilter||{}));
 }
 function sourceList(values){return values?.length?values.map(v=>`<p class="bullet">${esc(v)}</p>`).join(''):'<p class="caption">未提供明确说明</p>'}
 function controls(phone){
  const count=Object.values(phone.s.attrFilter||{}).filter(Boolean).length;
  return `<div class="feed-controls"><div class="kind-tabs" role="tablist" aria-label="动态类型">${['全部','上新','再贩','现货','尾款'].map(k=>button(k,'kind:'+k,phone.s.kind===k?'active':'',`role="tab" aria-selected="${phone.s.kind===k}"`)).join('')}</div><div class="store-row" aria-label="店铺筛选">${[['all','全部店铺'],...catalog().shops.map(s=>[s.id,s.name])].map(([id,name])=>button(name,'shop:'+id,'chip'+(phone.s.shop===id?' active':''),`aria-pressed="${phone.s.shop===id}"`)).join('')}</div><div class="filter-line"><p class="snapshot">${icon('clock')}11月3—5日采集</p>${button('商品筛选'+(count?' · '+count:''),'attributes','filter-trigger')}</div>${count?`<p class="filter-summary">${Object.entries(phone.s.attrFilter).filter(([,v])=>v).map(([k,v])=>esc(v==='__unknown__'?(k==='color'?'颜色未单列':'风格未标注'):v)).join(' · ')}</p>`:''}<p class="caption snapshot-note">采集快照，非实时库存</p></div>`;
 }
 function attributes(phone){
  const all=catalog().events.flatMap(e=>rows(phone,e)),filter=phone.s.pendingFilter||phone.s.attrFilter||{};
  const options={aesthetic:unique(all.flatMap(r=>r.styleFacts.map(f=>f.value))),color:unique(all.map(r=>r.color).filter(Boolean)),form:['单品','套装','组合','小物']};
  return `<div class="content"><p class="caption">与活动类型、店铺共同筛选；颜色和内容必须对应同一实际规格。</p>${Object.entries(options).map(([key,values])=>`<div class="field"><label>${{aesthetic:'风格',color:'颜色',form:'购买内容'}[key]}<select class="attribute-select" data-filter="${key}"><option value="">全部</option>${values.map(v=>`<option value="${esc(v)}" ${filter[key]===v?'selected':''}>${esc(v)}</option>`).join('')}${key!=='form'?`<option value="__unknown__" ${filter[key]==='__unknown__'?'selected':''}>${key==='color'?'颜色未单列':'风格未标注'}</option>`:''}</select></label></div>`).join('')}<p class="notice">“古典”“哥特”来自商品参数或标题；“帝政风”只对应SET款式。不按照片或店名替其他款式补风格。</p>${button('清除商品筛选','clear-attributes','secondary')}</div>`;
 }
 function feed(phone){
  const events=catalog().events.filter(e=>matches(phone,e));
  const info=phone.s.kind==='再贩'?'<p class="notice"><strong>本次收录的再贩预约已结束</strong><br>可查看原款和批次；尾款开放不代表重新接单。</p>':phone.s.kind==='尾款'?'<p class="notice"><strong>仅供已付对应批次定金者核对</strong><br>店铺补款信息不代表你的个人待付。</p>':'';
  return controls(phone)+`<div class="feed-content"><div class="featured"><h3>${{全部:'店铺近况',上新:'上新与预告',现货:'现货与掉落',再贩:'再贩与批次',尾款:'尾款与截止时间'}[phone.s.kind]}</h3><small>${events.length}条动态</small></div>${info}${events.length?`<div class="product-grid">${events.map(e=>phone.card(e)).join('')}</div>`:phone.emptyContent()}<p class="caption" style="margin-top:20px">日期未知的商品不能依据采集日判为首次上架；颜色、内容、风格详见对应商品资料。</p></div>`;
 }
 function cardInfo(phone,event){
  const all=rows(phone,event),colors=unique(all.map(r=>r.color).filter(Boolean));
  const forms=unique(all.map(r=>r.form));
  return `<p class="product-traits">${esc(styleText(phone,event))}</p><p class="caption product-range">${colors.length?colors.length+'色':'颜色未单列'} · ${forms.join(' / ')}</p>`;
 }
 function detailSummary(phone){
  const all=rows(phone),colors=unique(all.map(r=>r.color).filter(Boolean));
  return `<div class="detail-properties"><span>${esc(styleText(phone))}</span><span>${colors.length?colors.map(esc).join(' / '):'颜色未单列'}</span></div>${button('查看套装、小物与商品资料','information','secondary info-entry')}`;
 }
 function information(phone){
  const event=phone.event(),products=event.ids.map(id=>catalog().products[id]).filter(p=>!p.isDeposit);
  const all=rows(phone),colors=unique(all.map(r=>r.color).filter(Boolean));
  const styleFacts=unique(all.flatMap(r=>r.styleFacts.map(f=>f.value+'（'+f.source+'）')));
  const choices=unique(all.map(r=>r.content));
  const chosen=selected(phone);
  return `<div class="content"><div class="selection-preview"><img src="${event.cover}" alt="${esc(event.title)}"><div><h4>${event.title}</h4><p class="shop-label">${shopName(event.shop)}</p><p class="caption">${event.subtitle}</p></div></div><section class="panel"><h3 class="section-title">颜色与风格</h3><div class="tag-row">${colors.length?colors.map(c=>`<span class="tag">${esc(c)}</span>`).join(''):'<span class="tag">颜色未单列</span>'}</div><p>风格：${styleFacts.length?styleFacts.map(esc).join(' / '):'未明确标注'}</p><p class="caption">${event.shop==='neo'?'帝政风来自SET款式名，不代表衬衫、SK或其他小物的风格。':'颜色需和款式对应，不能任意拼出不存在的规格。'}</p>${products[0].info.season?`<p>适用季节：${esc(products[0].info.season)}（商品参数）</p>`:''}</section><section class="panel"><h3 class="section-title">购买内容</h3><p class="caption">${event.id==='pilgrim-repeat'?'裙装与衬衫小物两个链接均可查看。':'单品、小物与组合独立列出。'}</p>${choices.map(content=>{const r=all.find(x=>x.content===content);return `<div class="content-choice"><span class="tag">${r.form}</span><div><strong>${esc(content)}</strong><small>${r.parts.map(p=>esc(p.name)).join(' ＋ ')}${r.notSoldAlone?' · 商家注明不单售':''}</small></div></div>`}).join('')}</section><section class="panel"><h3 class="section-title">包含与另购</h3>${chosen?`<p><strong>所选SKU包含：</strong>${chosen.parts.map(p=>esc(p.name)).join(' ＋ ')}</p>`:'<p class="caption">选择具体SKU后确定本次包含；同系列小物不会全部附送。</p>'}${sourceList(unique(products.flatMap(p=>p.info.included)))}<h4 class="subheading">未包含 / 未明确包含</h4>${sourceList(unique(products.flatMap(p=>p.info.excluded)))}</section><section class="panel"><h3 class="section-title">赠品条件</h3>${sourceList(unique(products.flatMap(p=>p.info.gifts)))}<p class="caption">这些是来源页的赠品条件，不表示当前选择已符合。配套领花、托领、胸针另列；赠品不自动登记。</p></section><section class="panel"><h3 class="section-title">材质、版型与洗护</h3>${products.flatMap(p=>[['面料成分',p.info.materials],['版型工艺',p.info.craft],['洗护',p.info.care]].filter(([,v])=>v).map(([label,v])=>`<details class="component-evidence"><summary>${label} · ${esc(p.title)}</summary>${sourceList(lines(v))}</details>`)).join('')}${products.map(p=>p.info.components.map(c=>`<details class="component-evidence"><summary>${esc(c.name)}</summary><p>${esc(c.fabric||c.type||'材质按对应官方详情图核对')}</p>${c.cut?`<p>${esc(c.cut)}</p>`:''}</details>`).join('')).join('')}<p class="caption">不同款式的尺码与洗护分别适用，不能把主裙说明套到KC或内搭。</p></section></div>`;
 }
 function sku(phone){
  const event=phone.event(),row=restoreChoice(phone),all=rows(phone);
  const contents=unique(all.map(r=>r.content));
  const variants=all.filter(r=>r.content===phone.s.content),colors=unique(variants.map(colorValue));
  const sized=variants.filter(r=>colorValue(r)===phone.s.color),sizes=unique(sized.map(r=>r.size||'__unknown__'));
  return `<div class="content"><p class="steps"><strong>选择规格</strong><span>›</span><span>核对资料</span><span>›</span><span>保存</span></p><div class="selection-preview"><img src="${product(phone).previews[0]}" alt="${esc(product(phone).title)}"><div><h4>${event.title}</h4><p class="shop-label">${shopName(event.shop)}</p><p class="price">${esc(phone.amount(row?(event.kind==='再贩'?'下架页标价 ':'')+money(row.price):event.price))}</p><p class="caption">${event.kind==='再贩'?'已结束页面 · 不预填参考金额':'报价对应实际SKU，保存不记付款'}</p></div></div><div class="field"><label>购买内容<select class="content-select"><option value="">请选择单品 / 套装 / 组合</option>${contents.map(c=>`<option value="${esc(c)}" ${phone.s.content===c?'selected':''}>${esc(c)}</option>`).join('')}</select></label></div><div class="field"><label>颜色<select class="color-select" ${phone.s.content?'':'disabled'}><option value="">请选择颜色</option>${colors.map(c=>`<option value="${esc(c)}" ${phone.s.color===c?'selected':''}>${c==='__unknown__'?'未单列颜色':esc(c)}</option>`).join('')}</select></label></div><div class="field"><span class="field-label">尺码</span><div class="choice-wrap">${sizes.length?sizes.map(s=>button(s==='__unknown__'?'未提供':esc(s),'size:'+s,'choice'+(phone.s.size===s?' active':''))).join(''):'<p class="caption">先选购买内容与颜色，再查看实际尺码。</p>'}</div></div>${row?`<section class="panel selection-facts"><h3 class="section-title">本次包含</h3>${row.parts.map(part=>`<div class="part-summary"><strong>${esc(part.name)}</strong><span>${esc(part.color||'颜色待确认')} / ${esc(part.size||'尺码待确认')}</span></div>`).join('')}<p class="caption">${row.parts.length>1?'整组颜色、尺码保留原文；配件规格没有分别说明时不沿用主裙。':'本次为独立SKU，同系列其他物品不自动加入。'}</p><div class="kv"><span>页面状态</span><strong>${esc(row.stock)}</strong></div>${row.notSoldAlone?'<p class="notice">商家注明不单售，可先留心愿，购买需另核对组合要求。</p>':''}<details><summary class="inline-action">查看完整规格原文</summary>${Object.entries(row.props).map(([k,v])=>`<p>${esc(k)}：${esc(v)}</p>`).join('')}<p class="caption">商品 ${row.itemId} / SKU ${row.id}</p></details></section>`:''}<p class="caption">零预选；没有合适规格可暂不选择，颜色、尺码和金额留空后核对。</p>${button('查看套装、小物与赠品说明','information','inline-action')}</div>`;
 }
 function partInputs(phone,row){
  if(!phone.s.selectedParts)phone.s.selectedParts=row.parts.map(p=>p.key);
  return row.parts.map(part=>{
   const selected=phone.s.selectedParts.includes(part.key),over=phone.s.partOverrides?.[part.key]||{};
   return `<article class="registration-unit"><label class="check"><input class="part-check" type="checkbox" data-part="${esc(part.key)}" ${selected?'checked':''}><span><strong>${esc(part.name)}</strong><br><span class="caption">${part.isAccessory?'配套小物候选':'独立服饰'} · ${esc(part.category||'类型待确认')}</span></span></label>${selected?`<div class="unit-fields"><label>颜色<input type="text" class="part-color" data-part="${esc(part.key)}" placeholder="待确认，可留空" value="${esc(over.color??part.color??'')}"></label><label>尺码<input type="text" class="part-size" data-part="${esc(part.key)}" placeholder="未分别提供" value="${esc(over.size??part.size??'')}"></label></div>`:''}</article>`;
  }).join('');
 }
 function registration(phone,row){
  if(!row||row.parts.length<2)return '';
  const split=phone.s.registrationMode==='split';
  const garments=row.parts.filter(p=>!p.isAccessory&&(phone.s.selectedParts||row.parts.map(p=>p.key)).includes(p.key));
  const canLink=row.parts.some(p=>p.isAccessory&&(phone.s.selectedParts||row.parts.map(p=>p.key)).includes(p.key));
  return `<section class="panel"><h3 class="section-title">组合如何登记</h3><div class="choice-wrap">${button('整组登记','registration:group','choice'+(!split?' active':''))}${button('分件登记','registration:split','choice'+(split?' active':''))}</div><p class="caption">${split?'每条物品独立保留类型与规格；购买组合不等于衣橱套装归属。':'保存1条成组物品，完整保留包含清单；不额外创建套装或小物记录。'}</p>${split?partInputs(phone,row)+`<div class="field"><label>衣橱套装归属<select class="coordinate-select"><option value="none">不关联套装</option><option value="new" ${phone.s.coordinate==='new'?'selected':''}>新建一个套装</option></select></label>${phone.s.coordinate==='new'?`<input class="coordinate-name" aria-label="新套装名称" type="text" value="${esc(phone.s.coordinateName||phone.event().title+' · '+(row.color||'所选组合'))}">`:''}</div>${canLink&&garments.length?`<div class="field"><label>小物关联至主件（可选）<select class="parent-select"><option value="">不关联主件</option>${garments.map(p=>`<option value="${esc(p.key)}" ${phone.s.accessoryParent===p.key?'selected':''}>${esc(p.name)}</option>`).join('')}</select></label><p class="caption">只关联已选小物。内搭等服饰保留独立成员；本次不建立共价订单。</p></div>`:''}`:`<div class="included-list">${row.parts.map(p=>`<p>${esc(p.name)} <span>${esc(p.color||'颜色未分别提供')} / ${esc(p.size||'尺码未分别提供')}</span></p>`).join('')}</div>`}<p class="notice">${split?'组合报价仅保留为来源证据，不分摊到单件，也不重复保存成多条参考金额。':'组合参考总价只保存一次；购买和付款仍需另行登记。'}</p></section>`;
 }
 function confirm(phone){
  const event=phone.event(),row=restoreChoice(phone),p=product(phone),split=row?.parts.length>1&&phone.s.registrationMode==='split';
  phone.s.name??=event.title+(row?' · '+row.content+' / '+(row.color||'颜色未单列')+' / '+(row.size||'尺码未知'):'');
  const category=phone.s.category??row?.parts[0]?.category??'';
  const categories=unique(['JSK','OP','SK','衬衫','KC','Bonnet','罩裙','裤装','发圈','手袖','胸针','其他服装','其他配饰',...rows(phone).flatMap(r=>r.parts.map(p=>p.category).filter(Boolean))]);
  const styles=unique(row?.styleFacts.map(f=>f.value)||[]);
  const recordStyle=phone.s.recordStyle??styles[0]??'';
  const eligible=row&&!split&&event.kind!=='再贩'&&event.state!=='预告';
  return `<div class="content"><p class="steps"><span>选择规格</span><span>›</span><strong>核对资料</strong><span>›</span><span>保存</span></p><div class="selection-preview"><img src="${p.previews[0]}" alt="${esc(p.title)}"><div><h4>${event.title}</h4><p class="caption">${row?esc(Object.values(row.props).join(' / ')):'未选择规格'}</p></div></div>${registration(phone,row)}<section class="panel"><h3 class="section-title">${split?'登记摘要':'物品资料'}</h3><div class="field"><label>名称<input class="item-name" type="text" value="${esc(phone.s.name)}"></label></div><div class="kv"><span>品牌</span><strong>${shopName(event.shop)}</strong></div>${!split?`<div class="field"><label>类型<select class="category-select"><option value="">请选择类型</option>${categories.map(t=>`<option value="${esc(t)}" ${t===category?'selected':''}>${esc(t)}</option>`).join('')}</select></label></div><div class="field"><label>颜色<input class="record-color" type="text" placeholder="未明确，可留空" value="${esc(phone.s.recordColor??row?.color??'')}"></label></div>`:`<p>所选 ${(phone.s.selectedParts||[]).length} 条物品分别保存，颜色和尺码按各件填写。</p>`}<div class="field"><label>风格<select class="record-style"><option value="">未设置</option>${styles.map(s=>`<option value="${esc(s)}" ${recordStyle===s?'selected':''}>${esc(s)}</option>`).join('')}</select></label><p class="caption">${row?.styleFacts.length?row.styleFacts.map(f=>esc(f.value+' · '+f.source)).join('；'):'当前款式风格未明确，不根据图片补写。'}</p></div><div class="kv"><span>拥有状态</span><strong>心愿</strong></div></section><section class="panel"><h3 class="section-title">参考金额</h3>${eligible?`<label class="check"><input class="reference-check" type="checkbox" ${phone.s.reference?'checked':''}><span>保存${row.parts.length>1?'组合参考总价':'所选规格参考金额'}<br><strong>${esc(phone.amount(money(row.price)))}</strong><br><span class="caption">${event.captured}采集 · ${esc(row.priceType)}</span></span></label>`:`<p>${split?'分件后的单件参考金额均留空；组合报价保留在来源中。':event.kind==='再贩'?'已结束页面标价不作为参考金额。':event.state==='预告'?'预告价格待核对，参考金额留空。':'未选择完整规格，参考金额留空。'}</p>`}<p class="caption">不建立订单、实付或待付。</p></section><section class="panel"><h3 class="section-title">赠品与未包含物品</h3>${sourceList(p.info.gifts)}<p class="caption">赠品条件尚未核对，不自动生成记录。模特搭配、未注明包含的裙撑鞋袜也不加入。</p></section><section class="panel"><h3 class="section-title">图片与说明</h3><p>保留所选商品的原图、详情、适用尺码图和来源；${p.imageCount}张图片待核对。</p><p class="caption">分件后仍有完整详情，封面和适用尺码需逐件核对，不把合照自动裁成单品图。</p></section>${button('进入完整编辑','edit-demo','inline-action')}</div>`;
 }
 function snapshot(phone){
  const row=selected(phone),split=row?.parts.length>1&&phone.s.registrationMode==='split';
  const style=phone.root.querySelector('.record-style')?.value||phone.s.recordStyle||null;
  const parts=split?row.parts.filter(p=>phone.s.selectedParts?.includes(p.key)):[];
  return {name:phone.s.name,sourceKey:row?.key||null,mode:split?'split':'group',coordinate:split?phone.s.coordinate:'none',coordinateName:phone.root.querySelector('.coordinate-name')?.value.trim()||phone.s.coordinateName||null,sourceQuote:row?.price??null,
   units:split?parts.map(p=>{const over=phone.s.partOverrides?.[p.key]||{};return {name:p.name,category:p.category,color:over.color??p.color,size:over.size??p.size,style,parent:p.isAccessory?phone.s.accessoryParent||null:null,reference:null}}):[{name:phone.s.name,category:phone.root.querySelector('.category-select')?.value||null,color:phone.s.recordColor??row?.color??null,size:row?.size||null,style,parent:null,reference:phone.s.reference&&row?row.price:null,composition:row?.parts.length>1?'SET':'UNCONFIRMED'}]};
 }
 return {rows,selected,product,choose,restoreChoice,facts,controls,attributes,feed,cardInfo,detailSummary,information,sku,confirm,snapshot};
})();
