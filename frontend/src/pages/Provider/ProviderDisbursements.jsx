import { Link, useParams } from "react-router-dom";
import PaymentProof from "../../components/PaymentProof";
import PageHeader from "../../components/PageHeader";
import AppShell from "../../components/AppShell";
import LoadingSkeleton from "../../components/LoadingSkeleton";
import useVisibleLoading from "../../components/useVisibleLoading";
import useProviderPortfolio from "../../components/useProviderPortfolio";
import ProjectDirectory from "../../components/ProjectDirectory";
import RecordFilters from "../../components/RecordFilters";
import { useRecordFilters } from "../../components/useRecordFilters";
import { sameId, projectName, projectPayments, formatMoney, formatDate, formatStatus } from "../../components/projectData";

export default function ProviderDisbursements() {
  const { projectId } = useParams();
  const {projects,phases,records,loading,error,refresh} = useProviderPortfolio("payments");
  const showLoading = useVisibleLoading(loading);
  const project = projects.find(item => sameId(item.id,projectId));
  const payments = projectId ? projectPayments(projectId,phases,records) : [];
  const namedPayments = payments.map(item => ({...item,phaseName:phases.find(phase => sameId(phase.id,item.phaseId))?.phaseName || "Project phase"}));
  const filter = useRecordFilters(namedPayments);
  const total = status => formatMoney(payments.filter(item => item.status === status).reduce((sum,item)=>sum+Number(item.amount || 0),0));
  return <AppShell><main className="project-page">
    <PageHeader title={projectId ? projectName(project) : "Payments"} description={projectId ? "Funding and payment information for this project." : "Choose a project to view its payments."}>{projectId && <Link className="project-action secondary" to="/provider/disbursements">All projects</Link>}</PageHeader>
    {error && <div className="project-error" role="alert">{error}</div>}
    {showLoading ? <LoadingSkeleton/> : !error && (!projectId ?
      <ProjectDirectory projects={projects} basePath="/provider/disbursements" action="View payments" summary={project => {const list=projectPayments(project.id,phases,records);return list.length+" payment"+(list.length===1?"":"s");}}/> :
      !project ? <div className="project-empty">This project is unavailable or does not belong to your account.</div> :
      <>
        <section className="payment-summary"><article><span>Approved budget</span><strong>{formatMoney(project.approvedBudget)}</strong></article><article><span>Funds released</span><strong>{total("RELEASED")}</strong></article><article><span>Pending release</span><strong>{total("PENDING")}</strong></article><article><span>Remaining project funds</span><strong>{formatMoney(Number(project.approvedBudget)-payments.filter(p=>p.status==="RELEASED").reduce((sum,p)=>sum+Number(p.amount),0))}</strong></article></section>
        <RecordFilters filter={filter} label="payments"/>
        {!payments.length && <div className="project-empty">No payments have been prepared for this project yet.</div>}
        <div className="project-directory-grid">{filter.filtered.slice().sort((a,b)=>b.id-a.id).map(item=><article className="payment-detail-card" key={item.id}>
          <div className="project-directory-heading"><h2>{item.phaseName}</h2><span className="status-pill">{item.status==="RELEASED" ? "Recorded as paid" : formatStatus(item.status)}</span></div>
          <strong className="payment-amount">{formatMoney(item.amount)}</strong>
          <dl><div><dt>Payment reference</dt><dd>{item.paymentReference || "Not recorded"}</dd></div><div><dt>Released</dt><dd>{formatDate(item.releasedAt)}</dd></div></dl>
          <p>{item.status==="RELEASED" ? "The Administrator has recorded this payment as paid." : item.status==="PROCESSING" ? "Your approved payment is being processed; it has not been recorded as paid." : "Approved — waiting for the Administrator to record payment."}</p><PaymentProof payment={item} onChanged={refresh}/>
        </article>)}</div>
      </>)}
  </main></AppShell>;
}
