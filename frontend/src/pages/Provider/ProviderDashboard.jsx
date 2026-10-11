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


function ProviderDashboard() {
  const navigate = useNavigate();
  

  const [applications, setApplications] = useState([]);
  const [projects, setProjects] = useState([]);
  const [phases, setPhases] = useState([]);
  const [disbursements, setDisbursements] = useState([]);
  const [notifications, setNotifications] = useState([]);

  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadDashboard = async () => {
    setLoading(true);
    setError("");

    try {
      const [
        applicationsResponse,
        projectsResponse,
        phasesResponse,
        disbursementsResponse,
        notificationsResponse,
        unreadResponse,
      ] = await Promise.all([
        api.get("/applications/my"),
        api.get("/projects/my"),
        api.get("/project-phases/my"),
        api.get("/disbursements/my"),
        api.get("/notifications/my"),
        api.get("/notifications/my/unread-count"),
      ]);

      setApplications(applicationsResponse.data);
      setProjects(projectsResponse.data);
      setPhases(phasesResponse.data);
      setDisbursements(disbursementsResponse.data);
      setNotifications(notificationsResponse.data);

      setUnreadCount(
        unreadResponse.data.unreadCount || 0
      );
    } catch (error) {
      setError(
        error.response?.data ||
          "Unable to load dashboard information."
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDashboard();
  }, []);



  const formatMoney = (value) => {
    const amount = Number(value || 0);

    return new Intl.NumberFormat("en-ZA", {
      style: "currency",
      currency: "ZAR",
    }).format(amount);
  };

  const getStatusClass = (status) => {
    if (!status) {
      return "";
    }

    return `status-${status
      .toLowerCase()
      .replaceAll("_", "-")}`;
  };

  const activeProjects = projects.filter(
    (project) => project.status === "ACTIVE"
  ).length;

  const pendingApplications = applications.filter(
    (application) => application.status === "PENDING"
  ).length;

  const totalReleased = disbursements
    .filter(
      (disbursement) =>
        disbursement.status === "RELEASED"
    )
    .reduce(
      (total, disbursement) =>
        total + Number(disbursement.amount || 0),
      0
    );

  const showLoading = useVisibleLoading(loading);

  return (
    <AppShell>
      

      <main className="provider-main">
        <PageHeader title="Funding overview" description="Track your funding applications, projects and disbursements."><button
            className="new-application-button"
            type="button"
            onClick={() =>
              navigate("/provider/applications/new")
            }
          >
            New application
          </button></PageHeader>

        {error && (
          <div className="provider-error">
            {error}
          </div>
        )}

        {showLoading ? (
          <LoadingSkeleton />
        ) : (
          <>
            <DashboardMetrics items={[
 {label:"Funding applications",value:applications.length,note:pendingApplications + " awaiting review",icon:"document",to:"/provider/applications/new"},
 {label:"Active projects",value:activeProjects,note:projects.length + " projects in your portfolio",icon:"folder",to:"/provider/projects"},
 {label:"Funds received",value:formatMoney(totalReleased),note:"Released across project phases",icon:"money",to:"/provider/disbursements"},
 {label:"Unread updates",value:unreadCount,note:"Decisions and project activity",icon:"bell",to:"/provider/notifications"}]} />
            <div className="overview-focus">
              <PortfolioSnapshot records={applications} title="Your application pipeline" noun="Applications" />
              <ActionBoard items={[
                {title:"Continue your projects",description:"Propose phases and follow delivery.",count:activeProjects,to:"/provider/projects"},
                {title:"Submit project evidence",description:"Send completed work for verification.",to:"/provider/evidence"},
                {title:"Check your updates",description:"Decisions, payments and project activity.",count:unreadCount,to:"/provider/notifications"}
              ]}/>
            </div>


            <section className="provider-grid">
              <div className="provider-card applications-card">
                <div className="card-heading">
                  <div>
                    <h2>
                      Recent applications
                    </h2>

                    <p>
                      Your latest funding submissions
                    </p>
                  </div>
                </div>

                {applications.length === 0 ? (
                  <div className="empty-state">
                    No applications found.
                  </div>
                ) : (
                  <div className="table-wrapper" tabIndex={0} role="region" aria-label="Scrollable records">
                    <table>
                      <thead>
                        <tr>
                          <th>Application</th>
                          <th>Budget</th>
                          <th>Status</th>
                        </tr>
                      </thead>

                      <tbody>
                        {applications
                          .slice(0, 5)
                          .map((application) => (
                            <tr key={application.id}>
                              <td>
                                <strong>
                                  {application.title}
                                </strong>

                                <span>
                                  ID #{application.id}
                                </span>
                              </td>

                              <td>
                                {formatMoney(
                                  application.requestedBudget
                                )}
                              </td>

                              <td>
                                <span
                                  className={`status-pill ${getStatusClass(
                                    application.status
                                  )}`}
                                >
                                  {application.status}
                                  {application.status==="REJECTED"&&<p>Your project was rejected{application.rejectedAt ? " on "+new Date(application.rejectedAt).toLocaleDateString("en-ZA") : ""}.<br/>Reason: {application.rejectionReason || "No historic reason was recorded. Contact the Administrator."}</p>}
                                </span>
                              </td>
                            </tr>
                          ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>

              <div className="provider-card notifications-card">
                <div className="card-heading">
                  <div>
                    <h2>
                      Notifications
                    </h2>

                    <p>
                      Recent account activity
                    </p>
                  </div>
                </div>

                {notifications.length === 0 ? (
                  <div className="empty-state">
                    No notifications found.
                  </div>
                ) : (
                  <div className="notification-list">
                    {notifications
                      .slice(0, 5)
                      .map((notification) => (
                        <div
                          className={`notification-item ${
                            notification.readStatus
                              ? ""
                              : "unread"
                          }`}
                          key={notification.id}
                        >
                          <div>
                            <strong>
                              {notification.title}
                            </strong>

                            <p>
                              {notification.message}
                            </p>
                          </div>

                          {!notification.readStatus && (
                            <span className="unread-dot" />
                          )}
                        </div>
                      ))}
                  </div>
                )}
              </div>
            </section>

            <section className="provider-card">
              <div className="card-heading">
                <div>
                  <h2>
                    Project progress
                  </h2>

                  <p>
                    Current phases across your projects
                  </p>
                </div>
              </div>

              {phases.length === 0 ? (
                <div className="empty-state">
                  No project phases found.
                </div>
              ) : (
                <div className="phase-grid">
                  {phases
                    .slice(0, 6)
                    .map((phase) => (
                      <article
                        className="phase-card"
                        key={phase.id}
                      >
                        <div className="phase-top">
                          <span>
                            Phase {phase.phaseOrder}
                          </span>

                          <span
                            className={`status-pill ${getStatusClass(
                              phase.status
                            )}`}
                          >
                            {phase.status}
                          </span>
                        </div>

                        <h3>
                          {phase.phaseName}
                        </h3>

                        <p>
                          {phase.description}
                        </p>

                        <div className="phase-budget">
                          <span>
                            Allocated budget
                          </span>

                          <strong>
                            {formatMoney(
                              phase.allocatedBudget
                            )}
                          </strong>
                        </div>
                      </article>
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

export default ProviderDashboard;
