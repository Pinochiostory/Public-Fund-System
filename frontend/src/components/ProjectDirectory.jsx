import { Link } from "react-router-dom";
import Icon from "./Icon";
import RecordFilters from "./RecordFilters";
import { useRecordFilters } from "./useRecordFilters";
import { projectName, formatStatus, formatMoney } from "./projectData";
export default function ProjectDirectory({projects,basePath,action,summary}) {
  const filter = useRecordFilters(projects);
  return <section className="project-directory">
    <RecordFilters filter={filter} label="projects" />
    {!projects.length && <div className="project-empty">Your approved projects will appear here.</div>}
    <div className="project-directory-grid">{filter.filtered.map(project =>
      <article className="project-directory-card" key={project.id}>
        <div className="project-directory-heading"><span className="project-folder"><Icon name="folder" size={25}/></span><span className={"status-pill status-"+(project.status || "").toLowerCase().replaceAll("_","-")}>{formatStatus(project.status)}</span></div>
        <h2>{projectName(project)}</h2><p>{project.description || "Track this project's progress and records."}</p>
        {project.approvedBudget != null && <div className="project-directory-budget"><span>Approved funding</span><strong>{formatMoney(project.approvedBudget)}</strong></div>}
        <div className="project-directory-summary">{summary?.(project)}</div>
        <Link className="project-action" to={basePath+"/"+project.id}>{action}<Icon name="arrowRight" size={18}/></Link>
      </article>
    )}</div>
  </section>;
}
