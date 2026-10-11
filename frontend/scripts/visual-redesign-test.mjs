import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const output = new URL('../artifacts/redesign/',import.meta.url).pathname.replace(/^\/([A-Z]:)/,'$1');
await mkdir(output,{recursive:true});
const browser = await chromium.launch({channel:'msedge',headless:true});
const projects = [
 {id:1,applicationId:1,title:'Kimberley Community Library',description:'Restore the reading rooms and create an accessible learning space for the community.',status:'ACTIVE',approvedBudget:850000,remainingBudget:600000},
 {id:2,applicationId:2,title:'Galeshewe Water Upgrade',description:'Improve reliable water access through targeted infrastructure repairs.',status:'ACTIVE',approvedBudget:420000,remainingBudget:320000},
 {id:3,applicationId:3,title:'Youth Skills Workshop',description:'Equip a local training centre for practical skills development.',status:'COMPLETED',approvedBudget:185000,remainingBudget:0}
];
const applications=projects.map((p,i)=>({...p,status:i===0?'PENDING':i===1?'APPROVED':'REJECTED',requestedBudget:p.approvedBudget,timeline:'6 months'}));
const phases=[{id:1,projectId:1,projectTitle:projects[0].title,phaseName:'Site preparation',description:'Prepare the site and confirm the scope of work.',phaseOrder:1,allocatedBudget:250000,status:'IN_PROGRESS'},{id:2,projectId:1,projectTitle:projects[0].title,phaseName:'Reading room fit-out',description:'Install shelving, lighting and furniture.',phaseOrder:2,allocatedBudget:300000,status:'PROPOSED'}];
const evidence=[{id:1,phaseId:2,status:'PENDING',fileName:'Progress report.pdf',uploadedAt:'2026-09-20T12:00:00',providerName:'Lerato Molefe'}];
const payments=[{id:1,phaseId:1,amount:250000,status:'RELEASED',releasedAt:'2026-09-18T09:30:00'}];
const notifications=[{id:1,title:'Phase funding released',message:'Funds for Site preparation are available. You can start the phase.',readStatus:false,createdAt:'2026-09-18T09:30:00'},{id:2,title:'Project approved',message:'Kimberley Community Library is ready for phase planning.',readStatus:true,createdAt:'2026-09-17T10:00:00'}];
const errors=[];
const baseURL=process.env.VISUAL_BASE_URL || 'http://localhost:5173';
async function contextFor(role='SERVICE_PROVIDER',theme='light',width=1440){
 const context=await browser.newContext({viewport:{width,height:1000},reducedMotion:'reduce'});
 await context.addInitScript(({role,theme})=>{if(role){localStorage.setItem('user',JSON.stringify({id:1,fullName:'Lerato Molefe',email:'sample@example.com',role}));localStorage.setItem('token','visual-test-only');}if(!localStorage.getItem('public-fund-theme'))localStorage.setItem('public-fund-theme',theme);},{role,theme});
 await context.route('http://localhost:8080/api/**',async route=>{
  const path=new URL(route.request().url()).pathname;
  let data=path.includes('unread-count')?{unreadCount:1}:path.includes('notifications')?notifications:path.includes('applications')?applications:path.includes('project-phases')?phases:path.includes('projects')?projects:path.includes('disbursements')?payments:path.includes('evidence')?evidence:path.includes('audit')?[{id:1,action:'APPLICATION_APPROVED',description:'Funding application approved',userEmail:'admin@example.com',createdAt:'2026-09-18T09:30:00'}]:[];
  if(route.request().method()!=='GET') data={message:'Sample action completed'};
  await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(data)});
 });
 return context;
}
async function capture(context,path,name){
 const page=await context.newPage();page.on('pageerror',e=>{errors.push(name+': '+e.message);console.error(name,e.message);});
 await page.goto(baseURL+path);await page.locator('h1').first().waitFor();
 await page.waitForFunction(()=>!document.querySelector('.skeleton-screen'));
 await page.evaluate(()=>document.fonts.ready);
 assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),name+' overflows horizontally');
 await page.screenshot({path:output+'/'+name+'.png',fullPage:true});
 console.log('PASS '+name);return page;
}
try{
 const provider=await contextFor();
 const page=await capture(provider,'/provider','provider-light');
 await page.getByRole('switch',{name:'Dark mode'}).click();
 await page.reload();await page.locator('[data-theme="dark"] .theme-toggle').waitFor();
 assert.equal(await page.getByRole('switch',{name:'Dark mode'}).getAttribute('aria-checked'),'true');
 await page.waitForFunction(()=>!document.querySelector('.skeleton-screen'));
 await page.screenshot({path:output+'/provider-dark.png',fullPage:true});
 await page.getByRole('switch',{name:'Dark mode'}).click();
 const pay=await capture(provider,'/provider/disbursements','payments');
 await pay.getByRole('textbox',{name:'Search projects'}).fill('Kimberley');assert.equal(await pay.locator('.project-directory-card').count(),1);
 await pay.getByRole('link',{name:'View payments'}).first().click();await pay.getByRole('heading',{name:'Kimberley Community Library'}).waitFor();
 await pay.waitForFunction(()=>!document.querySelector('.skeleton-screen'));
 assert.ok(await pay.getByText('PAY-1').isVisible());
 const upload=await capture(provider,'/provider/evidence','evidence-upload');
 await upload.locator('#evidence-file').setInputFiles({name:'sample.pdf',mimeType:'application/pdf',buffer:Buffer.from('%PDF-1.4 sample visual test')});
 await upload.getByRole('button',{name:'Upload evidence'}).click();await upload.getByText(/sample.pdf uploaded successfully/).waitFor();
 await capture(provider,'/provider/evidence/1','evidence-phases');
 await capture(provider,'/provider/projects','provider-projects');
 await capture(provider,'/provider/applications/new','application');
 await capture(provider,'/provider/notifications','notifications');
 const admin=await contextFor('ADMINISTRATOR');await capture(admin,'/admin','admin');await capture(admin,'/admin/projects','admin-projects');
 const officer=await contextFor('VERIFICATION_OFFICER');await capture(officer,'/officer','officer');await capture(officer,'/officer/evidence','officer-evidence');
 const mobile=await contextFor('SERVICE_PROVIDER','light',390);const mobilePage=await capture(mobile,'/provider','provider-mobile');
 await mobilePage.getByRole('button',{name:'Toggle navigation'}).click();assert.ok(await mobilePage.locator('.workspace-sidebar').isVisible());await mobilePage.getByRole('switch',{name:'Dark mode'}).click();await mobilePage.keyboard.press('Escape');
 const publicContext=await contextFor(null);await capture(publicContext,'/login','login');await capture(publicContext,'/register','register');await capture(publicContext,'/how-it-works','guide');await capture(publicContext,'/forgot-password','forgot-password');
 const publicMobile=await contextFor(null,'light',390);const login=await capture(publicMobile,'/login','login-mobile');await login.getByLabel('Password',{exact:true}).fill('sample password');await login.getByRole('button',{name:'Show password'}).click();assert.equal(await login.locator('#password').getAttribute('type'),'text');await capture(publicMobile,'/register','register-mobile');
 const darkPublic=await contextFor(null,'dark');await capture(darkPublic,'/login','login-dark');
 const darkAdmin=await contextFor('ADMINISTRATOR','dark');await capture(darkAdmin,'/admin/projects','admin-projects-dark');
 assert.deepEqual(errors,[]);console.log('PASS theme persistence, project payment navigation, mobile menu and no runtime errors');
}finally{await browser.close();}
