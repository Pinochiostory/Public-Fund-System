import ActionFeedback from "../../components/ActionFeedback";
import { ProjectFunds, PhaseTransparency, ProjectActivity } from "../../components/WorkflowDetails";
import UnsavedChanges, { confirmDiscard } from "../../components/UnsavedChanges";
import ModalFrame from "../../components/ModalFrame";
import PageHeader from "../../components/PageHeader";
import RecordFilters from "../../components/RecordFilters";
import { useRecordFilters } from "../../components/useRecordFilters";
import AppShell from "../../components/AppShell";
import useVisibleLoading from "../../components/useVisibleLoading";
import { useEffect, useState } from "react";

import api from "../../services/api";
import LoadingSkeleton from "../../components/LoadingSkeleton";


function AdminProjects() {
  

  const [reconciliationStatus, setReconciliationStatus] = useState({});
  const [projects, setProjects] = useState([]);
  const [phases, setPhases] = useState([]);
  const [evidence,setEvidence]=useState([]);
  const [paymentReference,setPaymentReference]=useState("");
  const [disbursements, setDisbursements] =
    useState([]);

  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] =
    useState(null);

  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const [rejectingPhase, setRejectingPhase] =
    useState(null);

  const [rejectionReason, setRejectionReason] =
    useState("");

  const [confirmation, setConfirmation] =
    useState(null);

  useEffect(() => {
    loadData();
  }, []);

  const getErrorMessage = (
    err,
    fallback
  ) => {
    const data = err.response?.data;

    if (typeof data === "string") {
      return data;
    }

    if (
      data &&
      typeof data.message === "string"
    ) {
      return data.message;
    }

    return fallback;
  };

  const loadData = async () => {
    try {
      setLoading(true);
      setError("");

      const [
        projectsResponse,
        phasesResponse,
        disbursementsResponse, evidenceResponse,
      ] = await Promise.all([
        api.get("/projects"),
        api.get("/project-phases"),
        api.get("/disbursements"),api.get("/evidence"),
      ]);

      setEvidence(evidenceResponse.data||[]);
      const loadedProjects =
        projectsResponse.data || [];

      setProjects(
        [...loadedProjects].sort(
          (a, b) => {
            const dateDifference =
              new Date(
                b.createdAt || 0
              ) -
              new Date(
                a.createdAt || 0
              );

            if (
              dateDifference !== 0
            ) {
              return dateDifference;
            }

            return (
              Number(b.id) -
              Number(a.id)
            );
          }
        )
      );

      setPhases(
        phasesResponse.data || []
      );

      setDisbursements(
        disbursementsResponse.data ||
          []
      );
    } catch (err) {
      setError(
        getErrorMessage(
          err,
          "Unable to load project information."
        )
      );
    } finally {
      setLoading(false);
    }
  };



  const formatMoney = (value) => {
    return new Intl.NumberFormat(
      "en-ZA",
      {
        style: "currency",
        currency: "ZAR",
      }
    ).format(
      Number(value || 0)
    );
  };

  const formatStatus = (status) => {
    if (!status) {
      return "";
    }

    return status
      .replaceAll("_", " ")
      .toLowerCase()
      .replace(/\b\w/g, (letter) =>
        letter.toUpperCase()
      );
  };

  const getProjectPhases = (
    projectId
  ) => {
    return phases
      .filter(
        (phase) =>
          phase.projectId ===
          projectId
      )
      .slice()
      .sort(
        (a, b) =>
          Number(a.phaseOrder) -
          Number(b.phaseOrder)
      );
  };

  const getValidProjectPhases = (
    projectId
  ) => {
    return getProjectPhases(
      projectId
    ).filter(
      (phase) =>
        phase.status !== "REJECTED"
    );
  };

  const getAllocatedBudget = (
    projectId
  ) => {
    return getValidProjectPhases(
      projectId
    ).reduce(
      (total, phase) =>
        total +
        Number(
          phase.allocatedBudget || 0
        ),
      0
    );
  };

  const getRemainingBalance = (
    project
  ) => {
    return Math.max(
      Number(
        project.approvedBudget || 0
      ) -
        getAllocatedBudget(
          project.id
        ),
      0
    );
  };

  const getPhaseDisbursement = (
    phaseId
  ) => {
    return disbursements.find(
      (item) =>
        item.phaseId === phaseId
    );
  };

  const openConfirmation = ({
    type,
    title,
    message,
    item,
  }) => {
    setPaymentReference("");
    setConfirmation({
      type,
      title,
      message,
      item,
    });

    setError("");
    setSuccess("");
  };

  const closeConfirmation = (force=false) => {
    if(force!==true&&!confirmDiscard(Boolean(paymentReference)))return;
    setConfirmation(null);setPaymentReference("");
  };

  const approvePhase = async (
    phaseId
  ) => {
    try {
      setActionLoading(
        `approve-${phaseId}`
      );

      setError("");
      setSuccess("");

      await api.post(
        `/project-phases/${phaseId}/approve`
      );

      setSuccess(
        "Phase proposal approved."
      );

      closeConfirmation(true);
      await loadData();
    } catch (err) {
      setError(
        getErrorMessage(
          err,
          "Unable to approve phase."
        )
      );
    } finally {
      setActionLoading(null);
    }
  };

  const openRejectModal = (
    phase
  ) => {
    setRejectingPhase(phase);
    setRejectionReason("");
    setError("");
    setSuccess("");
  };

  const closeRejectModal = (force=false) => {
    if(force!==true&&!confirmDiscard(Boolean(rejectionReason)))return;
    setRejectingPhase(null);
    setRejectionReason("");
  };

  const rejectPhase = async (
    event
  ) => {
    event.preventDefault();

    if (!rejectingPhase) {
      return;
    }

    if (
      !rejectionReason.trim()
    ) {
      setError(
        "A rejection reason is required."
      );

      return;
    }

    try {
      setActionLoading(
        `reject-${rejectingPhase.id}`
      );

      setError("");
      setSuccess("");

      const body =
        new URLSearchParams();

      body.append(
        "rejectionReason",
        rejectionReason.trim()
      );

      await api.post(
        `/project-phases/${rejectingPhase.id}/reject`,
        body
      );

      setSuccess(
        "Phase proposal rejected."
      );

      closeRejectModal(true);
      await loadData();
    } catch (err) {
      setError(
        getErrorMessage(
          err,
          "Unable to reject phase."
        )
      );
    } finally {
      setActionLoading(null);
    }
  };

  const createDisbursement =
    async (phaseId) => {
      try {
        setActionLoading(
          `create-disbursement-${phaseId}`
        );

        setError("");
        setSuccess("");

        await api.post(
          `/disbursements/phase/${phaseId}`
        );

        setSuccess(
          "Disbursement created for the approved phase."
        );

        closeConfirmation(true);
        await loadData();
      } catch (err) {
        setError(
          getErrorMessage(
            err,
            "Unable to create disbursement."
          )
        );
      } finally {
        setActionLoading(null);
      }
    };

  const releaseDisbursement =
    async (disbursementId) => {
      if(!paymentReference.trim()){setError("A payment reference is required.");return;}
      try {
        setActionLoading(
          `release-${disbursementId}`
        );

        setError("");
        setSuccess("");

        await api.post(
          `/disbursements/${disbursementId}/release`, new URLSearchParams({paymentReference})
        );

        setSuccess(
          "Funds released successfully. The phase is now funded."
        );

        closeConfirmation(true);
        await loadData();
      } catch (err) {
        setError(
          getErrorMessage(
            err,
            "Unable to release funds."
          )
        );
      } finally {
        setActionLoading(null);
      }
    };

  const concludePhase = async (
    phaseId
  ) => {
    try {
      setActionLoading(
        `conclude-${phaseId}`
      );

      setError("");
      setSuccess("");

      await api.post(
        `/project-phases/${phaseId}/conclude`
      );

      setSuccess(
        "Phase formally completed."
      );

      closeConfirmation(true);
      await loadData();
    } catch (err) {
      setError(
        getErrorMessage(
          err,
          "Unable to conclude phase."
        )
      );
    } finally {
      setActionLoading(null);
    }
  };

  const completeProject = async (
    projectId
  ) => {
    try {
      setActionLoading(
        `complete-project-${projectId}`
      );

      setError("");
      setSuccess("");

      await api.post(
        `/projects/${projectId}/complete`
      );

      setSuccess(
        "Project formally completed."
      );

      closeConfirmation(true);
      await loadData();
    } catch (err) {
      setError(
        getErrorMessage(
          err,
          "Unable to complete project."
        )
      );
    } finally {
      setActionLoading(null);
    }
  };

  const performConfirmation =
    async () => {
      if (!confirmation) {
        return;
      }

      const {
        type,
        item,
      } = confirmation;

      if (
        type === "APPROVE_PHASE"
      ) {
        await approvePhase(
          item.id
        );
      }

      if (
        type ===
        "CREATE_DISBURSEMENT"
      ) {
        await createDisbursement(
          item.id
        );
      }

      if (
        type ===
        "RELEASE_DISBURSEMENT"
      ) {
        await releaseDisbursement(
          item.id
        );
      }

      if (
        type ===
        "CONCLUDE_PHASE"
      ) {
        await concludePhase(
          item.id
        );
      }

      if (
        type ===
        "COMPLETE_PROJECT"
      ) {
        await completeProject(
          item.id
        );
      }
    };

  const getConfirmationLoading =
    () => {
      if (!confirmation) {
        return false;
      }

      const {
        type,
        item,
      } = confirmation;

      if (
        type === "APPROVE_PHASE"
      ) {
        return (
          actionLoading ===
          `approve-${item.id}`
        );
      }

      if (
        type ===
        "CREATE_DISBURSEMENT"
      ) {
        return (
          actionLoading ===
          `create-disbursement-${item.id}`
        );
      }

      if (
        type ===
        "RELEASE_DISBURSEMENT"
      ) {
        return (
          actionLoading ===
          `release-${item.id}`
        );
      }

      if (
        type ===
        "CONCLUDE_PHASE"
      ) {
        return (
          actionLoading ===
          `conclude-${item.id}`
        );
      }

      if (
        type ===
        "COMPLETE_PROJECT"
      ) {
        return (
          actionLoading ===
          `complete-project-${item.id}`
        );
      }

      return false;
    };

  const renderPhaseActions = (
    phase
  ) => {
    const disbursement =
      getPhaseDisbursement(
        phase.id
      );

    if (
      phase.status ===
      "PENDING_APPROVAL"
    ) {
      return (
        <div className="admin-project-actions">
          <button
            className="admin-project-primary"
            onClick={() =>
              openConfirmation({
                type:
                  "APPROVE_PHASE",
                title:
                  "Approve phase",
                message:
                  `Approve "${phase.phaseName}" for ${formatMoney(
                    phase.allocatedBudget
                  )}?`,
                item: phase,
              })
            }
            disabled={
              actionLoading !==
              null
            }
          >
            Approve
          </button>

          <button
            className="admin-project-danger"
            onClick={() =>
              openRejectModal(
                phase
              )
            }
            disabled={
              actionLoading !==
              null
            }
          >
            Reject
          </button>
        </div>
      );
    }

    if (
      phase.status ===
        "APPROVED" &&
      !disbursement
    ) {
      return (
        <button
          className="admin-project-primary"
          onClick={() =>
            openConfirmation({
              type:
                "CREATE_DISBURSEMENT",
              title:
                "Create disbursement",
              message:
                `Create a ${formatMoney(
                  phase.allocatedBudget
                )} disbursement for "${phase.phaseName}"?`,
              item: phase,
            })
          }
          disabled={
            actionLoading !==
            null
          }
        >
          Create disbursement
        </button>
      );
    }

    if (
      phase.status ===
        "APPROVED" &&
      ["PENDING","PROCESSING"].includes(disbursement?.status)
    ) {
      return (
        <button
          className="admin-project-primary"
          onClick={() =>
            openConfirmation({
              type:
                "RELEASE_DISBURSEMENT",
              title:
                "Release funds",
              message:
                `Release ${formatMoney(
                  disbursement.amount
                )} for "${phase.phaseName}" of "${projects.find(p=>p.id===phase.projectId)?.title}"?`,
              item:
                disbursement,
            })
          }
          disabled={
            actionLoading !==
            null
          }
        >
          Release{" "}
          {formatMoney(
            disbursement.amount
          )}
        </button>
      );
    }

    if (
      phase.status ===
      "EVIDENCE_APPROVED"
    ) {
      return (
        <button
          className="admin-project-primary"
          onClick={() =>
            openConfirmation({
              type:
                "CONCLUDE_PHASE",
              title:
                "Conclude phase",
              message:
                `Evidence for "${phase.phaseName}" has been approved. Formally complete this phase?`,
              item: phase,
            })
          }
          disabled={
            actionLoading !==
            null
          }
        >
          Conclude phase
        </button>
      );
    }

    return null;
  };

  const pendingPhases =
    phases.filter(
      (phase) =>
        phase.status ===
        "PENDING_APPROVAL"
    ).length;

  const awaitingConclusion =
    phases.filter(
      (phase) =>
        phase.status ===
        "EVIDENCE_APPROVED"
    ).length;

  const pendingProjects =
    projects.filter(
      (project) =>
        project.status ===
        "PENDING_COMPLETION"
    ).length;

  const recordFilter = useRecordFilters(projects);

  const showLoading = useVisibleLoading(loading);

  return (
    <AppShell><UnsavedChanges dirty={Boolean(rejectionReason||paymentReference)}/>
      

      <main className="admin-project-main">
        <PageHeader title="Projects" description="Review phase proposals, release approved funding, conclude verified phases and formally complete projects."></PageHeader>
        {!loading && <RecordFilters filter={recordFilter} label="projects" />}

        {error && (
          <div className="admin-project-message error" role="alert">
            {error}
          </div>
        )}

        {success && (
          <ActionFeedback message={success}/>
        )}

        {!showLoading && (<section className="admin-project-stats">
          <article>
            <span>
              Total projects
            </span>

            <strong>
              {projects.length}
            </strong>
          </article>

          <article>
            <span>
              Phase approvals
            </span>

            <strong>
              {pendingPhases}
            </strong>
          </article>

          <article>
            <span>
              Awaiting conclusion
            </span>

            <strong>
              {awaitingConclusion}
            </strong>
          </article>

          <article>
            <span>
              Project completion
            </span>

            <strong>
              {pendingProjects}
            </strong>
          </article>
        </section>)}

        {showLoading ? (
          <LoadingSkeleton />
        ) : projects.length ===
          0 ? (
          <div className="admin-project-empty">
            No projects found.
          </div>
        ) : (
          <div className="admin-project-list">
            {recordFilter.filtered.map(
              (project) => {
                const projectPhases =
                  getProjectPhases(
                    project.id
                  );

                const validPhases =
                  getValidProjectPhases(
                    project.id
                  );

                const allocatedBudget =
                  getAllocatedBudget(
                    project.id
                  );

                const remainingBalance =
                  getRemainingBalance(
                    project
                  );

                return (
                  <section
                    className="admin-project-card"
                    key={
                      project.id
                    }
                  >
                    <div className="admin-project-card-header">
                      <div>
                        <span className="admin-project-id">
                          PROJECT
                        </span>

                        <h2>
                          {
                            project.title
                          }
                        </h2>

                        <p>
                          {
                            project.description
                          }
                        </p>
                      </div>

                      <span
                        className={`admin-project-status status-${project.status
                          .toLowerCase()
                          .replaceAll(
                            "_",
                            "-"
                          )}`}
                      >
                        {formatStatus(
                          project.status
                        )}
                      </span>
                    </div>

                    <div className="admin-project-details">
                      <div>
                        <span>
                          Approved budget
                        </span>

                        <strong>
                          {formatMoney(
                            project.approvedBudget
                          )}
                        </strong>
                      </div>

                      <div>
                        <span>
                          Allocated budget
                        </span>

                        <strong>
                          {formatMoney(
                            allocatedBudget
                          )}
                        </strong>
                      </div>

                      <div>
                        <span>
                          Unallocated budget
                        </span>

                        <strong>
                          {formatMoney(
                            remainingBalance
                          )}
                        </strong>
                      </div>

                      <div>
                        <span>
                          Timeline
                        </span>

                        <strong>
                          {
                            project.timeline
                          }
                        </strong>
                      </div>

                      <div>
                        <span>
                          Phases
                        </span>

                        <strong>
                          {
                            validPhases.length
                          }
                        </strong>
                      </div>
                    </div>

                    {project.status ===
                      "PENDING_COMPLETION" && (
                      <div className="admin-project-completion">
                        <div>
                          <strong>
                            Project completion requested
                          </strong>

                          <p>
                            The Service
                            Provider has requested final project completion.
                            {reconciliationStatus[project.id] !== "RECONCILED" && " Expenditure and fund returns must be reconciled below before completion."}
                          </p>
                        </div>

                        <button
                          className="admin-project-primary"
                          onClick={() =>
                            openConfirmation({
                              type:
                                "COMPLETE_PROJECT",
                              title:
                                "Complete project",
                              message:
                                `Formally complete "${project.title}"? Any undisbursed allocation will be frozen with the funder, not paid out. A different Administrator must review a remaining balance.`,
                              item:
                                project,
                            })
                          }
                          disabled={actionLoading !== null || reconciliationStatus[project.id] !== "RECONCILED"}
                        >
                          Complete project
                        </button>
                      </div>
                    )}

                    {project.status ===
                      "COMPLETED" && (
                      <div className="admin-project-completed">
                        Project formally
                        completed.
                      </div>
                    )}

                    <ProjectFunds project={project} phases={phases} payments={disbursements} onChanged={loadData} onReconciliation={status=>setReconciliationStatus(current=>({...current,[project.id]:status}))}/><ProjectActivity projectId={project.id} version={project.version}/>
                    <div className="admin-project-phase-section">
                      <div className="admin-project-section-heading">
                        <div>
                          <h3>
                            Project phases
                          </h3>

                          <p>
                            Review each
                            phase proposal
                            and manage its
                            progress.
                          </p>
                        </div>
                      </div>

                      {projectPhases.length ===
                      0 ? (
                        <div className="admin-project-phase-empty">
                          No phases have
                          been submitted.
                        </div>
                      ) : (
                        <div className="admin-project-phase-grid">
                          {projectPhases.map(
                            (phase) => {
                              const disbursement =
                                getPhaseDisbursement(
                                  phase.id
                                );

                              return (
                                <article
                                  className="admin-project-phase-card"
                                  key={
                                    phase.id
                                  }
                                >
                                  <div className="admin-project-phase-top">
                                    <span>
                                      Phase{" "}
                                      {
                                        phase.phaseOrder
                                      }
                                    </span>

                                    <span
                                      className={`admin-project-phase-status phase-${phase.status
                                        .toLowerCase()
                                        .replaceAll(
                                          "_",
                                          "-"
                                        )}`}
                                    >
                                      {formatStatus(
                                        phase.status
                                      )}
                                    </span>
                                  </div>

                                  <h4>
                                    {
                                      phase.phaseName
                                    }
                                  </h4>

                                  <p>
                                    {
                                      phase.description
                                    }
                                  </p>

                                  <div className="admin-project-phase-info">
                                    <div>
                                      <span>
                                        Requested
                                        amount
                                      </span>

                                      <strong>
                                        {formatMoney(
                                          phase.allocatedBudget
                                        )}
                                      </strong>
                                    </div>

                                    <div>
                                      <span>
                                        Disbursement
                                      </span>

                                      <strong>
                                        {disbursement
                                          ? formatStatus(
                                              disbursement.status
                                            )
                                          : "Not created"}
                                      </strong>
                                    </div>
                                  </div>

                                  {phase.status ===
                                    "REJECTED" &&
                                    phase.rejectionReason && (
                                      <div className="admin-project-rejection">
                                        <strong>
                                          Rejection
                                          reason
                                        </strong>

                                        <p>
                                          {
                                            phase.rejectionReason
                                          }
                                        </p>
                                      </div>
                                    )}

                                  {phase.status ===
                                    "IN_PROGRESS" && (
                                    <div className="admin-project-info-box">
                                      Service
                                      Provider is
                                      currently
                                      working on
                                      this phase.
                                    </div>
                                  )}

                                  {phase.status ===
                                    "EVIDENCE_APPROVED" && (
                                    <div className="admin-project-info-box">
                                      Evidence has
                                      been approved.
                                      Formal phase
                                      conclusion is
                                      required.
                                    </div>
                                  )}

                                  {phase.status ===
                                    "COMPLETED" && (
                                    <div className="admin-project-info-box">
                                      Phase formally
                                      completed.
                                    </div>
                                  )}

                                  <PhaseTransparency phase={phase} payment={getPhaseDisbursement(phase.id)} evidence={evidence} admin onChanged={loadData}/>
                                  {renderPhaseActions(
                                    phase
                                  )}
                                </article>
                              );
                            }
                          )}
                        </div>
                      )}
                    </div>
                  </section>
                );
              }
            )}
          </div>
        )}

        {rejectingPhase && (
          <ModalFrame className="admin-project-modal-backdrop" onClose={closeRejectModal} busy={actionLoading !== null} error={error}>
            <div className="admin-project-modal">
              <div className="admin-project-modal-header">
                <div>
                  <p>
                    PHASE REJECTION
                  </p>

                  <h2>
                    Reject phase
                  </h2>
                </div>

                <button
                  type="button"
                  onClick={
                    closeRejectModal
                  }
                >
                  Close
                </button>
              </div>

              <div className="admin-project-modal-phase">
                <strong>
                  {
                    rejectingPhase.phaseName
                  }
                </strong>

                <span>
                  Phase{" "}
                  {
                    rejectingPhase.phaseOrder
                  }
                </span>
              </div>

              <form
                onSubmit={
                  rejectPhase
                }
              >
                <label>
                  Rejection reason

                  <textarea
                    rows="5"
                    value={
                      rejectionReason
                    }
                    onChange={(
                      event
                    ) =>
                      setRejectionReason(
                        event.target
                          .value
                      )
                    }
                    placeholder="Explain why this phase proposal cannot be approved."
                    required
                  />
                </label>

                <div className="admin-project-modal-actions">
                  <button
                    type="button"
                    className="admin-project-secondary"
                    onClick={
                      closeRejectModal
                    }
                  >
                    Cancel
                  </button>

                  <button
                    type="submit"
                    className="admin-project-danger"
                    disabled={
                      actionLoading !==
                      null
                    }
                  >
                    {actionLoading ===
                    `reject-${rejectingPhase.id}`
                      ? "Rejecting..."
                      : "Reject phase"}
                  </button>
                </div>
              </form>
            </div>
          </ModalFrame>
        )}

        {confirmation && (
          <ModalFrame className="admin-project-modal-backdrop" onClose={closeConfirmation} busy={actionLoading !== null} error={error}>
            <div className="admin-project-modal">
              <div className="admin-project-modal-header">
                <div>
                  <p>
                    CONFIRM ACTION
                  </p>

                  <h2>
                    {
                      confirmation.title
                    }
                  </h2>
                </div>

                <button
                  type="button"
                  onClick={
                    closeConfirmation
                  }
                >
                  Close
                </button>
              </div>

              <p>
                {
                  confirmation.message
                }
              </p>
              {confirmation.type==="RELEASE_DISBURSEMENT"&&<label>Payment reference<input value={paymentReference} onChange={e=>setPaymentReference(e.target.value)} maxLength={100} required/><small>Confirm the payment has actually been made. This records a payment; it does not perform a bank transfer.</small></label>}

              <div className="admin-project-modal-actions">
                <button
                  type="button"
                  className="admin-project-secondary"
                  onClick={
                    closeConfirmation
                  }
                  disabled={
                    getConfirmationLoading()
                  }
                >
                  Cancel
                </button>

                <button
                  type="button"
                  className="admin-project-primary"
                  onClick={
                    performConfirmation
                  }
                  disabled={
                    getConfirmationLoading()
                  }
                >
                  {getConfirmationLoading()
                    ? "Processing..."
                    : "Confirm"}
                </button>
              </div>
            </div>
          </ModalFrame>
        )}
      </main>
    </AppShell>
  );
}

export default AdminProjects;
