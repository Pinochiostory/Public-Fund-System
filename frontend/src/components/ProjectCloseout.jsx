import { useActionFeedback } from "./ActionFeedback";
import { useRef, useState } from "react";
import { useAuth } from "../context/AuthContext";
import api from "../services/api";
import { formatMoney, formatDate, errorMessage } from "./projectData";
import useConfirmation from "./useConfirmation";
import UnsavedChanges from "./UnsavedChanges";

export default function ProjectCloseout({ project, onChanged }) {
  const { user } = useAuth();
  const notify=useActionFeedback();
  const [reference, setReference] = useState(""), [note, setNote] = useState("");
  const [busy, setBusy] = useState(false), [error, setError] = useState(""), [saved, setSaved] = useState(null);
  const processing = useRef(false);
  const { confirm, dialog } = useConfirmation();
  const record = saved || project;
  if (project.status !== "COMPLETED") return null;
  const recorded = record.closeoutRetainedAmount != null;
  const pending = recorded && Number(record.closeoutRetainedAmount) > 0 && !record.closeoutReviewedAt;
  const admin = user?.role === "ADMINISTRATOR";
  const own = String(record.closeoutInitiatedBy) === String(user?.id);
  const submit = async event => {
    event.preventDefault();
    if (processing.current) return;
    processing.current = true;
    try {
      const accepted = await confirm({ title: "Confirm independent balance review",
        message: `Record that ${formatMoney(record.closeoutRetainedAmount)} of undisbursed funding for "${project.title}" remains with the funder? Reference: ${reference.trim()}. This does not authorize a payout or confirm the return of money already released.`,
        action: "Record review" });
      if (!accepted) return;
      setBusy(true); setError("");
      const response = await api.post("/projects/" + project.id + "/closeout-review",
        new URLSearchParams({ reference: reference.trim(), note: note.trim() }),
        { headers: { "If-Match": String(project.version) } });
      notify("The independent closeout review has been recorded. No additional payment was authorized.", "Closeout review saved");
      setSaved(response.data); setReference(""); setNote("");
      try { await onChanged?.(); } catch { setError("Review saved, but the page could not refresh. Reload to see the latest activity; do not submit again."); }
    } catch (e) { setError(errorMessage(e, "The review was not recorded. Please retry or refresh the project.")); }
    finally { setBusy(false); processing.current = false; }
  };
  return <section className="workflow-details" aria-label="Project closeout">
    {dialog}<h3>Project closeout</h3>
    {!recorded ? <p className="provider-project-notice">This older project has no frozen closeout record. Its balance needs reconciliation; it is not automatically cleared or available for payout.</p> : <>
      <dl className="workflow-facts">
        <div><dt>Approved at closeout</dt><dd>{formatMoney(record.closeoutApprovedBudget)}</dd></div>
        <div><dt>Released at closeout</dt><dd>{formatMoney(record.closeoutReleasedAmount)}</dd></div>
        <div><dt>Frozen undisbursed allocation</dt><dd>{formatMoney(record.closeoutRetainedAmount)}</dd></div>
        <div><dt>Recorded</dt><dd>{formatDate(record.completedAt)}</dd></div>
      </dl>
      <p>No further payments can be recorded for this closed project. Undisbursed funds stay with the funder, not the provider or an Administrator.</p>
      <p>This balance excludes money already paid to the provider. Reconcile any unspent advance in the Expenditure and fund returns section below.</p>
      {pending && <p className="provider-project-notice">Waiting for a different Administrator to independently review the retained allocation.</p>}
      {record.closeoutReviewedAt && <p role="status">Independent review recorded {formatDate(record.closeoutReviewedAt)}. Reference: {record.closeoutReference}. Note: {record.closeoutNote}</p>}
      {recorded && Number(record.closeoutRetainedAmount) === 0 && <p>No undisbursed allocation remains.</p>}
      {pending && admin && own && <p>You recorded this closeout. A different Administrator must review it.</p>}
      {pending && admin && !own && <form className="closeout-form" onSubmit={submit}>
        <UnsavedChanges dirty={Boolean(reference || note) && !busy}/>
        <label>Funder reconciliation reference<input required maxLength={100} value={reference} onChange={e => setReference(e.target.value)} disabled={busy}/></label>
        <label>Reconciliation explanation<textarea required maxLength={1000} rows={3} value={note} onChange={e => setNote(e.target.value)} disabled={busy}/></label>
        <button className="project-action" disabled={busy || !reference.trim() || !note.trim()}>{busy ? "Saving review..." : "Review retained allocation"}</button>
      </form>}
    </>}
    {error && <p className="project-error" role="alert">{error}</p>}
  </section>;
}
