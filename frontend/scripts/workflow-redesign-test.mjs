import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir} from 'node:fs/promises';
const require=createRequire(import.meta.url);
const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const base=process.env.VISUAL_BASE_URL || 'http://127.0.0.1:5174';
const output=new URL('../artifacts/redesign/',import.meta.url).pathname.replace(/^\/([A-Z]:)/,'$1');
await mkdir(output,{recursive:true});
const browser=await chromium.launch({channel:'msedge',headless:true});
const projects=[{id:1,title:'Community Library Renewal',description:'Improve access to books and learning facilities.',status:'ACTIVE',approvedBudget:500000,timeline:'6 months',applicationId:1},{id:2,title:'Water Infrastructure',status:'ACTIVE',approvedBudget:300000,timeline:'4 months',applicationId:2}];
const phases=[{id:11,projectId:1,projectTitle:projects[0].title,phaseName:'Reading room',phaseOrder:1,allocatedBudget:50000,status:'PENDING_APPROVAL'},{id:21,projectId:2,projectTitle:projects[1].title,phaseName:'Pipe installation',phaseOrder:1,allocatedBudget:100000,status:'IN_PROGRESS'}];
const evidence=[{id:8,phaseId:21,fileName:'Delivery report.pdf',status:'PENDING',uploadedAt:'2026-10-01T12:00:00'}];
const applications=[{id:1,title:projects[0].title,status:'PENDING',requestedBudget:500000,description:'Library renewal',timeline:'6 months'}];
const payments=[{id:1,phaseId:21,amount:100000,status:'PENDING'}];
const notifications=[{id:1,title:'Application reviewed',message:'Your application has been reviewed.',readStatus:false,createdAt:'2026-10-01T12:00:00'}];
const requests=[],errors=[];
const roles=[
 [null,['/login','/register','/forgot-password','/reset-password','/how-it-works','/unauthorized','/missing']],
 ['SERVICE_PROVIDER',['/provider','/provider/applications/new','/provider/projects','/provider/evidence','/provider/evidence/2','/provider/evidence/2/phases/21','/provider/disbursements','/provider/disbursements/2','/provider/notifications']],
 ['ADMINISTRATOR',['/admin','/admin/applications','/admin/projects','/admin/disbursements','/admin/audit-logs']],
 ['VERIFICATION_OFFICER',['/officer','/officer/evidence','/officer/phases']]
];
async function session(role,width=1440,theme='light',mode='normal') {
 const context=await browser.newContext({viewport:{width,height:960},reducedMotion:'reduce'});
 await context.addInitScript(({role,theme})=>{localStorage.setItem('public-fund-theme',theme);if(role){localStorage.setItem('user',JSON.stringify({id:1,fullName:'Lerato Molefe',email:'test@example.com',role}));localStorage.setItem('token','isolated-test');}},{role,theme});
 await context.route('**/api/**',async route=>{
  const request=route.request(),path=new URL(request.url()).pathname;
  if(request.method()!=='GET') requests.push({path,body:request.postData()});
  if(mode==='error') return route.fulfill({status:500,contentType:'text/plain',body:'Unable to load sample records.'});
  if(mode==='loading') await new Promise(resolve=>setTimeout(resolve,1300));
  let data=path.includes('unread-count')?{unreadCount:1}:path.includes('notifications')?notifications:path.includes('project-phases')?phases:path.includes('projects')?projects:path.includes('applications')?applications:path.includes('disbursements')?payments:path.includes('evidence')?evidence:[{id:1,action:'APPLICATION_SUBMITTED',description:'Application submitted for review',userEmail:'test@example.com',userRole:'SERVICE_PROVIDER',createdAt:'2026-10-01T12:00:00'}];
  if(mode==='empty') data=path.includes('unread-count')?{unreadCount:0}:[];
  if(request.method()!=='GET') data=path.endsWith('/auth/login')?{user:{id:1,fullName:'Lerato Molefe',role:'SERVICE_PROVIDER'},token:'test-token'}:path.endsWith('/auth/forgot-password')?{resetToken:'test-token'}:'Request completed successfully.';
  await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(data)});
 });
 const page=await context.newPage(); page.setDefaultTimeout(12000); page.on('pageerror',e=>{errors.push(e.message);console.error('PAGE ERROR',e.message);});
 return {context,page};
}
async function ready(page,path) {
 await page.goto(base+path); await page.locator('h1').waitFor();
 await page.waitForFunction(()=>!document.querySelector('.skeleton-screen'));
 await page.evaluate(()=>document.fonts.ready);
 assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),path+' has horizontal page overflow');
 assert.equal(await page.locator('main,[role="main"]').count(),1,path+' needs one main landmark');
}
try {
 let checked=0;
 for(const width of process.env.WORKFLOW_ONLY ? [] : process.env.ROUTE_WIDTHS ? process.env.ROUTE_WIDTHS.split(',').map(Number) : [390,1024,1440]) for(const theme of ['light','dark']) for(const [role,paths] of roles) {
  const {context,page}=await session(role,width,theme);
  for(const path of paths) { await ready(page,path); checked++; }
  await context.close();
  console.log('PASS '+(role||'public')+' '+width+' '+theme);
 }
 console.log('PASS '+checked+' responsive/theme route combinations');
 const admin=await session('ADMINISTRATOR');
 await ready(admin.page,'/admin/projects');
 await admin.page.getByRole('button',{name:'Approve',exact:true}).click();
 const dialog=admin.page.getByRole('dialog');await dialog.waitFor();
 assert.ok(await admin.page.evaluate(()=>document.querySelector('dialog').contains(document.activeElement)),'Modal must receive focus');
 await admin.page.screenshot({path:output+'/admin-approval-dialog.png'});
 await dialog.getByRole('button',{name:'Confirm',exact:true}).click();
 await admin.page.waitForFunction(()=>!document.querySelector('dialog'));
 assert.ok(requests.some(r=>r.path==='/api/project-phases/11/approve'));
 await admin.context.close();
 const officer=await session('VERIFICATION_OFFICER',390,'dark');
 await ready(officer.page,'/officer/evidence');
 await officer.page.getByRole('button',{name:'Reject',exact:true}).click();
 await officer.page.getByRole('textbox',{name:'Rejection reason'}).fill('Please provide the signed delivery note.');
 await officer.page.screenshot({path:output+'/officer-review-mobile.png'});
 await officer.page.getByRole('button',{name:'Reject evidence',exact:true}).click();
 await officer.page.waitForFunction(()=>!document.querySelector('dialog'));
 assert.ok(requests.some(r=>r.path==='/api/evidence/8/reject' && new URLSearchParams(r.body).get('verificationComment')==='Please provide the signed delivery note.'));
 await officer.context.close();
 const provider=await session('SERVICE_PROVIDER');
 await ready(provider.page,'/provider/projects');
 await provider.page.getByRole('button',{name:'Propose new phase',exact:true}).first().click();
 await provider.page.getByRole('textbox',{name:'Phase name',exact:true}).fill('Furniture procurement');
 await provider.page.getByRole('textbox',{name:'Phase description'}).fill('Purchase reading room furniture.');
 await provider.page.getByRole('spinbutton',{name:'Requested amount'}).fill('25000');
 await provider.page.getByRole('button',{name:'Submit phase',exact:true}).click();
 await provider.page.waitForFunction(()=>!document.querySelector('dialog'));
 assert.ok(requests.some(r=>r.path==='/api/project-phases/project/1' && new URLSearchParams(r.body).get('allocatedBudget')==='25000'));
 await ready(provider.page,'/provider/applications/new');
 await provider.page.getByLabel('Project title').fill('Community Learning Hub');
 await provider.page.getByLabel('Project description').fill('Provide accessible learning spaces.');
 await provider.page.getByLabel('Requested budget').fill('125000');
 await provider.page.getByLabel('Project timeline').fill('6 months');
 await provider.page.getByRole('button',{name:'Submit application'}).click();
 await provider.page.getByText('Funding application submitted successfully.').waitFor();
 assert.ok(requests.some(r=>r.path==='/api/applications' && new URLSearchParams(r.body).get('title')==='Community Learning Hub'));
 await provider.context.close();
 const account=await session(null,390);
 await ready(account.page,'/register');
 await account.page.getByLabel('Full name').fill('Thabo Mokoena');
 await account.page.getByLabel('Email address').fill('sample@example.com');
 await account.page.getByLabel('Password',{exact:true}).fill('Example!123');
 await account.page.getByLabel('Confirm password',{exact:true}).fill('Example!123');
 await account.page.getByRole('button',{name:'Create account',exact:true}).click();
 await account.page.waitForURL('**/login');
 assert.ok(requests.some(r=>r.path==='/api/auth/register' && new URLSearchParams(r.body).get('role')==='SERVICE_PROVIDER' && new URLSearchParams(r.body).get('fullName')==='Thabo Mokoena'));
 await ready(account.page,'/forgot-password');
 await account.page.getByLabel('Email address').fill('sample@example.com');
 await account.page.getByRole('button',{name:'Continue',exact:true}).click();
 await account.page.waitForURL('**/reset-password');
 await account.page.getByLabel('New password',{exact:true}).fill('Example!123');
 await account.page.getByLabel('Confirm password',{exact:true}).fill('Example!123');
 await account.page.getByRole('button',{name:'Reset password',exact:true}).click();
 await account.page.getByRole('heading',{name:'Password updated'}).waitFor();
 assert.ok(requests.some(r=>r.path==='/api/auth/reset-password' && new URLSearchParams(r.body).get('token')==='test-token'));
 await ready(account.page,'/login');
 await account.page.getByLabel('Email address').fill('sample@example.com');
 await account.page.getByLabel('Password',{exact:true}).fill('Example!123');
 await account.page.getByRole('button',{name:'Sign in',exact:true}).click();
 await account.page.waitForURL('**/provider');
 await account.context.close();
 console.log('PASS registration, password recovery and sign-in integrations');
 for(const mode of ['empty','error','loading']) {
  console.log('Checking '+mode+' state');
  const {context,page}=await session('SERVICE_PROVIDER',390,'light',mode);
  if(mode==='loading') {await page.goto(base+'/provider');await page.getByRole('status',{name:'Loading content'}).waitFor();await page.screenshot({path:output+'/loading-mobile.png'});}
  else {await ready(page,'/provider/evidence');assert.ok(await page.getByText(mode==='empty'?'Your approved projects will appear here.':'Unable to load sample records.',{exact:true}).isVisible());}
  await context.close();
 }
 assert.deepEqual(errors,[]);
 console.log('PASS approval/rejection/proposal/application payloads, modal focus, empty/error/loading states; no live backend writes');
} finally {await browser.close();}
