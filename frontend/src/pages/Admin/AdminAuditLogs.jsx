import PageHeader from "../../components/PageHeader";
import RecordFilters from "../../components/RecordFilters";
import { useRecordFilters } from "../../components/useRecordFilters";
import AppShell from "../../components/AppShell";
import useVisibleLoading from "../../components/useVisibleLoading";
import { useEffect, useState } from "react";

import api from "../../services/api";
import LoadingSkeleton from "../../components/LoadingSkeleton";


function AdminAuditLogs() {
  

  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    loadLogs();
  }, []);

  const loadLogs = async () => {
    setLoading(true);
    setError("");

    try {
      const response = await api.get("/audit-logs");

      setLogs(response.data);
    } catch (error) {
      const message =
        error.response?.data?.message ||
        error.response?.data ||
        error.message ||
        "Unable to load audit logs.";

      setError(
        typeof message === "string"
          ? message
          : "Unable to load audit logs."
      );
    } finally {
      setLoading(false);
    }
  };



  const formatDate = (value) => {
    if (!value) {
      return "-";
    }

    return new Date(value).toLocaleString("en-ZA");
  };

  const recordFilter = useRecordFilters(logs);

  const showLoading = useVisibleLoading(loading);

  return (
    <AppShell>
      

      <main className="admin-audit-main">
        <PageHeader title="Audit Logs" description="Review recorded activity across the public fund management system."></PageHeader>
        {!loading && <RecordFilters filter={recordFilter} label="audit records" />}

        {error && (
          <div className="admin-audit-error">
            {error}
          </div>
        )}

        {showLoading ? (
          <LoadingSkeleton cards={2} />
        ) : logs.length === 0 ? (
          <div className="admin-audit-empty">
            No audit records found.
          </div>
        ) : (
          <section className="admin-audit-card">
            <div className="admin-audit-summary">
              <span>
                Total recorded actions
              </span>

              <strong>
                {logs.length}
              </strong>
            </div>

            <div className="admin-audit-table-wrapper" tabIndex={0} role="region" aria-label="Scrollable records">
              <table className="admin-audit-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Action</th>
                    <th>Description</th>
                    <th>User</th>
                    <th>Role</th>
                    <th>Date</th>
                  </tr>
                </thead>

                <tbody>
                  {recordFilter.filtered.map((log) => (
                    <tr key={log.id}>
                      <td>
                        #{log.id}
                      </td>

                      <td>
                        <strong>
                          {log.action}
                        </strong>
                      </td>

                      <td>
                        {log.description}
                      </td>

                      <td>
                        {log.userEmail}
                      </td>

                      <td>
                        {log.userRole}
                      </td>

                      <td>
                        {formatDate(
                          log.createdAt
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )}
      </main>
    </AppShell>
  );
}

export default AdminAuditLogs;
