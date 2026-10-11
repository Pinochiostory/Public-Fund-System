import { useState } from "react";
import { useLocation } from "react-router-dom";
import { readTheme, saveTheme } from "./themePreference";
import Icon from "./Icon";

function PublicTheme({children}) {
  const [theme,setTheme] = useState(()=>readTheme(globalThis.localStorage));
  const toggle = () => { const next=theme === "dark" ? "light" : "dark"; setTheme(next); saveTheme(next,globalThis.localStorage); };
  return <div className="public-surface" data-theme={theme}>{children}<button className="public-theme-toggle" onClick={toggle} aria-label={theme === "dark" ? "Use light mode" : "Use dark mode"} title={theme === "dark" ? "Use light mode" : "Use dark mode"}><Icon name={theme === "dark" ? "sun" : "moon"} size={19}/></button></div>;
}
export default function PublicAppearance({children}) {
  const {pathname} = useLocation();
  if (/^\/(provider|admin|officer)(\/|$)/.test(pathname)) return children;
  return <PublicTheme>{children}</PublicTheme>;
}
