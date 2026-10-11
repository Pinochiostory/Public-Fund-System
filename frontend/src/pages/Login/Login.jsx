import Link from "../../components/AuthLink";
import GoogleSignIn from "../../components/GoogleSignIn";
import LoginLayout from "../../components/LoginLayout";
import { useState } from "react";
import { useNavigate } from "react-router-dom";

import api from "../../services/api";
import { useAuth } from "../../context/AuthContext";
import Icon from "../../components/Icon";


function Login() {
  const navigate = useNavigate();
  const { login } = useAuth();

  const [formData, setFormData] = useState({
    email: "",
    password: "",
  });

  const [error, setError] = useState(() => sessionStorage.getItem("session-message") || "");
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const handleChange = (event) => {
    const { name, value } = event.target;

    setFormData((previous) => ({
      ...previous,
      [name]: value,
    }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    setError("");
    setLoading(true);

    try {
      const params = new URLSearchParams();

      params.append("email", formData.email);
      params.append("password", formData.password);

      // All roles now use database accounts through the existing login endpoint.
      // Do not retry a failed password against a second endpoint (double lockout count).
      const response = await api.post("/auth/login", params);

      const user = response.data.user;
      const token = response.data.token;

      login(user, token);

      if (user.mustChangePassword) {
        navigate("/change-password", { replace: true });
        return;
      }

      if (user.role === "ADMINISTRATOR") {
        navigate("/admin");
        return;
      }

      if (user.role === "VERIFICATION_OFFICER") {
        navigate("/officer");
        return;
      }

      if (user.role === "SERVICE_PROVIDER") {
        navigate("/provider");
        return;
      }

      setError("Unknown user role.");

    } catch (error) {
      const message =
        error.response?.data?.message || error.response?.data ||
        "Unable to login. Please check your details.";

      setError(
        typeof message === "string"
          ? message
          : "Unable to login."
      );

    } finally {
      setLoading(false);
    }
  };

  return <LoginLayout>
<div className="login-content">
          <h1>Welcome back</h1>

          <p className="login-description">
            Sign in to your Public Fund workspace.
          </p>

          <nav className="login-mode-nav" aria-label="Account access">
            <Link to="/login" aria-current="page">Sign in</Link>
            <Link to="/register">Register</Link>
          </nav>

          <form
            className="login-form"
            onSubmit={handleSubmit}
          >
            <div className="form-group">
              <div className="auth-field">
                <Icon name="mail" size={18} />
                <div className="login-field-content">
                <label htmlFor="email">Email address</label>
                <input
                  id="email"
                  name="email"
                  type="email"
                  value={formData.email}
                  onChange={handleChange}
                  placeholder="name@example.com"
                  autoComplete="email"
                  required
                />
                </div>
              </div>
            </div>

            <div className="form-group">
              <div className="auth-field">
                <Icon name="shield" size={18} />
                <div className="login-field-content">
                <label htmlFor="password">Password</label>
                <input
                  id="password"
                  name="password"
                  type={showPassword ? "text" : "password"}
                  value={formData.password}
                  onChange={handleChange}
                  placeholder="Enter your password"
                  autoComplete="current-password"
                  required
                />
                </div>

                <button
                  type="button"
                  className="password-toggle"
                  onClick={() => setShowPassword((visible) => !visible)}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  <Icon name={showPassword ? "eyeOff" : "eye"} size={18} />
                </button>
              </div>
            </div>

            <div className="login-forgot"><Link to="/forgot-password">Forgot password?</Link></div>

            {error && (
              <div className="login-error" role="alert">
                {error}
              </div>
            )}

            <button
              type="submit"
              className="login-button"
              disabled={loading}
              aria-busy={loading}
            >
              {loading && <span className="button-spinner" aria-hidden="true" />}
              <span>{loading ? "Signing in..." : "Sign in"}</span>
              {!loading && <Icon name="arrowRight" size={18} />}
            </button>
          </form>
          <GoogleSignIn disabled={loading} />

          <p className="register-link"><Link to="/verify-email">Verify email or resend code</Link></p>
        </div>
</LoginLayout>;
}

export default Login;
