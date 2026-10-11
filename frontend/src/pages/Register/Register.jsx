import ActionFeedback from "../../components/ActionFeedback";
import Link from "../../components/AuthLink";
import GoogleSignIn from "../../components/GoogleSignIn";
import AuthLayout from "../../components/AuthLayout";
import { useState } from "react";
import { useNavigate } from "react-router-dom";

import api from "../../services/api";
import Icon from "../../components/Icon";


function Register() {
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    fullName: "",
    email: "",
    password: "",
    confirmPassword: "",
    role: "SERVICE_PROVIDER",
  });

  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
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
    setSuccess("");

    if (formData.password !== formData.confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    setLoading(true);

    try {
      const params = new URLSearchParams();

      params.append("fullName", formData.fullName);
      params.append("email", formData.email);
      params.append("password", formData.password);
      params.append("role", "SERVICE_PROVIDER");

      const response = await api.post(
        "/auth/register",
        params
      );

      navigate("/verify-email", { state: {email:formData.email, message:response.data.message} });

    } catch (error) {
      const message =
        error.response?.data?.message || error.response?.data ||
        "Unable to create account.";

      setError(
        typeof message === "string"
          ? message
          : "Unable to create account."
      );

    } finally {
      setLoading(false);
    }
  };

  return <AuthLayout title="Good projects start here." description="Apply for funding, deliver the work, and keep every milestone in view." back={{to:"/login",label:"Back to sign in"}}>
<div className="register-content">
          <p className="register-label">
            CREATE ACCOUNT
          </p>

          <h1>Create your account</h1>

          <p className="register-description">Create your service provider account.</p>
          <nav className="login-mode-nav" aria-label="Account access">
            <Link to="/login">Sign in</Link><Link to="/register" aria-current="page">Register</Link>
          </nav>

          <form
            className="register-form"
            onSubmit={handleSubmit}
          >
            <div className="register-form-group">
              <div className="auth-field">
                <Icon name="user" size={18} />
                <div className="login-field-content"><label htmlFor="fullName">
                Full name
              </label>
                <input
                  id="fullName"
                  name="fullName"
                  autoComplete="name"
                  type="text"
                  placeholder="Enter your full name"
                  value={formData.fullName}
                  onChange={handleChange}
                  required
                /></div>
              </div>
            </div>

            <div className="register-form-group">
              <div className="auth-field">
                <Icon name="mail" size={18} />
                <div className="login-field-content"><label htmlFor="email">
                Email address
              </label>
                <input
                  id="email"
                  name="email"
                  autoComplete="email"
                  type="email"
                  placeholder="name@example.com"
                  value={formData.email}
                  onChange={handleChange}
                  required
                /></div>
              </div>
            </div>

            <div className="register-form-group">
              <div className="auth-field">
                <Icon name="shield" size={18} />
                <div className="login-field-content"><label htmlFor="password">
                Password
              </label>
                <input
                  id="password"
                  name="password"
                  autoComplete="new-password"
                  type={showPassword ? "text" : "password"}
                  placeholder="Create a secure password"
                  value={formData.password}
                  onChange={handleChange}
                  required
                /></div>

                <button
                  type="button"
                  className="password-toggle"
                  onClick={() => setShowPassword((visible) => !visible)}
                  aria-label={showPassword ? "Hide passwords" : "Show passwords"}
                >
                  <Icon name={showPassword ? "eyeOff" : "eye"} size={18} />
                </button>
              </div>

              <span className="password-help">
                Minimum 8 characters with uppercase,
                lowercase, number and special character.
              </span>
            </div>

            <div className="register-form-group">
              <div className="auth-field">
                <Icon name="shield" size={18} />
                <div className="login-field-content"><label htmlFor="confirmPassword">
                Confirm password
              </label>
                <input
                  id="confirmPassword"
                  name="confirmPassword"
                  autoComplete="new-password"
                  type={showPassword ? "text" : "password"}
                  placeholder="Repeat your password"
                  value={formData.confirmPassword}
                  onChange={handleChange}
                  required
                /></div>
              </div>
            </div>

            {error && (
              <div className="register-error" role="alert">
                {error}
              </div>
            )}

            {success && (
              <ActionFeedback message={success}/>
            )}

            <button
              type="submit"
              className="register-button"
              disabled={loading}
              aria-busy={loading}
            >
              {loading && <span className="button-spinner" aria-hidden="true" />}
              <span>
                {loading ? "Creating account..." : "Create account"}
              </span>
              {!loading && <Icon name="arrowRight" size={18} />}
            </button>
          </form>
          <GoogleSignIn disabled={loading} />

          
        </div>
</AuthLayout>;
}

export default Register;
