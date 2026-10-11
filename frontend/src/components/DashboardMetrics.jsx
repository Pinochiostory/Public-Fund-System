import { Link } from "react-router-dom";
import Icon from "./Icon";

function MetricValue({ value }) {
  return <strong className={"metric-value"+(typeof value === "string" ? " metric-currency" : "")} title={String(value)}>{value}</strong>;
}

export default function DashboardMetrics({ items }) {
  return <section className="dashboard-metrics" aria-label="Overview metrics">
    {items.map(item => <Link className="metric" key={item.label} to={item.to}>
      <div className="metric-label"><span className="metric-icon"><Icon name={item.icon} size={21}/></span><span>{item.label}</span><Icon className="metric-open" name="external" size={16}/></div>
      <MetricValue value={item.value} />
      <div className="metric-footer"><span>{item.note}</span></div>
    </Link>)}
  </section>;
}
