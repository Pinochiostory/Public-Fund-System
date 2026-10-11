import assert from "node:assert/strict";
import { createRequire } from "node:module";
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const browser = await chromium.launch({ channel: "msedge", headless: true });
const base = process.env.VISUAL_BASE_URL || "http://127.0.0.1:5173";
const failures = [];
// Stub only the external identity provider; no real credentials or accounts are used.
async function session(width = 1440) {
  const context = await browser.newContext({ viewport: { width, height: 1000 } });
  const requests = [];
  let link = true, failMail = false, rejectGoogle = false;
  await context.route("https://accounts.google.com/gsi/client", route => route.fulfill({
    contentType: "application/javascript", body: `window.google={accounts:{id:{initialize(options){this.options=options},renderButton(node){const button=document.createElement('button');button.textContent='Continue with Google';button.onclick=()=>this.options.callback({credential:'test-only-google-credential'});node.appendChild(button)}}}};`,
  }));
  await context.route("**/api/**", async route => {
    const r = route.request(), path = new URL(r.url()).pathname;
    requests.push({ path, data: r.postData() });
    const send = (data, status = 200) => route.fulfill({ status, contentType: "application/json", body: JSON.stringify(data) });
    if (path.endsWith("/google/config")) return send({ clientId: "test-only-client" });
    if (path.endsWith("/google/challenge")) return send({ nonce: "test-only-browser-challenge" });
    if (path.endsWith("/google/login")) {
      const params = new URLSearchParams(r.postData());
      assert.equal(params.get("nonce"), "test-only-browser-challenge");
      if (rejectGoogle) return send("Account is not active. Contact an administrator.", 400);
      if (link && !params.get("existingPassword")) return send({ code: "GOOGLE_LINK_REQUIRED", message: "Confirm your current password." }, 409);
      return send({ user: { id: 1, fullName: "Test Provider", email: "provider@example.test", role: "SERVICE_PROVIDER" }, token: "test-only-app-token" });
    }
    if (path.endsWith("/forgot-password")) return failMail ? send("The email could not be sent. Please try requesting a new email later.", 400) : send({ message: "If an eligible account exists, a reset link has been sent." });
    if (path.endsWith("/reset-password")) return send("Password reset successfully.");
    if (path.endsWith("/unread-count")) return send({ unreadCount: 0 });
    return send([]);
  });
  const page = await context.newPage();
  page.setDefaultTimeout(15000);
  page.on("pageerror", error => failures.push(error.message));
  return { context, page, requests, setLink: value => { link = value; }, setFailMail: value => { failMail = value; }, rejectGoogle: () => { rejectGoogle = true; } };
}
try {
  for (const width of [390, 1440]) {
    const s = await session(width), p = s.page;
    await p.goto(base + "/login");
    await p.getByRole("button", { name: "Continue with Google", exact: true }).click();
    await p.getByRole("heading", { name: "Connect your existing account" }).waitFor();
    assert.equal(await p.evaluate(() => localStorage.getItem("token")), null);
    await p.getByLabel("Current Public Fund password").fill("Test-Only@123");
    await p.getByRole("button", { name: "Confirm and connect Google" }).click();
    await p.waitForURL("**/provider");
    assert.equal(await p.evaluate(() => localStorage.getItem("token")), "test-only-app-token");
    assert.ok(!(await p.evaluate(() => JSON.stringify(localStorage))).includes("test-only-google-credential"));
    await s.context.close();
  }
  const s = await session(), p = s.page;
  s.rejectGoogle();
  await p.goto(base + "/register");
  await p.getByRole("button", { name: "Continue with Google", exact: true }).click();
  await p.getByText("Account is not active. Contact an administrator.", { exact: true }).waitFor();
  assert.equal(await p.evaluate(() => localStorage.getItem("token")), null);
  await p.goto(base + "/forgot-password");
  s.setFailMail(true);
  await p.getByLabel("Email address").fill("provider@example.test");
  await p.getByRole("button", { name: "Continue", exact: true }).click();
  await p.getByText("The email could not be sent. Please try requesting a new email later.", { exact: true }).waitFor();
  s.setFailMail(false);
  await p.getByRole("button", { name: "Continue", exact: true }).click();
  await p.getByText("If an eligible account exists, a reset link has been sent.", { exact: true }).waitFor();
  assert.equal(await p.getByText("Development reset token").count(), 0);
  await p.goto(base + "/reset-password#token=test-only-reset-link");
  await p.getByRole("heading", { name: "Choose a new password" }).waitFor();
  assert.equal(new URL(p.url()).hash, "");
  assert.equal(await p.evaluate(() => sessionStorage.getItem("passwordResetToken")), null);
  await p.getByLabel("New password", { exact: true }).fill("Test-Only@123");
  await p.getByLabel("Confirm password", { exact: true }).fill("Test-Only@123");
  await p.getByRole("button", { name: "Reset password", exact: true }).click();
  await p.getByRole("heading", { name: "Password updated" }).waitFor();
  const reset = s.requests.find(r => r.path.endsWith("/reset-password"));
  assert.equal(new URLSearchParams(reset.data).get("token"), "test-only-reset-link");
  await p.goto(base + "/reset-password");
  await p.getByText("Open the reset link in your email, or request a new link below.").waitFor();
  assert.equal(await p.getByRole("button", { name: "Reset password", exact: true }).isDisabled(), true);
  await s.context.close();
  assert.deepEqual(failures, []);
  console.log("PASS Google linking, mobile/desktop login, denied accounts, recovery delivery errors, reset-link privacy and missing-link handling (mocked APIs/Google).");
} finally { await browser.close(); }
