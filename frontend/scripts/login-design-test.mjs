import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const base = process.env.VISUAL_BASE_URL || 'http://127.0.0.1:5173';
const baseline = process.argv.includes('--baseline');
const errors = [];
try {
  for (const [width, height, theme] of baseline ? [[1440,900,'light']] : [[1440,900,'light'],[1440,900,'dark'],[1024,768,'light'],[768,1024,'light'],[390,844,'light'],[390,844,'dark'],[320,740,'light']]) {
    const context = await browser.newContext({ viewport: { width, height }, reducedMotion: 'reduce' });
    await context.addInitScript(theme => localStorage.setItem('public-fund-theme',theme), theme);
    let signIns = 0;
    await context.route('**/api/**', async route => {
      const path = new URL(route.request().url()).pathname;
      if (path === '/api/auth/login') {
        signIns++;
        await new Promise(resolve => setTimeout(resolve,350));
        return route.fulfill({status:400,contentType:'application/json',body:JSON.stringify({message:'The email or password is incorrect.'})});
      }
      return route.fulfill({contentType:'application/json',body:JSON.stringify({clientId:''})});
    });
    const page = await context.newPage();
    page.on('pageerror',error => errors.push(error.message));
    await page.goto(base+'/login');
    await page.getByRole('heading',{name:'Welcome back',exact:true}).waitFor();
    await page.evaluate(() => document.fonts.ready);
    if (!baseline) {
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),'No horizontal overflow');
      assert.equal(await page.locator('.login-portrait img').evaluate(image => image.complete && image.naturalWidth > 0),true);
      assert.equal(await page.getByRole('link',{name:'Register',exact:true}).getAttribute('href'),'/register');
      assert.equal(await page.getByRole('link',{name:'Forgot password?',exact:true}).getAttribute('href'),'/forgot-password');
      assert.equal(await page.getByRole('link',{name:'How it works',exact:true}).getAttribute('href'),'/how-it-works');
      const cta = await page.getByRole('button',{name:'Sign in',exact:true}).boundingBox();
      assert.ok(cta.y + cta.height <= height,'Primary action visible in viewport');
    }
    await page.screenshot({path:`artifacts/login-${baseline?'before':`${width}-${theme}`}.png`,fullPage:true});
    if (!baseline) {
      await page.getByLabel('Email address',{exact:true}).fill('test@example.test');
      await page.getByLabel('Password',{exact:true}).fill('Test-Only@123');
      await page.getByRole('button',{name:'Show password',exact:true}).click();
      assert.equal(await page.getByLabel('Password',{exact:true}).getAttribute('type'),'text');
      await page.getByRole('button',{name:'Hide password',exact:true}).click();
      await page.getByRole('button',{name:'Sign in',exact:true}).click();
      await page.getByRole('button',{name:'Signing in...',exact:true}).waitFor();
      assert.equal(await page.getByRole('button',{name:'Signing in...',exact:true}).isDisabled(),true);
      await page.getByRole('alert').filter({hasText:'The email or password is incorrect.'}).waitFor();
      assert.equal(signIns,1);
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1));
      await page.screenshot({path:`artifacts/login-error-${width}-${theme}.png`,fullPage:true});
    }
    await context.close();
  }
  assert.deepEqual(errors,[]);
  console.log(baseline ? 'Captured login baseline.' : 'PASS login responsive layouts, both themes, navigation, visibility toggle, loading, error and single-submit states. Mock API only.');
} finally { await browser.close(); }
