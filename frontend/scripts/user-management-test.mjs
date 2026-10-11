import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const base = process.env.VISUAL_BASE_URL || 'http://127.0.0.1:5186';
const browser = await chromium.launch({ channel: 'msedge', headless: true });
const errors = [];
const temporary = 'Mock-Temporary@789';
const sourceUsers = [
  { id: 1, fullName: 'Current Admin', email: 'admin@example.com', role: 'ADMIN', accountStatus: 'ACTIVE' },
  { id: 2, fullName: 'River Provider', email: 'provider@example.com', role: 'SERVICE_PROVIDER', accountStatus: 'ACTIVE' },
  { id: 3, fullName: 'Thabo Officer', email: 'officer@example.com', role: 'GOVERNMENT_OFFICER', accountStatus: 'DISABLED' },
  { id: 4, fullName: 'Other Admin', email: 'other@example.com', role: 'ADMIN', accountStatus: 'ACTIVE' },
];
const loginRole = role => role === 'ADMIN' ? 'ADMINISTRATOR' : role === 'GOVERNMENT_OFFICER' ? 'VERIFICATION_OFFICER' : role;
const home = role => role === 'ADMINISTRATOR' ? '/admin' : role === 'VERIFICATION_OFFICER' ? '/officer' : '/provider';

async function session({ role = 'ADMINISTRATOR', width = 1440, theme = 'light', required = false, fail = false, loginAs = 'SERVICE_PROVIDER' } = {}) {
  const context = await browser.newContext({ viewport: { width, height: 1050 }, reducedMotion: 'reduce' });
  await context.addInitScript(({ role, theme, required }) => {
    localStorage.setItem('public-fund-theme', theme);
    if (role) {
      localStorage.setItem('user', JSON.stringify({ id: 1, fullName: 'Current Admin', email: 'admin@example.com', role, mustChangePassword: required }));
      localStorage.setItem('token', 'mock-session');
    }
  }, { role, theme, required });
  const users = structuredClone(sourceUsers), requests = [];
  let failAction = false;
  await context.route('**/api/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname;
    requests.push({ path, method: request.method(), data: request.postData() });
    const respond = (data, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) });
    if (path === '/api/auth/google/config') return respond({ clientId: '' });
    if (path === '/api/admin/users' && request.method() === 'GET') {
      await new Promise(resolve => setTimeout(resolve, 150));
      return respond(fail ? { message: 'Unable to load users.' } : users, fail ? 500 : 200);
    }
    if (path.startsWith('/api/admin/users/')) {
      await new Promise(resolve => setTimeout(resolve, 250));
      if (failAction) return respond({ message: 'Action could not be completed.' }, 500);
      const user = users.find(item => item.id === Number(path.split('/')[4]));
      if (path.endsWith('/status')) { user.accountStatus = JSON.parse(request.postData()).status; return respond(user); }
      user.mustChangePassword = true;
      return respond({ user, temporaryPassword: temporary });
    }
    if (path === '/api/auth/login') return respond({ user: { id: 2, fullName: 'Test User', role: loginAs, mustChangePassword: true }, token: 'restricted-token' });
    if (path === '/api/auth/change-password') return respond({ user: { id: 2, fullName: 'Test User', role: loginAs, mustChangePassword: false }, token: 'full-session-token' });
    return respond(path.includes('unread-count') ? { unreadCount: 0 } : []);
  });
  const page = await context.newPage();
  page.setDefaultTimeout(20000);
  page.on('pageerror', error => errors.push(error.message));
  return { context, page, requests, failNext: value => { failAction = value; } };
}

async function ready(page) {
  await page.goto(base + '/admin/users');
  await page.getByRole('row', { name: /River Provider/ }).waitFor();
}
const userRow = (page, name) => page.getByRole('row').filter({ hasText: name });

try {
  for (const width of [390, 1440]) for (const theme of ['light', 'dark']) {
    const { context, page } = await session({ width, theme });
    await ready(page);
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), 'Page must not overflow');
    await page.screenshot({ path: `artifacts/user-management-${width}-${theme}.png`, fullPage: true });
    await userRow(page, 'River Provider').getByRole('button', { name: 'Disable', exact: true }).click();
    assert.equal(await page.getByRole('dialog').isVisible(), true);
    assert.ok(await page.getByRole('dialog').evaluate(node => node.scrollWidth <= node.clientWidth + 1));
    await page.getByRole('button', { name: 'Cancel', exact: true }).click();
    await context.close();
  }

  const { context, page, requests, failNext } = await session();
  await ready(page);
  assert.equal(await page.locator('tbody tr').count(), 4);
  assert.equal(await userRow(page, 'Current Admin').getByRole('button').count(), 0);
  assert.ok((await userRow(page, 'Current Admin').innerText()).includes('(You)'));
  assert.equal(await userRow(page, 'Other Admin').getByRole('button', { name: 'Disable', exact: true }).count(), 0);

  await page.getByLabel('Search users').fill('river');
  assert.equal(await page.locator('tbody tr').count(), 1);
  await page.getByLabel('Search users').fill('OFFICER@EXAMPLE.COM');
  assert.equal(await page.locator('tbody tr').count(), 1);
  await page.getByRole('button', { name: 'Clear filters' }).click();
  await page.getByLabel('Filter by role').selectOption('ADMIN');
  assert.equal(await page.locator('tbody tr').count(), 2);
  await page.getByLabel('Filter by status').selectOption('DISABLED');
  await page.getByText('No users match your filters.').waitFor();
  await page.getByRole('button', { name: 'Clear filters' }).click();
  await page.getByLabel('Filter by status').selectOption('DISABLED');
  assert.equal(await page.locator('tbody tr').count(), 1);
  await page.getByRole('button', { name: 'Clear filters' }).click();

  await userRow(page, 'River Provider').getByRole('button', { name: 'Disable', exact: true }).click();
  await page.getByRole('button', { name: 'Cancel', exact: true }).click();
  assert.equal(requests.filter(r => r.method === 'PATCH').length, 0);
  for (const [name, button, confirm] of [['River Provider', 'Disable', 'Disable account'], ['River Provider', 'Enable', 'Enable account'], ['Thabo Officer', 'Enable', 'Enable account'], ['Thabo Officer', 'Disable', 'Disable account']]) {
    await userRow(page, name).getByRole('button', { name: button, exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: confirm, exact: true }).click();
    await page.getByRole('dialog').waitFor({ state: 'hidden' });
  }
  assert.deepEqual(requests.filter(r => r.method === 'PATCH').map(r => JSON.parse(r.data).status), ['DISABLED', 'ACTIVE', 'ACTIVE', 'DISABLED']);

  failNext(true);
  await userRow(page, 'River Provider').getByRole('button', { name: 'Disable', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Disable account', exact: true }).click();
  await page.getByRole('dialog').getByRole('alert').waitFor();
  await page.getByRole('button', { name: 'Cancel', exact: true }).click();
  failNext(false);
  for (const name of ['River Provider', 'Thabo Officer', 'Other Admin']) {
    await userRow(page, name).getByRole('button', { name: 'Reset password', exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Reset password', exact: true }).click();
    await page.getByRole('heading', { name: 'Temporary password created' }).waitFor();
    assert.equal(await page.getByLabel('Temporary password', { exact: true }).inputValue(), temporary);
    const persisted = await page.evaluate(() => JSON.stringify({ local: { ...localStorage }, session: { ...sessionStorage } }));
    assert.equal(persisted.includes(temporary), false, 'Temporary password must not be persisted');
    await page.getByRole('button', { name: 'Done — close securely' }).click();
    assert.equal(await page.getByLabel('Temporary password', { exact: true }).count(), 0);
  }
  await page.reload();
  await userRow(page, 'River Provider').waitFor();
  assert.equal(await page.getByLabel('Temporary password', { exact: true }).count(), 0);
  await context.close();

  for (const role of ['SERVICE_PROVIDER', 'VERIFICATION_OFFICER', null]) {
    const { context, page } = await session({ role });
    await page.goto(base + '/admin/users');
    await page.waitForURL('**/' + (role ? 'unauthorized' : 'login'));
    await context.close();
  }
  for (const role of ['SERVICE_PROVIDER', 'VERIFICATION_OFFICER', 'ADMINISTRATOR']) {
    const { context, page, requests } = await session({ role: null, loginAs: role });
    await page.goto(base + '/login');
    await page.getByLabel('Email address').fill('test@example.com');
    await page.getByLabel('Password', { exact: true }).fill(temporary);
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();
    await page.waitForURL('**/change-password');
    assert.equal(requests.filter(r => r.method === 'GET' && r.path !== '/api/auth/google/config').length, 0, 'Restricted login cannot fetch dashboard data');
    await page.goto(base + home(role));
    await page.waitForURL('**/change-password');
    await page.getByLabel('Temporary password', { exact: true }).fill(temporary);
    await page.getByLabel('New password', { exact: true }).fill('New-Private@456');
    await page.getByLabel('Confirm new password', { exact: true }).fill('Different@456');
    await page.getByRole('button', { name: 'Save password and continue' }).click();
    await page.getByRole('alert').filter({ hasText: 'Passwords do not match' }).waitFor();
    assert.equal(requests.filter(r => r.path === '/api/auth/change-password').length, 0);
    await page.getByLabel('Confirm new password', { exact: true }).fill('New-Private@456');
    await page.getByRole('button', { name: 'Save password and continue' }).click();
    await page.waitForURL(base + home(role));
    const stored = await page.evaluate(() => JSON.parse(localStorage.getItem('user')));
    assert.equal(stored.mustChangePassword, false);
    assert.equal(stored.role, role);
    await context.close();
  }
  const failed = await session({ fail: true });
  await failed.page.goto(base + '/admin/users');
  await failed.page.getByRole('alert').waitFor();
  assert.equal(await failed.page.getByRole('button', { name: 'Try again' }).count(), 1);
  await failed.context.close();
  assert.deepEqual(errors, []);
  console.log('PASS User Management: name/email/role/status filters; confirmations; enable/disable; reset all roles; no self-actions; one-time temporary display; responsive layouts; route guards; required-password flow for all roles. Mock API only.');
} finally { await browser.close(); }
