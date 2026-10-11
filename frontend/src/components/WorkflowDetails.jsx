import FundReconciliation from "./FundReconciliation";
import ProjectCloseout from "./ProjectCloseout";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import api from "../services/api";
import { formatMoney, formatDate, errorMessage } from "./projectData";
import PaymentProof from "./PaymentProof";

export function deadlineText(phase,now=new Date()) {
  if(!phase.deadline)return "No deadline recorded";
  const deadline=new Date(phase.deadline+"T23:59:59");
  if(phase.completedAt){
    const completed=new Date(phase.completedAt);
    if(completed>deadline)return "Completed late";
    return completed.toDateString()===deadline.toDateString()?"Completed on time":"Completed early";
  }
  const today=new Date(now.getFullYear(),now.getMonth(),now.getDate());
  const due=new Date(phase.deadline+"T00:00:00");
  const days=Math.round((due-today)/86400000);
  return days===0?"Deadline today":days===1?"Deadline tomorrow":days>0?days+" days remaining":"Overdue by "+Math.abs(days)+" day"+(days===-1?"":"s");
}
export function durationText(phase) {
  if(!phase.startedAt)return "Not started";
  if(!phase.completedAt)return "In progress — duration is finalised on formal completion";
  const minutes=Math.floor((new Date(phase.completedAt)-new Date(phase.startedAt))/60000);
  if(minutes<0)return "Dates require Administrator review";
  return Math.floor(minutes/1440)+" days "+Math.floor(minutes%1440/60)+" hours "+minutes%60+" minutes";
}
export function phaseExplanation(phase,evidence=[],admin=false) {
  const records=evidence.filter(e=>String(e.phaseId)===String(phase.id));
  const pending=records.some(e=>e.status==="PENDING");
  const latest=[...records].sort((a,b)=>new Date(b.uploadedAt)-new Date(a.uploadedAt))[0];
  const messages={
    PENDING_APPROVAL:admin?"Action required: review this phase proposal.":"Waiting for Administrator approval of this phase.",
    APPROVED:admin?"Action required: prepare and record payment when the previous phase is completed.":"Waiting for the Administrator to release phase funding.",
    FUNDED:admin?"Waiting for the Service Provider to start this phase.":"Action required: start this funded phase when the previous phase is completed.",
    IN_PROGRESS:pending?(admin?"Action required: review the submitted evidence.":"Evidence under review — waiting for a reviewer."):(latest?.status==="REJECTED"?"Evidence rejected — correct the problem and resubmit.":admin?"Waiting for the Service Provider to submit evidence.":"Evidence pending — upload evidence when your phase work is ready for review."),
    EVIDENCE_APPROVED:admin?"Action required: formally conclude this verified phase.":"Evidence approved — waiting for the Administrator to complete this phase.",
    COMPLETED:"This phase has been formally completed.",
    REJECTED:"This phase proposal was rejected. Review the reason before submitting a corrected proposal.",
  };
  return messages[phase.status]||"Refresh for the latest workflow information.";
}
export function PhaseTransparency({phase,payment,evidence=[],admin=false,onChanged}) {
  const records=evidence.filter(e=>String(e.phaseId)===String(phase.id));
  const latest=[...records].sort((a,b)=>new Date(b.uploadedAt)-new Date(a.uploadedAt))[0];
  const approved=!["PENDING_APPROVAL","REJECTED"].includes(phase.status);
  return <div className="workflow-details">
    <p className="provider-project-notice">{phaseExplanation(phase,evidence,admin)}</p>
    <dl className="workflow-facts">
      <div><dt>Approved amount</dt><dd>{approved?formatMoney(phase.allocatedBudget):"Not approved"}</dd></div>
      <div><dt>Paid amount</dt><dd>{formatMoney(payment?.status==="RELEASED"?payment.amount:0)}</dd></div>
      <div><dt>Started</dt><dd>{phase.startedAt?formatDate(phase.startedAt):"Not started"}</dd></div>
      <div><dt>Deadline</dt><dd>{phase.deadline||"Not recorded"} · {deadlineText(phase)}</dd></div>
      <div><dt>Completed</dt><dd>{phase.completedAt?formatDate(phase.completedAt):"Not completed"}</dd></div>
      <div><dt>Completion duration</dt><dd>{durationText(phase)}</dd></div>
      <div><dt>Evidence</dt><dd>{latest?.status?.replaceAll("_"," ")||"No submission recorded"}</dd></div>
      <div><dt>Payment</dt><dd>{payment?.status==="RELEASED"?"Recorded as paid":payment?.status==="PROCESSING"?"Processing":payment?"Approved — awaiting payment":"Not prepared"}</dd></div>
    </dl>
    {latest?.status==="REJECTED"&&<p className="project-error">Reason: {latest.verificationComment}</p>}
    <Link className="project-action secondary" to={admin?"/admin/evidence":"/provider/evidence/"+phase.projectId+"/phases/"+phase.id}>View evidence history</Link>
    {payment&&<PaymentProof payment={payment} onChanged={onChanged}/>}
  </div>;
}
export function ProjectFunds({project,phases=[],payments=[],onChanged,onReconciliation}) {
  const list=phases.filter(p=>String(p.projectId)===String(project.id)&&p.status!=="REJECTED");
  const released=payments.filter(p=>String(p.projectId)===String(project.id)&&p.status==="RELEASED").reduce((sum,p)=>sum+Number(p.amount),0);
  const remaining=Number(project.approvedBudget)-released;
  const completed=list.filter(p=>p.status==="COMPLETED").length;
  const starts=list.map(p=>p.startedAt).filter(Boolean).sort();
  const next=list.find(p=>p.status==="APPROVED");
  const shortfall=next?Number(next.allocatedBudget)-remaining:0;
  return <section className="workflow-details" aria-label="Project funding and dates">
    <dl className="workflow-facts">
      <div><dt>Total funds released</dt><dd>{formatMoney(released)}</dd></div>
      <div><dt>Remaining project funds</dt><dd>{formatMoney(remaining)}</dd></div>
      <div><dt>Project start</dt><dd>{starts.length?formatDate(starts[0]):"Work not started"}</dd></div>
      <div><dt>Expected completion</dt><dd>{project.expectedCompletionDate||"Not recorded"}</dd></div>
      <div><dt>Completed phases</dt><dd>{completed} of {list.length}</dd></div>
      <div><dt>Outstanding phases</dt><dd>{list.length-completed}</dd></div>
    </dl>
    <ProjectCloseout project={project} onChanged={onChanged}/>
    {["PENDING_COMPLETION","COMPLETED"].includes(project.status) && <FundReconciliation project={project} onChanged={onChanged} onStatus={onReconciliation}/>}
    {(shortfall>0||remaining<0)&&<p className="project-error">Insufficient project funds. {shortfall>0?"The next approved phase requires an additional "+formatMoney(shortfall)+".":"The recorded balance needs Administrator review."} No additional payment may exceed the available balance.</p>}
  </section>;
}
export function ProjectActivity({projectId,version}) {
  const [open,setOpen]=useState(false),[items,setItems]=useState([]),[error,setError]=useState(""),[loading,setLoading]=useState(false);
  useEffect(()=>{
    if(!open)return;let active=true;setLoading(true);setError("");
    api.get("/projects/"+projectId+"/activity").then(r=>{if(active)setItems(r.data);})
      .catch(e=>{if(active)setError(errorMessage(e,"Could not load project activity."));}).finally(()=>{if(active)setLoading(false);});
    return()=>{active=false;};
  },[open,projectId,version]);
  return <details className="workflow-details" onToggle={e=>setOpen(e.currentTarget.open)}><summary>Project activity timeline</summary>
    {loading&&<p role="status">Loading activity…</p>}{error&&<p role="alert">{error}</p>}
    {!loading&&!error&&<ol className="workflow-timeline">{items.map(item=><li key={item.id}><strong>{item.action.replaceAll("_"," ")}</strong><p>{item.description}</p><small>{formatDate(item.createdAt)} · {item.userEmail} ({item.userRole.replaceAll("_"," ")})</small></li>)}</ol>}
    {!loading&&!error&&!items.length&&<p>No linked activity has been recorded yet. Older audit entries remain in the Administrator audit trail.</p>}
  </details>;
}
