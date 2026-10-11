import ActionFeedback from "../../components/ActionFeedback";
import useConfirmation from "../../components/useConfirmation";
import PageHeader from "../../components/PageHeader";
import RecordFilters from "../../components/RecordFilters";
import { useRecordFilters } from "../../components/useRecordFilters";
import AppShell from "../../components/AppShell";
import useVisibleLoading from "../../components/useVisibleLoading";
import { useEffect, useState } from "react";

import api from "../../services/api";
import LoadingSkeleton from "../../components/LoadingSkeleton";


function AdminApplications() {
  const {confirm,dialog}=useConfirmation();
  

  const [applications, setApplications] = useState([]);
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState("");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  useEffect(() => {
    loadApplications();
  }, []);

  const loadApplications = async () => {
    setLoading(true);
    setError("");

    try {
      const [applicationsResponse, projectsResponse] =
        await Promise.all([
          api.get("/applications"),
          api.get("/projects"),
        ]);

      setApplications(applicationsResponse.data);
      setProjects(projectsResponse.data);
    } catch (error) {
      setError(
        getErrorMessage(
          error,
          "Unable to load funding applications."
        )
      );
    } finally {
      setLoading(false);
    }
  };

  const getErrorMessage = (error, fallback) => {
    const message =
      error.response?.data?.message ||
      error.response?.data ||
      error.message ||
      fallback;

    return typeof message === "string"
      ? message
      : fallback;
  };

  const approveApplication = async (id) => {
    if(!await confirm({title:"Approve application",message:"Approve "+applications.find(a=>a.id===id)?.title+" with its submitted budget? This decision is recorded in the audit history."}))return;
    setActionLoading(`approve-${id}`);
    setError("");
    setSuccess("");

    try {
      await api.post(`/applications/${id}/approve`);

      setSuccess("Application approved successfully.");

      await loadApplications();
    } catch (error) {
      setError(
        getErrorMessage(
          error,
          "Unable to approve application."
        )
      );
    } finally {
      setActionLoading("");
    }
  };

  const rejectApplication = async (id) => {
    const decision=await confirm({title:"Reject project application",message:"The provider will receive the reason below. This decision is final.",label:"Reason for rejection",action:"Reject application"});
    if(!decision)return;
    setActionLoading(`reject-${id}`);
    setError("");
    setSuccess("");

    try {
      await api.post(`/applications/${id}/reject`,new URLSearchParams({rejectionReason:decision.value}));

      setSuccess("Application rejected successfully.");

      await loadApplications();
    } catch (error) {
      setError(
        getErrorMessage(
          error,
          "Unable to reject application."
        )
      );
    } finally {
      setActionLoading("");
    }
  };

  const createProject = async (applicationId) => {
    setActionLoading(`project-${applicationId}`);
    setError("");
    setSuccess("");

    try {
      await api.post(
        `/projects/from-application/${applicationId}`
      );

      setSuccess("Project created successfully.");

      await loadApplications();
    } catch (error) {
      setError(
        getErrorMessage(
          error,
          "Unable to create project."
        )
      );
    } finally {
      setActionLoading("");
    }
  };

  const applicationHasProject = (applicationId) => {
    return projects.some(
      (project) =>
        project.applicationId === applicationId
    );
  };

  const formatMoney = (value) => {
    return new Intl.NumberFormat("en-ZA", {
      style: "currency",
      currency: "ZAR",
    }).format(Number(value || 0));
  };



  const recordFilter = useRecordFilters(applications);

  const showLoading = useVisibleLoading(loading);

  return (
    <AppShell>{dialog}
      

      <main className="admin-applications-main">
        <PageHeader title="Applications" description="Review submitted funding requests and convert approved applications into projects."></PageHeader>
        {!loading && <RecordFilters filter={recordFilter} label="applications" />}

        {error && (
          <div className="admin-applications-error">
            {error}
          </div>
        )}

        {success && (
          <ActionFeedback message={success}/>
        )}

        {showLoading ? (
          <LoadingSkeleton cards={3} />
        ) : applications.length === 0 ? (
          <div className="admin-applications-empty">
            No funding applications found.
          </div>
        ) : (
          <section className="admin-applications-card">
            <div className="admin-applications-table-wrapper" tabIndex={0} role="region" aria-label="Scrollable records">
              <table className="admin-applications-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Application</th>
                    <th>Budget</th>
                    <th>Timeline</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>

                <tbody>
                  {recordFilter.filtered.map((application) => {
                    const hasProject =
                      applicationHasProject(
                        application.id
                      );

                    return (
                      <tr key={application.id}>
                        <td>#{application.id}</td>

                        <td>
                          <strong>
                            {application.title}
                          </strong>

                          <span>
                            {application.description}
                          </span>
                        </td>

                        <td>
                          {formatMoney(
                            application.requestedBudget
                          )}
                        </td>

                        <td>
                          {application.timeline}
                        </td>

                        <td>
                          <span
                            className={`admin-application-status status-${application.status
                              ?.toLowerCase()
                              .replaceAll("_", "-")}`}
                          >
                            {application.status}
                          </span>
                        </td>

                        <td>
                          <div className="admin-application-actions">
                            {application.status ===
                              "PENDING" && (
                              <>
                                <button
                                  className="approve-button"
                                  onClick={() =>
                                    approveApplication(
                                      application.id
                                    )
                                  }
                                  disabled={
                                    actionLoading !== ""
                                  }
                                >
                                  {actionLoading ===
                                  `approve-${application.id}`
                                    ? "Approving..."
                                    : "Approve"}
                                </button>

                                <button
                                  className="reject-button"
                                  onClick={() =>
                                    rejectApplication(
                                      application.id
                                    )
                                  }
                                  disabled={
                                    actionLoading !== ""
                                  }
                                >
                                  {actionLoading ===
                                  `reject-${application.id}`
                                    ? "Rejecting..."
                                    : "Reject"}
                                </button>
                              </>
                            )}

                            {application.status ===
                              "APPROVED" &&
                              !hasProject && (
                                <button
                                  className="project-button"
                                  onClick={() =>
                                    createProject(
                                      application.id
                                    )
                                  }
                                  disabled={
                                    actionLoading !== ""
                                  }
                                >
                                  {actionLoading ===
                                  `project-${application.id}`
                                    ? "Creating..."
                                    : "Create project"}
                                </button>
                              )}

                            {hasProject && (
                              <span className="project-created-label">
                                Project created
                              </span>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </section>
        )}
      </main>
    </AppShell>
  );
}

export default AdminApplications;
