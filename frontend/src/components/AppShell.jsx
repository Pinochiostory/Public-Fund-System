import { useEffect, useState } from "react";
import { NavLink, Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import useScrollReveal from "./useScrollReveal";
import Icon from "./Icon";
import logo from "../assets/public-fund-logo.webp";
import api from "../services/api";
import { readTheme, saveTheme } from "./themePreference";
import { FluentProvider, webLightTheme, webDarkTheme } from "@fluentui/react-components";

const menus = {
  provider: [["Overview", "", "dashboard"], ["Apply for funding", "/applications/new", "document"], ["Projects", "/projects", "folder"], ["Evidence", "/evidence", "upload"], ["Payments", "/disbursements", "money"], ["Notifications", "/notifications", "bell"]],
  admin: [["Overview", "", "dashboard"], ["Applications", "/applications", "document"], ["Projects", "/projects", "folder"], ["Payments", "/disbursements", "money"], ["Evidence review", "/evidence", "shield"], ["Audit trail", "/audit-logs", "audit"], ["User Management", "/users", "user"], ["Staff accounts", "/accounts", "user"]],
  officer: [["Overview", "", "dashboard"], ["Evidence review", "/evidence", "shield"], ["Project phases", "/phases", "folder"]],
};
const labels = { provider: "Service provider", admin: "Administrator", officer: "Verification officer" };

export default function AppShell({ children }) {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const role = pathname.split("/")[1];
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const [theme, setTheme] = useState(() => readTheme(globalThis.localStorage));
  const toggleTheme = () => {
    const next = theme === "dark" ? "light" : "dark";
    setTheme(next);
    saveTheme(next, globalThis.localStorage);
  };
  const [query, setQuery] = useState("");
  const [unreadCount, setUnreadCount] = useState(0);
  useEffect(() => {
    const desktop = window.matchMedia("(min-width: 1024px)");
    const closeDrawer = event => { if (event.matches) setOpen(false); };
    desktop.addEventListener("change", closeDrawer);
    return () => desktop.removeEventListener("change", closeDrawer);
  }, []);
  useEffect(() => {
    if (role !== "provider") return;
    let active = true;
    const refresh = () => api.get("/notifications/my/unread-count")
      .then(response => {if(active)setUnreadCount(Number(response.data.unreadCount) || 0);})
      .catch(() => {});
    refresh();
    const timer = setInterval(refresh, 30000);
    window.addEventListener("focus", refresh);
    window.addEventListener("publicfund:notifications-changed", refresh);
    return () => {active=false;clearInterval(timer);window.removeEventListener("focus",refresh);window.removeEventListener("publicfund:notifications-changed",refresh);};
  }, [role]);
  useEffect(() => {
    if (!open) return;
    const sidebar = document.querySelector(".workspace-sidebar");
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    sidebar.querySelector("a, button, input")?.focus();
    const closeOnEscape = event => {
      if (event.key === "Escape") {
        setOpen(false);
        document.querySelector(".menu-toggle")?.focus();
      }
      if (event.key === "Tab") {
        const controls = [...sidebar.querySelectorAll("a,button,input")].filter(node => !node.disabled);
        const first = controls[0], last = controls.at(-1);
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
      }
    };
    document.addEventListener("keydown", closeOnEscape);
    return () => { document.removeEventListener("keydown", closeOnEscape); document.body.style.overflow = previousOverflow; };
  }, [open]);
  const contentRef = useScrollReveal(pathname);
  const menu = menus[role] || menus.provider;
  const title = menu.find(([, path]) => path && pathname.startsWith("/" + role + path))?.[0] || "Overview";
  const filteredMenu = menu.filter(([label]) => label.toLowerCase().includes(query.trim().toLowerCase()));
  const back = () => {
    if (window.history.state?.idx > 0) navigate(-1);
    else if (pathname.includes("/phases/")) navigate(pathname.split("/phases/")[0]);
    else if (pathname.split("/").length === 4 && (pathname.startsWith("/provider/evidence/") || pathname.startsWith("/provider/disbursements/"))) navigate(pathname.slice(0,pathname.lastIndexOf("/")));
    else navigate("/" + role);
  };
  const initials = (user?.fullName || user?.email || "").split(/\s+/).slice(0,2).map(part=>part[0]).join("").toUpperCase();
  const uiTheme = {...(theme === "dark" ? webDarkTheme : webLightTheme), fontFamilyBase:'Montaga, Georgia, serif', borderRadiusMedium:"8px", colorBrandBackground:"#245ac0", colorBrandBackgroundHover:"#19479e"};
  return <FluentProvider theme={uiTheme}><div className="workspace" data-layout="default" data-theme={theme}>
    <a className="skip-link" href="#workspace-content">Skip to content</a>
    <aside className={"workspace-sidebar" + (open ? " is-open" : "")}>
      <Link className="workspace-brand" to={"/" + role}><img src={logo} alt="" /><span>Public Fund<small>Funding & project management</small></span></Link>
      <div className="workspace-context"><Icon name="shield" size={22}/><div>Workspace<strong>{labels[role]}</strong></div></div>
      <label className="nav-search"><Icon name="search" size={18} /><input aria-label="Find a workspace page" placeholder="Find a page…" value={query} onChange={event => setQuery(event.target.value)} /></label>
      <div className="nav-section-label">Workspace</div>
      <nav aria-label="Main navigation">{filteredMenu.map(([label, path, icon]) => <NavLink key={path} end={!path} to={"/" + role + path} onClick={() => {setOpen(false);setQuery("");}}><Icon name={icon} size={20} /><span>{label}</span>{path === "/notifications" && unreadCount > 0 && <span className="notification-count" aria-label={unreadCount+" unread notifications"}>{unreadCount > 99 ? "99+" : unreadCount}</span>}<Icon className="nav-chevron" name="arrowRight" size={15}/></NavLink>)}</nav>
      {filteredMenu.length === 0 && <p className="nav-no-results" role="status">No pages found. <button onClick={() => setQuery("")}>Clear search</button></p>}
      <div className="sidebar-footer">
        {role === "provider" && <Link to="/how-it-works" className="workspace-help"><Icon name="help" /><span>How it works</span><Icon name="arrowRight" size={16}/></Link>}
        <button className="theme-toggle" type="button" role="switch" aria-checked={theme === "dark"} aria-label="Dark mode" onClick={toggleTheme}>
          <Icon name={theme === "dark" ? "moon" : "sun"} size={19}/>
          <span>Dark mode</span>
          <span className="theme-switch" aria-hidden="true"><span /></span>
        </button>
      </div>
    </aside>
    <div className="workspace-body">
      <header className="workspace-topbar">
        <div className="workspace-topbar-left">
        <button className="menu-toggle" onClick={() => setOpen(!open)} aria-expanded={open} aria-label="Toggle navigation"><Icon name={open ? "close" : "menu"} /></button>
      <div className="workspace-breadcrumbs">
        <button className="workspace-back" onClick={back} aria-label="Go back"><Icon name="arrowRight" className="back-arrow" size={17}/>Back</button>
        <nav aria-label="Breadcrumb"><span>Workspace</span><span aria-hidden="true">/</span><span aria-current="page">{title}</span></nav>
      </div>
        </div>
        <div className="topbar-actions"><div className="workspace-title"><span className="profile-initials" aria-hidden="true">{initials}</span><strong>{user?.fullName || user?.email}</strong></div><button className="workspace-back navbar-signout" onClick={logout}><Icon name="logout" size={17}/><span>Sign out</span></button></div>
      </header>
      <div id="workspace-content" ref={contentRef} tabIndex={-1}>{children}</div>
    </div>
    {open && <button className="menu-scrim" aria-label="Close navigation" onClick={() => setOpen(false)} />}
  </div></FluentProvider>;
}
