export const THEME_KEY = "public-fund-theme";

export function readTheme(storage) {
  try { return storage?.getItem(THEME_KEY) === "dark" ? "dark" : "light"; }
  catch { return "light"; }
}

export function saveTheme(theme, storage) {
  try { storage?.setItem(THEME_KEY, theme); }
  catch { /* The toggle still works when browser storage is unavailable. */ }
}
