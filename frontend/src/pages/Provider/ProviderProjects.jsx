import ActionFeedback from "../../components/ActionFeedback";
import { phaseStartBlockedReason } from "../../components/phaseEligibility";
import { ProjectFunds, PhaseTransparency, ProjectActivity } from "../../components/WorkflowDetails";
import UnsavedChanges, { confirmDiscard } from "../../components/UnsavedChanges";
import ModalFrame from "../../components/ModalFrame";
import PageHeader from "../../components/PageHeader";
import RecordFilters from "../../components/RecordFilters";
import { useRecordFilters } from "../../components/useRecordFilters";
import AppShell from "../../components/AppShell";
import useVisibleLoading from "../../components/useVisibleLoading";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import api from "../../services/api";
import LoadingSkeleton from "../../components/LoadingSkeleton";


function ProviderProjects() {
  const navigate = useNavigate();
  

  const [projects, setProjects] = useState([]);
  const [phases, setPhases] = useState([]);
  const [payments,setPayments]=useState([]);
  const [evidence,setEvidence]=useState([]);

  const [selectedProjectId, setSelectedProjectId] =
    useState(null);

  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] =
    useState(null);

  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const [showPhaseForm, setShowPhaseForm] =
    useState(false);

  const [completionProject, setCompletionProject] =
    useState(null);

  const [phaseForm, setPhaseForm] = useState({
    deadline: "",
    phaseName: "",
    description: "",
    allocatedBudget: "",
    phaseOrder: "",
  });

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
        phasesResponse, paymentsResponse, evidenceResponse,
      ] = await Promise.all([
        api.get("/projects/my"),
        api.get("/project-phases/my"),api.get("/disbursements/my"),api.get("/evidence/my"),
      ]);

      setPayments(paymentsResponse.data||[]);setEvidence(evidenceResponse.data||[]);
      const loadedProjects =
        projectsResponse.data || [];

      const sortedLoadedProjects = [
        ...loadedProjects,
      ].sort((a, b) => {
        const dateDifference =
          new Date(
            b.createdAt || 0
          ) -
          new Date(
            a.createdAt || 0
          );

        if (dateDifference !== 0) {
          return dateDifference;
        }

        return Number(b.id) - Number(a.id);
      });

      setProjects(
        sortedLoadedProjects
      );

      setPhases(
        phasesResponse.data || []
      );

      if (
        sortedLoadedProjects.length > 0 &&
        selectedProjectId === null
      ) {
        setSelectedProjectId(
          sortedLoadedProjects[0].id
        );
      }
    } catch (err) {
      setError(
        getErrorMessage(
          err,
          "Unable to load your projects."
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
          phase.projectId === projectId
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
    const approvedBudget =
      Number(
        project.approvedBudget || 0
      );

    const allocatedBudget =
      getAllocatedBudget(
        project.id
      );

    return Math.max(
      approvedBudget -
        allocatedBudget,
      0
    );
  };

  /*
   * LOCKS THE PROJECT WHILE A PHASE IS
   * FUNDED, IN PROGRESS OR WAITING FOR
   * ADMIN CONCLUSION.
   */
  const projectHasLockedPhase = (
    projectId
  ) => {
    return phases.some(
      (phase) =>
        phase.projectId === projectId &&
        (
          phase.status === "FUNDED" ||
          phase.status === "IN_PROGRESS" ||
          phase.status === "EVIDENCE_APPROVED"
        )
    );
  };

  /*
   * USED TO CHECK IF ANOTHER PHASE
   * ALREADY HOLDS THE PROJECT LOCK.
   */
  const startBlockedReason = phase => phaseStartBlockedReason(
    phase, projects.find(project => String(project.id) === String(phase.projectId)), phases
  );

  const getNextPhaseOrder = (
    projectId
  ) => {
    const validPhases =
      getValidProjectPhases(
        projectId
      );

    if (validPhases.length === 0) {
      return 1;
    }

    return (
      Math.max(
        ...validPhases.map(
          (phase) =>
            Number(
              phase.phaseOrder
            )
        )
      ) + 1
    );
  };

  const openPhaseForm = (
    projectId
  ) => {
    if (
      projectHasLockedPhase(
        projectId
      )
    ) {
      setError(
        "You cannot propose another phase while the current phase is funded, in progress, or awaiting Administrator conclusion."
      );

      return;
    }

    setSelectedProjectId(
      projectId
    );

    setPhaseForm({
      phaseName: "",
      description: "",
      allocatedBudget: "",
      phaseOrder:
        getNextPhaseOrder(
          projectId
        ),
    });

    setShowPhaseForm(true);
    setError("");
    setSuccess("");
  };

  const closePhaseForm = (force=false) => {
    if(force!==true&&!confirmDiscard(Boolean(phaseForm.phaseName||phaseForm.description||phaseForm.allocatedBudget||phaseForm.deadline)))return;
    setShowPhaseForm(false);

    setPhaseForm({
      phaseName: "",
      description: "",
      allocatedBudget: "",
      phaseOrder: "",
    });
  };

  const handlePhaseChange = (
    event
  ) => {
    const {
      name,
      value,
    } = event.target;

    setPhaseForm(
      (current) => ({
        ...current,
        [name]: value,
      })
    );
  };

  const submitPhase = async (
    event
  ) => {
    event.preventDefault();

    if (!selectedProjectId) {
      return;
    }

    if (
      projectHasLockedPhase(
        selectedProjectId
      )
    ) {
      setError(
        "You cannot propose another phase while the current phase is still active."
      );

      closePhaseForm();
      return;
    }

    const project =
      projects.find(
        (item) =>
          item.id ===
          selectedProjectId
      );

    if (!project) {
      setError(
        "Project could not be found."
      );
      return;
    }

    const requestedAmount =
      Number(
        phaseForm.allocatedBudget
      );

    const remainingBalance =
      getRemainingBalance(
        project
      );

    if (
      requestedAmount >
      remainingBalance
    ) {
      setError(
        `Requested amount exceeds the remaining project balance of ${formatMoney(
          remainingBalance
        )}.`
      );

      return;
    }

    try {
      setActionLoading(
        "create-phase"
      );

      setError("");
      setSuccess("");

      const body =
        new URLSearchParams();

      body.append(
        "phaseName",
        phaseForm.phaseName
      );

      body.append(
        "description",
        phaseForm.description
      );

      body.append(
        "allocatedBudget",
        phaseForm.allocatedBudget
      );

      if(phaseForm.deadline)body.append("deadline",phaseForm.deadline);
      body.append(
        "phaseOrder",
        phaseForm.phaseOrder
      );

      await api.post(
        `/project-phases/project/${selectedProjectId}`,
        body
      );

      setSuccess(
        "Phase proposal submitted to the Administrator for review."
      );

      closePhaseForm(true);
      await loadData();
    } catch (err) {
      setError(
        getErrorMessage(
          err,
          "Unable to submit phase proposal."
        )
      );
    } finally {
      setActionLoading(
        null
      );
    }
  };

  const startPhase = async (
    phase
  ) => {
    const reason = startBlockedReason(phase);
    if (reason) { setError(reason); return; }

    try {
      setActionLoading(
        `start-${phase.id}`
      );

      setError("");
      setSuccess("");

      await api.post(
        `/project-phases/${phase.id}/start`
      );

      setSuccess(
        "Phase started successfully."
      );

      await loadData();
    } catch (err) {
      setError(
        getErrorMessage(
          err,
          "Unable to start phase."
        )
      );
    } finally {
      setActionLoading(
        null
      );
    }
  };

  const openCompletionModal = (
    project
  ) => {
    setCompletionProject(
      project
    );

    setError("");
    setSuccess("");
  };

  const closeCompletionModal = () => {
    setCompletionProject(
      null
    );
  };

  const requestProjectCompletion =
    async () => {
      if (!completionProject) {
        return;
      }

      try {
        setActionLoading(
          `complete-${completionProject.id}`
        );

        setError("");
        setSuccess("");

        await api.post(
          `/projects/${completionProject.id}/request-completion`
        );

        setSuccess(
          "Project completion request submitted to the Administrator."
        );

        closeCompletionModal();
        await loadData();
      } catch (err) {
        setError(
          getErrorMessage(
            err,
            "Unable to request project completion."
          )
        );
      } finally {
        setActionLoading(
          null
        );
      }
    };

  const canRequestCompletion = (
    project
  ) => {
    if (
      project.status !==
      "ACTIVE"
    ) {
      return false;
    }

    const projectPhases =
      getValidProjectPhases(
        project.id
      );

    if (
      projectPhases.length === 0
    ) {
      return false;
    }

    return projectPhases.every(
      (phase) =>
        phase.status ===
        "COMPLETED"
    );
  };

  const renderPhaseAction = (
    phase
  ) => {
    if (
      phase.status === "FUNDED"
    ) {
      const blocked = startBlockedReason(phase);

      if (blocked) {
        return <p className="provider-project-notice">{blocked}</p>;
      }

      return (
        <button
          className="provider-project-primary"
          onClick={() =>
            startPhase(
              phase
            )
          }
          disabled={
            actionLoading !==
            null
          }
        >
          {actionLoading ===
          `start-${phase.id}`
            ? "Starting..."
            : "Start phase"}
        </button>
      );
    }

    if (
      phase.status ===
      "IN_PROGRESS"
    ) {
      return (
        <button
          className="provider-project-secondary"
          onClick={() =>
            navigate(
              "/provider/evidence"
            )
          }
        >
          Submit evidence
        </button>
      );
    }

    return null;
  };

  const recordFilter = useRecordFilters(projects);

  const showLoading = useVisibleLoading(loading);

  return (
    <AppShell><UnsavedChanges dirty={showPhaseForm && Boolean(phaseForm.phaseName||phaseForm.description||phaseForm.allocatedBudget||phaseForm.deadline)}/>
      

      <main className="provider-project-main">
        <PageHeader title="Projects" description="Manage approved projects, submit phase proposals and track project progress."></PageHeader>
        {!loading && <RecordFilters filter={recordFilter} label="projects" />}

        {error && (
          <div className="provider-project-message error">
            {error}
          </div>
        )}

        {success && (
          <ActionFeedback message={success}/>
        )}

        {showLoading ? (
          <LoadingSkeleton />
        ) : projects.length ===
          0 ? (
          <div className="provider-project-empty">
            <h2>
              No projects yet
            </h2>

            <p>
              A project will
              appear here after
              an approved funding
              application is
              converted into a
              project.
            </p>
          </div>
        ) : (
          <div className="provider-project-list">
            {recordFilter.filtered.map(
              (project) => {
                const projectPhases =
                  getProjectPhases(
                    project.id
                  );

                const validPhases =
                  projectPhases.filter(
                    (phase) =>
                      phase.status !==
                      "REJECTED"
                  );

                const allocatedBudget =
                  getAllocatedBudget(
                    project.id
                  );

                const remainingBalance =
                  getRemainingBalance(
                    project
                  );

                const hasLockedPhase =
                  projectHasLockedPhase(
                    project.id
                  );

                return (
                  <section
                    className="provider-project-card"
                    key={project.id}
                  >
                    <div className="provider-project-card-header">
                      <div>
                        <span className="provider-project-id">
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
                        className={`provider-project-status status-${project.status
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

                    <div className="provider-project-details">
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
                          Total phases
                        </span>

                        <strong>
                          {
                            validPhases.length
                          }
                        </strong>
                      </div>
                    </div>

                    <ProjectFunds project={project} phases={phases} payments={payments}/><ProjectActivity projectId={project.id} version={project.version}/>
                    {project.status ===
                      "ACTIVE" && (
                      <div className="provider-project-actions">
                        {!hasLockedPhase &&
                          remainingBalance >
                            0 && (
                            <button
                              className="provider-project-primary"
                              onClick={() =>
                                openPhaseForm(
                                  project.id
                                )
                              }
                            >
                              Propose new phase
                            </button>
                          )}

                        {canRequestCompletion(
                          project
                        ) && (
                          <button
                            className="provider-project-secondary"
                            onClick={() =>
                              openCompletionModal(
                                project
                              )
                            }
                            disabled={
                              actionLoading !==
                              null
                            }
                          >
                            Request project completion
                          </button>
                        )}
                      </div>
                    )}

                    {hasLockedPhase &&
                      project.status ===
                        "ACTIVE" && (
                        <div className="provider-project-notice">
                          This project currently
                          has an active phase.
                          No additional phase
                          may be proposed or
                          started until the
                          current phase is
                          formally completed.
                        </div>
                      )}

                    {remainingBalance ===
                      0 &&
                      project.status ===
                        "ACTIVE" && (
                        <div className="provider-project-notice">
                          The full approved
                          project budget has
                          been allocated.
                        </div>
                      )}

                    {project.status ===
                      "PENDING_COMPLETION" && (
                      <div className="provider-project-notice">
                        Project completion has been requested. Complete any outstanding expenditure and fund-return reconciliation above; an Administrator can then confirm final closure.
                      </div>
                    )}

                    {project.status ===
                      "COMPLETED" && (
                      <div className="provider-project-notice">
                        This project has been
                        formally completed.
                      </div>
                    )}

                    <div className="provider-project-phase-section">
                      <div className="provider-project-section-title">
                        <div>
                          <h3>
                            Project phases
                          </h3>

                          <p>
                            Phase proposals and
                            implementation progress.
                          </p>
                        </div>
                      </div>

                      {projectPhases.length ===
                      0 ? (
                        <div className="provider-project-phase-empty">
                          No phase proposals
                          submitted yet.
                        </div>
                      ) : (
                        <div className="provider-project-phase-grid">
                          {projectPhases.map(
                            (phase) => (
                              <article
                                className="provider-project-phase-card"
                                key={
                                  phase.id
                                }
                              >
                                <div className="provider-project-phase-top">
                                  <span>
                                    Phase{" "}
                                    {
                                      phase.phaseOrder
                                    }
                                  </span>

                                  <span
                                    className={`provider-project-phase-status phase-${phase.status
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

                                <div className="provider-project-phase-budget">
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

                                {phase.status ===
                                  "REJECTED" &&
                                  phase.rejectionReason && (
                                    <div className="provider-project-rejection">
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
                                  "EVIDENCE_APPROVED" && (
                                  <div className="provider-project-phase-message">
                                    Evidence
                                    approved.
                                    Awaiting
                                    Administrator
                                    conclusion.
                                  </div>
                                )}

                                <PhaseTransparency phase={phase} payment={payments.find(p=>p.phaseId===phase.id)} evidence={evidence} onChanged={loadData}/>
                                {renderPhaseAction(
                                  phase
                                )}
                              </article>
                            )
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

        {showPhaseForm && (
          <ModalFrame className="provider-project-modal-backdrop" onClose={closePhaseForm} busy={actionLoading !== null} error={error}>
            <div className="provider-project-modal">
              <div className="provider-project-modal-header">
                <div>
                  <p>
                    PHASE PROPOSAL
                  </p>

                  <h2>
                    Create project phase
                  </h2>
                </div>

                <button
                  type="button"
                  onClick={
                    closePhaseForm
                  }
                >
                  Close
                </button>
              </div>

              {projects.find(
                (project) =>
                  project.id ===
                  selectedProjectId
              ) && (
                <div className="provider-project-phase-message">
                  Remaining project
                  balance:{" "}
                  <strong>
                    {formatMoney(
                      getRemainingBalance(
                        projects.find(
                          (project) =>
                            project.id ===
                            selectedProjectId
                        )
                      )
                    )}
                  </strong>
                </div>
              )}

              <form
                onSubmit={
                  submitPhase
                }
              >
                <label>
                  Phase name

                  <input
                    type="text"
                    name="phaseName"
                    value={
                      phaseForm.phaseName
                    }
                    onChange={
                      handlePhaseChange
                    }
                    required
                  />
                </label>

                <label>
                  Phase description

                  <textarea
                    name="description"
                    value={
                      phaseForm.description
                    }
                    onChange={
                      handlePhaseChange
                    }
                    rows="5"
                    required
                  />
                </label>

                <label>Phase deadline<input type="date" name="deadline" value={phaseForm.deadline||""} onChange={handlePhaseChange}/></label>
                <div className="provider-project-form-row">
                  <label>
                    Requested amount

                    <input
                      type="number"
                      name="allocatedBudget"
                      value={
                        phaseForm.allocatedBudget
                      }
                      onChange={
                        handlePhaseChange
                      }
                      min="0.01"
                      step="0.01"
                      required
                    />
                  </label>

                  <label>
                    Phase order

                    <input
                      type="number"
                      name="phaseOrder"
                      value={
                        phaseForm.phaseOrder
                      }
                      onChange={
                        handlePhaseChange
                      }
                      min="1"
                      required
                    />
                  </label>
                </div>

                <div className="provider-project-modal-actions">
                  <button
                    type="button"
                    className="provider-project-secondary"
                    onClick={
                      closePhaseForm
                    }
                  >
                    Cancel
                  </button>

                  <button
                    type="submit"
                    className="provider-project-primary"
                    disabled={
                      actionLoading !==
                      null
                    }
                  >
                    {actionLoading ===
                    "create-phase"
                      ? "Submitting..."
                      : "Submit phase"}
                  </button>
                </div>
              </form>
            </div>
          </ModalFrame>
        )}

        {completionProject && (
          <ModalFrame className="provider-project-modal-backdrop" onClose={closeCompletionModal} busy={actionLoading !== null} error={error}>
            <div className="provider-project-modal">
              <div className="provider-project-modal-header">
                <div>
                  <p>
                    PROJECT COMPLETION
                  </p>

                  <h2>
                    Request completion
                  </h2>
                </div>

                <button
                  type="button"
                  onClick={
                    closeCompletionModal
                  }
                >
                  Close
                </button>
              </div>

              <div className="provider-project-modal-phase">
                <strong>
                  {
                    completionProject.title
                  }
                </strong>

                <span>
                  {completionProject.title}
                </span>
              </div>

              <p>
                All valid phases are
                completed. Send this
                project to the
                Administrator for formal
                project completion? Any funding not released will remain with the funder and be frozen for independent review. Closing the project does not pay out the balance.
              </p>

              <div className="provider-project-modal-actions">
                <button
                  type="button"
                  className="provider-project-secondary"
                  onClick={
                    closeCompletionModal
                  }
                >
                  Cancel
                </button>

                <button
                  type="button"
                  className="provider-project-primary"
                  onClick={
                    requestProjectCompletion
                  }
                  disabled={
                    actionLoading !==
                    null
                  }
                >
                  {actionLoading ===
                  `complete-${completionProject.id}`
                    ? "Submitting..."
                    : "Request completion"}
                </button>
              </div>
            </div>
          </ModalFrame>
        )}
      </main>
    </AppShell>
  );
}

export default ProviderProjects;
