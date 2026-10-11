import { useEffect, useRef, useState } from "react";
import { useAuth } from "../context/AuthContext";
import api from "../services/api";
import { formatMoney, formatDate, errorMessage } from "./projectData";
import { useActionFeedback } from "./ActionFeedback";
import useConfirmation from "./useConfirmation";
import UnsavedChanges from "./UnsavedChanges";

const descriptions = {
  NOT_SUBMITTED: "Action required: the provider must declare actual expenditure with supporting documents.",
  PENDING_REVIEW: "Waiting for an Administrator to review the expenditure declaration.",
  CHANGES_REQUIRED: "The declaration was rejected. Review the reason below and submit a corrected statement.",
  RETURN_DUE: "Unspent funds must be returned to the official funder. They are not available for another project payment.",
  RETURN_UNDER_REVIEW: "A return has been reported, but has not yet been verified against the funder's records.",
  RECONCILED: "Expenditure is approved and all required returns have been verified. No further payout is authorized.",
  INVESTIGATION_REQUIRED: "Financial records differ from the declaration. Contact the system owner; do not make another transfer."
};

export default function FundReconciliation({ project, onChanged, onStatus }) {
  const { user } = useAuth();
  const admin = user?.role === "ADMINISTRATOR";
  const notify = useActionFeedback();
  const { confirm, dialog } = useConfirmation();
  const [data, setData] = useState(null), [error, setError] = useState(""), [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false), [progress, setProgress] = useState(0);
  const [amount, setAmount] = useState(""), [note, setNote] = useState(""), [file, setFile] = useState(null);
  const [reference, setReference] = useState(""), [date, setDate] = useState("");
  const [receipt, setReceipt] = useState("");
  const processing = useRef(false), input = useRef(null), statusCallback = useRef(onStatus);
  useEffect(() => { statusCallback.current = onStatus; }, [onStatus]);
  const endpoint = "/projects/" + project.id + "/reconciliation";
  const load = async () => {
    const response = await api.get(endpoint);
    if (!response.data || !Array.isArray(response.data.history)) throw new Error("Reconciliation response unavailable");
    setData(response.data); statusCallback.current?.(response.data.status);
  };
  useEffect(() => {
    let active = true; setLoading(true);
    api.get(endpoint).then(({data}) => {
      if (!data || !Array.isArray(data.history)) throw new Error("Reconciliation response unavailable");
      if(active){setData(data);statusCallback.current?.(data.status);}
    }).catch(e=>{if(active){setError(errorMessage(e,"Financial reconciliation could not load. Use Refresh below."));statusCallback.current?.("UNAVAILABLE");}})
      .finally(()=>{if(active)setLoading(false);});
    return()=>{active=false;};
  },[endpoint, project.version]);

  const declaration = data?.history.filter(r=>r.kind==="DECLARATION").at(-1);
  const canDeclare = !admin && ["NOT_SUBMITTED","CHANGES_REQUIRED"].includes(data?.status);
  const canReturn = !admin && data?.status==="RETURN_DUE" && !!data.returnDestination;
  const refresh = async () => {
    if(processing.current)return;
    setLoading(true);setError("");
    try { await load(); } catch(e){setError(errorMessage(e,"Could not refresh reconciliation."));}
    finally {setLoading(false);}
  };
  const saved = async title => {
    notify("The record was saved. The financial status below shows what happens next.",title);
    try {await load();await onChanged?.();}
    catch {setError("Saved, but the latest status could not load. Refresh before attempting another submission.");}
  };
  const submit = async e => {
    e.preventDefault();if(processing.current)return;
    setError("");
    if(!file || !file.size || file.size>=25*1024*1024 || !/\.(pdf|png|jpe?g)$/i.test(file.name)){
      setError("Choose a non-empty PDF, PNG or JPG supporting document under 25 MB.");return;
    }
    if(!amount.trim() || !Number.isFinite(Number(amount)) || Number(amount)<0 || !note.trim()){
      setError("Enter a valid amount and explanation.");return;
    }
    processing.current=true;
    try {
      const isReturn=canReturn;
      if(!canDeclare && !isReturn)return;
      const accepted=await confirm({title:isReturn?"Record a fund return":"Submit actual expenditure",
        message:isReturn?"Report a return of "+formatMoney(amount)+" for "+project.title+"? This records a transfer you have already made, not a new bank transfer. Receipt must be independently verified.":
          "Declare "+formatMoney(amount)+" as actual expenditure for "+project.title+"? An Administrator will review your statement and supporting documents.",
        action:isReturn?"Submit return for verification":"Submit declaration"});
      if(!accepted)return;
      setBusy(true);setProgress(0);
      const form=new FormData();form.append("amount",amount);form.append("note",note);form.append("file",file);
      if(isReturn){form.append("declarationId",declaration.id);form.append("reference",reference);form.append("transferredOn",date);form.append("destinationVersion",data.destinationVersion);}
      await api.post(endpoint+(isReturn?"/returns":"/declarations"),form,{
        headers:{"If-Match":String(isReturn?declaration.version:data.projectVersion)},
        onUploadProgress:e=>{if(e.total)setProgress(Math.round(e.loaded/e.total*100));}
      });
      setAmount("");setNote("");setFile(null);setReference("");setDate("");if(input.current)input.current.value="";
      await saved(isReturn?"Return submitted":"Expenditure submitted");
    }catch(e){setError(errorMessage(e,"Submission could not be confirmed. Refresh the history before submitting again."));}
    finally {processing.current=false;setBusy(false);}
  };
  const review = async (record,approve) => {
    if(processing.current)return;
    if(approve && record.kind==="RETURN" && !receipt.trim()){setError("Enter the unique transaction / receipt reference from the funder's bank or ledger records.");return;}
    processing.current=true;setError("");
    try {
      const decision=await confirm({title:approve?(record.kind==="RETURN"?"Confirm receipt by the funder":"Approve actual expenditure"):"Request a correction",
        message:approve?(record.kind==="RETURN"?"Confirm that "+formatMoney(record.amount)+" was actually received in the official funder account for "+project.title+". A provider's upload alone is not proof of receipt. Check the funder's bank/ledger records.":
          "Approve "+formatMoney(record.amount)+" of expenditure for "+project.title+" after reviewing the supporting statement? Approved figures cannot be silently changed."):
          "Reject this submission with an explanation. Its documents will stay in history, and the provider can submit a correction.",
        label:approve?"Review explanation":"Reason for rejection", maxLength:2000,
        action:approve?(record.kind==="RETURN"?"Verify receipt":"Approve expenditure"):"Reject submission"});
      if(!decision)return;setBusy(true);
      await api.post(endpoint+"/"+record.id+"/review",new URLSearchParams({approve:String(approve),reason:decision.value,receiptReference:receipt}),
        {headers:{"If-Match":String(record.version)}});
      setReceipt("");await saved(approve?"Financial review recorded":"Correction requested");
    }catch(e){setError(errorMessage(e,"Review could not be saved. Refresh before retrying."));}
    finally{processing.current=false;setBusy(false);}
  };
  const download = async record => {
    if(processing.current)return;processing.current=true;setBusy(true);setError("");
    try {const response=await api.get(endpoint+"/"+record.id+"/file",{responseType:"blob"});
      const url=URL.createObjectURL(response.data), a=document.createElement("a");a.href=url;a.download=record.fileName;
      document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),10000);
    }catch{setError("The supporting document could not be downloaded. Please retry or contact the system owner.");}
    finally{processing.current=false;setBusy(false);}
  };
  return <section className="workflow-details fund-reconciliation" aria-label="Expenditure and fund returns">
    {dialog}<h3>Expenditure and fund returns</h3>
    <p>Money never released stays with the funder. Money already paid but not spent must be accounted for separately.</p>
    {loading && <p role="status">Loading financial reconciliation…</p>}
    {error && <p className="project-error" role="alert">{error}</p>}
    <button type="button" className="project-action secondary" disabled={busy||loading} onClick={refresh}>Refresh reconciliation</button>
    {!loading && data && <>
      <p className="provider-project-notice">{descriptions[data.status] || "Refresh for the current financial status."}</p>
      <dl className="workflow-facts">
        <div><dt>Paid to provider (gross)</dt><dd>{formatMoney(data.releasedAmount)}</dd></div>
        <div><dt>Never released to provider</dt><dd>{formatMoney(data.undisbursedAmount)}</dd></div>
        <div><dt>Approved actual expenditure</dt><dd>{data.approvedExpenditure==null?"Awaiting declaration review":formatMoney(data.approvedExpenditure)}</dd></div>
        <div><dt>Returns verified by funder</dt><dd>{formatMoney(data.verifiedReturns)}</dd></div>
        <div><dt>Still due back to funder</dt><dd>{data.outstandingReturn==null?"Not yet determined":formatMoney(data.outstandingReturn)}</dd></div>
        <div><dt>Return awaiting verification</dt><dd>{formatMoney(data.pendingReturn)}</dd></div>
      </dl>
      {Number(data.outstandingReturn)>0 && <aside className="return-instructions">
        <h4>Official return instructions</h4>
        {data.returnDestination?<p className="return-destination">{data.returnDestination}</p>:<p>Not configured. Contact the system owner for verified funder instructions. Do not pay a personal account. Recording a return is disabled until this is configured.</p>}
        <p>The platform does not move money or verify bank transactions automatically. Administrators cannot edit the destination here. Include a project-specific reference when returning funds.</p>
      </aside>}
      {(canDeclare||canReturn) && <form className="closeout-form" onSubmit={submit}>
        <UnsavedChanges dirty={Boolean(amount||note||file||reference||date)&&!busy}/>
        <h4>{canReturn?"Record money returned":"Declare actual expenditure"}</h4>
        <label>{canReturn?"Amount returned (R)":"Actual expenditure (R)"}<input inputMode="decimal" type="number" step="0.01" min={canReturn?"0.01":"0"} max={canReturn?data.outstandingReturn:data.releasedAmount} required value={amount} onChange={e=>setAmount(e.target.value)} disabled={busy}/></label>
        <label>Explanation<textarea required maxLength={2000} value={note} onChange={e=>setNote(e.target.value)} disabled={busy}/></label>
        {canReturn && <>
          <label>Transfer reference<input required maxLength={100} value={reference} onChange={e=>setReference(e.target.value)} disabled={busy}/></label>
          <label>Transfer date<input type="date" required value={date} onChange={e=>setDate(e.target.value)} disabled={busy}/></label>
        </>}
        <label>{canReturn?"Proof of return":"Expenditure statement with supporting receipts"}<input ref={input} type="file" accept=".pdf,.png,.jpg,.jpeg" required disabled={busy} onChange={e=>setFile(e.target.files?.[0]||null)}/></label>
        <small>PDF, PNG or JPG, under 25 MB. You can include multiple receipts in one PDF. Existing phase evidence remains available.</small>
        {file && <p>{file.name} · {(file.size/1024).toFixed(1)} KB · {busy?"Uploading / saving":"Not submitted"}</p>}
        {busy && <div role="status"><progress max="100" value={progress}/>{progress<100?"Uploading… "+progress+"%":"Validating and saving…"}</div>}
        <button className="project-action" disabled={busy}>{busy?"Saving…":canReturn?"Record return":"Submit expenditure"}</button>
      </form>}
      <details className="reconciliation-history" open>
        <summary>Financial submission history ({data.history.length})</summary>
        {!data.history.length && <p>No expenditure declaration or fund return has been submitted.</p>}
        {data.history.map(record=><article key={record.id} className="reconciliation-record">
          <h4>{record.kind==="RETURN"?"Fund return":"Expenditure declaration"} · {formatMoney(record.amount)}</h4>
          <p><span className="status-pill">{record.status==="APPROVED"&&record.kind==="RETURN"?"Receipt verified":record.status.toLowerCase()}</span> · Submitted {formatDate(record.submittedAt)} · Provider ID {record.submittedBy}</p>
          <p>{record.note}</p>
          <p>{record.fileName} · {record.fileType} · {(record.fileSize/1024).toFixed(1)} KB</p>
          <button className="project-action secondary" disabled={busy} onClick={()=>download(record)}>Download supporting document</button>
          {record.kind==="RETURN" && <p>Transfer reference: {record.transferReference} · Date: {record.transferredOn}<br/>Destination recorded: {record.returnDestination}</p>}
          {record.reviewedAt && <p>Reviewed {formatDate(record.reviewedAt)} · Administrator ID {record.reviewedBy}<br/>Reason: {record.reviewNote}{record.receiptReference && <><br/>Verified funder receipt: {record.receiptReference}</>}</p>}
          {record.status==="PENDING" && admin && (record.kind==="RETURN"&&String(declaration?.reviewedBy)===String(user.id)?
            <p>You approved the expenditure. A different Administrator must verify or reject this return.</p>:
            <div className="reconciliation-review">
              {record.kind==="RETURN" && <label>Unique funder receipt reference<input value={receipt} maxLength={100} disabled={busy} onChange={e=>setReceipt(e.target.value)} placeholder="From the funder's bank or ledger"/></label>}
              <div><button className="project-action" disabled={busy} onClick={()=>review(record,true)}>{record.kind==="RETURN"?"Verify return received":"Approve expenditure"}</button>
              <button className="project-action secondary" disabled={busy} onClick={()=>review(record,false)}>Reject submission</button></div>
            </div>)}
        </article>)}
      </details>
    </>}
  </section>;
}

