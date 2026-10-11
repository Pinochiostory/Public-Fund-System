import PageHeader from "../../components/PageHeader";
import PortfolioSnapshot from "../../components/PortfolioSnapshot";
import ActionBoard from "../../components/ActionBoard";
import DashboardMetrics from "../../components/DashboardMetrics";
import AppShell from "../../components/AppShell";
import useVisibleLoading from "../../components/useVisibleLoading";
import { useEffect, useState } from "react";

import api from "../../services/api";
import LoadingSkeleton from "../../components/LoadingSkeleton";


function AdminDashboard() {
  

  const [applications, setApplications] = useState([]);
  const [projects, setProjects] = useState([]);
  const [auditLogs, setAuditLogs] = useState([]);

  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(null);
  const [error, setError] = useState("");

  const loadDashboard = async () => {
    setLoading(true);
    setError("");

    try {
      const [
        applicationsResponse,
        projectsResponse,
        auditLogsResponse,
      ] = await Promise.all([
        api.get("/applications"),
        api.get("/projects"),
        api.get("/audit-logs"),
      ]);

      setApplications(applicationsResponse.data);
      setProjects(projectsResponse.data);
      setAuditLogs(auditLogsResponse.data);
    } catch (error) {
      const message =
        error.response?.data ||
        "Unable to load administrator dashboard.";

      setError(
        typeof message === "string"
          ? message
          : "Unable to load administrator dashboard."
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDashboard();
  }, []);



  const approveApplication = async (applicationId) => {
    setActionLoading(`approve-${applicationId}`);
    setError("");

    try {
      await api.post(
        `/applications/${applicationId}/approve`
      );

      await loadDashboard();
    } catch (error) {
      const message =
        error.response?.data ||
        "Unable to approve application.";

      setError(
        typeof message === "string"
          ? message
          : "Unable to approve application."
      );
    } finally {
      setActionLoading(null);
    }
  };

  const rejectApplication = async (applicationId) => {
    setActionLoading(`reject-${applicationId}`);
    setError("");

    try {
      await api.post(
        `/applications/${applicationId}/reject`
      );

      await loadDashboard();
    } catch (error) {
      const message =
        error.response?.data ||
        "Unable to reject application.";

      setError(
        typeof message === "string"
          ? message
          : "Unable to reject application."
      );
    } finally {
      setActionLoading(null);
    }
  };

  const createProject = async (applicationId) => {
    setActionLoading(`project-${applicationId}`);
    setError("");

    try {
      await api.post(
        `/projects/from-application/${applicationId}`
      );

      await loadDashboard();
    } catch (error) {
      const message =
        error.response?.data ||
        "Unable to create project.";

      setError(
        typeof message === "string"
          ? message
          : "Unable to create project."
      );
    } finally {
      setActionLoading(null);
    }
  };

  const formatMoney = (value) => {
    const amount = Number(value || 0);

    return new Intl.NumberFormat("en-ZA", {
      style: "currency",
      currency: "ZAR",
    }).format(amount);
  };

  const pendingApplications = applications.filter(
    (application) =>
      application.status === "PENDING"
  ).length;

  const approvedApplications = applications.filter(
    (application) =>
      application.status === "APPROVED"
  ).length;

  const activeProjects = projects.filter(
    (project) =>
      project.status === "ACTIVE"
  ).length;

  const applicationHasProject = (applicationId) =>
    projects.some(
      (project) =>
        project.applicationId === applicationId
    );

  const showLoading = useVisibleLoading(loading);

  return (
    <AppShell>
      

      <main className="admin-main">
        <PageHeader title="Funding oversight" description="Review funding applications and manage approved public projects."></PageHeader>

        {error && (
          <div className="admin-error">
            {error}
          </div>
        )}

        {showLoading ? (
          <LoadingSkeleton />
        ) : (
          <>
            <DashboardMetrics items={[
 {label:"Awaiting review",value:pendingApplications,note:"Applications requiring a decision",icon:"document",to:"/admin/applications"},
 {label:"Approved applications",value:approvedApplications,note:"Review project creation status",icon:"check",to:"/admin/applications"},
 {label:"Active projects",value:activeProjects,note:"Manage phases and completion",icon:"folder",to:"/admin/projects"},
 {label:"Audit activity",value:auditLogs.length,note:"Recorded system events",icon:"audit",to:"/admin/audit-logs"}]} />
            <div className="overview-focus">
              <PortfolioSnapshot records={applications} title="Funding application pipeline" noun="Applications" />
              <ActionBoard title="Needs your attention" items={[
                {title:"Review funding requests",description:"Make a decision on pending applications.",count:pendingApplications,to:"/admin/applications"},
                {title:"Manage project delivery",description:"Review phases and completion requests.",count:activeProjects,to:"/admin/projects"},
                {title:"Review payments",description:"Check releases for approved phases.",to:"/admin/disbursements"}
              ]}/>
            </div>


            <section className="admin-card">
              <div className="admin-card-heading">
                <div>
                  <h2>
                    Funding applications
                  </h2>

                  <p>
                    Review pending requests and create
                    projects from approved applications.
                  </p>
                </div>
              </div>

              {applications.length === 0 ? (
                <div className="admin-empty">
                  No funding applications found.
                </div>
              ) : (
                <div className="admin-table-wrapper" tabIndex={0} role="region" aria-label="Scrollable records">
                  <table className="admin-table">
                    <thead>
                      <tr>
                        <th>ID</th>
                        <th>Project</th>
                        <th>Budget</th>
                        <th>Timeline</th>
                        <th>Status</th>
                        <th>Actions</th>
                      </tr>
                    </thead>

                    <tbody>
                      {applications.map((application) => {
                        const hasProject =
                          applicationHasProject(
                            application.id
                          );

                        return (
                          <tr key={application.id}>
                            <td>
                              #{application.id}
                            </td>

                            <td>
                              <strong>
                                {application.title}
                              </strong>

                              <span className="admin-description">
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
                                className={`admin-status admin-status-${application.status.toLowerCase()}`}
                              >
                                {application.status}
                              </span>
                            </td>

                            <td>
                              <div className="admin-actions">
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
                                        actionLoading !== null
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
                                        actionLoading !== null
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
                                        actionLoading !== null
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
              )}
            </section>

            <section className="admin-card">
              <div className="admin-card-heading">
                <div>
                  <h2>
                    Recent audit activity
                  </h2>

                  <p>
                    Latest recorded administrative and
                    system activity.
                  </p>
                </div>
              </div>

              {auditLogs.length === 0 ? (
                <div className="admin-empty">
                  No audit activity found.
                </div>
              ) : (
                <div className="audit-list">
                  {auditLogs
                    .slice(0, 8)
                    .map((log) => (
                      <div
                        className="audit-item"
                        key={log.id}
                      >
                        <div>
                          <strong>
                            {log.action}
                          </strong>

                          <p>
                            {log.description}
                          </p>
                        </div>

                        <span>
                          {log.userEmail}
                        </span>
                      </div>
                    ))}
                </div>
              )}
            </section>
          </>
        )}
      </main>
    </AppShell>
  );
}

export default AdminDashboard;
