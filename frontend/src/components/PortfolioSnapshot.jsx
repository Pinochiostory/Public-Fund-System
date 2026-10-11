import { useState } from "react";
import { Button } from "@fluentui/react-components";

const palette = ["var(--blue)", "var(--chart-2)", "var(--chart-3)", "var(--chart-4)", "var(--chart-5)"];
export default function PortfolioSnapshot({ records, title, noun }) {
  const [mode, setMode] = useState("count");
  const counts = records.reduce((result, item) => {
    const status = item.status || "UNSPECIFIED";
    result[status] = (result[status] || 0) + 1;
    return result;
  }, {});
  const groups = Object.entries(counts).sort((a, b) => b[1] - a[1]);
  const segments = groups.map(([, count], index) => {
    const start = groups.slice(0,index).reduce((sum,[,value])=>sum+value,0) / records.length * 100;
    const end = start + count / records.length * 100;
    return palette[index % palette.length] + " " + start + "% " + end + "%";
  });
  return <section className="portfolio-snapshot" aria-label={title}>
    <div className="snapshot-heading"><div><h2>{title}</h2><p>Where your {noun.toLowerCase()} stand today.</p></div>
      <div className="chart-switch" aria-label="Chart display"><Button size="small" appearance={mode === "count" ? "primary" : "subtle"} aria-pressed={mode === "count"} onClick={() => setMode("count")}>Count</Button><Button size="small" appearance={mode === "share" ? "primary" : "subtle"} aria-pressed={mode === "share"} onClick={() => setMode("share")}>Share</Button></div>
    </div>
    {groups.length ? <div className="snapshot-body">
      <div className="snapshot-donut" style={{background: "conic-gradient(" + segments.join(",") + ")"}} aria-hidden="true"><div><strong>{records.length}</strong><span>{noun}</span></div></div>
      <dl className="snapshot-legend">{groups.map(([status, count], index) => <div key={status}><dt><i style={{background:palette[index % palette.length]}}/>{status.replaceAll("_", " ").toLowerCase()}</dt><dd>{mode === "count" ? count : Math.round(count / records.length * 100) + "%"}</dd></div>)}</dl>
    </div> : <div className="snapshot-empty"><strong>No activity yet</strong><p>Your {noun.toLowerCase()} will appear here as activity begins.</p></div>}
  </section>;
}
