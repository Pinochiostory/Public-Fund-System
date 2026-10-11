import { useActionFeedback } from "../components/ActionFeedback";
import { useState } from "react";
import { Link, useLocation } from "react-router-dom";
import AuthLayout from "../components/AuthLayout";
import api from "../services/api";
import { errorMessage } from "../components/projectData";

export default function VerifyEmail() {
  const location=useLocation();
  const notify=useActionFeedback();
  const [email,setEmail]=useState(location.state?.email||"");
  const [code,setCode]=useState("");
  const [message,setMessage]=useState(location.state?.message||"Enter the code from your email. Codes expire after 10 minutes.");
  const [error,setError]=useState("");
  const [busy,setBusy]=useState(false);
  const [verified,setVerified]=useState(false);
  const submit=async(event,resend=false)=>{
    event.preventDefault(); if(busy)return;setBusy(true);setError("");setMessage("");
    try {
      const params=new URLSearchParams({email,...(!resend?{code}:{})});
      const {data}=await api.post(resend?"/auth/resend-verification":"/auth/verify-email",params);
      notify(data.message,resend ? "Verification code sent" : "Email verified");if(!resend)setVerified(true);
    } catch(e){setError(errorMessage(e,"Verification could not be completed. Please try again."));}
    finally{setBusy(false);}
  };
  return <AuthLayout back={{to:"/login",label:"Back to sign in"}}><div className="register-content">
    <h1>Verify your email</h1><p>Confirm your email address before accessing the funding platform.</p>
    {message&&<div className="register-success" role="status">{message}</div>}
    {error&&<div className="register-error" role="alert">{error}</div>}
    {verified?<Link className="project-action" to="/login">Continue to sign in</Link>:<form className="register-form" onSubmit={submit}>
      <label>Email address<div className="auth-field"><input type="email" value={email} autoComplete="email" onChange={e=>setEmail(e.target.value)} required disabled={busy}/></div></label>
      <label>Verification code<div className="auth-field"><input inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6} value={code} onChange={e=>setCode(e.target.value)} placeholder="6-digit code" required disabled={busy}/></div></label>
      <button className="register-button" disabled={busy}>{busy?"Please wait…":"Verify email"}</button>
      <button className="project-action secondary" type="button" disabled={busy||!email} onClick={e=>submit(e,true)}>Resend Code</button>
      <small>Wait 60 seconds between resend requests. Up to 3 codes can be requested per hour.</small>
    </form>}
  </div></AuthLayout>;
}
