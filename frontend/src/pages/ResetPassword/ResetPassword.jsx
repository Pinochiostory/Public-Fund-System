import ActionFeedback from "../../components/ActionFeedback";
import Link from "../../components/AuthLink";
import AuthLayout from "../../components/AuthLayout";
import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";

import api from "../../services/api";
import Icon from "../../components/Icon";


const specialCharacters = "!@#$%^&*()_+-=[]{};':\"\\|,.<>/?";

const passwordRules = [
  { label: "At least 8 characters", test: (value) => value.length >= 8 },
  { label: "One uppercase letter", test: (value) => /[A-Z]/.test(value) },
  { label: "One lowercase letter", test: (value) => /[a-z]/.test(value) },
  { label: "One number", test: (value) => /[0-9]/.test(value) },
  {
    label: "One special character",
    test: (value) =>
      Array.from(value).some((character) =>
        specialCharacters.includes(character)
      ),
  },
];

function ResetPassword() {
  const [searchParams] = useSearchParams();

  const [token] = useState(() => new URLSearchParams(typeof window === "undefined" ? "" : window.location.hash.slice(1)).get("token") || searchParams.get("token") || "");
  useEffect(() => {
    // Keep the link secret out of subsequent history/referrer URLs and browser storage.
    window.history.replaceState(window.history.state, "", window.location.pathname);
    sessionStorage.removeItem("passwordResetToken");
  }, []);
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [loading, setLoading] = useState(false);

  const ruleResults = useMemo(
    () =>
      passwordRules.map((rule) => ({
        ...rule,
        passed: rule.test(newPassword),
      })),
    [newPassword]
  );

  const passwordIsValid = ruleResults.every((rule) => rule.passed);
  const passwordsMatch =
    confirmPassword.length > 0 && newPassword === confirmPassword;

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");

    if (!passwordIsValid) {
      setError("Your new password does not meet all requirements.");
      return;
    }

    if (!passwordsMatch) {
      setError("The passwords do not match.");
      return;
    }

    setLoading(true);

    try {
      const params = new URLSearchParams();
      params.append("token", token.trim());
      params.append("newPassword", newPassword);

      const response = await api.post(
        "/auth/reset-password",
        params
      );

      setSuccess(
        typeof response.data === "string"
          ? response.data
          : "Password reset successfully."
      );
      sessionStorage.removeItem("passwordResetToken");
    } catch (requestError) {
      const responseMessage =
        requestError.response?.data?.message ||
        requestError.response?.data ||
        "Unable to reset your password.";

      setError(
        typeof responseMessage === "string"
          ? responseMessage
          : "Unable to reset your password."
      );
    } finally {
      setLoading(false);
    }
  };

  return <AuthLayout title="Keep your work moving." description="Recover access to your projects, funding records and decisions." back={{to:"/login",label:"Back to sign in"}}>
<div className="recovery-content recovery-content-wide">
          <div className="recovery-icon">
            <Icon name={success ? "check" : "shield"} size={26} />
          </div>

          <p className="recovery-eyebrow">NEW CREDENTIALS</p>
          <h1>{success ? "Password updated" : "Choose a new password"}</h1>
          <p className="recovery-description">
            {success
              ? "Your account is ready. You can now sign in with your new password."
              : "Choose a strong password to finish resetting your account."}
          </p>

          {success ? (
            <div className="recovery-result">
              <ActionFeedback message={success} title="Password updated"/>

              <Link className="recovery-primary" to="/login">
                <span>Return to sign in</span>
                <Icon name="arrowRight" size={18} />
              </Link>
            </div>
          ) : (
            <form className="recovery-form" onSubmit={handleSubmit}>
              {!token && <div className="recovery-alert error" role="alert">Open the reset link in your email, or request a new link below.</div>}
              <label htmlFor="new-password">New password</label>
              <div className="input-with-icon">
                <Icon name="shield" size={18} />
                <input
                  id="new-password"
                  type={showPassword ? "text" : "password"}
                  value={newPassword}
                  onChange={(event) => setNewPassword(event.target.value)}
                  placeholder="Create a secure password"
                  autoComplete="new-password"
                  required
                />

                <button
                  className="password-toggle"
                  type="button"
                  onClick={() => setShowPassword((visible) => !visible)}
                  aria-label={showPassword ? "Hide passwords" : "Show passwords"}
                >
                  <Icon name={showPassword ? "eyeOff" : "eye"} size={18} />
                </button>
              </div>

              <div className="password-rules" aria-live="polite">
                {ruleResults.map((rule) => (
                  <span className={rule.passed ? "passed" : ""} key={rule.label}>
                    <Icon name="check" size={13} />
                    {rule.label}
                  </span>
                ))}
              </div>

              <label htmlFor="confirm-new-password">Confirm password</label>
              <div className="input-with-icon">
                <Icon name="shield" size={18} />
                <input
                  id="confirm-new-password"
                  type={showPassword ? "text" : "password"}
                  value={confirmPassword}
                  onChange={(event) => setConfirmPassword(event.target.value)}
                  placeholder="Repeat your new password"
                  autoComplete="new-password"
                  required
                />
              </div>

              {confirmPassword && (
                <span
                  className={`password-match ${passwordsMatch ? "matched" : ""}`}
                >
                  {passwordsMatch
                    ? "Passwords match"
                    : "Passwords do not match"}
                </span>
              )}

              {error && (
                <div className="recovery-alert error" role="alert">
                  {error}
                </div>
              )}

              <button
                className="recovery-primary"
                type="submit"
                disabled={loading || !token.trim()}
              >
                <span>{loading ? "Updating password..." : "Reset password"}</span>
                {!loading && <Icon name="arrowRight" size={18} />}
              </button>
            </form>
          )}

          {!success && (
            <Link className="recovery-back" to="/forgot-password">
              <span aria-hidden="true">←</span>
              Request another reset link
            </Link>
          )}
        </div>
</AuthLayout>;
}

export default ResetPassword;
