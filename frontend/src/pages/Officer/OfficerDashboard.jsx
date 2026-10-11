import PageHeader from "../../components/PageHeader";
import PortfolioSnapshot from "../../components/PortfolioSnapshot";
import ActionBoard from "../../components/ActionBoard";
import DashboardMetrics from "../../components/DashboardMetrics";
import AppShell from "../../components/AppShell";
import useVisibleLoading from "../../components/useVisibleLoading";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import api from "../../services/api";
import LoadingSkeleton from "../../components/LoadingSkeleton";


function OfficerDashboard() {
  const navigate = useNavigate();
  

  const [evidence, setEvidence] = useState([]);
  const [phases, setPhases] = useState([]);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    loadDashboard();
  }, []);

  const loadDashboard = async () => {
    setLoading(true);
    setError("");

    try {
      const [
        evidenceResponse,
        phasesResponse,
      ] = await Promise.all([
        api.get("/evidence"),
        api.get("/project-phases"),
      ]);

      setEvidence(
        evidenceResponse.data
      );

      setPhases(
        phasesResponse.data
      );

    } catch (error) {
      const message =
        error.response?.data?.message ||
        error.response?.data ||
        error.message ||
        "Unable to load verification dashboard.";

      setError(
        typeof message === "string"
          ? message
          : "Unable to load verification dashboard."
      );
    } finally {
      setLoading(false);
    }
  };



  const pendingEvidence =
    evidence.filter(
      (item) =>
        item.status === "PENDING"
    ).length;

  const approvedEvidence =
    evidence.filter(
      (item) =>
        item.status === "APPROVED"
    ).length;

  const rejectedEvidence =
    evidence.filter(
      (item) =>
        item.status === "REJECTED"
    ).length;

  const inProgressPhases =
    phases.filter(
      (phase) =>
        phase.status === "IN_PROGRESS"
    ).length;

  const showLoading = useVisibleLoading(loading);

  return (
    <AppShell>
      

      <main className="officer-main">
        <PageHeader title="Verification overview" description="Monitor project implementation and verify submitted evidence."></PageHeader>

        {error && (
          <div className="officer-error">
            {error}
          </div>
        )}

        {showLoading ? (
          <LoadingSkeleton />
        ) : (
          <>
            <DashboardMetrics items={[
 {label:"Awaiting verification",value:pendingEvidence,note:"Evidence ready for your review",icon:"document",to:"/officer/evidence"},
 {label:"Approved evidence",value:approvedEvidence,note:"Verified submissions",icon:"check",to:"/officer/evidence"},
 {label:"Returned for correction",value:rejectedEvidence,note:"Submissions needing changes",icon:"audit",to:"/officer/evidence"},
 {label:"Work in progress",value:inProgressPhases,note:"Monitor active delivery",icon:"folder",to:"/officer/phases"}]} />
            <div className="overview-focus">
              <PortfolioSnapshot records={evidence} title="Evidence review pipeline" noun="Submissions" />
              <ActionBoard title="Your review queue" items={[
                {title:"Verify submitted evidence",description:"Open files and record your decision.",count:pendingEvidence,to:"/officer/evidence"},
                {title:"Monitor active phases",description:"Follow the work currently in progress.",count:inProgressPhases,to:"/officer/phases"}
              ]}/>
            </div>


            <section className="officer-card">
              <div className="officer-card-heading"><div><h2>Latest submissions</h2><p>Evidence awaiting a decision or recently reviewed.</p></div><button onClick={() => navigate("/officer/evidence")}>Review evidence</button></div>
              {evidence.length === 0 ? <div className="empty-state">No evidence submissions yet. Submitted files will appear here.</div> :
                <div className="table-wrapper" tabIndex={0} role="region" aria-label="Scrollable records"><table><thead><tr><th>Evidence</th><th>Project phase</th><th>Status</th><th>Uploaded</th></tr></thead><tbody>
                  {[...evidence].sort((a,b) => new Date(b.uploadedAt || 0) - new Date(a.uploadedAt || 0)).slice(0,5).map(item => <tr key={item.id}>
                    <td><strong>{item.fileName || "Evidence file"}</strong></td>
                    <td>{phases.find(phase=>phase.id === item.phaseId)?.phaseName || "Project phase"}</td>
                    <td><span className={"status-pill status-"+(item.status || "").toLowerCase()}>{item.status}</span></td>
                    <td>{item.uploadedAt ? new Date(item.uploadedAt).toLocaleDateString("en-ZA") : "Not available"}</td>
                  </tr>)}
                </tbody></table></div>}
              <div className="overview-footer"><span>{phases.length} project phases available for monitoring</span><button onClick={() => navigate("/officer/phases")}>View phases</button></div>
            </section>
          </>
        )}
      </main>
    </AppShell>
  );
}

export default OfficerDashboard;
