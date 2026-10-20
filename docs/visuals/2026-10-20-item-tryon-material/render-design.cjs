// Design-only rendering and interaction audit; does not run or modify the Android app.
const { chromium } = require(process.env.LOLITA_DESIGN_NODE_MODULES + '/playwright');
const fs = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');
const root = __dirname;
const url = 'file://' + path.join(root, 'prototype.html');
let browser;
(async () => {
  browser = await chromium.launch({headless:true,executablePath:process.env.LOLITA_DESIGN_CHROME || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
  const page = await browser.newPage({viewport:{width:1200,height:1100},deviceScaleFactor:1});
  const errors=[];
  page.on('pageerror', e=>errors.push(String(e)));
  const images=[];
  for (const [name,query,width] of [
    ['flow-light','board=flow',2106],
    ['saved-and-picker','board=reuse',864],
    ['skins-light','board=skins',2934],
    ['skins-dark','board=skins&dark=1',2934],
    ['states-light','board=states',1278],
    ['small-large-keyboard','board=adaptive',748],
  ]) {
    await page.setViewportSize({width,height:1100});
    await page.goto(url+'?'+query);
    await page.evaluate(()=>document.fonts.ready);
    await page.screenshot({path:path.join(root,name+'.png'),fullPage:true});
    const size=await page.evaluate(()=>({width:document.documentElement.scrollWidth,height:document.documentElement.scrollHeight}));
    images.push({file:name+'.png',...size});
  }
  await page.setViewportSize({width:900,height:1100});
  await page.goto(url+'?page=review');
  const save=page.locator('.footer .primary');
  if (!await save.isDisabled()) throw Error('Unreviewed material was saveable');
  for (const box of await page.locator('[data-check]').all()) await box.check();
  if (await save.isDisabled()) throw Error('Three confirmations did not enable saving');
  await save.click();
  if (await page.locator('.phone').getAttribute('data-page')!=='fuse') throw Error('Confirmation did not return to fusion');
  if (!await page.locator('.footer .primary').isDisabled()) throw Error('Unconfirmed dressed base was submitable');
  await page.locator('[data-base]').check();
  await page.locator('.footer .primary').click();
  if (await page.locator('.modal[aria-label="确认生成试衣图"]').count()!==1) throw Error('Fusion fee boundary missing');
  await page.locator('[data-final="result"]').click();
  if (await page.locator('.phone').getAttribute('data-page')!=='result') throw Error('Result was not shown');
  await page.selectOption('#page','prepare');
  await page.locator('.footer .primary').click();
  if (await page.locator('.modal[aria-label="确认生成素材"]').count()!==1) throw Error('Preparation fee boundary missing');
  await page.locator('.modal .primary').click();
  if (await page.locator('.phone').getAttribute('data-page')!=='queued') throw Error('Queue was not shown');
  await page.locator('#simulate').click();
  if (await page.locator('.phone').getAttribute('data-page')!=='review') throw Error('Local returned-result demo did not open review');
  const layout=[];
  for (const skin of ['default','gothic','chinese','classic','navy','country','victorian']) for (const dark of [0,1]) {
    await page.goto(url+`?page=review&skin=${skin}&dark=${dark}`);
    await page.evaluate(()=>document.fonts.ready);
    const audit=await page.evaluate(()=>{
      const phone=document.querySelector('.phone'), sc=phone.querySelector('.scroll');
      const boxes=[...phone.querySelectorAll('button,.section,.title,.choice')].map(el=>({el,r:el.getBoundingClientRect()}));
      const pr=phone.getBoundingClientRect();
      return {horizontalOverflow:sc.scrollWidth>sc.clientWidth+1,offscreen:boxes.filter(({r})=>r.left<pr.left-1||r.right>pr.right+1).map(({el})=>el.textContent),footerHeight:phone.querySelector('.footer').getBoundingClientRect().height,contentScroll:sc.scrollHeight>sc.clientHeight};
    });
    if(audit.horizontalOverflow||audit.offscreen.length)throw Error(JSON.stringify({skin,dark,...audit}));
    layout.push({skin,dark,...audit});
  }
  await page.setViewportSize({width:320,height:900});
  await page.goto(url+'?page=review&large=1');
  const narrow=await page.evaluate(()=>({pageOverflow:document.documentElement.scrollWidth>320,contentOverflow:document.querySelector('.scroll').scrollWidth>document.querySelector('.scroll').clientWidth,savePresent:!!document.querySelector('.footer .primary')}));
  if(narrow.pageOverflow||narrow.contentOverflow)throw Error('Small-screen overflow: '+JSON.stringify(narrow));
  if(errors.length)throw Error(errors.join('\n'));
  for(const img of images){const bytes=await fs.readFile(path.join(root,img.file));img.sha256=crypto.createHash('sha256').update(bytes).digest('hex');}
  const sources={};
  for(const name of ['prototype.html','render-design.cjs'])sources[name]=crypto.createHash('sha256').update(await fs.readFile(path.join(root,name))).digest('hex');
  await fs.writeFile(path.join(root,'design-verification.json'),JSON.stringify({kind:'HTML concept design only, not Android or real-service verification',date:'2026-10-20',browserVersion:browser.version(),sources,images,checks:{reviewConfirmation:true,dressedBaseConfirmation:true,separatePreparationAndFusionFeeDialogs:true,flowNavigation:true,narrow,layout},browserErrors:errors},null,2)+'\n');
  await browser.close();
  process.stdout.write(JSON.stringify({images,checks:'passed',browserErrors:errors}));
})().catch(e=>{process.stderr.write(String(e));process.exitCode=1}).finally(async()=>{if(browser?.isConnected())await browser.close()});
