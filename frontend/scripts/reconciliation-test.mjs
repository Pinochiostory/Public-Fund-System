import assert from "node:assert/strict";
import {createRequire} from "node:module";
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE||"playwright");
const browser=await chromium.launch({channel:"msedge",headless:true});
const base=process.env.VISUAL_BASE_URL||"http://127.0.0.1:5173";
const errors=[], requests=[];
const project={id:1,applicationId:1,applicantId:3,title:"Community Water Project",description:"Clean water infrastructure",approvedBudget:100000,timeline:"Six months",status:"PENDING_COMPLETION",version:1,createdAt:"2026-10-01T10:00:00"};
const phases=[{id:1,projectId:1,phaseName:"Water assessment",allocatedBudget:25000,phaseOrder:1,status:"COMPLETED",startedAt:"2026-10-01T10:00:00",completedAt:"2026-10-08T10:00:00"}];
const payments=[{id:1,projectId:1,phaseId:1,amount:25000,status:"RELEASED",paymentReference:"BANK-1"}];
const summary={projectVersion:1,releasedAmount:25000,undisbursedAmount:75000,approvedExpenditure:null,verifiedReturns:0,outstandingReturn:null,pendingReturn:0,status:"NOT_SUBMITTED",returnDestination:"TEST FUNDER ONLY — NOT A REAL ACCOUNT",destinationVersion:"test-destination",history:[]};
let failUpload=true;
async function session(role,id,width=1440){
 const ctx=await browser.newContext({viewport:{width,height:1000},reducedMotion:"reduce"});
 await ctx.addInitScript(({role,id})=>{localStorage.setItem("user",JSON.stringify({id,fullName:"Test User",email:"test@example.test",role}));localStorage.setItem("token","mock");},{role,id});
 await ctx.route("**/api/**",async route=>{
   const req=route.request(),path=new URL(req.url()).pathname;
   requests.push({path,method:req.method(),headers:req.headers(),data:req.postData()});
   const send=(data,status=200)=>route.fulfill({status,json:data});
   if(path.endsWith("/reconciliation")&&req.method()==="GET")return send(summary);
   if(path.endsWith("/reconciliation/declarations")){
     if(failUpload)return send("Upload failed. Your file was not stored; please try again.",400);
     assert.equal(req.headers()["if-match"],"1");
     summary.history.push({id:1,version:0,kind:"DECLARATION",amount:20000,status:"PENDING",submittedBy:3,submittedAt:"2026-10-11T09:00:00",note:"Receipts attached",fileName:"expenses.pdf",fileType:"application/pdf",fileSize:2000});
     summary.status="PENDING_REVIEW";return send(summary.history[0]);
   }
   if(path.endsWith("/reconciliation/returns")){
     assert.equal(req.headers()["if-match"],"1");
     summary.history.push({id:2,version:0,kind:"RETURN",declarationId:1,amount:5000,status:"PENDING",submittedBy:3,submittedAt:"2026-10-11T10:00:00",note:"Unused advance returned",fileName:"return.pdf",fileType:"application/pdf",fileSize:2000,transferReference:"REFUND-1",transferredOn:"2026-10-11",returnDestination:summary.returnDestination});
     summary.status="RETURN_UNDER_REVIEW";summary.pendingReturn=5000;return send(summary.history[1]);
   }
   if(path.endsWith("/review")){
     const r=summary.history.find(r=>path.endsWith("/"+r.id+"/review"));
     const params=new URLSearchParams(req.postData());assert.equal(req.headers()["if-match"],String(r.version));
     r.status="APPROVED";r.reviewedBy=id;r.reviewedAt="2026-10-11T11:00:00";r.reviewNote=params.get("reason");r.version++;
     if(r.kind==="DECLARATION"){summary.approvedExpenditure=20000;summary.outstandingReturn=5000;summary.status="RETURN_DUE";}
     else{r.receiptReference=params.get("receiptReference");summary.verifiedReturns=5000;summary.outstandingReturn=0;summary.pendingReturn=0;summary.status="RECONCILED";}
     return send(r);
   }
   if(path==="/api/projects"||path==="/api/projects/my")return send([project]);
   if(path.startsWith("/api/project-phases"))return send(phases);
   if(path.startsWith("/api/disbursements"))return send(payments);
   if(path.includes("unread-count"))return send({unreadCount:0});
   return send([]);
 });
 const page=await ctx.newPage();page.setDefaultTimeout(20000);page.on("pageerror",e=>errors.push(e.message));
 await page.goto(base+(role==="SERVICE_PROVIDER"?"/provider/projects":"/admin/projects"));
 await page.getByRole("heading",{name:"Expenditure and fund returns",exact:true}).waitFor();
 await page.getByRole("button",{name:"Refresh reconciliation",exact:true}).waitFor();
 return {page,ctx};
}
const file=name=>({name,mimeType:"application/pdf",buffer:Buffer.from("%PDF-1.4 test")});
try{
 let {page,ctx}=await session("SERVICE_PROVIDER",3,390);
 await page.getByLabel("Actual expenditure (R)",{exact:true}).fill("20000");
 await page.getByLabel("Explanation",{exact:true}).fill("Receipts attached");
 await page.getByLabel("Expenditure statement with supporting receipts",{exact:true}).setInputFiles(file("expenses.pdf"));
 await page.getByRole("button",{name:"Submit expenditure",exact:true}).click();
 await page.getByRole("dialog").getByRole("button",{name:"Submit declaration",exact:true}).click();
 await page.getByText("Upload failed. Your file was not stored; please try again.",{exact:true}).waitFor();
 assert.equal(summary.history.length,0);
 failUpload=false;
 await page.getByRole("button",{name:"Submit expenditure",exact:true}).click();
 await page.getByRole("dialog").getByRole("button",{name:"Submit declaration",exact:true}).click();
 await page.getByRole("heading",{name:"Expenditure submitted",exact:true}).waitFor();
 await page.getByRole("button",{name:"Done",exact:true}).click();
 assert.equal(summary.history.length,1);await ctx.close();

 ({page,ctx}=await session("ADMINISTRATOR",1));
 assert.equal(await page.getByRole("button",{name:"Complete project",exact:true}).isDisabled(),true);
 await page.getByRole("button",{name:"Approve expenditure",exact:true}).click();
 await page.getByLabel("Review explanation",{exact:true}).fill("Checked the expenditure receipts");
 await page.getByRole("dialog").getByRole("button",{name:"Approve expenditure",exact:true}).click();
 await page.getByRole("heading",{name:"Financial review recorded",exact:true}).waitFor();await ctx.close();

 const official=summary.returnDestination;summary.returnDestination="";
 ({page,ctx}=await session("SERVICE_PROVIDER",3));
 await page.getByText(/Not configured. Contact the system owner/).waitFor();
 assert.equal(await page.getByRole("button",{name:"Record return",exact:true}).count(),0);
 await ctx.close();summary.returnDestination=official;

 ({page,ctx}=await session("SERVICE_PROVIDER",3,390));
 await page.getByLabel("Amount returned (R)",{exact:true}).fill("5000");
 await page.getByLabel("Explanation",{exact:true}).fill("Unused advance returned");
 await page.getByLabel("Transfer reference",{exact:true}).fill("REFUND-1");
 await page.getByLabel("Transfer date",{exact:true}).fill("2026-10-11");
 await page.getByLabel("Proof of return",{exact:true}).setInputFiles(file("return.pdf"));
 assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
 await page.screenshot({path:"artifacts/reconciliation-provider-mobile.png",fullPage:true});
 await page.getByRole("button",{name:"Record return",exact:true}).click();
 await page.getByRole("dialog").getByRole("button",{name:"Submit return for verification",exact:true}).click();
 await page.getByRole("heading",{name:"Return submitted",exact:true}).waitFor();await ctx.close();

 ({page,ctx}=await session("ADMINISTRATOR",1));
 await page.getByText("You approved the expenditure. A different Administrator must verify or reject this return.",{exact:true}).waitFor();
 assert.equal(await page.getByRole("button",{name:"Verify return received",exact:true}).count(),0);
 assert.equal(await page.getByRole("button",{name:"Complete project",exact:true}).isDisabled(),true);await ctx.close();

 ({page,ctx}=await session("ADMINISTRATOR",2));
 await page.getByRole("button",{name:"Verify return received",exact:true}).click();
 await page.getByText("Enter the unique transaction / receipt reference from the funder's bank or ledger records.",{exact:true}).waitFor();
 await page.getByLabel("Unique funder receipt reference",{exact:true}).fill("FUNDER-RECEIPT-1");
 await page.getByRole("button",{name:"Verify return received",exact:true}).click();
 await page.getByLabel("Review explanation",{exact:true}).fill("Confirmed receipt against official funder ledger");
 await page.getByRole("dialog").getByRole("button",{name:"Verify receipt",exact:true}).click();
 await page.getByRole("heading",{name:"Financial review recorded",exact:true}).waitFor();
 await page.getByRole("button",{name:"Done",exact:true}).click();
 await page.getByText(descriptionsForTest(),{exact:true}).waitFor();
 assert.equal(await page.getByRole("button",{name:"Complete project",exact:true}).isEnabled(),true);
 assert.equal(summary.verifiedReturns,5000);assert.equal(payments[0].amount,25000);assert.equal(summary.undisbursedAmount,75000);
 await page.screenshot({path:"artifacts/reconciliation-admin.png",fullPage:true});await ctx.close();
 assert.deepEqual(errors,[]);
 console.log("PASS reconciliation declaration failure/retry, mobile form, Admin approval, missing destination, return proof, independent reviewer, required receipt, closure gate and unchanged gross payments (mocked API).");
}finally{await browser.close();}
function descriptionsForTest(){return "Expenditure is approved and all required returns have been verified. No further payout is authorized.";}

