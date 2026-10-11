import LoginLayout from "./LoginLayout";

// One shared visual shell; each existing page keeps its authentication behaviour.
export default function AuthLayout({ children, back }) {
  return <LoginLayout back={back}>{children}</LoginLayout>;
}
