package com.lolita.app.ui.screen.`import`

import com.google.gson.JsonParser

/** Reads only visible product data; an open SKU panel takes precedence over initial SSR data. */
internal object TaobaoProductWebReader {
    val script = """
        JSON.stringify((()=>{
          const visible=e=>e&&e.getClientRects().length>0;
          const header=Array.from(document.querySelectorAll('[aria-label]')).find(e=>visible(e)&&(e.getAttribute('aria-label')||'').startsWith('价格：'));
          const selected=Array.from(document.querySelectorAll('[role="button"][aria-label]')).filter(e=>visible(e)&&(e.getAttribute('aria-label')||'').startsWith('已选中 '));
          const names=selected.map(e=>{const n=e.querySelector('[class^="skuValueName--"]');return n?n.textContent.trim():e.getAttribute('aria-label').replace(/^已选中\s+/,'').replace(/，\s*${'$'}/,'').trim()});
          const numbers=header?Array.from(header.querySelectorAll('[class^="priceText--"]')).map(e=>e.textContent.trim()):[];
          const priceText=numbers.length?numbers.join('-'):(header?header.textContent.trim():'');
          const priceSummary=header?(header.textContent+' '+header.getAttribute('aria-label')).trim():'';
          const descriptor=Array.from(document.querySelectorAll('[aria-label]')).find(e=>visible(e)&&(e.getAttribute('aria-label')||'').startsWith('已选“'));
          const selectedText=descriptor?descriptor.getAttribute('aria-label'):'';
          const priceTail=priceSummary.includes('起')?'起':'';
          const label=(priceSummary.match(/定金|订金|尾款|补款|补尾|预约|券后|优惠前/g)||[]).join(' ');
          const detailRoots=Array.from(document.querySelectorAll('#v6_pc_content,.detail-desc'));
          const detailNodes=Array.from(new Set(detailRoots.flatMap(e=>Array.from(e.querySelectorAll('img')))));
          const original=value=>{
            if(typeof value!=='string'||!value)return null;
            try{
              const u=new URL(value.startsWith('//')?'https:'+value:value);
              if(!(u.hostname==='alicdn.com'||u.hostname.endsWith('.alicdn.com'))||u.username||u.password||u.port)return null;
              if(u.protocol==='http:')u.protocol='https:';
              if(u.protocol!=='https:'||/-2-tps-(2-2|790-300)/.test(u.href))return null;
              return u.href;
            }catch(_){return null;}
          };
          const detailSource=i=>{
            for(const attr of ['data-src','data-original','data-lazyload','data-ks-lazyload']){
              const source=original(i.getAttribute(attr));if(source)return source;
            }
            // v7 keeps the public image URL in its lazy image component, before an img src exists.
            // Read only this image's bounded ancestor imageUrl field, never page/session state.
            const key=Object.keys(i).find(k=>k.startsWith('__reactFiber${'$'}'));
            for(let fiber=key?i[key]:null,n=0;fiber&&n<7;fiber=fiber.return,n++){
              if(fiber.stateNode&&fiber.stateNode.nodeType===1&&!detailRoots.some(root=>root.contains(fiber.stateNode)))break;
              const source=original(fiber.memoizedProps&&fiber.memoizedProps.imageUrl);
              if(source)return source;
            }
            return original(i.currentSrc)||original(i.src);
          };
          const detailSources=detailNodes.map(detailSource);
          const detailImages=Array.from(new Set(detailSources.filter(Boolean))).map(url=>({url}));
          const textOnly=detailNodes.length===0&&detailRoots.some(root=>Array.from(root.querySelectorAll('p')).some(p=>{
            const text=p.innerText.trim();
            return !/加载|重试|请稍|稍等|展开|暂无详情/.test(text)&&text.replace(/[\s.。…！!?？:：，,、;；]/g,'').length>0;
          }));
          const detailStatus=detailRoots.length>0&&((detailNodes.length>0&&detailSources.every(Boolean))||textOnly)?'complete':'pending';
          return {url:location.href,text:document.body.innerText,
            liveSku:{present:!!header||selected.length>0,selectedNames:names,selectedText,priceText:priceText||'',priceTitle:label,priceTail,priceSummary},
            detailImages,detailStatus,
            entry:window.__ICE_SUSPENSE_LOADER__&&window.__ICE_SUSPENSE_LOADER__.entry};
        })())
    """.trimIndent()
    fun consistentSnapshots(first: String, second: String): Boolean = runCatching {
        val a = JsonParser.parseString(first).asJsonObject
        val b = JsonParser.parseString(second).asJsonObject
        a.get("url") == b.get("url") && a.get("liveSku") == b.get("liveSku")
    }.getOrDefault(false)

}
