import ActionFeedback from "../../components/ActionFeedback";
import Link from "../../components/AuthLink";
import AuthLayout from "../../components/AuthLayout";
import { useState } from "react";


import api from "../../services/api";
import Icon from "../../components/Icon";


function ForgotPassword() {


  const [email, setEmail] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");
    setMessage("");
    setLoading(true);

    try {
      const params = new URLSearchParams();
      params.append("email", email);

      const response = await api.post(
        "/auth/forgot-password",
        params
      );

      const responseMessage =
        response.data?.message ||
        (typeof response.data === "string" ? response.data : "") ||
        "If an account exists for this email, a reset link has been emailed.";

      setMessage(responseMessage);
    } catch (requestError) {
      const responseMessage =
        requestError.response?.data?.message ||
        requestError.response?.data ||
        "Unable to request a password reset right now.";

      setError(
        typeof responseMessage === "string"
          ? responseMessage
          : "Unable to request a password reset right now."
      );
    } finally {
      setLoading(false);
    }
  };

  return <AuthLayout title="Keep your work moving." description="Recover access to your projects, funding records and decisions." back={{to:"/login",label:"Back to sign in"}}>
<div className="recovery-content">
          <div className="recovery-icon">
            <Icon name="shield" size={26} />
          </div>

          <p className="recovery-eyebrow">ACCOUNT RECOVERY</p>
          <h1>Reset your password</h1>
          <p className="recovery-description">
            Enter the email linked to your account. A reset request
            is valid for 30 minutes and can only be used once.
          </p>

          {!message ? (
            <form className="recovery-form" onSubmit={handleSubmit}>
              <label htmlFor="recovery-email">Email address</label>

              <div className="input-with-icon">
                <Icon name="mail" size={18} />
                <input
                  id="recovery-email"
                  type="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  placeholder="name@example.com"
                  autoComplete="email"
                  required
                  autoFocus
                />
              </div>

              {error && (
                <div className="recovery-alert error" role="alert">
                  {error}
                </div>
              )}

              <button
                className="recovery-primary"
                type="submit"
                disabled={loading}
              >
                <span>
                  {loading ? "Sending reset link..." : "Continue"}
                </span>
                {!loading && <Icon name="arrowRight" size={18} />}
              </button>
            </form>
          ) : (
            <div className="recovery-result">
              <ActionFeedback message={message} title="Reset link requested"/>

              <p className="recovery-neutral">
                Check your inbox and spam folder. Staff accounts use administrator-assisted recovery.
              </p>

              <button
                className="recovery-text-button"
                type="button"
                onClick={() => {
                  setMessage("");
                }}
              >
                Try another email
              </button>
            </div>
          )}

          <Link className="recovery-back" to="/login">
            <span aria-hidden="true">←</span>
            Back to sign in
          </Link>
        </div>
</AuthLayout>;
}

export default ForgotPassword;
