import { useEffect, useRef } from "react";
import { Link, useNavigate } from "react-router-dom";

// Animate the outgoing account screen before routing, without a second router
// or keeping hidden password forms mounted. Modified clicks retain link behaviour.
export default function AuthLink({ to, children, onClick, ...props }) {
  const navigate = useNavigate();
  const flight = useRef(null);
  useEffect(() => () => {
    flight.current?.forEach(animation => animation.cancel());
    flight.current = null;
  }, []);
  const follow = async event => {
    onClick?.(event);
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey ||
        props.target || window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const shell = event.currentTarget.closest(".login-entry");
    if (!shell || !["/login", "/register", "/forgot-password"].includes(to) || !Element.prototype.animate) return;
    event.preventDefault();
    if (flight.current || shell.dataset.leaving) return;
    shell.dataset.leaving = "true";
    const direction = to === "/login" ? 1 : -1;
    const animations = [
      [shell.querySelector(".auth-form-stage"), direction * 100],
      [shell.querySelector(".login-portrait"), -direction * 160],
    ].filter(([element]) => element).map(([element, offset]) =>
      element.animate([{ opacity: 1, transform: "translateX(0) scale(1)" },
        { opacity: 0, transform: `translateX(${offset}px) scale(.94)` }],
      { duration: 420, easing: "cubic-bezier(.55,0,.7,.3)", fill: "forwards" }));
    flight.current = animations;
    await Promise.allSettled(animations.map(animation => animation.finished));
    if (flight.current !== animations) return;
    delete shell.dataset.leaving;
    navigate(to);
    animations.forEach(animation => animation.cancel());
    flight.current = null;
  };
  return <Link {...props} to={to} onClick={follow}>{children}</Link>;
}
