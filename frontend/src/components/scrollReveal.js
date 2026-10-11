// Animation markers must never change className: legacy page styles match
// class-name suffixes, so adding a class can remove their styling.
export function installScrollReveal(container, browser = window) {
  if (!container || !browser.matchMedia || !browser.IntersectionObserver || !browser.MutationObserver) return () => {};
  // Remove markers left on mounted elements by the previous hot-reloaded version.
  container.querySelectorAll(".reveal-on-scroll").forEach(node => {
    node.classList.remove("reveal-on-scroll", "is-revealed");
  });
  const preference = browser.matchMedia("(prefers-reduced-motion: reduce)");
  if (preference.matches) return () => {};
  const seen = new WeakSet();
  const observer = new browser.IntersectionObserver(entries => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.dataset.revealed = "";
        observer.unobserve(entry.target);
      }
    });
  }, { threshold: 0.04, rootMargin: "0px 0px -16px 0px" });
  const scan = () => {
    container.querySelectorAll("main > header, main > section, main > .record-tools, .dashboard-metrics > a, .portfolio-snapshot, [class$='-card'], .guide-roles > article, .guide-workflow li").forEach((node, index) => {
      if (seen.has(node) || node.closest(".skeleton-screen")) return;
      seen.add(node);
      node.style.setProperty("--reveal-delay", (index % 4) * 55 + "ms");
      node.dataset.reveal = "";
      observer.observe(node);
    });
  };
  scan();
  const mutations = new browser.MutationObserver(scan);
  mutations.observe(container, { childList: true, subtree: true });
  const revealAll = event => {
    if (event.matches) container.querySelectorAll("[data-reveal]").forEach(node => {node.dataset.revealed = "";});
  };
  preference.addEventListener("change", revealAll);
  return () => {
    observer.disconnect();
    mutations.disconnect();
    preference.removeEventListener("change", revealAll);
    container.querySelectorAll("[data-reveal]").forEach(node => {
      delete node.dataset.reveal;
      delete node.dataset.revealed;
      node.style.removeProperty("--reveal-delay");
    });
  };
}
