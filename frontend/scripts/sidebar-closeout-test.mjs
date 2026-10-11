import assert from "node:assert/strict";
import {createRequire} from "node:module";
const require=createRequire(import.meta.url);
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||"playwright");
const browser=await chromium.launch({channel:"msedge",headless:true});
const base=process.env.VISUAL_BASE_URL||"http://127.0.0.1:5173";
try {
 const ctx=await browser.newContext({viewport:{width:1366,height:768},reducedMotion:"reduce"});
 await ctx.addInitScript(()=>{
   localStorage.setItem("user",JSON.stringify({id:2,fullName:"Test Reviewer",email:"reviewer@example.test",role:"ADMINISTRATOR"}));
   localStorage.setItem("token","test-token");
 });
 let project={id:1,applicantId:3,title:"Community project",description:"Test project",approvedBudget:100,status:"COMPLETED",version:1,createdAt:"2026-10-01T10:00:00",completedAt:"2026-10-07T10:00:00",closeoutApprovedBudget:100,closeoutReleasedAmount:60,closeoutRetainedAmount:40,closeoutInitiatedBy:1};
 let reviewed=0;
 await ctx.route("**/api/**",async route=>{
   const path=new URL(route.request().url()).pathname;
   if(path==="/api/projects/1/closeout-review"){
     reviewed++;assert.equal(route.request().headers()["if-match"],"1");
     project={...project,version:2,closeoutReviewedBy:2,closeoutReviewedAt:"2026-10-07T11:00:00",closeoutReference:"LEDGER-1",closeoutNote:"Reconciled with funder"};
     return route.fulfill({json:project});
   }
   const data=path==="/api/projects"?[project]:path==="/api/disbursements"?[{id:1,phaseId:1,projectId:1,amount:60,status:"RELEASED"}]:
     path.startsWith("/api/project-phases")?[{id:1,projectId:1,phaseName:"Delivery",phaseOrder:1,status:"COMPLETED",allocatedBudget:60}]:[];
   return route.fulfill({json:data});
 });
 const page=await ctx.newPage();
 await page.goto(base+"/admin/projects");await page.getByRole("heading",{name:"Community project",exact:true}).waitFor();
 for(const [width,height] of [[1366,900],[1366,768],[1280,720],[1024,650],[1024,600],[390,844],[390,667],[844,390]]){
   await page.setViewportSize({width,height});
   if(width<1024 && await page.getByRole("button",{name:"Toggle navigation"}).getAttribute("aria-expanded")==="false")await page.getByRole("button",{name:"Toggle navigation"}).click();
   const fit=await page.locator(".workspace-sidebar").evaluate(el=>({client:el.clientHeight,scroll:el.scrollHeight}));
   assert.ok(fit.scroll<=fit.client+1,JSON.stringify({width,height,...fit}));
   for(const link of await page.locator('.workspace-sidebar nav a').all())assert.ok(await link.isVisible());
   const toggle=await page.getByRole("switch",{name:"Dark mode"}).boundingBox();
   assert.ok(toggle.y+toggle.height<=height);
   if(width<1024)await page.keyboard.press("Escape");
 }
 await page.setViewportSize({width:1366,height:900});
 await page.getByLabel("Funder reconciliation reference").fill("LEDGER-1");
 await page.getByLabel("Reconciliation explanation").fill("Reconciled with funder");
 await page.getByRole("button",{name:"Review retained allocation"}).click();
 await page.getByRole("dialog").waitFor();
 assert.match(await page.getByRole("dialog").innerText(),/R\s*40/);
 await page.getByRole("button",{name:"Record review",exact:true}).click();
 await page.getByRole("status").filter({hasText:"Independent review recorded"}).waitFor();
 assert.equal(reviewed,1);
 await page.screenshot({path:"artifacts/closeout-admin.png",fullPage:true});
 await ctx.close();
 console.log("PASS: complete sidebar fits 8 viewports; closeout confirmation, version header, save and refresh.");
}finally{await browser.close();}
