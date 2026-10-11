import assert from "node:assert/strict";
import { createServer } from "vite";
import React from "react";
import { renderToString, renderToPipeableStream } from "react-dom/server";
import { PassThrough } from "node:stream";
import { MemoryRouter } from "react-router-dom";
import { filterRecords } from "../src/components/useRecordFilters.js";
import { THEME_KEY, readTheme, saveTheme } from "../src/components/themePreference.js";

// Wait for lazy route modules while retaining the route-content assertions.
function renderRoute(element) {
  return new Promise((resolve,reject) => {
    let html="";
    const target=new PassThrough();
    target.on("data",chunk=>{html+=chunk.toString();});
    target.on("end",()=>resolve(html));
    const stream=renderToPipeableStream(element,{onAllReady(){stream.pipe(target);},onError:reject});
  });
}

const preferences = new Map();
const themeStorage = { getItem: key => preferences.get(key), setItem: (key, value) => preferences.set(key, value) };
assert.equal(readTheme(themeStorage), "light");
saveTheme("dark", themeStorage);
assert.equal(readTheme(themeStorage), "dark", "Saved theme must survive a fresh read");
saveTheme("light", themeStorage);
assert.equal(readTheme(themeStorage), "light");
assert.equal(readTheme({ getItem() { throw new Error("Storage unavailable"); } }), "light");
assert.doesNotThrow(() => saveTheme("dark", { setItem() { throw new Error("Storage unavailable"); } }));

const sampleRecords = [
  { id: 1, title: "Community clinic", status: "ACTIVE", amount: 500 },
  { id: 2, title: "School repairs", status: "PENDING", amount: 300 },
];
assert.equal(filterRecords(sampleRecords).length, 2);
assert.deepEqual(filterRecords(sampleRecords, " CLINIC ", "ACTIVE").map(r => r.id), [1]);
assert.equal(filterRecords(sampleRecords, "clinic", "PENDING").length, 0);
assert.equal(filterRecords(sampleRecords, "", "PENDING").length, 1);
assert.equal(filterRecords(sampleRecords, "500").length, 1);
assert.equal(sampleRecords.length, 2, "Filtering must not mutate original records");

// Render every route without a browser or live API; no real accounts are used.
const server = await createServer({ server: { middlewareMode: true }, appType: "custom" });
let currentUser = null;
let currentTheme = "light";
globalThis.sessionStorage = { getItem: () => null };
globalThis.localStorage = {
  getItem: key => key === THEME_KEY ? currentTheme : key === "user" ? (currentUser ? JSON.stringify(currentUser) : null) : currentUser ? "test-token" : null,
};
try {
  const { default: App } = await server.ssrLoadModule("/src/App.jsx");
  const { AuthProvider } = await server.ssrLoadModule("/src/context/AuthContext.jsx");
  const routes = [
    [null, ["/login", "/register", "/verify-email", "/forgot-password", "/reset-password", "/how-it-works"]],
    ["SERVICE_PROVIDER", ["/provider", "/provider/projects", "/provider/applications/new", "/provider/evidence", "/provider/evidence/1", "/provider/evidence/1/phases/2", "/provider/disbursements", "/provider/disbursements/1", "/provider/notifications"]],
    ["ADMINISTRATOR", ["/admin", "/admin/applications", "/admin/projects", "/admin/disbursements", "/admin/audit-logs", "/admin/accounts", "/admin/users", "/admin/evidence"]],
    ["VERIFICATION_OFFICER", ["/officer", "/officer/evidence", "/officer/phases"]],
  ];
  let count = 0;
  for (const [role, paths] of routes) {
    currentUser = role ? { fullName: "Smoke Test", email: "test@example.com", role } : null;
    for (const path of paths) {
      const html = await renderRoute(React.createElement(MemoryRouter, { initialEntries: [path] },
        React.createElement(AuthProvider, null, React.createElement(App))));
      assert.ok(html.length > 100, path + " rendered empty");
      if (role) {
        assert.ok(html.includes('aria-label="Main navigation"'), path + " missing shared navigation");
        assert.ok(html.includes("workspace-content"), path + " missing main content");
        assert.equal((html.match(/Smoke Test/g) || []).length, 1, path + " must display the name only once");
        assert.equal((html.match(/href="\/how-it-works"/g) || []).length, role === "SERVICE_PROVIDER" ? 1 : 0,
          path + " must not duplicate the guide link");
        assert.ok(html.includes('aria-label="Go back"'), path + " missing back navigation");
        const breadcrumbs = html.match(/<nav aria-label="Breadcrumb">([\s\S]*?)<\/nav>/)?.[1];
        assert.ok(breadcrumbs && !breadcrumbs.includes("<a"), "Breadcrumbs must be display-only");
        assert.ok(html.includes('class="workspace-back navbar-signout"'), "Sign out must share the Back button style");
        assert.ok(html.includes('data-layout="default"'), "All dashboards must use the default layout");
        assert.ok(html.includes('data-theme="light"'), "Dashboard must default to light mode");
        assert.ok(html.includes('role="switch" aria-checked="false" aria-label="Dark mode"'), "Accessible theme switch missing");
        if (path.includes("/provider/evidence") || path.includes("/provider/disbursements")) {
          assert.ok(html.includes('class="project-page"'), "Project pages must have their scoped styling");
        }
        assert.equal(html.includes('href="/how-it-works"'), role === "SERVICE_PROVIDER",
          path + " has incorrect guide visibility");
        // Creation forms have no initial data request; their loading state is on submit.
        if (!path.endsWith("/new") && path !== "/admin/accounts") assert.ok(html.includes("Loading content"), path + " missing loading feedback");
        if (path === "/admin/accounts") {
          assert.ok(html.includes('aria-busy="false"'), "Staff form must expose its submission state");
          assert.ok(html.includes('value="ADMIN"') && html.includes('value="GOVERNMENT_OFFICER"'), "Staff roles missing");
          assert.ok(html.includes('autoComplete="new-password"') || html.includes('autocomplete="new-password"'), "Staff password autocomplete missing");
        }
      }
      if (path === "/register") assert.ok(!html.includes('name="role"'), "Public role selector must be removed");
      if (path === "/login" || path === "/register") {
        assert.ok(html.includes("login-portrait"), "Standalone logo image missing");
        assert.ok(html.includes('class="auth-field"'), "Redesigned form fields missing");
        assert.ok(!html.includes("registration-note"), "Internal registration notice must not appear");
        assert.ok(!html.includes("auth-logo-card"), "Old boxed visual must not appear");
        assert.ok(html.includes('href="/how-it-works"'), "Public guide link missing");
      }
      if (path === "/how-it-works") assert.ok(html.includes("How your project moves forward"));
      count++;
      console.log("PASS " + path);
    }
  }
  currentTheme = "dark";
  for (const [role, paths] of routes.filter(([role]) => role)) {
    currentUser = { fullName: "Smoke Test", email: "test@example.com", role };
    const html = await renderRoute(React.createElement(MemoryRouter, { initialEntries: [paths[0]] },
      React.createElement(AuthProvider, null, React.createElement(App))));
    assert.ok(html.includes('data-theme="dark"'), role + " must restore dark mode");
    assert.ok(html.includes('role="switch" aria-checked="true" aria-label="Dark mode"'), role + " switch must reflect saved theme");
    assert.ok(html.includes('class="sidebar-footer"'), "Theme control must be in the sidebar footer");
  }
  console.log("PASS theme persistence, storage fallback and all three dark dashboard shells");
  const { default: Metrics } = await server.ssrLoadModule("/src/components/DashboardMetrics.jsx");
  const { default: Directory } = await server.ssrLoadModule("/src/components/ProjectDirectory.jsx");
  const directory = renderToString(React.createElement(MemoryRouter, null, React.createElement(Directory, {
    projects:[{id:8,title:"Community Clinic",status:"ACTIVE"}],
    basePath:"/provider/disbursements",action:"View payments",
  })));
  assert.ok(directory.includes("Community Clinic"));
  assert.ok(directory.includes('href="/provider/disbursements/8"'));
  assert.ok(!directory.includes("Project #"));
  const metrics = renderToString(React.createElement(MemoryRouter, null, React.createElement(Metrics, {
    items: [{label:"Active projects",value:3,note:"Open projects",icon:"folder",to:"/provider/projects"}],
  })));
  assert.ok(metrics.includes('href="/provider/projects"'));
  assert.ok(metrics.includes("Active projects"));
  const { default: Filters } = await server.ssrLoadModule("/src/components/RecordFilters.jsx");
  const filters = renderToString(React.createElement(Filters, {label:"projects",filter:{
    query:"none",setQuery(){},status:"",setStatus(){},statuses:["ACTIVE"],filtered:[],total:3,
  }}));
  assert.ok(filters.includes("No matches"));
  assert.ok(filters.includes("Clear filters"));
  const { default: Snapshot } = await server.ssrLoadModule("/src/components/PortfolioSnapshot.jsx");
  const chart = renderToString(React.createElement(Snapshot, {
    records: [{status:"PENDING"}, {status:"PENDING"}, {status:"APPROVED"}],
    title:"Funding pipeline", noun:"Applications",
  }));
  assert.ok(chart.includes("Funding pipeline"));
  assert.ok(chart.includes("pending"));
  assert.ok(chart.includes("approved"));
  assert.ok(chart.includes('aria-pressed="true"'));
  assert.ok(chart.includes("<dd>2</dd>"), "Pending count must be available as text");
  assert.ok(chart.includes("<dd>1</dd>"), "Approved count must be available as text");
  const emptyChart = renderToString(React.createElement(Snapshot, {
    records: [], title:"Funding pipeline", noun:"Applications",
  }));
  assert.ok(emptyChart.includes("will appear here"));
  assert.ok(!emptyChart.includes("NaN"));
  console.log(count + " routes, role visibility, auth layout, filters and 3 shared components passed.");
} finally {
  await server.close();
  delete globalThis.localStorage;
  delete globalThis.sessionStorage;
}
