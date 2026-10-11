import ActionFeedback from "../../components/ActionFeedback";
import UnsavedChanges from "../../components/UnsavedChanges";
import { useState } from "react";
import PageHeader from "../../components/PageHeader";
import AppShell from "../../components/AppShell";
import { useNavigate } from "react-router-dom";

import api from "../../services/api";
import Icon from "../../components/Icon";
import fundLogo from "../../assets/public-fund-logo.webp";


function NewApplication() {
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    title: "",
    description: "",
    requestedBudget: "",
    timeline: "",
    expectedCompletionDate: "",
  });

  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [loading, setLoading] = useState(false);

  const handleChange = (event) => {
    const { name, value } = event.target;

    setFormData((previous) => ({
      ...previous,
      [name]: value,
    }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    setError("");
    setSuccess("");
    setLoading(true);

    try {
      const params = new URLSearchParams();

      params.append("title", formData.title);
      params.append("description", formData.description);
      params.append(
        "requestedBudget",
        formData.requestedBudget
      );
      params.append("timeline", formData.timeline);
      if(formData.expectedCompletionDate)params.append("expectedCompletionDate",formData.expectedCompletionDate);

      await api.post(
        "/applications",
        params
      );

      setSuccess(
        "Funding application submitted successfully."
      );

      setTimeout(() => {
        navigate("/provider");
      }, 1200);

    } catch (error) {


      const message =
        error.response?.data?.message ||
        error.response?.data ||
        error.message ||
        "Unable to submit application.";

      setError(
        typeof message === "string"
          ? message
          : JSON.stringify(message)
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <AppShell><UnsavedChanges dirty={!success&&Object.values(formData).some(Boolean)}/><main className="new-application-page">
      <div className="application-form-container">
        <aside className="application-context">
          <img
            src={fundLogo}
            alt="Public Fund Management System"
          />

          <p>APPLICATION GUIDE</p>
          <h2>A clear case makes a stronger project.</h2>
          <span>
            Give the administrator enough context to assess
            public value, affordability and delivery time.
          </span>

          <ol>
            <li>
              <Icon name="document" size={18} />
              <div>
                <strong>Describe the outcome</strong>
                <small>Be specific about the public benefit.</small>
              </div>
            </li>
            <li>
              <Icon name="money" size={18} />
              <div>
                <strong>Set a realistic budget</strong>
                <small>Phase allocations cannot exceed it.</small>
              </div>
            </li>
            <li>
              <Icon name="shield" size={18} />
              <div>
                <strong>Review before sending</strong>
                <small>Your submission enters formal review.</small>
              </div>
            </li>
          </ol>
        </aside>

        <div className="application-workspace">
        <PageHeader title="New funding application" description="Submit a funding request for review by the administrator." />

        <form
          className="application-form"
          onSubmit={handleSubmit}
        >
          <div className="application-field">
            <label htmlFor="title">
              Project title
            </label>

            <input
              id="title"
              name="title"
              type="text"
              value={formData.title}
              onChange={handleChange}
              placeholder="Enter project title"
              required
            />
          </div>

          <div className="application-field">
            <label htmlFor="description">
              Project description
            </label>

            <textarea
              id="description"
              name="description"
              value={formData.description}
              onChange={handleChange}
              placeholder="Describe the purpose and scope of the project"
              rows="6"
              required
            />
          </div>

          <div className="application-row">
            <div className="application-field">
              <label htmlFor="requestedBudget">
                Requested budget
              </label>

              <div className="money-input">
                <span>R</span>

                <input
                  id="requestedBudget"
                  name="requestedBudget"
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={formData.requestedBudget}
                  onChange={handleChange}
                  placeholder="500000.00"
                  required
                />
              </div>
            </div>

            <div className="application-field">
              <label htmlFor="timeline">
                Project timeline
              </label>

              <input
                id="timeline"
                name="timeline"
                type="text"
                value={formData.timeline}
                onChange={handleChange}
                placeholder="e.g. 6 months"
                required
              />
            </div>
          </div>

          {error && (
            <div className="application-error" role="alert">
              {error}
            </div>
          )}

          {success && (
            <ActionFeedback message={success}/>
          )}

          <div className="application-actions">
            <label>Expected completion date<input type="date" name="expectedCompletionDate" value={formData.expectedCompletionDate} onChange={handleChange}/></label>
            <button
              type="button"
              className="cancel-button"
              onClick={() => navigate("/provider")}
            >
              Cancel
            </button>

            <button
              type="submit"
              className="submit-application-button"
              disabled={loading}
            >
              {loading
                ? "Submitting..."
                : "Submit application"}
            </button>
          </div>
        </form>
        </div>
      </div>
    </main></AppShell>
  );
}

export default NewApplication;
