import PageHeader from "../../components/PageHeader";
import RecordFilters from "../../components/RecordFilters";
import { useRecordFilters } from "../../components/useRecordFilters";
import AppShell from "../../components/AppShell";
import useVisibleLoading from "../../components/useVisibleLoading";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import api from "../../services/api";
import LoadingSkeleton from "../../components/LoadingSkeleton";


function OfficerPhases() {
  const navigate = useNavigate();
  

  const [phases, setPhases] = useState([]);
  const [loading, setLoading] = useState(true);

  const [error, setError] = useState("");

  useEffect(() => {
    loadPhases();
  }, []);

  const loadPhases = async () => {
    try {
      setLoading(true);
      setError("");

      const response =
        await api.get(
          "/project-phases"
        );

      setPhases(
        response.data || []
      );
    } catch (err) {
      setError(
        err.response?.data ||
          "Unable to load project phases."
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

  const pendingApproval =
    phases.filter(
      (phase) =>
        phase.status ===
        "PENDING_APPROVAL"
    ).length;

  const inProgress =
    phases.filter(
      (phase) =>
        phase.status ===
        "IN_PROGRESS"
    ).length;

  const evidenceApproved =
    phases.filter(
      (phase) =>
        phase.status ===
        "EVIDENCE_APPROVED"
    ).length;

  const completed =
    phases.filter(
      (phase) =>
        phase.status ===
        "COMPLETED"
    ).length;

  const recordFilter = useRecordFilters(phases);

  const showLoading = useVisibleLoading(loading);

  return (
    <AppShell>
      

      <main className="officer-phase-main">
        <PageHeader title="Project Phases" description="Monitor project phase progress and identify phases requiring evidence verification."></PageHeader>
        {!loading && <RecordFilters filter={recordFilter} label="phases" />}

        {error && (
          <div className="officer-phase-message error" role="alert">
            {error}
          </div>
        )}

        {!showLoading && (<section className="officer-phase-stats">
          <article>
            <span>
              Total phases
            </span>

            <strong>
              {phases.length}
            </strong>
          </article>

          <article>
            <span>
              Pending approval
            </span>

            <strong>
              {pendingApproval}
            </strong>
          </article>

          <article>
            <span>
              In progress
            </span>

            <strong>
              {inProgress}
            </strong>
          </article>

          <article>
            <span>
              Evidence approved
            </span>

            <strong>
              {evidenceApproved}
            </strong>
          </article>

          <article>
            <span>
              Completed
            </span>

            <strong>
              {completed}
            </strong>
          </article>
        </section>)}

        {showLoading ? (
          <LoadingSkeleton cards={3} />
        ) : phases.length === 0 ? (
          <div className="officer-phase-empty">
            No project phases found.
          </div>
        ) : (
          <div className="officer-phase-grid">
            {recordFilter.filtered
              .sort(
                (a, b) =>
                  a.projectId -
                    b.projectId ||
                  a.phaseOrder -
                    b.phaseOrder
              )
              .map(
                (phase) => (
                  <article
                    className="officer-phase-card"
                    key={
                      phase.id
                    }
                  >
                    <div className="officer-phase-card-top">
                      <div>
                        <span className="officer-phase-id">
                          {phase.projectTitle || "Project name unavailable"}
                        </span>

                        <h2>
                          {
                            phase.phaseName
                          }
                        </h2>

                        <p>
                          Phase{" "}
                          {
                            phase.phaseOrder
                          }
                        </p>
                      </div>

                      <span
                        className={`officer-phase-status status-${phase.status
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

                    <p className="officer-phase-description">
                      {
                        phase.description
                      }
                    </p>

                    <div className="officer-phase-info-grid">
                      <div>
                        <span>
                          Allocated budget
                        </span>

                        <strong>
                          {formatMoney(
                            phase.allocatedBudget
                          )}
                        </strong>
                      </div>

                      <div>
                        <span>
                          Phase order
                        </span>

                        <strong>
                          {
                            phase.phaseOrder
                          }
                        </strong>
                      </div>
                    </div>

                    {phase.status ===
                      "IN_PROGRESS" && (
                      <div className="officer-phase-notice">
                        This phase is active.
                        Review submitted evidence
                        when it becomes available.
                      </div>
                    )}

                    {phase.status ===
                      "EVIDENCE_APPROVED" && (
                      <div className="officer-phase-notice">
                        Evidence has been approved.
                        The phase is awaiting formal
                        conclusion by the
                        Administrator.
                      </div>
                    )}

                    {phase.status ===
                      "COMPLETED" && (
                      <div className="officer-phase-notice">
                        This phase has been formally
                        completed.
                      </div>
                    )}

                    {phase.status ===
                      "REJECTED" &&
                      phase.rejectionReason && (
                        <div className="officer-phase-rejection">
                          <strong>
                            Phase rejection reason
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
                      <button
                        className="officer-phase-primary"
                        onClick={() =>
                          navigate(
                            "/officer/evidence"
                          )
                        }
                      >
                        Review evidence
                      </button>
                    )}
                  </article>
                )
              )}
          </div>
        )}
      </main>
    </AppShell>
  );
}

export default OfficerPhases;
