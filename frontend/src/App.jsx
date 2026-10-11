import { ActionFeedbackProvider } from "./components/ActionFeedback";
import { lazy, Suspense } from "react";
import LoadingSkeleton from "./components/LoadingSkeleton";
import { readTheme } from "./components/themePreference";
import HowItWorks from "./pages/HowItWorks";
import PublicAppearance from "./components/PublicAppearance";
import { Link, Navigate, Route, Routes } from "react-router-dom";
import Icon from "./components/Icon";

import ProtectedRoute from "./components/ProtectedRoute";

import VerifyEmail from "./pages/VerifyEmail";
import Login from "./pages/Login/Login";
import Register from "./pages/Register/Register";
import ForgotPassword from "./pages/ForgotPassword/ForgotPassword";
import ResetPassword from "./pages/ResetPassword/ResetPassword";

const ProviderDashboard = lazy(() => import("./pages/Provider/ProviderDashboard"));
const NewApplication = lazy(() => import("./pages/Provider/NewApplication"));
const ProviderEvidence = lazy(() => import("./pages/Provider/ProviderEvidence"));
const ProviderProjects = lazy(() => import("./pages/Provider/ProviderProjects"));
const ProviderDisbursements = lazy(() => import("./pages/Provider/ProviderDisbursements"));
const ProviderNotifications = lazy(() => import("./pages/Provider/ProviderNotifications"));

const AdminDashboard = lazy(() => import("./pages/Admin/AdminDashboard"));
const AdminApplications = lazy(() => import("./pages/Admin/AdminApplications"));
const AdminProjects = lazy(() => import("./pages/Admin/AdminProjects"));
const AdminDisbursements = lazy(() => import("./pages/Admin/AdminDisbursements"));
const AdminAuditLogs = lazy(() => import("./pages/Admin/AdminAuditLogs"));
const AdminAccounts = lazy(() => import("./pages/Admin/AdminAccounts"));
const UserManagement = lazy(() => import("./pages/Admin/UserManagement"));
const RequiredPasswordChange = lazy(() => import("./pages/RequiredPasswordChange"));

const OfficerDashboard = lazy(() => import("./pages/Officer/OfficerDashboard"));
const OfficerEvidence = lazy(() => import("./pages/Officer/OfficerEvidence"));
const OfficerPhases = lazy(() => import("./pages/Officer/OfficerPhases"));


import "./styles/foundation.css";
import "./styles/workspace.css";
import "./styles/public.css";
import "./styles/workflow.css";
import "./styles/readability.css";

function App() {
  return (
    <ActionFeedbackProvider><PublicAppearance><Suspense fallback={<main className="route-loading" data-theme={readTheme(globalThis.localStorage)}><LoadingSkeleton /></main>}><Routes>
      <Route path="/provider/disbursements/:projectId" element={<ProtectedRoute allowedRoles={["SERVICE_PROVIDER"]}><ProviderDisbursements /></ProtectedRoute>} />
      <Route path="/provider/evidence/:projectId" element={<ProtectedRoute allowedRoles={["SERVICE_PROVIDER"]}><ProviderEvidence /></ProtectedRoute>} />
      <Route path="/provider/evidence/:projectId/phases/:phaseId" element={<ProtectedRoute allowedRoles={["SERVICE_PROVIDER"]}><ProviderEvidence /></ProtectedRoute>} />
      <Route path="/how-it-works" element={<HowItWorks />} />
      <Route path="/admin/accounts" element={<ProtectedRoute allowedRoles={["ADMINISTRATOR"]}><AdminAccounts /></ProtectedRoute>} />
      <Route path="/admin/users" element={<ProtectedRoute allowedRoles={["ADMINISTRATOR"]}><UserManagement /></ProtectedRoute>} />
      <Route path="/verify-email" element={<VerifyEmail/>} />
      <Route path="/admin/evidence" element={<ProtectedRoute allowedRoles={["ADMINISTRATOR"]}><OfficerEvidence /></ProtectedRoute>} />
      <Route path="/change-password" element={<RequiredPasswordChange />} />
      <Route
        path="/"
        element={<Navigate to="/login" replace />}
      />

      <Route
        path="/login"
        element={<Login />}
      />

      <Route
        path="/register"
        element={<Register />}
      />

      <Route
        path="/forgot-password"
        element={<ForgotPassword />}
      />

      <Route
        path="/reset-password"
        element={<ResetPassword />}
      />

      <Route
        path="/provider"
        element={
          <ProtectedRoute
            allowedRoles={["SERVICE_PROVIDER"]}
          >
            <ProviderDashboard />
          </ProtectedRoute>
        }
      />

      <Route
        path="/provider/applications/new"
        element={
          <ProtectedRoute
            allowedRoles={["SERVICE_PROVIDER"]}
          >
            <NewApplication />
          </ProtectedRoute>
        }
      />

      <Route
        path="/provider/projects"
        element={
          <ProtectedRoute
            allowedRoles={["SERVICE_PROVIDER"]}
          >
            <ProviderProjects />
          </ProtectedRoute>
        }
      />

      <Route
        path="/provider/evidence"
        element={
          <ProtectedRoute
            allowedRoles={["SERVICE_PROVIDER"]}
          >
            <ProviderEvidence />
          </ProtectedRoute>
        }
      />

      <Route
        path="/provider/disbursements"
        element={
          <ProtectedRoute
            allowedRoles={["SERVICE_PROVIDER"]}
          >
            <ProviderDisbursements />
          </ProtectedRoute>
        }
      />

      <Route
        path="/provider/notifications"
        element={
          <ProtectedRoute
            allowedRoles={["SERVICE_PROVIDER"]}
          >
            <ProviderNotifications />
          </ProtectedRoute>
        }
      />

      <Route
        path="/admin"
        element={
          <ProtectedRoute
            allowedRoles={["ADMINISTRATOR"]}
          >
            <AdminDashboard />
          </ProtectedRoute>
        }
      />

      <Route
        path="/admin/applications"
        element={
          <ProtectedRoute
            allowedRoles={["ADMINISTRATOR"]}
          >
            <AdminApplications />
          </ProtectedRoute>
        }
      />

      <Route
        path="/admin/projects"
        element={
          <ProtectedRoute
            allowedRoles={["ADMINISTRATOR"]}
          >
            <AdminProjects />
          </ProtectedRoute>
        }
      />

      <Route
        path="/admin/disbursements"
        element={
          <ProtectedRoute
            allowedRoles={["ADMINISTRATOR"]}
          >
            <AdminDisbursements />
          </ProtectedRoute>
        }
      />

      <Route
        path="/admin/audit-logs"
        element={
          <ProtectedRoute
            allowedRoles={["ADMINISTRATOR"]}
          >
            <AdminAuditLogs />
          </ProtectedRoute>
        }
      />

      <Route
        path="/officer"
        element={
          <ProtectedRoute
            allowedRoles={["VERIFICATION_OFFICER"]}
          >
            <OfficerDashboard />
          </ProtectedRoute>
        }
      />

      <Route
        path="/officer/evidence"
        element={
          <ProtectedRoute
            allowedRoles={["VERIFICATION_OFFICER"]}
          >
            <OfficerEvidence />
          </ProtectedRoute>
        }
      />

      <Route
        path="/officer/phases"
        element={
          <ProtectedRoute
            allowedRoles={["VERIFICATION_OFFICER"]}
          >
            <OfficerPhases />
          </ProtectedRoute>
        }
      />

      <Route
        path="/unauthorized"
        element={
          <main className="simple-page">
            <Icon name="shield" size={42}/>
            <h1>Access Denied</h1>

            <p>
              You do not have permission to view this page.
            </p>
            <Link className="project-action" to="/login">Return to sign in</Link>
          </main>
        }
      />

      <Route
        path="*"
        element={
          <main className="simple-page">
            <Icon name="search" size={42}/>
            <h1>404</h1>
            <p>Page not found.</p>
            <Link className="project-action" to="/login">Return to sign in</Link>
          </main>
        }
      />
    </Routes></Suspense></PublicAppearance></ActionFeedbackProvider>
  );
}

export default App;
