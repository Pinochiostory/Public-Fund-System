import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { installScrollReveal } from "../src/components/scrollReveal.js";

let onIntersection, onMutation, onPreference;
const observed = new Set();
const browser = {
  matchMedia: () => ({
    matches:false,
    addEventListener: (_, fn) => {onPreference = fn;},
    removeEventListener: () => {},
  }),
  IntersectionObserver: class {
    constructor(callback) {onIntersection = callback;}
    observe(node) {observed.add(node);}
    unobserve(node) {observed.delete(node);}
    disconnect() {observed.clear();}
  },
  MutationObserver: class {
    constructor(callback) {onMutation = callback;}
    observe() {}
    disconnect() {}
  },
};
const card = className => ({
  className, dataset:{}, style:{setProperty(){},removeProperty(){}},
  closest: () => null,
});
const nodes = [card("provider-card"), card("admin-stat-card"), card("officer-phase-card")];
const root = {querySelectorAll: selector => selector === ".reveal-on-scroll" ? [] : selector === "[data-reveal]" ? nodes.filter(node => "reveal" in node.dataset) : nodes};
const cleanup = installScrollReveal(root, browser);
assert.equal(observed.size, 3);
for (const node of nodes) {
  const originalClass = node.className;
  assert.ok(node.className.endsWith("-card"));
  onIntersection([{target:node,isIntersecting:true}]);
  assert.equal(node.className, originalClass, "Animation must not change style selectors");
  assert.ok("revealed" in node.dataset);
}
// Async-loaded project cards must also preserve styling.
nodes.push(card("project-directory-card"), card("payment-detail-card"), card("evidence-history-card"));
onMutation();
assert.equal(observed.size,3);
onPreference({matches:true});
assert.ok(nodes.every(node => "revealed" in node.dataset));
cleanup();
assert.ok(nodes.every(node => Object.keys(node.dataset).length === 0));
assert.equal(observed.size,0);
const reducedCard = card("project-directory-card");
installScrollReveal({querySelectorAll:()=>[]}, {...browser,matchMedia:()=>({matches:true})})();
assert.deepEqual(reducedCard.dataset,{});

const theme = readFileSync(new URL("../src/styles/workspace.css",import.meta.url),"utf8");
const app = readFileSync(new URL("../src/App.jsx",import.meta.url),"utf8");
assert.ok(!app.includes('"./project-workspace.css"'), "Legacy overrides must not be loaded");
assert.ok(!app.includes('"./workspace-design.css"'), "Legacy overrides must not be loaded");
assert.match(theme,/\.workspace\[data-layout="default"\]\s*\{[^}]*margin:0;[^}]*border-radius:0;/);
assert.match(theme,/border-radius:var\(--radius\)/);
assert.ok(theme.includes(".workspace[data-layout=\"default\"] .workspace-back"));
assert.ok(!/\.navbar-signout\s*\{/.test(theme), "Sign out must not introduce a separate button design");
assert.ok(theme.includes(".project-page .project-directory-grid"));
console.log("Default shell, rounded cards, shared buttons and async animation-style regression checks passed.");
