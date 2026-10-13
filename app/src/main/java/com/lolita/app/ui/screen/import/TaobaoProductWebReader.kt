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
          return {url:location.href,text:document.body.innerText,
            liveSku:{present:!!header||selected.length>0,selectedNames:names,selectedText,priceText:priceText||'',priceTitle:label,priceTail,priceSummary},
            detailImages:Array.from(document.querySelectorAll('#v6_pc_content img')).map(i=>({url:i.getAttribute('data-src')||i.currentSrc||i.src})),
            entry:window.__ICE_SUSPENSE_LOADER__&&window.__ICE_SUSPENSE_LOADER__.entry};
        })())
    """.trimIndent()
    fun consistentSnapshots(first: String, second: String): Boolean = runCatching {
        val a = JsonParser.parseString(first).asJsonObject
        val b = JsonParser.parseString(second).asJsonObject
        a.get("url") == b.get("url") && a.get("liveSku") == b.get("liveSku")
    }.getOrDefault(false)

}
