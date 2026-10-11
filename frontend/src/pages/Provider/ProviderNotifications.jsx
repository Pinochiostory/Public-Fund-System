import ActionFeedback from "../../components/ActionFeedback";
import PageHeader from "../../components/PageHeader";
import AppShell from "../../components/AppShell";
import useVisibleLoading from "../../components/useVisibleLoading";
import RecordFilters from "../../components/RecordFilters";
import { useRecordFilters } from "../../components/useRecordFilters";
import { useEffect, useState } from "react";

import api from "../../services/api";
import LoadingSkeleton from "../../components/LoadingSkeleton";


function ProviderNotifications() {
  

  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState("");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  useEffect(() => {
    loadNotifications();
  }, []);

  const loadNotifications = async () => {
    setLoading(true);
    setError("");

    try {
      const response = await api.get("/notifications/my");

      setNotifications(response.data);
    } catch (error) {
      const message =
        error.response?.data?.message ||
        error.response?.data ||
        error.message ||
        "Unable to load notifications.";

      setError(
        typeof message === "string"
          ? message
          : "Unable to load notifications."
      );
    } finally {
      setLoading(false);
    }
  };

  const markAsRead = async (notificationId) => {
    setActionLoading(`read-${notificationId}`);
    setError("");
    setSuccess("");

    try {
      await api.post(
        `/notifications/${notificationId}/read`
      );

      setSuccess("Notification marked as read.");

      await loadNotifications();
    } catch (error) {
      const message =
        error.response?.data?.message ||
        error.response?.data ||
        error.message ||
        "Unable to update notification.";

      setError(
        typeof message === "string"
          ? message
          : "Unable to update notification."
      );
    } finally {
      setActionLoading("");
    }
  };

  const markAllAsRead = async () => {
    setActionLoading("read-all");
    setError("");
    setSuccess("");

    try {
      await api.post("/notifications/my/read-all");

      setSuccess("All notifications marked as read.");

      await loadNotifications();
    } catch (error) {
      const message =
        error.response?.data?.message ||
        error.response?.data ||
        error.message ||
        "Unable to update notifications.";

      setError(
        typeof message === "string"
          ? message
          : "Unable to update notifications."
      );
    } finally {
      setActionLoading("");
    }
  };



  const unreadCount = notifications.filter(
    (notification) => !notification.readStatus
  ).length;

  const notificationFilter = useRecordFilters(notifications.map(item => ({
    ...item, status: item.readStatus ? "READ" : "UNREAD",
  })));
  const showLoading = useVisibleLoading(loading);

  return (
    <AppShell>
      

      <main className="provider-notifications-main">
        <PageHeader title="Notifications" description="View updates about applications, disbursements and project verification.">{unreadCount > 0 && (
            <button
              className="mark-all-button"
              onClick={markAllAsRead}
              disabled={actionLoading !== ""}
            >
              {actionLoading === "read-all"
                ? "Updating..."
                : "Mark all as read"}
            </button>
          )}</PageHeader>
        {!loading && <RecordFilters filter={notificationFilter} label="notifications" />}

        {error && (
          <div className="provider-notifications-error">
            {error}
          </div>
        )}

        {success && (
          <ActionFeedback message={success}/>
        )}

        {showLoading ? (
          <LoadingSkeleton cards={2} />
        ) : notifications.length === 0 ? (
          <div className="provider-notifications-empty">
            No notifications found.
          </div>
        ) : (
          <section className="provider-notifications-card">
            <div className="provider-notifications-summary">
              <span>
                {notifications.length} total notifications
              </span>

              <strong>
                {unreadCount} unread
              </strong>
            </div>

            <div className="provider-notifications-list">
              {notificationFilter.filtered.map((notification) => (
                <article
                  className={`provider-notification-item ${
                    notification.readStatus
                      ? "read"
                      : "unread"
                  }`}
                  key={notification.id}
                >
                  <div className="provider-notification-content">
                    <div className="provider-notification-heading">
                      <h3>{notification.title}</h3>

                      {!notification.readStatus && (
                        <span className="provider-unread-indicator">
                          Unread
                        </span>
                      )}
                    </div>

                    <p>{notification.message}</p>

                    {notification.createdAt && (
                      <span className="provider-notification-date">
                        {new Date(
                          notification.createdAt
                        ).toLocaleString("en-ZA")}
                      </span>
                    )}
                  </div>

                  {!notification.readStatus && (
                    <button
                      onClick={() =>
                        markAsRead(notification.id)
                      }
                      disabled={actionLoading !== ""}
                    >
                      {actionLoading ===
                      `read-${notification.id}`
                        ? "Updating..."
                        : "Mark as read"}
                    </button>
                  )}
                </article>
              ))}
            </div>
          </section>
        )}
      </main>
    </AppShell>
  );
}

export default ProviderNotifications;
