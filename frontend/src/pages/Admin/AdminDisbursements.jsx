import ActionFeedback from "../../components/ActionFeedback";
import PaymentProof from "../../components/PaymentProof";
import useConfirmation from "../../components/useConfirmation";
import PageHeader from "../../components/PageHeader";
import RecordFilters from "../../components/RecordFilters";
import { useRecordFilters } from "../../components/useRecordFilters";
import AppShell from "../../components/AppShell";
import useVisibleLoading from "../../components/useVisibleLoading";
import { useEffect, useState } from "react";

import api from "../../services/api";
import LoadingSkeleton from "../../components/LoadingSkeleton";


function AdminDisbursements() {
  const {confirm,dialog}=useConfirmation();
  

  const [disbursements, setDisbursements] = useState([]);
  const [phases, setPhases] = useState([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState("");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    setError("");

    try {
      const [disbursementsResponse, phasesResponse] =
        await Promise.all([
          api.get("/disbursements"),
          api.get("/project-phases"),
        ]);

      setDisbursements(disbursementsResponse.data);
      setPhases(phasesResponse.data);
    } catch (error) {
      const message =
        error.response?.data?.message ||
        error.response?.data ||
        error.message ||
        "Unable to load disbursements.";

      setError(
        typeof message === "string"
          ? message
          : "Unable to load disbursements."
      );
    } finally {
      setLoading(false);
    }
  };

  const releaseDisbursement = async (id) => {
    const payment=disbursements.find(p=>p.id===id);
    const phase=phases.find(p=>p.id===payment.phaseId);
    const decision=await confirm({title:"Record payment",message:"You are about to record a payment of "+formatMoney(payment.amount)+" for "+(phase?.phaseName||"this phase")+" of "+(phase?.projectTitle||"this project")+". Confirm the payment has actually been made. This does not perform a bank transfer.",label:"Payment reference",maxLength:100,rows:1,action:"Confirm payment"});
    if(!decision)return;
    setActionLoading(`release-${id}`);
    setError("");
    setSuccess("");

    try {
      await api.post(`/disbursements/${id}/release`,new URLSearchParams({paymentReference:decision.value}));

      setSuccess("Payment recorded. You can now attach proof of payment.");

      await loadData();
    } catch (error) {
      const message =
        error.response?.data?.message ||
        error.response?.data ||
        error.message ||
        "Unable to release funds.";

      setError(
        typeof message === "string"
          ? message
          : "Unable to release funds."
      );
    } finally {
      setActionLoading("");
    }
  };


  const markProcessing=async item=>{
    if(!await confirm({title:"Mark payment as processing",message:"Mark "+formatMoney(item.amount)+" as being processed? It will not be shown as paid.",action:"Mark processing"}))return;
    setActionLoading("processing-"+item.id);setError("");
    try{await api.post("/disbursements/"+item.id+"/processing");await loadData();}
    catch(e){setError(e.response?.data?.message||e.response?.data||"Payment processing status could not be saved.");}
    finally{setActionLoading("");}
  };
  const getPhase = (phaseId) => {
    return phases.find(
      (phase) => phase.id === phaseId
    );
  };

  const formatMoney = (value) => {
    return new Intl.NumberFormat("en-ZA", {
      style: "currency",
      currency: "ZAR",
    }).format(Number(value || 0));
  };



  const releasedTotal = disbursements
    .filter((item) => item.status === "RELEASED")
    .reduce(
      (total, item) =>
        total + Number(item.amount || 0),
      0
    );

  const pendingCount = disbursements.filter(
    (item) => item.status === "PENDING"
  ).length;

  const recordFilter = useRecordFilters(disbursements);

  const showLoading = useVisibleLoading(loading);

  return (
    <AppShell>{dialog}
      

      <main className="admin-disbursements-main">
        <PageHeader title="Disbursements" description="Review and release approved phase funding."></PageHeader>
        {!loading && <RecordFilters filter={recordFilter} label="payments" />}

        {error && (
          <div className="admin-disbursements-error">
            {error}
          </div>
        )}

        {success && (
          <ActionFeedback message={success}/>
        )}

        {showLoading ? (
          <LoadingSkeleton cards={3} />
        ) : (
          <>
            <section className="admin-disbursement-stats">
              <article>
                <span>Total disbursements</span>
                <strong>{disbursements.length}</strong>
              </article>

              <article>
                <span>Pending release</span>
                <strong>{pendingCount}</strong>
              </article>

              <article>
                <span>Total released</span>
                <strong>
                  {formatMoney(releasedTotal)}
                </strong>
              </article>
            </section>

            <section className="admin-disbursements-card">
              {disbursements.length === 0 ? (
                <div className="admin-disbursements-empty">
                  No disbursements found.
                </div>
              ) : (
                <div className="admin-disbursements-table-wrapper" tabIndex={0} role="region" aria-label="Scrollable records">
                  <table className="admin-disbursements-table">
                    <thead>
                      <tr>
                        <th>ID</th>
                        <th>Phase</th>
                        <th>Amount</th>
                        <th>Status</th>
                        <th>Reference / proof</th><th>Action</th>
                      </tr>
                    </thead>

                    <tbody>
                      {recordFilter.filtered.map((item) => {
                        const phase = getPhase(item.phaseId);

                        return (
                          <tr key={item.id}>
                            <td>#{item.id}</td>

                            <td>
                              <strong>
                                {phase?.phaseName ||
                                  `Phase ${item.phaseId}`}
                              </strong>

                              <span>
                                Phase {phase?.phaseOrder || "-"}
                              </span>
                            </td>

                            <td>
                              {formatMoney(item.amount)}
                            </td>

                            <td>
                              <span
                                className={`admin-disbursement-status status-${item.status?.toLowerCase()}`}
                              >
                                {item.status === "RELEASED" ? "Recorded as paid" : item.status === "PROCESSING" ? "Processing" : "Approved — awaiting payment"}
                              </span>
                            </td>

                            <td>{item.status==="PENDING"&&<button className="project-action secondary" disabled={!!actionLoading} onClick={()=>markProcessing(item)}>Mark processing</button>}{item.paymentReference || "Not recorded"}<PaymentProof payment={item} onChanged={loadData}/></td>
                            <td>
                              {["PENDING","PROCESSING"].includes(item.status) ? (
                                <button
                                  onClick={() =>
                                    releaseDisbursement(item.id)
                                  }
                                  disabled={actionLoading !== ""}
                                >
                                  {actionLoading ===
                                  `release-${item.id}`
                                    ? "Releasing..."
                                    : "Release funds"}
                                </button>
                              ) : (
                                <span className="released-label">
                                  Released
                                </span>
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </section>
          </>
        )}
      </main>
    </AppShell>
  );
}

export default AdminDisbursements;
