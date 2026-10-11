import { useLocation } from "react-router-dom";
import AuthLink from "./AuthLink";
import Icon from "./Icon";
import logo from "../assets/public-fund-logo.webp";
import portrait from "../assets/public-fund-portrait.png";
import "../styles/login-entry.css";

export default function LoginLayout({ children, back }) {
  const { pathname } = useLocation();
  return <div className="login-entry" data-auth-page={pathname.slice(1)}>
    <div className="login-entry-layout">
      <div className="login-entry-panel">
        <header className="login-entry-header">
          <AuthLink to="/login" className="public-brand">
            <img src={logo} alt="" width="44" height="44" />
            <span>Public Fund<small>Management System</small></span>
          </AuthLink>
        </header>
        <main className="login-entry-main" key={pathname} id="login-form"><div className="auth-form-stage">{back && !["/register", "/forgot-password"].includes(pathname) && <AuthLink className="auth-back" to={back.to}><Icon name="arrowRight" className="back-arrow" size={16}/>{back.label}</AuthLink>}{children}</div></main>
        <footer className="login-entry-footer">
          <p>Manage funding. Track progress. Keep every decision in view.</p>
          <AuthLink to="/how-it-works"><Icon name="help" size={18} />How it works<Icon name="arrowRight" size={15} /></AuthLink>
        </footer>
      </div>
      <aside className="login-portrait" aria-label="Public Fund identity">
        <picture>
          <source media="(min-width: 768px)" srcSet={portrait} />
          <img src={logo} alt="Public Fund Management System, with South African colours, a protea and landscape motifs" width="1122" height="1402" fetchPriority="high" />
        </picture>
      </aside>
    </div>
  </div>;
}
