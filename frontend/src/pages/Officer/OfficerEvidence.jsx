import ActionFeedback from "../../components/ActionFeedback";
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


function OfficerEvidence() {
  

  const [evidence, setEvidence] = useState([]);
  const [phases, setPhases] = useState([]);

  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] =
    useState(null);

  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const [reviewEvidence, setReviewEvidence] =
    useState(null);

  const [reviewType, setReviewType] =
    useState("");

  const [verificationComment, setVerificationComment] =
    useState("");

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
        evidenceResponse,
        phasesResponse,
      ] = await Promise.all([
        api.get("/evidence"),
        api.get("/project-phases"),
      ]);

      setEvidence(
        evidenceResponse.data || []
      );

      setPhases(
        phasesResponse.data || []
      );
    } catch (err) {
      setError(
        getErrorMessage(
          err,
          "Unable to load evidence."
        )
      );
    } finally {
      setLoading(false);
    }
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

  const formatDate = (value) => {
    if (!value) {
      return "Not available";
    }

    return new Date(value)
      .toLocaleString("en-ZA");
  };

  const getPhase = (phaseId) => {
    return phases.find(
      (phase) =>
        phase.id === phaseId
    );
  };

  const openEvidenceFile = async (
    item
  ) => {
    try {
      setActionLoading(
        `file-${item.id}`
      );

      setError("");

      const response =
        await api.get(
          `/evidence/${item.id}/file`,
          {
            responseType: "blob",
          }
        );

      const fileType =
        response.headers[
          "content-type"
        ] ||
        item.fileType ||
        "application/octet-stream";

      const blob =
        new Blob(
          [response.data],
          {
            type: fileType,
          }
        );

      const fileUrl =
        window.URL.createObjectURL(
          blob
        );

      window.open(
        fileUrl,
        "_blank",
        "noopener,noreferrer"
      );

      setTimeout(() => {
        window.URL.revokeObjectURL(
          fileUrl
        );
      }, 60000);
    } catch (err) {
      setError(
        getErrorMessage(
          err,
          "Unable to open evidence file."
        )
      );
    } finally {
      setActionLoading(
        null
      );
    }
  };

  const openReviewModal = (
    item,
    type
  ) => {
    setReviewEvidence(
      item
    );

    setReviewType(
      type
    );

    setVerificationComment(
      ""
    );

    setError("");
    setSuccess("");
  };

  const closeReviewModal = (force=false) => {
    if(force!==true&&!confirmDiscard(Boolean(verificationComment)))return;
    setReviewEvidence(
      null
    );

    setReviewType(
      ""
    );

    setVerificationComment(
      ""
    );
  };

  const approveEvidence = async () => {
    if (!reviewEvidence) {
      return;
    }

    try {
      setActionLoading(
        `approve-${reviewEvidence.id}`
      );

      setError("");
      setSuccess("");

      const body =
        new URLSearchParams();

      if (
        verificationComment.trim()
      ) {
        body.append(
          "verificationComment",
          verificationComment.trim()
        );
      }

      await api.post(
        `/evidence/${reviewEvidence.id}/approve`,
        body
      );

      setSuccess(
        "Evidence file approved. The phase advances only after every file in the submission has been approved."
      );

      closeReviewModal(true);
      await loadData();
    } catch (err) {
      setError(
        getErrorMessage(
          err,
          "Unable to approve evidence."
        )
      );
    } finally {
      setActionLoading(
        null
      );
    }
  };

  const rejectEvidence = async () => {
    if (!reviewEvidence) {
      return;
    }

    if (
      !verificationComment.trim()
    ) {
      setError(
        "A rejection reason is required."
      );

      return;
    }

    try {
      setActionLoading(
        `reject-${reviewEvidence.id}`
      );

      setError("");
      setSuccess("");

      const body =
        new URLSearchParams();

      body.append(
        "verificationComment",
        verificationComment.trim()
      );

      await api.post(
        `/evidence/${reviewEvidence.id}/reject`,
        body
      );

      setSuccess(
        "Evidence rejected. The phase remains in progress and the Service Provider may submit corrected evidence."
      );

      closeReviewModal();
      await loadData();
    } catch (err) {
      setError(
        getErrorMessage(
          err,
          "Unable to reject evidence."
        )
      );
    } finally {
      setActionLoading(
        null
      );
    }
  };

  const submitReview = async (
    event
  ) => {
    event.preventDefault();

    if (
      reviewType ===
      "APPROVE"
    ) {
      await approveEvidence();
      return;
    }

    if (
      reviewType ===
      "REJECT"
    ) {
      await rejectEvidence();
    }
  };

  const sortedEvidence = [
    ...evidence,
  ].sort((a, b) => {
    const dateDifference =
      new Date(
        b.uploadedAt || 0
      ) -
      new Date(
        a.uploadedAt || 0
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
  });

  const pendingCount =
    evidence.filter(
      (item) =>
        item.status ===
        "PENDING"
    ).length;

  const approvedCount =
    evidence.filter(
      (item) =>
        item.status ===
        "APPROVED"
    ).length;

  const rejectedCount =
    evidence.filter(
      (item) =>
        item.status ===
        "REJECTED"
    ).length;

  const recordFilter = useRecordFilters(sortedEvidence);

  const showLoading = useVisibleLoading(loading);

  return (
    <AppShell><UnsavedChanges dirty={Boolean(reviewEvidence&&verificationComment)}/>
      

      <main className="officer-evidence-main">
        <PageHeader title="Evidence Review" description="Review evidence submitted by Service Providers and approve or reject each submission."></PageHeader>
        {!loading && <RecordFilters filter={recordFilter} label="evidence" />}

        {error && (
          <div className="officer-evidence-message error" role="alert">
            {error}
          </div>
        )}

        {success && (
          <ActionFeedback message={success}/>
        )}

        {!showLoading && (<section className="officer-evidence-stats">
          <article>
            <span>
              Total evidence
            </span>

            <strong>
              {evidence.length}
            </strong>
          </article>

          <article>
            <span>
              Awaiting review
            </span>

            <strong>
              {pendingCount}
            </strong>
          </article>

          <article>
            <span>
              Approved
            </span>

            <strong>
              {approvedCount}
            </strong>
          </article>

          <article>
            <span>
              Rejected
            </span>

            <strong>
              {rejectedCount}
            </strong>
          </article>
        </section>)}

        {showLoading ? (
          <LoadingSkeleton cards={3} />
        ) : sortedEvidence.length ===
          0 ? (
          <div className="officer-evidence-empty">
            No evidence submissions
            found.
          </div>
        ) : (
          <div className="officer-evidence-list">
            {recordFilter.filtered.map(
              (item) => {
                const phase =
                  getPhase(
                    item.phaseId
                  );

                return (
                  <article
                    className="officer-evidence-card"
                    key={
                      item.id
                    }
                  >
                    <div className="officer-evidence-card-header">
                      <div>
                        <span className="officer-evidence-id">
                          EVIDENCE #
                          {item.id}
                        </span>

                        <h2>
                          {phase?.phaseName ||
                            "Project phase"}
                        </h2>

                        <p>
                          {phase?.projectTitle || "Project name unavailable"}{" "}
                          · Phase{" "}
                          {phase?.phaseOrder ||
                            "-"}
                        </p>
                      </div>

                      <span
                        className={`officer-evidence-status status-${item.status
                          .toLowerCase()
                          .replaceAll(
                            "_",
                            "-"
                          )}`}
                      >
                        {formatStatus(
                          item.status
                        )}
                      </span>
                    </div>

                    <div className="officer-evidence-info">
                      <div><span>Service Provider</span><strong>{item.providerName || "Provider details unavailable"}</strong></div>
                      <div><span>File type / size</span><strong>{item.fileType} · {item.fileSize==null?"Size not recorded":(item.fileSize/1024).toFixed(1)+" KB"}</strong></div>
                      <div>
                        <span>
                          File
                        </span>

                        <strong>
                          {item.fileName ||
                            "Evidence file"}
                        </strong>
                      </div>

                      <div>
                        <span>
                          Uploaded
                        </span>

                        <strong>
                          {formatDate(
                            item.uploadedAt
                          )}
                        </strong>
                      </div>

                      <div>
                        <span>
                          Phase status
                        </span>

                        <strong>
                          {formatStatus(
                            phase?.status
                          )}
                        </strong>
                      </div>

                      <div>
                        <span>
                          Verified
                        </span>

                        <strong>
                          {formatDate(
                            item.verifiedAt
                          )}
                        </strong>
                      </div>
                    </div>

                    <div className="officer-evidence-file-actions">
                      <button
                        type="button"
                        className="officer-evidence-secondary"
                        onClick={() =>
                          openEvidenceFile(
                            item
                          )
                        }
                        disabled={
                          actionLoading !==
                          null
                        }
                      >
                        {actionLoading ===
                        `file-${item.id}`
                          ? "Opening..."
                          : "Open evidence"}
                      </button>
                    </div>

                    {item.status ===
                      "PENDING" && (
                      <div className="officer-evidence-actions">
                        <button
                          type="button"
                          className="officer-evidence-primary"
                          onClick={() =>
                            openReviewModal(
                              item,
                              "APPROVE"
                            )
                          }
                          disabled={
                            actionLoading !==
                            null
                          }
                        >
                          Approve
                        </button>

                        <button
                          type="button"
                          className="officer-evidence-danger"
                          onClick={() =>
                            openReviewModal(
                              item,
                              "REJECT"
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
                    )}

                    {item.status ===
                      "APPROVED" && (
                      <div className="officer-evidence-info-box">
                        This file is approved. Every file in the latest submission must be approved before the Administrator can conclude the phase.
                      </div>
                    )}

                    {item.status ===
                      "REJECTED" && (
                      <div className="officer-evidence-rejection">
                        <strong>
                          Rejection reason
                        </strong>

                        <p>
                          {item.verificationComment ||
                            "No verification comment was provided."}
                        </p>
                      </div>
                    )}

                    {item.status ===
                      "APPROVED" &&
                      item.verificationComment && (
                        <div className="officer-evidence-comment">
                          <strong>
                            Verification comment
                          </strong>

                          <p>
                            {
                              item.verificationComment
                            }
                          </p>
                        </div>
                      )}
                  </article>
                );
              }
            )}
          </div>
        )}

        {reviewEvidence && (
          <ModalFrame className="officer-evidence-modal-backdrop" onClose={closeReviewModal} busy={actionLoading !== null} error={error}>
            <div className="officer-evidence-modal">
              <div className="officer-evidence-modal-header">
                <div>
                  <p>
                    {reviewType ===
                    "APPROVE"
                      ? "EVIDENCE APPROVAL"
                      : "EVIDENCE REJECTION"}
                  </p>

                  <h2>
                    {reviewType ===
                    "APPROVE"
                      ? "Approve evidence"
                      : "Reject evidence"}
                  </h2>
                </div>

                <button
                  type="button"
                  onClick={
                    closeReviewModal
                  }
                >
                  Close
                </button>
              </div>

              <div className="officer-evidence-modal-summary">
                <strong>
                  {getPhase(
                    reviewEvidence.phaseId
                  )?.phaseName ||
                    "Project phase"}
                </strong>

                <span>
                  {reviewEvidence.fileName}
                </span>
              </div>

              {reviewType ===
                "APPROVE" && (
                <div className="officer-evidence-modal-notice">
                  Approving this evidence
                  will move the phase to
                  Evidence Approved. The
                  Administrator must still
                  formally conclude the
                  phase before another
                  phase may begin.
                </div>
              )}

              {reviewType ===
                "REJECT" && (
                <div className="officer-evidence-modal-notice">
                  Rejecting this evidence
                  will keep the phase In
                  Progress so the Service
                  Provider can submit
                  corrected evidence.
                </div>
              )}

              <form
                onSubmit={
                  submitReview
                }
              >
                <label>
                  {reviewType ===
                  "REJECT"
                    ? "Rejection reason"
                    : "Verification comment"}

                  <textarea
                    rows="5"
                    value={
                      verificationComment
                    }
                    onChange={(
                      event
                    ) =>
                      setVerificationComment(
                        event.target.value
                      )
                    }
                    placeholder={
                      reviewType ===
                      "REJECT"
                        ? "Explain why the evidence cannot be approved."
                        : "Optional verification comment."
                    }
                    required={
                      reviewType ===
                      "REJECT"
                    }
                  />
                </label>

                <div className="officer-evidence-modal-actions">
                  <button
                    type="button"
                    className="officer-evidence-secondary"
                    onClick={
                      closeReviewModal
                    }
                    disabled={
                      actionLoading !==
                      null
                    }
                  >
                    Cancel
                  </button>

                  <button
                    type="submit"
                    className={
                      reviewType ===
                      "REJECT"
                        ? "officer-evidence-danger"
                        : "officer-evidence-primary"
                    }
                    disabled={
                      actionLoading !==
                      null
                    }
                  >
                    {reviewType ===
                    "APPROVE"
                      ? actionLoading ===
                        `approve-${reviewEvidence.id}`
                        ? "Approving..."
                        : "Approve evidence"
                      : actionLoading ===
                        `reject-${reviewEvidence.id}`
                      ? "Rejecting..."
                      : "Reject evidence"}
                  </button>
                </div>
              </form>
            </div>
          </ModalFrame>
        )}
      </main>
    </AppShell>
  );
}

export default OfficerEvidence;
