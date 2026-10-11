import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const base = process.env.VISUAL_BASE_URL || 'http://127.0.0.1:5186';
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const errors = [];

async function session(role, width = 1440, theme = 'light') {
  const context = await browser.newContext({ viewport: { width, height: 1000 }, reducedMotion: 'reduce' });
  await context.addInitScript(({ role, theme }) => {
    localStorage.setItem('public-fund-theme', theme);
    if (role) {
      localStorage.setItem('user', JSON.stringify({ id: 42, fullName: 'Test Admin', email: 'test@example.com', role }));
      localStorage.setItem('token', 'mock-test-token');
    }
  }, { role, theme });
  const requests = [];
  let fail = false;
  await context.route('**/api/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname;
    if (request.method() === 'POST') requests.push({ path, body: new URLSearchParams(request.postData()) });
    if (path === '/api/auth/staff') {
      await new Promise(resolve => setTimeout(resolve, 500));
      if (fail) return route.fulfill({ status: 400, contentType: 'text/plain', body: 'Email already registered' });
      const params = new URLSearchParams(request.postData());
      return route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({ id: 59,
        fullName: params.get('fullName'), email: params.get('email'), role: params.get('role'), accountStatus: 'ACTIVE' }) });
    }
    if (path === '/api/auth/login') return route.fulfill({ status: 400, contentType: 'text/plain', body: 'Invalid email or password' });
    return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
  });
  const page = await context.newPage();
  page.on('pageerror', error => errors.push(error.message));
  page.setDefaultTimeout(15000);
  return { context, page, requests, failNext: () => { fail = true; } };
}

async function fill(page, role = 'GOVERNMENT_OFFICER') {
  await page.getByLabel('Full name', { exact: true }).fill('Test Staff');
  await page.getByLabel('Email address', { exact: true }).fill('staff@example.com');
  await page.getByLabel('Account role', { exact: true }).selectOption(role);
  await page.getByLabel('Password', { exact: true }).fill('Test-Only@123');
  await page.getByLabel('Confirm password', { exact: true }).fill('Test-Only@123');
}

try {
  for (const width of [390, 1440]) {
    for (const theme of ['light', 'dark']) {
      const { context, page } = await session('ADMINISTRATOR', width, theme);
      await page.goto(base + '/admin/accounts');
      await page.getByRole('heading', { name: 'Staff accounts', exact: true }).waitFor();
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1));
      assert.equal(await page.locator('main').count(), 1);
      assert.equal(await page.locator('.workspace').getAttribute('data-theme'), theme);
      if (width === 1440 && theme === 'light') await page.screenshot({ path: 'artifacts/staff-accounts-desktop.png', fullPage: true });
      if (width === 390 && theme === 'dark') await page.screenshot({ path: 'artifacts/staff-accounts-mobile.png', fullPage: true });
      await context.close();
    }
  }
  const { context, page, requests, failNext } = await session('ADMINISTRATOR');
  await page.goto(base + '/admin/accounts');
  await fill(page);
  await page.getByLabel('Confirm password', { exact: true }).fill('Different@123');
  await page.getByRole('button', { name: 'Create account', exact: true }).click();
  await page.getByRole('alert').filter({ hasText: 'Passwords do not match.' }).waitFor();
  assert.equal(requests.length, 0);

  for (const role of ['GOVERNMENT_OFFICER', 'ADMIN']) {
    await fill(page, role);
    await page.getByRole('button', { name: 'Create account', exact: true }).click();
    await page.getByRole('button', { name: 'Creating account…', exact: true }).waitFor();
    assert.equal(await page.getByRole('button', { name: 'Creating account…', exact: true }).isDisabled(), true);
    await page.getByRole('status').filter({ hasText: 'account has been created' }).waitFor();
    const request = requests.at(-1);
    assert.equal(request.path, '/api/auth/staff');
    assert.equal(request.body.get('role'), role);
    assert.equal(request.body.get('fullName'), 'Test Staff');
    assert.equal(request.body.has('confirmPassword'), false);
    assert.equal(await page.getByLabel('Password', { exact: true }).inputValue(), '');
    assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('user')).id), 42, 'Creating staff must not replace the admin session');
  }
  failNext();
  await fill(page);
  await page.getByRole('button', { name: 'Create account', exact: true }).click();
  await page.getByRole('alert').filter({ hasText: 'Email already registered' }).waitFor();
  assert.equal(await page.getByLabel('Password', { exact: true }).inputValue(), '');
  assert.equal(await page.getByLabel('Full name', { exact: true }).inputValue(), 'Test Staff');
  await context.close();

  for (const role of ['SERVICE_PROVIDER', 'VERIFICATION_OFFICER', null]) {
    const { context, page } = await session(role);
    await page.goto(base + '/admin/accounts');
    await page.waitForURL('**/' + (role ? 'unauthorized' : 'login'));
    assert.equal(await page.getByRole('button', { name: 'Create account', exact: true }).count(), 0);
    await context.close();
  }
  const login = await session(null);
  await login.page.goto(base + '/login');
  await login.page.getByLabel('Email address').fill('staff@example.com');
  await login.page.getByLabel('Password', { exact: true }).fill('Wrong-Only@123');
  await login.page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await login.page.getByRole('alert').waitFor();
  assert.deepEqual(login.requests.map(request => request.path), ['/api/auth/login']);
  await login.context.close();
  assert.deepEqual(errors, []);
  console.log('PASS staff creation, validation, loading, success/error states, role guards, session preservation, single login attempt and responsive light/dark layouts. Mock API only; no real accounts created.');
} finally {
  await browser.close();
}
