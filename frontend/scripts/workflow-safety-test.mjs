import assert from "node:assert/strict";
import {createRequire} from "node:module";
const require=createRequire(import.meta.url);
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||"playwright");
const browser=await chromium.launch({channel:"msedge",headless:true});
const base=process.env.VISUAL_BASE_URL||"http://127.0.0.1:5186";
const failures=[];
async function session(role="SERVICE_PROVIDER",width=1440) {
  const context=await browser.newContext({viewport:{width,height:1000},reducedMotion:"reduce"});
  await context.addInitScript(role=>{
    if(role){localStorage.setItem("user",JSON.stringify({id:1,fullName:"Test User",email:"test@example.test",role}));localStorage.setItem("token","mock-token");}
  },role);
  const project={id:1,applicationId:1,applicantId:1,title:"Community Water Project",description:"Clean water infrastructure",approvedBudget:100000,timeline:"Six months",expectedCompletionDate:"2027-01-01",status:"ACTIVE",version:0};
  const phases=[{id:1,projectId:1,phaseName:"Water assessment",projectTitle:project.title,description:"Site evidence and assessment",allocatedBudget:25000,phaseOrder:1,status:"IN_PROGRESS",startedAt:"2026-09-01T09:00:00",deadline:"2026-09-15",version:0}];
  const payments=[{id:1,projectId:1,phaseId:1,amount:25000,status:"RELEASED",paymentReference:"BANK-001",releasedAt:"2026-09-01T08:00:00",version:0}];
  const evidence=[{id:1,phaseId:1,uploadedBy:1,providerName:"River Provider",fileName:"site-invoice.pdf",fileType:"application/pdf",fileSize:1024,status:"REJECTED",verificationComment:"The invoice amount does not match.",uploadedAt:"2026-09-10T11:00:00",verifiedAt:"2026-09-11T12:00:00",version:1}];
  const apps=[{id:1,title:project.title,description:project.description,requestedBudget:100000,timeline:"Six months",status:"PENDING",version:0}];
  const requests=[];
  let failUpload=false,expire=false;
  await context.route("**/api/**",async route=>{
    const request=route.request(),path=new URL(request.url()).pathname,method=request.method();
    requests.push({path,method,data:request.postData(),headers:request.headers()});
    const respond=(data,status=200)=>route.fulfill({status,contentType:"application/json",body:JSON.stringify(data)});
    if(expire)return respond({message:"Your session has expired. Please log in again."},401);
    if(path==="/api/auth/register")return respond({message:"Account created, but email could not be sent.",emailVerificationRequired:true,codeSent:false},202);
    if(path==="/api/auth/verify-email")return new URLSearchParams(request.postData()).get("code")==="123456"?respond({message:"Email verified. You can now sign in."}):respond("The verification code is incorrect.",400);
    if(path==="/api/auth/resend-verification")return respond({message:"A new verification code has been sent."});
    if(path.includes("/notifications/"))return respond(path.includes("unread-count")?{unreadCount:0}:[]);
    if(path.endsWith("/activity"))return respond([{id:1,action:"PHASE_STARTED",description:"Provider started Water assessment.",createdAt:"2026-09-01T09:00:00",userEmail:"provider@example.test",userRole:"SERVICE_PROVIDER"}]);
    if(method==="GET"){
      if(path==="/api/projects/my"||path==="/api/projects")return respond([project]);
      if(path.startsWith("/api/project-phases"))return respond(phases);
      if(path==="/api/disbursements/my"||path==="/api/disbursements")return respond(payments);
      if(path==="/api/evidence/my"||path==="/api/evidence")return respond(evidence);
      if(path.startsWith("/api/applications"))return respond(apps);
      return respond([]);
    }
    if(path==="/api/applications/1/reject"){apps[0].status="REJECTED";return respond({...apps[0],version:1});}
    if(path==="/api/disbursements/1/release"){payments[0].status="RELEASED";payments[0].paymentReference=new URLSearchParams(request.postData()).get("paymentReference");return respond({...payments[0],version:1});}
    if(path==="/api/disbursements/1/proof"){
      await new Promise(r=>setTimeout(r,150));
      if(failUpload)return respond("File storage is unavailable.",400);
      Object.assign(payments[0],{proofName:"proof.pdf",proofType:"application/pdf",proofSize:1000,proofUploadedAt:"2026-10-06T12:00:00",version:2});return respond(payments[0]);
    }
    if(path==="/api/evidence/phase/1/batch"){
      await new Promise(r=>setTimeout(r,250));
      if(failUpload)return respond("Upload failed. Your evidence was not submitted.",400);
      evidence.push({...evidence[0],id:2,status:"PENDING",verificationComment:null,uploadedAt:new Date().toISOString()});return respond([evidence[1]]);
    }
    return respond({});
  });
  const page=await context.newPage();page.setDefaultTimeout(15000);page.on("pageerror",e=>failures.push(e.message));
  return {context,page,requests,payments,failUpload:value=>{failUpload=value;},expire:()=>{expire=true;}};
}
try{
  const publicSession=await session(null);let page=publicSession.page;
  await page.goto(base+"/verify-email");await page.getByLabel("Email address").fill("provider@example.test");
  await page.getByLabel("Verification code").fill("000000");await page.getByRole("button",{name:"Verify email",exact:true}).click();
  await page.getByText("The verification code is incorrect.",{exact:true}).waitFor();
  await page.getByRole("button",{name:"Resend Code"}).click();await page.getByText("A new verification code has been sent.",{exact:true}).waitFor();await page.getByRole("button",{name:"Done",exact:true}).click();
  await page.getByLabel("Verification code").fill("123456");await page.getByRole("button",{name:"Verify email",exact:true}).click();
  await page.getByRole("link",{name:"Continue to sign in"}).waitFor();await publicSession.context.close();

  for(const width of [390,1440]){
    const s=await session("SERVICE_PROVIDER",width);page=s.page;
    await page.goto(base+"/provider/projects");await page.getByText("Remaining project funds",{exact:true}).waitFor();
    assert.match(await page.locator("main").innerText(),/75.?000/);
    await page.getByText("Project activity timeline",{exact:true}).click();await page.getByText("Provider started Water assessment.",{exact:true}).waitFor();
    assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
    await page.screenshot({path:"artifacts/workflow-provider-"+width+".png",fullPage:true});
    await page.goto(base+"/provider/evidence");
    await page.locator("#evidence-file").setInputFiles([
      {name:"site-evidence.pdf",mimeType:"application/pdf",buffer:Buffer.from("%PDF-1.4 site")},
      {name:"invoice.pdf",mimeType:"application/pdf",buffer:Buffer.from("%PDF-1.4 invoice")}
    ]);
    assert.equal(await page.locator(".evidence-selected-files li").count(),2);
    await page.getByRole("button",{name:"Remove invoice.pdf",exact:true}).click();
    assert.equal(await page.locator(".evidence-selected-files li").count(),1);
    assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
    const heading=await page.locator("main h1").evaluate(el=>({size:parseFloat(getComputedStyle(el).fontSize),weight:getComputedStyle(el).fontWeight,align:getComputedStyle(el).textAlign}));
    assert.ok(heading.size>=32);assert.equal(heading.weight,"700");assert.equal(heading.align,"center");
    await page.screenshot({path:"artifacts/evidence-selected-"+width+".png",fullPage:true});await s.context.close();
  }
  const provider=await session();page=provider.page;
  await page.goto(base+"/provider/applications/new");await page.getByLabel("Project title",{exact:true}).fill("Unsaved funding request");
  await page.locator(".workspace-sidebar").getByRole("link",{name:"Projects",exact:true}).click();
  await page.getByRole("heading",{name:"Unsaved changes",exact:true}).waitFor();
  await page.getByRole("button",{name:"Keep editing",exact:true}).click();assert.match(page.url(),/applications\/new/);
  await page.locator(".workspace-sidebar").getByRole("link",{name:"Projects",exact:true}).click();
  await page.getByRole("button",{name:"Leave page",exact:true}).click();await page.waitForURL("**/provider/projects");
  await page.goto(base+"/provider/evidence");
  await page.locator("#evidence-file").setInputFiles([{name:"corrected.pdf",mimeType:"application/pdf",buffer:Buffer.from("%PDF-1.4 test")},{name:"invoice.pdf",mimeType:"application/pdf",buffer:Buffer.from("%PDF-1.4 invoice")}]);
  assert.equal(await page.locator(".evidence-selected-files li").count(),2);
  provider.failUpload(true);await page.getByRole("button",{name:"Upload evidence",exact:true}).click();
  await page.getByText("Upload failed. Your evidence was not submitted.",{exact:true}).waitFor();
  provider.failUpload(false);await page.getByRole("button",{name:"Upload evidence",exact:true}).click();
  await page.getByRole("heading",{name:"Evidence uploaded",exact:true}).waitFor();
  assert.match(await page.getByRole("dialog").innerText(),/2 evidence files/);
  assert.equal(provider.requests.filter(r=>r.path.endsWith("/batch")&&r.method==="POST").length,2);
  await page.screenshot({path:"artifacts/evidence-success-popup.png",fullPage:true});
  await page.getByRole("button",{name:"Done",exact:true}).click();
  assert.equal(await page.locator(".evidence-selected-files li").count(),0);
  await provider.context.close();

  const admin=await session("ADMINISTRATOR");page=admin.page;
  await page.goto(base+"/admin/applications");await page.getByRole("button",{name:"Reject",exact:true}).click();
  await page.getByRole("button",{name:"Reject application",exact:true}).click();
  assert.equal(admin.requests.filter(r=>r.path.endsWith("/reject")).length,0);
  await page.getByLabel("Reason for rejection").fill("Budget explanation is incomplete.");
  await page.getByRole("button",{name:"Reject application",exact:true}).click();
  await page.getByText("Application rejected successfully.",{exact:true}).waitFor();
  const rejection=admin.requests.find(r=>r.path.endsWith("/reject"));assert.equal(rejection.headers["if-match"],"0");
  assert.equal(new URLSearchParams(rejection.data).get("rejectionReason"),"Budget explanation is incomplete.");
  admin.payments[0].status="PENDING";
  await page.goto(base+"/admin/disbursements");await page.getByRole("button",{name:"Release funds",exact:true}).click();
  await page.getByRole("heading",{name:"Record payment",exact:true}).waitFor();
  assert.match(await page.getByRole("dialog").innerText(),/Community Water Project/);
  await page.getByLabel("Payment reference",{exact:true}).fill("BANK-SAFE-25");
  await page.getByRole("button",{name:"Confirm payment",exact:true}).click();
  await page.getByText("Payment recorded. You can now attach proof of payment.",{exact:true}).waitFor();await page.getByRole("button",{name:"Done",exact:true}).click();
  admin.failUpload(true);
  await page.locator('input[type="file"]').setInputFiles({name:"proof.pdf",mimeType:"application/pdf",buffer:Buffer.from("%PDF-1.4 test")});
  await page.getByRole("button",{name:"Confirm",exact:true}).click();
  await page.getByText(/Payment remains recorded, but proof was not uploaded/).waitFor();
  assert.equal(admin.payments[0].status,"RELEASED");
  await page.screenshot({path:"artifacts/workflow-admin-proof-failure.png",fullPage:true});
  await page.goto(base+"/admin/evidence");await page.getByText("River Provider",{exact:true}).waitFor();
  await admin.context.close();

  const expired=await session();expired.expire();page=expired.page;
  await page.goto(base+"/provider/projects");await page.waitForURL("**/login");
  await page.getByText("Your session has expired. Please log in again.",{exact:true}).waitFor();await expired.context.close();
  assert.deepEqual(failures,[]);
  console.log("PASS verification errors/resend, responsive project transparency, unsaved navigation, upload failure/retry, rejection confirmation/version, payment reference, partial proof failure, Admin evidence access, session expiry.");
}finally{await browser.close();}
