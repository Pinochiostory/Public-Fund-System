import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { phaseStartBlockedReason } from "../src/components/phaseEligibility.js";
const require = createRequire(import.meta.url);
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const base = process.env.VISUAL_BASE_URL || "http://127.0.0.1:5173";
const project={id:1,status:"ACTIVE"};
const candidate={id:3,projectId:1,status:"FUNDED",phaseOrder:3};
assert.match(phaseStartBlockedReason(candidate,project,[{id:1,projectId:"1",status:"PENDING_APPROVAL",phaseOrder:1},{id:2,projectId:1,status:"COMPLETED",phaseOrder:2}]),/Phase 1/);
assert.match(phaseStartBlockedReason(candidate,project,[{id:1,projectId:1,status:"EVIDENCE_APPROVED",phaseOrder:1}]),/completed/);
assert.equal(phaseStartBlockedReason(candidate,project,[{id:1,projectId:1,status:"COMPLETED",phaseOrder:1},{id:2,projectId:1,status:"REJECTED",phaseOrder:2}]),"");
assert.equal(phaseStartBlockedReason({...candidate,phaseOrder:1},project,[{id:4,projectId:1,status:"FUNDED",phaseOrder:4}]),"");
assert.match(phaseStartBlockedReason(candidate,{...project,status:"COMPLETED"},[]),/not active/);
const browser=await chromium.launch({channel:"msedge",headless:true});
const failures=[];
try {
 for(const [width,height] of process.argv.includes("--motion") ? [] : process.argv.includes("--short") ? [[1024,650]] : [[1440,900],[1366,768],[1280,720],[1024,650],[768,1024],[390,844],[375,667],[320,740]]) {
  const ctx=await browser.newContext({viewport:{width,height},reducedMotion:"reduce"});
  await ctx.route("https://accounts.google.com/gsi/client", route => route.fulfill({contentType:"application/javascript", body:"window.google={accounts:{id:{initialize(){},renderButton(node){const b=document.createElement('button');b.textContent='Continue with Google';b.style.height='44px';node.appendChild(b)}}}}"}));
  await ctx.route("**/api/**",route=>route.fulfill({json:{clientId:"test-client",nonce:"test-nonce"}}));
  const page=await ctx.newPage();
  for(const path of ["/login","/register","/forgot-password","/reset-password#token=layout-test"]) {
    await page.goto(base+path);await page.locator(".auth-form-stage").waitFor();
    await page.evaluate(()=>document.fonts.ready);
    const fit=await page.evaluate(()=>({height:document.documentElement.scrollHeight,width:document.documentElement.scrollWidth,viewport:innerHeight,font:getComputedStyle(document.body).fontFamily}));
    if(fit.height>height+1 || fit.width>width+1) {
      failures.push({width,height,path,...fit});
      console.log(await page.locator(".login-entry-panel").evaluate(el=>[el,...el.querySelectorAll(".login-entry-main,.auth-form-stage,h1,.register-description,.login-mode-nav,.register-form,.auth-field,.password-help,.register-button,.google-sign-in,.google-button-host,.google-sign-in>p,.login-entry-footer")].map(e=>({element:e.className||e.tagName,height:e.getBoundingClientRect().height,margin:getComputedStyle(e).margin,padding:getComputedStyle(e).padding}))));
    }
    assert.match(fit.font,/Montaga/);
    if(width>=768) assert.equal(await page.locator(".login-portrait img").evaluate(el=>getComputedStyle(el).borderTopLeftRadius),"20px");
    if(width===1440 || width===390)await page.screenshot({path:"artifacts/account-"+path.split(/[/#]/).filter(Boolean)[0]+"-"+width+".png",fullPage:true});
  }
  await ctx.close();
 }
 console.log("Measured layout exceptions:",JSON.stringify(failures));
 const ctx=await browser.newContext({viewport:{width:1366,height:768},reducedMotion:"no-preference"});
 await ctx.route("**/api/**",route=>route.fulfill({contentType:"application/json",body:JSON.stringify({clientId:""})}));
 const page=await ctx.newPage();await page.goto(base+"/login");
 await page.getByRole("link",{name:"Register",exact:true}).click();
 await page.waitForURL("**/register");
 await page.waitForFunction(() => document.querySelector('[data-auth-page="register"] .auth-form-stage')?.getAnimations().length > 0);
 assert.match(await page.locator(".auth-form-stage").evaluate(el=>getComputedStyle(el).animationName),/auth-form-arrive/,"Incoming register animation exists");
 await page.getByRole("link",{name:"Sign in",exact:true}).click();
 await page.waitForURL("**/login");
 await page.waitForFunction(() => document.querySelector('[data-auth-page="login"] .auth-form-stage')?.getAnimations().length > 0);
 assert.match(await page.locator(".auth-form-stage").evaluate(el=>getComputedStyle(el).animationName),/auth-form-arrive/);
 await ctx.close();
 console.log("Layout exceptions:",JSON.stringify(failures));
 assert.deepEqual(failures,[],"All account forms fit the tested viewports");
 console.log(process.argv.includes("--motion") ? "PASS bidirectional slide/fade motion." : process.argv.includes("--short") ? "PASS short-screen account layouts and motion." : "PASS: Montaga, rounded artwork, 32 account layouts, bidirectional motion and phase eligibility.");
} finally {await browser.close();}
